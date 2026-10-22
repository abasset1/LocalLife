package com.locallife.backend.foodtruck.application;

import com.locallife.backend.foodtruck.domain.FoodTruck;
import com.locallife.backend.foodtruck.domain.FoodTruckPresence;
import com.locallife.backend.foodtruck.infrastructure.FoodTruckPresenceRepository;
import com.locallife.backend.foodtruck.infrastructure.FoodTruckRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
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

    private final FoodTruckRepository foodTruckRepository;
    private final FoodTruckPresenceRepository presenceRepository;

    public FoodTruckService(FoodTruckRepository foodTruckRepository, FoodTruckPresenceRepository presenceRepository) {
        this.foodTruckRepository = foodTruckRepository;
        this.presenceRepository = presenceRepository;
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
}
