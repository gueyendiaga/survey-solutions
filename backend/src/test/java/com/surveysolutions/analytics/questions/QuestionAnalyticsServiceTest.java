package com.surveysolutions.analytics.questions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static com.surveysolutions.analytics.surveysolutions.SurveySolutionsDtos.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import com.surveysolutions.analytics.surveysolutions.SurveySolutionsService;

class QuestionAnalyticsServiceTest {
    private static final UUID QUESTIONNAIRE_ID =
            UUID.fromString("588daf2d-0c72-4b89-bec9-254459a68983");
    private static final String VARIABLE = "test_variable";

    private final SurveySolutionsService surveySolutions = mock(SurveySolutionsService.class);
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final QuestionAnalyticsService service =
            new QuestionAnalyticsService(surveySolutions, executor);

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
                new QuestionAnalyticsDtos.AnswerObservation(1, "10"),
                new QuestionAnalyticsDtos.AnswerObservation(2, "20")), result.observations());
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
        assertEquals(List.of(
                new QuestionAnalyticsDtos.AnswerCount("A", 1, 50.0),
                new QuestionAnalyticsDtos.AnswerCount("B", 1, 50.0)), result.distribution());
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

    private Question question(String type, List<QuestionOption> options) {
        return question(VARIABLE, type, options);
    }

    private Question question(String variableName, String type, List<QuestionOption> options) {
        return new Question("question-id", variableName, "Question", "Question",
                type, List.of(), options);
    }

    private Interview interview(int id) {
        return new Interview(uuid(id).toString(), QUESTIONNAIRE_ID.toString(),
                1, null, "Completed", null);
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
