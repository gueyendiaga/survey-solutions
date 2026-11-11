package com.surveysolutions.analytics.dashboard;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.surveysolutions.analytics.store.AnalyticsRepository;
import com.surveysolutions.analytics.surveysolutions.SurveySolutionsService;

@Service
public class InterviewActionService {
    private static final Logger logger = LoggerFactory.getLogger(InterviewActionService.class);

    private final AnalyticsRepository repository;
    private final SurveySolutionsService surveySolutions;

    public InterviewActionService(
            AnalyticsRepository repository,
            SurveySolutionsService surveySolutions) {
        this.repository = repository;
        this.surveySolutions = surveySolutions;
    }

    public ActionResult approve(UUID questionnaireId, long version, UUID interviewId, String comment) {
        validateComment(comment);
        requireStatus(questionnaireId, version, interviewId, "Completed", "ApprovedBySupervisor");
        surveySolutions.approveInterviewAsHeadquarters(interviewId, comment);
        updateStatus(questionnaireId, version, interviewId, "ApprovedByHeadquarters");
        audit("approve", questionnaireId, version, interviewId);
        return new ActionResult("ApprovedByHeadquarters");
    }

    public ActionResult reject(
            UUID questionnaireId,
            long version,
            UUID interviewId,
            String comment,
            UUID responsibleId) {
        validateComment(comment);
        requireStatus(questionnaireId, version, interviewId, "Completed", "ApprovedBySupervisor");
        surveySolutions.rejectInterviewAsHeadquarters(interviewId, comment, responsibleId);
        updateStatus(questionnaireId, version, interviewId, "RejectedByHeadquarters");
        audit("reject", questionnaireId, version, interviewId);
        return new ActionResult("RejectedByHeadquarters");
    }

    public ActionResult unapprove(UUID questionnaireId, long version, UUID interviewId, String comment) {
        validateComment(comment);
        requireStatus(questionnaireId, version, interviewId, "ApprovedByHeadquarters");
        surveySolutions.unapproveInterviewAsHeadquarters(interviewId, comment);
        updateStatus(questionnaireId, version, interviewId, "ApprovedBySupervisor");
        audit("unapprove", questionnaireId, version, interviewId);
        return new ActionResult("ApprovedBySupervisor");
    }

    private void requireStatus(
            UUID questionnaireId,
            long version,
            UUID interviewId,
            String... allowedStatuses) {
        String currentStatus = repository.interviewStatus(interviewId, questionnaireId, version)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Cette interview n’est pas présente dans la version synchronisée."));
        for (String allowed : allowedStatuses) {
            if (allowed.equals(currentStatus)) {
                return;
            }
        }
        throw new InterviewActionConflictException(
                "L’action n’est pas autorisée pour le statut actuel de l’interview.");
    }

    private void updateStatus(UUID questionnaireId, long version, UUID interviewId, String status) {
        if (repository.updateInterviewStatus(interviewId, questionnaireId, version, status) != 1) {
            throw new IllegalStateException(
                    "HQ a traité l’action, mais le statut local de l’interview n’a pas pu être actualisé.");
        }
    }

    private void validateComment(String comment) {
        if (comment != null && comment.length() > 2_000) {
            throw new IllegalArgumentException("Le commentaire ne peut pas dépasser 2000 caractères.");
        }
    }

    private void audit(String action, UUID questionnaireId, long version, UUID interviewId) {
        logger.info("Headquarters interview action={} questionnaire={} version={} interview={}",
                action, questionnaireId, version, interviewId);
    }

    public record ActionResult(String status) {}
}
