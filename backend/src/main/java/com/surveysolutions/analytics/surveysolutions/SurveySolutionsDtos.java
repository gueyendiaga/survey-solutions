package com.surveysolutions.analytics.surveysolutions;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

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

    public record InterviewDetails(
            @JsonProperty("Answers") List<InterviewAnswer> answers) {}

    public record InterviewAnswer(
            @JsonProperty("VariableName") String variableName,
            @JsonProperty("Answer") String answer) {}

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
