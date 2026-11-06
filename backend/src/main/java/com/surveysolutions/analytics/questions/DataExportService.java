package com.surveysolutions.analytics.questions;

import static com.surveysolutions.analytics.surveysolutions.SurveySolutionsDtos.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import com.surveysolutions.analytics.surveysolutions.SurveySolutionsService;

@Service
public class DataExportService {
    private static final int MAX_INTERVIEWS_PER_EXPORT = 5_000;
    private static final int MAX_EXCEL_ROWS = 1_048_576;
    private static final int MAX_EXCEL_CELL_LENGTH = 32_767;
    private static final List<String> HEADERS = List.of(
            "N° interview",
            "Statut de l’interview",
            "Dernière mise à jour",
            "Nom de la variable",
            "Question",
            "Type de question",
            "Section du questionnaire",
            "Réponse",
            "Format de la réponse",
            "Informations complémentaires");
    private static final MediaType XLSX_MEDIA_TYPE =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final SurveySolutionsService surveySolutions;
    private final ExecutorService executor;
    private final ObjectMapper objectMapper;

    private record InterviewAnswers(int number, Interview interview, List<InterviewAnswer> answers) {}

    private record ExportRow(List<String> cells) {}

    public record ExportFile(String filename, MediaType contentType, byte[] content) {}

    public enum Format {
        CSV,
        XLSX;

        public static Format parse(String value) {
            if (value == null) {
                throw new IllegalArgumentException("Le format d’export est obligatoire (csv ou xlsx).");
            }
            return switch (value.toLowerCase(java.util.Locale.ROOT)) {
                case "csv" -> CSV;
                case "xlsx" -> XLSX;
                default -> throw new IllegalArgumentException(
                        "Format d’export non pris en charge. Utilisez csv ou xlsx.");
            };
        }

        private String extension() {
            return this == CSV ? "csv" : "xlsx";
        }
    }

    public DataExportService(
            SurveySolutionsService surveySolutions,
            ExecutorService analyticsExecutor,
            ObjectMapper objectMapper) {
        this.surveySolutions = surveySolutions;
        this.executor = analyticsExecutor;
        this.objectMapper = objectMapper;
    }

    public ExportFile export(UUID questionnaireId, long version, Format format) {
        if (version < 1) {
            throw new IllegalArgumentException("La version doit être supérieure à zéro.");
        }

        List<Question> questions = surveySolutions.getQuestions(questionnaireId, version);
        List<Interview> interviews = surveySolutions.getInterviews(questionnaireId, version);
        if (interviews.size() > MAX_INTERVIEWS_PER_EXPORT) {
            throw new IllegalArgumentException(
                    "Cet export demanderait " + interviews.size()
                            + " lectures d’interviews. La limite de sécurité actuelle est de "
                            + MAX_INTERVIEWS_PER_EXPORT
                            + "; contactez l’administrateur pour choisir une stratégie adaptée au volume.");
        }

        List<ExportRow> rows = loadRows(interviews, questions);
        byte[] content = format == Format.CSV ? toCsv(rows) : toXlsx(rows);
        String filename = "survey-solutions-" + questionnaireId + "-v" + version
                + "." + format.extension();
        return new ExportFile(
                filename,
                format == Format.CSV ? new MediaType("text", "csv", StandardCharsets.UTF_8) : XLSX_MEDIA_TYPE,
                content);
    }

    private List<ExportRow> loadRows(List<Interview> interviews, List<Question> questions) {
        Map<String, Question> questionsByVariable = questions.stream()
                .filter(question -> question.variableName() != null)
                .collect(java.util.stream.Collectors.toMap(
                        Question::variableName,
                        question -> question,
                        (first, ignored) -> first));

        List<CompletableFuture<InterviewAnswers>> requests =
                java.util.stream.IntStream.range(0, interviews.size())
                        .mapToObj(index -> {
                            Interview interview = interviews.get(index);
                            return CompletableFuture.supplyAsync(() -> {
                                List<InterviewAnswer> answers = surveySolutions.getInterviewDetails(
                                                UUID.fromString(interview.interviewId()))
                                        .answers();
                                return new InterviewAnswers(index + 1, interview, answers);
                            }, executor);
                        })
                        .toList();

        List<ExportRow> rows = new ArrayList<>();
        try {
            for (CompletableFuture<InterviewAnswers> request : requests) {
                InterviewAnswers interviewAnswers = request.join();
                for (InterviewAnswer answer : interviewAnswers.answers()) {
                    if (answer == null) {
                        continue;
                    }
                    Question question = questionsByVariable.get(answer.variableName());
                    rows.add(toExportRow(interviewAnswers, answer, question));
                }
            }
        } catch (CompletionException exception) {
            requests.forEach(request -> request.cancel(true));
            if (exception.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw exception;
        }
        return List.copyOf(rows);
    }

    private ExportRow toExportRow(
            InterviewAnswers interviewAnswers,
            InterviewAnswer answer,
            Question question) {
        return new ExportRow(List.of(
                Integer.toString(interviewAnswers.number()),
                value(interviewAnswers.interview().status()),
                value(interviewAnswers.interview().lastEntryDate()),
                value(answer.variableName()),
                question == null ? "" : value(
                        question.label() == null || question.label().isBlank()
                                ? question.questionText() : question.label()),
                question == null ? "" : value(question.type()),
                question == null || question.breadcrumbs() == null
                        ? "" : String.join(" / ", question.breadcrumbs()),
                answerValue(answer.answer()),
                answerFormat(answer.answer(), question),
                additionalFields(answer)));
    }

    private String answerValue(Object answer) {
        if (answer == null) {
            return "";
        }
        if (answer instanceof String text) {
            return text;
        }
        if (answer instanceof JsonNode node) {
            return formatNode(node);
        }
        return formatNode(objectMapper.valueToTree(answer));
    }

    private String answerFormat(Object answer, Question question) {
        if (answer == null) {
            return "Sans réponse";
        }
        if (answer instanceof Map<?, ?> || answer instanceof List<?>
                || answer instanceof JsonNode node && (node.isObject() || node.isArray())) {
            return "Donnée structurée";
        }
        if (question != null) {
            String type = question.type() == null ? "" : question.type().toLowerCase(Locale.ROOT);
            if (type.contains("numeric")) {
                return "Numérique";
            }
            if (type.contains("date")) {
                return "Date / heure";
            }
            if (type.contains("gps") || type.contains("coordinate") || type.contains("geospatial")) {
                return "Coordonnées géographiques";
            }
            if (type.contains("multi") || type.contains("multy") || type.contains("checkbox")) {
                return "Choix multiples";
            }
            if (type.contains("single") || type.contains("option")) {
                return "Choix unique";
            }
            if (type.contains("text")) {
                return "Texte";
            }
        }
        if (answer instanceof Number) {
            return "Numérique";
        }
        if (answer instanceof Boolean) {
            return "Oui / non";
        }
        if (answer instanceof JsonNode node) {
            if (node.isNumber()) {
                return "Numérique";
            }
            if (node.isBoolean()) {
                return "Oui / non";
            }
        }
        return "Texte";
    }

    private String additionalFields(InterviewAnswer answer) {
        return answer.additionalFields().entrySet().stream()
                .map(entry -> humanize(entry.getKey()) + " : " + formatNode(entry.getValue()))
                .collect(java.util.stream.Collectors.joining(" ; "));
    }

    private String formatNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return "Sans valeur";
        }
        if (node.isTextual() || node.isNumber()) {
            return node.asText();
        }
        if (node.isBoolean()) {
            return node.asBoolean() ? "Oui" : "Non";
        }
        if (node.isObject()) {
            List<String> fields = new ArrayList<>();
            Iterator<Map.Entry<String, JsonNode>> values = node.fields();
            while (values.hasNext()) {
                Map.Entry<String, JsonNode> entry = values.next();
                fields.add(humanize(entry.getKey()) + " : " + formatNode(entry.getValue()));
            }
            return fields.isEmpty() ? "Aucune donnée" : String.join(" ; ", fields);
        }
        if (node.isArray()) {
            List<String> values = new ArrayList<>();
            node.forEach(value -> values.add(formatNode(value)));
            return values.isEmpty() ? "Aucun élément" : String.join(" ; ", values);
        }
        return node.asText();
    }

    private String humanize(String key) {
        String words = key.replaceAll("([a-z0-9])([A-Z])", "$1 $2")
                .replace('_', ' ')
                .replace('-', ' ')
                .trim();
        if (words.isEmpty()) {
            return key;
        }
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    private byte[] toCsv(List<ExportRow> rows) {
        StringBuilder csv = new StringBuilder("\uFEFF");
        appendCsvRow(csv, HEADERS);
        for (ExportRow row : rows) {
            appendCsvRow(csv, row.cells());
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void appendCsvRow(StringBuilder csv, List<String> cells) {
        for (int index = 0; index < cells.size(); index++) {
            if (index > 0) {
                csv.append(',');
            }
            String value = safeCsvValue(cells.get(index));
            csv.append('"').append(value.replace("\"", "\"\"")).append('"');
        }
        csv.append("\r\n");
    }

    private String safeCsvValue(String value) {
        String result = value == null ? "" : value;
        String trimmed = result.stripLeading();
        if (result.startsWith("\t") || result.startsWith("\r") || result.startsWith("\n")
                || !trimmed.isEmpty() && "=+-@".indexOf(trimmed.charAt(0)) >= 0) {
            return "'" + result;
        }
        return result;
    }

    private byte[] toXlsx(List<ExportRow> rows) {
        SXSSFWorkbook workbook = new SXSSFWorkbook(100);
        workbook.setCompressTempFiles(true);
        try (workbook; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            CellStyle headerStyle = workbook.createCellStyle();
            var headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            Sheet sheet = newSheet(workbook, 1, headerStyle);
            int rowNumber = 1;
            int sheetNumber = 1;
            for (ExportRow data : rows) {
                for (String cell : data.cells()) {
                    if (cell.length() > MAX_EXCEL_CELL_LENGTH) {
                        throw new IllegalArgumentException(
                                "Une réponse dépasse la limite Excel de 32 767 caractères. "
                                        + "Utilisez l’export CSV pour conserver les valeurs longues.");
                    }
                }
                if (rowNumber == MAX_EXCEL_ROWS) {
                    sheet = newSheet(workbook, ++sheetNumber, headerStyle);
                    rowNumber = 1;
                }
                Row row = sheet.createRow(rowNumber++);
                for (int column = 0; column < data.cells().size(); column++) {
                    row.createCell(column).setCellValue(data.cells().get(column));
                }
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Impossible de générer le fichier Excel.", exception);
        } finally {
            workbook.dispose();
        }
    }

    private Sheet newSheet(Workbook workbook, int sheetNumber, CellStyle headerStyle) {
        Sheet sheet = workbook.createSheet(sheetNumber == 1 ? "Responses" : "Responses " + sheetNumber);
        Row header = sheet.createRow(0);
        for (int column = 0; column < HEADERS.size(); column++) {
            var cell = header.createCell(column);
            cell.setCellValue(HEADERS.get(column));
            cell.setCellStyle(headerStyle);
        }
        sheet.createFreezePane(0, 1);
        return sheet;
    }

    private String value(String value) {
        return value == null ? "" : value;
    }
}
