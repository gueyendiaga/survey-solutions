package com.surveysolutions.analytics.report;

import static com.surveysolutions.analytics.report.ReportHtmlRenderer.integer;
import static com.surveysolutions.analytics.report.ReportHtmlRenderer.percent;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import com.surveysolutions.analytics.fieldwork.FieldworkDtos.DailyProgress;
import com.surveysolutions.analytics.fieldwork.FieldworkDtos.FieldworkOverview;
import com.surveysolutions.analytics.fieldwork.FieldworkDtos.InterviewerProgress;
import com.surveysolutions.analytics.fieldwork.FieldworkDtos.TeamProgress;
import com.surveysolutions.analytics.questions.QuestionAnalyticsDtos.AnswerCount;
import com.surveysolutions.analytics.questions.QuestionAnalyticsDtos.PercentageBase;
import com.surveysolutions.analytics.questions.QuestionAnalyticsDtos.QuestionAnalytics;
import com.surveysolutions.analytics.questions.QuestionAnalyticsDtos.QuestionCompleteness;

/**
 * Rédaction automatique du rapport : titre informatif, chapeau et phrases d'introduction des
 * sections, déduits des seuls chiffres (aucune interprétation au-delà des données).
 */
public final class ReportNarrative {
    /** Sous ce nombre de réponses, les proportions sont signalées comme indicatives. */
    static final int SMALL_BASE = 30;
    private static final ZoneId ZONE = ZoneId.of("Africa/Dakar");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRANCE);
    private static final DateTimeFormatter SHORT_DAY = DateTimeFormatter.ofPattern("d MMMM", Locale.FRANCE);

    private ReportNarrative() {}

    // Les identifiants d'enquêteurs (gueye30…) ne sont jamais mis en majuscule.

    /** Enquêteur ayant des affectations et aucune interview. */
    static List<String> notStarted(FieldworkOverview fieldwork) {
        return interviewers(fieldwork).stream()
                .filter(row -> row.target() > 0 && row.total() == 0)
                .map(InterviewerProgress::interviewer)
                .toList();
    }

    /** Affectations détenues par un superviseur, pas encore confiées à un enquêteur. */
    static long unassigned(TeamProgress team) {
        return Math.max(0, team.target() - team.interviewers().stream().mapToLong(InterviewerProgress::target).sum());
    }

    static long unassigned(FieldworkOverview fieldwork) {
        return fieldwork.teams().stream().mapToLong(ReportNarrative::unassigned).sum();
    }

    static List<InterviewerProgress> interviewers(FieldworkOverview fieldwork) {
        return fieldwork.teams().stream().flatMap(team -> team.interviewers().stream()).toList();
    }

    static long lowResponseVariables(List<QuestionCompleteness> variables) {
        return variables.stream()
                .filter(item -> item.answeredCount() + item.missingCount() > 0
                        && item.responseRate() < ReportHtmlRenderer.LOW_RESPONSE_RATE)
                .count();
    }

    /** Titre informatif : le constat principal, puis le point d'attention le plus important. */
    public static String headline(ReportData data) {
        FieldworkOverview fieldwork = data.fieldwork();
        if (fieldwork.completed() == 0) {
            return fieldwork.target() > 0
                    ? "Collecte non commencée : aucune des " + integer(fieldwork.target()) + " interviews prévues n’est terminée"
                    : "Collecte non commencée";
        }
        String lead;
        if (fieldwork.completionRate() == null) {
            lead = integer(fieldwork.completed()) + " interviews réalisées";
        } else if (fieldwork.completionRate() >= 100) {
            lead = "Objectif atteint : " + integer(fieldwork.completed()) + " interviews réalisées";
        } else {
            lead = share(fieldwork.completionRate()) + " de l’objectif atteint";
        }
        List<String> notStarted = notStarted(fieldwork);
        long lowResponse = lowResponseVariables(data.completeness().variables());
        String attention;
        if (!notStarted.isEmpty()) {
            attention = notStarted.size() == 1 ? "un enquêteur encore inactif"
                    : integer(notStarted.size()) + " enquêteurs encore inactifs";
        } else if (fieldwork.rejected() > 0) {
            attention = integer(fieldwork.rejected()) + (fieldwork.rejected() == 1
                    ? " interview à corriger" : " interviews à corriger");
        } else if (lowResponse > 0) {
            attention = integer(lowResponse) + (lowResponse == 1
                    ? " variable mal renseignée" : " variables mal renseignées");
        } else {
            attention = "sans anomalie de qualité";
        }
        return lead + ", " + attention;
    }

    /** Fractions usuelles pour un titre lisible ; sinon le pourcentage. */
    static String share(double rate) {
        double[][] fractions = {{25, 0}, {100.0 / 3, 1}, {50, 2}, {200.0 / 3, 3}, {75, 4}};
        String[] words = {"Un quart", "Un tiers", "La moitié", "Deux tiers", "Trois quarts"};
        for (int index = 0; index < fractions.length; index++) {
            if (Math.abs(rate - fractions[index][0]) < 0.5) {
                return words[index];
            }
        }
        return percent(rate);
    }

    /** Chapeau : trois phrases au plus, chiffres clés et responsables des écarts. */
    public static String lead(ReportData data) {
        FieldworkOverview fieldwork = data.fieldwork();
        List<String> sentences = new ArrayList<>();
        String date = fieldwork.syncedAt() == null ? "À la date du rapport"
                : "Au " + DAY.format(fieldwork.syncedAt().atZone(ZONE));
        if (fieldwork.target() > 0) {
            sentences.add(date + ", " + plural(fieldwork.completed(), "interview a été réalisée", "interviews ont été réalisées")
                    + " pour un objectif de " + integer(fieldwork.target()) + ", soit " + percent(fieldwork.completionRate()) + ".");
        } else {
            sentences.add(date + ", " + plural(fieldwork.completed(), "interview a été réalisée", "interviews ont été réalisées")
                    + " ; aucune affectation ne porte d’objectif chiffré.");
        }
        if (fieldwork.completed() > 0) {
            if (fieldwork.approved() == fieldwork.completed() && fieldwork.rejected() == 0) {
                sentences.add(fieldwork.completed() == 1 ? "Elle a été approuvée." : "Toutes ont été approuvées.");
            } else {
                sentences.add(plural(fieldwork.approved(), "est approuvée", "sont approuvées") + ", "
                        + plural(fieldwork.rejected(), "est rejetée", "sont rejetées") + " et "
                        + plural(fieldwork.inProgress(), "reste en cours", "restent en cours") + ".");
            }
        }
        List<String> notStarted = notStarted(fieldwork);
        long unassigned = unassigned(fieldwork);
        List<String> gaps = new ArrayList<>();
        if (!notStarted.isEmpty()) {
            gaps.add(names(notStarted) + (notStarted.size() == 1 ? " n’a pas commencé" : " n’ont pas commencé"));
        }
        if (unassigned > 0) {
            gaps.add(plural(unassigned, "affectation n’est pas confiée", "affectations ne sont pas confiées")
                    + " à un enquêteur");
        }
        if (!gaps.isEmpty()) {
            sentences.add("Reste à faire : " + String.join(" et ", gaps) + ".");
        }
        return String.join(" ", sentences);
    }

    /** Paragraphe « rythme » de la page de synthèse. */
    public static String rhythm(FieldworkOverview fieldwork) {
        List<DailyProgress> days = fieldwork.daily().stream().filter(day -> day.completed() > 0).toList();
        if (days.isEmpty()) {
            return "Aucune interview n’a encore été terminée.";
        }
        String period = days.size() == 1
                ? "le " + SHORT_DAY.format(days.get(0).date())
                : "du " + SHORT_DAY.format(days.get(0).date()) + " au " + SHORT_DAY.format(days.get(days.size() - 1).date());
        DailyProgress best = days.stream().max(Comparator.comparingLong(DailyProgress::completed)).orElseThrow();
        StringBuilder text = new StringBuilder("Les interviews ont été terminées sur ")
                .append(plural(days.size(), "journée", "journées")).append(" de collecte, ").append(period).append(".");
        if (days.size() > 1) {
            text.append(" La journée la plus productive est le ").append(SHORT_DAY.format(best.date()))
                    .append(" (").append(plural(best.completed(), "interview", "interviews")).append(").");
        }
        long remaining = Math.max(0, fieldwork.target() - fieldwork.completed());
        double perDay = (double) fieldwork.completed() / days.size();
        if (remaining > 0 && perDay > 0) {
            long daysNeeded = (long) Math.ceil(remaining / perDay);
            text.append(" Au rythme moyen observé (").append(ReportHtmlRenderer.decimal(perDay))
                    .append(" par jour actif), les ").append(integer(remaining)).append(" restantes demandent environ ")
                    .append(plural(daysNeeded, "journée", "journées")).append(" de collecte, à condition d’être attribuées.");
        }
        return text.toString();
    }

    public static String qualitySummary(ReportData data) {
        List<QuestionCompleteness> variables = data.completeness().variables();
        long posed = variables.stream().filter(item -> item.answeredCount() + item.missingCount() > 0).count();
        if (posed == 0) {
            return "Aucune variable n’a encore été posée dans les interviews analysées.";
        }
        long low = lowResponseVariables(variables);
        long missing = variables.stream().mapToLong(QuestionCompleteness::missingCount).sum();
        String base = plural(posed, "variable a été posée", "variables ont été posées") + " dans "
                + plural(data.completeness().interviewCount(), "interview analysée", "interviews analysées") + ".";
        if (low == 0 && missing == 0) {
            return base + " Toutes sont entièrement renseignées ; aucune n’approche le seuil d’alerte de "
                    + percent(ReportHtmlRenderer.LOW_RESPONSE_RATE) + ".";
        }
        return base + " " + plural(missing, "question posée est restée", "questions posées sont restées") + " sans réponse ; "
                + plural(low, "variable passe", "variables passent") + " sous le seuil d’alerte de "
                + percent(ReportHtmlRenderer.LOW_RESPONSE_RATE) + ".";
    }

    public static String teamsSummary(FieldworkOverview fieldwork) {
        List<InterviewerProgress> rows = interviewers(fieldwork);
        if (rows.isEmpty()) {
            return "Aucune affectation ni interview n’est rattachée à une équipe.";
        }
        List<String> done = rows.stream()
                .filter(row -> row.target() > 0 && row.completed() >= row.target())
                .map(InterviewerProgress::interviewer).toList();
        List<String> notStarted = notStarted(fieldwork);
        List<String> sentences = new ArrayList<>();
        sentences.add(plural(fieldwork.teams().size(), "équipe", "équipes") + " et "
                + plural(rows.size(), "enquêteur", "enquêteurs") + " ont des affectations ou des interviews.");
        if (!done.isEmpty()) {
            sentences.add(names(done) + (done.size() == 1 ? " a atteint son objectif." : " ont atteint leur objectif."));
        }
        if (!notStarted.isEmpty()) {
            sentences.add(names(notStarted) + (notStarted.size() == 1 ? " n’a pas commencé." : " n’ont pas commencé."));
        }
        long unassigned = unassigned(fieldwork);
        if (unassigned > 0) {
            sentences.add(capitalize(plural(unassigned, "affectation reste", "affectations restent")) + " chez les superviseurs.");
        }
        return String.join(" ", sentences);
    }

    /** Sous-titre d'une figure de variable : modalité dominante et prudence sur les petits effectifs. */
    public static String variableNote(QuestionAnalytics analytics) {
        List<String> parts = new ArrayList<>();
        if (analytics.mean() != null) {
            parts.add("Moyenne " + ReportHtmlRenderer.decimal(analytics.mean().doubleValue())
                    + ", médiane " + ReportHtmlRenderer.decimal(analytics.median().doubleValue()) + ".");
        } else if (!analytics.distribution().isEmpty() && analytics.percentageBase() != PercentageBase.NUMERIC_VALUES) {
            AnswerCount top = analytics.distribution().get(0);
            if (top.count() > 0) {
                parts.add("Modalité la plus fréquente : « " + top.label() + " » (" + percent(top.percentage()) + ").");
            }
        }
        if (analytics.responseCount() > 0 && analytics.responseCount() < SMALL_BASE) {
            parts.add("Effectif faible : proportions indicatives.");
        }
        return String.join(" ", parts);
    }

    static String plural(long count, String singular, String pluralForm) {
        return integer(count) + " " + (count <= 1 ? singular : pluralForm);
    }

    private static String names(List<String> names) {
        if (names.size() <= 3) {
            return names.size() == 1 ? names.get(0)
                    : String.join(", ", names.subList(0, names.size() - 1)) + " et " + names.get(names.size() - 1);
        }
        return String.join(", ", names.subList(0, 3)) + " et " + (names.size() - 3) + " autre(s)";
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
