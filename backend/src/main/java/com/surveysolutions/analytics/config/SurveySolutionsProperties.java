package com.surveysolutions.analytics.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "survey-solutions")
public class SurveySolutionsProperties {
    private String baseUrl;
    private String username;
    private String password;
    private String allowedOrigin;
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration readTimeout = Duration.ofSeconds(30);
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

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
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
}
