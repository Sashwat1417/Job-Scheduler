package com.chronoflow.scheduler.task;

import com.chronoflow.scheduler.model.ExecuteCommandTask;
import com.chronoflow.scheduler.model.TaskPayload;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class ExecuteCommandHandler implements TaskHandler {

    private static final long TIMEOUT_MINUTES = 60;

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
            boolean finished = process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                return TaskResult.failure("timeout after " + TIMEOUT_MINUTES + "m");
            }
            int code = process.exitValue();
            if (code == 0) {
                return TaskResult.success();
            }
            return TaskResult.failure("exitCode=" + code);
        } catch (IOException e) {
            return TaskResult.failure("io: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return TaskResult.failure("interrupted");
        }
    }
}
