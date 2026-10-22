package com.locallife.backend.foodtruck.api;

import com.locallife.backend.common.ErrorResponse;
import com.locallife.backend.foodtruck.application.FoodTruckService;
import com.locallife.backend.foodtruck.application.FoodTruckService.FoodTruckDetail;
import com.locallife.backend.foodtruck.domain.FoodTruck;
import com.locallife.backend.foodtruck.domain.FoodTruckPresence;
import com.locallife.backend.foodtruck.domain.SpotKind;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contrôleur REST pour les food trucks (LL-6009). Périmètre strictement
 * limité à la consultation publique (carte, fiche, food trucks d'un lieu).
 * La création et la gestion passent par l'espace professionnel
 * ({@code /api/v1/pro/foodtrucks}) : l'ancienne création directe
 * ({@code POST /api/v1/foodtrucks}, publiée sans validation) a été retirée
 * le 22/10/2026.
 *
 * Annotations Swagger/OpenAPI ajoutées en LL-6011 (documentation de fin
 * de sprint) — voir la remarque équivalente sur {@code SourceController}.
 */
@RestController
@RequestMapping("/api/v1")
public class FoodTruckController {

    private final FoodTruckService foodTruckService;

    public FoodTruckController(FoodTruckService foodTruckService) {
        this.foodTruckService = foodTruckService;
    }

    @Operation(
            summary = "Food trucks présents un jour donné",
            description = "Un élément par présence active (ADR-0013) : un food truck présent à deux endroits apparaît "
                    + "deux fois ; latitude/longitude sont celles de l'emplacement. Présences expirées, à venir ou "
                    + "suspendues exclues. Endpoint public.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Liste renvoyée avec succès (peut être vide)."),
        @ApiResponse(responseCode = "400", description = "Date invalide (format yyyy-MM-dd attendu).")
    })
    @GetMapping("/foodtrucks")
    public ResponseEntity<Object> getAllFoodTrucks(
            @Parameter(description = "Jour (yyyy-MM-dd) ; aujourd'hui, heure de Paris, par défaut.")
            @RequestParam(required = false) String date, HttpServletRequest httpRequest) {
        LocalDate day;
        try {
            day = date == null || date.isBlank() ? FoodTruckService.today() : LocalDate.parse(date);
        } catch (DateTimeParseException exception) {
            return errorResponse(HttpStatus.BAD_REQUEST, "Date invalide : yyyy-MM-dd attendu.", httpRequest);
        }
        return ResponseEntity.ok(foodTruckService.findActiveOn(day).stream().map(FoodTruckMapResponse::from).toList());
    }

    @Operation(summary = "Fiche d'un food truck",
            description = "Food truck publié et son planning : ses présences publiées encore valables. 404 sinon.")
    @GetMapping("/foodtrucks/{id}")
    public ResponseEntity<Object> getFoodTruck(@PathVariable Long id) {
        return foodTruckService.findPublishedDetail(id, FoodTruckService.today())
                .<ResponseEntity<Object>>map(detail -> ResponseEntity.ok(FoodTruckDetailResponse.from(detail)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(summary = "Food trucks présents à un lieu",
            description = "Présences actives aujourd'hui sur ce lieu LocalLife ou sur un emplacement qui lui "
                    + "est relié.")
    @GetMapping("/places/{placeId}/foodtrucks")
    public List<FoodTruckMapResponse> getFoodTrucksAtPlace(@PathVariable Long placeId) {
        return foodTruckService.findActiveAtPlace(placeId, FoodTruckService.today()).stream()
                .map(FoodTruckMapResponse::from).toList();
    }

    private ResponseEntity<Object> errorResponse(HttpStatus status, String message, HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                Instant.now(), status.value(), status.getReasonPhrase(), message, request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }

    /**
     * Corps de requête pour la création d'un food truck (LL-6009).
     * Contrairement à {@code ActivityController.CreateActivityRequest},
     * reçoit directement {@code latitude}/{@code longitude} (pas
     * d'adresse à géocoder) — voir {@code FoodTruckService} pour cette
     * décision. {@code id} et {@code status} restent gérés côté serveur.
     */
    /**
     * Food truck sur la carte, pour une présence (ADR-0013). Garde les champs historiques de
     * {@code GET /api/v1/foodtrucks} ({@code id} = celui du food truck) et ajoute la présence.
     */
    public record FoodTruckMapResponse(
            Long id, String name, String description, Double latitude, Double longitude, String category,
            String contact, String status, Long presenceId, String spotName, SpotKind spotKind, String daysOfWeek,
            LocalTime startTime, LocalTime endTime, LocalDate validFrom, LocalDate validUntil, Long placeId) {

        static FoodTruckMapResponse from(FoodTruckPresence presence) {
            return new FoodTruckMapResponse(presence.foodTruckId(), presence.foodTruckName(), presence.description(),
                    presence.latitude(), presence.longitude(), presence.category(), presence.contact(), "PUBLISHED",
                    presence.id(), presence.spotName(), presence.spotKind(), presence.daysOfWeek(),
                    presence.startTime(), presence.endTime(), presence.validFrom(), presence.validUntil(),
                    presence.placeId());
        }
    }

    /** Présence dans le planning d'un food truck. */
    public record PresenceResponse(
            Long id, Double latitude, Double longitude, String spotName, SpotKind spotKind, String daysOfWeek,
            LocalTime startTime, LocalTime endTime, LocalDate validFrom, LocalDate validUntil, Long placeId) {

        static PresenceResponse from(FoodTruckPresence presence) {
            return new PresenceResponse(presence.id(), presence.latitude(), presence.longitude(),
                    presence.spotName(), presence.spotKind(), presence.daysOfWeek(), presence.startTime(),
                    presence.endTime(), presence.validFrom(), presence.validUntil(), presence.placeId());
        }
    }

    /** Fiche publique d'un food truck : son profil et son planning, distincts. */
    public record FoodTruckDetailResponse(
            Long id, String name, String description, String category, String contact,
            List<PresenceResponse> presences) {

        static FoodTruckDetailResponse from(FoodTruckDetail detail) {
            FoodTruck foodTruck = detail.foodTruck();
            return new FoodTruckDetailResponse(foodTruck.id(), foodTruck.name(), foodTruck.description(),
                    foodTruck.category(), foodTruck.contact(),
                    detail.presences().stream().map(PresenceResponse::from).toList());
        }
    }

}
