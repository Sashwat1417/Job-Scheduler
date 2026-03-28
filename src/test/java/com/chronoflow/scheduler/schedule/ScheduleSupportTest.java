package com.chronoflow.scheduler.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class ScheduleSupportTest {

    @Test
    void parsesIsoInstantAsOneTime() {
        Optional<ParsedSchedule> s = ScheduleSupport.parse("2027-01-01T00:00:00Z");
        assertThat(s).isPresent();
        assertThat(s.get()).isInstanceOf(ParsedSchedule.OneTimeSchedule.class);
    }

    @Test
    void normalizesFiveFieldCronWithSecondsPrefix() {
        Optional<ParsedSchedule> s = ScheduleSupport.parse("*/5 * * * *");
        assertThat(s).isPresent();
        assertThat(s.get()).isInstanceOf(ParsedSchedule.CronSchedule.class);
        assertThat(((ParsedSchedule.CronSchedule) s.get()).quartzExpression()).isEqualTo("0 */5 * * * ?");
    }

    @Test
    void rejectsInvalidCron() {
        assertThat(ScheduleSupport.parse("not-a-cron")).isEmpty();
    }
}
