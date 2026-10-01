package com.locallife.backend.organizertype.api;

import com.locallife.backend.organizertype.application.OrganizerTypeService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contrôleur REST pour la consultation du référentiel des types
 * d'organisateurs (CAT-004). Même structure que les contrôleurs des trois
 * référentiels précédents : lecture seule, DTO
 * {@link OrganizerTypeResponse}.
 */
@RestController
@RequestMapping("/api/v1/organizertypes")
public class OrganizerTypeController {

    private final OrganizerTypeService organizerTypeService;

    public OrganizerTypeController(OrganizerTypeService organizerTypeService) {
        this.organizerTypeService = organizerTypeService;
    }

    @GetMapping
    public ResponseEntity<List<OrganizerTypeResponse>> getAllOrganizerTypes(
            @RequestParam(name = "locale", required = false, defaultValue = "fr-FR") String locale) {
        List<OrganizerTypeResponse> types = organizerTypeService.getAllOrganizerTypes(locale);
        return ResponseEntity.ok(types);
    }

}
