package com.surveysolutions.analytics.report;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.surveysolutions.analytics.config.SurveySolutionsProperties;
import com.surveysolutions.analytics.store.InterviewFilter;

/**
 * Génère chaque jour (ANALYTICS_REPORT_CRON) le rapport complet de chaque version ayant des
 * interviews, et l'archive en base. Un échec sur une version n'empêche pas les suivantes.
 */
@Component
public class ReportScheduler {
    private static final Logger LOG = LoggerFactory.getLogger(ReportScheduler.class);
    private static final InterviewFilter NO_FILTER = new InterviewFilter(null, null, null, null, null, null, null);

    private final ReportService reports;
    private final ReportArchive archive;
    private final int retention;

    public ReportScheduler(ReportService reports, ReportArchive archive, SurveySolutionsProperties properties) {
        this.reports = reports;
        this.archive = archive;
        this.retention = properties.getReportRetention();
    }

    @Scheduled(cron = "${survey-solutions.report-cron:0 0 7 * * *}", zone = "Africa/Dakar")
    public void generateDailyReports() {
        for (ReportArchive.VersionRef version : archive.versionsWithInterviews()) {
            try {
                ReportService.ReportFile file =
                        reports.generate(version.questionnaireId(), version.version(), NO_FILTER);
                archive.save(version.questionnaireId(), version.version(), "SCHEDULED", file, Instant.now());
                archive.prune(version.questionnaireId(), version.version(), retention);
                LOG.info("Rapport automatique archivé : {}", file.filename());
            } catch (RuntimeException exception) {
                LOG.warn("Rapport automatique impossible pour {} v{} : {}",
                        version.questionnaireId(), version.version(), exception.getMessage());
            }
        }
    }
}
