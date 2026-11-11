package com.surveysolutions.analytics.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "survey-solutions")
public class SurveySolutionsProperties {
    private String baseUrl;
    private String allowedOrigin;
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration readTimeout = Duration.ofSeconds(30);
    /** Workspace HQ (ex. primary). Vide pour une instance sans workspaces. */
    private String workspace = "";
    /** Intervalle entre deux synchronisations automatiques. */
    private Duration syncInterval = Duration.ofMinutes(30);
    /** Durée maximale d'attente d'un export HQ avant abandon. */
    private Duration exportTimeout = Duration.ofMinutes(30);
    /** Organisme affiché en en-tête des rapports PDF. */
    private String reportOrganization = "Survey Solutions Analytics";
    /** Nombre de rapports automatiques conservés par version de questionnaire. */
    private int reportRetention = 30;
    /**
     * Statuts HQ des interviews prises en compte dans les analyses de variables et la complétude.
     * Par défaut : interviews terminées ou approuvées (les interviews encore affectées ou rejetées
     * n'entrent pas dans les dénominateurs).
     */
    private List<String> analysisStatuses = List.of(
            "Completed", "ApprovedBySupervisor", "ApprovedByHeadquarters");

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getAllowedOrigin() {
        return allowedOrigin;
    }

    public void setAllowedOrigin(String allowedOrigin) {
        this.allowedOrigin = allowedOrigin;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public List<String> getAnalysisStatuses() {
        return analysisStatuses;
    }

    public void setAnalysisStatuses(List<String> analysisStatuses) {
        this.analysisStatuses = analysisStatuses == null ? List.of() : List.copyOf(analysisStatuses);
    }

    public String getWorkspace() {
        return workspace;
    }

    public void setWorkspace(String workspace) {
        this.workspace = workspace == null ? "" : workspace.trim();
    }

    /** URL de base HQ incluant le workspace éventuel, sans barre oblique finale. */
    public String getApiBaseUrl() {
        String base = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        return workspace.isBlank() ? base : base + "/" + workspace;
    }

    public Duration getSyncInterval() {
        return syncInterval;
    }

    public void setSyncInterval(Duration syncInterval) {
        this.syncInterval = syncInterval;
    }

    public Duration getExportTimeout() {
        return exportTimeout;
    }

    public void setExportTimeout(Duration exportTimeout) {
        this.exportTimeout = exportTimeout;
    }

    public String getReportOrganization() {
        return reportOrganization;
    }

    public void setReportOrganization(String reportOrganization) {
        this.reportOrganization = reportOrganization == null || reportOrganization.isBlank()
                ? "Survey Solutions Analytics" : reportOrganization.trim();
    }

    public int getReportRetention() {
        return reportRetention;
    }

    public void setReportRetention(int reportRetention) {
        this.reportRetention = Math.max(1, reportRetention);
    }
}
