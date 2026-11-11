package com.surveysolutions.analytics.store;

import static com.surveysolutions.analytics.surveysolutions.SurveySolutionsDtos.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import com.surveysolutions.analytics.config.HqApiUserPrincipal;
import com.surveysolutions.analytics.surveysolutions.HqCredentialContext;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.surveysolutions.analytics.surveysolutions.SurveySolutionsExportClient;
import com.surveysolutions.analytics.surveysolutions.SurveySolutionsService;

class SyncServiceTest {
    private static final UUID QUESTIONNAIRE_ID = UUID.fromString("588daf2d-0c72-4b89-bec9-254459a68983");

    private final SurveySolutionsService surveySolutions = mock(SurveySolutionsService.class);
    private final SurveySolutionsExportClient exportClient = mock(SurveySolutionsExportClient.class);
    private final TabularExportParser parser = mock(TabularExportParser.class);
    private final AnalyticsRepository repository = mock(AnalyticsRepository.class);
    private final SessionRegistry sessionRegistry = mock(SessionRegistry.class);
    private final HqApiUserPrincipal principal = new HqApiUserPrincipal("api-user", "secret");
    private final SyncService service = new SyncService(
            surveySolutions, exportClient, parser, repository, sessionRegistry, new DirectExecutor());

    @BeforeEach
    void catalogue() {
        when(sessionRegistry.getAllPrincipals()).thenReturn(List.of(principal));
        when(sessionRegistry.getAllSessions(principal, false)).thenReturn(List.of(
                new SessionInformation("session-1", principal.getName(),
                        java.util.Date.from(java.time.Instant.now()))));
        when(surveySolutions.getQuestionnaires()).thenReturn(List.of(new Questionnaire(
                "id$1", QUESTIONNAIRE_ID.toString(), 1, "Enquête", "survey", null, false, false)));
        when(surveySolutions.getQuestions(QUESTIONNAIRE_ID, 1)).thenReturn(List.of());
        when(surveySolutions.getDataFingerprint(QUESTIONNAIRE_ID, 1)).thenReturn("2|2026-11-05");
        when(repository.startSync(any(), anyLong(), any(), any())).thenReturn(7L);
        when(surveySolutions.getAssignments(QUESTIONNAIRE_ID, 1)).thenReturn(List.of());
        when(surveySolutions.getTeams()).thenReturn(java.util.Map.of("enq1", "sup1"));
    }

    @Test
    void exportsDownloadsAndImportsTheVersion() throws Exception {
        Path archive = Files.createTempFile("export", ".zip");
        when(exportClient.startTabularExport(QUESTIONNAIRE_ID, 1)).thenReturn(42L);
        when(exportClient.download(42L)).thenReturn(archive);
        when(repository.replaceVersion(eq(QUESTIONNAIRE_ID), eq(1L), any(), any()))
                .thenReturn(new AnalyticsRepository.ImportCounts(2, 5));

        assertThat(HqCredentialContext.with(principal,
                () -> service.requestSync(QUESTIONNAIRE_ID, 1, SyncService.Trigger.MANUAL))).isTrue();

        verify(repository).startSync(QUESTIONNAIRE_ID, 1, "MANUAL", "2|2026-11-05");
        verify(repository).recordExportJob(7L, 42L);
        verify(exportClient).awaitCompletion(42L);
        verify(repository).finishSync(7L, 2, 5);
        verify(repository).replaceTeams(java.util.Map.of("enq1", "sup1"));
        assertThat(Files.exists(archive)).isFalse();
        assertThat(service.isPending(QUESTIONNAIRE_ID, 1)).isFalse();
    }

    @Test
    void recordsTheFailureWhenTheExportFails() {
        when(exportClient.startTabularExport(QUESTIONNAIRE_ID, 1))
                .thenThrow(new IllegalStateException("Export refusé"));

        HqCredentialContext.with(principal, () -> service.requestSync(
                QUESTIONNAIRE_ID, 1, SyncService.Trigger.MANUAL));

        verify(repository).failSync(eq(7L), contains("Export refusé"));
        verify(repository, never()).finishSync(anyLong(), any(Integer.class), any(Integer.class));
    }

    @Test
    void scheduledRunSkipsVersionsWhoseDataDidNotChange() {
        when(repository.latestSuccessfulSync(QUESTIONNAIRE_ID, 1)).thenReturn(Optional.of(
                new AnalyticsRepository.SyncRun(1, QUESTIONNAIRE_ID, 1, "SUCCEEDED", "SCHEDULED",
                        Instant.now(), Instant.now(), 2, 5, "2|2026-11-05", null)));

        service.synchronizeChangedQuestionnaires();

        verify(exportClient, never()).startTabularExport(any(), anyLong());
    }

    @Test
    void scheduledRunSynchronizesChangedVersions() {
        when(repository.latestSuccessfulSync(QUESTIONNAIRE_ID, 1)).thenReturn(Optional.of(
                new AnalyticsRepository.SyncRun(1, QUESTIONNAIRE_ID, 1, "SUCCEEDED", "SCHEDULED",
                        Instant.now(), Instant.now(), 1, 2, "1|2026-11-01", null)));
        when(exportClient.startTabularExport(QUESTIONNAIRE_ID, 1))
                .thenThrow(new IllegalStateException("arrêt du test après le déclenchement"));

        service.synchronizeChangedQuestionnaires();

        verify(repository).startSync(QUESTIONNAIRE_ID, 1, "SCHEDULED", "2|2026-11-05");
    }

    /** Exécute les tâches immédiatement, dans le fil du test. */
    private static final class DirectExecutor extends AbstractExecutorService {
        @Override
        public void execute(Runnable command) {
            command.run();
        }

        @Override
        public void shutdown() {}

        @Override
        public List<Runnable> shutdownNow() {
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return false;
        }

        @Override
        public boolean isTerminated() {
            return false;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }
    }
}
