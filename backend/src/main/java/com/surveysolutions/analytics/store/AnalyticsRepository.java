package com.surveysolutions.analytics.store;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;
import com.surveysolutions.analytics.store.TabularExportParser.AnswerState;
import com.surveysolutions.analytics.store.TabularExportParser.InterviewRecord;
import com.surveysolutions.analytics.store.TabularExportParser.UnitRecord;

@Repository
public class AnalyticsRepository {
    private static final int BATCH_SIZE = 500;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final ObjectMapper objectMapper;

    public record SyncRun(
            long id,
            UUID questionnaireId,
            long version,
            String status,
            String trigger,
            Instant startedAt,
            Instant finishedAt,
            Integer interviewCount,
            Integer unitCount,
            String hqFingerprint,
            String error) {}

    /** Une observation d'une variable : unité (interview ou ligne de roster) et son état. */
    public record VariableObservation(
            UUID interviewId,
            String interviewKey,
            String rowKey,
            String state,
            String value) {}

    public record VariableCompleteness(
            String variable,
            String level,
            long answeredUnits,
            long missingUnits,
            long disabledUnits,
            long answeredInterviews) {}

    public AnalyticsRepository(
            JdbcTemplate jdbc,
            TransactionTemplate transactions,
            ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.objectMapper = objectMapper;
    }

    // ---- Synchronisations ------------------------------------------------------------------

    public long startSync(UUID questionnaireId, long version, String trigger, String hqFingerprint) {
        Long id = jdbc.queryForObject("""
                INSERT INTO sync_run (questionnaire_id, version, status, trigger, started_at, hq_fingerprint)
                VALUES (?, ?, 'RUNNING', ?, ?, ?) RETURNING id
                """, Long.class, questionnaireId, version, trigger, Timestamp.from(Instant.now()),
                hqFingerprint);
        return id == null ? 0 : id;
    }

    public void recordExportJob(long syncId, long exportJobId) {
        jdbc.update("UPDATE sync_run SET export_job_id = ? WHERE id = ?", exportJobId, syncId);
    }

    public void finishSync(long syncId, int interviewCount, int unitCount) {
        jdbc.update("""
                UPDATE sync_run SET status = 'SUCCEEDED', finished_at = ?, interview_count = ?, unit_count = ?
                WHERE id = ?
                """, Timestamp.from(Instant.now()), interviewCount, unitCount, syncId);
    }

    public void failSync(long syncId, String error) {
        String message = error == null ? "Erreur inconnue" : error;
        jdbc.update("UPDATE sync_run SET status = 'FAILED', finished_at = ?, error = ? WHERE id = ?",
                Timestamp.from(Instant.now()),
                message.length() > 2_000 ? message.substring(0, 2_000) : message,
                syncId);
    }

    /** Un redémarrage du backend interrompt les synchronisations en cours. */
    public int failInterruptedSyncs() {
        return jdbc.update("""
                UPDATE sync_run SET status = 'FAILED', finished_at = ?,
                       error = 'Synchronisation interrompue par un redémarrage du service.'
                WHERE status = 'RUNNING'
                """, Timestamp.from(Instant.now()));
    }

    public Optional<SyncRun> latestSync(UUID questionnaireId, long version) {
        return querySync("""
                SELECT * FROM sync_run WHERE questionnaire_id = ? AND version = ?
                ORDER BY started_at DESC, id DESC LIMIT 1
                """, questionnaireId, version);
    }

    public Optional<SyncRun> latestSuccessfulSync(UUID questionnaireId, long version) {
        return querySync("""
                SELECT * FROM sync_run WHERE questionnaire_id = ? AND version = ? AND status = 'SUCCEEDED'
                ORDER BY finished_at DESC, id DESC LIMIT 1
                """, questionnaireId, version);
    }

    private Optional<SyncRun> querySync(String sql, Object... args) {
        return jdbc.query(sql, (rs, rowNum) -> new SyncRun(
                rs.getLong("id"),
                rs.getObject("questionnaire_id", UUID.class),
                rs.getLong("version"),
                rs.getString("status"),
                rs.getString("trigger"),
                rs.getTimestamp("started_at").toInstant(),
                rs.getTimestamp("finished_at") == null ? null : rs.getTimestamp("finished_at").toInstant(),
                (Integer) rs.getObject("interview_count"),
                (Integer) rs.getObject("unit_count"),
                rs.getString("hq_fingerprint"),
                rs.getString("error")), args).stream().findFirst();
    }

    // ---- Import -----------------------------------------------------------------------------

    public record ImportCounts(int interviews, int units) {}

    /**
     * Remplace atomiquement les données d'une version : les lecteurs voient l'ancienne version
     * jusqu'au commit, puis la nouvelle.
     */
    public ImportCounts replaceVersion(
            UUID questionnaireId,
            long version,
            java.util.function.Consumer<TabularExportParser.Sink> importer) {
        return transactions.execute(status -> {
            jdbc.update("DELETE FROM interview WHERE questionnaire_id = ? AND version = ?",
                    questionnaireId, version);
            BatchWriter writer = new BatchWriter(questionnaireId, version);
            importer.accept(writer);
            writer.flush();
            return new ImportCounts(writer.interviewCount, writer.unitCount);
        });
    }

    private final class BatchWriter implements TabularExportParser.Sink {
        private final UUID questionnaireId;
        private final long version;
        private final List<Object[]> interviews = new ArrayList<>();
        private final List<Object[]> units = new ArrayList<>();
        private int interviewCount;
        private int unitCount;

        private BatchWriter(UUID questionnaireId, long version) {
            this.questionnaireId = questionnaireId;
            this.version = version;
        }

        @Override
        public void interview(InterviewRecord interview) {
            interviews.add(new Object[] {
                interview.interviewId(), questionnaireId, version, interview.interviewKey(),
                interview.status(), interview.assignmentId(), interview.hasErrors(),
                interview.errorsCount(), interview.rejectionsSupervisor(), interview.rejectionsHq(),
                interview.interviewerCount(), interview.durationSeconds(), interview.unansweredCount()
            });
            interviewCount++;
            if (interviews.size() >= BATCH_SIZE) {
                flushInterviews();
            }
        }

        @Override
        public void unit(UnitRecord unit) {
            flushInterviews();
            units.add(new Object[] {
                questionnaireId, version, unit.interviewId(), unit.level(), unit.rowKey(),
                json(unit.answers())
            });
            unitCount++;
            if (units.size() >= BATCH_SIZE) {
                flushUnits();
            }
        }

        private void flush() {
            flushInterviews();
            flushUnits();
        }

        private void flushInterviews() {
            if (interviews.isEmpty()) {
                return;
            }
            jdbc.batchUpdate("""
                    INSERT INTO interview (interview_id, questionnaire_id, version, interview_key, status,
                        assignment_id, has_errors, errors_count, rejections_supervisor, rejections_hq,
                        interviewer_count, duration_seconds, unanswered_count)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, interviews, new int[] {
                        Types.OTHER, Types.OTHER, Types.BIGINT, Types.VARCHAR, Types.VARCHAR,
                        Types.INTEGER, Types.BOOLEAN, Types.INTEGER, Types.INTEGER, Types.INTEGER,
                        Types.INTEGER, Types.BIGINT, Types.INTEGER
                    });
            interviews.clear();
        }

        private void flushUnits() {
            if (units.isEmpty()) {
                return;
            }
            jdbc.batchUpdate("""
                    INSERT INTO answer_unit (questionnaire_id, version, interview_id, level, row_key, answers)
                    VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb))
                    """, units, new int[] {
                        Types.OTHER, Types.BIGINT, Types.OTHER, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR
                    });
            units.clear();
        }
    }

    private String json(Map<String, AnswerState> answers) {
        Map<String, Map<String, String>> compact = new java.util.LinkedHashMap<>();
        answers.forEach((variable, answer) -> compact.put(
                variable,
                answer.value() == null
                        ? Map.of("s", answer.state())
                        : Map.of("s", answer.state(), "v", answer.value())));
        try {
            return objectMapper.writeValueAsString(compact);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Réponses non sérialisables.", exception);
        }
    }

    // ---- Lecture ----------------------------------------------------------------------------

    public int countInterviews(UUID questionnaireId, long version, List<String> statuses) {
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM interview
                WHERE questionnaire_id = ? AND version = ? AND status = ANY (?)
                """, Integer.class, questionnaireId, version, statuses.toArray(String[]::new));
        return count == null ? 0 : count;
    }

    public int countAllInterviews(UUID questionnaireId, long version) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM interview WHERE questionnaire_id = ? AND version = ?",
                Integer.class, questionnaireId, version);
        return count == null ? 0 : count;
    }

    /** Observations d'une variable, ordonnées par interview puis ligne de roster. */
    public List<VariableObservation> observations(
            UUID questionnaireId,
            long version,
            String variable,
            List<String> statuses) {
        return jdbc.query("""
                SELECT u.interview_id, i.interview_key, u.row_key,
                       u.answers -> ? ->> 's' AS state, u.answers -> ? ->> 'v' AS value
                FROM answer_unit u
                JOIN interview i ON i.interview_id = u.interview_id
                WHERE u.questionnaire_id = ? AND u.version = ? AND jsonb_exists(u.answers, ?)
                  AND i.status = ANY (?)
                ORDER BY i.interview_key, u.row_key
                """,
                (rs, rowNum) -> new VariableObservation(
                        rs.getObject("interview_id", UUID.class),
                        rs.getString("interview_key"),
                        rs.getString("row_key"),
                        rs.getString("state"),
                        rs.getString("value")),
                variable, variable, questionnaireId, version, variable,
                statuses.toArray(String[]::new));
    }

    /** États agrégés de toutes les variables, calculés par PostgreSQL. */
    public List<VariableCompleteness> completeness(
            UUID questionnaireId,
            long version,
            List<String> statuses) {
        return jdbc.query("""
                SELECT kv.key AS variable, min(u.level) AS level,
                       count(*) FILTER (WHERE kv.value ->> 's' = 'A') AS answered_units,
                       count(*) FILTER (WHERE kv.value ->> 's' = 'M') AS missing_units,
                       count(*) FILTER (WHERE kv.value ->> 's' = 'D') AS disabled_units,
                       count(DISTINCT u.interview_id) FILTER (WHERE kv.value ->> 's' = 'A') AS answered_interviews
                FROM answer_unit u
                JOIN interview i ON i.interview_id = u.interview_id
                CROSS JOIN LATERAL jsonb_each(u.answers) kv
                WHERE u.questionnaire_id = ? AND u.version = ? AND i.status = ANY (?)
                GROUP BY kv.key
                """,
                (rs, rowNum) -> new VariableCompleteness(
                        rs.getString("variable"),
                        rs.getString("level"),
                        rs.getLong("answered_units"),
                        rs.getLong("missing_units"),
                        rs.getLong("disabled_units"),
                        rs.getLong("answered_interviews")),
                questionnaireId, version, statuses.toArray(String[]::new));
    }
}
