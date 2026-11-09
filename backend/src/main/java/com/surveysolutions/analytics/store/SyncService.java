package com.surveysolutions.analytics.store;

import static com.surveysolutions.analytics.surveysolutions.SurveySolutionsDtos.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.surveysolutions.analytics.store.AnalyticsRepository.ImportCounts;
import com.surveysolutions.analytics.store.AnalyticsRepository.SyncRun;
import com.surveysolutions.analytics.surveysolutions.SurveySolutionsExportClient;
import com.surveysolutions.analytics.surveysolutions.SurveySolutionsService;

/**
 * Alimente l'entrepôt Analytics depuis l'Export API de HQ.
 *
 * <p>Les synchronisations s'exécutent une à la fois (fil dédié) pour ne pas surcharger HQ. Une
 * version déjà en cours de synchronisation n'est pas relancée.
 */
@Service
public class SyncService implements DisposableBean {
    private static final Logger LOG = LoggerFactory.getLogger(SyncService.class);

    public enum Trigger { SCHEDULED, MANUAL, ON_DEMAND }

    private record VersionKey(UUID questionnaireId, long version) {}

    private final SurveySolutionsService surveySolutions;
    private final SurveySolutionsExportClient exportClient;
    private final TabularExportParser parser;
    private final AnalyticsRepository repository;
    private final ExecutorService worker;
    private final Set<VersionKey> pending = ConcurrentHashMap.newKeySet();

    @Autowired
    public SyncService(
            SurveySolutionsService surveySolutions,
            SurveySolutionsExportClient exportClient,
            TabularExportParser parser,
            AnalyticsRepository repository) {
        this(surveySolutions, exportClient, parser, repository, Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "analytics-sync");
            thread.setDaemon(true);
            return thread;
        }));
    }

    SyncService(
            SurveySolutionsService surveySolutions,
            SurveySolutionsExportClient exportClient,
            TabularExportParser parser,
            AnalyticsRepository repository,
            ExecutorService worker) {
        this.surveySolutions = surveySolutions;
        this.exportClient = exportClient;
        this.parser = parser;
        this.repository = repository;
        this.worker = worker;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void markInterruptedSyncs() {
        int interrupted = repository.failInterruptedSyncs();
        if (interrupted > 0) {
            LOG.warn("{} synchronisation(s) interrompue(s) par le redémarrage marquée(s) en échec.", interrupted);
        }
    }

    /**
     * Planifie la synchronisation d'une version. Renvoie false si elle est déjà en attente ou en
     * cours.
     */
    public boolean requestSync(UUID questionnaireId, long version, Trigger trigger) {
        VersionKey key = new VersionKey(questionnaireId, version);
        if (!pending.add(key)) {
            return false;
        }
        worker.submit(() -> {
            try {
                synchronize(questionnaireId, version, trigger, null);
            } finally {
                pending.remove(key);
            }
        });
        return true;
    }

    public boolean isPending(UUID questionnaireId, long version) {
        return pending.contains(new VersionKey(questionnaireId, version));
    }

    public Optional<SyncRun> latestSync(UUID questionnaireId, long version) {
        return repository.latestSync(questionnaireId, version);
    }

    public Optional<SyncRun> latestSuccessfulSync(UUID questionnaireId, long version) {
        return repository.latestSuccessfulSync(questionnaireId, version);
    }

    /**
     * Passe en revue le catalogue HQ et synchronise les versions dont les interviews ont changé
     * depuis la dernière synchronisation réussie.
     */
    @Scheduled(
            initialDelayString = "PT1M",
            fixedDelayString = "${survey-solutions.sync-interval:PT30M}")
    public void synchronizeChangedQuestionnaires() {
        try {
            for (Questionnaire questionnaire : surveySolutions.getQuestionnaires()) {
                UUID questionnaireId = UUID.fromString(questionnaire.questionnaireId());
                long version = questionnaire.version();
                VersionKey key = new VersionKey(questionnaireId, version);
                String fingerprint = surveySolutions.getDataFingerprint(questionnaireId, version);
                Optional<SyncRun> lastSuccess = repository.latestSuccessfulSync(questionnaireId, version);
                boolean unchanged = lastSuccess
                        .map(run -> fingerprint.equals(run.hqFingerprint()))
                        .orElse(false);
                // Une version sans interview n'est importée que pour effacer d'anciennes données.
                boolean nothingToImport = fingerprint.startsWith("0|") && lastSuccess.isEmpty();
                if (unchanged || nothingToImport || !pending.add(key)) {
                    continue;
                }
                worker.submit(() -> {
                    try {
                        synchronize(questionnaireId, version, Trigger.SCHEDULED, fingerprint);
                    } finally {
                        pending.remove(key);
                    }
                });
            }
        } catch (RuntimeException exception) {
            LOG.warn("Revue planifiée du catalogue HQ impossible : {}", exception.getMessage());
        }
    }

    void synchronize(UUID questionnaireId, long version, Trigger trigger, String knownFingerprint) {
        String fingerprint = knownFingerprint;
        if (fingerprint == null) {
            try {
                fingerprint = surveySolutions.getDataFingerprint(questionnaireId, version);
            } catch (RuntimeException exception) {
                LOG.warn("Empreinte HQ indisponible pour {} v{} : {}",
                        questionnaireId, version, exception.getMessage());
            }
        }
        long syncId = repository.startSync(questionnaireId, version, trigger.name(), fingerprint);
        Path archive = null;
        try {
            Questionnaire questionnaire = surveySolutions.getQuestionnaires().stream()
                    .filter(q -> questionnaireId.toString().equalsIgnoreCase(q.questionnaireId())
                            && q.version() == version)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Le questionnaire ou sa version n'existe pas dans HQ."));
            Map<String, String> questionTypes = new HashMap<>();
            surveySolutions.getQuestions(questionnaireId, version).forEach(question -> {
                if (question.variableName() != null) {
                    questionTypes.put(question.variableName(), question.type() == null ? "" : question.type());
                }
            });

            long jobId = exportClient.startTabularExport(questionnaireId, version);
            repository.recordExportJob(syncId, jobId);
            exportClient.awaitCompletion(jobId);
            archive = exportClient.download(jobId);

            Path file = archive;
            ImportCounts counts = repository.replaceVersion(questionnaireId, version, writer ->
                    parser.parse(file, questionTypes, questionnaire.variable(), writer));
            repository.finishSync(syncId, counts.interviews(), counts.units());
            LOG.info("Synchronisation {} v{} terminée : {} interview(s), {} unité(s).",
                    questionnaireId, version, counts.interviews(), counts.units());
        } catch (RuntimeException exception) {
            LOG.warn("Synchronisation {} v{} en échec : {}", questionnaireId, version, exception.getMessage());
            repository.failSync(syncId, exception.getMessage());
        } finally {
            if (archive != null) {
                try {
                    Files.deleteIfExists(archive);
                } catch (IOException ignored) {
                    // Fichier temporaire : le système le nettoiera.
                }
            }
        }
    }

    @Override
    public void destroy() {
        worker.shutdownNow();
    }
}
