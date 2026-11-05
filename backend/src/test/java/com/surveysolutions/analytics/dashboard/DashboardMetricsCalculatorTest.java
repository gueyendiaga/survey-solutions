package com.surveysolutions.analytics.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static com.surveysolutions.analytics.surveysolutions.SurveySolutionsDtos.*;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.junit.jupiter.api.Test;

class DashboardMetricsCalculatorTest {
    private final DashboardMetricsCalculator calculator = new DashboardMetricsCalculator();

    @Test
    void calculatesDistinctQuestionnairesResponsiblesAndObservedStatuses() {
        Questionnaire first = questionnaire("survey-1", 1);
        Questionnaire secondVersion = questionnaire("survey-1", 2);
        List<Interview> interviews = List.of(
                interview("person-1", "Completed"),
                interview("person-1", "ApprovedBySupervisor"),
                interview("person-2", "RejectedByHeadquarters"));

        DashboardDtos.Overview result =
                calculator.calculate(List.of(first, secondVersion), interviews, 3, null);

        assertThat(result.questionnaireCount()).isEqualTo(1);
        assertThat(result.interviewCount()).isEqualTo(3);
        assertThat(result.responsibleCount()).isEqualTo(2);
        assertThat(result.supervisorCount()).isEqualTo(3);
        assertThat(result.statuses())
                .containsExactlyInAnyOrder(
                        new DashboardDtos.StatusCount("Completed", 1),
                        new DashboardDtos.StatusCount("ApprovedBySupervisor", 1),
                        new DashboardDtos.StatusCount("RejectedByHeadquarters", 1));
    }

    @Test
    void countsOnlyInterviewUpdatesInTheLastFourteenUtcDays() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        List<Interview> interviews = List.of(
                interview("person-1", "Completed", today),
                interview("person-2", "Completed", today.minusDays(13)),
                interview("person-3", "Completed", today.minusDays(14)));

        DashboardDtos.Overview result = calculator.calculate(List.of(), interviews, 0, null);

        assertThat(result.updatesByDay()).hasSize(14);
        assertThat(result.updatesByDay().get(0))
                .isEqualTo(new DashboardDtos.TrendPoint(today.minusDays(13), 1));
        assertThat(result.updatesByDay().get(13))
                .isEqualTo(new DashboardDtos.TrendPoint(today, 1));
        assertThat(result.updatesByDay().stream().mapToLong(DashboardDtos.TrendPoint::count).sum())
                .isEqualTo(2);
    }

    private Questionnaire questionnaire(String id, long version) {
        return new Questionnaire(null, id, version, "Survey", "survey", null, false, false);
    }

    private Interview interview(String responsibleId, String status) {
        return interview(responsibleId, status, LocalDate.now(ZoneOffset.UTC));
    }

    private Interview interview(String responsibleId, String status, LocalDate date) {
        String timestamp = date.atStartOfDay().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        return new Interview("interview", "survey-1", 1, responsibleId, status, timestamp);
    }
}
