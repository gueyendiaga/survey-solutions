package com.surveysolutions.analytics.fieldwork;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.surveysolutions.analytics.store.AnalyticsRepository.SyncRun;
import com.surveysolutions.analytics.store.FieldworkRepository;
import com.surveysolutions.analytics.store.FieldworkRepository.AssignmentTarget;
import com.surveysolutions.analytics.store.FieldworkRepository.DailyCount;
import com.surveysolutions.analytics.store.FieldworkRepository.InterviewerActivity;
import com.surveysolutions.analytics.store.InterviewFilter;
import com.surveysolutions.analytics.store.SyncService;

class FieldworkServiceTest {
    private static final UUID QUESTIONNAIRE_ID = UUID.fromString("588daf2d-0c72-4b89-bec9-254459a68983");
    private static final InterviewFilter NO_FILTER = new InterviewFilter(null, null, null, null, null, null, null);

    private final FieldworkRepository repository = mock(FieldworkRepository.class);
    private final SyncService sync = mock(SyncService.class);
    private final FieldworkService service = new FieldworkService(repository, sync);

    @BeforeEach
    void data() {
        Instant now = Instant.parse("2026-11-09T10:00:00Z");
        when(sync.latestSuccessfulSync(QUESTIONNAIRE_ID, 1)).thenReturn(Optional.of(
                new SyncRun(1, QUESTIONNAIRE_ID, 1, "SUCCEEDED", "MANUAL", now, now, 5, 5, "f", null)));
        when(repository.activityByInterviewer(eq(QUESTIONNAIRE_ID), eq(1L), any())).thenReturn(List.of(
                activity("enq1", "sup1", 4, 3, 2, 1, 1, 1500.0, 2),
                activity("enq2", "sup2", 1, 0, 0, 0, 1, null, 0)));
        when(repository.targets(QUESTIONNAIRE_ID, 1)).thenReturn(List.of(
                new AssignmentTarget("enq1", "sup1", 10, 0, 2),
                new AssignmentTarget("enq3", "sup1", 5, 1, 2),
                new AssignmentTarget("sup2", "sup2", 4, 0, 1)));
        when(repository.completionsByDay(eq(QUESTIONNAIRE_ID), eq(1L), any())).thenReturn(List.of(
                new DailyCount(LocalDate.parse("2026-11-04"), 2),
                new DailyCount(LocalDate.parse("2026-11-05"), 1)));
        when(repository.statuses(eq(QUESTIONNAIRE_ID), eq(1L), any())).thenReturn(List.of());
    }

    @Test
    void computesCompletionAgainstAssignmentTargetsPerTeamAndInterviewer() {
        FieldworkDtos.FieldworkOverview overview = service.overview(QUESTIONNAIRE_ID, 1, NO_FILTER);

        assertThat(overview.target()).isEqualTo(19);
        assertThat(overview.unlimitedAssignments()).isEqualTo(1);
        assertThat(overview.completed()).isEqualTo(3);
        assertThat(overview.completionRate()).isEqualTo(15.79);
        assertThat(overview.daily()).extracting(FieldworkDtos.DailyProgress::cumulative).containsExactly(2L, 3L);

        FieldworkDtos.TeamProgress sup1 = overview.teams().get(0);
        assertThat(sup1.supervisor()).isEqualTo("sup1");
        assertThat(sup1.target()).isEqualTo(15);
        assertThat(sup1.completionRate()).isEqualTo(20.0);
        assertThat(sup1.interviewers()).extracting(FieldworkDtos.InterviewerProgress::interviewer)
                .containsExactly("enq1", "enq3");
        FieldworkDtos.InterviewerProgress enq1 = sup1.interviewers().get(0);
        assertThat(enq1.completionRate()).isEqualTo(30.0);
        assertThat(enq1.medianDurationMinutes()).isEqualTo(25.0);
        assertThat(enq1.completedPerActiveDay()).isEqualTo(1.5);

        FieldworkDtos.TeamProgress sup2 = overview.teams().get(1);
        assertThat(sup2.target()).isEqualTo(4);
        assertThat(sup2.interviewers()).extracting(FieldworkDtos.InterviewerProgress::interviewer)
                .containsExactly("enq2");
    }

    @Test
    void restrictsTargetsToTheFilteredInterviewer() {
        InterviewFilter filter = new InterviewFilter(null, null, null, null, "enq1", null, null);
        when(repository.activityByInterviewer(QUESTIONNAIRE_ID, 1L, filter)).thenReturn(List.of(
                activity("enq1", "sup1", 4, 3, 2, 1, 1, 1500.0, 2)));

        FieldworkDtos.FieldworkOverview overview = service.overview(QUESTIONNAIRE_ID, 1, filter);

        assertThat(overview.filtersApplied()).isTrue();
        assertThat(overview.teams()).flatExtracting(FieldworkDtos.TeamProgress::interviewers)
                .extracting(FieldworkDtos.InterviewerProgress::interviewer).containsExactly("enq1");
        assertThat(overview.target()).isEqualTo(10);
        assertThat(overview.teams()).extracting(FieldworkDtos.TeamProgress::supervisor).containsExactly("sup1");
    }

    @Test
    void keepsTargetsWhenFilteringByPeriod() {
        InterviewFilter filter = new InterviewFilter(
                null, LocalDate.parse("2026-11-05"), null, null, null, null, null);

        FieldworkDtos.FieldworkOverview overview = service.overview(QUESTIONNAIRE_ID, 1, filter);

        assertThat(overview.target()).isEqualTo(19);
    }

    @Test
    void showsTheTargetOfAFilteredInterviewerWithoutInterviews() {
        InterviewFilter filter = new InterviewFilter(null, null, null, null, "enq3", null, null);
        when(repository.activityByInterviewer(QUESTIONNAIRE_ID, 1L, filter)).thenReturn(List.of());

        FieldworkDtos.FieldworkOverview overview = service.overview(QUESTIONNAIRE_ID, 1, filter);

        assertThat(overview.target()).isEqualTo(5);
        assertThat(overview.teams()).flatExtracting(FieldworkDtos.TeamProgress::interviewers)
                .extracting(FieldworkDtos.InterviewerProgress::interviewer).containsExactly("enq3");
    }

    @Test
    void returnsNoRateWithoutNumericTarget() {
        assertThat(FieldworkService.rate(3, 0)).isNull();
    }

    private InterviewerActivity activity(String interviewer, String supervisor, long total, long completed,
            long approved, long rejected, long inProgress, Double medianSeconds, long activeDays) {
        return new InterviewerActivity(interviewer, supervisor, total, completed, approved, rejected,
                inProgress, 0, 0, medianSeconds, activeDays, null, null);
    }
}
