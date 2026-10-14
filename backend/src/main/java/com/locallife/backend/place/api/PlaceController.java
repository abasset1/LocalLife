package com.locallife.backend.place.api;

import com.locallife.backend.common.ErrorResponse;
import com.locallife.backend.place.infrastructure.PlaceRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lieux permanents publiés (ADR-0011, phase B) : couche « Lieux » de la carte. Seuls les lieux {@code PUBLISHED}
 * sont renvoyés ; public, comme les activités.
 */
@RestController
@RequestMapping("/api/v1/places")
public class PlaceController {

    static final int MAX_RESULTS = 500;

    private final PlaceRepository placeRepository;

    public PlaceController(PlaceRepository placeRepository) {
        this.placeRepository = placeRepository;
    }

    @Operation(summary = "Lieux publiés dans une zone",
            description = "Lieux permanents publiés dans le rectangle donné (au plus 500), éventuellement limités "
                    + "à certains types (liste séparée par des virgules, ex. Museum,Theater).")
    @GetMapping("/within-bounds")
    public ResponseEntity<Object> withinBounds(
            @RequestParam double minLat, @RequestParam double maxLat,
            @RequestParam double minLng, @RequestParam double maxLng,
            @Parameter(description = "Types de lieux, séparés par des virgules.") @RequestParam(required = false)
            String types,
            HttpServletRequest request) {
        if (minLat > maxLat || minLng > maxLng || minLat < -90 || maxLat > 90 || minLng < -180 || maxLng > 180) {
            return ResponseEntity.badRequest().body(new ErrorResponse(Instant.now(), 400,
                    HttpStatus.BAD_REQUEST.getReasonPhrase(), "Zone invalide.", request.getRequestURI()));
        }
        List<String> placeTypes = types == null || types.isBlank() ? List.of()
                : Arrays.stream(types.split(",")).map(String::trim).filter(type -> !type.isEmpty()).toList();
        return ResponseEntity.ok(placeRepository.findPublishedWithinBounds(
                minLat, maxLat, minLng, maxLng, placeTypes, MAX_RESULTS).stream().map(PlaceResponse::from).toList());
    }
}
