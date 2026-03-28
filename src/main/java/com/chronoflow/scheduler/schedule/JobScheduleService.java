package com.chronoflow.scheduler.schedule;

import com.chronoflow.scheduler.config.JobsProperties;
import com.chronoflow.scheduler.logging.ExecutionLogger;
import com.chronoflow.scheduler.model.JobDefinition;
import com.chronoflow.scheduler.model.TaskPayload;
import com.chronoflow.scheduler.quartz.ChronoflowQuartzJob;
import com.chronoflow.scheduler.task.TaskHandlerRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.stream.Stream;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class JobScheduleService {

    private static final Logger log = LoggerFactory.getLogger(JobScheduleService.class);

    private static final String JOB_GROUP = "chronoflow-files";

    private final Scheduler scheduler;
    private final ObjectMapper objectMapper;
    private final JobsProperties jobsProperties;
    private final TaskHandlerRegistry taskHandlerRegistry;
    private final ExecutionLogger executionLogger;

    public JobScheduleService(
            Scheduler scheduler,
            ObjectMapper objectMapper,
            JobsProperties jobsProperties,
            TaskHandlerRegistry taskHandlerRegistry,
            ExecutionLogger executionLogger
    ) {
        this.scheduler = scheduler;
        this.objectMapper = objectMapper;
        this.jobsProperties = jobsProperties;
        this.taskHandlerRegistry = taskHandlerRegistry;
        this.executionLogger = executionLogger;
    }

    public void publishSchedulerContext() throws SchedulerException {
        scheduler.getContext().put(ChronoflowQuartzJob.CTX_OBJECT_MAPPER, objectMapper);
        scheduler.getContext().put(ChronoflowQuartzJob.CTX_TASK_REGISTRY, taskHandlerRegistry);
        scheduler.getContext().put(ChronoflowQuartzJob.CTX_EXECUTION_LOGGER, executionLogger);
    }

    public Path jobsRoot() {
        return Path.of(jobsProperties.getDirectory()).toAbsolutePath().normalize();
    }

    public void scanAndScheduleAll() {
        Path root = jobsRoot();
        try {
            Files.createDirectories(root);
        } catch (Exception e) {
            log.error("Could not create jobs directory {}", root, e);
            return;
        }
        try (Stream<Path> paths = Files.list(root)) {
            paths.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .filter(Files::isRegularFile)
                    .forEach(this::syncFile);
        } catch (Exception e) {
            log.error("Initial scan failed for {}", root, e);
        }
    }

    public void syncFile(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        if (!absolute.startsWith(jobsRoot())) {
            log.debug("Ignoring path outside jobs root: {}", absolute);
        }
        if (!absolute.getFileName().toString().endsWith(".json")) {
            return;
        }
        if (!Files.isRegularFile(absolute)) {
            removeJobForPath(absolute);
            return;
        }
        try {
            JobDefinition def = objectMapper.readValue(absolute.toFile(), JobDefinition.class);
            if (!validate(def)) {
                return;
            }
            Optional<ParsedSchedule> schedule = ScheduleSupport.parse(def.schedule());
            if (schedule.isEmpty()) {
                log.warn("Invalid schedule for job_id={} file={}", def.jobId(), absolute);
                return;
            }
            if (schedule.get() instanceof ParsedSchedule.OneTimeSchedule oneTime) {
                if (oneTime.when().isBefore(Instant.now())) {
                    log.warn(
                            "Skipping one-time job in the past job_id={} file={} when={}",
                            def.jobId(),
                            absolute,
                            oneTime.when()
                    );
                    return;
                }
            }
            reschedule(absolute, def, schedule.get());
        } catch (Exception e) {
            log.error("Failed to parse or schedule job file {}", absolute, e);
        }
    }

    public void removeJobForPath(Path path) {
        try {
            JobKey key = jobKeyForPath(path);
            if (scheduler.checkExists(key)) {
                scheduler.deleteJob(key);
                log.info("Removed schedule for file {}", path);
            }
        } catch (SchedulerException e) {
            log.error("Failed to remove job for path {}", path, e);
        }
    }

    private boolean validate(JobDefinition def) {
        if (def.jobId() == null || def.jobId().isBlank()) {
            log.warn("job_id missing in definition");
            return false;
        }
        TaskPayload task = def.task();
        if (task == null) {
            log.warn("task missing for job_id={}", def.jobId());
            return false;
        }
        if (!taskHandlerRegistry.hasHandlerFor(task)) {
            log.warn("Unsupported task for job_id={}: {}", def.jobId(), task.getClass().getName());
            return false;
        }
        return true;
    }

    private void reschedule(Path file, JobDefinition def, ParsedSchedule parsedSchedule) throws Exception {
        JobKey jobKey = jobKeyForPath(file);
        if (scheduler.checkExists(jobKey)) {
            scheduler.deleteJob(jobKey);
        }
        String taskJson = objectMapper.writeValueAsString(def.task());
        JobDetail detail = JobBuilder.newJob(ChronoflowQuartzJob.class)
                .withIdentity(jobKey)
                .usingJobData(ChronoflowQuartzJob.JOB_DATA_JOB_ID, def.jobId())
                .usingJobData(ChronoflowQuartzJob.JOB_DATA_TASK_JSON, taskJson)
                .build();
        Trigger trigger = buildTrigger(parsedSchedule, jobKey);
        scheduler.scheduleJob(detail, trigger);
        log.info("Scheduled job_id={} from {}", def.jobId(), file);
    }

    private Trigger buildTrigger(ParsedSchedule schedule, JobKey jobKey) {
        TriggerKey tk = triggerKeyFor(jobKey);
        if (schedule instanceof ParsedSchedule.OneTimeSchedule oneTime) {
            return TriggerBuilder.newTrigger()
                    .withIdentity(tk)
                    .startAt(java.util.Date.from(oneTime.when()))
                    .build();
        }
        if (schedule instanceof ParsedSchedule.CronSchedule cron) {
            return TriggerBuilder.newTrigger()
                    .withIdentity(tk)
                    .withSchedule(CronScheduleBuilder.cronSchedule(cron.quartzExpression()))
                    .build();
        }
        throw new IllegalArgumentException("Unsupported schedule: " + schedule);
    }

    static JobKey jobKeyForPath(Path path) {
        String name = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(path.toAbsolutePath().normalize().toString().getBytes(StandardCharsets.UTF_8));
        return JobKey.jobKey(name, JOB_GROUP);
    }

    private static TriggerKey triggerKeyFor(JobKey jobKey) {
        return TriggerKey.triggerKey(jobKey.getName() + "-trg", JOB_GROUP);
    }

    public boolean isScheduled(Path path) throws SchedulerException {
        return scheduler.checkExists(jobKeyForPath(path));
    }
}
