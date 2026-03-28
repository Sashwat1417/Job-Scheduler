package com.chronoflow.scheduler.task;

import com.chronoflow.scheduler.model.ExecuteCommandTask;
import com.chronoflow.scheduler.model.TaskPayload;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class ExecuteCommandHandler implements TaskHandler {

    private static final long TIMEOUT_MINUTES = 60;
    private static final int MAX_CAPTURE_BYTES = 64 * 1024;

    @Override
    public boolean supports(TaskPayload task) {
        return task instanceof ExecuteCommandTask;
    }

    @Override
    public TaskResult run(TaskPayload task) {
        ExecuteCommandTask cmd = (ExecuteCommandTask) task;
        if (cmd.getCommand() == null || cmd.getCommand().isBlank()) {
            return TaskResult.failure("empty command");
        }
        ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c", cmd.getCommand());
        pb.redirectErrorStream(true);
        try {
            Process process = pb.start();
            AtomicReference<String> stdout = new AtomicReference<>("");
            Thread drainer = new Thread(
                    () -> {
                        try {
                            stdout.set(readLimited(process.getInputStream(), MAX_CAPTURE_BYTES));
                        } catch (IOException e) {
                            stdout.set("");
                        }
                    },
                    "chronoflow-exec-drain"
            );
            drainer.setDaemon(true);
            drainer.start();
            boolean finished = process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES);
            drainer.join(Duration.ofMinutes(TIMEOUT_MINUTES).toMillis());
            String output = stdout.get();
            if (!finished) {
                process.destroyForcibly();
                return TaskResult.failure("timeout after " + TIMEOUT_MINUTES + "m", output);
            }
            int code = process.exitValue();
            if (code == 0) {
                return TaskResult.success(output);
            }
            return TaskResult.failure("exitCode=" + code, output);
        } catch (IOException e) {
            return TaskResult.failure("io: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return TaskResult.failure("interrupted");
        }
    }

    private static String readLimited(InputStream in, int maxBytes) throws IOException {
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int total = 0;
        int r;
        while ((r = in.read(buf)) != -1) {
            int allow = Math.min(r, maxBytes - total);
            if (allow > 0) {
                captured.write(buf, 0, allow);
                total += allow;
            }
            if (total >= maxBytes) {
                while (in.read(buf) != -1) {
                    // discard remainder
                }
                break;
            }
        }
        return captured.toString(StandardCharsets.UTF_8).trim();
    }
}
