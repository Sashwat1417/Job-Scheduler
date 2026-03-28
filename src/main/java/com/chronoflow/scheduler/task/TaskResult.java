package com.chronoflow.scheduler.task;

public record TaskResult(ExecutionStatus status, String detail, String output) {

    public static TaskResult success(String output) {
        return new TaskResult(ExecutionStatus.SUCCESS, null, blankToNull(output));
    }

    public static TaskResult failure(String detail) {
        return new TaskResult(ExecutionStatus.FAILURE, detail, null);
    }

    /**
     * Failure with captured process output (stdout/stderr) for diagnostics.
     */
    public static TaskResult failure(String detail, String output) {
        return new TaskResult(ExecutionStatus.FAILURE, detail, blankToNull(output));
    }

    private static String blankToNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s;
    }
}
