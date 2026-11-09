package com.surveysolutions.analytics.questions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static com.surveysolutions.analytics.surveysolutions.SurveySolutionsDtos.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import com.surveysolutions.analytics.config.SurveySolutionsProperties;
import com.surveysolutions.analytics.surveysolutions.SurveySolutionsService;

class QuestionAnalyticsServiceTest {
    private static final UUID QUESTIONNAIRE_ID =
            UUID.fromString("588daf2d-0c72-4b89-bec9-254459a68983");
    private static final String VARIABLE = "test_variable";

    private final SurveySolutionsService surveySolutions = mock(SurveySolutionsService.class);
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final QuestionAnalyticsService service =
            new QuestionAnalyticsService(surveySolutions, executor, new SurveySolutionsProperties());

    @AfterEach
    void stopExecutor() {
        executor.shutdownNow();
    }

    @Test
    void computesNumericStatisticsAndCountsInterviewsWithoutAnswers() {
        Question question = question("Numeric", List.of());
        List<Interview> interviews = List.of(interview(1), interview(2), interview(3));
        when(surveySolutions.getQuestions(QUESTIONNAIRE_ID, 1)).thenReturn(List.of(question));
        when(surveySolutions.getInterviews(QUESTIONNAIRE_ID, 1L)).thenReturn(interviews);
        when(surveySolutions.getInterviewDetails(uuid(1)))
                .thenReturn(details(answer("10")));
        when(surveySolutions.getInterviewDetails(uuid(2)))
                .thenReturn(details(answer("20")));
        when(surveySolutions.getInterviewDetails(uuid(3)))
                .thenReturn(details());

        QuestionAnalyticsDtos.QuestionAnalytics result =
                service.analyze(QUESTIONNAIRE_ID, 1, VARIABLE);

        assertEquals(3, result.interviewCount());
        assertEquals(2, result.answeredInterviewCount());
        assertEquals(1, result.interviewWithoutAnswerCount());
        assertEquals(2, result.responseCount());
        assertEquals(new BigDecimal("10"), result.minimum());
        assertEquals(new BigDecimal("20"), result.maximum());
        assertEquals(new BigDecimal("15.00"), result.mean());
        assertEquals(new BigDecimal("15.00"), result.median());
        assertEquals(new BigDecimal("30"), result.sum());
        assertEquals(new BigDecimal("10"), result.percentile05());
        assertEquals(new BigDecimal("20"), result.percentile95());
        assertEquals(List.of(
                new QuestionAnalyticsDtos.AnswerObservation(1, null, "10"),
                new QuestionAnalyticsDtos.AnswerObservation(2, null, "20")), result.observations());
        assertEquals(2, result.distribution().stream().mapToLong(
                QuestionAnalyticsDtos.AnswerCount::count).sum());
    }

    @Test
    void mapsAnswerCodesToQuestionOptionLabels() {
        Question question = question("SingleOption", List.of(
                new QuestionOption(1, "Oui", null),
                new QuestionOption(2, "Non", null)));
        when(surveySolutions.getQuestions(QUESTIONNAIRE_ID, 1)).thenReturn(List.of(question));
        when(surveySolutions.getInterviews(QUESTIONNAIRE_ID, 1L))
                .thenReturn(List.of(interview(1), interview(2)));
        when(surveySolutions.getInterviewDetails(uuid(1))).thenReturn(details(answer("1")));
        when(surveySolutions.getInterviewDetails(uuid(2))).thenReturn(details(answer("2")));

        QuestionAnalyticsDtos.QuestionAnalytics result =
                service.analyze(QUESTIONNAIRE_ID, 1, VARIABLE);

        assertEquals(List.of(
                new QuestionAnalyticsDtos.AnswerCount("Non", 1, 50.0),
                new QuestionAnalyticsDtos.AnswerCount("Oui", 1, 50.0)), result.distribution());
    }

    @Test
    void mapsAnswerTextAndAnswerValueToOptionLabels() {
        Question question = question("SingleOption", List.of(
                new QuestionOption(1, "Oui", "yes"),
                new QuestionOption(2, "Non", "no")));
        when(surveySolutions.getQuestions(QUESTIONNAIRE_ID, 1)).thenReturn(List.of(question));
        when(surveySolutions.getInterviews(QUESTIONNAIRE_ID, 1L))
                .thenReturn(List.of(interview(1), interview(2)));
        when(surveySolutions.getInterviewDetails(uuid(1))).thenReturn(details(answer("OUI")));
        when(surveySolutions.getInterviewDetails(uuid(2))).thenReturn(details(answer("no")));

        QuestionAnalyticsDtos.QuestionAnalytics result =
                service.analyze(QUESTIONNAIRE_ID, 1, VARIABLE);

        assertEquals(List.of(
                new QuestionAnalyticsDtos.AnswerCount("Non", 1, 50.0),
                new QuestionAnalyticsDtos.AnswerCount("Oui", 1, 50.0)), result.distribution());
    }

    @Test
    void showsTheActualAnswerInsteadOfAnUnmappedPlaceholderWhenOptionTextDiffers() {
        String actualAnswer = "Oui, les entretiens en face à face sont totalement suspendus";
        Question question = question("SingleOption", List.of(
                new QuestionOption(1, "Yes, face-to-face interviews are fully suspended", "yes"),
                new QuestionOption(2, "No", "no")));
        when(surveySolutions.getQuestions(QUESTIONNAIRE_ID, 1)).thenReturn(List.of(question));
        when(surveySolutions.getInterviews(QUESTIONNAIRE_ID, 1L))
                .thenReturn(List.of(interview(1)));
        when(surveySolutions.getInterviewDetails(uuid(1))).thenReturn(details(answer(actualAnswer)));

        QuestionAnalyticsDtos.QuestionAnalytics result =
                service.analyze(QUESTIONNAIRE_ID, 1, VARIABLE);

        assertEquals(actualAnswer, result.observations().get(0).value());
        assertEquals(List.of(
                new QuestionAnalyticsDtos.AnswerCount(actualAnswer, 1, 100.0),
                new QuestionAnalyticsDtos.AnswerCount("No", 0, 0.0),
                new QuestionAnalyticsDtos.AnswerCount(
                        "Yes, face-to-face interviews are fully suspended", 0, 0.0)),
                result.distribution());
    }

    @Test
    void splitsRecognizedMultipleChoiceCodesBeforeCalculatingPercentages() {
        Question question = question("MultipleOption", List.of(
                new QuestionOption(1, "A", null),
                new QuestionOption(2, "B", null)));
        when(surveySolutions.getQuestions(QUESTIONNAIRE_ID, 1)).thenReturn(List.of(question));
        when(surveySolutions.getInterviews(QUESTIONNAIRE_ID, 1L))
                .thenReturn(List.of(interview(1)));
        when(surveySolutions.getInterviewDetails(uuid(1))).thenReturn(details(answer("1, 2")));

        QuestionAnalyticsDtos.QuestionAnalytics result =
                service.analyze(QUESTIONNAIRE_ID, 1, VARIABLE);

        assertEquals(1, result.responseCount());
        assertEquals(QuestionAnalyticsDtos.PercentageBase.RESPONDENTS, result.percentageBase());
        assertEquals(List.of(
                new QuestionAnalyticsDtos.AnswerCount("A", 1, 100.0),
                new QuestionAnalyticsDtos.AnswerCount("B", 1, 100.0)), result.distribution());
    }

    @Test
    void summarizesCompletenessAcrossAllVariablesWithoutCountingDuplicateAnswersTwice() {
        Question age = question("age", "Numeric", List.of());
        Question consent = question("consent", "SingleOption", List.of());
        when(surveySolutions.getQuestions(QUESTIONNAIRE_ID, 1))
                .thenReturn(List.of(age, consent));
        when(surveySolutions.getInterviews(QUESTIONNAIRE_ID, 1L))
                .thenReturn(List.of(interview(1), interview(2), interview(3)));
        when(surveySolutions.getInterviewDetails(uuid(1))).thenReturn(details(
                answer("age", "30"),
                answer("age", "31"),
                answer("consent", "yes")));
        when(surveySolutions.getInterviewDetails(uuid(2))).thenReturn(details(
                answer("age", " "),
                answer("consent", "")));
        when(surveySolutions.getInterviewDetails(uuid(3))).thenReturn(details(
                answer("age", "42")));

        QuestionAnalyticsDtos.CompletenessOverview result =
                service.completeness(QUESTIONNAIRE_ID, 1);

        assertEquals(3, result.interviewCount());
        assertEquals(List.of(
                new QuestionAnalyticsDtos.QuestionCompleteness(
                        QuestionAnalyticsDtos.QuestionSummary.from(age), 2, 1, 66.67),
                new QuestionAnalyticsDtos.QuestionCompleteness(
                        QuestionAnalyticsDtos.QuestionSummary.from(consent), 1, 2, 33.33)),
                result.variables());
    }

    @Test
    void excludesInterviewsWhoseStatusIsNotConfiguredForAnalysis() {
        Question question = question("SingleOption", List.of(new QuestionOption(1, "Oui", null)));
        when(surveySolutions.getQuestions(QUESTIONNAIRE_ID, 1)).thenReturn(List.of(question));
        when(surveySolutions.getInterviews(QUESTIONNAIRE_ID, 1L)).thenReturn(List.of(
                interview(1, "ApprovedByHeadquarters"),
                interview(2, "InterviewerAssigned"),
                interview(3, "RejectedBySupervisor")));
        when(surveySolutions.getInterviewDetails(uuid(1))).thenReturn(details(answer("1")));

        QuestionAnalyticsDtos.QuestionAnalytics result =
                service.analyze(QUESTIONNAIRE_ID, 1, VARIABLE);

        assertEquals(1, result.interviewCount());
        assertEquals(0, result.interviewWithoutAnswerCount());
        assertEquals(2, result.excludedInterviewCount());
        assertEquals(List.of("Completed", "ApprovedBySupervisor", "ApprovedByHeadquarters"),
                result.includedStatuses());
        verify(surveySolutions, never()).getInterviewDetails(uuid(2));
        verify(surveySolutions, never()).getInterviewDetails(uuid(3));
    }

    @Test
    void keepsNumericSpecialValuesOutOfDescriptiveStatistics() {
        Question question = question("Numeric", List.of(
                new QuestionOption(98, "Ne sait pas", "98"),
                new QuestionOption(99, "Refus", "99")));
        when(surveySolutions.getQuestions(QUESTIONNAIRE_ID, 1)).thenReturn(List.of(question));
        when(surveySolutions.getInterviews(QUESTIONNAIRE_ID, 1L))
                .thenReturn(List.of(interview(1), interview(2), interview(3), interview(4)));
        when(surveySolutions.getInterviewDetails(uuid(1))).thenReturn(details(answer("20")));
        when(surveySolutions.getInterviewDetails(uuid(2))).thenReturn(details(answer("40")));
        when(surveySolutions.getInterviewDetails(uuid(3))).thenReturn(details(answer("98")));
        when(surveySolutions.getInterviewDetails(uuid(4))).thenReturn(details(answer("98.0")));

        QuestionAnalyticsDtos.QuestionAnalytics result =
                service.analyze(QUESTIONNAIRE_ID, 1, VARIABLE);

        assertEquals(4, result.responseCount());
        assertEquals(new BigDecimal("30.00"), result.mean());
        assertEquals(new BigDecimal("40"), result.maximum());
        assertEquals(List.of(
                new QuestionAnalyticsDtos.AnswerCount("Ne sait pas", 2, 50.0),
                new QuestionAnalyticsDtos.AnswerCount("Refus", 0, 0.0)), result.specialValues());
        assertEquals(2, result.distribution().stream().mapToLong(
                QuestionAnalyticsDtos.AnswerCount::count).sum());
    }

    @Test
    void attachesTheHqInterviewKeyToObservationsWhenAvailable() {
        Question question = question("Text", List.of());
        when(surveySolutions.getQuestions(QUESTIONNAIRE_ID, 1)).thenReturn(List.of(question));
        when(surveySolutions.getInterviews(QUESTIONNAIRE_ID, 1L))
                .thenReturn(List.of(interview(1), interview(2)));
        when(surveySolutions.getInterviewKeys(QUESTIONNAIRE_ID, 1)).thenReturn(Map.of(
                "00000000000000000000000000000001", "01-50-90-18"));
        when(surveySolutions.getInterviewDetails(uuid(1))).thenReturn(details(answer("a")));
        when(surveySolutions.getInterviewDetails(uuid(2))).thenReturn(details(answer("b")));

        QuestionAnalyticsDtos.QuestionAnalytics result =
                service.analyze(QUESTIONNAIRE_ID, 1, VARIABLE);

        assertEquals("01-50-90-18", result.observations().get(0).interviewKey());
        assertNull(result.observations().get(1).interviewKey());
    }

    @Test
    void stillAnalyzesWhenInterviewKeysCannotBeRead() {
        Question question = question("Text", List.of());
        when(surveySolutions.getQuestions(QUESTIONNAIRE_ID, 1)).thenReturn(List.of(question));
        when(surveySolutions.getInterviews(QUESTIONNAIRE_ID, 1L)).thenReturn(List.of(interview(1)));
        when(surveySolutions.getInterviewKeys(QUESTIONNAIRE_ID, 1))
                .thenThrow(new IllegalStateException("GraphQL indisponible"));
        when(surveySolutions.getInterviewDetails(uuid(1))).thenReturn(details(answer("a")));

        QuestionAnalyticsDtos.QuestionAnalytics result =
                service.analyze(QUESTIONNAIRE_ID, 1, VARIABLE);

        assertEquals(1, result.responseCount());
        assertNull(result.observations().get(0).interviewKey());
    }

    private Question question(String type, List<QuestionOption> options) {
        return question(VARIABLE, type, options);
    }

    private Question question(String variableName, String type, List<QuestionOption> options) {
        return new Question("question-id", variableName, "Question", "Question",
                type, List.of(), options);
    }

    private Interview interview(int id) {
        return interview(id, "Completed");
    }

    private Interview interview(int id, String status) {
        return new Interview(uuid(id).toString(), QUESTIONNAIRE_ID.toString(),
                1, null, status, null);
    }

    private UUID uuid(int value) {
        return UUID.fromString("00000000-0000-0000-0000-00000000000" + value);
    }

    private InterviewDetails details(InterviewAnswer... answers) {
        return new InterviewDetails(List.of(answers));
    }

    private InterviewAnswer answer(String value) {
        return answer(VARIABLE, value);
    }

    private InterviewAnswer answer(String variableName, String value) {
        return new InterviewAnswer(variableName, value);
    }
}
