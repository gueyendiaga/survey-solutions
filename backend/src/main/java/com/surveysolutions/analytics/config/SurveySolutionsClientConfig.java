package com.surveysolutions.analytics.config;

import java.net.http.HttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.security.core.session.SessionRegistry;
import com.surveysolutions.analytics.surveysolutions.SurveySolutionsClientFactory;

@Configuration
public class SurveySolutionsClientConfig {
    @Bean
    SurveySolutionsClientFactory surveySolutionsClientFactory(
            RestClient.Builder builder,
            SurveySolutionsProperties properties,
            SessionRegistry sessionRegistry) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.getReadTimeout());
        builder.baseUrl(properties.getApiBaseUrl())
                .requestFactory(requestFactory);
        return new SurveySolutionsClientFactory(builder, properties, sessionRegistry);
    }
}
