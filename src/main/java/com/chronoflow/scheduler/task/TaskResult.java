package com.chronoflow.scheduler.task;

public record TaskResult(ExecutionStatus status, String detail) {
    public static TaskResult success() {
        return new TaskResult(ExecutionStatus.SUCCESS, null);
    }

    public static TaskResult failure(String detail) {
        return new TaskResult(ExecutionStatus.FAILURE, detail);
    }
}
