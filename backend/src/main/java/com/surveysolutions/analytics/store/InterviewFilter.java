package com.surveysolutions.analytics.store;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Filtres globaux appliqués aux interviews (alias SQL {@code i}). Les champs null ou vides sont
 * ignorés.
 *
 * @param statuses    statuts HQ retenus ; null = tous sauf les interviews supprimées
 * @param from        date de complétion minimale (incluse, UTC)
 * @param to          date de complétion maximale (incluse, UTC)
 * @param supervisor  superviseur de l'équipe de l'enquêteur
 * @param interviewer enquêteur ayant réalisé l'interview
 * @param variable    variable de ventilation (ex. région) ; avec value, ne retient que les
 *                    interviews dont une unité a cette valeur (code de modalité)
 */
public record InterviewFilter(
        List<String> statuses,
        LocalDate from,
        LocalDate to,
        String supervisor,
        String interviewer,
        String variable,
        String value) {

    private static final Pattern VARIABLE_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,63}");

    public InterviewFilter {
        statuses = statuses == null ? null : statuses.stream()
                .map(String::trim)
                .filter(status -> !status.isEmpty())
                .toList();
        if (statuses != null && statuses.isEmpty()) {
            statuses = null;
        }
        supervisor = blankToNull(supervisor);
        interviewer = blankToNull(interviewer);
        variable = blankToNull(variable);
        value = blankToNull(value);
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("La date de début doit précéder la date de fin.");
        }
        if (variable != null && !VARIABLE_NAME.matcher(variable).matches()) {
            throw new IllegalArgumentException("Nom de variable de filtre invalide.");
        }
        if ((variable == null) != (value == null)) {
            throw new IllegalArgumentException("Le filtre par variable demande une variable et une valeur.");
        }
    }

    public static InterviewFilter withStatuses(List<String> statuses) {
        return new InterviewFilter(statuses, null, null, null, null, null, null);
    }

    public InterviewFilter withDefaultStatuses(List<String> defaults) {
        return statuses != null ? this
                : new InterviewFilter(defaults, from, to, supervisor, interviewer, variable, value);
    }

    public InterviewFilter withoutStatuses() {
        return new InterviewFilter(null, from, to, supervisor, interviewer, variable, value);
    }

    /** Fragment SQL commençant par « AND », et ses paramètres dans l'ordre. */
    public record Sql(String clause, List<Object> args) {}

    public Sql toSql() {
        StringBuilder clause = new StringBuilder();
        List<Object> args = new ArrayList<>();
        if (statuses != null) {
            clause.append(" AND i.status = ANY (?)");
            args.add(statuses.toArray(String[]::new));
        } else {
            clause.append(" AND i.status <> 'Deleted'");
        }
        if (from != null) {
            clause.append(" AND i.completed_at >= ?");
            args.add(from.atStartOfDay().atOffset(ZoneOffset.UTC));
        }
        if (to != null) {
            clause.append(" AND i.completed_at < ?");
            args.add(to.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC));
        }
        if (interviewer != null) {
            clause.append(" AND i.interviewer_name = ?");
            args.add(interviewer);
        }
        if (supervisor != null) {
            clause.append(" AND EXISTS (SELECT 1 FROM team_member tf"
                    + " WHERE tf.interviewer_name = i.interviewer_name AND tf.supervisor_name = ?)");
            args.add(supervisor);
        }
        if (variable != null) {
            clause.append(" AND EXISTS (SELECT 1 FROM answer_unit fu"
                    + " WHERE fu.interview_id = i.interview_id AND fu.answers -> ? ->> 'v' = ?)");
            args.add(variable);
            args.add(value);
        }
        return new Sql(clause.toString(), args);
    }

    public boolean narrowsPopulation() {
        return from != null || to != null || supervisor != null || interviewer != null || variable != null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
