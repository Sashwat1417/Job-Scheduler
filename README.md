# Chronoflow Job Scheduler

A lightweight, in-memory job scheduler built with **Spring Boot 3** and **Quartz**. Job definitions are JSON files under a watched directory (hot reload: add, update, delete without restart).

## Requirements

- **JDK 17+** and Maven 3.9+ (or use the included `./mvnw` wrapper).

## Run

```bash
./mvnw spring-boot:run
```

By default the jobs directory is `./jobs.d` (see `application.yml`). Override with:

```bash
export CHRONOFLOW_JOBS_DIRECTORY=/path/to/jobs.d
```

or:

```
java -jar target/job-scheduler-*.jar --chronoflow.jobs.directory=/path/to/jobs.d
```

Example definitions are under `jobs.d/` with an `.example.json` suffix so they are not active until you rename them to `.json`.

Each execution’s **stdout/stderr** (up to 64 KiB) is logged to the console and, by default, appended as one line per run to **`./logs/execution-results.log`**. Override or disable with `chronoflow.jobs.execution-results-log` (use an empty value to turn off file append).

## Tests

```bash
./mvnw test
```

Integration tests use Quartz `* * * * * ?` (every second) and expect a Unix shell at `/bin/sh`.

## Design notes

- Quartz `JobKey` is derived from the **absolute path** of the definition file so duplicate `job_id` values in different files do not collide.
- Five-field cron (minute-first) is normalized to Quartz by prefixing `0` for seconds.
- One-time schedules use an ISO-8601 instant; jobs in the past are skipped at load time.
