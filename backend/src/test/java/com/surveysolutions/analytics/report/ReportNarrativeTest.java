package com.surveysolutions.analytics.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.surveysolutions.analytics.fieldwork.FieldworkDtos.DailyProgress;
import com.surveysolutions.analytics.fieldwork.FieldworkDtos.FieldworkOverview;
import com.surveysolutions.analytics.fieldwork.FieldworkDtos.InterviewerProgress;
import com.surveysolutions.analytics.fieldwork.FieldworkDtos.TeamProgress;

class ReportNarrativeTest {
    private static FieldworkOverview fieldwork(long target, long completed, long approved, long rejected,
            List<TeamProgress> teams) {
        return new FieldworkOverview(ReportHtmlRendererTest.AT, false, target, 0, completed, approved, rejected, 0,
                target == 0 ? null : Math.round(completed * 10_000.0 / target) / 100.0, List.of(),
                List.of(new DailyProgress(LocalDate.parse("2026-11-03"), 1, 1),
                        new DailyProgress(LocalDate.parse("2026-11-05"), completed - 1, completed)),
                teams);
    }

    private static ReportData data(FieldworkOverview fieldwork) {
        ReportData sample = ReportHtmlRendererTest.sample();
        return new ReportData(sample.organization(), sample.questionnaireTitle(), 1, sample.generatedAt(),
                sample.scope(), fieldwork, sample.completeness(), sample.variables(), 0, List.of());
    }

    private static final List<TeamProgress> TEAM = List.of(new TeamProgress("gueye26", 6, 4, 4, 0, 0, 0, 66.67, List.of(
            new InterviewerProgress("gueye27", 4, 4, 4, 4, 0, 0, 0, 0, 100.0, 0.4, 2, 2.0, LocalDate.parse("2026-11-05")),
            new InterviewerProgress("gueye30", 1, 0, 0, 0, 0, 0, 0, 0, 0.0, null, 0, null, null))));

    @Test
    void writesAnInformativeHeadline() {
        assertThat(ReportNarrative.headline(data(fieldwork(6, 4, 4, 0, TEAM))))
                .isEqualTo("Deux tiers de l’objectif atteint, un enquêteur encore inactif");
        assertThat(ReportNarrative.headline(data(fieldwork(4, 4, 4, 0, List.of()))))
                .startsWith("Objectif atteint : 4 interviews réalisées");
        assertThat(ReportNarrative.headline(data(fieldwork(10, 3, 1, 2, List.of()))))
                .isEqualTo("30 % de l’objectif atteint, 2 interviews à corriger");
    }

    @Test
    void leadNamesTheGaps() {
        String lead = ReportNarrative.lead(data(fieldwork(6, 4, 4, 0, TEAM)));

        assertThat(lead).contains("Au 9 novembre 2026, 4 interviews ont été réalisées pour un objectif de 6, soit 66,7 %.")
                .contains("Toutes ont été approuvées.")
                .contains("Reste à faire : gueye30 n’a pas commencé")
                .contains("1 affectation n’est pas confiée à un enquêteur");
    }

    @Test
    void describesTheCollectionRhythm() {
        String rhythm = ReportNarrative.rhythm(fieldwork(6, 4, 4, 0, TEAM));

        assertThat(rhythm).contains("2 journées de collecte, du 3 novembre au 5 novembre")
                .contains("les 2 restantes demandent environ 1 journée");
    }

    @Test
    void flagsSmallBases() {
        assertThat(ReportNarrative.variableNote(ReportHtmlRendererTest.sample().variables().get(0)))
                .contains("Modalité la plus fréquente : « Dakar » (75 %).", "Effectif faible");
    }
}
