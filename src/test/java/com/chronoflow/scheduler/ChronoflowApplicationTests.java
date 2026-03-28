package com.chronoflow.scheduler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
class ChronoflowApplicationTests {

    private static final Path JOB_DIR = createTempJobDir();

    private static Path createTempJobDir() {
        try {
            return Files.createTempDirectory("chronoflow-app-it");
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("chronoflow.jobs.directory", () -> JOB_DIR.toAbsolutePath().toString());
        registry.add("chronoflow.jobs.watch-debounce-ms", () -> "0");
    }

    @Test
    void contextLoads() {
    }
}
