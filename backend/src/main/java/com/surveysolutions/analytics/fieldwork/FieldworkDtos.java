package com.surveysolutions.analytics.fieldwork;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class FieldworkDtos {
    private FieldworkDtos() {}

    /**
     * Suivi de collecte d'une version.
     *
     * @param target              somme des quotas des affectations actives (hors sans quota)
     * @param completed           interviews terminées au moins une fois par un enquêteur
     * @param completionRate      completed / target en %, null sans objectif chiffré
     * @param filtersApplied      vrai si période, équipe, enquêteur ou zone filtrent les interviews :
     *                            les objectifs, eux, ne sont pas filtrés
     */
    public record FieldworkOverview(
            Instant syncedAt,
            boolean filtersApplied,
            long target,
            long unlimitedAssignments,
            long completed,
            long approved,
            long rejected,
            long inProgress,
            Double completionRate,
            List<StatusCount> statuses,
            List<DailyProgress> daily,
            List<TeamProgress> teams) {}

    public record StatusCount(String status, long count) {}

    public record DailyProgress(LocalDate date, long completed, long cumulative) {}

    public record TeamProgress(
            String supervisor,
            long target,
            long completed,
            long approved,
            long rejected,
            long inProgress,
            long withErrors,
            Double completionRate,
            List<InterviewerProgress> interviewers) {}

    /**
     * @param medianDurationMinutes durée médiane des interviews terminées (minutes)
     * @param completedPerActiveDay interviews terminées par jour de collecte effectif
     */
    public record InterviewerProgress(
            String interviewer,
            long target,
            long total,
            long completed,
            long approved,
            long rejected,
            long inProgress,
            long withErrors,
            long rejections,
            Double completionRate,
            Double medianDurationMinutes,
            long activeDays,
            Double completedPerActiveDay,
            LocalDate lastCompletion) {}

    /** Valeurs proposées par la barre de filtres. */
    public record FilterOptions(
            List<String> supervisors,
            List<String> interviewers,
            List<String> statuses) {}
}
