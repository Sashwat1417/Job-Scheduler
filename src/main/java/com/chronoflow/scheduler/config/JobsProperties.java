package com.chronoflow.scheduler.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "chronoflow.jobs")
public class JobsProperties {

    /**
     * Directory containing *.json job definition files.
     */
    @NotBlank
    private String directory = "./jobs.d";

    /**
     * Debounce window for filesystem MODIFY events before reloading a file.
     */
    @Min(0)
    private long watchDebounceMs = 400;

    public String getDirectory() {
        return directory;
    }

    public void setDirectory(String directory) {
        this.directory = directory;
    }

    public long getWatchDebounceMs() {
        return watchDebounceMs;
    }

    public void setWatchDebounceMs(long watchDebounceMs) {
        this.watchDebounceMs = watchDebounceMs;
    }
}
