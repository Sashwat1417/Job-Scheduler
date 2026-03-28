package com.chronoflow.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.chronoflow.scheduler.logging.ExecutionLogger;
import com.chronoflow.scheduler.schedule.JobScheduleService;
import com.chronoflow.scheduler.task.ExecutionStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
class JobSchedulerIntegrationTest {

    private static final Path JOB_DIR = createTempJobDir();

    private static Path createTempJobDir() {
        try {
            return Files.createTempDirectory("chronoflow-jobs-it");
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("chronoflow.jobs.directory", () -> JOB_DIR.toAbsolutePath().toString());
        registry.add("chronoflow.jobs.watch-debounce-ms", () -> "0");
    }

    @Autowired
    private JobScheduleService jobScheduleService;

    @Autowired
    private ExecutionLogger executionLogger;

    @BeforeEach
    void cleanJobDirAndLogs() throws IOException {
        executionLogger.clearRecentRecords();
        try (Stream<Path> stream = Files.list(JOB_DIR)) {
            for (Path p : stream.toList()) {
                if (p.getFileName().toString().endsWith(".json")) {
                    jobScheduleService.removeJobForPath(p);
                }
                Files.deleteIfExists(p);
            }
        }
    }

    @AfterAll
    static void removeTempDir() throws IOException {
        try (Stream<Path> stream = Files.list(JOB_DIR)) {
            stream.forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        }
        Files.deleteIfExists(JOB_DIR);
    }

    @Test
    void schedulesAndRunsSuccessfulCommand() {
        Path f = JOB_DIR.resolve("echo.json");
        write(
                f,
                """
                        {
                          "job_id": "echo-1",
                          "schedule": "* * * * * ?",
                          "task": { "type": "execute_command", "command": "echo hi" }
                        }
                        """
        );
        jobScheduleService.syncFile(f);
        await().atMost(Duration.ofSeconds(25)).untilAsserted(() -> assertThat(
                executionLogger.getRecentRecords().stream()
                        .anyMatch(r -> "echo-1".equals(r.jobId()) && r.status() == ExecutionStatus.SUCCESS)
        ).isTrue());
    }

    @Test
    void recordsFailureForNonZeroExit() {
        Path f = JOB_DIR.resolve("fail.json");
        write(
                f,
                """
                        {
                          "job_id": "fail-1",
                          "schedule": "* * * * * ?",
                          "task": { "type": "execute_command", "command": "exit 7" }
                        }
                        """
        );
        jobScheduleService.syncFile(f);
        await().atMost(Duration.ofSeconds(25)).untilAsserted(() -> assertThat(
                executionLogger.getRecentRecords().stream()
                        .anyMatch(r -> "fail-1".equals(r.jobId()) && r.status() == ExecutionStatus.FAILURE)
        ).isTrue());
    }

    @Test
    void removeJobUnschedules() throws Exception {
        Path f = JOB_DIR.resolve("gone.json");
        write(
                f,
                """
                        {
                          "job_id": "gone-1",
                          "schedule": "0 * * * * ?",
                          "task": { "type": "execute_command", "command": "echo x" }
                        }
                        """
        );
        jobScheduleService.syncFile(f);
        assertThat(jobScheduleService.isScheduled(f)).isTrue();
        jobScheduleService.removeJobForPath(f);
        assertThat(jobScheduleService.isScheduled(f)).isFalse();
    }

    @Test
    void multipleJobsCanRunInParallel() {
        Path a = JOB_DIR.resolve("a.json");
        Path b = JOB_DIR.resolve("b.json");
        write(
                a,
                """
                        {
                          "job_id": "parallel-a",
                          "schedule": "* * * * * ?",
                          "task": { "type": "execute_command", "command": "sleep 2" }
                        }
                        """
        );
        write(
                b,
                """
                        {
                          "job_id": "parallel-b",
                          "schedule": "* * * * * ?",
                          "task": { "type": "execute_command", "command": "sleep 2" }
                        }
                        """
        );
        jobScheduleService.syncFile(a);
        jobScheduleService.syncFile(b);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            long withA =
                    executionLogger.getRecentRecords().stream().filter(r -> "parallel-a".equals(r.jobId())).count();
            long withB =
                    executionLogger.getRecentRecords().stream().filter(r -> "parallel-b".equals(r.jobId())).count();
            assertThat(withA).isGreaterThanOrEqualTo(1);
            assertThat(withB).isGreaterThanOrEqualTo(1);
        });
        var concurrent =
                executionLogger.getRecentRecords().stream()
                        .filter(r -> r.jobId().startsWith("parallel-"))
                        .filter(r -> r.startedAt() != null && r.finishedAt() != null)
                        .toList();
        assertThat(concurrent.size()).isGreaterThanOrEqualTo(2);
    }

    private static void write(Path file, String body) {
        try {
            Files.writeString(file, body.trim());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
