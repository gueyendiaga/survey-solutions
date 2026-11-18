package com.surveysolutions.analytics.quality;

import static com.surveysolutions.analytics.quality.AutoRejectDtos.*;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Stockage des règles de rejet automatique et détection des interviews en infraction. */
@Repository
public class AutoRejectRepository {
    /** Statuts qu'HQ peut rejeter. */
    static final String REJECTABLE = "('Completed', 'ApprovedBySupervisor')";

    private final JdbcTemplate jdbc;

    public AutoRejectRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record PendingInterview(
            UUID interviewId,
            String interviewKey,
            String status,
            String responsible,
            int hqRejections,
            Instant completedAt) {}

    public record Hit(UUID interviewId, String detail) {}

    public Settings settings(UUID questionnaireId, long version) {
        List<Rule> rules = jdbc.query("""
                SELECT id, type, variable, min_value, max_value, message, enabled
                FROM auto_reject_rule WHERE questionnaire_id = ? AND version = ?
                ORDER BY position, id
                """, (rs, i) -> new Rule(
                        rs.getLong("id"),
                        RuleType.valueOf(rs.getString("type")),
                        rs.getString("variable"),
                        rs.getBigDecimal("min_value"),
                        rs.getBigDecimal("max_value"),
                        rs.getString("message"),
                        rs.getBoolean("enabled")),
                questionnaireId, version);
        return jdbc.query("""
                SELECT mode, max_hq_rejections, updated_at, updated_by
                FROM auto_reject_settings WHERE questionnaire_id = ? AND version = ?
                """, (rs, i) -> new Settings(
                        Mode.valueOf(rs.getString("mode")),
                        rs.getInt("max_hq_rejections"),
                        rules,
                        instant(rs.getTimestamp("updated_at")),
                        rs.getString("updated_by")),
                questionnaireId, version).stream().findFirst()
                .orElse(new Settings(Mode.OFF, 2, rules, null, null));
    }

    @Transactional
    public void save(UUID questionnaireId, long version, Mode mode, int maxHqRejections, List<Rule> rules, String user) {
        jdbc.update("""
                INSERT INTO auto_reject_settings (questionnaire_id, version, mode, max_hq_rejections, updated_at, updated_by)
                VALUES (?, ?, ?, ?, now(), ?)
                ON CONFLICT (questionnaire_id, version) DO UPDATE
                SET mode = EXCLUDED.mode, max_hq_rejections = EXCLUDED.max_hq_rejections,
                    updated_at = now(), updated_by = EXCLUDED.updated_by
                """, questionnaireId, version, mode.name(), maxHqRejections, user);
        jdbc.update("DELETE FROM auto_reject_rule WHERE questionnaire_id = ? AND version = ?", questionnaireId, version);
        int position = 0;
        for (Rule rule : rules) {
            jdbc.update("""
                    INSERT INTO auto_reject_rule
                        (questionnaire_id, version, position, type, variable, min_value, max_value, message, enabled)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, questionnaireId, version, position++, rule.type().name(), rule.variable(),
                    rule.minValue(), rule.maxValue(), rule.message(), rule.enabled());
        }
    }

    /** Interviews terminées ou approuvées par le superviseur : les seules qu'HQ peut rejeter. */
    public List<PendingInterview> pending(UUID questionnaireId, long version) {
        return jdbc.query("""
                SELECT interview_id, interview_key, status, interviewer_name,
                       coalesce(rejections_hq, 0) AS rejections_hq,
                       coalesce(last_completed_at, completed_at) AS completed_at
                FROM interview
                WHERE questionnaire_id = ? AND version = ? AND status IN %s
                ORDER BY interview_key
                """.formatted(REJECTABLE), (rs, i) -> new PendingInterview(
                        rs.getObject("interview_id", UUID.class),
                        rs.getString("interview_key"),
                        rs.getString("status"),
                        rs.getString("interviewer_name"),
                        rs.getInt("rejections_hq"),
                        instant(rs.getTimestamp("completed_at"))),
                questionnaireId, version);
    }

    /** Interviews en infraction avec une règle, et le détail lisible de l'infraction. */
    public List<Hit> hits(UUID questionnaireId, long version, Rule rule) {
        String pending = "i.questionnaire_id = ? AND i.version = ? AND i.status IN " + REJECTABLE;
        return switch (rule.type()) {
            case VALIDATION_ERRORS -> jdbc.query("""
                    SELECT i.interview_id, i.errors_count::text AS detail FROM interview i
                    WHERE %s AND coalesce(i.errors_count, 0) >= ?
                    """.formatted(pending), this::hit, questionnaireId, version, threshold(rule.minValue(), 1));
            case MIN_DURATION -> jdbc.query("""
                    SELECT i.interview_id, round(i.duration_seconds / 60.0)::text AS detail FROM interview i
                    WHERE %s AND i.duration_seconds IS NOT NULL AND i.duration_seconds < ? * 60
                    """.formatted(pending), this::hit, questionnaireId, version, threshold(rule.minValue(), 10));
            case MAX_UNANSWERED -> jdbc.query("""
                    SELECT i.interview_id, i.unanswered_count::text AS detail FROM interview i
                    WHERE %s AND coalesce(i.unanswered_count, 0) > ?
                    """.formatted(pending), this::hit, questionnaireId, version, threshold(rule.maxValue(), 0));
            case REQUIRED_ANSWER -> jdbc.query("""
                    SELECT i.interview_id, count(*)::text AS detail
                    FROM interview i JOIN answer_unit u ON u.interview_id = i.interview_id
                    WHERE %s AND u.answers -> ? ->> 's' = 'M'
                    GROUP BY i.interview_id
                    """.formatted(pending), this::hit, questionnaireId, version, rule.variable());
            case VALUE_RANGE -> valueRange(questionnaireId, version, rule, pending);
        };
    }

    private List<Hit> valueRange(UUID questionnaireId, long version, Rule rule, String pending) {
        String variable = rule.variable();
        List<Object> args = new ArrayList<>(List.of(variable, variable, variable, questionnaireId, version, variable));
        List<String> bounds = new ArrayList<>();
        if (rule.minValue() != null) {
            bounds.add("v.n < ?");
            args.add(rule.minValue());
        }
        if (rule.maxValue() != null) {
            bounds.add("v.n > ?");
            args.add(rule.maxValue());
        }
        if (bounds.isEmpty()) {
            return List.of();
        }
        return jdbc.query("""
                SELECT v.interview_id, string_agg(DISTINCT v.raw, ', ') AS detail
                FROM (SELECT i.interview_id, u.answers -> ? ->> 'v' AS raw,
                             CASE WHEN u.answers -> ? ->> 'v' ~ '^-?[0-9]+(\\.[0-9]+)?$'
                                  THEN (u.answers -> ? ->> 'v')::numeric END AS n
                      FROM interview i JOIN answer_unit u ON u.interview_id = i.interview_id
                      WHERE %s AND u.answers -> ? ->> 's' = 'A') v
                WHERE v.n IS NOT NULL AND (%s)
                GROUP BY v.interview_id
                """.formatted(pending, String.join(" OR ", bounds)), this::hit, args.toArray());
    }

    /** Une interview déjà rejetée automatiquement pour cette soumission ne l'est pas une seconde fois. */
    public boolean alreadyHandled(UUID interviewId, Instant completedAt) {
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM auto_reject_event
                WHERE interview_id = ? AND outcome IN ('REJECTED', 'SKIPPED_LIMIT')
                  AND completed_at IS NOT DISTINCT FROM ?
                """, Integer.class, interviewId, timestamp(completedAt));
        return count != null && count > 0;
    }

    public void recordEvent(
            UUID questionnaireId,
            long version,
            PendingInterview interview,
            String outcome,
            String reasons,
            String error) {
        jdbc.update("""
                INSERT INTO auto_reject_event
                    (questionnaire_id, version, interview_id, interview_key, completed_at, outcome, reasons, error)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, questionnaireId, version, interview.interviewId(), interview.interviewKey(),
                timestamp(interview.completedAt()), outcome, reasons, error);
    }

    public List<Event> recentEvents(UUID questionnaireId, long version, int limit) {
        return jdbc.query("""
                SELECT interview_id, interview_key, evaluated_at, outcome, reasons, error
                FROM auto_reject_event WHERE questionnaire_id = ? AND version = ?
                ORDER BY evaluated_at DESC, id DESC LIMIT ?
                """, (rs, i) -> new Event(
                        rs.getObject("interview_id", UUID.class),
                        rs.getString("interview_key"),
                        instant(rs.getTimestamp("evaluated_at")),
                        rs.getString("outcome"),
                        rs.getString("reasons"),
                        rs.getString("error")),
                questionnaireId, version, limit);
    }

    public Optional<Boolean> variableExists(UUID questionnaireId, long version, String variable) {
        Boolean exists = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM answer_unit
                               WHERE questionnaire_id = ? AND version = ? AND jsonb_exists(answers, ?))
                """, Boolean.class, questionnaireId, version, variable);
        Boolean hasData = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM answer_unit WHERE questionnaire_id = ? AND version = ?)",
                Boolean.class, questionnaireId, version);
        return Boolean.TRUE.equals(hasData) ? Optional.ofNullable(exists) : Optional.empty();
    }

    private Hit hit(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new Hit(rs.getObject("interview_id", UUID.class), rs.getString("detail"));
    }

    private static BigDecimal threshold(BigDecimal value, int fallback) {
        return value == null ? BigDecimal.valueOf(fallback) : value;
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
