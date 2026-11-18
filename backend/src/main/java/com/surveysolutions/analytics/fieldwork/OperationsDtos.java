package com.surveysolutions.analytics.fieldwork;

import java.time.Instant;
import java.util.List;

/** Opérations de terrain : tablettes, file de validation, zones, mode de collecte, rendez-vous. */
public final class OperationsDtos {
    private OperationsDtos() {}

    /**
     * @param inactivityDays seuil (jours) au-delà duquel un enquêteur est signalé sans activité
     */
    public record FieldOperations(
            Instant syncedAt,
            int inactivityDays,
            TabletSummary tablets,
            List<InterviewerTablet> interviewers,
            ValidationQueue validation,
            ZoneBreakdown zones,
            List<ModeCount> modes,
            Appointments appointments) {}

    public record TabletSummary(
            long interviewers,
            long active,
            long inactive,
            long neverActive,
            long pendingAssignments,
            long withoutDevice,
            long locked) {}

    /**
     * @param lastTabletContact dernière réception d'une affectation ou d'une interview par la tablette
     * @param lastCompletion    dernière interview terminée
     * @param state             ACTIVE, INACTIVE (aucune activité depuis inactivityDays), NEVER, LOCKED
     */
    public record InterviewerTablet(
            String interviewer,
            String supervisor,
            Instant lastTabletContact,
            Instant lastCompletion,
            Long daysSinceActivity,
            long pendingAssignments,
            Instant oldestPendingSince,
            boolean locked,
            Boolean deviceLinked,
            String state) {}

    /**
     * Interviews terminées en attente du superviseur, et approuvées par le superviseur en attente
     * de HQ, avec leur ancienneté et les délais médians de validation.
     */
    public record ValidationQueue(
            QueueStage supervisor,
            QueueStage headquarters,
            Double medianSupervisorReviewHours,
            Double medianHqReviewHours,
            List<WaitingInterview> oldest) {}

    public record QueueStage(
            long total,
            long lessThanOneDay,
            long oneToThreeDays,
            long threeToSevenDays,
            long moreThanSevenDays) {}

    public record WaitingInterview(
            String interviewId,
            String interviewKey,
            String interviewer,
            String supervisor,
            String status,
            Instant since,
            Double waitingDays) {}

    /**
     * @param variable   variable de ventilation retenue (null si aucune n'est disponible)
     * @param candidates variables à choix unique du niveau principal, questions d'identification d'abord
     */
    public record ZoneBreakdown(
            String variable,
            List<ZoneOption> candidates,
            List<ZoneProgress> rows) {}

    public record ZoneOption(String variable, String label, boolean identifying) {}

    public record ZoneProgress(
            String code,
            String label,
            long interviews,
            long completed,
            long approved,
            long rejected,
            long inProgress,
            Double approvalRate) {}

    /** mode : CAPI, CAWI ou UNKNOWN. */
    public record ModeCount(String mode, long count) {}

    public record Appointments(long upcoming, long overdue, List<AppointmentItem> items) {}

    public record AppointmentItem(
            Instant startAt,
            String interviewKey,
            Integer assignmentId,
            String responsible,
            String supervisor,
            boolean overdue,
            String comment) {}
}
