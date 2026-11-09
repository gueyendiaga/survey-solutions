package com.surveysolutions.analytics.questions;

import static com.surveysolutions.analytics.questions.QuestionAnalyticsDtos.*;
import static com.surveysolutions.analytics.surveysolutions.SurveySolutionsDtos.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
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
import org.springframework.stereotype.Service;
import com.surveysolutions.analytics.config.SurveySolutionsProperties;
import com.surveysolutions.analytics.store.AnalyticsRepository;
import com.surveysolutions.analytics.store.AnalyticsRepository.SyncRun;
import com.surveysolutions.analytics.store.AnalyticsRepository.VariableCompleteness;
import com.surveysolutions.analytics.store.AnalyticsRepository.VariableObservation;
import com.surveysolutions.analytics.store.SyncService;
import com.surveysolutions.analytics.store.TabularExportParser;
import com.surveysolutions.analytics.surveysolutions.SurveySolutionsService;

/**
 * Indicateurs par variable, calculés sur l'entrepôt Analytics alimenté par l'Export API.
 * Les métadonnées des questions (types, modalités) restent lues dans HQ.
 */
@Service
public class QuestionAnalyticsService {
    private static final int NUMERIC_HISTOGRAM_BUCKETS = 8;

    private final SurveySolutionsService surveySolutions;
    private final AnalyticsRepository repository;
    private final SyncService sync;
    private final List<String> analysisStatuses;

    /** Valeur répondue d'une unité (interview ou ligne de roster). */
    private record Response(int interviewNumber, String interviewKey, String rowKey, String value) {}

    /** Périmètre de l'analyse : interviews retenues et unités sans réponse de la variable. */
    private record AnalysisScope(
            int interviewCount,
            int excludedCount,
            long missingCount,
            long notApplicableCount,
            Instant syncedAt) {}

    public QuestionAnalyticsService(
            SurveySolutionsService surveySolutions,
            AnalyticsRepository repository,
            SyncService sync,
            SurveySolutionsProperties properties) {
        this.surveySolutions = surveySolutions;
        this.repository = repository;
        this.sync = sync;
        this.analysisStatuses = properties.getAnalysisStatuses().stream()
                .map(String::trim)
                .filter(status -> !status.isEmpty())
                .toList();
        if (analysisStatuses.isEmpty()) {
            throw new IllegalStateException(
                    "ANALYTICS_STATUSES doit contenir au moins un statut d'interview HQ.");
        }
    }

    public List<QuestionSummary> getQuestions(UUID questionnaireId, long version) {
        requireVersion(version);
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
        requireVersion(version);
        if (variableName == null || variableName.isBlank()) {
            throw new IllegalArgumentException("La variable de question est obligatoire.");
        }

        Question question = surveySolutions.getQuestions(questionnaireId, version).stream()
                .filter(candidate -> variableName.equals(candidate.variableName()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Cette variable n'existe pas dans la version sélectionnée."));
        SyncRun syncRun = requireSynchronizedData(questionnaireId, version);

        List<VariableObservation> rows = repository.observations(
                questionnaireId, version, variableName, analysisStatuses);
        Map<UUID, Integer> interviewNumbers = new HashMap<>();
        List<Response> responses = new ArrayList<>();
        long missing = 0;
        long notApplicable = 0;
        for (VariableObservation row : rows) {
            int number = interviewNumbers.computeIfAbsent(
                    row.interviewId(), ignored -> interviewNumbers.size() + 1);
            switch (row.state()) {
                case TabularExportParser.ANSWERED -> responses.add(new Response(
                        number, row.interviewKey(), row.rowKey(), row.value()));
                case TabularExportParser.MISSING -> missing++;
                default -> notApplicable++;
            }
        }

        int interviewCount = repository.countInterviews(questionnaireId, version, analysisStatuses);
        int excluded = repository.countAllInterviews(questionnaireId, version) - interviewCount;
        return aggregate(question, new AnalysisScope(
                interviewCount, excluded, missing, notApplicable, syncRun.finishedAt()), responses);
    }

    public CompletenessOverview completeness(UUID questionnaireId, long version) {
        requireVersion(version);
        List<QuestionSummary> questions = getQuestions(questionnaireId, version);
        SyncRun syncRun = requireSynchronizedData(questionnaireId, version);

        int interviewCount = repository.countInterviews(questionnaireId, version, analysisStatuses);
        int excluded = repository.countAllInterviews(questionnaireId, version) - interviewCount;
        Map<String, VariableCompleteness> byVariable = new HashMap<>();
        repository.completeness(questionnaireId, version, analysisStatuses)
                .forEach(item -> byVariable.put(item.variable(), item));

        List<QuestionCompleteness> completeness = questions.stream()
                .map(question -> {
                    VariableCompleteness item = byVariable.get(question.variableName());
                    long answered = item == null ? 0 : item.answeredUnits();
                    long missing = item == null ? 0 : item.missingUnits();
                    long notApplicable = item == null ? 0 : item.disabledUnits();
                    return new QuestionCompleteness(
                            question,
                            item == null ? null : item.level(),
                            answered,
                            missing,
                            notApplicable,
                            item == null ? 0 : item.answeredInterviews(),
                            rate(answered, answered + missing));
                })
                .toList();
        return new CompletenessOverview(
                interviewCount, completeness, analysisStatuses, excluded, syncRun.finishedAt());
    }

    /**
     * Les analyses lisent l'entrepôt. Sans synchronisation réussie, une synchronisation est
     * demandée et l'appelant est invité à réessayer.
     */
    private SyncRun requireSynchronizedData(UUID questionnaireId, long version) {
        return sync.latestSuccessfulSync(questionnaireId, version).orElseThrow(() -> {
            sync.requestSync(questionnaireId, version, SyncService.Trigger.ON_DEMAND);
            return new DataNotSynchronizedException(
                    "Les données de cette version sont en cours de synchronisation depuis HQ. "
                            + "Les indicateurs s'afficheront dès la fin de l'import.");
        });
    }

    private static void requireVersion(long version) {
        if (version < 1) {
            throw new IllegalArgumentException("La version doit être supérieure à zéro.");
        }
    }

    /** Taux en pourcentage arrondi à deux décimales ; 0 si le dénominateur est nul. */
    private static double rate(long numerator, long denominator) {
        return denominator == 0 ? 0 : Math.round(numerator * 10_000.0 / denominator) / 100.0;
    }

    private QuestionAnalytics aggregate(
            Question question,
            AnalysisScope scope,
            List<Response> answers) {
        int interviewCount = scope.interviewCount();
        String questionType = question.type() == null ? "" : question.type().toLowerCase();
        boolean numericQuestion = isNumeric(questionType);
        Map<String, String> optionLabels = new HashMap<>();
        Map<String, Long> counts = new HashMap<>();
        // Pour une question numérique, les modalités du document sont des valeurs spéciales
        // (ex. 98 = Ne sait pas, 99 = Refus) : elles sont comptées à part, hors statistiques.
        Map<BigDecimal, String> specialValueLabels = new HashMap<>();
        Map<String, Long> specialValueCounts = new java.util.LinkedHashMap<>();
        if (numericQuestion && question.answers() != null) {
            for (QuestionOption option : question.answers()) {
                String label = option.text() == null || option.text().isBlank()
                        ? Integer.toString(option.answer())
                        : option.text();
                specialValueLabels.put(BigDecimal.valueOf(option.answer()), label);
                specialValueCounts.putIfAbsent(label, 0L);
            }
        }
        if (!numericQuestion && question.answers() != null) {
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
        boolean dateQuestion = questionType.contains("date");
        boolean multipleChoice = questionType.contains("multi")
                || questionType.contains("multy")
                || questionType.contains("checkbox");
        boolean hideValues = isText(questionType) || isGeospatial(questionType);

        for (Response response : answers) {
            String answerValue = response.value();
            if (answerValue == null || answerValue.isBlank()) {
                continue;
            }
            responseCount++;
            answeredInterviews.add(Integer.toString(response.interviewNumber()));
            observations.add(new AnswerObservation(
                    response.interviewNumber(), response.interviewKey(), response.rowKey(), answerValue));

            if (numericQuestion) {
                try {
                    BigDecimal value = new BigDecimal(answerValue.trim());
                    String specialLabel = specialValueLabel(specialValueLabels, value);
                    if (specialLabel != null) {
                        specialValueCounts.merge(specialLabel, 1L, Long::sum);
                    } else {
                        numericValues.add(value);
                    }
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
        } else if (!numericQuestion) {
            // Choix multiples : une réponse peut cocher plusieurs modalités. Le pourcentage se
            // rapporte alors aux réponses non vides (répondants), pas au nombre de cases cochées.
            long totalResponseCount = multipleChoice ? responseCount : distributionCount;
            distribution = counts.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                            .thenComparing(Map.Entry::getKey))
                    .map(entry -> new AnswerCount(
                            entry.getKey(),
                            entry.getValue(),
                            totalResponseCount == 0 ? 0
                                    : Math.round(entry.getValue() * 10_000.0 / totalResponseCount) / 100.0))
                    .toList();
        } else {
            distribution = List.of();
        }

        long specialValueBase = responseCount;
        List<AnswerCount> specialValues = specialValueCounts.entrySet().stream()
                .map(entry -> new AnswerCount(
                        entry.getKey(),
                        entry.getValue(),
                        specialValueBase == 0 ? 0
                                : Math.round(entry.getValue() * 10_000.0 / specialValueBase) / 100.0))
                .toList();
        PercentageBase percentageBase = numericQuestion
                ? PercentageBase.NUMERIC_VALUES
                : multipleChoice ? PercentageBase.RESPONDENTS : PercentageBase.RESPONSES;

        return new QuestionAnalytics(
                QuestionSummary.from(question),
                interviewCount,
                Math.min(interviewCount, answeredInterviews.size()),
                responseCount,
                scope.missingCount(),
                scope.notApplicableCount(),
                rate(responseCount, responseCount + scope.missingCount()),
                List.copyOf(observations),
                distribution,
                minimum,
                maximum,
                mean,
                median,
                sum,
                percentile05,
                percentile95,
                nonNumericResponseCount,
                percentageBase,
                specialValues,
                analysisStatuses,
                scope.excludedCount(),
                scope.syncedAt());
    }

    private String specialValueLabel(Map<BigDecimal, String> labels, BigDecimal value) {
        if (labels.isEmpty()) {
            return null;
        }
        for (Map.Entry<BigDecimal, String> entry : labels.entrySet()) {
            if (entry.getKey().compareTo(value) == 0) {
                return entry.getValue();
            }
        }
        return null;
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
