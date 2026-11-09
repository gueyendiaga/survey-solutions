package com.surveysolutions.analytics.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.surveysolutions.analytics.fieldwork.FieldworkDtos.DailyProgress;
import com.surveysolutions.analytics.fieldwork.FieldworkDtos.FieldworkOverview;
import com.surveysolutions.analytics.fieldwork.FieldworkDtos.InterviewerProgress;
import com.surveysolutions.analytics.fieldwork.FieldworkDtos.TeamProgress;
import com.surveysolutions.analytics.questions.QuestionAnalyticsDtos.AnswerCount;
import com.surveysolutions.analytics.questions.QuestionAnalyticsDtos.AnswerObservation;
import com.surveysolutions.analytics.questions.QuestionAnalyticsDtos.CompletenessOverview;
import com.surveysolutions.analytics.questions.QuestionAnalyticsDtos.PercentageBase;
import com.surveysolutions.analytics.questions.QuestionAnalyticsDtos.QuestionAnalytics;
import com.surveysolutions.analytics.questions.QuestionAnalyticsDtos.QuestionCompleteness;
import com.surveysolutions.analytics.questions.QuestionAnalyticsDtos.QuestionSummary;

class ReportHtmlRendererTest {
    static final Instant AT = Instant.parse("2026-11-09T10:00:00Z");

    static ReportData sample() {
        QuestionSummary region = new QuestionSummary("q1", "region", "Région & revenu < 5 <b>ménages</b>", "", "SingleOption",
                List.of("Section A"), 2);
        QuestionSummary age = new QuestionSummary("q2", "age", "Âge", "", "Numeric", List.of(), 0);
        FieldworkOverview fieldwork = new FieldworkOverview(AT, false, 10, 1, 4, 3, 1, 2, 40.0, List.of(),
                List.of(new DailyProgress(LocalDate.parse("2026-11-04"), 3, 3),
                        new DailyProgress(LocalDate.parse("2026-11-05"), 1, 4)),
                List.of(new TeamProgress("sup1", 10, 4, 3, 1, 0, 1, 40.0, List.of(
                        new InterviewerProgress("enq1", 6, 4, 4, 3, 1, 0, 1, 1, 66.67, 22.5, 2, 2.0,
                                LocalDate.parse("2026-11-05")),
                        new InterviewerProgress("enq2", 4, 0, 0, 0, 0, 0, 0, 0, 0.0, null, 0, null, null)))));
        CompletenessOverview completeness = new CompletenessOverview(4, List.of(
                new QuestionCompleteness(region, "survey", 4, 0, 0, 4, 100.0),
                new QuestionCompleteness(age, "survey", 2, 2, 0, 2, 50.0)), List.of("Completed"), 0, AT);
        QuestionAnalytics regionAnalytics = new QuestionAnalytics(region, 4, 4, 4, 0, 0, 100.0,
                List.of(new AnswerObservation(1, "01-02-03-04", "", "Mme Secret")),
                List.of(new AnswerCount("Dakar", 3, 75.0), new AnswerCount("Thiès & environs", 1, 25.0)),
                null, null, null, null, null, null, null, 0, PercentageBase.RESPONSES, List.of(), List.of("Completed"), 0, AT);
        QuestionAnalytics ageAnalytics = new QuestionAnalytics(age, 4, 2, 2, 2, 0, 50.0, List.of(),
                List.of(new AnswerCount("20 – 40", 2, 100.0)), new BigDecimal("20"), new BigDecimal("40"),
                new BigDecimal("30.00"), new BigDecimal("30.00"), new BigDecimal("60"), new BigDecimal("20"),
                new BigDecimal("40"), 0, PercentageBase.NUMERIC_VALUES,
                List.of(new AnswerCount("Ne sait pas", 1, 33.3)), List.of("Completed"), 0, AT);
        return new ReportData("Agence test", "Enquête ménages 2026", 3, AT, "Toutes les interviews de la version",
                fieldwork, completeness, List.of(regionAnalytics, ageAnalytics), 2, List.of("nom", "gps"));
    }

    @Test
    void rendersAllSectionsWithAggregatesOnly() {
        String html = ReportHtmlRenderer.render(sample());

        assertThat(html).contains("Enquête ménages 2026", "Agence test", "Chiffres clés",
                "Avancement par équipe et par enquêteur", "Qualité des données", "Indicateurs par variable",
                "Encadré — Sources, définitions et méthode", "Figure 1", "Tableau 1", "enq2", "Dakar",
                "Moyenne", "Ne sait pas", "2 autre(s) variable(s)", "nom, gps");
        assertThat(html).doesNotContain("Mme Secret").doesNotContain("01-02-03-04");
    }

    @Test
    void escapesQuestionnaireContent() {
        String html = ReportHtmlRenderer.render(sample());

        assertThat(html).contains("Région &amp; revenu &lt; 5 ménages", "Thiès &amp; environs");
    }

    @Test
    void fillsMissingDaysWithZero() {
        var days = ReportHtmlRenderer.continuousDays(List.of(
                new DailyProgress(LocalDate.parse("2026-11-03"), 1, 1),
                new DailyProgress(LocalDate.parse("2026-11-05"), 3, 4)));

        assertThat(days).extracting(DailyProgress::completed).containsExactly(1L, 0L, 3L);
        assertThat(days).extracting(DailyProgress::cumulative).containsExactly(1L, 1L, 4L);
    }

    @Test
    void formatsNumbersTheFrenchWay() {
        assertThat(ReportHtmlRenderer.integer(1234567)).isEqualTo("1\u00A0234\u00A0567");
        assertThat(ReportHtmlRenderer.percent(66.67)).isEqualTo("66,7\u00A0%");
    }
}
