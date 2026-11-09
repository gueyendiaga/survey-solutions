package com.surveysolutions.analytics.config;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class SurveySolutionsClientConfig {
    @Bean
    RestClient surveySolutionsClient(
            RestClient.Builder builder,
            SurveySolutionsProperties properties) {
        String username = properties.getUsername();
        String password = properties.getPassword();
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "SURVEY_SOLUTIONS_USERNAME et SURVEY_SOLUTIONS_PASSWORD doivent être configurés.");
        }

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.getReadTimeout());

        String credentials = Base64.getEncoder().encodeToString(
                (username + ":" + password).getBytes(StandardCharsets.UTF_8));

        return builder
                .baseUrl(properties.getApiBaseUrl())
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + credentials)
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .build();
    }
}
