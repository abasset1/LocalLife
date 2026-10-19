package com.locallife.backend.foodtruck.api;

import com.locallife.backend.auth.application.JwtAuthentication;
import com.locallife.backend.common.ErrorResponse;
import com.locallife.backend.foodtruck.application.ProFoodTruckService;
import com.locallife.backend.foodtruck.application.ProFoodTruckService.OwnerAction;
import com.locallife.backend.foodtruck.application.ProFoodTruckService.PresenceInput;
import com.locallife.backend.foodtruck.application.ProFoodTruckService.ProfileInput;
import com.locallife.backend.foodtruck.domain.FoodTruckPresence;
import com.locallife.backend.foodtruck.domain.FoodTruckStatus;
import com.locallife.backend.foodtruck.domain.ManagedPresence;
import com.locallife.backend.foodtruck.domain.PresenceStatus;
import com.locallife.backend.foodtruck.domain.SpotKind;
import com.locallife.backend.foodtruck.infrastructure.OwnedFoodTruckRepository.OwnedFoodTruck;
import com.locallife.backend.pro.application.ProActionException;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Food trucks de l'espace professionnel : profil (PRO-01 lot C) et présences (FOODTRUCK-01). Connexion exigée
 * ({@code /api/v1/pro/**}, {@code SecurityConfig}) ; droit professionnel et propriété vérifiés dans
 * {@code ProFoodTruckService} : 403 sans compte professionnel, 404 pour le food truck d'un autre.
 */
@RestController
@RequestMapping("/api/v1/pro")
public class ProFoodTruckController {

    private final ProFoodTruckService service;

    public ProFoodTruckController(ProFoodTruckService service) {
        this.service = service;
    }

    /** Food truck vu par son propriétaire. {@code suspendedByLocalLife} : suspendu par un administrateur. */
    public record OwnedFoodTruckResponse(
            Long id, String name, String description, String category, String contact, FoodTruckStatus status,
            boolean suspendedByLocalLife, OffsetDateTime updatedAt) {

        static OwnedFoodTruckResponse from(OwnedFoodTruck foodTruck) {
            return new OwnedFoodTruckResponse(foodTruck.id(), foodTruck.name(), foodTruck.description(),
                    foodTruck.category(), foodTruck.contact(), foodTruck.status(),
                    foodTruck.suspendedByAdminAt() != null, foodTruck.updatedAt());
        }
    }

    /** Présence vue par le propriétaire, tous statuts. */
    public record ManagedPresenceResponse(
            Long id, Long foodTruckId, PresenceStatus status, Long locationId, String spotName, Double latitude,
            Double longitude, SpotKind spotKind, String daysOfWeek, LocalTime startTime, LocalTime endTime,
            LocalDate validFrom, LocalDate validUntil, Long placeId) {

        static ManagedPresenceResponse from(ManagedPresence managed) {
            FoodTruckPresence presence = managed.presence();
            return new ManagedPresenceResponse(presence.id(), presence.foodTruckId(), managed.status(),
                    managed.locationId(), presence.spotName(), presence.latitude(), presence.longitude(),
                    presence.spotKind(), presence.daysOfWeek(), presence.startTime(), presence.endTime(),
                    presence.validFrom(), presence.validUntil(), presence.placeId());
        }
    }

    // ---------------------------------------------------------------- profil

    @Operation(summary = "Mes food trucks")
    @GetMapping("/foodtrucks")
    public ResponseEntity<Object> mine(Authentication authentication, HttpServletRequest request) {
        return handle(request, () -> ResponseEntity.ok(service.findMine(userId(authentication)).stream()
                .map(OwnedFoodTruckResponse::from).toList()));
    }

    @Operation(summary = "Créer un food truck", description = "Profil en brouillon : nom, cuisine, description, "
            + "contact. Publication : POST /pro/foodtrucks/{id}/publish. Distinct des lieux.")
    @PostMapping("/foodtrucks")
    public ResponseEntity<Object> create(@RequestBody(required = false) ProfileInput body,
            Authentication authentication, HttpServletRequest request) {
        return handle(request, () -> ResponseEntity.status(HttpStatus.CREATED)
                .body(OwnedFoodTruckResponse.from(service.create(userId(authentication), body))));
    }

    @Operation(summary = "Un de mes food trucks")
    @GetMapping("/foodtrucks/{id}")
    public ResponseEntity<Object> one(@PathVariable Long id, Authentication authentication,
            HttpServletRequest request) {
        return handle(request, () -> ResponseEntity.ok(OwnedFoodTruckResponse.from(
                service.findMine(userId(authentication), id))));
    }

    @Operation(summary = "Modifier le profil", description = "Sauf food truck archivé.")
    @PutMapping("/foodtrucks/{id}")
    public ResponseEntity<Object> update(@PathVariable Long id, @RequestBody(required = false) ProfileInput body,
            Authentication authentication, HttpServletRequest request) {
        return handle(request, () -> ResponseEntity.ok(OwnedFoodTruckResponse.from(
                service.update(userId(authentication), id, body))));
    }

    @Operation(summary = "Publier, suspendre ou archiver un food truck",
            description = "action : publish (brouillon ou suspendu → publié, sauf suspension par LocalLife), "
                    + "suspend (publié → suspendu), archive (définitif).")
    @PostMapping("/foodtrucks/{id}/{action}")
    public ResponseEntity<Object> act(@PathVariable Long id, @PathVariable String action,
            Authentication authentication, HttpServletRequest request) {
        return handle(request, () -> ResponseEntity.ok(OwnedFoodTruckResponse.from(
                service.act(userId(authentication), id, action(action)))));
    }

    // ---------------------------------------------------------------- présences

    @Operation(summary = "Emplacements et planning d'un de mes food trucks", description = "Tous statuts.")
    @GetMapping("/foodtrucks/{id}/presences")
    public ResponseEntity<Object> presences(@PathVariable Long id, Authentication authentication,
            HttpServletRequest request) {
        return handle(request, () -> ResponseEntity.ok(service.findPresences(userId(authentication), id).stream()
                .map(ManagedPresenceResponse::from).toList()));
    }

    @Operation(summary = "Ajouter un emplacement",
            description = "Lieu LocalLife publié (placeId) ou nouvel emplacement (newSpot) ; type MARKET, PRIVATE, "
                    + "EVENT ou OTHER ; jours MO..SU (vide = tous) ; horaires HH:mm ; période yyyy-MM-dd. Créé en "
                    + "brouillon. 409 s'il chevauche un autre emplacement de ce food truck.")
    @PostMapping("/foodtrucks/{id}/presences")
    public ResponseEntity<Object> createPresence(@PathVariable Long id,
            @RequestBody(required = false) PresenceInput body, Authentication authentication,
            HttpServletRequest request) {
        return handle(request, () -> ResponseEntity.status(HttpStatus.CREATED).body(ManagedPresenceResponse.from(
                service.createPresence(userId(authentication), id, body))));
    }

    @Operation(summary = "Modifier un emplacement", description = "Sans placeId ni newSpot, l'emplacement est gardé.")
    @PutMapping("/presences/{presenceId}")
    public ResponseEntity<Object> updatePresence(@PathVariable Long presenceId,
            @RequestBody(required = false) PresenceInput body, Authentication authentication,
            HttpServletRequest request) {
        return handle(request, () -> ResponseEntity.ok(ManagedPresenceResponse.from(
                service.updatePresence(userId(authentication), presenceId, body))));
    }

    @Operation(summary = "Publier, réactiver, suspendre ou archiver un emplacement",
            description = "action : publish (brouillon ou suspendu → publié), suspend, archive.")
    @PostMapping("/presences/{presenceId}/{action}")
    public ResponseEntity<Object> actOnPresence(@PathVariable Long presenceId, @PathVariable String action,
            Authentication authentication, HttpServletRequest request) {
        return handle(request, () -> ResponseEntity.ok(ManagedPresenceResponse.from(
                service.actOnPresence(userId(authentication), presenceId, action(action)))));
    }

    // ---------------------------------------------------------------- outils

    private static OwnerAction action(String value) {
        try {
            return OwnerAction.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ProActionException(ProActionException.Reason.NOT_FOUND, "Action inconnue : " + value + ".");
        }
    }

    static ResponseEntity<Object> handle(HttpServletRequest request, Supplier<ResponseEntity<Object>> action) {
        try {
            return action.get();
        } catch (ProActionException exception) {
            HttpStatus status = switch (exception.reason()) {
                case INVALID -> HttpStatus.BAD_REQUEST;
                case FORBIDDEN -> HttpStatus.FORBIDDEN;
                case NOT_FOUND -> HttpStatus.NOT_FOUND;
                case CONFLICT -> HttpStatus.CONFLICT;
                case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            };
            return ResponseEntity.status(status).body(new ErrorResponse(Instant.now(), status.value(),
                    status.getReasonPhrase(), exception.getMessage(), request.getRequestURI()));
        }
    }

    private static Long userId(Authentication authentication) {
        return ((JwtAuthentication) authentication).getUserId();
    }
}
