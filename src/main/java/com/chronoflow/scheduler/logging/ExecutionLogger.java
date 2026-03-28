package com.chronoflow.scheduler.logging;

import com.chronoflow.scheduler.task.TaskResult;
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

    private final List<ExecutionRecord> recent = Collections.synchronizedList(new ArrayList<>());

    public void log(String jobId, Instant startedAt, Instant finishedAt, TaskResult result) {
        ExecutionRecord record = new ExecutionRecord(
                jobId,
                startedAt,
                finishedAt,
                result.status(),
                result.detail()
        );
        synchronized (recent) {
            while (recent.size() >= MAX_RECENT) {
                recent.remove(0);
            }
            recent.add(record);
        }
        log.info(
                "job_id={} status={} startedAt={} finishedAt={} detail={}",
                jobId,
                result.status(),
                startedAt,
                finishedAt,
                result.detail()
        );
    }

    public List<ExecutionRecord> getRecentRecords() {
        return Collections.unmodifiableList(new ArrayList<>(recent));
    }

    public void clearRecentRecords() {
        recent.clear();
    }
}
