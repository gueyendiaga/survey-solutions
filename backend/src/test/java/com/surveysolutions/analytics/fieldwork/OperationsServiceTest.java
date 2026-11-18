package com.surveysolutions.analytics.fieldwork;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.surveysolutions.analytics.fieldwork.OperationsDtos.InterviewerTablet;
import com.surveysolutions.analytics.store.OperationsRepository.TabletActivity;

class OperationsServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-17T12:00:00Z");

    private static TabletActivity activity(String name, boolean locked, Boolean device, Instant tablet, Instant completion, long pending) {
        return new TabletActivity(name, "sup1", locked, device, tablet, completion, pending,
                pending > 0 ? Instant.parse("2026-11-10T08:00:00Z") : null);
    }

    @Test
    void classifiesInterviewersByTheirLatestTabletOrCompletionActivity() {
        InterviewerTablet active = OperationsService.tablet(activity("enq1", false, true,
                Instant.parse("2026-11-12T08:00:00Z"), Instant.parse("2026-11-16T18:00:00Z"), 0), NOW);
        InterviewerTablet inactive = OperationsService.tablet(activity("enq2", false, true,
                Instant.parse("2026-11-13T09:00:00Z"), null, 2), NOW);
        InterviewerTablet never = OperationsService.tablet(activity("enq3", false, false, null, null, 3), NOW);
        InterviewerTablet locked = OperationsService.tablet(activity("enq4", true, true,
                Instant.parse("2026-11-17T08:00:00Z"), null, 0), NOW);

        assertThat(active.state()).isEqualTo("ACTIVE");
        assertThat(active.daysSinceActivity()).isZero();
        assertThat(inactive.state()).isEqualTo("INACTIVE");
        assertThat(inactive.daysSinceActivity()).isEqualTo(4);
        assertThat(never.state()).isEqualTo("NEVER");
        assertThat(never.daysSinceActivity()).isNull();
        assertThat(locked.state()).isEqualTo("LOCKED");

        OperationsDtos.TabletSummary summary = OperationsService.summary(List.of(active, inactive, never, locked));
        assertThat(summary.interviewers()).isEqualTo(4);
        assertThat(summary.active()).isEqualTo(1);
        assertThat(summary.inactive()).isEqualTo(1);
        assertThat(summary.neverActive()).isEqualTo(1);
        assertThat(summary.pendingAssignments()).isEqualTo(5);
        assertThat(summary.withoutDevice()).isEqualTo(1);
        assertThat(summary.locked()).isEqualTo(1);
    }
}
