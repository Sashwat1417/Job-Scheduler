# Chronoflow Job Scheduler

Modern applications often need background tasks to run on a schedule—sending digests, generating reports, or doing routine cleanup. **Chronoflow Job Scheduler** is a lightweight, **in-memory** scheduling service built with **Spring Boot 3** and **Quartz** that reads job definitions from JSON files and executes them without requiring restarts when jobs change.

## Key features

- **Dynamic job management**: watches a directory for `*.json` job files and hot-reloads on add/update/delete.
- **Flexible schedules**: supports **recurring cron** schedules and **one-time** ISO-8601 timestamps.
- **Extensible tasks**: designed around task handlers; currently implements `execute_command`.
- **Concurrent execution**: runs multiple jobs in parallel via Quartz thread pool.
- **Execution logging**: logs each run with `job_id`, timestamps, status, and details; can also append to a results log file.

## Job definition format

Jobs live under a directory (default: `./jobs.d`) and follow this schema:

```json
{
  "job_id": "canary-001",
  "description": "Must keep firing even while blocker is stuck.",
  "schedule": "*/1 * * * *",
  "task": {
    "type": "execute_command",
    "command": "echo 'canary-001 alive'"
  }
}
```

### `schedule`

- **Recurring (cron)**: a cron string (for example `*/5 * * * *` for every 5 minutes). Five-field cron (minute-first) is normalized for Quartz by prefixing `0` seconds.
- **One-time**: an ISO-8601 instant (for example `2025-10-26T15:00:00Z`). Jobs in the past are skipped at load time.

### `task.type`

- `execute_command`: runs the command via `/bin/sh -c <command>`.
  - Timeout: **60 minutes**
  - Captures up to **64KB** of combined output (stdout + stderr)

## Configuration

The default config is in `src/main/resources/application.yml`:

- `chronoflow.jobs.directory`: jobs directory to watch (default `./jobs.d`)
- `chronoflow.jobs.watch-debounce-ms`: debounce window for file changes (default `400`)
- `chronoflow.jobs.execution-results-log`: file to append execution results to (default `./logs/execution-results.log`); set to empty string to disable
- `spring.quartz.properties.org.quartz.threadPool.threadCount`: worker threads (default `8`)

You can override the jobs directory via env var:

```bash
export CHRONOFLOW_JOBS_DIRECTORY=/path/to/jobs.d
```

or via Spring Boot args:

```bash
java -jar target/job-scheduler-*.jar --chronoflow.jobs.directory=/path/to/jobs.d
```

## Requirements

- **JDK 17+** and Maven 3.9+ (or use the included `./mvnw` wrapper).

## Run

```bash
./mvnw spring-boot:run
```

The service watches the configured jobs directory (default `./jobs.d`). Example job files are in `jobs.d/` (for example `canary-001.json` and `blocker-001.json`).

## Tests

```bash
./mvnw test
```

Integration tests use Quartz `* * * * * ?` (every second) and expect a Unix shell at `/bin/sh`.

## Observability

Each execution is logged at INFO with fields like `job_id`, `status`, `startedAt`, `finishedAt`, plus `detail` and captured output. If `chronoflow.jobs.execution-results-log` is set, a single-line record is also appended to that file:

```
<finishedAt> job_id=<id> status=<SUCCESS|FAILURE> detail=<...> output=<...>
```

## Design notes & limitations

- Quartz `JobKey` is derived from the **absolute path** of the definition file so duplicate `job_id` values in different files do not collide.
- The scheduler is **in-memory only** (no database). State is rebuilt from job files on startup.
- `execute_command` is powerful—only run job definitions you trust.
