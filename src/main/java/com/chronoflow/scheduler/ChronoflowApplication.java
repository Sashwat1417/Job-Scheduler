package com.chronoflow.scheduler;

import com.chronoflow.scheduler.config.JobsProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(JobsProperties.class)
public class ChronoflowApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChronoflowApplication.class, args);
    }
}
