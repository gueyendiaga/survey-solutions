package com.surveysolutions.analytics.quality;

import static com.surveysolutions.analytics.quality.AutoRejectDtos.*;

import java.security.Principal;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Paramétrage et exécution des règles de rejet automatique d'une version de questionnaire. */
@RestController
@RequestMapping("/api/questionnaires/{questionnaireId}/{version}/auto-reject")
public class AutoRejectController {
    private final AutoRejectService service;

    public AutoRejectController(AutoRejectService service) {
        this.service = service;
    }

    @GetMapping
    public Overview overview(@PathVariable UUID questionnaireId, @PathVariable long version) {
        return service.overview(questionnaireId, version);
    }

    @PutMapping
    public Settings save(
            @PathVariable UUID questionnaireId,
            @PathVariable long version,
            @RequestBody SettingsUpdate update,
            Principal principal) {
        return service.save(questionnaireId, version, update, principal == null ? null : principal.getName());
    }

    @GetMapping("/preview")
    public Preview preview(@PathVariable UUID questionnaireId, @PathVariable long version) {
        return service.preview(questionnaireId, version);
    }

    @PostMapping("/run")
    public RunResult run(@PathVariable UUID questionnaireId, @PathVariable long version) {
        return service.run(questionnaireId, version);
    }
}
