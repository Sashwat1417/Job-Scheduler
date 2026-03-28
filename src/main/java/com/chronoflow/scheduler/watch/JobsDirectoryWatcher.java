package com.chronoflow.scheduler.watch;

import com.chronoflow.scheduler.config.JobsProperties;
import com.chronoflow.scheduler.schedule.JobScheduleService;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class JobsDirectoryWatcher {

    private static final Logger log = LoggerFactory.getLogger(JobsDirectoryWatcher.class);

    private final JobsProperties jobsProperties;
    private final JobScheduleService jobScheduleService;

    private final ScheduledExecutorService debouncer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "jobs-watch-debounce");
        t.setDaemon(true);
        return t;
    });
    private final Map<Path, ScheduledFuture<?>> pending = new ConcurrentHashMap<>();

    private volatile WatchService watchService;
    private volatile Thread watchThread;

    public JobsDirectoryWatcher(JobsProperties jobsProperties, JobScheduleService jobScheduleService) {
        this.jobsProperties = jobsProperties;
        this.jobScheduleService = jobScheduleService;
    }

    public void start() {
        Path root = jobScheduleService.jobsRoot();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            log.error("Could not ensure jobs directory exists: {}", root, e);
            return;
        }
        try {
            watchService = root.getFileSystem().newWatchService();
            root.register(
                    watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE
            );
        } catch (IOException e) {
            log.error("Could not watch jobs directory {}", root, e);
            return;
        }
        watchThread = new Thread(this::watchLoop, "jobs-directory-watch");
        watchThread.setDaemon(true);
        watchThread.start();
        log.info("Watching job definitions under {}", root);
    }

    private void watchLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            WatchKey key;
            try {
                key = watchService.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Watch service error", e);
                break;
            }
            Path root = jobScheduleService.jobsRoot();
            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();
                if (kind == StandardWatchEventKinds.OVERFLOW) {
                    jobScheduleService.scanAndScheduleAll();
                    continue;
                }
                @SuppressWarnings("unchecked")
                WatchEvent<Path> ev = (WatchEvent<Path>) event;
                Path name = ev.context();
                Path full = root.resolve(name).normalize();
                handleEvent(kind, full);
            }
            boolean valid = key.reset();
            if (!valid) {
                log.warn("Watch key invalid; stopping directory watcher");
                break;
            }
        }
    }

    private void handleEvent(WatchEvent.Kind<?> kind, Path full) {
        if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
            cancelPending(full);
            jobScheduleService.removeJobForPath(full);
            return;
        }
        if (!full.getFileName().toString().endsWith(".json")) {
            return;
        }
        long debounceMs = jobsProperties.getWatchDebounceMs();
        if (debounceMs <= 0) {
            jobScheduleService.syncFile(full);
            return;
        }
        cancelPending(full);
        final ScheduledFuture<?>[] holder = new ScheduledFuture<?>[1];
        holder[0] = debouncer.schedule(
                () -> {
                    ScheduledFuture<?> current = pending.get(full);
                    if (current != holder[0]) {
                        return;
                    }
                    pending.remove(full);
                    if (Files.isRegularFile(full)) {
                        jobScheduleService.syncFile(full);
                    } else {
                        jobScheduleService.removeJobForPath(full);
                    }
                },
                debounceMs,
                TimeUnit.MILLISECONDS
        );
        pending.put(full, holder[0]);
    }

    private void cancelPending(Path full) {
        ScheduledFuture<?> prev = pending.remove(full);
        if (prev != null) {
            prev.cancel(false);
        }
    }

    @PreDestroy
    public void stop() {
        if (watchThread != null) {
            watchThread.interrupt();
        }
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException ignored) {
            }
        }
        debouncer.shutdownNow();
    }
}
