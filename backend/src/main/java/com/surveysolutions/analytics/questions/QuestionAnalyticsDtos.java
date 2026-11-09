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

    /** interviewKey : clé HQ lisible (ex. 01-50-90-18), null si HQ ne l'a pas fournie. */
    public record AnswerObservation(int interviewNumber, String interviewKey, String value) {}

    /** Base de calcul des pourcentages de la distribution. */
    public enum PercentageBase {
        /** Part des réponses (choix unique, dates) : la somme fait 100 %. */
        RESPONSES,
        /** Part des réponses non vides ayant coché la modalité (choix multiples) : la somme peut dépasser 100 %. */
        RESPONDENTS,
        /** Part des valeurs numériques valides (histogramme). */
        NUMERIC_VALUES
    }

    public record QuestionCompleteness(
            QuestionSummary question,
            int answeredInterviewCount,
            int interviewWithoutAnswerCount,
            double responseRate) {}

    public record CompletenessOverview(
            int interviewCount,
            List<QuestionCompleteness> variables,
            List<String> includedStatuses,
            int excludedInterviewCount) {}

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
            int nonNumericResponseCount,
            PercentageBase percentageBase,
            List<AnswerCount> specialValues,
            List<String> includedStatuses,
            int excludedInterviewCount) {}
}
