package com.surveysolutions.analytics.report;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.surveysolutions.analytics.fieldwork.FieldworkDtos.DailyProgress;
import com.surveysolutions.analytics.fieldwork.FieldworkDtos.FieldworkOverview;
import com.surveysolutions.analytics.fieldwork.FieldworkDtos.InterviewerProgress;
import com.surveysolutions.analytics.fieldwork.FieldworkDtos.TeamProgress;
import com.surveysolutions.analytics.questions.QuestionAnalyticsDtos.AnswerCount;
import com.surveysolutions.analytics.questions.QuestionAnalyticsDtos.PercentageBase;
import com.surveysolutions.analytics.questions.QuestionAnalyticsDtos.QuestionAnalytics;
import com.surveysolutions.analytics.questions.QuestionAnalyticsDtos.QuestionCompleteness;

/**
 * Gabarit XHTML du rapport, mis en page comme une publication statistique (bandeau et titre
 * informatif, chapeau, chiffres clés en marge, figures et tableaux numérotés avec sources,
 * encadré méthodologique). Converti en PDF par openhtmltopdf, qui ne gère ni flexbox ni grid :
 * la mise en page repose sur des tableaux et des blocs positionnés, graphiques compris.
 */
public final class ReportHtmlRenderer {
    /** Seuil sous lequel un taux de réponse est signalé. */
    static final double LOW_RESPONSE_RATE = 80.0;
    /** Seuil sous lequel la réalisation d'un enquêteur est signalée. */
    static final double LOW_COMPLETION_RATE = 50.0;
    private static final int MAX_DAYS = 31;
    private static final int MAX_QUALITY_ROWS = 15;
    private static final int MAX_CATEGORIES = 12;
    private static final int MAX_EXCLUDED_LISTED = 20;
    private static final ZoneId ZONE = ZoneId.of("Africa/Dakar");
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("d MMMM yyyy 'à' H 'h' mm", Locale.FRANCE).withZone(ZONE);
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRANCE).withZone(ZONE);
    private static final DateTimeFormatter AXIS_DAY = DateTimeFormatter.ofPattern("d MMM", Locale.FRANCE);
    private static final Map<String, String> STATUS_LABELS = Map.ofEntries(
            Map.entry("SupervisorAssigned", "Affectées au superviseur"),
            Map.entry("InterviewerAssigned", "Affectées à l'enquêteur"),
            Map.entry("Restarted", "Redémarrées"),
            Map.entry("Completed", "Terminées"),
            Map.entry("RejectedBySupervisor", "Rejetées par le superviseur"),
            Map.entry("ApprovedBySupervisor", "Approuvées par le superviseur"),
            Map.entry("RejectedByHeadquarters", "Rejetées par HQ"),
            Map.entry("ApprovedByHeadquarters", "Approuvées par HQ"),
            Map.entry("Created", "Créées"),
            Map.entry("Deleted", "Supprimées"));

    private ReportHtmlRenderer() {}

    /** Numérotation des figures et tableaux, dans l'ordre d'apparition. */
    private static final class Counters {
        private int figure;
        private int table;

        String figure() {
            return "Figure " + (++figure);
        }

        String table() {
            return "Tableau " + (++table);
        }
    }

    public static String render(ReportData data) {
        Counters counters = new Counters();
        StringBuilder html = new StringBuilder(80_000);
        html.append("<!DOCTYPE html><html lang=\"fr\"><head><meta charset=\"UTF-8\"/>")
                .append("<title>").append(escape(ReportNarrative.headline(data))).append("</title>")
                .append("<style>").append(CSS).append("</style></head><body>");

        html.append("<div class=\"running-header\"><table class=\"rh\"><tr><td>")
                .append(escape(data.questionnaireTitle())).append("</td><td class=\"r\">Suivi de collecte · ")
                .append(escape(dataDate(data))).append("</td></tr></table></div>")
                .append("<div class=\"running-footer\">Document de travail — diffusion restreinte</div>");

        cover(html, data, counters);
        teams(html, data, counters);
        quality(html, data, counters);
        variables(html, data, counters);
        method(html, data);

        html.append("</body></html>");
        return html.toString();
    }

    private static String dataDate(ReportData data) {
        return data.fieldwork().syncedAt() == null ? DATE.format(data.generatedAt()) : DATE.format(data.fieldwork().syncedAt());
    }

    // ---- Page 1 : bandeau, chapeau, chiffres clés, progression ----------------------------

    private static void cover(StringBuilder html, ReportData data, Counters counters) {
        FieldworkOverview fieldwork = data.fieldwork();
        html.append("<div class=\"band\">")
                .append("<table class=\"band-top\"><tr><td>").append(escape(data.organization()))
                .append("</td><td class=\"r\">Suivi de collecte · ").append(escape(dataDate(data))).append("</td></tr></table>")
                .append("<div class=\"band-rule\"></div>")
                .append("<h1>").append(escape(ReportNarrative.headline(data))).append("</h1>")
                .append("<div class=\"band-sub\">").append(escape(data.questionnaireTitle())).append(" · version ")
                .append(data.version()).append(" · données arrêtées au ")
                .append(fieldwork.syncedAt() == null ? "—" : escape(DATE_TIME.format(fieldwork.syncedAt())))
                .append("</div></div>");

        html.append("<table class=\"cols\"><tr><td class=\"main\">");
        html.append("<p class=\"chapeau\">").append(escape(ReportNarrative.lead(data))).append("</p>");
        html.append("<p class=\"body\"><b>Rythme de collecte.</b> ").append(escape(ReportNarrative.rhythm(fieldwork))).append("</p>");
        html.append("<p class=\"body\"><b>Qualité des données.</b> ").append(escape(ReportNarrative.qualitySummary(data))).append("</p>");
        if (!"Toutes les interviews de la version".equals(data.scope())) {
            html.append("<p class=\"scope\">Périmètre : ").append(escape(data.scope())).append("</p>");
        }
        dailyChart(html, fieldwork, counters);
        html.append("</td><td class=\"aside\">");
        keyFigures(html, data);
        html.append("</td></tr></table>");
    }

    private static void keyFigures(StringBuilder html, ReportData data) {
        FieldworkOverview fieldwork = data.fieldwork();
        html.append("<div class=\"aside-title\">Chiffres clés</div>");
        if (fieldwork.completionRate() != null) {
            html.append("<div class=\"kf kf-first\"><div class=\"kf-big\">").append(percent(fieldwork.completionRate()))
                    .append("</div>").append(track(fieldwork.completionRate()))
                    .append("<div class=\"kfl\">de l’objectif atteint</div></div>");
            figure(html, integer(fieldwork.completed()) + " / " + integer(fieldwork.target()),
                    "interviews réalisées sur l’objectif des affectations", false);
        } else {
            figure(html, integer(fieldwork.completed()), "interviews réalisées (pas d’objectif chiffré)", true);
        }
        figure(html, integer(fieldwork.approved()), "interviews approuvées", false);
        figure(html, integer(fieldwork.rejected() + fieldwork.inProgress()), "rejetées ou en cours", false);
        Double bestPace = ReportNarrative.interviewers(fieldwork).stream()
                .map(InterviewerProgress::completedPerActiveDay)
                .filter(value -> value != null)
                .max(Double::compare)
                .orElse(null);
        if (bestPace != null) {
            figure(html, decimal(bestPace), "interviews par jour actif (enquêteur le plus rapide)", false);
        }
        figure(html, averageResponseRate(data.completeness().variables()), "taux de réponse moyen des variables", false);
        html.append("<div class=\"kf-end\"></div>");
    }

    private static void figure(StringBuilder html, String value, String label, boolean first) {
        html.append("<div class=\"kf").append(first ? " kf-first" : "").append("\"><div class=\"kfv\">")
                .append(escape(value)).append("</div><div class=\"kfl\">").append(escape(label)).append("</div></div>");
    }

    /** Barres : interviews terminées dans la journée ; aplat : cumul ; tirets : objectif. */
    private static void dailyChart(StringBuilder html, FieldworkOverview fieldwork, Counters counters) {
        List<DailyProgress> days = continuousDays(fieldwork.daily());
        html.append("<div class=\"fig keep\"><div class=\"fig-title\"><b>").append(counters.figure())
                .append("</b> — Interviews terminées par jour et cumul</div>");
        if (days.isEmpty()) {
            html.append("<p class=\"empty\">Aucune interview terminée sur le périmètre.</p></div>");
            return;
        }
        long total = days.get(days.size() - 1).cumulative();
        long max = Math.max(1, Math.max(total, fieldwork.target()));
        double scale = 100.0 / (max * 1.12);
        double width = 100.0 / days.size();
        int labelEvery = Math.max(1, (int) Math.ceil(days.size() / 8.0));
        html.append("<div class=\"chart\">");
        for (int line = 1; line <= 3; line++) {
            html.append("<div class=\"grid\" style=\"bottom:").append(pct(line * 25.0)).append("\"></div>");
        }
        for (int index = 0; index < days.size(); index++) {
            DailyProgress day = days.get(index);
            double left = index * width;
            html.append("<div class=\"cumul\" style=\"left:").append(pct(left)).append(";width:").append(pct(width))
                    .append(";height:").append(pct(day.cumulative() * scale)).append("\"></div>");
            if (day.completed() > 0) {
                html.append("<div class=\"daybar\" style=\"left:").append(pct(left + width * 0.25)).append(";width:")
                        .append(pct(width * 0.5)).append(";height:").append(pct(day.completed() * scale)).append("\"></div>");
                if (days.size() <= 16) {
                    html.append("<div class=\"barlabel\" style=\"left:").append(pct(left)).append(";width:").append(pct(width))
                            .append(";bottom:").append(pct(day.completed() * scale + 1.5)).append("\">")
                            .append(integer(day.completed())).append("</div>");
                }
            }
        }
        if (fieldwork.target() > 0) {
            html.append("<div class=\"target\" style=\"bottom:").append(pct(fieldwork.target() * scale)).append("\"></div>")
                    .append("<div class=\"target-label\" style=\"bottom:").append(pct(fieldwork.target() * scale + 2))
                    .append("\">Objectif : ").append(integer(fieldwork.target())).append("</div>");
        }
        html.append("<div class=\"cumul-label\" style=\"bottom:").append(pct(Math.min(90, total * scale + 2)))
                .append("\">Cumul : ").append(integer(total)).append("</div>");
        html.append("</div><table class=\"axis\"><tr>");
        for (int index = 0; index < days.size(); index++) {
            html.append("<td>").append(index % labelEvery == 0 || index == days.size() - 1
                    ? escape(AXIS_DAY.format(days.get(index).date())) : "").append("</td>");
        }
        html.append("</tr></table>")
                .append("<div class=\"src\">Barres : interviews terminées dans la journée. Aplat : cumul")
                .append(fieldwork.target() > 0 ? ". Tirets : objectif des affectations" : "")
                .append(". Source : Survey Solutions HQ.</div></div>");
    }

    /** Jours consécutifs (un jour sans complétion apparaît à zéro), limités aux MAX_DAYS derniers. */
    static List<DailyProgress> continuousDays(List<DailyProgress> daily) {
        List<DailyProgress> days = new ArrayList<>();
        if (daily.isEmpty()) {
            return days;
        }
        Map<LocalDate, DailyProgress> byDate = new java.util.HashMap<>();
        daily.forEach(day -> byDate.put(day.date(), day));
        LocalDate last = daily.get(daily.size() - 1).date();
        LocalDate first = daily.get(0).date();
        if (first.plusDays(MAX_DAYS - 1).isBefore(last)) {
            first = last.minusDays(MAX_DAYS - 1);
        }
        long cumulative = 0;
        for (DailyProgress day : daily) {
            if (day.date().isBefore(first)) {
                cumulative = day.cumulative();
            }
        }
        for (LocalDate date = first; !date.isAfter(last); date = date.plusDays(1)) {
            DailyProgress day = byDate.get(date);
            if (day != null) {
                cumulative = day.cumulative();
            }
            days.add(new DailyProgress(date, day == null ? 0 : day.completed(), cumulative));
        }
        return days;
    }

    // ---- Section 1 : équipes ------------------------------------------------------------

    private static void teams(StringBuilder html, ReportData data, Counters counters) {
        FieldworkOverview fieldwork = data.fieldwork();
        sectionHead(html, "1", "Avancement par équipe et par enquêteur", true);
        html.append("<div class=\"section-body\"><p class=\"body\">")
                .append(escape(ReportNarrative.teamsSummary(fieldwork))).append("</p>");
        if (fieldwork.teams().isEmpty()) {
            html.append("</div>");
            return;
        }

        long scale = 1;
        for (TeamProgress team : fieldwork.teams()) {
            for (InterviewerProgress row : team.interviewers()) {
                scale = Math.max(scale, Math.max(row.target(), row.completed()));
            }
            scale = Math.max(scale, ReportNarrative.unassigned(team));
        }
        html.append("<div class=\"fig\"><div class=\"fig-title\"><b>").append(counters.figure())
                .append("</b> — Interviews réalisées par rapport à l’objectif, par enquêteur</div><table class=\"bullets\">");
        for (TeamProgress team : fieldwork.teams()) {
            for (InterviewerProgress row : team.interviewers()) {
                bullet(html, row.interviewer(), row.completed(), row.target(), scale,
                        row.target() > 0 && row.total() == 0 ? "pas commencé" : null, false);
            }
            long unassigned = ReportNarrative.unassigned(team);
            if (unassigned > 0) {
                bullet(html, "Non confiée (" + team.supervisor() + ")", 0, unassigned, scale, null, true);
            }
        }
        html.append("<tr><td></td><td class=\"scale\"><table class=\"ticks\"><tr><td>0</td><td class=\"c\">")
                .append(decimal(scale / 2.0)).append("</td><td class=\"r\">").append(integer(scale))
                .append("</td></tr></table></td><td></td></tr></table>")
                .append("<div class=\"src\">Barre : interviews réalisées. Trait ocre : objectif. Fond clair : reste à faire.</div></div>");

        html.append("<div class=\"tab-title\"><b>").append(counters.table())
                .append("</b> — Réalisation, qualité et rythme par enquêteur</div>")
                .append("<table class=\"t\"><thead><tr><th class=\"l\">Équipe / enquêteur</th><th>Objectif</th>")
                .append("<th>Réalisées</th><th>Taux</th><th>Approuvées</th><th>Rejetées</th><th>En cours</th>")
                .append("<th>Avec erreurs</th><th>Durée médiane</th><th>Par jour actif</th><th>Dernière</th></tr></thead><tbody>");
        for (TeamProgress team : fieldwork.teams()) {
            html.append("<tr class=\"grp\"><td class=\"l\">Équipe ").append(escape(team.supervisor())).append("</td>")
                    .append(cells(team.target(), team.completed(), team.completionRate(), team.approved(),
                            team.rejected(), team.inProgress(), team.withErrors()))
                    .append("<td></td><td></td><td></td></tr>");
            for (InterviewerProgress row : team.interviewers()) {
                boolean notStarted = row.target() > 0 && row.total() == 0;
                html.append("<tr class=\"").append(notStarted ? "muted" : "").append("\"><td class=\"l in\">")
                        .append(escape(row.interviewer())).append("</td>")
                        .append(cells(row.target(), row.completed(), row.completionRate(), row.approved(),
                                row.rejected(), row.inProgress(), row.withErrors()))
                        .append("<td>").append(row.medianDurationMinutes() == null ? "—"
                                : decimal(row.medianDurationMinutes()) + " min").append("</td>")
                        .append("<td>").append(row.completedPerActiveDay() == null ? "—"
                                : decimal(row.completedPerActiveDay())).append("</td>")
                        .append("<td>").append(row.lastCompletion() == null ? "—"
                                : escape(AXIS_DAY.format(row.lastCompletion()))).append("</td></tr>");
            }
            long unassigned = ReportNarrative.unassigned(team);
            if (unassigned > 0) {
                html.append("<tr class=\"muted\"><td class=\"l in\">Non confiée</td><td>").append(integer(unassigned))
                        .append("</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td><td>—</td></tr>");
            }
        }
        html.append("</tbody></table><div class=\"src\">Lecture : un taux de 100 % signifie que l’enquêteur a "
                + "réalisé autant d’interviews que son objectif. Réalisées : terminées au moins une fois, quel que "
                + "soit le statut actuel. En italique : affectations sans interview.</div></div>");
    }

    private static void bullet(StringBuilder html, String name, long completed, long target, long scale,
            String note, boolean muted) {
        double targetPct = 100.0 * target / scale;
        double donePct = 100.0 * Math.min(completed, scale) / scale;
        html.append("<tr><td class=\"bname").append(muted ? " muted-text" : "").append("\">").append(escape(name))
                .append("</td><td class=\"btrack-cell\"><div class=\"btrack\">")
                .append("<div class=\"bbg\" style=\"width:").append(pct(targetPct)).append("\"></div>");
        if (completed > 0) {
            html.append("<div class=\"bbar\" style=\"width:").append(pct(donePct)).append("\"></div>");
        }
        if (target > 0) {
            html.append("<div class=\"btick\" style=\"left:").append(pct(Math.min(99.4, targetPct))).append("\"></div>");
        }
        html.append("</div></td><td class=\"bval\"><b>").append(integer(completed)).append(" / ").append(integer(target))
                .append("</b>").append(note == null ? "" : " <span class=\"alert\">" + escape(note) + "</span>")
                .append("</td></tr>");
    }

    private static String cells(long target, long completed, Double rate, long approved, long rejected,
            long inProgress, long withErrors) {
        return "<td>" + integer(target) + "</td><td>" + integer(completed) + "</td><td"
                + (rate != null && rate < LOW_COMPLETION_RATE ? " class=\"alert\"" : "") + ">"
                + (rate == null ? "—" : percent(rate)) + "</td><td>" + integer(approved) + "</td><td>"
                + integer(rejected) + "</td><td>" + integer(inProgress) + "</td><td>" + integer(withErrors) + "</td>";
    }

    // ---- Section 2 : qualité ------------------------------------------------------------

    private static void quality(StringBuilder html, ReportData data, Counters counters) {
        sectionHead(html, "2", "Qualité des données", false);
        html.append("<div class=\"section-body\"><p class=\"body\">")
                .append(escape(ReportNarrative.qualitySummary(data))).append("</p>");
        List<QuestionCompleteness> posed = data.completeness().variables().stream()
                .filter(item -> item.answeredCount() + item.missingCount() > 0)
                .toList();
        List<QuestionCompleteness> lowest = posed.stream()
                .sorted(Comparator.comparingDouble(QuestionCompleteness::responseRate)
                        .thenComparing(item -> item.question().variableName()))
                .limit(MAX_QUALITY_ROWS)
                .toList();
        if (!lowest.isEmpty()) {
            html.append("<div class=\"fig\"><div class=\"fig-title\"><b>").append(counters.figure())
                    .append("</b> — Taux de réponse par variable")
                    .append(lowest.size() < posed.size() ? " (les " + lowest.size() + " plus faibles)" : "")
                    .append("</div><table class=\"dots\"><tr><th class=\"l\"></th><th class=\"dtrack-cell\">")
                    .append("<div class=\"zone-label\">zone d’alerte (&lt; ").append(percent(LOW_RESPONSE_RATE))
                    .append(")</div></th><th>Taux</th><th>Sans rép.</th><th>Non applic.</th></tr>");
            for (QuestionCompleteness item : lowest) {
                boolean alert = item.responseRate() < LOW_RESPONSE_RATE;
                html.append("<tr><td class=\"l mono\">").append(escape(item.question().variableName())).append("</td>")
                        .append("<td class=\"dtrack-cell\"><div class=\"dtrack\"><div class=\"zone\" style=\"width:")
                        .append(pct(LOW_RESPONSE_RATE)).append("\"></div><div class=\"threshold\" style=\"left:")
                        .append(pct(LOW_RESPONSE_RATE)).append("\"></div><div class=\"dline\"></div><div class=\"dot")
                        .append(alert ? " dot-alert" : "").append("\" style=\"left:")
                        .append(pct(Math.max(0, Math.min(100, item.responseRate())))).append("\"></div></div></td>")
                        .append("<td").append(alert ? " class=\"alert\"" : "").append(">").append(percent(item.responseRate()))
                        .append("</td><td>").append(integer(item.missingCount())).append("</td><td>")
                        .append(integer(item.notApplicableCount())).append("</td></tr>");
            }
            html.append("</table><div class=\"src\">Taux de réponse = réponses / (réponses + sans réponse) ; "
                    + "les questions non applicables (filtres) sont exclues. Champ : ")
                    .append(escape(statusList(data))).append(".</div></div>");
        }
        html.append("</div>");
    }

    // ---- Section 3 : variables ----------------------------------------------------------

    private static void variables(StringBuilder html, ReportData data, Counters counters) {
        sectionHead(html, "3", "Indicateurs par variable", true);
        html.append("<div class=\"section-body\"><p class=\"body\">Variables à choix, numériques et dates, dans "
                + "l’ordre du questionnaire. Les réponses libres, GPS et multimédia ne figurent pas dans ce rapport "
                + "afin de préserver la confidentialité des répondants.</p>");
        if (data.variables().isEmpty()) {
            html.append("<p class=\"empty\">Aucune variable à choix, numérique ou date à présenter.</p>");
        }
        for (QuestionAnalytics analytics : data.variables()) {
            variable(html, analytics, counters, statusList(data));
        }
        if (data.omittedVariables() > 0 || !data.excludedVariables().isEmpty()) {
            html.append("<p class=\"aside-note\">");
            if (data.omittedVariables() > 0) {
                html.append(data.omittedVariables()).append(" autre(s) variable(s) analysable(s) ne sont pas détaillées "
                        + "ici ; elles restent consultables dans l’application. ");
            }
            if (!data.excludedVariables().isEmpty()) {
                List<String> listed = data.excludedVariables().subList(0,
                        Math.min(MAX_EXCLUDED_LISTED, data.excludedVariables().size()));
                html.append("Non représentées (réponses libres, GPS ou multimédia) : <span class=\"mono\">")
                        .append(escape(String.join(", ", listed))).append("</span>");
                if (data.excludedVariables().size() > listed.size()) {
                    html.append(" et ").append(data.excludedVariables().size() - listed.size()).append(" autre(s)");
                }
                html.append(".");
            }
            html.append("</p>");
        }
        html.append("</div>");
    }

    private static void variable(StringBuilder html, QuestionAnalytics analytics, Counters counters, String scope) {
        var question = analytics.question();
        html.append("<div class=\"fig var keep\"><div class=\"fig-title\"><b>").append(counters.figure()).append("</b> — ")
                .append(escape(questionLabel(question.label(), question.questionText(), question.variableName())))
                .append("</div><div class=\"var-meta\"><span class=\"mono\">").append(escape(question.variableName()))
                .append("</span> · ").append(escape(typeLabel(question.type())));
        if (!question.breadcrumbs().isEmpty()) {
            html.append(" · ").append(escape(String.join(" › ", question.breadcrumbs())));
        }
        html.append("</div>");
        String note = ReportNarrative.variableNote(analytics);
        if (!note.isEmpty()) {
            html.append("<div class=\"var-note\">").append(escape(note)).append("</div>");
        }
        if (analytics.mean() != null) {
            html.append("<table class=\"stats\"><tr>");
            stat(html, "Moyenne", analytics.mean());
            stat(html, "Médiane", analytics.median());
            stat(html, "Minimum", analytics.minimum());
            stat(html, "Maximum", analytics.maximum());
            stat(html, "P5", analytics.percentile05());
            stat(html, "P95", analytics.percentile95());
            html.append("</tr></table>");
        }
        List<AnswerCount> distribution = analytics.distribution();
        if (!distribution.isEmpty()) {
            List<AnswerCount> shown = distribution.size() > MAX_CATEGORIES ? distribution.subList(0, MAX_CATEGORIES) : distribution;
            double maxPercent = shown.stream().mapToDouble(AnswerCount::percentage).max().orElse(100);
            html.append("<table class=\"dist\">");
            for (AnswerCount count : shown) {
                double width = maxPercent == 0 ? 0 : 72.0 * count.percentage() / maxPercent;
                html.append("<tr").append(count.count() == 0 ? " class=\"muted-text\"" : "").append("><td class=\"dlabel\">")
                        .append(escape(truncate(count.label(), 70))).append("</td><td class=\"dbar-cell\">");
                if (count.count() > 0) {
                    html.append("<div class=\"dbar\" style=\"width:").append(pct(width)).append("\"></div>");
                } else {
                    html.append("<div class=\"dzero\"></div>");
                }
                html.append("<span class=\"dval\"><b>").append(percent(count.percentage())).append("</b> (")
                        .append(integer(count.count())).append(")</span></td></tr>");
            }
            html.append("</table>");
            if (distribution.size() > MAX_CATEGORIES) {
                html.append("<div class=\"src\">").append(distribution.size() - MAX_CATEGORIES)
                        .append(" modalité(s) moins fréquente(s) non affichée(s).</div>");
            }
        }
        html.append("<div class=\"src\">Base : ").append(ReportNarrative.plural(analytics.responseCount(), "réponse", "réponses"))
                .append(" ; ").append(integer(analytics.missingCount())).append(" sans réponse ; ")
                .append(integer(analytics.notApplicableCount())).append(" non applicable(s).");
        if (analytics.percentageBase() == PercentageBase.RESPONDENTS) {
            html.append(" Choix multiples : part des réponses ayant coché chaque modalité ; le total peut dépasser 100 %.");
        }
        if (analytics.specialValues().stream().anyMatch(value -> value.count() > 0)) {
            html.append(" Valeurs spéciales exclues des statistiques : ").append(escape(String.join(" ; ",
                    analytics.specialValues().stream().filter(value -> value.count() > 0)
                            .map(value -> value.label() + " " + integer(value.count()))
                            .toList()))).append(".");
        }
        html.append(" Champ : ").append(escape(scope)).append(".</div></div>");
    }

    // ---- Encadré méthodologique ---------------------------------------------------------

    private static void method(StringBuilder html, ReportData data) {
        html.append("<div class=\"box\"><div class=\"box-title\">Encadré — Sources, définitions et méthode</div>")
                .append("<table class=\"defs\"><tr>");
        String[][] definitions = {
            {"Source", "Export tabulaire de Survey Solutions Headquarters, importé dans la base Analytics"
                    + (data.fieldwork().syncedAt() == null ? "." : " le " + DATE_TIME.format(data.fieldwork().syncedAt()) + ".")},
            {"Objectif", "Somme des quotas des affectations actives ; les affectations sans quota sont comptées à part."},
            {"Interviews réalisées", "Terminées au moins une fois par un enquêteur, quel que soit leur statut actuel."},
            {"Par jour actif", "Interviews réalisées rapportées aux jours où l’enquêteur en a terminé au moins une."},
            {"Sans réponse / non applicable", "Question posée restée vide / question désactivée par un filtre, exclue du taux de réponse."},
            {"Rosters", "Pour une question de roster, chaque ligne (membre, parcelle…) compte comme une réponse."},
            {"Champ des indicateurs", "Sections 2 et 3 : " + statusList(data) + "."},
            {"Périmètre", data.scope() + "."}
        };
        for (int index = 0; index < definitions.length; index++) {
            if (index > 0 && index % 2 == 0) {
                html.append("</tr><tr>");
            }
            html.append("<td><div class=\"dt\">").append(escape(definitions[index][0])).append("</div><div class=\"dd\">")
                    .append(escape(definitions[index][1])).append("</div></td>");
        }
        html.append("</tr></table></div>")
                .append("<table class=\"colophon\"><tr><td>Ce document ne contient que des agrégats et aucune réponse "
                        + "individuelle. Produit automatiquement le ")
                .append(escape(DATE_TIME.format(data.generatedAt())))
                .append(" ; les chiffres évoluent à chaque synchronisation avec HQ.</td><td class=\"r sig\">")
                .append(escape(data.organization())).append("</td></tr></table>");
    }

    // ---- Éléments ---------------------------------------------------------------------

    private static void sectionHead(StringBuilder html, String number, String title, boolean newPage) {
        html.append("<table class=\"section-head").append(newPage ? " new-page" : "").append("\"><tr><td class=\"secn\">")
                .append(number).append("</td><td><h2>").append(escape(title)).append("</h2></td></tr></table>");
    }

    private static String track(double rate) {
        return "<div class=\"track\"><div class=\"track-fill\" style=\"width:"
                + pct(Math.max(0, Math.min(100, rate))) + "\"></div></div>";
    }

    private static void stat(StringBuilder html, String label, BigDecimal value) {
        html.append("<td><div class=\"stat-label\">").append(label).append("</div><div class=\"stat-value\">")
                .append(value == null ? "—" : escape(decimal(value.doubleValue()))).append("</div></td>");
    }

    private static String statusList(ReportData data) {
        List<String> statuses = data.completeness().includedStatuses();
        return statuses == null || statuses.isEmpty() ? "toutes les interviews"
                : "interviews " + String.join(", ", statuses.stream()
                        .map(status -> lowerFirst(statusLabel(status))).toList());
    }

    private static String lowerFirst(String text) {
        return text.isEmpty() ? text : Character.toLowerCase(text.charAt(0)) + text.substring(1);
    }

    private static String averageResponseRate(List<QuestionCompleteness> variables) {
        double average = variables.stream()
                .filter(item -> item.answeredCount() + item.missingCount() > 0)
                .mapToDouble(QuestionCompleteness::responseRate)
                .average()
                .orElse(Double.NaN);
        return Double.isNaN(average) ? "—" : percent(average);
    }

    private static String questionLabel(String label, String text, String variable) {
        if (text != null && !text.isBlank()) {
            // Retire le balisage HTML des intitulés HQ (<b>, <br/>…) sans toucher un « < » du texte.
            return text.replaceAll("</?[A-Za-z][^<>]*>", "").replaceAll("\\s+", " ").trim();
        }
        return label != null && !label.isBlank() ? label : variable;
    }

    private static String typeLabel(String type) {
        String value = type == null ? "" : type.toLowerCase(Locale.ROOT);
        if (value.contains("multi") || value.contains("multy")) {
            return "choix multiples";
        }
        if (value.contains("single")) {
            return "choix unique";
        }
        if (value.contains("numeric")) {
            return "numérique";
        }
        if (value.contains("date")) {
            return "date";
        }
        return type == null ? "" : type;
    }

    static String statusLabel(String status) {
        return STATUS_LABELS.getOrDefault(status, status);
    }

    // ---- Formats ----------------------------------------------------------------------

    private static String pct(double value) {
        return String.format(Locale.ROOT, "%.2f%%", value);
    }

    static String integer(long value) {
        String digits = Long.toString(Math.abs(value));
        StringBuilder grouped = new StringBuilder();
        for (int index = 0; index < digits.length(); index++) {
            if (index > 0 && (digits.length() - index) % 3 == 0) {
                grouped.append(' ');
            }
            grouped.append(digits.charAt(index));
        }
        return (value < 0 ? "-" : "") + grouped;
    }

    static String percent(double value) {
        return decimal(value) + " %";
    }

    static String decimal(double value) {
        DecimalFormat format = new DecimalFormat("#,##0.#", DecimalFormatSymbols.getInstance(Locale.FRANCE));
        DecimalFormatSymbols symbols = format.getDecimalFormatSymbols();
        symbols.setGroupingSeparator(' ');
        format.setDecimalFormatSymbols(symbols);
        return format.format(value);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }

    /** Échappement XHTML ; supprime les caractères de contrôle invalides en XML. */
    static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder escaped = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&#39;");
                default -> {
                    if (character >= 0x20 || character == '\n' || character == '\t') {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.toString();
    }

    private static final String INK = "#12355b";
    private static final String OCHRE = "#c27c1e";

    private static final String CSS = """
            @page {
              size: A4;
              margin: 20mm 17mm 17mm 17mm;
              @top-left { content: element(header); }
              @bottom-left { content: element(footer); }
              @bottom-right { content: counter(page); font-family: 'Report Sans'; font-size: 8.5pt; color: #3f4854; }
            }
            @page :first { margin-top: 0; @top-left { content: none; } }
            body { font-family: 'Report Sans', sans-serif; font-size: 9.5pt; color: #1a1f27; line-height: 1.45; }
            b { font-weight: 600; }
            .r { text-align: right; }
            .c { text-align: center; }
            .mono { font-family: 'Report Mono', monospace; font-size: 8.5pt; }
            .running-header { position: running(header); width: 176mm; }
            table.rh { width: 100%; border-collapse: collapse; border-bottom: 0.75pt solid #d6dbe3;
              font-family: 'Report Condensed'; font-weight: 600; font-size: 7.5pt; letter-spacing: 0.5pt;
              text-transform: uppercase; color: #3f4854; }
            table.rh td { padding: 0 0 4pt; }
            .running-footer { position: running(footer); font-size: 8.5pt; color: #3f4854; }

            .band { margin: 0 -17mm; padding: 13mm 17mm 8mm; background: INK; color: #ffffff; }
            table.band-top { width: 100%; border-collapse: collapse; font-family: 'Report Condensed'; font-weight: 600;
              font-size: 8pt; letter-spacing: 0.6pt; text-transform: uppercase; color: #c9d6e6; }
            .band-rule { width: 13mm; height: 3pt; margin-top: 11mm; background: OCHRE; }
            h1 { margin: 4mm 0 0; font-family: 'Report Serif'; font-weight: 700; font-size: 25pt; line-height: 1.12; }
            .band-sub { margin-top: 3.5mm; font-family: 'Report Serif'; font-style: italic; font-size: 11.5pt; color: #dbe5f1; }

            table.cols { width: 100%; border-collapse: collapse; margin-top: 7mm; table-layout: fixed; }
            td.main { vertical-align: top; padding-right: 9mm; }
            td.aside { width: 44mm; vertical-align: top; }
            .chapeau { margin: 0; font-family: 'Report Serif'; font-weight: 600; font-size: 12pt; line-height: 1.5; }
            .body { margin: 3.5mm 0 0; font-family: 'Report Serif'; font-size: 10.5pt; line-height: 1.55; }
            .body b { font-family: 'Report Serif'; font-weight: 700; }
            .scope { margin: 3mm 0 0; font-size: 8.5pt; color: #3f4854; }
            .aside-title { font-family: 'Report Condensed'; font-weight: 600; font-size: 8pt; letter-spacing: 0.6pt;
              text-transform: uppercase; color: INK; margin-bottom: 2mm; }
            .kf { padding: 3mm 0; border-top: 0.75pt solid #d6dbe3; }
            .kf-first { border-top: 1.5pt solid INK; }
            .kf-end { border-top: 0.75pt solid #d6dbe3; }
            .kf-big { font-family: 'Report Serif'; font-weight: 700; font-size: 24pt; line-height: 1; color: INK; }
            .kfv { font-family: 'Report Serif'; font-weight: 600; font-size: 19pt; line-height: 1; color: INK; }
            .kfl { margin-top: 1.5mm; font-size: 8.5pt; line-height: 1.35; color: #3f4854; }
            .track { height: 4pt; margin-top: 2mm; background: #dce6f0; }
            .track-fill { height: 4pt; background: OCHRE; }

            .fig { margin-top: 6mm; }
            .keep { page-break-inside: avoid; }
            .fig-title, .tab-title { padding-top: 2mm; border-top: 1.5pt solid OCHRE; font-size: 9.5pt; line-height: 1.35; }
            .fig-title b, .tab-title b { color: INK; }
            .tab-title { margin-top: 6mm; }
            .src { margin-top: 1.8mm; font-size: 8pt; line-height: 1.45; color: #3f4854; }
            .empty { margin: 3mm 0 0; padding: 4mm; background: #eef2f7; color: #3f4854; text-align: center; }

            .chart { position: relative; height: 42mm; margin-top: 4mm; border-bottom: 0.75pt solid #1a1f27; }
            .grid { position: absolute; left: 0; right: 0; height: 0; border-top: 0.5pt solid #e6e9ee; }
            .cumul { position: absolute; bottom: 0; background: #dce6f0; }
            .daybar { position: absolute; bottom: 0; background: INK; }
            .barlabel { position: absolute; text-align: center; font-size: 8pt; font-weight: 600; }
            .target { position: absolute; left: 0; right: 0; height: 0; border-top: 1pt dashed OCHRE; }
            .target-label { position: absolute; right: 0; font-size: 8pt; font-weight: 600; color: #8a5410; }
            .cumul-label { position: absolute; left: 0; font-size: 8pt; font-weight: 600; color: INK; }
            table.axis { width: 100%; border-collapse: collapse; table-layout: fixed; font-size: 7.5pt; color: #3f4854; }
            table.axis td { padding-top: 1.5mm; text-align: center; white-space: nowrap; }

            table.section-head { border-collapse: collapse; margin-top: 9mm; width: 100%; page-break-after: avoid; }
            table.section-head.new-page { page-break-before: always; margin-top: 0; }
            td.secn { width: 13mm; vertical-align: top; font-family: 'Report Serif'; font-weight: 700; font-size: 30pt;
              line-height: 1; color: OCHRE; }
            h2 { margin: 1.5mm 0 0; font-family: 'Report Serif'; font-weight: 700; font-size: 16pt; line-height: 1.2; color: INK; }
            .section-body { margin-left: 13mm; }

            table.bullets { width: 100%; border-collapse: collapse; margin-top: 3mm; }
            table.bullets td { padding: 1.4mm 0; vertical-align: middle; font-size: 8.5pt; }
            td.bname { width: 38mm; padding-right: 3mm !important; }
            td.btrack-cell, td.scale { padding-right: 3mm !important; }
            .btrack { position: relative; height: 10pt; }
            .bbg { position: absolute; left: 0; top: 0; height: 10pt; background: #e6edf5; }
            .bbar { position: absolute; left: 0; top: 2.5pt; height: 5pt; background: INK; }
            .btick { position: absolute; top: -2pt; width: 2pt; height: 14pt; background: OCHRE; }
            td.bval { width: 36mm; white-space: nowrap; }
            .alert { color: #8a5410; font-style: italic; }
            .muted-text { color: #5a6472; font-style: italic; }
            table.ticks { width: 100%; border-collapse: collapse; border-top: 0.5pt solid #b9c3d0; font-size: 7.5pt; color: #3f4854; }
            table.ticks td { padding-top: 1mm; }

            table.t { width: 100%; border-collapse: collapse; margin-top: 2mm; border-top: 1.5pt solid INK; font-size: 8.3pt;
              -fs-table-paginate: paginate; }
            table.t th { padding: 1.6mm 1.2mm; text-align: right; font-weight: 600; font-size: 8pt; vertical-align: bottom;
              border-bottom: 0.75pt solid #1a1f27; line-height: 1.2; }
            table.t td { padding: 1.5mm 1.2mm; text-align: right; border-bottom: 0.5pt solid #e6e9ee; white-space: nowrap; }
            table.t .l { text-align: left; padding-left: 0; white-space: normal; }
            table.t td.in { padding-left: 4mm; }
            table.t tr { page-break-inside: avoid; }
            table.t tr.grp td { font-weight: 600; color: INK; border-bottom: 0.75pt solid #b9c3d0; }
            table.t tr.muted td { color: #5a6472; font-style: italic; }
            table.t td.alert { color: #8a5410; font-weight: 600; font-style: normal; }

            table.dots { width: 100%; border-collapse: collapse; margin-top: 3mm; font-size: 8.5pt; }
            table.dots th { font-weight: 600; font-size: 7.5pt; text-align: right; padding: 0 0 1mm 2mm; color: #3f4854; }
            table.dots td { padding: 1.3mm 0 1.3mm 2mm; text-align: right; vertical-align: middle; }
            table.dots .l { width: 30mm; text-align: left; padding-left: 0; }
            .dtrack-cell { width: 58%; padding-right: 4mm !important; padding-left: 3mm !important; }
            .zone-label { text-align: left; font-weight: 400; font-style: italic; color: #8a5410; }
            .dtrack { position: relative; height: 10pt; }
            .zone { position: absolute; left: 0; top: 0; height: 10pt; background: #fbf3e7; }
            .threshold { position: absolute; top: -1pt; width: 0; height: 12pt; border-left: 0.75pt dashed OCHRE; }
            .dline { position: absolute; left: 0; right: 0; top: 4.5pt; height: 0; border-top: 0.5pt solid #d6dbe3; }
            .dot { position: absolute; top: 1pt; width: 8pt; height: 8pt; margin-left: -4pt; border-radius: 4pt; background: INK; }
            .dot-alert { background: OCHRE; }

            .var { margin-top: 7mm; }
            .var-meta { margin-top: 1mm; font-size: 8pt; color: #3f4854; }
            .var-note { margin-top: 1.5mm; font-family: 'Report Serif'; font-style: italic; font-size: 9.5pt; color: #1a1f27; }
            table.dist { width: 100%; border-collapse: collapse; margin-top: 3mm; font-size: 8.8pt; }
            table.dist td { padding: 1.1mm 0; vertical-align: middle; }
            td.dlabel { width: 40%; padding-right: 3mm !important; }
            .dbar { display: inline-block; height: 9pt; background: INK; vertical-align: middle; }
            .dzero { display: inline-block; width: 1pt; height: 9pt; background: #b9c3d0; vertical-align: middle; }
            .dval { margin-left: 2mm; white-space: nowrap; }
            table.stats { width: 100%; border-collapse: collapse; margin-top: 3mm; table-layout: fixed;
              border-top: 0.75pt solid #d6dbe3; border-bottom: 0.75pt solid #d6dbe3; }
            table.stats td { padding: 2mm 0; }
            .stat-label { font-size: 7.5pt; color: #3f4854; }
            .stat-value { font-family: 'Report Serif'; font-weight: 600; font-size: 12pt; color: INK; }
            .aside-note { margin-top: 6mm; padding: 2.5mm 0; border-top: 0.5pt solid #e6e9ee; border-bottom: 0.5pt solid #e6e9ee;
              font-size: 8.5pt; line-height: 1.5; color: #2c333d; }

            .box { margin-top: 9mm; padding: 5mm 6mm 2mm; background: #eef2f7; border-top: 2.5pt solid INK; page-break-inside: avoid; }
            .box-title { font-family: 'Report Condensed'; font-weight: 600; font-size: 8pt; letter-spacing: 0.6pt;
              text-transform: uppercase; color: INK; }
            table.defs { width: 100%; border-collapse: collapse; margin-top: 3mm; table-layout: fixed; }
            table.defs td { width: 50%; vertical-align: top; padding: 0 5mm 3mm 0; }
            .dt { font-weight: 600; font-size: 8.5pt; color: INK; }
            .dd { margin-top: 0.5mm; font-size: 8.5pt; line-height: 1.45; color: #2c333d; }
            table.colophon { width: 100%; border-collapse: collapse; margin-top: 6mm; font-size: 8pt; color: #3f4854; }
            td.sig { font-family: 'Report Serif'; font-style: italic; font-size: 9.5pt; color: INK; white-space: nowrap; padding-left: 6mm; }
            """.replace("INK", INK).replace("OCHRE", OCHRE);
}
