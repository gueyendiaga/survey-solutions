package com.surveysolutions.analytics;

import com.surveysolutions.analytics.config.SurveySolutionsProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(SurveySolutionsProperties.class)
public class SurveyAnalyticsApplication {
    public static void main(String[] args) {
        SpringApplication.run(SurveyAnalyticsApplication.class, args);
    }
}
