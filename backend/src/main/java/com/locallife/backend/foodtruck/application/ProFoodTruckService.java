package com.locallife.backend.foodtruck.application;

import com.locallife.backend.foodtruck.domain.FoodTruckStatus;
import com.locallife.backend.foodtruck.domain.ManagedPresence;
import com.locallife.backend.foodtruck.domain.PresenceRules;
import com.locallife.backend.foodtruck.domain.PresenceRules.Slot;
import com.locallife.backend.foodtruck.domain.PresenceStatus;
import com.locallife.backend.foodtruck.domain.SpotKind;
import com.locallife.backend.foodtruck.infrastructure.FoodTruckPresenceRepository;
import com.locallife.backend.foodtruck.infrastructure.FoodTruckPresenceRepository.PresenceFields;
import com.locallife.backend.foodtruck.infrastructure.OwnedFoodTruckRepository;
import com.locallife.backend.foodtruck.infrastructure.OwnedFoodTruckRepository.OwnedFoodTruck;
import com.locallife.backend.foodtruck.infrastructure.OwnedFoodTruckRepository.ProfileFields;
import com.locallife.backend.geocoding.application.Coordinates;
import com.locallife.backend.geocoding.application.GeocodingException;
import com.locallife.backend.geocoding.application.GeocodingService;
import com.locallife.backend.geocoding.application.GeocodingUnavailableException;
import com.locallife.backend.pro.application.ProAccessPolicy;
import com.locallife.backend.pro.application.ProActionException;
import com.locallife.backend.pro.application.ProNotifier;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Food trucks gérés par leur propriétaire professionnel : profil (PRO-01 lot C) et présences (FOODTRUCK-01).
 *
 * <p>Profil : créé en brouillon ; sa première publication est validée par l'équipe (décision du 22/10/2026,
 * remplace la décision D6) : « publier » l'envoie en validation ({@code PENDING}), un administrateur le publie ou le
 * refuse. Une fois validé, son propriétaire le suspend et le republie lui-même ; il peut toujours l'archiver
 * (définitif). Un food truck suspendu par un administrateur ne peut pas être republié par son propriétaire.
 *
 * <p>Sans accès professionnel (offre terminée, rôle retiré), le propriétaire garde la consultation de ses food
 * trucks et peut les archiver, rien de plus ; ses food trucks publiés ont été suspendus
 * ({@code ProAccessSynchronizer}).
 *
 * <p>Présences : un emplacement (lieu LocalLife publié, ou adresse saisie), un type, des jours, des horaires et une
 * période ; créées en brouillon, publiées, suspendues, réactivées ou archivées. Deux présences non archivées d'un
 * même food truck ne peuvent pas se chevaucher : il ne peut pas être annoncé à deux endroits au même moment.
 * LocalLife ne vaut pas autorisation de stationnement : l'interface le rappelle.
 *
 * <p>Un professionnel ne voit et ne modifie que ses propres food trucks (404 sinon).
 */
@Service
public class ProFoodTruckService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProFoodTruckService.class);

    private static final int MAX_NAME_LENGTH = 255;
    private static final int MAX_CATEGORY_LENGTH = 255;
    private static final int MAX_DESCRIPTION_LENGTH = 2000;
    private static final int MAX_CONTACT_LENGTH = 512;
    private static final int MAX_SPOT_TEXT_LENGTH = 255;
    private static final int MAX_ADDRESS_LENGTH = 512;

    private static final Set<PresenceStatus> NOT_ARCHIVED =
            EnumSet.of(PresenceStatus.DRAFT, PresenceStatus.PUBLISHED, PresenceStatus.SUSPENDED);

    private final OwnedFoodTruckRepository foodTruckRepository;
    private final FoodTruckPresenceRepository presenceRepository;
    private final ProAccessPolicy proAccessPolicy;
    private final GeocodingService geocodingService;
    private final ProNotifier notifier;

    public ProFoodTruckService(OwnedFoodTruckRepository foodTruckRepository,
            FoodTruckPresenceRepository presenceRepository, ProAccessPolicy proAccessPolicy,
            GeocodingService geocodingService, ProNotifier notifier) {
        this.foodTruckRepository = foodTruckRepository;
        this.presenceRepository = presenceRepository;
        this.proAccessPolicy = proAccessPolicy;
        this.geocodingService = geocodingService;
        this.notifier = notifier;
    }

    /** Profil saisi. */
    public record ProfileInput(String name, String description, String category, String contact) {
    }

    /** Nouvel emplacement saisi : position facultative, déduite de l'adresse si absente. */
    public record SpotInput(
            String name, String address, String postalCode, String city, Double latitude, Double longitude) {
    }

    /**
     * Présence saisie : un lieu LocalLife publié ({@code placeId}) ou un nouvel emplacement ({@code newSpot}) ;
     * jours (codes {@code MO..SU}, vide = tous les jours) ; horaires {@code HH:mm} ; dates {@code yyyy-MM-dd}.
     */
    public record PresenceInput(
            Long placeId, SpotInput newSpot, String spotKind, List<String> days, String startTime, String endTime,
            String validFrom, String validUntil) {
    }

    /** Action du propriétaire sur un profil ou une présence. */
    public enum OwnerAction {
        PUBLISH, SUSPEND, ARCHIVE
    }

    // ---------------------------------------------------------------- profil (PRO-01 lot C)

    @Transactional
    public OwnedFoodTruck create(Long userId, ProfileInput input) {
        requirePro(userId);
        Long id = foodTruckRepository.insert(userId, validate(input), FoodTruckStatus.DRAFT);
        LOGGER.info("Food truck {} créé en brouillon par le professionnel {}", id, userId);
        return foodTruckRepository.findById(id).orElseThrow();
    }

    @Transactional
    public OwnedFoodTruck update(Long userId, Long id, ProfileInput input) {
        OwnedFoodTruck foodTruck = owned(userId, id);
        if (foodTruck.status() == FoodTruckStatus.ARCHIVED) {
            throw ProActionException.conflict("Un food truck archivé n'est plus modifiable.");
        }
        foodTruckRepository.update(id, validate(input));
        return foodTruckRepository.findById(id).orElseThrow();
    }

    @Transactional
    public OwnedFoodTruck act(Long userId, Long id, OwnerAction action) {
        OwnedFoodTruck foodTruck = action == OwnerAction.ARCHIVE ? ownedForReading(userId, id) : owned(userId, id);
        if (action == OwnerAction.PUBLISH && foodTruck.suspendedByAdminAt() != null) {
            throw ProActionException.conflict("Ce food truck a été suspendu par LocalLife : contacte l'équipe pour "
                    + "le rétablir.");
        }
        boolean approved = foodTruck.approvedAt() != null;
        boolean changed = switch (action) {
            case PUBLISH -> approved
                    ? foodTruckRepository.transition(id,
                            EnumSet.of(FoodTruckStatus.DRAFT, FoodTruckStatus.SUSPENDED), FoodTruckStatus.PUBLISHED)
                    : foodTruckRepository.transition(id,
                            EnumSet.of(FoodTruckStatus.DRAFT, FoodTruckStatus.REJECTED), FoodTruckStatus.PENDING);
            // Suspendre une demande de validation la retire : le food truck revient en brouillon.
            case SUSPEND -> foodTruck.status() == FoodTruckStatus.PENDING
                    ? foodTruckRepository.transition(id, EnumSet.of(FoodTruckStatus.PENDING), FoodTruckStatus.DRAFT)
                    : foodTruckRepository.transition(id, EnumSet.of(FoodTruckStatus.PUBLISHED),
                            FoodTruckStatus.SUSPENDED);
            case ARCHIVE -> foodTruckRepository.transition(id, EnumSet.of(FoodTruckStatus.DRAFT,
                    FoodTruckStatus.PENDING, FoodTruckStatus.PUBLISHED, FoodTruckStatus.SUSPENDED,
                    FoodTruckStatus.REJECTED), FoodTruckStatus.ARCHIVED);
        };
        if (!changed) {
            throw ProActionException.conflict("Action impossible pour un food truck au statut " + foodTruck.status()
                    + ".");
        }
        if (action == OwnerAction.PUBLISH && !approved) {
            LOGGER.info("Food truck {} envoyé en validation par le professionnel {}", id, userId);
        }
        return foodTruckRepository.findById(id).orElseThrow();
    }

    // ---------------------------------------------------------------- validation (ADMIN)

    /**
     * Première publication validée par un administrateur, jamais par le propriétaire lui-même ; un refus exige un
     * motif, le propriétaire peut corriger et renvoyer.
     */
    @Transactional
    public OwnedFoodTruck review(Long adminId, Long id, boolean approve, String reason) {
        OwnedFoodTruck foodTruck = foodTruckRepository.findById(id)
                .orElseThrow(() -> ProActionException.notFound("Food truck introuvable."));
        if (adminId.equals(foodTruck.ownerId())) {
            throw ProActionException.forbidden("Un administrateur ne valide pas son propre food truck.");
        }
        String cleanReason = text(reason, MAX_DESCRIPTION_LENGTH, "Le motif");
        if (!approve && cleanReason == null) {
            throw ProActionException.invalid("Un motif est obligatoire pour refuser un food truck.");
        }
        if (approve && (foodTruck.ownerId() == null || !proAccessPolicy.hasProAccess(foodTruck.ownerId()))) {
            throw ProActionException.conflict("Le propriétaire n'a plus d'accès professionnel.");
        }
        if (!foodTruckRepository.review(id, approve, adminId, cleanReason)) {
            throw ProActionException.conflict("Ce food truck n'attend pas de validation.");
        }
        LOGGER.info("Food truck {} {} par l'administrateur {}", id, approve ? "validé" : "refusé", adminId);
        String name = "« " + foodTruck.name() + " »";
        if (approve) {
            notifier.notify(foodTruck.ownerId(), "Ton food truck est publié",
                    "L'équipe LocalLife a validé " + name + " : il est visible sur la carte. Tu le gères désormais "
                            + "sans validation.", null);
        } else {
            notifier.notify(foodTruck.ownerId(), "Ton food truck n'a pas été publié",
                    "L'équipe LocalLife n'a pas validé " + name + ". Corrige-le puis renvoie-le depuis ton espace "
                            + "professionnel.", cleanReason);
        }
        return foodTruckRepository.findById(id).orElseThrow();
    }

    /** Ses food trucks ; un ex-professionnel les consulte encore, un compte qui n'en a jamais eu reçoit 403. */
    public List<OwnedFoodTruck> findMine(Long userId) {
        List<OwnedFoodTruck> mine = foodTruckRepository.findByOwner(userId);
        if (mine.isEmpty()) {
            requirePro(userId);
        }
        return mine;
    }

    public OwnedFoodTruck findMine(Long userId, Long id) {
        return ownedForReading(userId, id);
    }

    // ---------------------------------------------------------------- présences (FOODTRUCK-01)

    public List<ManagedPresence> findPresences(Long userId, Long foodTruckId) {
        ownedForReading(userId, foodTruckId);
        return presenceRepository.findAllForTruck(foodTruckId);
    }

    @Transactional
    public ManagedPresence createPresence(Long userId, Long foodTruckId, PresenceInput input) {
        OwnedFoodTruck foodTruck = owned(userId, foodTruckId);
        if (foodTruck.status() == FoodTruckStatus.ARCHIVED) {
            throw ProActionException.conflict("Un food truck archivé ne reçoit plus d'emplacement.");
        }
        PresenceFields fields = validate(input, null);
        requireNoOverlap(foodTruckId, null, fields);
        Long id = presenceRepository.insert(foodTruckId, fields, PresenceStatus.DRAFT);
        return presenceRepository.findManaged(id).orElseThrow();
    }

    @Transactional
    public ManagedPresence updatePresence(Long userId, Long presenceId, PresenceInput input) {
        ManagedPresence presence = ownedPresence(userId, presenceId, true);
        if (presence.status() == PresenceStatus.ARCHIVED) {
            throw ProActionException.conflict("Un emplacement archivé n'est plus modifiable.");
        }
        PresenceFields fields = validate(input, presence.locationId());
        requireNoOverlap(presence.presence().foodTruckId(), presenceId, fields);
        presenceRepository.update(presenceId, fields);
        return presenceRepository.findManaged(presenceId).orElseThrow();
    }

    /** Publier (ou réactiver une présence suspendue), suspendre, archiver. */
    @Transactional
    public ManagedPresence actOnPresence(Long userId, Long presenceId, OwnerAction action) {
        ManagedPresence presence = ownedPresence(userId, presenceId, action != OwnerAction.ARCHIVE);
        if (action == OwnerAction.PUBLISH && presence.presence().validUntil() != null
                && presence.presence().validUntil().isBefore(FoodTruckService.today())) {
            throw ProActionException.conflict("La période de cet emplacement est passée : modifie-la d'abord.");
        }
        boolean changed = switch (action) {
            case PUBLISH -> presenceRepository.transition(presenceId,
                    EnumSet.of(PresenceStatus.DRAFT, PresenceStatus.SUSPENDED), PresenceStatus.PUBLISHED);
            case SUSPEND -> presenceRepository.transition(presenceId, EnumSet.of(PresenceStatus.PUBLISHED),
                    PresenceStatus.SUSPENDED);
            case ARCHIVE -> presenceRepository.transition(presenceId, NOT_ARCHIVED, PresenceStatus.ARCHIVED);
        };
        if (!changed) {
            throw ProActionException.conflict("Action impossible pour un emplacement au statut " + presence.status()
                    + ".");
        }
        return presenceRepository.findManaged(presenceId).orElseThrow();
    }

    // ---------------------------------------------------------------- outils

    /** Food truck du propriétaire, pour le modifier : accès professionnel exigé. */
    private OwnedFoodTruck owned(Long userId, Long id) {
        requirePro(userId);
        return ownedForReading(userId, id);
    }

    /** Food truck du propriétaire, pour le consulter ou l'archiver, même sans accès professionnel. */
    private OwnedFoodTruck ownedForReading(Long userId, Long id) {
        return foodTruckRepository.findById(id).filter(foodTruck -> userId.equals(foodTruck.ownerId()))
                .orElseThrow(() -> ProActionException.notFound("Food truck introuvable."));
    }

    private ManagedPresence ownedPresence(Long userId, Long presenceId, boolean modifies) {
        if (modifies) {
            requirePro(userId);
        }
        ManagedPresence presence = presenceRepository.findManaged(presenceId)
                .orElseThrow(() -> ProActionException.notFound("Emplacement introuvable."));
        ownedForReading(userId, presence.presence().foodTruckId());
        return presence;
    }

    private void requireNoOverlap(Long foodTruckId, Long excludedPresenceId, PresenceFields fields) {
        Slot slot = slot(fields);
        for (ManagedPresence other : presenceRepository.findAllForTruck(foodTruckId)) {
            if (other.status() == PresenceStatus.ARCHIVED || other.presence().id().equals(excludedPresenceId)) {
                continue;
            }
            var presence = other.presence();
            if (PresenceRules.overlaps(slot, new Slot(presence.daysOfWeek(), presence.startTime(),
                    presence.endTime(), presence.validFrom(), presence.validUntil()))) {
                throw ProActionException.conflict("Ce créneau chevauche un autre emplacement de ce food truck ("
                        + (presence.spotName() == null ? "sans nom" : presence.spotName()) + ").");
            }
        }
    }

    private static Slot slot(PresenceFields fields) {
        return new Slot(fields.daysOfWeek(), fields.startTime(), fields.endTime(), fields.validFrom(),
                fields.validUntil());
    }

    private PresenceFields validate(PresenceInput input, Long currentLocationId) {
        if (input == null) {
            throw ProActionException.invalid("Corps de requête manquant.");
        }
        SpotKind kind;
        try {
            kind = SpotKind.valueOf(input.spotKind() == null ? "" : input.spotKind().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw ProActionException.invalid("Type d'emplacement inconnu : MARKET, PRIVATE, EVENT ou OTHER.");
        }
        String days;
        try {
            days = PresenceRules.normalizeDays(input.days());
        } catch (IllegalArgumentException exception) {
            throw ProActionException.invalid(exception.getMessage());
        }
        LocalTime start = time(input.startTime(), "L'heure d'arrivée");
        LocalTime end = time(input.endTime(), "L'heure de départ");
        LocalDate from = date(input.validFrom(), "La date de début");
        LocalDate until = date(input.validUntil(), "La date de fin");
        try {
            PresenceRules.validate(new Slot(days, start, end, from, until), FoodTruckService.today());
        } catch (IllegalArgumentException exception) {
            throw ProActionException.invalid(exception.getMessage());
        }
        Long locationId = spot(input, currentLocationId);
        return new PresenceFields(locationId, kind, days, start, end, from, until);
    }

    /**
     * Emplacement de la présence : un lieu LocalLife publié, un nouvel emplacement saisi, ou, à la modification,
     * l'emplacement actuel si aucun des deux n'est fourni.
     */
    private Long spot(PresenceInput input, Long currentLocationId) {
        if (input.placeId() != null && input.newSpot() != null) {
            throw ProActionException.invalid("Choisis un lieu existant ou saisis une adresse, pas les deux.");
        }
        if (input.placeId() != null) {
            if (!presenceRepository.isPublishedPlace(input.placeId())) {
                throw ProActionException.invalid("Ce lieu n'existe pas ou n'est pas publié.");
            }
            return input.placeId();
        }
        if (input.newSpot() == null) {
            if (currentLocationId == null) {
                throw ProActionException.invalid("Indique l'emplacement : un lieu existant ou une adresse.");
            }
            return currentLocationId;
        }
        SpotInput spot = input.newSpot();
        String name = text(spot.name(), MAX_SPOT_TEXT_LENGTH, "Le nom de l'emplacement");
        String address = text(spot.address(), MAX_ADDRESS_LENGTH, "L'adresse");
        String postalCode = text(spot.postalCode(), 20, "Le code postal");
        String city = text(spot.city(), MAX_SPOT_TEXT_LENGTH, "La commune");
        if (city == null) {
            throw ProActionException.invalid("La commune de l'emplacement est obligatoire.");
        }
        Double latitude = spot.latitude();
        Double longitude = spot.longitude();
        if (latitude == null || longitude == null) {
            if (address == null) {
                throw ProActionException.invalid("Indique l'adresse de l'emplacement ou sa position.");
            }
            Coordinates coordinates = locate(address, postalCode, city);
            latitude = coordinates.latitude();
            longitude = coordinates.longitude();
        }
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw ProActionException.invalid("Position hors limites.");
        }
        return presenceRepository.insertSpot(name, address, postalCode, city, latitude, longitude);
    }

    private Coordinates locate(String address, String postalCode, String city) {
        try {
            return geocodingService.geocode(Stream.of(address, postalCode, city)
                    .filter(part -> part != null && !part.isBlank()).collect(Collectors.joining(" ")));
        } catch (GeocodingUnavailableException exception) {
            throw ProActionException.unavailable("Service de localisation indisponible : indique la position.");
        } catch (GeocodingException exception) {
            throw ProActionException.invalid("Adresse introuvable : vérifie-la ou indique la position.");
        }
    }

    private static ProfileFields validate(ProfileInput input) {
        if (input == null) {
            throw ProActionException.invalid("Corps de requête manquant.");
        }
        String name = text(input.name(), MAX_NAME_LENGTH, "Le nom");
        String category = text(input.category(), MAX_CATEGORY_LENGTH, "La cuisine");
        if (name == null || category == null) {
            throw ProActionException.invalid("Le nom et la cuisine sont obligatoires.");
        }
        return new ProfileFields(name, text(input.description(), MAX_DESCRIPTION_LENGTH, "La description"), category,
                text(input.contact(), MAX_CONTACT_LENGTH, "Le contact"));
    }

    private static LocalTime time(String value, String label) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(value.trim());
        } catch (DateTimeParseException exception) {
            throw ProActionException.invalid(label + " doit être au format HH:mm.");
        }
    }

    private static LocalDate date(String value, String label) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException exception) {
            throw ProActionException.invalid(label + " doit être au format yyyy-MM-dd.");
        }
    }

    private static String text(String value, int maxLength, String label) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw ProActionException.invalid(label + " ne doit pas dépasser " + maxLength + " caractères.");
        }
        return trimmed;
    }

    private void requirePro(Long userId) {
        if (!proAccessPolicy.hasProAccess(userId)) {
            throw ProActionException.forbidden("Compte professionnel requis.");
        }
    }
}
