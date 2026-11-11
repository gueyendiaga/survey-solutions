package com.surveysolutions.analytics.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final SurveySolutionsProperties properties;

    public WebConfig(SurveySolutionsProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(properties.getAllowedOrigin())
                .allowedMethods("GET", "POST", "PATCH")
                .allowedHeaders("Content-Type", "Authorization", "X-XSRF-TOKEN")
                .allowCredentials(true);
    }
}
