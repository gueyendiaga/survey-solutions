package com.surveysolutions.analytics.surveysolutions;

import static com.surveysolutions.analytics.surveysolutions.SurveySolutionsDtos.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class SurveySolutionsService {
    private static final int QUESTIONNAIRE_PAGE_SIZE = 40;
    private static final int INTERVIEW_PAGE_SIZE = 100;
    private static final int MAX_PAGES = 1000;

    private final RestClient client;

    public SurveySolutionsService(RestClient surveySolutionsClient) {
        this.client = surveySolutionsClient;
    }

    public List<Questionnaire> getQuestionnaires() {
        List<Questionnaire> questionnaires = new ArrayList<>();
        int offset = 1;
        int totalCount;
        int pages = 0;
        do {
            int currentOffset = offset;
            QuestionnairePage page = client.get()
                    .uri(uri -> uri.path("/api/v1/questionnaires")
                            .queryParam("limit", QUESTIONNAIRE_PAGE_SIZE)
                            .queryParam("offset", currentOffset)
                            .build())
                    .retrieve()
                    .body(QuestionnairePage.class);
            if (page == null || page.questionnaires() == null) {
                throw new IllegalStateException("HQ a renvoyé un catalogue de questionnaires vide ou invalide.");
            }
            questionnaires.addAll(page.questionnaires());
            totalCount = page.totalCount();
            offset++;
            pages++;
            ensurePaginationProgress(questionnaires.size(), totalCount, page.questionnaires().size(), pages);
        } while (questionnaires.size() < totalCount);
        return List.copyOf(questionnaires);
    }

    public List<Interview> getInterviews(UUID questionnaireId, Long version) {
        List<Interview> interviews = new ArrayList<>();
        int pageNumber = 1;
        int totalCount;
        int pages = 0;
        do {
            int currentPage = pageNumber;
            InterviewPage page = client.get()
                    .uri(uri -> {
                        var builder = uri.path("/api/v1/interviews")
                                .queryParam("pageSize", INTERVIEW_PAGE_SIZE)
                                .queryParam("page", currentPage);
                        if (questionnaireId != null) {
                            builder.queryParam("questionnaireId", questionnaireId);
                        }
                        if (version != null) {
                            builder.queryParam("questionnaireVersion", version);
                        }
                        return builder.build();
                    })
                    .retrieve()
                    .body(InterviewPage.class);
            if (page == null || page.interviews() == null) {
                throw new IllegalStateException("HQ a renvoyé une page d'interviews vide ou invalide.");
            }
            interviews.addAll(page.interviews());
            totalCount = page.totalCount();
            pageNumber++;
            pages++;
            ensurePaginationProgress(interviews.size(), totalCount, page.interviews().size(), pages);
        } while (interviews.size() < totalCount);
        return List.copyOf(interviews);
    }

    public int getSupervisorCount() {
        UserPage page = client.get()
                .uri(uri -> uri.path("/api/v1/supervisors")
                        .queryParam("limit", 1)
                        .queryParam("offset", 1)
                        .build())
                .retrieve()
                .body(UserPage.class);
        if (page == null) {
            throw new IllegalStateException("HQ a renvoyé une liste de superviseurs invalide.");
        }
        return page.totalCount();
    }

    public List<Question> getQuestions(UUID questionnaireId, long version) {
        Question[] questions = client.get()
                .uri(uri -> uri.path("/api/v1/statistics/questions")
                        .queryParam("questionnaireId", questionnaireId)
                        .queryParam("version", version)
                        .build())
                .retrieve()
                .body(Question[].class);
        if (questions == null) {
            throw new IllegalStateException("HQ a renvoyé une liste de questions invalide.");
        }
        if (questions.length == 0) {
            JsonNode document = client.get()
                    .uri("/api/v1/questionnaires/{id}/{version}/document", questionnaireId, version)
                    .retrieve()
                    .body(JsonNode.class);
            if (document == null || document.isNull()) {
                throw new IllegalStateException("HQ a renvoyé un document de questionnaire invalide.");
            }
            List<Question> documentQuestions = new ArrayList<>();
            collectDocumentQuestions(document, List.of(), documentQuestions);
            return List.copyOf(documentQuestions);
        }
        return List.of(questions);
    }

    private void collectDocumentQuestions(
            JsonNode node,
            List<String> breadcrumbs,
            List<Question> questions) {
        if (node.isArray()) {
            node.forEach(child -> collectDocumentQuestions(child, breadcrumbs, questions));
            return;
        }
        if (!node.isObject()) {
            return;
        }

        JsonNode variable = node.get("VariableName");
        JsonNode text = node.get("QuestionText");
        if (variable != null && variable.isTextual()
                && !variable.asText().isBlank()
                && text != null && text.isTextual()) {
            questions.add(documentQuestion(node, breadcrumbs, variable.asText(), text.asText()));
        }

        List<String> childBreadcrumbs = breadcrumbs;
        JsonNode children = node.get("Children");
        JsonNode title = node.get("Title");
        if (children != null && children.isArray()
                && !children.isEmpty() && title != null && title.isTextual()
                && !title.asText().isBlank()
                && !node.path("$type").asText().endsWith("QuestionnaireDocument")) {
            childBreadcrumbs = new ArrayList<>(breadcrumbs);
            childBreadcrumbs.add(title.asText());
        }
        if (children != null) {
            collectDocumentQuestions(children, childBreadcrumbs, questions);
        }
    }

    private Question documentQuestion(
            JsonNode node,
            List<String> breadcrumbs,
            String variableName,
            String questionText) {
        List<QuestionOption> answers = new ArrayList<>();
        JsonNode answerNodes = node.get("Answers");
        if (answerNodes != null && answerNodes.isArray()) {
            answerNodes.forEach(answer -> {
                JsonNode answerText = answer.get("AnswerText");
                JsonNode value = answer.get("AnswerValue");
                Integer code = optionCode(answer.get("AnswerCode"), value);
                if (code != null && answerText != null && answerText.isTextual()) {
                    answers.add(new QuestionOption(
                            code,
                            answerText.asText(),
                            value == null || value.isNull() ? null : value.asText()));
                }
            });
        }

        String questionType = questionType(node);
        return new Question(
                node.path("PublicKey").asText(""),
                variableName,
                questionText,
                node.path("VariableLabel").asText(""),
                questionType,
                List.copyOf(breadcrumbs),
                List.copyOf(answers));
    }

    /**
     * Le document HQ ne renseigne pas toujours AnswerCode (par exemple pour les options ajoutées
     * après la première) : AnswerValue porte alors le code de la modalité.
     */
    private Integer optionCode(JsonNode code, JsonNode value) {
        if (code != null && code.canConvertToInt()) {
            return code.asInt();
        }
        if (value != null && value.isTextual()) {
            try {
                return new java.math.BigDecimal(value.asText().trim()).intValueExact();
            } catch (NumberFormatException | ArithmeticException ignored) {
                return null;
            }
        }
        return value != null && value.canConvertToInt() ? value.asInt() : null;
    }

    /**
     * Renvoie la clé lisible (InterviewKey, ex. 01-50-90-18) de chaque interview d'une version,
     * indexée par identifiant normalisé (sans tirets, minuscules). L'API REST v1 ne fournit pas
     * cette clé : elle est lue en une requête paginée sur l'API GraphQL de HQ.
     */
    public Map<String, String> getInterviewKeys(UUID questionnaireId, long version) {
        Map<String, String> keys = new HashMap<>();
        String query = """
                query($questionnaireId: UUID!, $version: Long!, $skip: Int!, $take: Int!) {
                  interviews(skip: $skip, take: $take, where: {
                    questionnaireId: { eq: $questionnaireId },
                    questionnaireVersion: { eq: $version } }) {
                    nodes { id key }
                  }
                }""";
        for (int page = 0; page < MAX_PAGES; page++) {
            Map<String, Object> variables = Map.of(
                    "questionnaireId", questionnaireId.toString(),
                    "version", version,
                    "skip", page * INTERVIEW_PAGE_SIZE,
                    "take", INTERVIEW_PAGE_SIZE);
            JsonNode response = client.post()
                    .uri("/graphql")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("query", query, "variables", variables))
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null || response.has("errors")) {
                throw new IllegalStateException("HQ GraphQL n'a pas renvoyé les clés d'interview.");
            }
            JsonNode nodes = response.path("data").path("interviews").path("nodes");
            if (!nodes.isArray()) {
                throw new IllegalStateException("HQ GraphQL a renvoyé des clés d'interview invalides.");
            }
            nodes.forEach(node -> {
                String id = node.path("id").asText("");
                String key = node.path("key").asText("");
                if (!id.isBlank() && !key.isBlank()) {
                    keys.put(normalizeInterviewId(id), key);
                }
            });
            if (nodes.size() < INTERVIEW_PAGE_SIZE) {
                return Map.copyOf(keys);
            }
        }
        throw new IllegalStateException("La pagination HQ dépasse la limite de sécurité de 1000 pages.");
    }

    public static String normalizeInterviewId(String interviewId) {
        return interviewId == null ? "" : interviewId.replace("-", "").toLowerCase(java.util.Locale.ROOT);
    }

    private String questionType(JsonNode node) {
        JsonNode type = node.get("QuestionType");
        if (type != null && type.canConvertToInt()) {
            return switch (type.asInt()) {
                case 0 -> "SingleOption";
                case 3 -> "MultyOption";
                case 4 -> "Numeric";
                case 5 -> "DateTime";
                case 6 -> "GPS";
                case 7 -> "Text";
                case 9 -> "TextList";
                case 10 -> "QRBarcode";
                case 11 -> "Multimedia";
                case 12 -> "Area";
                case 13 -> "Audio";
                default -> "Unsupported";
            };
        }

        String documentType = node.path("$type").asText();
        if (documentType.endsWith("GpsCoordinateQuestion")) {
            return "GPS";
        }
        if (documentType.endsWith("NumericQuestion")) {
            return "Numeric";
        }
        if (documentType.endsWith("DateTimeQuestion")) {
            return "DateTime";
        }
        if (documentType.endsWith("TextQuestion")) {
            return "Text";
        }
        if (documentType.endsWith("SingleQuestion") && !node.path("Answers").isEmpty()) {
            return "SingleOption";
        }
        return "Unsupported";
    }

    public InterviewDetails getInterviewDetails(UUID interviewId) {
        InterviewDetails details = client.get()
                .uri("/api/v1/interviews/{id}", interviewId)
                .retrieve()
                .body(InterviewDetails.class);
        if (details == null || details.answers() == null) {
            throw new IllegalStateException("HQ a renvoyé les réponses d'interview dans un format invalide.");
        }
        return details;
    }

    private void ensurePaginationProgress(
            int received,
            int totalCount,
            int pageSize,
            int pages) {
        if (totalCount < 0 || pageSize == 0 && received < totalCount) {
            throw new IllegalStateException("La pagination HQ n'a pas permis de récupérer toutes les données.");
        }
        if (pages >= MAX_PAGES && received < totalCount) {
            throw new IllegalStateException("La pagination HQ dépasse la limite de sécurité de 1000 pages.");
        }
    }
}
