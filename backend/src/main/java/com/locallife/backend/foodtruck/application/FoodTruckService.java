package com.locallife.backend.foodtruck.application;

import com.locallife.backend.foodtruck.domain.FoodTruck;
import com.locallife.backend.foodtruck.domain.FoodTruckPresence;
import com.locallife.backend.foodtruck.domain.FoodTruckStatus;
import com.locallife.backend.foodtruck.infrastructure.FoodTruckPresenceRepository;
import com.locallife.backend.foodtruck.infrastructure.FoodTruckRepository;
import com.locallife.backend.foodtruck.infrastructure.OwnedFoodTruckRepository;
import com.locallife.backend.foodtruck.infrastructure.OwnedFoodTruckRepository.ProfileFields;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final FoodTruckPresenceRepository presenceRepository;
    private final OwnedFoodTruckRepository ownedRepository;

    public FoodTruckService(FoodTruckRepository foodTruckRepository, FoodTruckPresenceRepository presenceRepository,
            OwnedFoodTruckRepository ownedRepository) {
        this.foodTruckRepository = foodTruckRepository;
        this.presenceRepository = presenceRepository;
        this.ownedRepository = ownedRepository;
    }

    /** Fuseau des dates de présence (« aujourd'hui » pour la carte). */
    public static final ZoneId PRESENCE_ZONE = ZoneId.of("Europe/Paris");

    /** Fiche publique d'un food truck et son planning (ADR-0013). */
    public record FoodTruckDetail(FoodTruck foodTruck, List<FoodTruckPresence> presences) {
    }

    /**
     * Présences actives le jour donné (ADR-0013, décision D2) : un élément par présence, un food truck présent à
     * deux endroits apparaît deux fois. Seuls les food trucks {@code PUBLISHED} et les présences publiées valables
     * ce jour-là sont retenus : une présence expirée, à venir ou suspendue n'est jamais présentée comme actuelle.
     */
    public List<FoodTruckPresence> findActiveOn(LocalDate day) {
        return presenceRepository.findActiveOn(day, null);
    }

    /** Food trucks présents le jour donné à un lieu LocalLife (lui-même ou un emplacement qui lui est relié). */
    public List<FoodTruckPresence> findActiveAtPlace(Long placeId, LocalDate day) {
        return presenceRepository.findActiveOn(day, placeId);
    }

    /** Food truck publié et ses présences encore valables à partir de {@code from} ; vide s'il n'est pas publié. */
    public Optional<FoodTruckDetail> findPublishedDetail(Long id, LocalDate from) {
        return foodTruckRepository.findById(id).filter(foodTruck -> PUBLIC_STATUS.equals(foodTruck.status()))
                .map(foodTruck -> new FoodTruckDetail(foodTruck, presenceRepository.findUpcomingForTruck(id, from)));
    }

    public static LocalDate today() {
        return LocalDate.now(PRESENCE_ZONE);
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
     *
     * Depuis ADR-0013, la position reçue n'est plus écrite sur le food truck :
     * elle devient une présence permanente sur un emplacement créé pour lui
     * (même forme que les food trucks migrés par V65). La réponse garde la
     * position reçue, comme avant.
     *
     * Depuis PRO-01 lot C, réservé aux comptes professionnels (vérifié par
     * {@code FoodTruckController}) : {@code ownerId} devient le propriétaire.
     */
    @Transactional
    public FoodTruck createFoodTruck(Long ownerId,
            String name, String description, double latitude, double longitude, String category, String contact) {
        validateName(name);
        validateCategory(category);
        validateLatitude(latitude);
        validateLongitude(longitude);

        Long id = ownedRepository.insert(ownerId, new ProfileFields(name, description, category, contact),
                FoodTruckStatus.valueOf(DEFAULT_STATUS));
        presenceRepository.insertPermanent(id, latitude, longitude);
        // Relu pour renvoyer la date de publication posée par la base (trigger V55).
        FoodTruck read = foodTruckRepository.findById(id).orElseThrow();
        return new FoodTruck(read.id(), read.name(), read.description(), latitude, longitude, read.category(),
                read.contact(), read.status(), read.publishedAt());
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
