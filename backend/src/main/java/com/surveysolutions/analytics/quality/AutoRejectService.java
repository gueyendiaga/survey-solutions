package com.surveysolutions.analytics.quality;

import static com.surveysolutions.analytics.quality.AutoRejectDtos.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.surveysolutions.analytics.dashboard.InterviewActionConflictException;
import com.surveysolutions.analytics.dashboard.InterviewActionService;
import com.surveysolutions.analytics.store.SyncService;
import org.springframework.context.event.EventListener;
import com.surveysolutions.analytics.quality.AutoRejectRepository.PendingInterview;

/**
 * Rejet automatique des interviews qui enfreignent des règles qualité définies par version.
 *
 * <p>Trois modes : désactivé, simulation (liste des interviews concernées, aucune action) et
 * automatique (rejet HQ après chaque synchronisation). Garde-fous : une interview n'est rejetée
 * qu'une fois par soumission, et plus du tout au-delà d'un nombre de rejets HQ, pour laisser la
 * main au superviseur.
 */
@Service
public class AutoRejectService {
    private static final Logger LOG = LoggerFactory.getLogger(AutoRejectService.class);
    private static final int MAX_RULES = 30;
    private static final Set<RuleType> NEEDS_VARIABLE = EnumSet.of(RuleType.REQUIRED_ANSWER, RuleType.VALUE_RANGE);
    private static final String COMMENT_HEADER = "Rejet automatique (contrôle qualité) :";

    private final AutoRejectRepository repository;
    private final InterviewActionService interviewActions;

    public AutoRejectService(AutoRejectRepository repository, InterviewActionService interviewActions) {
        this.repository = repository;
        this.interviewActions = interviewActions;
    }

    public Overview overview(UUID questionnaireId, long version) {
        return new Overview(repository.settings(questionnaireId, version),
                repository.recentEvents(questionnaireId, version, 50));
    }

    public Settings save(UUID questionnaireId, long version, SettingsUpdate update, String user) {
        if (update == null || update.mode() == null) {
            throw new IllegalArgumentException("Le mode de rejet automatique est obligatoire.");
        }
        List<Rule> rules = update.rules() == null ? List.of() : update.rules();
        if (rules.size() > MAX_RULES) {
            throw new IllegalArgumentException("Au plus " + MAX_RULES + " règles par questionnaire.");
        }
        int maxRejections = update.maxHqRejections() == null ? 2 : update.maxHqRejections();
        if (maxRejections < 1 || maxRejections > 10) {
            throw new IllegalArgumentException("Le plafond de rejets HQ doit être compris entre 1 et 10.");
        }
        List<Rule> cleaned = rules.stream().map(rule -> validate(questionnaireId, version, rule)).toList();
        if (update.mode() == Mode.AUTO && cleaned.stream().noneMatch(Rule::enabled)) {
            throw new IllegalArgumentException("Activez au moins une règle avant de passer en mode automatique.");
        }
        repository.save(questionnaireId, version, update.mode(), maxRejections, cleaned, user);
        return repository.settings(questionnaireId, version);
    }

    /** Évalue les règles actives sans rien modifier dans HQ. */
    public Preview preview(UUID questionnaireId, long version) {
        Settings settings = repository.settings(questionnaireId, version);
        List<PendingInterview> pending = repository.pending(questionnaireId, version);
        Map<UUID, List<Violation>> violations = new LinkedHashMap<>();
        List<RuleCount> perRule = new ArrayList<>();
        for (Rule rule : settings.rules()) {
            if (!rule.enabled()) {
                continue;
            }
            List<AutoRejectRepository.Hit> hits = repository.hits(questionnaireId, version, rule);
            perRule.add(new RuleCount(rule.id(), rule.type(), rule.variable(), hits.size()));
            for (AutoRejectRepository.Hit hit : hits) {
                violations.computeIfAbsent(hit.interviewId(), id -> new ArrayList<>())
                        .add(new Violation(rule.id(), rule.type(), rule.variable(), reason(rule, hit.detail())));
            }
        }
        List<Candidate> candidates = pending.stream()
                .filter(interview -> violations.containsKey(interview.interviewId()))
                .map(interview -> new Candidate(
                        interview.interviewId(),
                        interview.interviewKey(),
                        interview.status(),
                        interview.responsible(),
                        interview.hqRejections(),
                        interview.hqRejections() >= settings.maxHqRejections(),
                        violations.get(interview.interviewId())))
                .toList();
        return new Preview(pending.size(), candidates, perRule);
    }

    /** Après une synchronisation réussie : ne fait rien hors du mode automatique. */
    @EventListener
    public void afterSync(SyncService.VersionSynchronized event) {
        if (repository.settings(event.questionnaireId(), event.version()).mode() == Mode.AUTO) {
            run(event.questionnaireId(), event.version());
        }
    }

    /** Rejette dans HQ les interviews en infraction (mode automatique uniquement). */
    public RunResult run(UUID questionnaireId, long version) {
        Settings settings = repository.settings(questionnaireId, version);
        if (settings.mode() != Mode.AUTO) {
            throw new InterviewActionConflictException(
                    "Le rejet automatique n’est pas activé pour cette version : passez en mode automatique.");
        }
        Preview preview = preview(questionnaireId, version);
        Map<UUID, PendingInterview> pending = repository.pending(questionnaireId, version).stream()
                .collect(Collectors.toMap(PendingInterview::interviewId, interview -> interview));
        int rejected = 0;
        int skipped = 0;
        int handled = 0;
        int failed = 0;
        for (Candidate candidate : preview.candidates()) {
            PendingInterview interview = pending.get(candidate.interviewId());
            if (interview == null) {
                continue;
            }
            if (repository.alreadyHandled(interview.interviewId(), interview.completedAt())) {
                handled++;
                continue;
            }
            String reasons = candidate.violations().stream()
                    .map(violation -> "- " + violation.reason())
                    .collect(Collectors.joining("\n"));
            if (candidate.overLimit()) {
                repository.recordEvent(questionnaireId, version, interview, "SKIPPED_LIMIT", reasons, null);
                skipped++;
                continue;
            }
            try {
                interviewActions.reject(questionnaireId, version, interview.interviewId(),
                        COMMENT_HEADER + "\n" + reasons, null);
                repository.recordEvent(questionnaireId, version, interview, "REJECTED", reasons, null);
                rejected++;
            } catch (RuntimeException exception) {
                LOG.warn("Rejet automatique de {} impossible : {}", interview.interviewKey(), exception.getMessage());
                repository.recordEvent(questionnaireId, version, interview, "FAILED", reasons, exception.getMessage());
                failed++;
            }
        }
        LOG.info("Rejet automatique {} v{} : {} rejetée(s), {} au plafond, {} déjà traitée(s), {} échec(s).",
                questionnaireId, version, rejected, skipped, handled, failed);
        return new RunResult(preview.evaluated(), rejected, skipped, handled, failed);
    }

    private Rule validate(UUID questionnaireId, long version, Rule rule) {
        if (rule == null || rule.type() == null) {
            throw new IllegalArgumentException("Chaque règle doit avoir un type.");
        }
        String variable = rule.variable() == null ? null : rule.variable().trim();
        if (NEEDS_VARIABLE.contains(rule.type())) {
            if (variable == null || variable.isEmpty()) {
                throw new IllegalArgumentException("Choisissez la variable contrôlée par chaque règle.");
            }
            if (repository.variableExists(questionnaireId, version, variable).filter(exists -> !exists).isPresent()) {
                throw new IllegalArgumentException("La variable « " + variable + " » n’existe pas dans les données.");
            }
        } else {
            variable = null;
        }
        BigDecimal min = rule.minValue();
        BigDecimal max = rule.maxValue();
        switch (rule.type()) {
            case VALIDATION_ERRORS -> requirePositive(min, "Le nombre minimal d’erreurs");
            case MIN_DURATION -> requirePositive(min, "La durée minimale");
            case MAX_UNANSWERED -> {
                if (max == null || max.signum() < 0) {
                    throw new IllegalArgumentException("Indiquez le nombre maximal de questions sans réponse.");
                }
            }
            case VALUE_RANGE -> {
                if (min == null && max == null) {
                    throw new IllegalArgumentException("Indiquez au moins une borne pour la plage de valeurs.");
                }
                if (min != null && max != null && min.compareTo(max) > 0) {
                    throw new IllegalArgumentException("La borne minimale dépasse la borne maximale.");
                }
            }
            case REQUIRED_ANSWER -> { }
        }
        String message = rule.message() == null || rule.message().isBlank() ? null : rule.message().trim();
        if (message != null && message.length() > 300) {
            throw new IllegalArgumentException("Le message d’une règle ne peut pas dépasser 300 caractères.");
        }
        return new Rule(rule.id(), rule.type(), variable, min, max, message, rule.enabled());
    }

    private static void requirePositive(BigDecimal value, String label) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(label + " doit être supérieur à zéro.");
        }
    }

    /** Motif lu par l'enquêteur dans le commentaire de rejet : le message personnalisé prime. */
    static String reason(Rule rule, String detail) {
        if (rule.message() != null) {
            return rule.message();
        }
        return switch (rule.type()) {
            case VALIDATION_ERRORS -> detail + " erreur(s) de validation à corriger";
            case MIN_DURATION -> "Durée d’interview trop courte (" + detail + " min, minimum "
                    + plain(rule.minValue()) + " min)";
            case MAX_UNANSWERED -> detail + " question(s) sans réponse (maximum " + plain(rule.maxValue()) + ")";
            case REQUIRED_ANSWER -> "Réponse manquante à « " + rule.variable() + " »";
            case VALUE_RANGE -> "Valeur hors plage pour « " + rule.variable() + " » (" + detail + ", attendu "
                    + range(rule) + ")";
        };
    }

    private static String range(Rule rule) {
        if (rule.minValue() != null && rule.maxValue() != null) {
            return "entre " + plain(rule.minValue()) + " et " + plain(rule.maxValue());
        }
        return rule.minValue() != null ? "au moins " + plain(rule.minValue()) : "au plus " + plain(rule.maxValue());
    }

    private static String plain(BigDecimal value) {
        return value == null ? "—" : value.stripTrailingZeros().toPlainString();
    }
}
