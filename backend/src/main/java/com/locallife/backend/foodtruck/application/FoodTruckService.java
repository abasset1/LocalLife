package com.locallife.backend.foodtruck.application;

import com.locallife.backend.foodtruck.domain.FoodTruck;
import com.locallife.backend.foodtruck.infrastructure.FoodTruckRepository;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Service FoodTruck (LL-6009) — création avec validation (mêmes règles
 * qu'{@code ActivityService#createActivity} pour les champs communs :
 * nom obligatoire, coordonnées dans les bornes valides) et consultation
 * publique restreinte aux food trucks {@code PUBLISHED}, même convention
 * qu'{@code ActivityService#findNearby}/{@code #findWithinBounds} depuis
 * LL-6004.
 *
 * Pas de géocodage d'adresse (contrairement à
 * {@code ActivityService#createActivity} depuis LL-3012) : la création
 * reçoit directement {@code latitude}/{@code longitude} — décision prise
 * pour rester minimal, le ticket LL-6009 ne demande pas de saisie par
 * adresse ; à réévaluer si un besoin explicite apparaît plus tard (pas
 * de changement spéculatif).
 */
@Service
public class FoodTruckService {

    /** Statut retourné par la consultation publique — voir la javadoc de la classe. */
    private static final String PUBLIC_STATUS = "PUBLISHED";

    /**
     * Statut par défaut d'un food truck nouvellement créé — voir la
     * javadoc de {@link FoodTruck} pour la justification du choix
     * {@code PUBLISHED} plutôt que {@code PENDING} (aucun endpoint de
     * modération pour les food trucks à ce stade).
     */
    private static final String DEFAULT_STATUS = "PUBLISHED";

    /** Même limite qu'{@code ActivityService} pour {@code title}, appliquée ici à {@code name}. */
    private static final int MAX_NAME_LENGTH = 255;

    private final FoodTruckRepository foodTruckRepository;

    public FoodTruckService(FoodTruckRepository foodTruckRepository) {
        this.foodTruckRepository = foodTruckRepository;
    }

    /**
     * Food trucks visibles publiquement (LL-6009, critère « visibilité
     * sur la carte ») : uniquement {@code PUBLISHED}, comme les activités
     * depuis LL-6004.
     */
    public List<FoodTruck> findAllPublished() {
        return foodTruckRepository.findByStatus(PUBLIC_STATUS);
    }

    /**
     * Crée un food truck (LL-6009, critère « création possible »).
     * Validation alignée sur {@code ActivityService#createActivity} pour
     * les champs communs (nom obligatoire et borné, coordonnées dans les
     * bornes valides) ; {@code category} obligatoire ici (contrairement à
     * {@code Activity.category}, optionnelle) — voir
     * {@code FOOD_TRUCK_CONTRACT.md}, qui la marque explicitement comme
     * obligatoire (type de cuisine). {@code description}/{@code contact}
     * restent optionnels, sans validation de format.
     */
    public FoodTruck createFoodTruck(
            String name, String description, double latitude, double longitude, String category, String contact) {
        validateName(name);
        validateCategory(category);
        validateLatitude(latitude);
        validateLongitude(longitude);

        FoodTruck foodTruck = new FoodTruck(
                null, name, description, latitude, longitude, category, contact, DEFAULT_STATUS);
        return foodTruckRepository.save(foodTruck);
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Le champ 'name' est obligatoire.");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException(
                    "Le champ 'name' ne doit pas dépasser " + MAX_NAME_LENGTH + " caractères.");
        }
    }

    private void validateCategory(String category) {
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("Le champ 'category' est obligatoire.");
        }
    }

    private void validateLatitude(double latitude) {
        if (latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("Le champ 'latitude' doit être compris entre -90 et 90.");
        }
    }

    private void validateLongitude(double longitude) {
        if (longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Le champ 'longitude' doit être compris entre -180 et 180.");
        }
    }

}
