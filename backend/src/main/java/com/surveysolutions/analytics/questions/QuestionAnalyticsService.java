package com.surveysolutions.analytics.questions;

import static com.surveysolutions.analytics.questions.QuestionAnalyticsDtos.*;
import static com.surveysolutions.analytics.surveysolutions.SurveySolutionsDtos.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import com.surveysolutions.analytics.surveysolutions.SurveySolutionsService;

@Service
public class QuestionAnalyticsService {
    private static final int MAX_INTERVIEWS_PER_ANALYSIS = 5_000;
    private static final int NUMERIC_HISTOGRAM_BUCKETS = 8;

    private final SurveySolutionsService surveySolutions;
    private final ExecutorService executor;

    private record InterviewResponse(int interviewNumber, InterviewAnswer answer) {}

    public QuestionAnalyticsService(
            SurveySolutionsService surveySolutions,
            ExecutorService analyticsExecutor) {
        this.surveySolutions = surveySolutions;
        this.executor = analyticsExecutor;
    }

    public List<QuestionSummary> getQuestions(UUID questionnaireId, long version) {
        if (version < 1) {
            throw new IllegalArgumentException("La version doit être supérieure à zéro.");
        }
        return surveySolutions.getQuestions(questionnaireId, version)
                .stream()
                .filter(question -> question.variableName() != null
                        && !question.variableName().isBlank())
                .map(QuestionSummary::from)
                .toList();
    }

    public QuestionAnalytics analyze(
            UUID questionnaireId,
            long version,
            String variableName) {
        if (version < 1) {
            throw new IllegalArgumentException("La version doit être supérieure à zéro.");
        }
        if (variableName == null || variableName.isBlank()) {
            throw new IllegalArgumentException("La variable de question est obligatoire.");
        }

        Question question = surveySolutions.getQuestions(questionnaireId, version).stream()
                .filter(candidate -> variableName.equals(candidate.variableName()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Cette variable n'existe pas dans la version sélectionnée."));
        List<Interview> interviews = surveySolutions.getInterviews(questionnaireId, version);
        if (interviews.size() > MAX_INTERVIEWS_PER_ANALYSIS) {
            throw new IllegalArgumentException(
                    "Cette analyse demanderait " + interviews.size()
                            + " lectures d'interviews. La limite de sécurité actuelle est de "
                            + MAX_INTERVIEWS_PER_ANALYSIS
                            + "; contactez l'administrateur pour choisir une stratégie adaptée au volume.");
        }

        List<InterviewResponse> answers = fetchAnswers(interviews);
        return aggregate(question, interviews.size(), answers);
    }

    public CompletenessOverview completeness(UUID questionnaireId, long version) {
        if (version < 1) {
            throw new IllegalArgumentException("La version doit être supérieure à zéro.");
        }

        List<QuestionSummary> questions = getQuestions(questionnaireId, version);
        List<Interview> interviews = surveySolutions.getInterviews(questionnaireId, version);
        if (interviews.size() > MAX_INTERVIEWS_PER_ANALYSIS) {
            throw new IllegalArgumentException(
                    "Cette analyse demanderait " + interviews.size()
                            + " lectures d'interviews. La limite de sécurité actuelle est de "
                            + MAX_INTERVIEWS_PER_ANALYSIS
                            + "; contactez l'administrateur pour choisir une stratégie adaptée au volume.");
        }
        if (questions.isEmpty()) {
            return new CompletenessOverview(interviews.size(), List.of());
        }

        Set<String> variables = questions.stream()
                .map(QuestionSummary::variableName)
                .collect(java.util.stream.Collectors.toSet());
        Map<String, Set<Integer>> answeredInterviewNumbers = new HashMap<>();
        for (InterviewResponse response : fetchAnswers(interviews)) {
            InterviewAnswer answer = response.answer();
            String answerValue = answerText(answer.answer());
            if (variables.contains(answer.variableName())
                    && answerValue != null
                    && !answerValue.isBlank()) {
                answeredInterviewNumbers
                        .computeIfAbsent(answer.variableName(), ignored -> new HashSet<>())
                        .add(response.interviewNumber());
            }
        }

        int interviewCount = interviews.size();
        List<QuestionCompleteness> completeness = questions.stream()
                .map(question -> {
                    int answeredCount = answeredInterviewNumbers
                            .getOrDefault(question.variableName(), Set.of())
                            .size();
                    int missingCount = Math.max(0, interviewCount - answeredCount);
                    double responseRate = interviewCount == 0
                            ? 0
                            : Math.round(answeredCount * 10_000.0 / interviewCount) / 100.0;
                    return new QuestionCompleteness(
                            question, answeredCount, missingCount, responseRate);
                })
                .toList();
        return new CompletenessOverview(interviewCount, completeness);
    }

    private List<InterviewResponse> fetchAnswers(List<Interview> interviews) {
        List<CompletableFuture<List<InterviewResponse>>> requests =
                java.util.stream.IntStream.range(0, interviews.size())
                .mapToObj(index -> {
                    Interview interview = interviews.get(index);
                    return CompletableFuture.supplyAsync(
                        () -> {
                            List<InterviewAnswer> answers = surveySolutions.getInterviewDetails(
                                            UUID.fromString(interview.interviewId()))
                                    .answers();
                            return answers.stream()
                                    .map(answer -> new InterviewResponse(index + 1, answer))
                                    .toList();
                        },
                        executor);
                })
                .toList();

        List<InterviewResponse> answers = new ArrayList<>();
        try {
            for (CompletableFuture<List<InterviewResponse>> request : requests) {
                answers.addAll(request.join());
            }
        } catch (CompletionException exception) {
            requests.forEach(request -> request.cancel(true));
            if (exception.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw exception;
        }
        return answers;
    }

    private QuestionAnalytics aggregate(
            Question question,
            int interviewCount,
            List<InterviewResponse> answers) {
        Map<String, String> optionLabels = new HashMap<>();
        Map<String, Long> counts = new HashMap<>();
        if (question.answers() != null) {
            for (QuestionOption option : question.answers()) {
                if (option.text() == null || option.text().isBlank()) {
                    continue;
                }
                optionLabels.put(Integer.toString(option.answer()), option.text());
                counts.putIfAbsent(option.text(), 0L);
                optionLabels.put(normalizeAnswerKey(option.text()), option.text());
                if (option.column() != null && !option.column().isBlank()) {
                    optionLabels.put(normalizeAnswerKey(option.column()), option.text());
                }
            }
        }

        Set<String> answeredInterviews = new HashSet<>();
        List<AnswerObservation> observations = new ArrayList<>();
        List<BigDecimal> numericValues = new ArrayList<>();
        long responseCount = 0;
        long distributionCount = 0;
        int nonNumericResponseCount = 0;
        String questionType = question.type() == null ? "" : question.type().toLowerCase();
        boolean numericQuestion = isNumeric(questionType);
        boolean dateQuestion = questionType.contains("date");
        boolean multipleChoice = questionType.contains("multi")
                || questionType.contains("multy")
                || questionType.contains("checkbox");
        boolean hideValues = isText(questionType) || isGeospatial(questionType);

        for (InterviewResponse response : answers) {
            InterviewAnswer answer = response.answer();
            String answerValue = answerText(answer.answer());
            if (!question.variableName().equals(answer.variableName())
                    || answerValue == null
                    || answerValue.isBlank()) {
                continue;
            }
            responseCount++;
            answeredInterviews.add(Integer.toString(response.interviewNumber()));
            observations.add(new AnswerObservation(response.interviewNumber(), answerValue));

            if (numericQuestion) {
                try {
                    numericValues.add(new BigDecimal(answerValue.trim()));
                } catch (NumberFormatException exception) {
                    nonNumericResponseCount++;
                }
            } else if (!hideValues) {
                if (dateQuestion) {
                    counts.merge(dateLabel(answerValue.trim()), 1L, Long::sum);
                    distributionCount++;
                } else {
                    List<String> answerLabels = answerLabels(
                            answerValue.trim(), optionLabels, multipleChoice);
                    for (String label : answerLabels) {
                        counts.merge(label, 1L, Long::sum);
                        distributionCount++;
                    }
                }
            }
        }

        List<AnswerCount> distribution;
        BigDecimal minimum = null;
        BigDecimal maximum = null;
        BigDecimal mean = null;
        BigDecimal median = null;
        BigDecimal sum = null;
        BigDecimal percentile05 = null;
        BigDecimal percentile95 = null;
        if (numericQuestion && !numericValues.isEmpty()) {
            numericValues.sort(Comparator.naturalOrder());
            minimum = numericValues.get(0);
            maximum = numericValues.get(numericValues.size() - 1);
            sum = numericValues.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            mean = sum.divide(BigDecimal.valueOf(numericValues.size()), 2, RoundingMode.HALF_UP);
            median = median(numericValues);
            percentile05 = percentile(numericValues, 0.05);
            percentile95 = percentile(numericValues, 0.95);
            distribution = numericHistogram(numericValues, minimum, maximum);
        } else {
            long totalResponseCount = distributionCount;
            distribution = counts.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                            .thenComparing(Map.Entry::getKey))
                    .map(entry -> new AnswerCount(
                            entry.getKey(),
                            entry.getValue(),
                            totalResponseCount == 0 ? 0
                                    : Math.round(entry.getValue() * 10_000.0 / totalResponseCount) / 100.0))
                    .toList();
        }

        return new QuestionAnalytics(
                QuestionSummary.from(question),
                interviewCount,
                Math.min(interviewCount, answeredInterviews.size()),
                Math.max(0, interviewCount - answeredInterviews.size()),
                responseCount,
                List.copyOf(observations),
                distribution,
                minimum,
                maximum,
                mean,
                median,
                sum,
                percentile05,
                percentile95,
                nonNumericResponseCount);
    }

    private String answerText(Object answer) {
        if (answer instanceof JsonNode node) {
            return node.isTextual() ? node.asText() : node.toString();
        }
        return answer == null ? null : answer.toString();
    }

    private List<AnswerCount> numericHistogram(
            List<BigDecimal> values,
            BigDecimal minimum,
            BigDecimal maximum) {
        BigDecimal range = maximum.subtract(minimum);
        if (range.signum() == 0) {
            return List.of(new AnswerCount(minimum.stripTrailingZeros().toPlainString(), values.size(), 100));
        }

        int bucketCount = Math.min(NUMERIC_HISTOGRAM_BUCKETS, values.size());
        BigDecimal width = range.divide(BigDecimal.valueOf(bucketCount), 8, RoundingMode.CEILING);
        long[] counts = new long[bucketCount];
        for (BigDecimal value : values) {
            int index = value.subtract(minimum).divide(width, 0, RoundingMode.DOWN).intValue();
            counts[Math.min(index, bucketCount - 1)]++;
        }
        List<AnswerCount> distribution = new ArrayList<>();
        for (int index = 0; index < bucketCount; index++) {
            BigDecimal start = minimum.add(width.multiply(BigDecimal.valueOf(index)));
            BigDecimal end = index == bucketCount - 1
                    ? maximum
                    : start.add(width);
            String label = start.stripTrailingZeros().toPlainString()
                    + " – "
                    + end.stripTrailingZeros().toPlainString();
            distribution.add(new AnswerCount(
                    label,
                    counts[index],
                    Math.round(counts[index] * 10_000.0 / values.size()) / 100.0));
        }
        return List.copyOf(distribution);
    }

    private BigDecimal median(List<BigDecimal> sortedValues) {
        int middle = sortedValues.size() / 2;
        if (sortedValues.size() % 2 == 1) {
            return sortedValues.get(middle);
        }
        return sortedValues.get(middle - 1)
                .add(sortedValues.get(middle))
                .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal percentile(List<BigDecimal> sortedValues, double percentile) {
        int index = (int) Math.ceil(percentile * sortedValues.size()) - 1;
        return sortedValues.get(Math.max(0, index));
    }

    private boolean isNumeric(String type) {
        return type.contains("numeric")
                || type.contains("integer")
                || type.contains("decimal")
                || type.contains("number")
                || type.contains("double")
                || type.contains("float");
    }

    private boolean isText(String type) {
        return type.contains("text")
                || type.contains("textlist")
                || type.contains("string")
                || type.contains("memo")
                || type.contains("openended")
                || type.contains("open-ended");
    }

    private boolean isGeospatial(String type) {
        return type.contains("gps")
                || type.contains("geopoint")
                || type.contains("coordinate")
                || type.contains("geospatial");
    }

    private List<String> answerLabels(
            String answer,
            Map<String, String> optionLabels,
            boolean multipleChoice) {
        String label = optionLabels.get(normalizeAnswerKey(answer));
        if (label != null) {
            return List.of(label);
        }

        String normalized = answer.replaceAll("^[\\[({\\\"']+|[\\])}\\\"']+$", "");
        String[] parts = normalized.split("[,;|]");
        if (multipleChoice && parts.length > 1) {
            List<String> labels = new ArrayList<>();
            for (String part : parts) {
                String partLabel = optionLabels.get(normalizeAnswerKey(
                        part.trim().replaceAll("^[\\\"']+|[\\\"']+$", "")));
                if (partLabel == null) {
                    return List.of(answer);
                }
                labels.add(partLabel);
            }
            return labels;
        }
        return List.of(answer);
    }

    private String normalizeAnswerKey(String answer) {
        return answer.trim().toLowerCase(Locale.ROOT);
    }

    private String dateLabel(String value) {
        try {
            return LocalDate.parse(value).toString();
        } catch (DateTimeParseException exception) {
            try {
                return LocalDateTime.parse(value).toLocalDate().toString();
            } catch (DateTimeParseException ignored) {
                try {
                    return OffsetDateTime.parse(value).toLocalDate().toString();
                } catch (DateTimeParseException ignoredOffset) {
                    return "Date non interprétable";
                }
            }
        }
    }
}
