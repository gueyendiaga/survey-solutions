package com.surveysolutions.analytics.dashboard;

import static com.surveysolutions.analytics.dashboard.DashboardDtos.*;
import static com.surveysolutions.analytics.surveysolutions.SurveySolutionsDtos.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class DashboardMetricsCalculator {
    public Overview calculate(
            List<Questionnaire> questionnaires,
            List<Interview> interviews,
            int supervisorCount,
            Questionnaire selectedQuestionnaire) {
        Set<String> questionnaireIds = new HashSet<>();
        questionnaires.forEach(q -> questionnaireIds.add(q.questionnaireId()));

        Set<String> responsibleIds = new HashSet<>();
        Map<String, Long> statusCounts = new HashMap<>();
        Map<LocalDate, Long> dailyUpdates = new HashMap<>();
        for (Interview interview : interviews) {
            if (interview.responsibleId() != null) {
                responsibleIds.add(interview.responsibleId());
            }
            statusCounts.merge(
                    interview.status() == null ? "Inconnu" : interview.status(),
                    1L,
                    Long::sum);
            if (interview.lastEntryDate() != null && interview.lastEntryDate().length() >= 10) {
                LocalDate date = LocalDate.parse(interview.lastEntryDate().substring(0, 10));
                dailyUpdates.merge(date, 1L, Long::sum);
            }
        }

        List<StatusCount> statuses = statusCounts.entrySet().stream()
                .map(entry -> new StatusCount(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingLong(StatusCount::count).reversed()
                        .thenComparing(StatusCount::status))
                .toList();

        LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);
        List<TrendPoint> updates = new ArrayList<>();
        for (int day = 13; day >= 0; day--) {
            LocalDate date = today.minusDays(day);
            updates.add(new TrendPoint(date, dailyUpdates.getOrDefault(date, 0L)));
        }

        return new Overview(
                java.time.Instant.now(),
                questionnaires.stream().map(QuestionnaireSummary::from).toList(),
                questionnaireIds.size(),
                interviews.size(),
                responsibleIds.size(),
                supervisorCount,
                selectedQuestionnaire == null
                        ? null
                        : QuestionnaireSummary.from(selectedQuestionnaire),
                statuses,
                updates);
    }
}
