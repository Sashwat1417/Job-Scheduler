package com.chronoflow.scheduler.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = ExecuteCommandTask.class, name = "execute_command")
})
public sealed interface TaskPayload permits ExecuteCommandTask {
}
