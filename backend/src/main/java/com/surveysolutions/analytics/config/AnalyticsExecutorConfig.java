package com.surveysolutions.analytics.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AnalyticsExecutorConfig {
    @Bean(destroyMethod = "shutdown")
    ExecutorService analyticsExecutor() {
        return Executors.newFixedThreadPool(4);
    }
}
