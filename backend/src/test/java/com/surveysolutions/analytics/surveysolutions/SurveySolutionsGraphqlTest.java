package com.surveysolutions.analytics.surveysolutions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class SurveySolutionsGraphqlTest {
    private static final UUID QUESTIONNAIRE_ID = UUID.fromString("588daf2d-0c72-4b89-bec9-254459a68983");

    @Test
    void readsInterviewModesAndAppointmentsPageByPage() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://hq.test/primary");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://hq.test/primary/graphql"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.variables.skip").value(0))
                .andRespond(withSuccess("""
                        {"data":{"interviews":{"filteredCount":2,"nodes":[
                          {"key":"01-50-90-18","interviewMode":"CAPI","receivedByInterviewerAtUtc":"2026-11-03T10:00:00Z",
                           "responsibleName":"enq1","calendarEvent":{"startUtc":"2026-11-20T09:00:00Z","isCompleted":false,"comment":"Rappeler"}}]}}}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://hq.test/primary/graphql"))
                .andExpect(jsonPath("$.variables.skip").value(1))
                .andRespond(withSuccess("""
                        {"data":{"interviews":{"filteredCount":2,"nodes":[
                          {"key":"02-11-22-33","interviewMode":"CAWI","receivedByInterviewerAtUtc":null,
                           "responsibleName":"enq2","calendarEvent":null}]}}}
                        """, MediaType.APPLICATION_JSON));

        var metadata = new SurveySolutionsService(builder.build()).getInterviewMetadata(QUESTIONNAIRE_ID, 1);

        assertThat(metadata).hasSize(2);
        assertThat(metadata.get(0).mode()).isEqualTo("CAPI");
        assertThat(metadata.get(0).receivedByInterviewerAt()).isEqualTo(Instant.parse("2026-11-03T10:00:00Z"));
        assertThat(metadata.get(0).calendar().startAt()).isEqualTo(Instant.parse("2026-11-20T09:00:00Z"));
        assertThat(metadata.get(0).calendar().comment()).isEqualTo("Rappeler");
        assertThat(metadata.get(1).mode()).isEqualTo("CAWI");
        assertThat(metadata.get(1).calendar()).isNull();
        server.verify();
    }

    @Test
    void keepsOnlyIdentifyingVariablesInQuestionnaireOrder() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://hq.test/primary");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://hq.test/primary/graphql"))
                .andRespond(withSuccess("""
                        {"data":{"questionnaireItems":[
                          {"variable":"region","identifying":true,"entityType":"QUESTION"},
                          {"variable":"age","identifying":false,"entityType":"QUESTION"},
                          {"variable":null,"identifying":false,"entityType":"SECTION"},
                          {"variable":"commune","identifying":true,"entityType":"QUESTION"}]}}
                        """, MediaType.APPLICATION_JSON));

        assertThat(new SurveySolutionsService(builder.build()).getIdentifyingVariables(QUESTIONNAIRE_ID, 1))
                .containsExactly("region", "commune");
        server.verify();
    }

    @Test
    void reportsGraphqlErrors() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://hq.test/primary");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://hq.test/primary/graphql"))
                .andRespond(withSuccess("""
                        {"errors":[{"message":"The current user is not authorized"}]}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> new SurveySolutionsService(builder.build()).getAssignmentCalendars(QUESTIONNAIRE_ID, 1))
                .hasMessageContaining("not authorized");
    }
}
