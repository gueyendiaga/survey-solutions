package com.surveysolutions.analytics.fieldwork;

import static com.surveysolutions.analytics.fieldwork.OperationsDtos.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.surveysolutions.analytics.questions.DataNotSynchronizedException;
import com.surveysolutions.analytics.store.AnalyticsRepository.SyncRun;
import com.surveysolutions.analytics.store.InterviewFilter;
import com.surveysolutions.analytics.store.OperationsRepository;
import com.surveysolutions.analytics.store.OperationsRepository.QueueCounts;
import com.surveysolutions.analytics.store.OperationsRepository.TabletActivity;
import com.surveysolutions.analytics.store.SyncService;
import com.surveysolutions.analytics.surveysolutions.SurveySolutionsDtos.Question;
import com.surveysolutions.analytics.surveysolutions.SurveySolutionsService;

/**
 * Pilotage des opérations de terrain à partir des métadonnées HQ : activité des tablettes, file
 * de validation, avancement par zone, mode de collecte et rendez-vous.
 */
@Service
public class OperationsService {
    private static final Logger LOG = LoggerFactory.getLogger(OperationsService.class);

    /** Au-delà, un enquêteur sans contact tablette ni interview terminée est signalé. */
    static final int INACTIVITY_DAYS = 3;
    private static final int OLDEST_LIMIT = 8;
    private static final int APPOINTMENT_HORIZON_DAYS = 14;
    private static final int APPOINTMENT_LIMIT = 12;

    private final OperationsRepository repository;
    private final SyncService sync;
    private final SurveySolutionsService surveySolutions;
    private final Clock clock;

    @Autowired
    public OperationsService(OperationsRepository repository, SyncService sync, SurveySolutionsService surveySolutions) {
        this(repository, sync, surveySolutions, Clock.systemUTC());
    }

    OperationsService(OperationsRepository repository, SyncService sync, SurveySolutionsService surveySolutions, Clock clock) {
        this.repository = repository;
        this.sync = sync;
        this.surveySolutions = surveySolutions;
        this.clock = clock;
    }

    public FieldOperations overview(UUID questionnaireId, long version, InterviewFilter filter, String zoneVariable) {
        SyncRun syncRun = sync.latestSuccessfulSync(questionnaireId, version).orElseThrow(() -> {
            sync.requestSync(questionnaireId, version, SyncService.Trigger.ON_DEMAND);
            return new DataNotSynchronizedException(
                    "Les données de cette version sont en cours de synchronisation depuis HQ. "
                            + "Les opérations de terrain s'afficheront dès la fin de l'import.");
        });
        Instant now = clock.instant();

        List<InterviewerTablet> interviewers = repository
                .tabletActivity(questionnaireId, version, filter.supervisor(), filter.interviewer())
                .stream().map(activity -> tablet(activity, now)).toList();

        return new FieldOperations(
                syncRun.finishedAt(),
                INACTIVITY_DAYS,
                summary(interviewers),
                interviewers,
                validation(questionnaireId, version, filter, now),
                zones(questionnaireId, version, filter, zoneVariable),
                repository.modes(questionnaireId, version, filter).stream()
                        .map(row -> new ModeCount(row.mode(), row.count())).toList(),
                appointments(questionnaireId, version, filter, now));
    }

    static InterviewerTablet tablet(TabletActivity activity, Instant now) {
        Instant last = latest(activity.lastTabletContact(), activity.lastCompletion());
        Long days = last == null ? null : Math.max(0, Duration.between(last, now).toDays());
        String state = activity.locked() ? "LOCKED"
                : last == null ? "NEVER"
                : days >= INACTIVITY_DAYS ? "INACTIVE"
                : "ACTIVE";
        return new InterviewerTablet(
                activity.interviewer(),
                activity.supervisor(),
                activity.lastTabletContact(),
                activity.lastCompletion(),
                days,
                activity.pendingAssignments(),
                activity.oldestPendingSince(),
                activity.locked(),
                activity.deviceLinked(),
                state);
    }

    static TabletSummary summary(List<InterviewerTablet> interviewers) {
        return new TabletSummary(
                interviewers.size(),
                interviewers.stream().filter(row -> row.state().equals("ACTIVE")).count(),
                interviewers.stream().filter(row -> row.state().equals("INACTIVE")).count(),
                interviewers.stream().filter(row -> row.state().equals("NEVER")).count(),
                interviewers.stream().mapToLong(InterviewerTablet::pendingAssignments).sum(),
                interviewers.stream().filter(row -> Boolean.FALSE.equals(row.deviceLinked())).count(),
                interviewers.stream().filter(InterviewerTablet::locked).count());
    }

    private ValidationQueue validation(UUID questionnaireId, long version, InterviewFilter filter, Instant now) {
        Map<String, QueueCounts> counts = new HashMap<>();
        repository.validationQueue(questionnaireId, version, filter).forEach(row -> counts.put(row.status(), row));
        OperationsRepository.ReviewDelays delays = repository.reviewDelays(questionnaireId, version, filter);
        List<WaitingInterview> oldest = repository.oldestWaiting(questionnaireId, version, filter, OLDEST_LIMIT)
                .stream()
                .map(row -> new WaitingInterview(
                        row.interviewId().toString(),
                        row.interviewKey(),
                        row.interviewer(),
                        row.supervisor(),
                        row.status(),
                        row.since(),
                        row.since() == null ? null
                                : Math.round(Duration.between(row.since(), now).toMinutes() / 144.0) / 10.0))
                .toList();
        return new ValidationQueue(
                stage(counts.get("Completed")),
                stage(counts.get("ApprovedBySupervisor")),
                round(delays.supervisorHours()),
                round(delays.hqHours()),
                oldest);
    }

    private static QueueStage stage(QueueCounts counts) {
        return counts == null ? new QueueStage(0, 0, 0, 0, 0)
                : new QueueStage(counts.total(), counts.lessThanOneDay(), counts.oneToThreeDays(),
                        counts.threeToSevenDays(), counts.moreThanSevenDays());
    }

    private ZoneBreakdown zones(UUID questionnaireId, long version, InterviewFilter filter, String requested) {
        List<Question> questions;
        try {
            questions = surveySolutions.getQuestions(questionnaireId, version);
        } catch (RuntimeException exception) {
            LOG.warn("Questions HQ indisponibles pour la ventilation par zone : {}", exception.getMessage());
            return new ZoneBreakdown(null, List.of(), List.of());
        }
        List<String> identifying;
        try {
            identifying = surveySolutions.getIdentifyingVariables(questionnaireId, version);
        } catch (RuntimeException exception) {
            identifying = List.of();
        }
        Set<String> mainLevel = repository.mainLevelVariables(questionnaireId, version);
        List<String> identifyingOrder = identifying;
        List<Question> zoneQuestions = questions.stream()
                .filter(question -> "SingleOption".equals(question.type())
                        && question.variableName() != null
                        && mainLevel.contains(question.variableName()))
                .sorted(Comparator.comparingInt(question -> {
                    int index = identifyingOrder.indexOf(question.variableName());
                    return index < 0 ? Integer.MAX_VALUE : index;
                }))
                .toList();
        List<ZoneOption> candidates = zoneQuestions.stream()
                .map(question -> new ZoneOption(
                        question.variableName(),
                        label(question),
                        identifyingOrder.contains(question.variableName())))
                .toList();
        String variable = candidates.stream().map(ZoneOption::variable)
                .filter(name -> name.equals(requested)).findFirst()
                .orElse(candidates.stream().filter(ZoneOption::identifying).map(ZoneOption::variable)
                        .findFirst().orElse(null));
        if (variable == null) {
            return new ZoneBreakdown(null, candidates, List.of());
        }
        Question question = zoneQuestions.stream()
                .filter(item -> item.variableName().equals(variable)).findFirst().orElseThrow();
        Map<String, String> labels = new HashMap<>();
        if (question.answers() != null) {
            question.answers().forEach(option -> labels.put(String.valueOf(option.answer()), option.text()));
        }
        List<ZoneProgress> rows = repository.zones(questionnaireId, version, variable, filter).stream()
                .map(row -> new ZoneProgress(
                        row.code(),
                        row.code() == null ? "(non renseigné)" : labels.getOrDefault(row.code(), row.code()),
                        row.interviews(),
                        row.completed(),
                        row.approved(),
                        row.rejected(),
                        row.inProgress(),
                        row.interviews() == 0 ? null : round(row.approved() * 100.0 / row.interviews())))
                .toList();
        return new ZoneBreakdown(variable, candidates, rows);
    }

    private Appointments appointments(UUID questionnaireId, long version, InterviewFilter filter, Instant now) {
        OperationsRepository.AppointmentCounts counts =
                repository.appointmentCounts(questionnaireId, version, filter.supervisor(), filter.interviewer());
        List<AppointmentItem> items = repository.openAppointments(questionnaireId, version,
                        filter.supervisor(), filter.interviewer(), APPOINTMENT_HORIZON_DAYS, APPOINTMENT_LIMIT)
                .stream()
                .map(row -> new AppointmentItem(row.startAt(), row.interviewKey(), row.assignmentId(),
                        row.responsible(), row.supervisor(), row.startAt().isBefore(now), row.comment()))
                .toList();
        return new Appointments(counts.upcoming(), counts.overdue(), items);
    }

    private static String label(Question question) {
        if (question.label() != null && !question.label().isBlank()) {
            return question.label();
        }
        return question.questionText() == null ? question.variableName() : question.questionText();
    }

    private static Instant latest(Instant first, Instant second) {
        if (first == null) {
            return second;
        }
        return second == null || first.isAfter(second) ? first : second;
    }

    private static Double round(Double value) {
        return value == null ? null : Math.round(value * 10) / 10.0;
    }
}
