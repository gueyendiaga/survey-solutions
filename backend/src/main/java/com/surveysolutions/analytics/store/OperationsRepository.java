package com.surveysolutions.analytics.store;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Agrégats des opérations de terrain (tablettes, validation, zones, mode, rendez-vous). */
@Repository
public class OperationsRepository {
    private final JdbcTemplate jdbc;

    public record TabletActivity(
            String interviewer,
            String supervisor,
            boolean locked,
            Boolean deviceLinked,
            Instant lastTabletContact,
            Instant lastCompletion,
            long pendingAssignments,
            Instant oldestPendingSince) {}

    public record QueueCounts(
            String status,
            long total,
            long lessThanOneDay,
            long oneToThreeDays,
            long threeToSevenDays,
            long moreThanSevenDays) {}

    public record ReviewDelays(Double supervisorHours, Double hqHours) {}

    public record Waiting(
            UUID interviewId,
            String interviewKey,
            String interviewer,
            String supervisor,
            String status,
            Instant since) {}

    public record ZoneCounts(
            String code,
            long interviews,
            long completed,
            long approved,
            long rejected,
            long inProgress) {}

    public record ModeRow(String mode, long count) {}

    public record AppointmentRow(
            Instant startAt,
            String interviewKey,
            Integer assignmentId,
            String responsible,
            String supervisor,
            boolean completed,
            String comment) {}

    public record AppointmentCounts(long upcoming, long overdue) {}

    public OperationsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Enquêteurs ayant des interviews ou des affectations actives sur la version, avec leur
     * dernier contact tablette et leurs affectations pas encore reçues.
     */
    public List<TabletActivity> tabletActivity(UUID questionnaireId, long version, String supervisor, String interviewer) {
        List<Object> args = new ArrayList<>(List.of(questionnaireId, version));
        StringBuilder where = new StringBuilder();
        if (supervisor != null) {
            where.append(" AND t.supervisor_name = ?");
            args.add(supervisor);
        }
        if (interviewer != null) {
            where.append(" AND r.name = ?");
            args.add(interviewer);
        }
        return jdbc.query("""
                WITH p AS (SELECT CAST(? AS uuid) AS q, CAST(? AS bigint) AS v),
                relevant AS (
                    SELECT i.interviewer_name AS name FROM interview i, p
                    WHERE i.questionnaire_id = p.q AND i.version = p.v AND i.interviewer_name IS NOT NULL
                    UNION
                    SELECT a.responsible_name FROM assignment a
                    JOIN team_member tm ON tm.interviewer_name = a.responsible_name, p
                    WHERE a.questionnaire_id = p.q AND a.version = p.v AND NOT a.archived),
                interviews AS (
                    SELECT i.interviewer_name AS name,
                           max(i.received_by_tablet_at) AS tablet,
                           max(coalesce(i.last_completed_at, i.completed_at)) AS completion
                    FROM interview i, p
                    WHERE i.questionnaire_id = p.q AND i.version = p.v AND i.status <> 'Deleted'
                    GROUP BY 1),
                assignments AS (
                    SELECT a.responsible_name AS name,
                           max(a.received_by_tablet_at) AS tablet,
                           count(*) FILTER (WHERE a.received_by_tablet_at IS NULL AND NOT a.web_mode) AS pending,
                           min(a.created_at) FILTER (WHERE a.received_by_tablet_at IS NULL AND NOT a.web_mode) AS oldest
                    FROM assignment a, p
                    WHERE a.questionnaire_id = p.q AND a.version = p.v AND NOT a.archived
                    GROUP BY 1)
                SELECT r.name AS interviewer,
                       t.supervisor_name AS supervisor,
                       coalesce(t.locked, false) AS locked,
                       t.device_linked,
                       greatest(iv.tablet, asg.tablet) AS last_tablet,
                       iv.completion AS last_completion,
                       coalesce(asg.pending, 0) AS pending,
                       asg.oldest AS oldest_pending
                FROM relevant r
                LEFT JOIN team_member t ON t.interviewer_name = r.name
                LEFT JOIN interviews iv ON iv.name = r.name
                LEFT JOIN assignments asg ON asg.name = r.name
                WHERE true
                """ + where + " ORDER BY 2 NULLS LAST, 1",
                (rs, rowNum) -> new TabletActivity(
                        rs.getString("interviewer"),
                        rs.getString("supervisor"),
                        rs.getBoolean("locked"),
                        (Boolean) rs.getObject("device_linked"),
                        instant(rs, "last_tablet"),
                        instant(rs, "last_completion"),
                        rs.getLong("pending"),
                        instant(rs, "oldest_pending")),
                args.toArray());
    }

    private static final String WAITING_SINCE = """
            CASE WHEN i.status = 'Completed' THEN coalesce(i.last_completed_at, i.completed_at)
                 ELSE coalesce(i.hq_queue_since, i.supervisor_approved_at, i.last_completed_at, i.completed_at) END""";

    /** Interviews en attente de validation (Completed, ApprovedBySupervisor), par ancienneté. */
    public List<QueueCounts> validationQueue(UUID questionnaireId, long version, InterviewFilter filter) {
        InterviewFilter.Sql sql = filter.withoutStatuses().toSql();
        return jdbc.query("""
                SELECT w.status,
                       count(*) AS total,
                       count(*) FILTER (WHERE w.since >= now() - interval '1 day') AS lt1,
                       count(*) FILTER (WHERE w.since < now() - interval '1 day' AND w.since >= now() - interval '3 days') AS d1_3,
                       count(*) FILTER (WHERE w.since < now() - interval '3 days' AND w.since >= now() - interval '7 days') AS d3_7,
                       count(*) FILTER (WHERE w.since < now() - interval '7 days') AS gt7
                FROM (SELECT i.status, %s AS since
                      FROM interview i
                      WHERE i.questionnaire_id = ? AND i.version = ?
                        AND i.status IN ('Completed', 'ApprovedBySupervisor') %s) w
                GROUP BY 1
                """.formatted(WAITING_SINCE, sql.clause()),
                (rs, rowNum) -> new QueueCounts(
                        rs.getString("status"), rs.getLong("total"), rs.getLong("lt1"),
                        rs.getLong("d1_3"), rs.getLong("d3_7"), rs.getLong("gt7")),
                args(questionnaireId, version, sql));
    }

    /** Délais médians (heures) entre complétion et approbation superviseur, puis jusqu'à HQ. */
    public ReviewDelays reviewDelays(UUID questionnaireId, long version, InterviewFilter filter) {
        InterviewFilter.Sql sql = filter.withoutStatuses().toSql();
        return jdbc.queryForObject("""
                SELECT percentile_cont(0.5) WITHIN GROUP (ORDER BY
                           extract(epoch FROM i.supervisor_approved_at - coalesce(i.last_completed_at, i.completed_at)) / 3600)
                           FILTER (WHERE i.supervisor_approved_at >= coalesce(i.last_completed_at, i.completed_at)) AS supervisor_hours,
                       percentile_cont(0.5) WITHIN GROUP (ORDER BY
                           extract(epoch FROM i.hq_approved_at - greatest(i.supervisor_approved_at, coalesce(i.last_completed_at, i.completed_at))) / 3600)
                           FILTER (WHERE i.hq_approved_at >= greatest(i.supervisor_approved_at, coalesce(i.last_completed_at, i.completed_at))) AS hq_hours
                FROM interview i
                WHERE i.questionnaire_id = ? AND i.version = ?
                """ + sql.clause(),
                (rs, rowNum) -> new ReviewDelays(
                        (Double) rs.getObject("supervisor_hours"), (Double) rs.getObject("hq_hours")),
                args(questionnaireId, version, sql));
    }

    /** Interviews qui attendent une validation depuis le plus longtemps. */
    public List<Waiting> oldestWaiting(UUID questionnaireId, long version, InterviewFilter filter, int limit) {
        InterviewFilter.Sql sql = filter.withoutStatuses().toSql();
        List<Object> args = new ArrayList<>(List.of(args(questionnaireId, version, sql)));
        args.add(limit);
        return jdbc.query("""
                SELECT i.interview_id, i.interview_key, i.interviewer_name, t.supervisor_name, i.status, %s AS since
                FROM interview i
                LEFT JOIN team_member t ON t.interviewer_name = i.interviewer_name
                WHERE i.questionnaire_id = ? AND i.version = ?
                  AND i.status IN ('Completed', 'ApprovedBySupervisor') %s
                ORDER BY since NULLS LAST, i.interview_key
                LIMIT ?
                """.formatted(WAITING_SINCE, sql.clause()),
                (rs, rowNum) -> new Waiting(
                        rs.getObject("interview_id", UUID.class),
                        rs.getString("interview_key"),
                        rs.getString("interviewer_name"),
                        rs.getString("supervisor_name"),
                        rs.getString("status"),
                        instant(rs, "since")),
                args.toArray());
    }

    /** Variables renseignées au niveau principal (échantillon), pour proposer les ventilations. */
    public Set<String> mainLevelVariables(UUID questionnaireId, long version) {
        return Set.copyOf(jdbc.queryForList("""
                SELECT DISTINCT k.key
                FROM (SELECT u.answers FROM answer_unit u
                      WHERE u.questionnaire_id = ? AND u.version = ? AND u.row_key = ''
                      LIMIT 500) s,
                     jsonb_object_keys(s.answers) AS k(key)
                """, String.class, questionnaireId, version));
    }

    /** Avancement par modalité d'une variable du niveau principal (région, département…). */
    public List<ZoneCounts> zones(UUID questionnaireId, long version, String variable, InterviewFilter filter) {
        InterviewFilter.Sql sql = filter.toSql();
        List<Object> args = new ArrayList<>();
        args.add(variable);
        args.addAll(List.of(args(questionnaireId, version, sql)));
        return jdbc.query("""
                SELECT u.answers -> ? ->> 'v' AS code,
                       count(DISTINCT i.interview_id) AS interviews,
                       count(DISTINCT i.interview_id) FILTER (WHERE i.completed_at IS NOT NULL) AS completed,
                       count(DISTINCT i.interview_id) FILTER (WHERE i.status IN ('ApprovedBySupervisor', 'ApprovedByHeadquarters')) AS approved,
                       count(DISTINCT i.interview_id) FILTER (WHERE i.status IN ('RejectedBySupervisor', 'RejectedByHeadquarters')) AS rejected,
                       count(DISTINCT i.interview_id) FILTER (WHERE i.completed_at IS NULL) AS in_progress
                FROM interview i
                JOIN answer_unit u ON u.interview_id = i.interview_id AND u.row_key = ''
                WHERE i.questionnaire_id = ? AND i.version = ?
                """ + sql.clause() + " GROUP BY 1 ORDER BY 2 DESC, 1",
                (rs, rowNum) -> new ZoneCounts(
                        rs.getString("code"), rs.getLong("interviews"), rs.getLong("completed"),
                        rs.getLong("approved"), rs.getLong("rejected"), rs.getLong("in_progress")),
                args.toArray());
    }

    public List<ModeRow> modes(UUID questionnaireId, long version, InterviewFilter filter) {
        InterviewFilter.Sql sql = filter.toSql();
        return jdbc.query("""
                SELECT coalesce(i.interview_mode, 'UNKNOWN') AS mode, count(*) AS count
                FROM interview i
                WHERE i.questionnaire_id = ? AND i.version = ?
                """ + sql.clause() + " GROUP BY 1 ORDER BY 2 DESC",
                (rs, rowNum) -> new ModeRow(rs.getString("mode"), rs.getLong("count")),
                args(questionnaireId, version, sql));
    }

    /** Rendez-vous non honorés : en retard, puis à venir dans les horizonDays prochains jours. */
    public List<AppointmentRow> openAppointments(
            UUID questionnaireId, long version, String supervisor, String interviewer, int horizonDays, int limit) {
        List<Object> args = new ArrayList<>(List.of(questionnaireId, version, horizonDays));
        String where = appointmentFilter(supervisor, interviewer, args);
        args.add(limit);
        return jdbc.query("""
                SELECT a.start_at, a.interview_key, a.assignment_id, a.responsible_name, t.supervisor_name,
                       a.completed, a.comment
                FROM appointment a
                LEFT JOIN team_member t ON t.interviewer_name = a.responsible_name
                WHERE a.questionnaire_id = ? AND a.version = ? AND NOT a.completed
                  AND a.start_at < now() + make_interval(days => ?)
                """ + where + " ORDER BY a.start_at LIMIT ?",
                (rs, rowNum) -> new AppointmentRow(
                        instant(rs, "start_at"),
                        rs.getString("interview_key"),
                        (Integer) rs.getObject("assignment_id"),
                        rs.getString("responsible_name"),
                        rs.getString("supervisor_name"),
                        rs.getBoolean("completed"),
                        rs.getString("comment")),
                args.toArray());
    }

    public AppointmentCounts appointmentCounts(UUID questionnaireId, long version, String supervisor, String interviewer) {
        List<Object> args = new ArrayList<>(List.of(questionnaireId, version));
        String where = appointmentFilter(supervisor, interviewer, args);
        return jdbc.queryForObject("""
                SELECT count(*) FILTER (WHERE a.start_at >= now()) AS upcoming,
                       count(*) FILTER (WHERE a.start_at < now()) AS overdue
                FROM appointment a
                LEFT JOIN team_member t ON t.interviewer_name = a.responsible_name
                WHERE a.questionnaire_id = ? AND a.version = ? AND NOT a.completed
                """ + where,
                (rs, rowNum) -> new AppointmentCounts(rs.getLong("upcoming"), rs.getLong("overdue")),
                args.toArray());
    }

    private static String appointmentFilter(String supervisor, String interviewer, List<Object> args) {
        StringBuilder where = new StringBuilder();
        if (supervisor != null) {
            where.append(" AND coalesce(t.supervisor_name, a.responsible_name) = ?");
            args.add(supervisor);
        }
        if (interviewer != null) {
            where.append(" AND a.responsible_name = ?");
            args.add(interviewer);
        }
        return where.toString();
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Object[] args(UUID questionnaireId, long version, InterviewFilter.Sql sql) {
        List<Object> args = new ArrayList<>();
        args.add(questionnaireId);
        args.add(version);
        args.addAll(sql.args());
        return args.toArray();
    }
}
