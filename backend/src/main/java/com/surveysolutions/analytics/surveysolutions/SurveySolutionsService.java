package com.surveysolutions.analytics.surveysolutions;

import static com.surveysolutions.analytics.surveysolutions.SurveySolutionsDtos.*;

import java.util.ArrayList;
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

    /**
     * Affectations actives (non archivées) d'une version, toutes pages confondues. Contrairement
     * aux autres listes HQ, Offset est ici le nombre de lignes à sauter (base 0).
     */
    public List<Assignment> getAssignments(UUID questionnaireId, long version) {
        Map<Integer, Assignment> assignments = new java.util.LinkedHashMap<>();
        int totalCount;
        int pages = 0;
        do {
            int currentOffset = assignments.size();
            AssignmentPage response = client.get()
                    .uri(uri -> uri.path("/api/v1/assignments")
                            .queryParam("QuestionnaireId", questionnaireId + "$" + version)
                            .queryParam("ShowArchive", false)
                            .queryParam("Limit", QUESTIONNAIRE_PAGE_SIZE)
                            .queryParam("Offset", currentOffset)
                            .build())
                    .retrieve()
                    .body(AssignmentPage.class);
            if (response == null || response.assignments() == null) {
                throw new IllegalStateException("HQ a renvoyé une liste d'affectations invalide.");
            }
            int before = assignments.size();
            response.assignments().forEach(assignment -> assignments.putIfAbsent(assignment.id(), assignment));
            totalCount = response.totalCount();
            pages++;
            ensurePaginationProgress(assignments.size(), totalCount, assignments.size() - before, pages);
        } while (assignments.size() < totalCount);
        return List.copyOf(assignments.values());
    }

    /** Composition des équipes : nom d'enquêteur → nom de son superviseur. */
    public Map<String, String> getTeams() {
        Map<String, String> teams = new java.util.LinkedHashMap<>();
        for (User supervisor : getUsers("/api/v1/supervisors")) {
            for (User interviewer : getUsers("/api/v1/supervisors/" + supervisor.userId() + "/interviewers")) {
                if (interviewer.userName() != null) {
                    teams.put(interviewer.userName(), supervisor.userName());
                }
            }
        }
        return Map.copyOf(teams);
    }

    private List<User> getUsers(String path) {
        List<User> users = new ArrayList<>();
        int page = 1;
        int totalCount;
        int pages = 0;
        do {
            int currentPage = page;
            UserPage response = client.get()
                    .uri(uri -> uri.path(path)
                            .queryParam("limit", QUESTIONNAIRE_PAGE_SIZE)
                            .queryParam("offset", currentPage)
                            .build())
                    .retrieve()
                    .body(UserPage.class);
            if (response == null || response.users() == null) {
                throw new IllegalStateException("HQ a renvoyé une liste d'utilisateurs invalide.");
            }
            users.addAll(response.users());
            totalCount = response.totalCount();
            page++;
            pages++;
            ensurePaginationProgress(users.size(), totalCount, response.users().size(), pages);
        } while (users.size() < totalCount);
        return users;
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
     * Empreinte des données d'une version (nombre d'interviews et dernière modification), lue en
     * une requête GraphQL. Si elle n'a pas changé depuis la dernière synchronisation, l'export
     * HQ n'est pas relancé.
     */
    public String getDataFingerprint(UUID questionnaireId, long version) {
        String query = """
                query($questionnaireId: UUID!, $version: Long!) {
                  interviews(take: 1, where: {
                    questionnaireId: { eq: $questionnaireId },
                    questionnaireVersion: { eq: $version } },
                    order: [{ updateDateUtc: DESC }]) {
                    filteredCount
                    nodes { updateDateUtc }
                  }
                }""";
        JsonNode response = client.post()
                .uri("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("query", query, "variables", Map.of(
                        "questionnaireId", questionnaireId.toString(),
                        "version", version)))
                .retrieve()
                .body(JsonNode.class);
        if (response == null || response.has("errors")) {
            throw new IllegalStateException("HQ GraphQL n'a pas renvoyé l'état des interviews.");
        }
        JsonNode interviews = response.path("data").path("interviews");
        if (!interviews.has("filteredCount")) {
            throw new IllegalStateException("HQ GraphQL a renvoyé un état d'interviews invalide.");
        }
        return interviews.path("filteredCount").asLong() + "|"
                + interviews.path("nodes").path(0).path("updateDateUtc").asText("");
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
