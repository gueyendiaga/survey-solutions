package com.surveysolutions.analytics.dashboard;

import static com.surveysolutions.analytics.dashboard.DashboardDtos.*;
import static com.surveysolutions.analytics.surveysolutions.SurveySolutionsDtos.*;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import com.surveysolutions.analytics.surveysolutions.SurveySolutionsService;

@Service
public class DashboardService {
    private final SurveySolutionsService surveySolutions;
    private final DashboardMetricsCalculator calculator;

    public DashboardService(
            SurveySolutionsService surveySolutions,
            DashboardMetricsCalculator calculator) {
        this.surveySolutions = surveySolutions;
        this.calculator = calculator;
    }

    public Overview getOverview(UUID questionnaireId, Long version) {
        if (version != null && questionnaireId == null) {
            throw new IllegalArgumentException("Un identifiant de questionnaire est requis avec une version.");
        }
        if (version != null && version < 1) {
            throw new IllegalArgumentException("La version doit être supérieure à zéro.");
        }

        List<Questionnaire> questionnaires = surveySolutions.getQuestionnaires();
        Questionnaire selected = null;
        if (questionnaireId != null) {
            selected = questionnaires.stream()
                    .filter(q -> questionnaireId.toString().equalsIgnoreCase(q.questionnaireId()))
                    .filter(q -> version == null || version == q.version())
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Le questionnaire ou sa version n'existe pas dans HQ."));
        }

        List<Interview> interviews = surveySolutions.getInterviews(questionnaireId, version);
        int supervisors = surveySolutions.getSupervisorCount();
        return calculator.calculate(questionnaires, interviews, supervisors, selected);
    }
}
