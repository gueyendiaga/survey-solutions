package com.surveysolutions.analytics.questions;

import static com.surveysolutions.analytics.surveysolutions.SurveySolutionsDtos.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.surveysolutions.analytics.config.SurveySolutionsProperties;
import com.surveysolutions.analytics.store.AnalyticsRepository;
import com.surveysolutions.analytics.store.AnalyticsRepository.SyncRun;
import com.surveysolutions.analytics.store.AnalyticsRepository.VariableCompleteness;
import com.surveysolutions.analytics.store.AnalyticsRepository.VariableObservation;
import com.surveysolutions.analytics.store.InterviewFilter;
import com.surveysolutions.analytics.store.SyncService;
import com.surveysolutions.analytics.surveysolutions.SurveySolutionsService;

class QuestionAnalyticsServiceTest {
    private static final UUID QUESTIONNAIRE_ID =
            UUID.fromString("588daf2d-0c72-4b89-bec9-254459a68983");
    private static final String VARIABLE = "test_variable";
    private static final List<String> STATUSES =
            List.of("Completed", "ApprovedBySupervisor", "ApprovedByHeadquarters");
    private static final InterviewFilter NO_FILTER =
            new InterviewFilter(null, null, null, null, null, null, null);
    private static final Instant SYNCED_AT = Instant.parse("2026-11-09T10:00:00Z");

    private final SurveySolutionsService surveySolutions = mock(SurveySolutionsService.class);
    private final AnalyticsRepository repository = mock(AnalyticsRepository.class);
    private final SyncService sync = mock(SyncService.class);
    private final QuestionAnalyticsService service = new QuestionAnalyticsService(
            surveySolutions, repository, sync, new SurveySolutionsProperties());
    private final List<VariableObservation> observations = new ArrayList<>();

    @BeforeEach
    void synchronizedVersion() {
        when(sync.latestSuccessfulSync(QUESTIONNAIRE_ID, 1)).thenReturn(Optional.of(new SyncRun(
                1, QUESTIONNAIRE_ID, 1, "SUCCEEDED", "MANUAL", SYNCED_AT, SYNCED_AT, 3, 3, "3|x", null)));
        when(repository.observations(eq(QUESTIONNAIRE_ID), eq(1L), eq(VARIABLE), any()))
                .thenReturn(observations);
    }

    @Test
    void computesNumericStatisticsAndSeparatesMissingFromNotApplicable() {
        question("Numeric", List.of());
        interviews(4, 4);
        answered(1, "10");
        answered(2, "20");
        observe(3, "M", null);
        observe(4, "D", null);

        QuestionAnalyticsDtos.QuestionAnalytics result = service.analyze(QUESTIONNAIRE_ID, 1, VARIABLE, NO_FILTER);

        assertThat(result.interviewCount()).isEqualTo(4);
        assertThat(result.answeredInterviewCount()).isEqualTo(2);
        assertThat(result.responseCount()).isEqualTo(2);
        assertThat(result.missingCount()).isEqualTo(1);
        assertThat(result.notApplicableCount()).isEqualTo(1);
        assertThat(result.responseRate()).isEqualTo(66.67);
        assertThat(result.minimum()).isEqualTo(new BigDecimal("10"));
        assertThat(result.maximum()).isEqualTo(new BigDecimal("20"));
        assertThat(result.mean()).isEqualTo(new BigDecimal("15.00"));
        assertThat(result.median()).isEqualTo(new BigDecimal("15.00"));
        assertThat(result.sum()).isEqualTo(new BigDecimal("30"));
        assertThat(result.observations()).containsExactly(
                new QuestionAnalyticsDtos.AnswerObservation(
                        1, uuid(1).toString(), "key-1", "Completed", "", "10"),
                new QuestionAnalyticsDtos.AnswerObservation(
                        2, uuid(2).toString(), "key-2", "Completed", "", "20"));
        assertThat(result.syncedAt()).isEqualTo(SYNCED_AT);
    }

    @Test
    void mapsExportedOptionCodesToLabels() {
        question("SingleOption", List.of(
                new QuestionOption(1, "Oui", null),
                new QuestionOption(2, "Non", null)));
        interviews(2, 2);
        answered(1, "1");
        answered(2, "2");

        QuestionAnalyticsDtos.QuestionAnalytics result = service.analyze(QUESTIONNAIRE_ID, 1, VARIABLE, NO_FILTER);

        assertThat(result.distribution()).containsExactly(
                new QuestionAnalyticsDtos.AnswerCount("Non", 1, 50.0),
                new QuestionAnalyticsDtos.AnswerCount("Oui", 1, 50.0));
        assertThat(result.observations()).extracting(QuestionAnalyticsDtos.AnswerObservation::value)
                .containsExactly("Oui", "Non");
    }

    @Test
    void reportsMultipleChoicePercentagesRelativeToRespondents() {
        question("MultyOption", List.of(
                new QuestionOption(1, "A", null),
                new QuestionOption(2, "B", null)));
        interviews(2, 2);
        answered(1, "1, 2");
        answered(2, "1");

        QuestionAnalyticsDtos.QuestionAnalytics result = service.analyze(QUESTIONNAIRE_ID, 1, VARIABLE, NO_FILTER);

        assertThat(result.percentageBase()).isEqualTo(QuestionAnalyticsDtos.PercentageBase.RESPONDENTS);
        assertThat(result.distribution()).containsExactly(
                new QuestionAnalyticsDtos.AnswerCount("A", 2, 100.0),
                new QuestionAnalyticsDtos.AnswerCount("B", 1, 50.0));
        assertThat(result.observations().get(0).value()).isEqualTo("A, B");
    }

    @Test
    void keepsNumericSpecialValuesOutOfDescriptiveStatistics() {
        question("Numeric", List.of(
                new QuestionOption(98, "Ne sait pas", "98"),
                new QuestionOption(99, "Refus", "99")));
        interviews(4, 4);
        answered(1, "20");
        answered(2, "40");
        answered(3, "98");
        answered(4, "98.0");

        QuestionAnalyticsDtos.QuestionAnalytics result = service.analyze(QUESTIONNAIRE_ID, 1, VARIABLE, NO_FILTER);

        assertThat(result.responseCount()).isEqualTo(4);
        assertThat(result.mean()).isEqualTo(new BigDecimal("30.00"));
        assertThat(result.specialValues()).containsExactly(
                new QuestionAnalyticsDtos.AnswerCount("Ne sait pas", 2, 50.0),
                new QuestionAnalyticsDtos.AnswerCount("Refus", 0, 0.0));
    }

    @Test
    void countsRosterRowsAsSeparateResponsesOfTheSameInterview() {
        question("Numeric", List.of());
        interviews(1, 1);
        observations.add(new VariableObservation(uuid(1), "key-1", "Completed", "1", "A", "30"));
        observations.add(new VariableObservation(uuid(1), "key-1", "Completed", "2", "A", "8"));
        observations.add(new VariableObservation(uuid(1), "key-1", "Completed", "3", "M", null));

        QuestionAnalyticsDtos.QuestionAnalytics result = service.analyze(QUESTIONNAIRE_ID, 1, VARIABLE, NO_FILTER);

        assertThat(result.answeredInterviewCount()).isEqualTo(1);
        assertThat(result.responseCount()).isEqualTo(2);
        assertThat(result.missingCount()).isEqualTo(1);
        assertThat(result.observations()).extracting(QuestionAnalyticsDtos.AnswerObservation::rowKey)
                .containsExactly("1", "2");
    }

    @Test
    void reportsInterviewsExcludedByStatus() {
        question("Text", List.of());
        interviews(2, 5);

        QuestionAnalyticsDtos.QuestionAnalytics result = service.analyze(QUESTIONNAIRE_ID, 1, VARIABLE, NO_FILTER);

        assertThat(result.interviewCount()).isEqualTo(2);
        assertThat(result.excludedInterviewCount()).isEqualTo(3);
        assertThat(result.includedStatuses()).isEqualTo(STATUSES);
        verify(repository).observations(QUESTIONNAIRE_ID, 1L, VARIABLE, InterviewFilter.withStatuses(STATUSES));
    }

    @Test
    void requestsASynchronizationWhenNoDataHasBeenImportedYet() {
        question("Text", List.of());
        when(sync.latestSuccessfulSync(QUESTIONNAIRE_ID, 1)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.analyze(QUESTIONNAIRE_ID, 1, VARIABLE, NO_FILTER))
                .isInstanceOf(DataNotSynchronizedException.class);
        verify(sync).requestSync(QUESTIONNAIRE_ID, 1, SyncService.Trigger.ON_DEMAND);
    }

    @Test
    void computesCompletenessWithoutCountingNotApplicableAsMissing() {
        Question age = new Question("q1", "age", "Âge", "", "Numeric", List.of(), List.of());
        Question consent = new Question("q2", "consent", "Consentement", "", "SingleOption", List.of(), List.of());
        Question never = new Question("q3", "never", "Jamais exportée", "", "Text", List.of(), List.of());
        when(surveySolutions.getQuestions(QUESTIONNAIRE_ID, 1)).thenReturn(List.of(age, consent, never));
        interviews(3, 4);
        when(repository.completeness(eq(QUESTIONNAIRE_ID), eq(1L), any())).thenReturn(List.of(
                new VariableCompleteness("age", "MEMBRES", 6, 2, 1, 3),
                new VariableCompleteness("consent", "survey", 2, 0, 1, 2)));

        QuestionAnalyticsDtos.CompletenessOverview result = service.completeness(QUESTIONNAIRE_ID, 1, NO_FILTER);

        assertThat(result.interviewCount()).isEqualTo(3);
        assertThat(result.excludedInterviewCount()).isEqualTo(1);
        assertThat(result.variables()).containsExactly(
                new QuestionAnalyticsDtos.QuestionCompleteness(
                        QuestionAnalyticsDtos.QuestionSummary.from(age), "MEMBRES", 6, 2, 1, 3, 75.0),
                new QuestionAnalyticsDtos.QuestionCompleteness(
                        QuestionAnalyticsDtos.QuestionSummary.from(consent), "survey", 2, 0, 1, 2, 100.0),
                new QuestionAnalyticsDtos.QuestionCompleteness(
                        QuestionAnalyticsDtos.QuestionSummary.from(never), null, 0, 0, 0, 0, 0.0));
    }

    @Test
    void appliesRequestedFiltersOnTopOfDefaultStatuses() {
        question("Text", List.of());
        interviews(1, 3);
        InterviewFilter filter = new InterviewFilter(
                null, java.time.LocalDate.parse("2026-11-01"), null, "sup1", null, "region", "1");

        QuestionAnalyticsDtos.QuestionAnalytics result =
                service.analyze(QUESTIONNAIRE_ID, 1, VARIABLE, filter);

        InterviewFilter expected = new InterviewFilter(
                STATUSES, java.time.LocalDate.parse("2026-11-01"), null, "sup1", null, "region", "1");
        verify(repository).observations(QUESTIONNAIRE_ID, 1L, VARIABLE, expected);
        verify(repository).countInterviews(QUESTIONNAIRE_ID, 1L, expected);
        assertThat(result.excludedInterviewCount()).isEqualTo(2);
    }

    @Test
    void letsTheCallerOverrideTheAnalysedStatuses() {
        question("Text", List.of());
        interviews(1, 3);

        QuestionAnalyticsDtos.QuestionAnalytics result = service.analyze(
                QUESTIONNAIRE_ID, 1, VARIABLE, InterviewFilter.withStatuses(List.of("RejectedBySupervisor")));

        assertThat(result.includedStatuses()).containsExactly("RejectedBySupervisor");
    }

    private void question(String type, List<QuestionOption> options) {
        when(surveySolutions.getQuestions(QUESTIONNAIRE_ID, 1)).thenReturn(List.of(
                new Question("question-id", VARIABLE, "Question", "Question", type, List.of(), options)));
    }

    private void interviews(int included, int all) {
        when(repository.countInterviews(eq(QUESTIONNAIRE_ID), eq(1L), any())).thenReturn(included);
        when(repository.countAllInterviews(QUESTIONNAIRE_ID, 1L)).thenReturn(all);
    }

    private void answered(int interview, String value) {
        observe(interview, "A", value);
    }

    private void observe(int interview, String state, String value) {
        observations.add(new VariableObservation(
                uuid(interview), "key-" + interview, "Completed", "", state, value));
    }

    private UUID uuid(int value) {
        return UUID.fromString("00000000-0000-0000-0000-00000000000" + value);
    }
}
