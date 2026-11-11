package com.surveysolutions.analytics.config;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ApiSecurityTestController {
    @GetMapping("/api/interviews/{id}")
    String interview(@PathVariable String id) {
        return id;
    }

    @GetMapping("/api/interviews/{id}/pdf")
    String transcript(@PathVariable String id) {
        return id;
    }

    @PostMapping("/api/sync")
    String sync() {
        return "started";
    }

    @GetMapping("/api/health")
    String health() {
        return "UP";
    }
}
