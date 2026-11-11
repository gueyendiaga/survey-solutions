package com.surveysolutions.analytics.surveysolutions;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import com.surveysolutions.analytics.config.SurveySolutionsProperties;

/**
 * Client de l'Export API v2 de Survey Solutions : crée un export tabulaire, attend sa génération
 * puis télécharge l'archive ZIP dans un fichier temporaire.
 */
@Component
public class SurveySolutionsExportClient {
    private static final Duration DEFAULT_POLL_INTERVAL = Duration.ofSeconds(3);

    private final SurveySolutionsClientFactory clients;
    private final RestClient testClient;
    private final Duration timeout;
    private final Duration pollInterval;
    private final HttpClient redirectClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public record ExportJob(
            @JsonProperty("JobId") long jobId,
            @JsonProperty("ExportStatus") String exportStatus,
            @JsonProperty("HasExportFile") boolean hasExportFile,
            @JsonProperty("Error") String error) {}

    @Autowired
    public SurveySolutionsExportClient(
            SurveySolutionsClientFactory clients,
            SurveySolutionsProperties properties) {
        this(clients, null, properties.getExportTimeout(), DEFAULT_POLL_INTERVAL);
    }

    SurveySolutionsExportClient(RestClient client, Duration timeout, Duration pollInterval) {
        this(null, client, timeout, pollInterval);
    }

    private SurveySolutionsExportClient(
            SurveySolutionsClientFactory clients,
            RestClient testClient,
            Duration timeout,
            Duration pollInterval) {
        this.clients = clients;
        this.testClient = testClient;
        this.timeout = timeout;
        this.pollInterval = pollInterval;
    }

    /** Toutes les interviews sont exportées : le filtrage par statut se fait ensuite en base. */
    public long startTabularExport(UUID questionnaireId, long version) {
        ExportJob job = currentClient().post()
                .uri("/api/v2/export")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "ExportType", "Tabular",
                        "QuestionnaireId", questionnaireId + "$" + version,
                        "InterviewStatus", "All"))
                .retrieve()
                .body(ExportJob.class);
        if (job == null || job.jobId() <= 0) {
            throw new IllegalStateException("HQ n'a pas créé l'export demandé.");
        }
        return job.jobId();
    }

    public ExportJob awaitCompletion(long jobId) {
        Instant deadline = Instant.now().plus(timeout);
        while (true) {
            ExportJob job = currentClient().get()
                    .uri("/api/v2/export/{id}", jobId)
                    .retrieve()
                    .body(ExportJob.class);
            if (job == null) {
                throw new IllegalStateException("HQ a renvoyé un état d'export invalide.");
            }
            if ("Completed".equalsIgnoreCase(job.exportStatus()) && job.hasExportFile()) {
                return job;
            }
            if ("Fail".equalsIgnoreCase(job.exportStatus())
                    || "Canceled".equalsIgnoreCase(job.exportStatus())) {
                throw new IllegalStateException("L'export HQ " + jobId + " a échoué : "
                        + (job.error() == null || job.error().isBlank() ? job.exportStatus() : job.error()));
            }
            if (Instant.now().isAfter(deadline)) {
                throw new IllegalStateException("L'export HQ " + jobId
                        + " n'est pas terminé après " + timeout.toMinutes() + " minutes.");
            }
            try {
                Thread.sleep(pollInterval.toMillis());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Attente de l'export HQ interrompue.", exception);
            }
        }
    }

    /**
     * HQ renvoie le fichier directement (200) ou redirige vers un stockage externe (302). Dans ce
     * second cas, l'URL signée est suivie sans transmettre les identifiants HQ.
     */
    public Path download(long jobId) {
        Path target;
        try {
            target = Files.createTempFile("hq-export-" + jobId + "-", ".zip");
        } catch (IOException exception) {
            throw new IllegalStateException("Impossible de créer le fichier temporaire d'export.", exception);
        }
        try {
            URI redirect = currentClient().get()
                    .uri("/api/v2/export/{id}/file", jobId)
                    .exchange((request, response) -> {
                        if (response.getStatusCode().is3xxRedirection()) {
                            String location = response.getHeaders().getFirst(HttpHeaders.LOCATION);
                            if (location == null || location.isBlank()) {
                                throw new IllegalStateException("HQ a redirigé l'export sans adresse.");
                            }
                            return request.getURI().resolve(location);
                        }
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            throw new IllegalStateException("Téléchargement de l'export HQ refusé ("
                                    + response.getStatusCode().value() + ").");
                        }
                        try (InputStream body = response.getBody()) {
                            Files.copy(body, target, StandardCopyOption.REPLACE_EXISTING);
                        }
                        return null;
                    });
            if (redirect != null) {
                HttpResponse<Path> response = redirectClient.send(
                        HttpRequest.newBuilder(redirect).timeout(timeout).GET().build(),
                        HttpResponse.BodyHandlers.ofFile(target));
                if (response.statusCode() / 100 != 2) {
                    throw new IllegalStateException("Téléchargement de l'export externe refusé ("
                            + response.statusCode() + ").");
                }
            }
            return target;
        } catch (IOException exception) {
            deleteQuietly(target);
            throw new IllegalStateException("Impossible d'enregistrer l'export HQ.", exception);
        } catch (InterruptedException exception) {
            deleteQuietly(target);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Téléchargement de l'export HQ interrompu.", exception);
        } catch (RuntimeException exception) {
            deleteQuietly(target);
            throw exception;
        }
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // Fichier temporaire : le système le nettoiera.
        }
    }

    private RestClient currentClient() {
        return clients == null ? testClient : clients.currentUserClient();
    }
}
