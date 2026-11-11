package com.surveysolutions.analytics.report;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.surveysolutions.analytics.config.SurveySolutionsProperties;
import com.surveysolutions.analytics.store.InterviewFilter;
import com.surveysolutions.analytics.config.HqApiUserPrincipal;
import com.surveysolutions.analytics.surveysolutions.HqCredentialContext;
import org.springframework.security.core.session.SessionRegistry;

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
    private final SessionRegistry sessionRegistry;

    public ReportScheduler(
            ReportService reports,
            ReportArchive archive,
            SurveySolutionsProperties properties,
            SessionRegistry sessionRegistry) {
        this.reports = reports;
        this.archive = archive;
        this.retention = properties.getReportRetention();
        this.sessionRegistry = sessionRegistry;
    }

    @Scheduled(cron = "${survey-solutions.report-cron:0 0 7 * * *}", zone = "Africa/Dakar")
    public void generateDailyReports() {
        HqApiUserPrincipal principal = sessionRegistry.getAllPrincipals().stream()
                .filter(HqApiUserPrincipal.class::isInstance)
                .map(HqApiUserPrincipal.class::cast)
                .filter(user -> !sessionRegistry.getAllSessions(user, false).isEmpty())
                .findFirst()
                .orElse(null);
        if (principal == null) {
            LOG.info("Rapports automatiques ignorés : aucun utilisateur HQ connecté.");
            return;
        }
        HqCredentialContext.with(principal, this::generateDailyReportsForUser);
    }

    private void generateDailyReportsForUser() {
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
