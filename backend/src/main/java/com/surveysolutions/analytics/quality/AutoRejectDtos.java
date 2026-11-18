package com.surveysolutions.analytics.quality;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Contrats JSON des règles de rejet automatique. */
public final class AutoRejectDtos {
    private AutoRejectDtos() {}

    public enum Mode { OFF, SIMULATION, AUTO }

    public enum RuleType { VALIDATION_ERRORS, MIN_DURATION, MAX_UNANSWERED, REQUIRED_ANSWER, VALUE_RANGE }

    public record Rule(
            Long id,
            RuleType type,
            String variable,
            BigDecimal minValue,
            BigDecimal maxValue,
            String message,
            boolean enabled) {}

    public record Settings(Mode mode, int maxHqRejections, List<Rule> rules, Instant updatedAt, String updatedBy) {}

    public record SettingsUpdate(Mode mode, Integer maxHqRejections, List<Rule> rules) {}

    public record Violation(Long ruleId, RuleType type, String variable, String reason) {}

    public record Candidate(
            UUID interviewId,
            String interviewKey,
            String status,
            String responsible,
            int hqRejections,
            boolean overLimit,
            List<Violation> violations) {}

    /** Résultat d'une évaluation : interviews concernées parmi celles en attente de validation. */
    public record Preview(int evaluated, List<Candidate> candidates, List<RuleCount> perRule) {}

    public record RuleCount(Long ruleId, RuleType type, String variable, int interviews) {}

    public record Event(
            UUID interviewId,
            String interviewKey,
            Instant evaluatedAt,
            String outcome,
            String reasons,
            String error) {}

    public record RunResult(int evaluated, int rejected, int skippedLimit, int alreadyHandled, int failed) {}

    public record Overview(Settings settings, List<Event> recentEvents) {}
}
