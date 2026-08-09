package com.locallife.backend.activity.api;

import com.locallife.backend.activity.application.ActivityService;
import com.locallife.backend.activity.domain.Activity;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contrôleur REST pour la gestion des activités.
 * Consultation et création (contribution, LL-2012).
 */
@RestController
@RequestMapping("/api/v1/activities")
public class ActivityController {

    private final ActivityService activityService;

    public ActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    @GetMapping
    public ResponseEntity<List<Activity>> getAllActivities() {
        List<Activity> activities = activityService.findAll();
        return ResponseEntity.ok(activities);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Activity> getActivityById(@PathVariable Long id) {
        Optional<Activity> activity = activityService.findById(id);
        return activity.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @PostMapping
    public ResponseEntity<Activity> createActivity(@RequestBody CreateActivityRequest request) {
        Activity activity = activityService.createActivity(
                request.title(), request.description(), request.category(),
                request.latitude(), request.longitude());
        return ResponseEntity.status(HttpStatus.CREATED).body(activity);
    }

    /**
     * Corps de requête pour la contribution d'une activité (LL-2012) :
     * titre, description, catégorie, localisation — exactement les champs
     * demandés par le ticket. id, dates et statut sont gérés côté serveur.
     */
    public record CreateActivityRequest(
            String title, String description, String category, double latitude, double longitude) {
    }

}
