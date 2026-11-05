package com.surveysolutions.analytics.questions;

import java.math.BigDecimal;
import java.util.List;
import com.surveysolutions.analytics.surveysolutions.SurveySolutionsDtos.Question;

public final class QuestionAnalyticsDtos {
    private QuestionAnalyticsDtos() {}

    public record QuestionSummary(
            String id,
            String variableName,
            String questionText,
            String label,
            String type,
            List<String> breadcrumbs,
            int optionCount) {
        public static QuestionSummary from(Question question) {
            return new QuestionSummary(
                    question.id(),
                    question.variableName(),
                    question.questionText(),
                    question.label(),
                    question.type(),
                    question.breadcrumbs() == null ? List.of() : question.breadcrumbs(),
                    question.answers() == null ? 0 : question.answers().size());
        }
    }

    public record AnswerCount(String label, long count, double percentage) {}

    public record AnswerObservation(int interviewNumber, String value) {}

    public record QuestionCompleteness(
            QuestionSummary question,
            int answeredInterviewCount,
            int interviewWithoutAnswerCount,
            double responseRate) {}

    public record CompletenessOverview(
            int interviewCount,
            List<QuestionCompleteness> variables) {}

    public record QuestionAnalytics(
            QuestionSummary question,
            int interviewCount,
            int answeredInterviewCount,
            int interviewWithoutAnswerCount,
            long responseCount,
            List<AnswerObservation> observations,
            List<AnswerCount> distribution,
            BigDecimal minimum,
            BigDecimal maximum,
            BigDecimal mean,
            BigDecimal median,
            BigDecimal sum,
            BigDecimal percentile05,
            BigDecimal percentile95,
            int nonNumericResponseCount) {}
}
