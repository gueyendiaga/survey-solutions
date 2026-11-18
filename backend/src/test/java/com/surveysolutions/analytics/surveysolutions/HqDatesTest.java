package com.surveysolutions.analytics.surveysolutions;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class HqDatesTest {
    @Test
    void readsUtcDatesWithOrWithoutAnExplicitOffset() {
        assertThat(HqDates.parse("2026-11-03T14:59:48.156526")).isEqualTo(Instant.parse("2026-11-03T14:59:48.156526Z"));
        assertThat(HqDates.parse("2026-11-03T14:59:48Z")).isEqualTo(Instant.parse("2026-11-03T14:59:48Z"));
        assertThat(HqDates.parse("2026-11-03T16:59:48+02:00")).isEqualTo(Instant.parse("2026-11-03T14:59:48Z"));
        assertThat(HqDates.parse("")).isNull();
        assertThat(HqDates.parse("pas une date")).isNull();
    }
}
