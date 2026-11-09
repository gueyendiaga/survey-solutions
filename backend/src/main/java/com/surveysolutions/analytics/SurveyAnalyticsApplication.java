package com.surveysolutions.analytics;

import com.surveysolutions.analytics.config.SurveySolutionsProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(SurveySolutionsProperties.class)
public class SurveyAnalyticsApplication {
    public static void main(String[] args) {
        SpringApplication.run(SurveyAnalyticsApplication.class, args);
    }
}
