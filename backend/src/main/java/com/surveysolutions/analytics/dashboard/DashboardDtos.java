package com.surveysolutions.analytics.dashboard;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import com.surveysolutions.analytics.surveysolutions.SurveySolutionsDtos.Questionnaire;

public final class DashboardDtos {
    private DashboardDtos() {}

    public record QuestionnaireSummary(
            String questionnaireIdentity,
            String questionnaireId,
            long version,
            String title,
            String variable,
            String lastEntryDate,
            boolean audioRecordingEnabled,
            boolean webModeEnabled) {
        public static QuestionnaireSummary from(Questionnaire questionnaire) {
            return new QuestionnaireSummary(
                    questionnaire.questionnaireIdentity(),
                    questionnaire.questionnaireId(),
                    questionnaire.version(),
                    questionnaire.title(),
                    questionnaire.variable(),
                    questionnaire.lastEntryDate(),
                    questionnaire.audioRecordingEnabled(),
                    questionnaire.webModeEnabled());
        }
    }

    public record Overview(
            Instant generatedAt,
            List<QuestionnaireSummary> questionnaires,
            int questionnaireCount,
            int interviewCount,
            int responsibleCount,
            int supervisorCount,
            QuestionnaireSummary selectedQuestionnaire,
            List<StatusCount> statuses,
            List<TrendPoint> updatesByDay) {}

    public record StatusCount(String status, long count) {}

    public record TrendPoint(LocalDate date, long count) {}
}
