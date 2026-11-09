package com.surveysolutions.analytics.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import com.surveysolutions.analytics.fieldwork.FieldworkService;
import com.surveysolutions.analytics.questions.QuestionAnalyticsDtos.QuestionSummary;
import com.surveysolutions.analytics.questions.QuestionAnalyticsService;
import com.surveysolutions.analytics.store.InterviewFilter;
import com.surveysolutions.analytics.surveysolutions.SurveySolutionsDtos.Questionnaire;
import com.surveysolutions.analytics.surveysolutions.SurveySolutionsService;

class ReportServiceTest {
    private static final UUID QUESTIONNAIRE_ID = UUID.fromString("588daf2d-0c72-4b89-bec9-254459a68983");
    private static final InterviewFilter NO_FILTER = new InterviewFilter(null, null, null, null, null, null, null);

    private final SurveySolutionsService surveySolutions = mock(SurveySolutionsService.class);
    private final FieldworkService fieldwork = mock(FieldworkService.class);
    private final QuestionAnalyticsService analytics = mock(QuestionAnalyticsService.class);
    private final ReportService service = new ReportService(surveySolutions, fieldwork, analytics, "Agence test",
            Clock.fixed(ReportHtmlRendererTest.AT, ZoneOffset.UTC));

    @Test
    void producesAPdfAndSkipsFreeTextQuestions() {
        ReportData sample = ReportHtmlRendererTest.sample();
        when(surveySolutions.getQuestionnaires()).thenReturn(List.of(new Questionnaire(
                "id", QUESTIONNAIRE_ID.toString(), 3, "Enquête ménages 2026", "menages", null, false, false)));
        when(fieldwork.overview(QUESTIONNAIRE_ID, 3, NO_FILTER)).thenReturn(sample.fieldwork());
        when(analytics.completeness(QUESTIONNAIRE_ID, 3, NO_FILTER)).thenReturn(sample.completeness());
        when(analytics.getQuestions(QUESTIONNAIRE_ID, 3)).thenReturn(List.of(
                sample.variables().get(0).question(),
                new QuestionSummary("q9", "nom", "Nom du chef de ménage", "", "Text", List.of(), 0)));
        when(analytics.analyze(eq(QUESTIONNAIRE_ID), eq(3L), eq("region"), any()))
                .thenReturn(sample.variables().get(0));

        ReportService.ReportFile file = service.generate(QUESTIONNAIRE_ID, 3, NO_FILTER);

        assertThat(new String(file.content(), 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(file.content().length).isGreaterThan(20_000);
        assertThat(file.filename()).isEqualTo("rapport-enquete-menages-2026-v3-20261109-1000.pdf");
        verify(analytics, never()).analyze(eq(QUESTIONNAIRE_ID), anyLong(), eq("nom"), any());
    }

    @Test
    void describesTheAppliedFilters() {
        String scope = ReportService.describe(new InterviewFilter(List.of("Completed"),
                LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-07"), "sup1", null, "region", "2"));

        assertThat(scope).contains("du 1 nov. 2026 au 7 nov. 2026", "équipe sup1", "region = 2", "Terminées");
        assertThat(ReportService.describe(NO_FILTER)).isEqualTo("Toutes les interviews de la version");
        assertThat(ReportService.slug("Enquête : Ménages / 2026")).isEqualTo("enquete-menages-2026");
    }
}
