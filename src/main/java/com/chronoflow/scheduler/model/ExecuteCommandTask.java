package com.chronoflow.scheduler.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class ExecuteCommandTask implements TaskPayload {

    private final String command;

    @JsonCreator
    public ExecuteCommandTask(@JsonProperty("command") String command) {
        this.command = command;
    }

    public String getCommand() {
        return command;
    }
}
