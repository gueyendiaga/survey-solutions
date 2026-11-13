package com.surveysolutions.analytics.surveysolutions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import com.surveysolutions.analytics.config.HqApiUserPrincipal;
import com.surveysolutions.analytics.config.SurveySolutionsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.web.client.RestClient;

class SurveySolutionsServiceTest {
    @Test
    void usesTheLoggedInUsersCredentialsForHeadquartersRequests() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SurveySolutionsProperties properties = new SurveySolutionsProperties();
        properties.setBaseUrl("http://hq.test");
        properties.setWorkspace("primary");
        HqApiUserPrincipal principal = new HqApiUserPrincipal("api-user", "secret");
        SessionRegistryImpl sessions = new SessionRegistryImpl();
        sessions.registerNewSession("active-session", principal);
        server.expect(requestTo("http://hq.test/primary/api/v1/questionnaires?limit=40&offset=1"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Basic YXBpLXVzZXI6c2VjcmV0"))
                .andRespond(withSuccess(questionnairePage(1, 1), MediaType.APPLICATION_JSON));
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, java.util.List.of()));
        try {
            SurveySolutionsService service = new SurveySolutionsService(
                    new SurveySolutionsClientFactory(builder, properties, sessions));

            assertThat(service.getQuestionnaires()).hasSize(1);
        } finally {
            SecurityContextHolder.clearContext();
        }
        server.verify();
    }

    @Test
    void refusesToReuseCredentialsAfterTheSessionIsInvalidated() {
        RestClient.Builder builder = RestClient.builder();
        SurveySolutionsProperties properties = new SurveySolutionsProperties();
        properties.setBaseUrl("http://hq.test");
        properties.setWorkspace("primary");
        HqApiUserPrincipal principal = new HqApiUserPrincipal("api-user", "secret");
        SessionRegistryImpl sessions = new SessionRegistryImpl();
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, java.util.List.of()));
        try {
            SurveySolutionsService service = new SurveySolutionsService(
                    new SurveySolutionsClientFactory(builder, properties, sessions));

            org.assertj.core.api.Assertions.assertThatThrownBy(service::getQuestionnaires)
                    .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void verifiesTheSubmittedUserAgainstTheHeadquartersApi() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://hq.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://hq.test/api/v1/questionnaires?limit=1&offset=1"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Basic YXBpLXVzZXI6c2VjcmV0"))
                .andRespond(withSuccess(questionnairePage(1, 1), MediaType.APPLICATION_JSON));

        assertThat(new SurveySolutionsService(builder.build()).isApiUser("api-user", "secret")).isTrue();
        server.verify();
    }

    @Test
    void rejectsHeadquartersCredentialsThatAreNotAuthorizedForTheApi() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://hq.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://hq.test/api/v1/questionnaires?limit=1&offset=1"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThat(new SurveySolutionsService(builder.build()).isApiUser("reader", "secret")).isFalse();
        server.verify();
    }

    @Test
    void downloadsInterviewTranscriptFromHeadquarters() {
        UUID interviewId = UUID.fromString("b60cb2fe-10c0-47c9-9d0d-ca2e69dd2ce1");
        byte[] pdf = "%PDF- transcript".getBytes(StandardCharsets.UTF_8);
        RestClient.Builder builder = RestClient.builder().baseUrl("http://hq.test/primary");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://hq.test/primary/api/v1/interviews/" + interviewId + "/pdf"))
                .andRespond(withSuccess(pdf, MediaType.APPLICATION_PDF)
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=51-40-70-47.pdf"));

        var transcript = new SurveySolutionsService(builder.build()).getInterviewTranscript(interviewId);

        assertThat(transcript.filename()).isEqualTo("51-40-70-47.pdf");
        assertThat(transcript.content()).isEqualTo(pdf);
        server.verify();
    }

    @Test
    void loadsInterviewHistoryAndStatisticsFromHeadquarters() {
        UUID interviewId = UUID.fromString("b60cb2fe-10c0-47c9-9d0d-ca2e69dd2ce1");
        RestClient.Builder builder = RestClient.builder().baseUrl("http://hq.test/primary");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://hq.test/primary/api/v1/interviews/" + interviewId + "/history"))
                .andRespond(withSuccess("""
                        {"InterviewId":"%s","QuestionnaireId":"588daf2d-0c72-4b89-bec9-254459a68983",
                         "QuestionnaireVersion":1,"Records":[{"Index":3,"Action":"CommentSet",
                         "OriginatorName":"Reviewer","OriginatorRole":"Headquarters",
                         "Parameters":{"Comment":"Vérifier cette réponse"},
                         "Timestamp":"2026-11-12T10:00:00Z","Offset":"00:10:00"}]}
                        """.formatted(interviewId), MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://hq.test/primary/api/v1/interviews/" + interviewId + "/stats"))
                .andRespond(withSuccess("""
                        {"Answered":12,"NotAnswered":2,"Flagged":1,"Invalid":1,"WithComments":2,
                         "InterviewId":"%s","InterviewKey":"01-02-03-04","Status":"Completed",
                         "ResponsibleName":"Interviewer","NumberOfInterviewers":1,
                         "InterviewDuration":"00:18:00","UpdatedAtUtc":"2026-11-12T10:00:00Z"}
                        """.formatted(interviewId), MediaType.APPLICATION_JSON));
        SurveySolutionsService service = new SurveySolutionsService(builder.build());

        var history = service.getInterviewHistory(interviewId);
        var stats = service.getInterviewStatistics(interviewId);

        assertThat(history.records()).hasSize(1);
        assertThat(history.records().get(0).action()).isEqualTo("CommentSet");
        assertThat(history.records().get(0).parameters()).containsEntry("Comment", "Vérifier cette réponse");
        assertThat(stats.answered()).isEqualTo(12);
        assertThat(stats.withComments()).isEqualTo(2);
        assertThat(stats.responsibleName()).isEqualTo("Interviewer");
        server.verify();
    }

    @Test
    void postsAQuestionCommentWithItsRosterVector() {
        UUID interviewId = UUID.fromString("b60cb2fe-10c0-47c9-9d0d-ca2e69dd2ce1");
        RestClient.Builder builder = RestClient.builder().baseUrl("http://hq.test/primary");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://hq.test/primary/api/v1/interviews/" + interviewId
                        + "/comment-by-variable/income?comment=Please%20check&rosterVector=1&rosterVector=4"))
                .andRespond(withSuccess());

        new SurveySolutionsService(builder.build())
                .addInterviewComment(interviewId, "income", java.util.List.of(1, 4), "Please check");

        server.verify();
    }

    @Test
    void mapsTheDocumentedQuestionnaireEnvelopeAndFollowsPageNumbers() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://hq.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://hq.test/api/v1/questionnaires?limit=40&offset=1"))
                .andRespond(withSuccess(questionnairePage(1, 2), MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://hq.test/api/v1/questionnaires?limit=40&offset=2"))
                .andRespond(withSuccess(questionnairePage(2, 2), MediaType.APPLICATION_JSON));

        SurveySolutionsService service = new SurveySolutionsService(builder.build());

        assertThat(service.getQuestionnaires())
                .extracting(SurveySolutionsDtos.Questionnaire::questionnaireIdentity)
                .containsExactly("survey$1", "survey$2");
        server.verify();
    }

    @Test
    void readsInterviewStatusesUsingTheDocumentedQuestionnaireFilters() {
        UUID questionnaireId = UUID.fromString("588daf2d-0c72-4b89-bec9-254459a68983");
        RestClient.Builder builder = RestClient.builder().baseUrl("http://hq.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(
                        "http://hq.test/api/v1/interviews?pageSize=100&page=1"
                                + "&questionnaireId=" + questionnaireId + "&questionnaireVersion=1"))
                .andRespond(withSuccess(
                        """
                        {
                          "Interviews": [{
                            "InterviewId": "123e4567-e89b-12d3-a456-426614174000",
                            "QuestionnaireId": "588daf2d-0c72-4b89-bec9-254459a68983",
                            "QuestionnaireVersion": 1,
                            "ResponsibleId": "123e4567-e89b-12d3-a456-426614174001",
                            "Status": "Completed",
                            "LastEntryDate": "2026-11-03T10:38:49.13373"
                          }],
                          "TotalCount": 1,
                          "Limit": 100,
                          "Offset": 1
                        }
                        """,
                        MediaType.APPLICATION_JSON));

        var interviews = new SurveySolutionsService(builder.build())
                .getInterviews(questionnaireId, 1L);

        assertThat(interviews).hasSize(1);
        assertThat(interviews.get(0).status()).isEqualTo("Completed");
        assertThat(interviews.get(0).responsibleId())
                .isEqualTo("123e4567-e89b-12d3-a456-426614174001");
        server.verify();
    }

    @Test
    void discoversVariablesFromQuestionnaireDocumentWhenStatisticsEndpointIsEmpty() {
        UUID questionnaireId = UUID.fromString("588daf2d-0c72-4b89-bec9-254459a68983");
        RestClient.Builder builder = RestClient.builder().baseUrl("http://hq.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(
                        "http://hq.test/api/v1/statistics/questions"
                                + "?questionnaireId=" + questionnaireId + "&version=1"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        server.expect(requestTo(
                        "http://hq.test/api/v1/questionnaires/" + questionnaireId + "/1/document"))
                .andRespond(withSuccess(
                        """
                        {
                          "$type": "QuestionnaireDocument",
                          "Title": "Survey",
                          "Children": [{
                            "$type": "Group",
                            "Title": "Section A",
                            "IsRoster": true,
                            "RosterTitleQuestionId": "title-question-id",
                            "Children": [
                              {
                                "$type": "SingleQuestion",
                                "PublicKey": "question-id",
                                "VariableName": "choice",
                                "QuestionText": "Select one",
                                "QuestionType": 0,
                                "VariableLabel": "Choice",
                                "Answers": [{
                                  "AnswerCode": 1,
                                  "AnswerText": "Yes",
                                  "AnswerValue": "yes"
                                }]
                              },
                              {
                                "$type": "TextQuestion",
                                "PublicKey": "text-id",
                                "VariableName": "comment",
                                "QuestionText": "Comment",
                                "QuestionType": 7,
                                "VariableLabel": "Comment",
                                "Answers": []
                              }
                            ]
                          }]
                        }
                        """,
                        MediaType.APPLICATION_JSON));

        var questions = new SurveySolutionsService(builder.build())
                .getQuestions(questionnaireId, 1);

        assertThat(questions).hasSize(2);
        assertThat(questions.get(0).variableName()).isEqualTo("choice");
        assertThat(questions.get(0).type()).isEqualTo("SingleOption");
        assertThat(questions.get(0).breadcrumbs()).containsExactly("Section A");
        assertThat(questions.get(0).rosterTitleQuestionIds()).containsExactly("title-question-id");
        assertThat(questions.get(0).answers())
                .containsExactly(new SurveySolutionsDtos.QuestionOption(1, "Yes", "yes"));
        assertThat(questions.get(1).variableName()).isEqualTo("comment");
        assertThat(questions.get(1).type()).isEqualTo("Text");
        server.verify();
    }

    @Test
    void enrichesStatisticsQuestionsWithRosterTitleQuestionIdsFromDocument() {
        UUID questionnaireId = UUID.fromString("588daf2d-0c72-4b89-bec9-254459a68983");
        RestClient.Builder builder = RestClient.builder().baseUrl("http://hq.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(
                        "http://hq.test/api/v1/statistics/questions"
                                + "?questionnaireId=" + questionnaireId + "&version=1"))
                .andRespond(withSuccess(
                        """
                        [{
                          "Id": "question-id",
                          "VariableName": "relationship",
                          "QuestionText": "Relation to %rostertitle%",
                          "Type": "Text",
                          "Breadcrumbs": [],
                          "Answers": []
                        }]
                        """,
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(
                        "http://hq.test/api/v1/questionnaires/" + questionnaireId + "/1/document"))
                .andRespond(withSuccess(
                        """
                        {
                          "$type": "QuestionnaireDocument",
                          "Children": [{
                            "$type": "Group",
                            "Title": "Household members",
                            "IsRoster": true,
                            "RosterTitleQuestionId": "title-question-id",
                            "Children": [{
                              "$type": "TextQuestion",
                              "PublicKey": "question-id",
                              "VariableName": "relationship",
                              "QuestionText": "Relation to %rostertitle%",
                              "QuestionType": 7,
                              "Answers": []
                            }]
                          }]
                        }
                        """,
                        MediaType.APPLICATION_JSON));

        var questions = new SurveySolutionsService(builder.build())
                .getQuestions(questionnaireId, 1);

        assertThat(questions).singleElement()
                .satisfies(question -> assertThat(question.rosterTitleQuestionIds())
                        .containsExactly("title-question-id"));
        server.verify();
    }

    @Test
    void mapsNumericQuestionTypeFromOfficialQuestionnaireEnum() {
        UUID questionnaireId = UUID.fromString("588daf2d-0c72-4b89-bec9-254459a68983");
        RestClient.Builder builder = RestClient.builder().baseUrl("http://hq.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(
                        "http://hq.test/api/v1/statistics/questions"
                                + "?questionnaireId=" + questionnaireId + "&version=1"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        server.expect(requestTo(
                        "http://hq.test/api/v1/questionnaires/" + questionnaireId + "/1/document"))
                .andRespond(withSuccess(
                        """
                        {
                          "$type": "QuestionnaireDocument",
                          "Children": [{
                            "$type": "TextQuestion",
                            "PublicKey": "numeric-id",
                            "VariableName": "household_size",
                            "QuestionText": "Household size",
                            "QuestionType": 4,
                            "Answers": []
                          }]
                        }
                        """,
                        MediaType.APPLICATION_JSON));

        var questions = new SurveySolutionsService(builder.build())
                .getQuestions(questionnaireId, 1);

        assertThat(questions).singleElement()
                .satisfies(question -> assertThat(question.type()).isEqualTo("Numeric"));
        server.verify();
    }

    @Test
    void retainsStructuredAnswersAndAdditionalRosterFieldsForExport() {
        UUID interviewId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        RestClient.Builder builder = RestClient.builder().baseUrl("http://hq.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://hq.test/api/v1/interviews/" + interviewId))
                .andRespond(withSuccess(
                        """
                        {
                          "Answers": [{
                            "VariableName": "location",
                            "Answer": {"latitude": 48.8, "longitude": 2.3},
                            "RosterVector": [1, 2]
                          }]
                        }
                        """,
                        MediaType.APPLICATION_JSON));

        var details = new SurveySolutionsService(builder.build()).getInterviewDetails(interviewId);

        assertThat(details.answers()).singleElement().satisfies(answer -> {
            assertThat(answer.answer()).isInstanceOf(java.util.Map.class);
            assertThat(answer.additionalFields()).containsKey("RosterVector");
            assertThat(answer.additionalFields().get("RosterVector").toString()).isEqualTo("[1,2]");
        });
        server.verify();
    }

    @Test
    void forwardsHeadquartersActionsWithOptionalCommentsAndRejectResponsibleId() {
        UUID interviewId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        UUID responsibleId = UUID.fromString("123e4567-e89b-12d3-a456-426614174001");
        RestClient.Builder builder = RestClient.builder().baseUrl("http://hq.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://hq.test/api/v1/interviews/" + interviewId
                        + "/hqapprove?comment=Looks%20good"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                        .withSuccess());
        server.expect(requestTo("http://hq.test/api/v1/interviews/" + interviewId + "/hqunapprove"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                        .withSuccess());
        server.expect(requestTo("http://hq.test/api/v1/interviews/" + interviewId
                        + "/hqreject?comment=Needs%20revision&responsibleId=" + responsibleId))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                        .withSuccess());

        SurveySolutionsService service = new SurveySolutionsService(builder.build());

        service.approveInterviewAsHeadquarters(interviewId, "Looks good");
        service.unapproveInterviewAsHeadquarters(interviewId, null);
        service.rejectInterviewAsHeadquarters(interviewId, "Needs revision", responsibleId);

        server.verify();
    }

    @Test
    void usesAnswerValueAsOptionCodeWhenAnswerCodeIsMissing() {
        UUID questionnaireId = UUID.fromString("588daf2d-0c72-4b89-bec9-254459a68983");
        RestClient.Builder builder = RestClient.builder().baseUrl("http://hq.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(
                        "http://hq.test/api/v1/statistics/questions"
                                + "?questionnaireId=" + questionnaireId + "&version=1"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        server.expect(requestTo(
                        "http://hq.test/api/v1/questionnaires/" + questionnaireId + "/1/document"))
                .andRespond(withSuccess(
                        """
                        {
                          "$type": "QuestionnaireDocument",
                          "Children": [{
                            "$type": "MultyOptionsQuestion",
                            "PublicKey": "op-id",
                            "VariableName": "op1",
                            "QuestionText": "Operations",
                            "QuestionType": 3,
                            "Answers": [
                              {"AnswerCode": 601.0, "AnswerValue": "601", "AnswerText": "Covid"},
                              {"AnswerCode": null, "AnswerValue": "602", "AnswerText": "Census"}
                            ]
                          }]
                        }
                        """,
                        MediaType.APPLICATION_JSON));

        var questions = new SurveySolutionsService(builder.build())
                .getQuestions(questionnaireId, 1);

        assertThat(questions.get(0).answers()).containsExactly(
                new SurveySolutionsDtos.QuestionOption(601, "Covid", "601"),
                new SurveySolutionsDtos.QuestionOption(602, "Census", "602"));
        server.verify();
    }

    @Test
    void buildsDataFingerprintFromGraphqlCountAndLastUpdate() {
        UUID questionnaireId = UUID.fromString("588daf2d-0c72-4b89-bec9-254459a68983");
        RestClient.Builder builder = RestClient.builder().baseUrl("http://hq.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://hq.test/graphql"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers
                        .jsonPath("$.variables.questionnaireId").value(questionnaireId.toString()))
                .andRespond(withSuccess(
                        """
                        {"data": {"interviews": {"filteredCount": 3,
                          "nodes": [{"updateDateUtc": "2026-11-05T08:42:20.035Z"}]}}}
                        """,
                        MediaType.APPLICATION_JSON));

        String fingerprint = new SurveySolutionsService(builder.build())
                .getDataFingerprint(questionnaireId, 1);

        assertThat(fingerprint).isEqualTo("3|2026-11-05T08:42:20.035Z");
        server.verify();
    }

    @Test
    void pagesThroughAssignmentsUsingARowOffset() {
        UUID questionnaireId = UUID.fromString("588daf2d-0c72-4b89-bec9-254459a68983");
        RestClient.Builder builder = RestClient.builder().baseUrl("http://hq.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        String first = "{\"Assignments\":[" + assignment(3) + "," + assignment(4) + "],\"TotalCount\":3}";
        String second = "{\"Assignments\":[" + assignment(5) + "],\"TotalCount\":3}";
        server.expect(requestTo(org.hamcrest.Matchers.containsString("Offset=0")))
                .andRespond(withSuccess(first, MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("Offset=2")))
                .andRespond(withSuccess(second, MediaType.APPLICATION_JSON));

        var assignments = new SurveySolutionsService(builder.build()).getAssignments(questionnaireId, 1);

        assertThat(assignments).extracting(SurveySolutionsDtos.Assignment::id).containsExactly(3, 4, 5);
        server.verify();
    }

    private String assignment(int id) {
        return "{\"Id\":" + id + ",\"ResponsibleName\":\"enq\",\"Quantity\":2,"
                + "\"InterviewsCount\":1,\"Archived\":false}";
    }

    private String questionnairePage(int page, int totalCount) {
        return """
                {
                  "Questionnaires": [{
                    "QuestionnaireIdentity": "survey$%d",
                    "QuestionnaireId": "588daf2d-0c72-4b89-bec9-254459a68983",
                    "Version": %d,
                    "Title": "Test",
                    "Variable": "test",
                    "LastEntryDate": "2026-11-03T10:38:49.13373",
                    "IsAudioRecordingEnabled": false,
                    "WebModeEnabled": false
                  }],
                  "Limit": 40,
                  "TotalCount": %d,
                  "Offset": %d
                }
                """.formatted(page, page, totalCount, page);
    }
}
