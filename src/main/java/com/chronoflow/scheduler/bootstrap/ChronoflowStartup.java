package com.chronoflow.scheduler.bootstrap;

import com.chronoflow.scheduler.schedule.JobScheduleService;
import com.chronoflow.scheduler.watch.JobsDirectoryWatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(0)
public class ChronoflowStartup implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ChronoflowStartup.class);

    private final JobScheduleService jobScheduleService;
    private final JobsDirectoryWatcher jobsDirectoryWatcher;

    public ChronoflowStartup(JobScheduleService jobScheduleService, JobsDirectoryWatcher jobsDirectoryWatcher) {
        this.jobScheduleService = jobScheduleService;
        this.jobsDirectoryWatcher = jobsDirectoryWatcher;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        jobScheduleService.publishSchedulerContext();
        jobScheduleService.scanAndScheduleAll();
        jobsDirectoryWatcher.start();
        log.info("Chronoflow job scheduler ready; jobs directory={}", jobScheduleService.jobsRoot());
    }
}
