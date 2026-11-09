package com.surveysolutions.analytics.report;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Rapports PDF archivés en base. */
@Repository
public class ReportArchive {
    private final JdbcTemplate jdbc;

    public record ArchivedReport(
            long id,
            UUID questionnaireId,
            long version,
            Instant generatedAt,
            String trigger,
            String filename,
            int sizeBytes) {}

    public ReportArchive(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long save(UUID questionnaireId, long version, String trigger, ReportService.ReportFile file, Instant at) {
        Long id = jdbc.queryForObject("""
                INSERT INTO report (questionnaire_id, version, generated_at, trigger, filename, size_bytes, content)
                VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id
                """, Long.class, questionnaireId, version, Timestamp.from(at), trigger, file.filename(),
                file.content().length, file.content());
        return id == null ? 0 : id;
    }

    /** Ne garde que les retention rapports les plus récents de la version. */
    public int prune(UUID questionnaireId, long version, int retention) {
        return jdbc.update("""
                DELETE FROM report WHERE questionnaire_id = ? AND version = ? AND id NOT IN (
                    SELECT id FROM report WHERE questionnaire_id = ? AND version = ?
                    ORDER BY generated_at DESC, id DESC LIMIT ?)
                """, questionnaireId, version, questionnaireId, version, retention);
    }

    public List<ArchivedReport> list(UUID questionnaireId, long version) {
        return jdbc.query("""
                SELECT id, questionnaire_id, version, generated_at, trigger, filename, size_bytes
                FROM report WHERE questionnaire_id = ? AND version = ?
                ORDER BY generated_at DESC, id DESC
                """, (rs, rowNum) -> new ArchivedReport(
                        rs.getLong("id"),
                        rs.getObject("questionnaire_id", UUID.class),
                        rs.getLong("version"),
                        rs.getTimestamp("generated_at").toInstant(),
                        rs.getString("trigger"),
                        rs.getString("filename"),
                        rs.getInt("size_bytes")),
                questionnaireId, version);
    }

    public Optional<ReportService.ReportFile> content(long id) {
        return jdbc.query("SELECT filename, content FROM report WHERE id = ?",
                (rs, rowNum) -> new ReportService.ReportFile(rs.getString("filename"), rs.getBytes("content")),
                id).stream().findFirst();
    }

    public record VersionRef(UUID questionnaireId, long version) {}

    /** Versions ayant au moins une interview importée : candidates au rapport automatique. */
    public List<VersionRef> versionsWithInterviews() {
        return jdbc.query("""
                SELECT DISTINCT questionnaire_id, version FROM interview WHERE status <> 'Deleted'
                ORDER BY 1, 2
                """, (rs, rowNum) -> new VersionRef(rs.getObject("questionnaire_id", UUID.class), rs.getLong("version")));
    }
}
