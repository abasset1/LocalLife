package com.locallife.backend.audience.api;

import com.locallife.backend.audience.application.AudienceService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contrôleur REST pour la consultation du référentiel des publics
 * (CAT-003). Même structure que {@code CategoryController}/
 * {@code ActivityTypeController} : lecture seule, DTO
 * {@link AudienceResponse}.
 */
@RestController
@RequestMapping("/api/v1/audiences")
public class AudienceController {

    private final AudienceService audienceService;

    public AudienceController(AudienceService audienceService) {
        this.audienceService = audienceService;
    }

    @GetMapping
    public ResponseEntity<List<AudienceResponse>> getAllAudiences(
            @RequestParam(name = "locale", required = false, defaultValue = "fr-FR") String locale) {
        List<AudienceResponse> audiences = audienceService.getAllAudiences(locale);
        return ResponseEntity.ok(audiences);
    }

}
