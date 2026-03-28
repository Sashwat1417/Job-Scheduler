package com.chronoflow.scheduler.task;

import com.chronoflow.scheduler.model.TaskPayload;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TaskHandlerRegistry {

    private final List<TaskHandler> handlers;

    public TaskHandlerRegistry(List<TaskHandler> handlers) {
        this.handlers = handlers;
    }

    public boolean hasHandlerFor(TaskPayload task) {
        return handlers.stream().anyMatch(h -> h.supports(task));
    }

    public TaskResult execute(TaskPayload task) {
        return handlers.stream()
                .filter(h -> h.supports(task))
                .findFirst()
                .map(h -> h.run(task))
                .orElseGet(() -> TaskResult.failure("unsupported task type"));
    }
}
