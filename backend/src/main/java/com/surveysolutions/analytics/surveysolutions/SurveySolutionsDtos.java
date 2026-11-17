package com.surveysolutions.analytics.surveysolutions;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
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

    /** isLocked et deviceId ne sont renvoyés que par la liste des enquêteurs d'un superviseur. */
    public record User(
            @JsonProperty("UserId") String userId,
            @JsonProperty("UserName") String userName,
            @JsonProperty("IsLocked") Boolean isLocked,
            @JsonProperty("DeviceId") String deviceId) {
        public User(String userId, String userName) {
            this(userId, userName, null, null);
        }
    }

    /** Enquêteur d'une équipe, avec l'état de son compte et de sa tablette. */
    public record TeamMember(String interviewer, String supervisor, boolean locked, Boolean deviceLinked) {}

    public record AssignmentPage(
            @JsonProperty("Assignments") List<Assignment> assignments,
            @JsonProperty("TotalCount") int totalCount) {}

    /** quantity null ou -1 : affectation sans quota. */
    public record Assignment(
            @JsonProperty("Id") int id,
            @JsonProperty("ResponsibleName") String responsibleName,
            @JsonProperty("Quantity") Integer quantity,
            @JsonProperty("InterviewsCount") int interviewsCount,
            @JsonProperty("Archived") boolean archived,
            /** Dates HQ en UTC, sans fuseau explicite selon les versions : voir {@link HqDates}. */
            @JsonProperty("CreatedAtUtc") String createdAtUtc,
            @JsonProperty("ReceivedByTabletAtUtc") String receivedByTabletAtUtc,
            @JsonProperty("WebMode") boolean webMode,
            @JsonProperty("TargetArea") String targetArea) {
        public Assignment(int id, String responsibleName, Integer quantity, int interviewsCount, boolean archived) {
            this(id, responsibleName, quantity, interviewsCount, archived, null, null, false, null);
        }
    }

    /** Rendez-vous du calendrier HQ. */
    public record CalendarEntry(java.time.Instant startAt, boolean completed, String comment) {}

    /**
     * Métadonnées d'une interview lues par GraphQL (absentes de l'export Tabular).
     *
     * @param mode CAPI, CAWI ou UNKNOWN
     */
    public record InterviewMetadata(
            String key,
            String mode,
            java.time.Instant receivedByInterviewerAt,
            String responsibleName,
            CalendarEntry calendar) {}

    /** Rendez-vous posé sur une affectation (pas encore d'interview). */
    public record AssignmentCalendar(int assignmentId, CalendarEntry calendar) {}

    public record InterviewDetails(
            @JsonProperty("Answers") List<InterviewAnswer> answers) {}

    public record InterviewHistory(
            @JsonProperty("InterviewId") String interviewId,
            @JsonProperty("QuestionnaireId") String questionnaireId,
            @JsonProperty("QuestionnaireVersion") long questionnaireVersion,
            @JsonProperty("Records") List<InterviewHistoryRecord> records) {}

    public record InterviewHistoryRecord(
            @JsonProperty("Index") Long index,
            @JsonProperty("Action") String action,
            @JsonProperty("OriginatorName") String originatorName,
            @JsonProperty("OriginatorRole") String originatorRole,
            @JsonProperty("Parameters") Map<String, String> parameters,
            @JsonProperty("Timestamp") Instant timestamp,
            @JsonProperty("Offset") String offset) {}

    public record InterviewStatistics(
            @JsonProperty("Answered") Integer answered,
            @JsonProperty("NotAnswered") Integer notAnswered,
            @JsonProperty("Flagged") Integer flagged,
            @JsonProperty("NotFlagged") Integer notFlagged,
            @JsonProperty("Valid") Integer valid,
            @JsonProperty("Invalid") Integer invalid,
            @JsonProperty("WithComments") Integer withComments,
            @JsonProperty("ForInterviewer") Integer forInterviewer,
            @JsonProperty("ForSupervisor") Integer forSupervisor,
            @JsonProperty("InterviewId") String interviewId,
            @JsonProperty("InterviewKey") String interviewKey,
            @JsonProperty("Status") String status,
            @JsonProperty("ResponsibleId") String responsibleId,
            @JsonProperty("ResponsibleName") String responsibleName,
            @JsonProperty("NumberOfInterviewers") Integer numberOfInterviewers,
            @JsonProperty("NumberRejectionsBySupervisor") Integer numberRejectionsBySupervisor,
            @JsonProperty("NumberRejectionsByHq") Integer numberRejectionsByHq,
            @JsonProperty("InterviewDuration") String interviewDuration,
            @JsonProperty("AssignmentId") Integer assignmentId,
            @JsonProperty("UpdatedAtUtc") Instant updatedAtUtc,
            @JsonProperty("WebInterviewUrl") String webInterviewUrl) {}

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
            @JsonProperty("Answers") List<QuestionOption> answers,
            List<String> rosterTitleQuestionIds) {
        public Question(
                String id,
                String variableName,
                String questionText,
                String label,
                String type,
                List<String> breadcrumbs,
                List<QuestionOption> answers) {
            this(id, variableName, questionText, label, type, breadcrumbs, answers, List.of());
        }
    }

    public record QuestionOption(
            @JsonProperty("Answer") int answer,
            @JsonProperty("Text") String text,
            @JsonProperty("Column") String column) {}
}
