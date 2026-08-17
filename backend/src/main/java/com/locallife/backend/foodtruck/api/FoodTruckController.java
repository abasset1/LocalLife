package com.locallife.backend.foodtruck.api;

import com.locallife.backend.common.ErrorResponse;
import com.locallife.backend.foodtruck.application.FoodTruckService;
import com.locallife.backend.foodtruck.domain.FoodTruck;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contrôleur REST pour les food trucks (LL-6009). Périmètre strictement
 * limité aux critères d'acceptation du ticket : consultation publique
 * (visibilité sur la carte) et création — pas de consultation par id, pas
 * de mise à jour/suppression, pas de modération (voir
 * {@code FoodTruckService}/{@code FoodTruck} pour le détail des
 * décisions), pas de système de commande ou paiement (exclu
 * explicitement par le ticket).
 *
 * {@code POST} protégé de la même façon que
 * {@code POST /api/v1/activities} (LL-3008, {@code SecurityConfig}) :
 * utilisateur connecté requis, même posture de sécurité qu'une
 * contribution d'activité.
 */
@RestController
@RequestMapping("/api/v1/foodtrucks")
public class FoodTruckController {

    private final FoodTruckService foodTruckService;

    public FoodTruckController(FoodTruckService foodTruckService) {
        this.foodTruckService = foodTruckService;
    }

    @GetMapping
    public ResponseEntity<List<FoodTruck>> getAllFoodTrucks() {
        List<FoodTruck> foodTrucks = foodTruckService.findAllPublished();
        return ResponseEntity.ok(foodTrucks);
    }

    @PostMapping
    public ResponseEntity<Object> createFoodTruck(
            @RequestBody CreateFoodTruckRequest request, HttpServletRequest httpRequest) {
        try {
            FoodTruck foodTruck = foodTruckService.createFoodTruck(
                    request.name(), request.description(), request.latitude(), request.longitude(),
                    request.category(), request.contact());
            return ResponseEntity.status(HttpStatus.CREATED).body(foodTruck);
        } catch (IllegalArgumentException exception) {
            return errorResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), httpRequest);
        }
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
    public record CreateFoodTruckRequest(
            String name, String description, double latitude, double longitude, String category, String contact) {
    }

}
