package com.surveysolutions.analytics.surveysolutions;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SurveySolutionsDtos {
    private SurveySolutionsDtos() {}

    public record QuestionnairePage(
            @JsonProperty("Questionnaires") List<Questionnaire> questionnaires,
            @JsonProperty("TotalCount") int totalCount) {}

    public record Questionnaire(
            @JsonProperty("QuestionnaireIdentity") String questionnaireIdentity,
            @JsonProperty("QuestionnaireId") String questionnaireId,
            @JsonProperty("Version") long version,
            @JsonProperty("Title") String title,
            @JsonProperty("Variable") String variable,
            @JsonProperty("LastEntryDate") String lastEntryDate,
            @JsonProperty("IsAudioRecordingEnabled") boolean audioRecordingEnabled,
            @JsonProperty("WebModeEnabled") boolean webModeEnabled) {}

    public record InterviewPage(
            @JsonProperty("Interviews") List<Interview> interviews,
            @JsonProperty("TotalCount") int totalCount) {}

    public record Interview(
            @JsonProperty("InterviewId") String interviewId,
            @JsonProperty("QuestionnaireId") String questionnaireId,
            @JsonProperty("QuestionnaireVersion") long questionnaireVersion,
            @JsonProperty("ResponsibleId") String responsibleId,
            @JsonProperty("Status") String status,
            @JsonProperty("LastEntryDate") String lastEntryDate) {}

    public record UserPage(
            @JsonProperty("Users") List<User> users,
            @JsonProperty("TotalCount") int totalCount) {}

    public record User(
            @JsonProperty("UserId") String userId,
            @JsonProperty("UserName") String userName) {}

    public record AssignmentPage(
            @JsonProperty("Assignments") List<Assignment> assignments,
            @JsonProperty("TotalCount") int totalCount) {}

    /** quantity null ou -1 : affectation sans quota. */
    public record Assignment(
            @JsonProperty("Id") int id,
            @JsonProperty("ResponsibleName") String responsibleName,
            @JsonProperty("Quantity") Integer quantity,
            @JsonProperty("InterviewsCount") int interviewsCount,
            @JsonProperty("Archived") boolean archived) {}

    public record InterviewDetails(
            @JsonProperty("Answers") List<InterviewAnswer> answers) {}

    public static final class InterviewAnswer {
        private final String variableName;
        private final Object answer;
        private final Map<String, JsonNode> additionalFields = new LinkedHashMap<>();

        @JsonCreator
        public InterviewAnswer(
                @JsonProperty("VariableName") String variableName,
                @JsonProperty("Answer") Object answer) {
            this.variableName = variableName;
            this.answer = answer;
        }

        public String variableName() {
            return variableName;
        }

        public Object answer() {
            return answer;
        }

        @JsonAnySetter
        public void addAdditionalField(String name, JsonNode value) {
            additionalFields.put(name, value);
        }

        @JsonAnyGetter
        public Map<String, JsonNode> additionalFields() {
            return Map.copyOf(additionalFields);
        }
    }

    public record Question(
            @JsonProperty("Id") String id,
            @JsonProperty("VariableName") String variableName,
            @JsonProperty("QuestionText") String questionText,
            @JsonProperty("Label") String label,
            @JsonProperty("Type") String type,
            @JsonProperty("Breadcrumbs") List<String> breadcrumbs,
            @JsonProperty("Answers") List<QuestionOption> answers) {}

    public record QuestionOption(
            @JsonProperty("Answer") int answer,
            @JsonProperty("Text") String text,
            @JsonProperty("Column") String column) {}
}
