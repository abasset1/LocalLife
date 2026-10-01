package com.locallife.backend.activitytype.api;

import com.locallife.backend.activitytype.application.ActivityTypeService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contrôleur REST pour la consultation du référentiel des types d'activité
 * (CAT-002). Même structure que {@code CategoryController} (CAT-001) :
 * lecture seule, DTO {@link ActivityTypeResponse} (jamais l'entité brute).
 */
@RestController
@RequestMapping("/api/v1/activitytypes")
public class ActivityTypeController {

    private final ActivityTypeService activityTypeService;

    public ActivityTypeController(ActivityTypeService activityTypeService) {
        this.activityTypeService = activityTypeService;
    }

    @GetMapping
    public ResponseEntity<List<ActivityTypeResponse>> getAllActivityTypes(
            @RequestParam(name = "locale", required = false, defaultValue = "fr-FR") String locale) {
        List<ActivityTypeResponse> types = activityTypeService.getAllActivityTypes(locale);
        return ResponseEntity.ok(types);
    }

}
