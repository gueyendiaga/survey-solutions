package com.surveysolutions.analytics.dashboard;

import static com.surveysolutions.analytics.surveysolutions.SurveySolutionsDtos.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.surveysolutions.analytics.questions.DataExportService;
import com.surveysolutions.analytics.questions.QuestionAnalyticsDtos;
import com.surveysolutions.analytics.questions.QuestionAnalyticsService;
import com.surveysolutions.analytics.fieldwork.FieldworkDtos;
import com.surveysolutions.analytics.fieldwork.FieldworkService;
import com.surveysolutions.analytics.report.ReportArchive;
import com.surveysolutions.analytics.report.ReportService;
import com.surveysolutions.analytics.store.AnalyticsRepository;
import com.surveysolutions.analytics.store.InterviewFilter;
import com.surveysolutions.analytics.store.SyncService;
import com.surveysolutions.analytics.surveysolutions.SurveySolutionsService;

@RestController
public class AnalyticsController {
    private final DashboardService dashboard;
    private final SurveySolutionsService surveySolutions;
    private final QuestionAnalyticsService questionAnalytics;
    private final DataExportService dataExport;
    private final SyncService sync;
    private final FieldworkService fieldwork;
    private final ReportService reports;
    private final ReportArchive reportArchive;

    public AnalyticsController(
            DashboardService dashboard,
            SurveySolutionsService surveySolutions,
            QuestionAnalyticsService questionAnalytics,
            DataExportService dataExport,
            SyncService sync,
            FieldworkService fieldwork,
            ReportService reports,
            ReportArchive reportArchive) {
        this.dashboard = dashboard;
        this.surveySolutions = surveySolutions;
        this.questionAnalytics = questionAnalytics;
        this.dataExport = dataExport;
        this.sync = sync;
        this.fieldwork = fieldwork;
        this.reports = reports;
        this.reportArchive = reportArchive;
    }

    @GetMapping("/api/health")
    public HealthResponse health() {
        return new HealthResponse("UP");
    }

    @GetMapping("/api/questionnaires")
    public List<DashboardDtos.QuestionnaireSummary> questionnaires() {
        return surveySolutions.getQuestionnaires().stream()
                .map(DashboardDtos.QuestionnaireSummary::from)
                .toList();
    }

    @GetMapping("/api/questionnaires/{questionnaireId}/{version}/questions")
    public List<Question> questions(
            @PathVariable UUID questionnaireId,
            @PathVariable long version) {
        return surveySolutions.getQuestions(questionnaireId, version);
    }

    @GetMapping("/api/questionnaires/{questionnaireId}/{version}/question-list")
    public List<QuestionAnalyticsDtos.QuestionSummary> questionList(
            @PathVariable UUID questionnaireId,
            @PathVariable long version) {
        return questionAnalytics.getQuestions(questionnaireId, version);
    }

    @GetMapping("/api/questionnaires/{questionnaireId}/{version}/question-analytics")
    public QuestionAnalyticsDtos.QuestionAnalytics questionAnalytics(
            @PathVariable UUID questionnaireId,
            @PathVariable long version,
            @RequestParam String variableName,
            @ModelAttribute FilterParams filter) {
        return questionAnalytics.analyze(questionnaireId, version, variableName, filter.toFilter());
    }

    @GetMapping("/api/questionnaires/{questionnaireId}/{version}/question-completeness")
    public QuestionAnalyticsDtos.CompletenessOverview questionCompleteness(
            @PathVariable UUID questionnaireId,
            @PathVariable long version,
            @ModelAttribute FilterParams filter) {
        return questionAnalytics.completeness(questionnaireId, version, filter.toFilter());
    }

    /** Suivi terrain : réalisation par rapport aux affectations, équipes, enquêteurs, tendance. */
    @GetMapping("/api/questionnaires/{questionnaireId}/{version}/fieldwork")
    public FieldworkDtos.FieldworkOverview fieldwork(
            @PathVariable UUID questionnaireId,
            @PathVariable long version,
            @ModelAttribute FilterParams filter) {
        if (version < 1) {
            throw new IllegalArgumentException("La version doit être supérieure à zéro.");
        }
        return fieldwork.overview(questionnaireId, version, filter.toFilter());
    }

    @GetMapping("/api/questionnaires/{questionnaireId}/{version}/filter-options")
    public FieldworkDtos.FilterOptions filterOptions(
            @PathVariable UUID questionnaireId,
            @PathVariable long version) {
        return fieldwork.filterOptions(questionnaireId, version);
    }

    /** Rapport PDF à la demande, avec les filtres courants. */
    @GetMapping("/api/questionnaires/{questionnaireId}/{version}/report.pdf")
    public ResponseEntity<byte[]> report(
            @PathVariable UUID questionnaireId,
            @PathVariable long version,
            @ModelAttribute FilterParams filter) {
        if (version < 1) {
            throw new IllegalArgumentException("La version doit être supérieure à zéro.");
        }
        return pdf(reports.generate(questionnaireId, version, filter.toFilter()));
    }

    /** Rapports archivés (générés automatiquement chaque jour). */
    @GetMapping("/api/questionnaires/{questionnaireId}/{version}/reports")
    public List<ReportArchive.ArchivedReport> archivedReports(
            @PathVariable UUID questionnaireId,
            @PathVariable long version) {
        return reportArchive.list(questionnaireId, version);
    }

    @GetMapping("/api/reports/{id}")
    public ResponseEntity<byte[]> archivedReport(@PathVariable long id) {
        return reportArchive.content(id)
                .map(this::pdf)
                .orElseThrow(() -> new IllegalArgumentException("Ce rapport n'existe pas ou a été supprimé."));
    }

    private ResponseEntity<byte[]> pdf(ReportService.ReportFile file) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.filename(), StandardCharsets.UTF_8).build().toString())
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .body(file.content());
    }

    /**
     * Filtres globaux en paramètres de requête : statuses (liste séparée par des virgules),
     * from et to (AAAA-MM-JJ, date de complétion), supervisor, interviewer, variable et value.
     */
    public record FilterParams(
            List<String> statuses,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            String supervisor,
            String interviewer,
            String variable,
            String value) {
        InterviewFilter toFilter() {
            return new InterviewFilter(statuses, from, to, supervisor, interviewer, variable, value);
        }
    }

    @GetMapping("/api/questionnaires/{questionnaireId}/{version}/export")
    public ResponseEntity<byte[]> export(
            @PathVariable UUID questionnaireId,
            @PathVariable long version,
            @RequestParam String format) {
        DataExportService.ExportFile file = dataExport.export(
                questionnaireId, version, DataExportService.Format.parse(format));
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(file.filename(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .contentType(file.contentType())
                .body(file.content());
    }

    @GetMapping("/api/questionnaires/{questionnaireId}/{version}/sync")
    public SyncStatus syncStatus(
            @PathVariable UUID questionnaireId,
            @PathVariable long version) {
        return syncStatusOf(questionnaireId, version);
    }

    /** Déclenche une synchronisation manuelle (export HQ puis import). */
    @PostMapping("/api/questionnaires/{questionnaireId}/{version}/sync")
    public ResponseEntity<SyncStatus> startSync(
            @PathVariable UUID questionnaireId,
            @PathVariable long version) {
        if (version < 1) {
            throw new IllegalArgumentException("La version doit être supérieure à zéro.");
        }
        sync.requestSync(questionnaireId, version, SyncService.Trigger.MANUAL);
        return ResponseEntity.accepted().body(syncStatusOf(questionnaireId, version));
    }

    private SyncStatus syncStatusOf(UUID questionnaireId, long version) {
        Optional<AnalyticsRepository.SyncRun> latest = sync.latestSync(questionnaireId, version);
        Optional<AnalyticsRepository.SyncRun> success = sync.latestSuccessfulSync(questionnaireId, version);
        boolean running = sync.isPending(questionnaireId, version);
        AnalyticsRepository.SyncRun last = latest.orElse(null);
        return new SyncStatus(
                running,
                success.map(AnalyticsRepository.SyncRun::finishedAt).orElse(null),
                success.map(AnalyticsRepository.SyncRun::interviewCount).orElse(null),
                last == null ? null : last.status(),
                last != null && "FAILED".equals(last.status()) ? last.error() : null);
    }

    /**
     * @param inProgress     synchronisation en attente ou en cours
     * @param lastSuccessAt  fin de la dernière synchronisation réussie (fraîcheur des données)
     * @param lastStatus     état de la dernière tentative (RUNNING, SUCCEEDED, FAILED)
     * @param lastError      message de la dernière tentative si elle a échoué
     */
    public record SyncStatus(
            boolean inProgress,
            java.time.Instant lastSuccessAt,
            Integer interviewCount,
            String lastStatus,
            String lastError) {}

    @GetMapping("/api/dashboard")
    public DashboardDtos.Overview dashboard(
            @RequestParam(required = false) UUID questionnaireId,
            @RequestParam(required = false) Long version) {
        return dashboard.getOverview(questionnaireId, version);
    }

    public record HealthResponse(String status) {}
}
