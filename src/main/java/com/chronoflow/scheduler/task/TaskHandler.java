package com.chronoflow.scheduler.task;

import com.chronoflow.scheduler.model.TaskPayload;

public interface TaskHandler {

    boolean supports(TaskPayload task);

    TaskResult run(TaskPayload task);
}
