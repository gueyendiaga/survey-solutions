package com.surveysolutions.analytics.report;

import static com.surveysolutions.analytics.surveysolutions.SurveySolutionsDtos.Questionnaire;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.surveysolutions.analytics.config.SurveySolutionsProperties;
import com.surveysolutions.analytics.fieldwork.FieldworkService;
import com.surveysolutions.analytics.questions.QuestionAnalyticsDtos.QuestionAnalytics;
import com.surveysolutions.analytics.questions.QuestionAnalyticsDtos.QuestionSummary;
import com.surveysolutions.analytics.questions.QuestionAnalyticsService;
import com.surveysolutions.analytics.store.InterviewFilter;
import com.surveysolutions.analytics.surveysolutions.SurveySolutionsService;

/** Rapport PDF d'une version de questionnaire : suivi terrain, qualité et indicateurs agrégés. */
@Service
public class ReportService {
    private static final Logger LOG = LoggerFactory.getLogger(ReportService.class);
    /** Nombre maximal de variables détaillées, pour garder un rapport lisible. */
    static final int MAX_VARIABLES = 60;
    /** Polices embarquées (licence OFL), dans src/main/resources/fonts. */
    private static final String[][] FONTS = {
        {"SourceSerif4-Regular.ttf", "Report Serif", "400", "NORMAL"},
        {"SourceSerif4-Semibold.ttf", "Report Serif", "600", "NORMAL"},
        {"SourceSerif4-Bold.ttf", "Report Serif", "700", "NORMAL"},
        {"SourceSerif4-It.ttf", "Report Serif", "400", "ITALIC"},
        {"IBMPlexSans-Regular.ttf", "Report Sans", "400", "NORMAL"},
        {"IBMPlexSans-SemiBold.ttf", "Report Sans", "600", "NORMAL"},
        {"IBMPlexSans-Italic.ttf", "Report Sans", "400", "ITALIC"},
        {"IBMPlexSansCondensed-SemiBold.ttf", "Report Condensed", "600", "NORMAL"},
        {"IBMPlexMono-Regular.ttf", "Report Mono", "400", "NORMAL"}
    };
    private static final DateTimeFormatter FILE_DATE =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmm").withZone(ZoneId.of("Africa/Dakar"));

    private final SurveySolutionsService surveySolutions;
    private final FieldworkService fieldwork;
    private final QuestionAnalyticsService questionAnalytics;
    private final String organization;
    private final Clock clock;

    public record ReportFile(String filename, byte[] content) {}

    @Autowired
    public ReportService(
            SurveySolutionsService surveySolutions,
            FieldworkService fieldwork,
            QuestionAnalyticsService questionAnalytics,
            SurveySolutionsProperties properties) {
        this(surveySolutions, fieldwork, questionAnalytics, properties.getReportOrganization(), Clock.systemUTC());
    }

    ReportService(
            SurveySolutionsService surveySolutions,
            FieldworkService fieldwork,
            QuestionAnalyticsService questionAnalytics,
            String organization,
            Clock clock) {
        this.surveySolutions = surveySolutions;
        this.fieldwork = fieldwork;
        this.questionAnalytics = questionAnalytics;
        this.organization = organization;
        this.clock = clock;
    }

    public ReportFile generate(UUID questionnaireId, long version, InterviewFilter filter) {
        ReportData data = collect(questionnaireId, version, filter);
        byte[] pdf = toPdf(ReportHtmlRenderer.render(data));
        String filename = "rapport-" + slug(data.questionnaireTitle()) + "-v" + version + "-"
                + FILE_DATE.format(data.generatedAt()) + ".pdf";
        return new ReportFile(filename, pdf);
    }

    ReportData collect(UUID questionnaireId, long version, InterviewFilter filter) {
        Questionnaire questionnaire = surveySolutions.getQuestionnaires().stream()
                .filter(q -> questionnaireId.toString().equalsIgnoreCase(q.questionnaireId()) && q.version() == version)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Le questionnaire ou sa version n'existe pas dans HQ."));

        var overview = fieldwork.overview(questionnaireId, version, filter);
        var completeness = questionAnalytics.completeness(questionnaireId, version, filter);

        List<QuestionSummary> questions = questionAnalytics.getQuestions(questionnaireId, version);
        List<QuestionSummary> analysable = questions.stream().filter(ReportService::isAggregatable).toList();
        List<String> excluded = questions.stream()
                .filter(question -> !isAggregatable(question))
                .map(QuestionSummary::variableName)
                .toList();
        List<QuestionAnalytics> variables = new ArrayList<>();
        for (QuestionSummary question : analysable.subList(0, Math.min(MAX_VARIABLES, analysable.size()))) {
            variables.add(questionAnalytics.analyze(questionnaireId, version, question.variableName(), filter));
        }

        return new ReportData(
                organization,
                questionnaire.title() == null || questionnaire.title().isBlank()
                        ? questionnaire.variable() : questionnaire.title(),
                version,
                Instant.now(clock),
                describe(filter),
                overview,
                completeness,
                variables,
                Math.max(0, analysable.size() - MAX_VARIABLES),
                excluded);
    }

    /**
     * Seules les variables à choix, numériques et dates sont détaillées : les réponses libres,
     * GPS ou multimédia pourraient identifier les répondants.
     */
    static boolean isAggregatable(QuestionSummary question) {
        String type = question.type() == null ? "" : question.type().toLowerCase(Locale.ROOT);
        return type.contains("single") || type.contains("multi") || type.contains("multy")
                || type.contains("numeric") || type.contains("date");
    }

    static String describe(InterviewFilter filter) {
        List<String> parts = new ArrayList<>();
        if (filter.from() != null || filter.to() != null) {
            DateTimeFormatter day = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.FRANCE);
            parts.add("interviews terminées " + (filter.from() == null ? "jusqu'au " + day.format(filter.to())
                    : filter.to() == null ? "depuis le " + day.format(filter.from())
                    : "du " + day.format(filter.from()) + " au " + day.format(filter.to())));
        }
        if (filter.supervisor() != null) {
            parts.add("équipe " + filter.supervisor());
        }
        if (filter.interviewer() != null) {
            parts.add("enquêteur " + filter.interviewer());
        }
        if (filter.variable() != null) {
            parts.add(filter.variable() + " = " + filter.value());
        }
        if (filter.statuses() != null) {
            parts.add("statuts : " + String.join(", ",
                    filter.statuses().stream().map(ReportHtmlRenderer::statusLabel).toList()));
        }
        return parts.isEmpty() ? "Toutes les interviews de la version" : String.join(" · ", parts);
    }

    private byte[] toPdf(String html) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            registerFont(builder);
            builder.withHtmlContent(html, null);
            builder.toStream(output);
            builder.run();
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Génération du rapport PDF impossible.", exception);
        }
    }

    private void registerFont(PdfRendererBuilder builder) {
        for (String[] font : FONTS) {
            String resource = "/fonts/" + font[0];
            if (ReportService.class.getResource(resource) == null) {
                LOG.warn("Police de rapport absente : {}", resource);
                continue;
            }
            builder.useFont(() -> ReportService.class.getResourceAsStream(resource), font[1],
                    Integer.parseInt(font[2]), PdfRendererBuilder.FontStyle.valueOf(font[3]), true);
        }
    }

    static String slug(String value) {
        String ascii = java.text.Normalizer.normalize(value == null ? "" : value, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (ascii.isEmpty()) {
            return "questionnaire";
        }
        return ascii.length() > 50 ? ascii.substring(0, 50).replaceAll("-$", "") : ascii;
    }
}
