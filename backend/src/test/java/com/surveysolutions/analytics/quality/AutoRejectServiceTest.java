package com.surveysolutions.analytics.quality;

import static com.surveysolutions.analytics.quality.AutoRejectDtos.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import com.surveysolutions.analytics.dashboard.InterviewActionConflictException;
import com.surveysolutions.analytics.dashboard.InterviewActionService;
import com.surveysolutions.analytics.quality.AutoRejectRepository.Hit;
import com.surveysolutions.analytics.quality.AutoRejectRepository.PendingInterview;
import com.surveysolutions.analytics.store.SyncService;

class AutoRejectServiceTest {
    private static final UUID Q = UUID.fromString("d048fb80-6071-40b2-91e0-4dd13b57ad1e");
    private static final UUID FAST = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID LIMIT = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID CLEAN = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID DONE = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final Instant COMPLETED = Instant.parse("2026-11-13T13:05:24Z");

    private final AutoRejectRepository repository = mock(AutoRejectRepository.class);
    private final InterviewActionService actions = mock(InterviewActionService.class);
    private final AutoRejectService service = new AutoRejectService(repository, actions);

    private final Rule duration = new Rule(1L, RuleType.MIN_DURATION, null, BigDecimal.valueOf(20), null, null, true);
    private final Rule age = new Rule(2L, RuleType.VALUE_RANGE, "AgeAnnee", BigDecimal.valueOf(15), BigDecimal.valueOf(99),
            "Âge incohérent : vérifier la date de naissance", true);
    private final Rule disabled = new Rule(3L, RuleType.VALIDATION_ERRORS, null, BigDecimal.ONE, null, null, false);

    private void given(Mode mode) {
        when(repository.settings(Q, 1)).thenReturn(new Settings(mode, 2, List.of(duration, age, disabled), null, null));
        when(repository.pending(Q, 1)).thenReturn(List.of(
                new PendingInterview(FAST, "11-11-11-11", "Completed", "gueye27", 0, COMPLETED),
                new PendingInterview(LIMIT, "22-22-22-22", "ApprovedBySupervisor", "gueye29", 2, COMPLETED),
                new PendingInterview(CLEAN, "33-33-33-33", "Completed", "gueye27", 0, COMPLETED),
                new PendingInterview(DONE, "44-44-44-44", "Completed", "gueye30", 1, COMPLETED)));
        when(repository.hits(Q, 1, duration)).thenReturn(List.of(new Hit(FAST, "6"), new Hit(LIMIT, "4"), new Hit(DONE, "3")));
        when(repository.hits(Q, 1, age)).thenReturn(List.of(new Hit(FAST, "7")));
        when(repository.alreadyHandled(DONE, COMPLETED)).thenReturn(true);
    }

    @Test
    void previewListsViolationsPerInterviewWithoutTouchingHq() {
        given(Mode.SIMULATION);

        Preview preview = service.preview(Q, 1);

        assertThat(preview.evaluated()).isEqualTo(4);
        assertThat(preview.candidates()).extracting(Candidate::interviewKey)
                .containsExactly("11-11-11-11", "22-22-22-22", "44-44-44-44");
        assertThat(preview.candidates().get(0).violations()).extracting(Violation::reason).containsExactly(
                "Durée d’interview trop courte (6 min, minimum 20 min)",
                "Âge incohérent : vérifier la date de naissance");
        assertThat(preview.candidates().get(1).overLimit()).isTrue();
        assertThat(preview.perRule()).extracting(RuleCount::interviews).containsExactly(3, 1);
        verify(repository, never()).hits(Q, 1, disabled);
    }

    @Test
    void runRejectsOncePerSubmissionAndStopsAtTheRejectionLimit() {
        given(Mode.AUTO);

        RunResult result = service.run(Q, 1);

        assertThat(result).isEqualTo(new RunResult(4, 1, 1, 1, 0));
        verify(actions).reject(eq(Q), eq(1L), eq(FAST), eq("""
                Rejet automatique (contrôle qualité) :
                - Durée d’interview trop courte (6 min, minimum 20 min)
                - Âge incohérent : vérifier la date de naissance"""), isNull());
        verify(actions, never()).reject(eq(Q), eq(1L), eq(LIMIT), anyString(), any());
        verify(actions, never()).reject(eq(Q), eq(1L), eq(DONE), anyString(), any());
        verify(repository).recordEvent(eq(Q), eq(1L), any(), eq("REJECTED"), anyString(), isNull());
        verify(repository).recordEvent(eq(Q), eq(1L), any(), eq("SKIPPED_LIMIT"), anyString(), isNull());
    }

    @Test
    void recordsHqFailuresAndContinues() {
        given(Mode.AUTO);
        when(actions.reject(eq(Q), eq(1L), eq(FAST), anyString(), any()))
                .thenThrow(new InterviewActionConflictException("Statut modifié"));

        RunResult result = service.run(Q, 1);

        assertThat(result.failed()).isEqualTo(1);
        verify(repository).recordEvent(eq(Q), eq(1L), any(), eq("FAILED"), anyString(), eq("Statut modifié"));
    }

    @Test
    void syncOnlyRejectsInAutomaticMode() {
        given(Mode.SIMULATION);

        service.afterSync(new SyncService.VersionSynchronized(Q, 1));

        verify(actions, never()).reject(any(), any(Long.class), any(), anyString(), any());
        assertThatThrownBy(() -> service.run(Q, 1)).isInstanceOf(InterviewActionConflictException.class);
    }

    @Test
    void validatesRulesBeforeSaving() {
        when(repository.variableExists(Q, 1, "inconnue")).thenReturn(Optional.of(false));

        assertThatThrownBy(() -> service.save(Q, 1, new SettingsUpdate(Mode.SIMULATION, 2, List.of(
                new Rule(null, RuleType.REQUIRED_ANSWER, "inconnue", null, null, null, true))), "hq"))
                .hasMessageContaining("« inconnue » n’existe pas");
        assertThatThrownBy(() -> service.save(Q, 1, new SettingsUpdate(Mode.SIMULATION, 2, List.of(
                new Rule(null, RuleType.VALUE_RANGE, "AgeAnnee", BigDecimal.TEN, BigDecimal.ONE, null, true))), "hq"))
                .hasMessageContaining("borne minimale");
        assertThatThrownBy(() -> service.save(Q, 1, new SettingsUpdate(Mode.AUTO, 2, List.of(
                new Rule(null, RuleType.VALIDATION_ERRORS, null, BigDecimal.ONE, null, null, false))), "hq"))
                .hasMessageContaining("au moins une règle");
    }
}
