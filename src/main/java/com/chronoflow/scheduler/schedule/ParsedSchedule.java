package com.chronoflow.scheduler.schedule;

import java.time.Instant;

public sealed interface ParsedSchedule permits ParsedSchedule.CronSchedule, ParsedSchedule.OneTimeSchedule {

    record OneTimeSchedule(Instant when) implements ParsedSchedule {}

    record CronSchedule(String quartzExpression) implements ParsedSchedule {}
}
