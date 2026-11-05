package com.surveysolutions.analytics.dashboard;

import static com.surveysolutions.analytics.surveysolutions.SurveySolutionsDtos.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.surveysolutions.analytics.questions.DataExportService;
import com.surveysolutions.analytics.questions.QuestionAnalyticsDtos;
import com.surveysolutions.analytics.questions.QuestionAnalyticsService;
import com.surveysolutions.analytics.surveysolutions.SurveySolutionsService;

@RestController
public class AnalyticsController {
    private final DashboardService dashboard;
    private final SurveySolutionsService surveySolutions;
    private final QuestionAnalyticsService questionAnalytics;
    private final DataExportService dataExport;

    public AnalyticsController(
            DashboardService dashboard,
            SurveySolutionsService surveySolutions,
            QuestionAnalyticsService questionAnalytics,
            DataExportService dataExport) {
        this.dashboard = dashboard;
        this.surveySolutions = surveySolutions;
        this.questionAnalytics = questionAnalytics;
        this.dataExport = dataExport;
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

    @GetMapping("/api/dashboard")
    public DashboardDtos.Overview dashboard(
            @RequestParam(required = false) UUID questionnaireId,
            @RequestParam(required = false) Long version) {
        return dashboard.getOverview(questionnaireId, version);
    }

    public record HealthResponse(String status) {}
}
