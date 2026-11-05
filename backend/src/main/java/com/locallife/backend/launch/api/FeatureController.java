package com.locallife.backend.launch.api;

import com.locallife.backend.launch.Feature;
import com.locallife.backend.launch.LaunchFeatures;
import io.swagger.v3.oas.annotations.Operation;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Fonctions disponibles pour l'appelant ({@link LaunchFeatures}) : le site et l'application les lisent au démarrage
 * pour masquer ce qui n'est pas encore ouvert. Public ; avec un JWT de l'équipe LocalLife ou d'un administrateur,
 * tout est disponible.
 */
@RestController
public class FeatureController {

    private final LaunchFeatures launchFeatures;

    public FeatureController(LaunchFeatures launchFeatures) {
        this.launchFeatures = launchFeatures;
    }

    /** Disponibilité de chaque fonction pour l'appelant. */
    public record FeaturesResponse(boolean places, boolean foodTrucks, boolean premium, boolean pro) {
    }

    @Operation(summary = "Fonctions disponibles", description = "Lieux, food trucks, offres Premium et Pro : "
            + "disponibles ou pas encore ouvertes. L'équipe LocalLife et les administrateurs voient tout.")
    @GetMapping("/api/v1/features")
    public FeaturesResponse features(Authentication authentication) {
        Map<Feature, Boolean> available = launchFeatures.availability(authentication);
        return new FeaturesResponse(available.get(Feature.PLACES), available.get(Feature.FOOD_TRUCKS),
                available.get(Feature.PREMIUM), available.get(Feature.PRO));
    }
}
