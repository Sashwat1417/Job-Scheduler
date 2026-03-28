package com.chronoflow.scheduler.logging;

import com.chronoflow.scheduler.config.JobsProperties;
import com.chronoflow.scheduler.task.TaskResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ExecutionLogger {

    private static final Logger log = LoggerFactory.getLogger(ExecutionLogger.class);
    private static final int MAX_RECENT = 2_000;

    private final JobsProperties jobsProperties;
    private final List<ExecutionRecord> recent = Collections.synchronizedList(new ArrayList<>());

    public ExecutionLogger(JobsProperties jobsProperties) {
        this.jobsProperties = jobsProperties;
    }

    public void log(String jobId, Instant startedAt, Instant finishedAt, TaskResult result) {
        ExecutionRecord record = new ExecutionRecord(
                jobId,
                startedAt,
                finishedAt,
                result.status(),
                result.detail(),
                result.output()
        );
        synchronized (recent) {
            while (recent.size() >= MAX_RECENT) {
                recent.remove(0);
            }
            recent.add(record);
        }
        log.info(
                "job_id={} status={} startedAt={} finishedAt={} detail={} output={}",
                jobId,
                result.status(),
                startedAt,
                finishedAt,
                result.detail(),
                result.output()
        );
        appendResultsFile(finishedAt, jobId, result);
    }

    private void appendResultsFile(Instant finishedAt, String jobId, TaskResult result) {
        String pathStr = jobsProperties.getExecutionResultsLog();
        if (pathStr == null || pathStr.isBlank()) {
            return;
        }
        Path path = Path.of(pathStr);
        try {
            Files.createDirectories(
                    path.getParent() != null ? path.getParent() : Path.of(".")
            );
            String line = formatResultsLine(finishedAt, jobId, result);
            synchronized (ExecutionLogger.class) {
                Files.writeString(path, line + System.lineSeparator(), StandardCharsets.UTF_8,
                        java.nio.file.StandardOpenOption.CREATE,
                        java.nio.file.StandardOpenOption.APPEND);
            }
        } catch (IOException e) {
            log.warn("Could not append execution results to {}", path, e);
        }
    }

    private static String formatResultsLine(Instant finishedAt, String jobId, TaskResult result) {
        return "%s job_id=%s status=%s detail=%s output=%s"
                .formatted(
                        finishedAt,
                        jobId,
                        result.status(),
                        singleLine(result.detail()),
                        singleLine(result.output())
                );
    }

    private static String singleLine(String s) {
        if (s == null) {
            return "";
        }
        return s.replace('\r', ' ').replace('\n', ' ').trim();
    }

    public List<ExecutionRecord> getRecentRecords() {
        return Collections.unmodifiableList(new ArrayList<>(recent));
    }

    public void clearRecentRecords() {
        recent.clear();
    }
}
