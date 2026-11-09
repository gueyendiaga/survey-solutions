package com.surveysolutions.analytics.dashboard;

import static com.surveysolutions.analytics.surveysolutions.SurveySolutionsDtos.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.surveysolutions.analytics.questions.DataExportService;
import com.surveysolutions.analytics.questions.QuestionAnalyticsDtos;
import com.surveysolutions.analytics.questions.QuestionAnalyticsService;
import com.surveysolutions.analytics.store.AnalyticsRepository;
import com.surveysolutions.analytics.store.SyncService;
import com.surveysolutions.analytics.surveysolutions.SurveySolutionsService;

@RestController
public class AnalyticsController {
    private final DashboardService dashboard;
    private final SurveySolutionsService surveySolutions;
    private final QuestionAnalyticsService questionAnalytics;
    private final DataExportService dataExport;
    private final SyncService sync;

    public AnalyticsController(
            DashboardService dashboard,
            SurveySolutionsService surveySolutions,
            QuestionAnalyticsService questionAnalytics,
            DataExportService dataExport,
            SyncService sync) {
        this.dashboard = dashboard;
        this.surveySolutions = surveySolutions;
        this.questionAnalytics = questionAnalytics;
        this.dataExport = dataExport;
        this.sync = sync;
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
            @RequestParam String variableName) {
        return questionAnalytics.analyze(questionnaireId, version, variableName);
    }

    @GetMapping("/api/questionnaires/{questionnaireId}/{version}/question-completeness")
    public QuestionAnalyticsDtos.CompletenessOverview questionCompleteness(
            @PathVariable UUID questionnaireId,
            @PathVariable long version) {
        return questionAnalytics.completeness(questionnaireId, version);
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
