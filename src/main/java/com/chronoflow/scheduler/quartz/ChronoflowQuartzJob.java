package com.chronoflow.scheduler.quartz;

import com.chronoflow.scheduler.logging.ExecutionLogger;
import com.chronoflow.scheduler.model.TaskPayload;
import com.chronoflow.scheduler.task.TaskHandlerRegistry;
import com.chronoflow.scheduler.task.TaskResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.SchedulerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChronoflowQuartzJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(ChronoflowQuartzJob.class);

    public static final String JOB_DATA_JOB_ID = "jobId";
    public static final String JOB_DATA_TASK_JSON = "taskJson";

    public static final String CTX_OBJECT_MAPPER = "objectMapper";
    public static final String CTX_TASK_REGISTRY = "taskRegistry";
    public static final String CTX_EXECUTION_LOGGER = "executionLogger";

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        JobDataMap map = context.getMergedJobDataMap();
        String jobId = map.getString(JOB_DATA_JOB_ID);
        String taskJson = map.getString(JOB_DATA_TASK_JSON);
        Instant start = Instant.now();
        try {
            SchedulerContext schedulerContext = context.getScheduler().getContext();
            ObjectMapper mapper = (ObjectMapper) schedulerContext.get(CTX_OBJECT_MAPPER);
            TaskHandlerRegistry registry = (TaskHandlerRegistry) schedulerContext.get(CTX_TASK_REGISTRY);
            ExecutionLogger executionLogger = (ExecutionLogger) schedulerContext.get(CTX_EXECUTION_LOGGER);
            TaskPayload task = mapper.readValue(taskJson, TaskPayload.class);
            TaskResult result = registry.execute(task);
            executionLogger.log(jobId, start, Instant.now(), result);
        } catch (Exception e) {
            log.error("job_id={} execution failed", jobId, e);
            try {
                SchedulerContext schedulerContext = context.getScheduler().getContext();
                ExecutionLogger executionLogger = (ExecutionLogger) schedulerContext.get(CTX_EXECUTION_LOGGER);
                executionLogger.log(
                        jobId,
                        start,
                        Instant.now(),
                        TaskResult.failure(e.getMessage())
                );
            } catch (Exception inner) {
                log.error("Could not record execution failure", inner);
            }
            throw new JobExecutionException(e);
        }
    }
}
