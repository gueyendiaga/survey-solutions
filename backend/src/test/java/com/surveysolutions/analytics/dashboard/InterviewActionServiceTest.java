package com.surveysolutions.analytics.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import com.surveysolutions.analytics.store.AnalyticsRepository;
import com.surveysolutions.analytics.surveysolutions.SurveySolutionsService;

class InterviewActionServiceTest {
    private static final UUID QUESTIONNAIRE_ID =
            UUID.fromString("588daf2d-0c72-4b89-bec9-254459a68983");
    private static final UUID INTERVIEW_ID =
            UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

    private final AnalyticsRepository repository = mock(AnalyticsRepository.class);
    private final SurveySolutionsService surveySolutions = mock(SurveySolutionsService.class);
    private final InterviewActionService service = new InterviewActionService(repository, surveySolutions);

    @Test
    void approvesOnlyCompletedOrSupervisorApprovedInterviews() {
        when(repository.interviewStatus(INTERVIEW_ID, QUESTIONNAIRE_ID, 1))
                .thenReturn(Optional.of("Completed"));
        when(repository.updateInterviewStatus(INTERVIEW_ID, QUESTIONNAIRE_ID, 1, "ApprovedByHeadquarters"))
                .thenReturn(1);

        assertThat(service.approve(QUESTIONNAIRE_ID, 1, INTERVIEW_ID, "Reviewed").status())
                .isEqualTo("ApprovedByHeadquarters");

        verify(surveySolutions).approveInterviewAsHeadquarters(INTERVIEW_ID, "Reviewed");
        verify(repository).updateInterviewStatus(
                INTERVIEW_ID, QUESTIONNAIRE_ID, 1, "ApprovedByHeadquarters");
    }

    @Test
    void rejectsActionsForAnInterviewOutsideTheSynchronizedVersion() {
        when(repository.interviewStatus(INTERVIEW_ID, QUESTIONNAIRE_ID, 1))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approve(QUESTIONNAIRE_ID, 1, INTERVIEW_ID, null))
                .isInstanceOf(IllegalArgumentException.class);

        verify(surveySolutions, never()).approveInterviewAsHeadquarters(INTERVIEW_ID, null);
    }

    @Test
    void doesNotCallHeadquartersWhenTheInterviewStatusDoesNotAllowTheAction() {
        when(repository.interviewStatus(INTERVIEW_ID, QUESTIONNAIRE_ID, 1))
                .thenReturn(Optional.of("RejectedByHeadquarters"));

        assertThatThrownBy(() -> service.approve(QUESTIONNAIRE_ID, 1, INTERVIEW_ID, null))
                .isInstanceOf(InterviewActionConflictException.class);

        verify(surveySolutions, never()).approveInterviewAsHeadquarters(INTERVIEW_ID, null);
    }

    @Test
    void removesHeadquartersApprovalBackToSupervisorApprovedStatus() {
        when(repository.interviewStatus(INTERVIEW_ID, QUESTIONNAIRE_ID, 1))
                .thenReturn(Optional.of("ApprovedByHeadquarters"));
        when(repository.updateInterviewStatus(INTERVIEW_ID, QUESTIONNAIRE_ID, 1, "ApprovedBySupervisor"))
                .thenReturn(1);

        assertThat(service.unapprove(QUESTIONNAIRE_ID, 1, INTERVIEW_ID, null).status())
                .isEqualTo("ApprovedBySupervisor");

        verify(surveySolutions).unapproveInterviewAsHeadquarters(INTERVIEW_ID, null);
    }

    @Test
    void rejectsAnInterviewAndUpdatesItsCachedStatus() {
        when(repository.interviewStatus(INTERVIEW_ID, QUESTIONNAIRE_ID, 1))
                .thenReturn(Optional.of("ApprovedBySupervisor"));
        when(repository.updateInterviewStatus(INTERVIEW_ID, QUESTIONNAIRE_ID, 1, "RejectedByHeadquarters"))
                .thenReturn(1);

        assertThat(service.reject(QUESTIONNAIRE_ID, 1, INTERVIEW_ID, "Please correct", null).status())
                .isEqualTo("RejectedByHeadquarters");

        verify(surveySolutions).rejectInterviewAsHeadquarters(INTERVIEW_ID, "Please correct", null);
    }
}
