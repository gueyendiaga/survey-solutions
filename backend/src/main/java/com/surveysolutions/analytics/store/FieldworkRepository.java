package com.surveysolutions.analytics.store;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Agrégats de suivi de collecte, calculés en SQL sur l'entrepôt. */
@Repository
public class FieldworkRepository {
    public static final String NO_INTERVIEWER = "(enquêteur inconnu)";
    public static final String NO_TEAM = "(sans équipe)";

    private final JdbcTemplate jdbc;

    /**
     * Activité d'un enquêteur. completed = interviews terminées au moins une fois (date de
     * complétion connue) ; inProgress = jamais terminées.
     */
    public record InterviewerActivity(
            String interviewer,
            String supervisor,
            long total,
            long completed,
            long approved,
            long rejected,
            long inProgress,
            long withErrors,
            long rejections,
            Double medianDurationSeconds,
            long activeDays,
            LocalDate firstCompletion,
            LocalDate lastCompletion) {}

    /** Objectif des affectations d'un responsable (enquêteur ou superviseur). */
    public record AssignmentTarget(
            String responsible,
            String supervisor,
            long target,
            long unlimitedAssignments,
            long assignments) {}

    public record DailyCount(LocalDate date, long count) {}

    public record StatusCount(String status, long count) {}

    public FieldworkRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<InterviewerActivity> activityByInterviewer(
            UUID questionnaireId, long version, InterviewFilter filter) {
        InterviewFilter.Sql sql = filter.toSql();
        return jdbc.query("""
                SELECT coalesce(i.interviewer_name, ?) AS interviewer,
                       coalesce(t.supervisor_name, ?) AS supervisor,
                       count(*) AS total,
                       count(*) FILTER (WHERE i.completed_at IS NOT NULL) AS completed,
                       count(*) FILTER (WHERE i.status IN ('ApprovedBySupervisor', 'ApprovedByHeadquarters')) AS approved,
                       count(*) FILTER (WHERE i.status IN ('RejectedBySupervisor', 'RejectedByHeadquarters')) AS rejected,
                       count(*) FILTER (WHERE i.completed_at IS NULL) AS in_progress,
                       count(*) FILTER (WHERE i.has_errors) AS with_errors,
                       coalesce(sum(coalesce(i.rejections_supervisor, 0) + coalesce(i.rejections_hq, 0)), 0) AS rejections,
                       percentile_cont(0.5) WITHIN GROUP (ORDER BY i.duration_seconds)
                           FILTER (WHERE i.completed_at IS NOT NULL AND i.duration_seconds IS NOT NULL) AS median_duration,
                       count(DISTINCT (i.completed_at AT TIME ZONE 'UTC')::date) AS active_days,
                       min((i.completed_at AT TIME ZONE 'UTC')::date) AS first_completion,
                       max((i.completed_at AT TIME ZONE 'UTC')::date) AS last_completion
                FROM interview i
                LEFT JOIN team_member t ON t.interviewer_name = i.interviewer_name
                WHERE i.questionnaire_id = ? AND i.version = ?
                """ + sql.clause() + " GROUP BY 1, 2 ORDER BY 2, 1",
                (rs, rowNum) -> new InterviewerActivity(
                        rs.getString("interviewer"),
                        rs.getString("supervisor"),
                        rs.getLong("total"),
                        rs.getLong("completed"),
                        rs.getLong("approved"),
                        rs.getLong("rejected"),
                        rs.getLong("in_progress"),
                        rs.getLong("with_errors"),
                        rs.getLong("rejections"),
                        (Double) rs.getObject("median_duration"),
                        rs.getLong("active_days"),
                        rs.getObject("first_completion", LocalDate.class),
                        rs.getObject("last_completion", LocalDate.class)),
                args(List.of(NO_INTERVIEWER, NO_TEAM), questionnaireId, version, sql));
    }

    /**
     * Objectifs par responsable d'affectation. Un responsable superviseur porte lui-même
     * l'affectation (pas encore confiée à un enquêteur).
     */
    public List<AssignmentTarget> targets(UUID questionnaireId, long version) {
        return jdbc.query("""
                SELECT a.responsible_name AS responsible,
                       coalesce(t.supervisor_name,
                                CASE WHEN EXISTS (SELECT 1 FROM team_member s WHERE s.supervisor_name = a.responsible_name)
                                     THEN a.responsible_name END, ?) AS supervisor,
                       coalesce(sum(a.quantity) FILTER (WHERE a.quantity > 0), 0) AS target,
                       count(*) FILTER (WHERE a.quantity IS NULL OR a.quantity <= 0) AS unlimited,
                       count(*) AS assignments
                FROM assignment a
                LEFT JOIN team_member t ON t.interviewer_name = a.responsible_name
                WHERE a.questionnaire_id = ? AND a.version = ? AND NOT a.archived
                GROUP BY 1, 2
                """,
                (rs, rowNum) -> new AssignmentTarget(
                        rs.getString("responsible"),
                        rs.getString("supervisor"),
                        rs.getLong("target"),
                        rs.getLong("unlimited"),
                        rs.getLong("assignments")),
                NO_TEAM, questionnaireId, version);
    }

    public List<DailyCount> completionsByDay(UUID questionnaireId, long version, InterviewFilter filter) {
        InterviewFilter.Sql sql = filter.toSql();
        return jdbc.query("""
                SELECT (i.completed_at AT TIME ZONE 'UTC')::date AS day, count(*) AS count
                FROM interview i
                WHERE i.questionnaire_id = ? AND i.version = ? AND i.completed_at IS NOT NULL
                """ + sql.clause() + " GROUP BY 1 ORDER BY 1",
                (rs, rowNum) -> new DailyCount(rs.getObject("day", LocalDate.class), rs.getLong("count")),
                args(List.of(), questionnaireId, version, sql));
    }

    public List<StatusCount> statuses(UUID questionnaireId, long version, InterviewFilter filter) {
        InterviewFilter.Sql sql = filter.toSql();
        return jdbc.query("""
                SELECT i.status, count(*) AS count
                FROM interview i
                WHERE i.questionnaire_id = ? AND i.version = ?
                """ + sql.clause() + " GROUP BY 1 ORDER BY 2 DESC, 1",
                (rs, rowNum) -> new StatusCount(rs.getString("status"), rs.getLong("count")),
                args(List.of(), questionnaireId, version, sql));
    }

    /** Enquêteurs et superviseurs présents dans la version ou dans les équipes, pour les filtres. */
    /** Enquêteurs ayant des interviews ou des affectations sur la version. */
    public List<String> interviewers(UUID questionnaireId, long version) {
        return jdbc.queryForList("""
                SELECT interviewer_name FROM interview
                WHERE questionnaire_id = ? AND version = ? AND interviewer_name IS NOT NULL
                UNION
                SELECT a.responsible_name FROM assignment a
                JOIN team_member t ON t.interviewer_name = a.responsible_name
                WHERE a.questionnaire_id = ? AND a.version = ?
                ORDER BY 1
                """, String.class, questionnaireId, version, questionnaireId, version);
    }

    public List<String> supervisors(UUID questionnaireId, long version) {
        return jdbc.queryForList("""
                SELECT t.supervisor_name FROM interview i
                JOIN team_member t ON t.interviewer_name = i.interviewer_name
                WHERE i.questionnaire_id = ? AND i.version = ?
                UNION
                SELECT coalesce(t.supervisor_name, s.supervisor_name) FROM assignment a
                LEFT JOIN team_member t ON t.interviewer_name = a.responsible_name
                LEFT JOIN (SELECT DISTINCT supervisor_name FROM team_member) s
                       ON s.supervisor_name = a.responsible_name
                WHERE a.questionnaire_id = ? AND a.version = ?
                  AND coalesce(t.supervisor_name, s.supervisor_name) IS NOT NULL
                ORDER BY 1
                """, String.class, questionnaireId, version, questionnaireId, version);
    }

    private static Object[] args(List<Object> before, UUID questionnaireId, long version, InterviewFilter.Sql sql) {
        List<Object> args = new ArrayList<>(before);
        args.add(questionnaireId);
        args.add(version);
        args.addAll(sql.args());
        return args.toArray();
    }
}
