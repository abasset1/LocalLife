package com.locallife.backend.foodtruck.api;

import com.locallife.backend.auth.application.JwtAuthentication;
import com.locallife.backend.foodtruck.api.ProFoodTruckController.OwnedFoodTruckResponse;
import com.locallife.backend.foodtruck.application.ProFoodTruckService;
import com.locallife.backend.foodtruck.domain.FoodTruckStatus;
import com.locallife.backend.foodtruck.infrastructure.OwnedFoodTruckRepository;
import com.locallife.backend.pro.application.ProActionException;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Food trucks côté administration : validation de leur première publication (décision du 22/10/2026 ; un
 * administrateur ne valide jamais le sien), puis suspension d'un food truck publié et levée de cette suspension.
 * Réservé au rôle {@code ADMIN} ({@code /api/v1/admin/**}).
 */
@RestController
@RequestMapping("/api/v1/admin/foodtrucks")
public class AdminFoodTruckController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminFoodTruckController.class);
    private static final int MAX_LIMIT = 200;

    private final OwnedFoodTruckRepository repository;
    private final ProFoodTruckService proFoodTruckService;

    public AdminFoodTruckController(OwnedFoodTruckRepository repository, ProFoodTruckService proFoodTruckService) {
        this.repository = repository;
        this.proFoodTruckService = proFoodTruckService;
    }

    /** Motif d'un refus. */
    public record ReviewRequest(String reason) {
    }

    /** Food truck avec son propriétaire. */
    public record AdminFoodTruckResponse(
            OwnedFoodTruckResponse foodTruck, Long ownerId, String ownerEmail, OffsetDateTime suspendedByAdminAt) {
    }

    @Operation(summary = "Food trucks", description = "Statuts séparés par des virgules ; tous par défaut.")
    @GetMapping
    public ResponseEntity<Object> list(@RequestParam(required = false) String status,
            @RequestParam(required = false) Integer limit, @RequestParam(required = false) Integer offset,
            HttpServletRequest request) {
        return ProFoodTruckController.handle(request, () -> {
            Set<FoodTruckStatus> statuses = EnumSet.noneOf(FoodTruckStatus.class);
            try {
                if (status != null && !status.isBlank()) {
                    Arrays.stream(status.split(","))
                            .map(value -> FoodTruckStatus.valueOf(value.trim().toUpperCase(Locale.ROOT)))
                            .forEach(statuses::add);
                }
            } catch (IllegalArgumentException exception) {
                throw new ProActionException(ProActionException.Reason.INVALID, "Statut inconnu : " + status + ".");
            }
            int effectiveLimit = limit == null ? 50 : limit;
            int effectiveOffset = offset == null ? 0 : offset;
            if (effectiveLimit < 1 || effectiveLimit > MAX_LIMIT || effectiveOffset < 0) {
                throw new ProActionException(ProActionException.Reason.INVALID,
                        "'limit' doit être entre 1 et " + MAX_LIMIT + ", 'offset' positif.");
            }
            List<AdminFoodTruckResponse> items = repository.findAll(statuses, effectiveLimit, effectiveOffset)
                    .stream().map(foodTruck -> new AdminFoodTruckResponse(OwnedFoodTruckResponse.from(foodTruck),
                            foodTruck.ownerId(), foodTruck.ownerEmail(), foodTruck.suspendedByAdminAt()))
                    .toList();
            return ResponseEntity.ok(items);
        });
    }

    @Operation(summary = "Suspendre un food truck", description = "Le propriétaire ne peut plus le republier. "
            + "409 s'il est archivé ou introuvable.")
    @PatchMapping("/{id}/suspend")
    public ResponseEntity<Object> suspend(@PathVariable Long id, Authentication authentication,
            HttpServletRequest request) {
        Long adminId = ((JwtAuthentication) authentication).getUserId();
        return ProFoodTruckController.handle(request, () -> {
            refuseOwn(adminId, id);
            if (!repository.suspendByAdmin(id, adminId)) {
                throw new ProActionException(ProActionException.Reason.CONFLICT, "Food truck introuvable ou archivé.");
            }
            LOGGER.info("Food truck {} suspendu par l'administrateur {}", id, adminId);
            return ResponseEntity.ok(OwnedFoodTruckResponse.from(repository.findById(id).orElseThrow()));
        });
    }

    @Operation(summary = "Lever la suspension", description = "Le food truck est de nouveau publié.")
    @PatchMapping("/{id}/reinstate")
    public ResponseEntity<Object> reinstate(@PathVariable Long id, Authentication authentication,
            HttpServletRequest request) {
        Long adminId = ((JwtAuthentication) authentication).getUserId();
        return ProFoodTruckController.handle(request, () -> {
            refuseOwn(adminId, id);
            if (!repository.reinstate(id)) {
                throw new ProActionException(ProActionException.Reason.CONFLICT,
                        "Ce food truck n'est pas suspendu par LocalLife.");
            }
            LOGGER.info("Suspension du food truck {} levée par l'administrateur {}", id, adminId);
            return ResponseEntity.ok(OwnedFoodTruckResponse.from(repository.findById(id).orElseThrow()));
        });
    }

    @Operation(summary = "Valider la première publication", description = "PENDING → PUBLISHED. 403 pour son propre "
            + "food truck, 409 s'il n'attend pas de validation ou si son propriétaire n'a plus d'accès professionnel.")
    @PostMapping("/{id}/approve")
    public ResponseEntity<Object> approve(@PathVariable Long id, Authentication authentication,
            HttpServletRequest request) {
        Long adminId = ((JwtAuthentication) authentication).getUserId();
        return ProFoodTruckController.handle(request, () -> ResponseEntity.ok(OwnedFoodTruckResponse.from(
                proFoodTruckService.review(adminId, id, true, null))));
    }

    @Operation(summary = "Refuser la première publication", description = "PENDING → REJECTED, motif obligatoire ; "
            + "le propriétaire peut corriger et renvoyer.")
    @PostMapping("/{id}/reject")
    public ResponseEntity<Object> reject(@PathVariable Long id, @RequestBody(required = false) ReviewRequest body,
            Authentication authentication, HttpServletRequest request) {
        Long adminId = ((JwtAuthentication) authentication).getUserId();
        return ProFoodTruckController.handle(request, () -> ResponseEntity.ok(OwnedFoodTruckResponse.from(
                proFoodTruckService.review(adminId, id, false, body == null ? null : body.reason()))));
    }

    /** Un administrateur ne suspend ni ne rétablit son propre food truck (décision du 22/10/2026). */
    private void refuseOwn(Long adminId, Long id) {
        repository.findById(id).filter(foodTruck -> adminId.equals(foodTruck.ownerId())).ifPresent(own -> {
            throw new ProActionException(ProActionException.Reason.FORBIDDEN,
                    "Un administrateur ne suspend ni ne rétablit son propre food truck.");
        });
    }
}
