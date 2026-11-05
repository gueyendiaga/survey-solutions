package com.surveysolutions.analytics.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static com.surveysolutions.analytics.surveysolutions.SurveySolutionsDtos.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class QuestionnaireSummarySerializationTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesQuestionnaireFieldsUsingTheAngularJsonContract() throws Exception {
        var questionnaire = new Questionnaire(
                "survey$1",
                "questionnaire-id",
                1,
                "Test questionnaire",
                "survey_variable",
                "2026-11-03T10:38:49",
                false,
                false);

        var json = objectMapper.valueToTree(
                DashboardDtos.QuestionnaireSummary.from(questionnaire));

        assertThat(json.has("questionnaireId")).isTrue();
        assertThat(json.has("questionnaireIdentity")).isTrue();
        assertThat(json.has("webModeEnabled")).isTrue();
        assertThat(json.has("QuestionnaireId")).isFalse();
    }
}
