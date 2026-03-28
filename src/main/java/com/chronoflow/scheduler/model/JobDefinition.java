package com.chronoflow.scheduler.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JobDefinition(
        @JsonProperty("job_id") String jobId,
        String description,
        String schedule,
        TaskPayload task
) {
}
