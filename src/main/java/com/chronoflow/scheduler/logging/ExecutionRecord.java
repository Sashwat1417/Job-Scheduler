package com.chronoflow.scheduler.logging;

import com.chronoflow.scheduler.task.ExecutionStatus;
import java.time.Instant;

public record ExecutionRecord(
        String jobId,
        Instant startedAt,
        Instant finishedAt,
        ExecutionStatus status,
        String detail
) {
}
