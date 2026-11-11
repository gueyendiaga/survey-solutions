package com.surveysolutions.analytics.surveysolutions;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.web.client.RestClient;
import com.surveysolutions.analytics.config.SurveySolutionsProperties;

public class SurveySolutionsClientFactory {
    private final RestClient.Builder builder;
    private final SurveySolutionsProperties properties;
    private final SessionRegistry sessionRegistry;

    public SurveySolutionsClientFactory(
            RestClient.Builder builder,
            SurveySolutionsProperties properties,
            SessionRegistry sessionRegistry) {
        this.builder = builder;
        this.properties = properties;
        this.sessionRegistry = sessionRegistry;
    }

    public RestClient forCredentials(String username, String password) {
        return builder.clone()
                .baseUrl(properties.getApiBaseUrl())
                .defaultHeaders(headers -> {
                    headers.setBasicAuth(username, password);
                    headers.set(HttpHeaders.ACCEPT, "application/json");
                })
                .build();
    }

    public RestClient currentUserClient() {
        var principal = HqCredentialContext.current();
        if (sessionRegistry.getAllSessions(principal, false).isEmpty()) {
            throw new AuthenticationCredentialsNotFoundException("La session HQ a expiré ou a été fermée.");
        }
        return forCredentials(principal.getName(), principal.password());
    }
}
