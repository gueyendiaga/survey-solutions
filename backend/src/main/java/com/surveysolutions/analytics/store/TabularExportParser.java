package com.surveysolutions.analytics.store;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.springframework.stereotype.Component;

/**
 * Lit une archive d'export tabulaire Survey Solutions (fichiers .tab séparés par des tabulations).
 *
 * <p>Conventions de l'export HQ exploitées ici :
 * <ul>
 *   <li>cellule vide : question désactivée (non applicable) ;</li>
 *   <li>{@code -999999999} ou {@code ##N/A##} : question active mais sans réponse ;</li>
 *   <li>colonnes {@code variable__suffixe} : choix multiples ({@code __code}), listes de textes
 *       ({@code __0}, {@code __1}…), GPS ({@code __Latitude}…) ;</li>
 *   <li>un fichier par niveau : fichier principal (variable du questionnaire) et un fichier par
 *       roster, identifié par ses colonnes {@code ROSTER__id}.</li>
 * </ul>
 */
@Component
public class TabularExportParser {
    public static final String ANSWERED = "A";
    public static final String MISSING = "M";
    public static final String DISABLED = "D";

    private static final Set<String> MISSING_CODES = Set.of("-999999999", "##N/A##");
    private static final Set<String> SYSTEM_COLUMNS = Set.of(
            "interview__key", "interview__id", "sssys_irnd", "has__errors",
            "interview__status", "assignment__id");
    private static final Set<String> SERVICE_FILE_PREFIXES = Set.of("interview__", "assignment__");
    private static final Map<Integer, String> STATUS_CODES = Map.ofEntries(
            Map.entry(-1, "Deleted"),
            Map.entry(0, "Restored"),
            Map.entry(20, "Created"),
            Map.entry(40, "SupervisorAssigned"),
            Map.entry(60, "InterviewerAssigned"),
            Map.entry(65, "RejectedBySupervisor"),
            Map.entry(80, "ReadyForInterview"),
            Map.entry(85, "SentToCapi"),
            Map.entry(95, "Restarted"),
            Map.entry(100, "Completed"),
            Map.entry(120, "ApprovedBySupervisor"),
            Map.entry(125, "RejectedByHeadquarters"),
            Map.entry(130, "ApprovedByHeadquarters"));

    /** État et valeur normalisée d'une variable pour une unité. value est null sauf si ANSWERED. */
    public record AnswerState(String state, String value) {}

    public record InterviewRecord(
            UUID interviewId,
            String interviewKey,
            String status,
            Integer assignmentId,
            Boolean hasErrors,
            Integer errorsCount,
            Integer rejectionsSupervisor,
            Integer rejectionsHq,
            Integer interviewerCount,
            Long durationSeconds,
            Integer unansweredCount,
            /** Enquêteur ayant terminé l'interview, ou à défaut son dernier enquêteur responsable. */
            String interviewerName,
            Instant createdAt,
            /** Première complétion par l'enquêteur (action Completed) ; null si jamais terminée. */
            Instant completedAt,
            Instant lastActionAt) {}

    public record UnitRecord(
            UUID interviewId,
            String level,
            String rowKey,
            Map<String, AnswerState> answers) {}

    /** Reçoit les interviews (toutes d'abord), puis les unités d'observation. */
    public interface Sink {
        void interview(InterviewRecord interview);

        void unit(UnitRecord unit);
    }

    /** Codes d'action de interview__actions.tab utilisés pour le suivi terrain. */
    private static final int ACTION_COMPLETED = 3;
    private static final int ACTION_CREATED = 12;
    /** Code de rôle « enquêteur » dans les colonnes role et responsible__role. */
    private static final int ROLE_INTERVIEWER = 1;

    private static final class History {
        private String interviewer;
        private String completedBy;
        private Instant createdAt;
        private Instant firstAction;
        private Instant completedAt;
        private Instant lastAction;
        private Instant lastCompletion;
        private Instant lastInterviewerResponsibility;

        private String interviewerName() {
            return completedBy != null ? completedBy : interviewer;
        }
    }

    private record Diagnostics(
            Integer interviewerCount,
            Integer rejectionsSupervisor,
            Integer rejectionsHq,
            Integer errorsCount,
            Long durationSeconds,
            Integer unansweredCount) {}

    /**
     * @param questionTypes type HQ de chaque variable (SingleOption, MultyOption, TextList, GPS…)
     * @param mainLevel     variable du questionnaire, qui nomme le fichier principal
     */
    public void parse(Path zip, Map<String, String> questionTypes, String mainLevel, Sink sink) {
        try (ZipFile archive = new ZipFile(zip.toFile(), StandardCharsets.UTF_8)) {
            List<ZipEntry> dataFiles = new ArrayList<>();
            ZipEntry diagnosticsFile = null;
            ZipEntry actionsFile = null;
            for (ZipEntry entry : Collections.list(archive.entries())) {
                String name = entry.getName();
                if (entry.isDirectory() || name.contains("/") || !name.endsWith(".tab")) {
                    continue;
                }
                if (name.equals("interview__diagnostics.tab")) {
                    diagnosticsFile = entry;
                } else if (name.equals("interview__actions.tab")) {
                    actionsFile = entry;
                } else if (SERVICE_FILE_PREFIXES.stream().noneMatch(name::startsWith)) {
                    dataFiles.add(entry);
                }
            }

            Map<UUID, Diagnostics> diagnostics = diagnosticsFile == null
                    ? Map.of()
                    : readDiagnostics(archive, diagnosticsFile);
            Map<UUID, History> histories = actionsFile == null
                    ? Map.of()
                    : readHistories(archive, actionsFile);
            ZipEntry mainFile = findMainFile(archive, dataFiles, mainLevel);

            Set<UUID> interviews = new java.util.HashSet<>();
            readTable(archive, mainFile, (header, row) -> {
                UUID interviewId = interviewId(value(header, row, "interview__id"));
                Diagnostics diag = diagnostics.get(interviewId);
                History history = histories.get(interviewId);
                sink.interview(new InterviewRecord(
                        interviewId,
                        value(header, row, "interview__key"),
                        status(value(header, row, "interview__status")),
                        integer(value(header, row, "assignment__id")),
                        flag(value(header, row, "has__errors")),
                        diag == null ? null : diag.errorsCount(),
                        diag == null ? null : diag.rejectionsSupervisor(),
                        diag == null ? null : diag.rejectionsHq(),
                        diag == null ? null : diag.interviewerCount(),
                        diag == null ? null : diag.durationSeconds(),
                        diag == null ? null : diag.unansweredCount(),
                        history == null ? null : history.interviewerName(),
                        history == null ? null
                                : history.createdAt != null ? history.createdAt : history.firstAction,
                        history == null ? null : history.completedAt,
                        history == null ? null : history.lastAction));
                interviews.add(interviewId);
            });

            List<ZipEntry> levels = new ArrayList<>();
            levels.add(mainFile);
            dataFiles.stream().filter(entry -> entry != mainFile).forEach(levels::add);
            for (ZipEntry file : levels) {
                String level = file.getName().substring(0, file.getName().length() - 4);
                readTable(archive, file, (header, row) -> {
                    UUID interviewId = interviewId(value(header, row, "interview__id"));
                    if (!interviews.contains(interviewId)) {
                        return;
                    }
                    sink.unit(new UnitRecord(
                            interviewId,
                            level,
                            rowKey(header, row),
                            answers(header, row, questionTypes)));
                });
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Archive d'export HQ illisible.", exception);
        }
    }

    private ZipEntry findMainFile(ZipFile archive, List<ZipEntry> dataFiles, String mainLevel)
            throws IOException {
        if (mainLevel != null && !mainLevel.isBlank()) {
            for (ZipEntry entry : dataFiles) {
                if (entry.getName().equalsIgnoreCase(mainLevel + ".tab")) {
                    return entry;
                }
            }
        }
        // Repli : seul le fichier principal porte la colonne interview__status.
        for (ZipEntry entry : dataFiles) {
            if (header(archive, entry).contains("interview__status")) {
                return entry;
            }
        }
        throw new IllegalStateException("Fichier principal introuvable dans l'export HQ.");
    }

    private Map<UUID, Diagnostics> readDiagnostics(ZipFile archive, ZipEntry file) throws IOException {
        Map<UUID, Diagnostics> diagnostics = new HashMap<>();
        readTable(archive, file, (header, row) -> diagnostics.put(
                interviewId(value(header, row, "interview__id")),
                new Diagnostics(
                        integer(value(header, row, "interviewers")),
                        integer(value(header, row, "rejections__sup")),
                        integer(value(header, row, "rejections__hq")),
                        integer(value(header, row, "entities__errors")),
                        duration(value(header, row, "interview__duration")),
                        integer(value(header, row, "n_questions_unanswered")))));
        return diagnostics;
    }

    /**
     * Parcourt l'historique des actions. Les dates de l'export sont en UTC. L'enquêteur retenu est
     * celui de la dernière complétion, sinon le dernier responsable de rôle enquêteur.
     */
    private Map<UUID, History> readHistories(ZipFile archive, ZipEntry file) throws IOException {
        Map<UUID, History> histories = new HashMap<>();
        readTable(archive, file, (header, row) -> {
            Instant at = timestamp(value(header, row, "date"), value(header, row, "time"));
            Integer action = integer(value(header, row, "action"));
            if (at == null || action == null) {
                return;
            }
            History history = histories.computeIfAbsent(
                    interviewId(value(header, row, "interview__id")), ignored -> new History());
            if (history.firstAction == null || at.isBefore(history.firstAction)) {
                history.firstAction = at;
            }
            if (history.lastAction == null || at.isAfter(history.lastAction)) {
                history.lastAction = at;
            }
            if (action == ACTION_CREATED && (history.createdAt == null || at.isBefore(history.createdAt))) {
                history.createdAt = at;
            }
            if (action == ACTION_COMPLETED) {
                if (history.completedAt == null || at.isBefore(history.completedAt)) {
                    history.completedAt = at;
                }
                Integer role = integer(value(header, row, "role"));
                String originator = value(header, row, "originator");
                if (role != null && role == ROLE_INTERVIEWER && !originator.isBlank()
                        && (history.lastCompletion == null || !at.isBefore(history.lastCompletion))) {
                    history.lastCompletion = at;
                    history.completedBy = originator;
                }
            }
            Integer responsibleRole = integer(value(header, row, "responsible__role"));
            String responsible = value(header, row, "responsible__name");
            if (responsibleRole != null && responsibleRole == ROLE_INTERVIEWER && !responsible.isBlank()
                    && (history.lastInterviewerResponsibility == null
                            || !at.isBefore(history.lastInterviewerResponsibility))) {
                history.lastInterviewerResponsibility = at;
                history.interviewer = responsible;
            }
        });
        return histories;
    }

    static Instant timestamp(String date, String time) {
        if (date == null || date.isBlank()) {
            return null;
        }
        try {
            java.time.LocalDate day = java.time.LocalDate.parse(date.trim());
            java.time.LocalTime clock = time == null || time.isBlank()
                    ? java.time.LocalTime.MIDNIGHT
                    : java.time.LocalTime.parse(time.trim());
            return day.atTime(clock).toInstant(java.time.ZoneOffset.UTC);
        } catch (java.time.format.DateTimeParseException exception) {
            return null;
        }
    }

    private interface RowHandler {
        void row(Map<String, Integer> header, String[] row);
    }

    private List<String> header(ZipFile archive, ZipEntry file) throws IOException {
        try (BufferedReader reader = reader(archive, file)) {
            String line = reader.readLine();
            return line == null ? List.of() : List.of(split(stripBom(line)));
        }
    }

    private void readTable(ZipFile archive, ZipEntry file, RowHandler handler) throws IOException {
        try (BufferedReader reader = reader(archive, file)) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return;
            }
            String[] columns = split(stripBom(headerLine));
            Map<String, Integer> header = new LinkedHashMap<>();
            for (int index = 0; index < columns.length; index++) {
                header.put(columns[index], index);
            }
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isEmpty()) {
                    handler.row(header, split(line));
                }
            }
        }
    }

    private BufferedReader reader(ZipFile archive, ZipEntry file) throws IOException {
        return new BufferedReader(new InputStreamReader(
                archive.getInputStream(file), StandardCharsets.UTF_8));
    }

    private static String[] split(String line) {
        String trimmed = line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
        return trimmed.split("\t", -1);
    }

    private static String stripBom(String line) {
        return line.startsWith("﻿") ? line.substring(1) : line;
    }

    private static String value(Map<String, Integer> header, String[] row, String column) {
        Integer index = header.get(column);
        return index == null || index >= row.length ? "" : row[index];
    }

    private static String rowKey(Map<String, Integer> header, String[] row) {
        List<String> ids = new ArrayList<>();
        header.forEach((column, index) -> {
            if (column.endsWith("__id") && !SYSTEM_COLUMNS.contains(column)) {
                ids.add(index < row.length ? row[index] : "");
            }
        });
        return String.join("/", ids);
    }

    /** Regroupe les colonnes par variable (partie avant le premier « __ »). */
    static Map<String, AnswerState> answers(
            Map<String, Integer> header,
            String[] row,
            Map<String, String> questionTypes) {
        Map<String, List<String[]>> columnsByVariable = new LinkedHashMap<>();
        header.forEach((column, index) -> {
            if (SYSTEM_COLUMNS.contains(column) || column.endsWith("__id")) {
                return;
            }
            int separator = column.indexOf("__");
            String variable = separator > 0 ? column.substring(0, separator) : column;
            String suffix = separator > 0 ? column.substring(separator + 2) : null;
            String raw = index < row.length ? row[index] : "";
            columnsByVariable.computeIfAbsent(variable, ignored -> new ArrayList<>())
                    .add(new String[] {suffix, raw});
        });

        Map<String, AnswerState> answers = new LinkedHashMap<>();
        columnsByVariable.forEach((variable, columns) -> answers.put(
                variable,
                normalize(columns, questionTypes.getOrDefault(variable, ""))));
        return answers;
    }

    static AnswerState normalize(List<String[]> columns, String questionType) {
        if (columns.stream().allMatch(column -> column[1].isEmpty())) {
            return new AnswerState(DISABLED, null);
        }
        if (columns.size() == 1 && columns.get(0)[0] == null) {
            String raw = columns.get(0)[1];
            return MISSING_CODES.contains(raw)
                    ? new AnswerState(MISSING, null)
                    : new AnswerState(ANSWERED, raw);
        }

        String type = questionType.toLowerCase(Locale.ROOT);
        List<String> parts = new ArrayList<>();
        if (type.contains("multi") || type.contains("multy")) {
            // Choix multiple : __code vaut 1 (ou le rang pour un choix ordonné) si coché.
            List<String[]> selected = new ArrayList<>();
            for (String[] column : columns) {
                BigDecimal number = number(column[1]);
                if (number != null && number.signum() > 0 && !MISSING_CODES.contains(column[1])) {
                    selected.add(column);
                }
            }
            selected.sort(java.util.Comparator.comparing(column -> number(column[1])));
            selected.forEach(column -> parts.add(column[0]));
            return parts.isEmpty()
                    ? new AnswerState(MISSING, null)
                    : new AnswerState(ANSWERED, String.join(", ", parts));
        }
        if (type.contains("gps")) {
            String latitude = suffixValue(columns, "Latitude");
            String longitude = suffixValue(columns, "Longitude");
            return latitude == null || longitude == null
                    ? new AnswerState(MISSING, null)
                    : new AnswerState(ANSWERED, latitude + ", " + longitude);
        }
        boolean labelled = !type.contains("textlist") && !type.contains("list");
        for (String[] column : columns) {
            if (!column[1].isEmpty() && !MISSING_CODES.contains(column[1])) {
                parts.add(labelled ? column[0] + ": " + column[1] : column[1]);
            }
        }
        return parts.isEmpty()
                ? new AnswerState(MISSING, null)
                : new AnswerState(ANSWERED, String.join(" | ", parts));
    }

    private static String suffixValue(List<String[]> columns, String suffix) {
        for (String[] column : columns) {
            if (suffix.equalsIgnoreCase(column[0])
                    && !column[1].isEmpty()
                    && !MISSING_CODES.contains(column[1])) {
                return column[1];
            }
        }
        return null;
    }

    private static UUID interviewId(String raw) {
        String hex = raw.replace("-", "");
        if (hex.length() != 32) {
            throw new IllegalStateException("Identifiant d'interview invalide dans l'export : " + raw);
        }
        return UUID.fromString(hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-"
                + hex.substring(12, 16) + "-" + hex.substring(16, 20) + "-" + hex.substring(20));
    }

    private static String status(String raw) {
        Integer code = integer(raw);
        if (code == null) {
            return raw.isBlank() ? "Inconnu" : raw;
        }
        return STATUS_CODES.getOrDefault(code, raw);
    }

    private static BigDecimal number(String raw) {
        try {
            return raw == null || raw.isBlank() ? null : new BigDecimal(raw.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static Integer integer(String raw) {
        BigDecimal number = number(raw);
        if (number == null || MISSING_CODES.contains(raw.trim())) {
            return null;
        }
        try {
            return number.intValueExact();
        } catch (ArithmeticException exception) {
            return null;
        }
    }

    private static Boolean flag(String raw) {
        Integer value = integer(raw);
        return value == null ? null : value != 0;
    }

    /** Durée HQ au format [j.]hh:mm:ss (ex. 00.00:05:34). */
    static Long duration(String raw) {
        if (raw == null || raw.isBlank() || MISSING_CODES.contains(raw)) {
            return null;
        }
        String[] dayAndTime = raw.trim().split("\\.", 2);
        long days = 0;
        String time = raw.trim();
        if (dayAndTime.length == 2 && !dayAndTime[0].contains(":")) {
            days = Long.parseLong(dayAndTime[0]);
            time = dayAndTime[1];
        }
        String[] parts = time.split(":");
        if (parts.length != 3) {
            return null;
        }
        try {
            return days * 86_400
                    + Long.parseLong(parts[0]) * 3_600
                    + Long.parseLong(parts[1]) * 60
                    + (long) Double.parseDouble(parts[2]);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
