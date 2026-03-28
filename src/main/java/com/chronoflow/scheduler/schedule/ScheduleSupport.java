package com.chronoflow.scheduler.schedule;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import org.quartz.CronScheduleBuilder;

final class ScheduleSupport {

    private ScheduleSupport() {}

    static Optional<ParsedSchedule> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String s = raw.trim();
        try {
            return Optional.of(new ParsedSchedule.OneTimeSchedule(Instant.parse(s)));
        } catch (DateTimeParseException ignored) {
            // fall through to cron
        }
        String quartz = toQuartzCronExpression(s);
        try {
            CronScheduleBuilder.cronSchedule(quartz);
            return Optional.of(new ParsedSchedule.CronSchedule(quartz));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Quartz expects seconds first and does not allow both day-of-month and day-of-week to be unconstrained.
     * Classic five-field cron (minute hour dom month dow) is mapped to six Quartz fields with {@code ?} for dow
     * when both dom and dow are {@code *}.
     */
    static String toQuartzCronExpression(String expression) {
        String[] parts = expression.trim().split("\\s+");
        if (parts.length == 5) {
            String dom = parts[2];
            String dow = parts[4];
            if ("*".equals(dom) && "*".equals(dow)) {
                return "0 " + parts[0] + " " + parts[1] + " * " + parts[3] + " ?";
            }
            return "0 " + parts[0] + " " + parts[1] + " " + dom + " " + parts[3] + " " + dow;
        }
        return expression.trim();
    }
}
