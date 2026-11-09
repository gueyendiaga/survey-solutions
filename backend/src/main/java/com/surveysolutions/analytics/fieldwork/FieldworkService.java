package com.surveysolutions.analytics.fieldwork;

import static com.surveysolutions.analytics.fieldwork.FieldworkDtos.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import com.surveysolutions.analytics.questions.DataNotSynchronizedException;
import com.surveysolutions.analytics.store.AnalyticsRepository.SyncRun;
import com.surveysolutions.analytics.store.FieldworkRepository;
import com.surveysolutions.analytics.store.FieldworkRepository.AssignmentTarget;
import com.surveysolutions.analytics.store.FieldworkRepository.InterviewerActivity;
import com.surveysolutions.analytics.store.InterviewFilter;
import com.surveysolutions.analytics.store.SyncService;

/** Taux de réalisation par rapport aux affectations, par équipe et par enquêteur. */
@Service
public class FieldworkService {
    /** Statuts HQ proposés dans les filtres (ordre du cycle de vie d'une interview). */
    private static final List<String> STATUSES = List.of(
            "SupervisorAssigned", "InterviewerAssigned", "Restarted", "Completed",
            "RejectedBySupervisor", "ApprovedBySupervisor", "RejectedByHeadquarters",
            "ApprovedByHeadquarters");

    private final FieldworkRepository repository;
    private final SyncService sync;

    public FieldworkService(FieldworkRepository repository, SyncService sync) {
        this.repository = repository;
        this.sync = sync;
    }

    public FieldworkOverview overview(UUID questionnaireId, long version, InterviewFilter filter) {
        SyncRun syncRun = requireSynchronizedData(questionnaireId, version);

        List<InterviewerActivity> activities =
                repository.activityByInterviewer(questionnaireId, version, filter);
        Map<String, AssignmentTarget> targets = new LinkedHashMap<>();
        repository.targets(questionnaireId, version).forEach(target -> targets.put(target.responsible(), target));

        // Équipes : regroupe les enquêteurs actifs, puis ajoute ceux qui ont des affectations
        // sans encore avoir d'interview.
        Map<String, List<InterviewerProgress>> teams = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        for (InterviewerActivity activity : activities) {
            AssignmentTarget target = targets.get(activity.interviewer());
            seen.add(activity.interviewer());
            teams.computeIfAbsent(activity.supervisor(), ignored -> new ArrayList<>())
                    .add(progress(activity, target == null ? 0 : target.target()));
        }
        Map<String, Long> teamOwnTargets = new LinkedHashMap<>();
        for (AssignmentTarget target : targets.values()) {
            if (seen.contains(target.responsible())) {
                continue;
            }
            boolean heldBySupervisor = target.responsible() != null
                    && target.responsible().equals(target.supervisor());
            if (filter.interviewer() != null
                    && (heldBySupervisor || !filter.interviewer().equals(target.responsible()))) {
                continue;
            }
            // Les objectifs ne dépendent ni de la période ni de la zone : ils restent affichés.
            if (heldBySupervisor) {
                // Affectation détenue par le superviseur, pas encore confiée à un enquêteur.
                teamOwnTargets.merge(target.supervisor(), target.target(), Long::sum);
                teams.computeIfAbsent(target.supervisor(), ignored -> new ArrayList<>());
            } else {
                teams.computeIfAbsent(target.supervisor(), ignored -> new ArrayList<>())
                        .add(new InterviewerProgress(target.responsible(), target.target(),
                                0, 0, 0, 0, 0, 0, 0, rate(0, target.target()), null, 0, null, null));
            }
        }
        if (filter.supervisor() != null) {
            teams.keySet().removeIf(supervisor -> !supervisor.equals(filter.supervisor()));
        }

        List<TeamProgress> teamRows = teams.entrySet().stream()
                .map(entry -> team(entry.getKey(), entry.getValue(),
                        teamOwnTargets.getOrDefault(entry.getKey(), 0L)))
                .sorted(Comparator.comparing(TeamProgress::supervisor))
                .toList();

        // Objectif du périmètre affiché : avec un filtre d'équipe ou d'enquêteur, seules leurs
        // affectations comptent.
        long target = teamRows.stream().mapToLong(TeamProgress::target).sum();
        long unlimited = targets.values().stream().mapToLong(AssignmentTarget::unlimitedAssignments).sum();
        long completed = activities.stream().mapToLong(InterviewerActivity::completed).sum();

        List<DailyProgress> daily = new ArrayList<>();
        long cumulative = 0;
        for (FieldworkRepository.DailyCount day : repository.completionsByDay(questionnaireId, version, filter)) {
            cumulative += day.count();
            daily.add(new DailyProgress(day.date(), day.count(), cumulative));
        }

        return new FieldworkOverview(
                syncRun.finishedAt(),
                filter.narrowsPopulation(),
                target,
                unlimited,
                completed,
                activities.stream().mapToLong(InterviewerActivity::approved).sum(),
                activities.stream().mapToLong(InterviewerActivity::rejected).sum(),
                activities.stream().mapToLong(InterviewerActivity::inProgress).sum(),
                rate(completed, target),
                repository.statuses(questionnaireId, version, filter).stream()
                        .map(status -> new StatusCount(status.status(), status.count()))
                        .toList(),
                daily,
                teamRows);
    }

    public FilterOptions filterOptions(UUID questionnaireId, long version) {
        return new FilterOptions(
                repository.supervisors(questionnaireId, version),
                repository.interviewers(questionnaireId, version),
                STATUSES);
    }

    private InterviewerProgress progress(InterviewerActivity activity, long target) {
        return new InterviewerProgress(
                activity.interviewer(),
                target,
                activity.total(),
                activity.completed(),
                activity.approved(),
                activity.rejected(),
                activity.inProgress(),
                activity.withErrors(),
                activity.rejections(),
                rate(activity.completed(), target),
                activity.medianDurationSeconds() == null ? null
                        : round(activity.medianDurationSeconds() / 60.0),
                activity.activeDays(),
                activity.activeDays() == 0 ? null : round((double) activity.completed() / activity.activeDays()),
                activity.lastCompletion());
    }

    private TeamProgress team(String supervisor, List<InterviewerProgress> interviewers, long ownTarget) {
        long target = ownTarget + interviewers.stream().mapToLong(InterviewerProgress::target).sum();
        long completed = interviewers.stream().mapToLong(InterviewerProgress::completed).sum();
        return new TeamProgress(
                supervisor,
                target,
                completed,
                interviewers.stream().mapToLong(InterviewerProgress::approved).sum(),
                interviewers.stream().mapToLong(InterviewerProgress::rejected).sum(),
                interviewers.stream().mapToLong(InterviewerProgress::inProgress).sum(),
                interviewers.stream().mapToLong(InterviewerProgress::withErrors).sum(),
                rate(completed, target),
                interviewers.stream().sorted(Comparator.comparing(InterviewerProgress::interviewer)).toList());
    }

    private SyncRun requireSynchronizedData(UUID questionnaireId, long version) {
        return sync.latestSuccessfulSync(questionnaireId, version).orElseThrow(() -> {
            sync.requestSync(questionnaireId, version, SyncService.Trigger.ON_DEMAND);
            return new DataNotSynchronizedException(
                    "Les données de cette version sont en cours de synchronisation depuis HQ. "
                            + "Le suivi terrain s'affichera dès la fin de l'import.");
        });
    }

    /** Pourcentage arrondi à deux décimales ; null sans dénominateur. */
    static Double rate(long numerator, long denominator) {
        return denominator <= 0 ? null : Math.round(numerator * 10_000.0 / denominator) / 100.0;
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
