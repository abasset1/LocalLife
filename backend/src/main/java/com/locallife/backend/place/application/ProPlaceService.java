package com.locallife.backend.place.application;

import com.locallife.backend.geocoding.application.Coordinates;
import com.locallife.backend.geocoding.application.GeocodingException;
import com.locallife.backend.geocoding.application.GeocodingService;
import com.locallife.backend.geocoding.application.GeocodingUnavailableException;
import com.locallife.backend.place.domain.PlaceStatus;
import com.locallife.backend.place.domain.PlaceTypeWhitelist;
import com.locallife.backend.place.infrastructure.PlaceManagerRepository;
import com.locallife.backend.place.infrastructure.ProPlaceRepository;
import com.locallife.backend.place.infrastructure.ProPlaceRepository.ManagedPlace;
import com.locallife.backend.place.infrastructure.ProPlaceRepository.ProPlaceFields;
import com.locallife.backend.place.infrastructure.ProPlaceRepository.SimilarPlace;
import com.locallife.backend.pro.application.ProAccessPolicy;
import com.locallife.backend.pro.application.ProActionException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lieux créés et gérés par un professionnel (ADR-0012, parcours B). Recherche préalable des lieux proches, création
 * en brouillon, aperçu, soumission à la modération existante ({@code PlaceModerationService}). Une création ne
 * réutilise ni ne modifie jamais un lieu existant. Un lieu publié (importé ou non) se modifie par une proposition
 * validée par l'équipe ({@code ManagedPlaceService}, décision D5 du 22/10/2026).
 */
@Service
public class ProPlaceService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProPlaceService.class);

    /** Rayon de la recherche de lieux proches : celui du rapprochement des lieux ({@code PlaceMatcher}). */
    static final double SIMILAR_RADIUS_METERS = 100;
    static final int MAX_SIMILAR = 20;

    private static final int MAX_NAME_LENGTH = 255;
    private static final int MAX_ADDRESS_LENGTH = 512;
    private static final int MAX_CITY_LENGTH = 255;
    private static final int MAX_DESCRIPTION_LENGTH = 5000;
    private static final int MAX_WEBSITE_LENGTH = 512;
    private static final int MAX_EMAIL_LENGTH = 255;
    private static final int MAX_PHONE_LENGTH = 50;
    private static final Pattern POSTAL_CODE = Pattern.compile("^\\d{5}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern PHONE = Pattern.compile("^[0-9+().\\s-]{6,50}$");

    /** Types proposés à la création : ceux de la couche « Lieux » de la carte. */
    public static final List<String> PLACE_TYPES = PlaceTypeWhitelist.SPECIFIC_TYPES;

    private final ProPlaceRepository proPlaceRepository;
    private final PlaceManagerRepository managerRepository;
    private final ProAccessPolicy proAccessPolicy;
    private final GeocodingService geocodingService;

    public ProPlaceService(ProPlaceRepository proPlaceRepository, PlaceManagerRepository managerRepository,
            ProAccessPolicy proAccessPolicy, GeocodingService geocodingService) {
        this.proPlaceRepository = proPlaceRepository;
        this.managerRepository = managerRepository;
        this.proAccessPolicy = proAccessPolicy;
        this.geocodingService = geocodingService;
    }

    /** Lieu saisi par le professionnel ; position facultative, déduite de l'adresse si absente. */
    public record PlaceInput(
            String name, String placeType, String address, String postalCode, String city, Double latitude,
            Double longitude, String description, String website, String phone, String email) {
    }

    /** Position retenue (ou {@code null} si l'adresse n'a pas pu être localisée) et lieux proches. */
    public record SimilarResult(Double latitude, Double longitude, List<SimilarPlace> candidates) {
    }

    /** Création refusée parce que des lieux proches existent et que le professionnel ne les a pas écartés. */
    public static class SimilarPlacesFoundException extends ProActionException {

        private final transient List<SimilarPlace> candidates;

        SimilarPlacesFoundException(List<SimilarPlace> candidates) {
            super(Reason.CONFLICT, "Des lieux proches existent déjà : vérifie qu'il ne s'agit pas du tien.");
            this.candidates = candidates;
        }

        public List<SimilarPlace> candidates() {
            return candidates;
        }
    }

    /** Recherche préalable : lieux existants proches ou de même nom dans la commune. */
    public SimilarResult findSimilar(Long userId, String name, String address, String postalCode, String city,
            Double latitude, Double longitude) {
        requirePro(userId);
        String cleanName = clean(name);
        String cleanCity = clean(city);
        if (cleanName == null && cleanCity == null && (latitude == null || longitude == null)) {
            throw ProActionException.invalid("Indique au moins le nom et la commune du lieu.");
        }
        Double[] position = {latitude, longitude};
        if (latitude == null || longitude == null) {
            position = locateQuietly(address, postalCode, cleanCity);
        }
        return new SimilarResult(position[0], position[1], proPlaceRepository.findSimilar(
                cleanName, position[0], position[1], cleanCity, SIMILAR_RADIUS_METERS, MAX_SIMILAR));
    }

    /**
     * Crée un lieu en brouillon, géré par son créateur. Sans {@code confirmNotDuplicate}, la création est refusée
     * quand des lieux proches existent ({@link SimilarPlacesFoundException}). {@code reportedSimilarTo} signale au
     * modérateur un lieu existant proche du nouveau.
     */
    @Transactional
    public ManagedPlace create(Long userId, PlaceInput input, boolean confirmNotDuplicate, Long reportedSimilarTo) {
        requirePro(userId);
        ProPlaceFields fields = validate(input);
        if (reportedSimilarTo != null && proPlaceRepository.findPlace(reportedSimilarTo)
                .filter(found -> found.place().status() != PlaceStatus.DRAFT).isEmpty()) {
            throw ProActionException.invalid("Le lieu signalé comme similaire n'existe pas.");
        }
        if (!confirmNotDuplicate) {
            List<SimilarPlace> candidates = proPlaceRepository.findSimilar(fields.name(), fields.latitude(),
                    fields.longitude(), fields.city(), SIMILAR_RADIUS_METERS, MAX_SIMILAR);
            if (!candidates.isEmpty()) {
                throw new SimilarPlacesFoundException(candidates);
            }
        }
        Long id = proPlaceRepository.insertDraft(fields, userId, reportedSimilarTo);
        managerRepository.grant(id, userId, null, null);
        LOGGER.info("Lieu {} créé en brouillon par le professionnel {}", id, userId);
        return proPlaceRepository.findPlace(id).orElseThrow();
    }

    /** Modifie un lieu créé par un professionnel, tant qu'il est en brouillon ou en attente de modération. */
    @Transactional
    public ManagedPlace update(Long userId, Long id, PlaceInput input) {
        ManagedPlace managed = managedPlace(userId, id);
        if (managed.imported()) {
            throw ProActionException.conflict("Une fiche importée se modifie par une proposition validée par "
                    + "l'équipe.");
        }
        PlaceStatus status = managed.place().status();
        if (status != PlaceStatus.DRAFT && status != PlaceStatus.PENDING) {
            throw ProActionException.conflict("Ce lieu n'est plus modifiable directement (statut " + status
                    + ") : propose une modification.");
        }
        proPlaceRepository.update(id, validate(input));
        return proPlaceRepository.findPlace(id).orElseThrow();
    }

    /** Envoie un brouillon à la modération. */
    @Transactional
    public ManagedPlace submit(Long userId, Long id) {
        managedPlace(userId, id);
        if (!proPlaceRepository.submit(id)) {
            throw ProActionException.conflict("Seul un brouillon peut être envoyé à la modération.");
        }
        return proPlaceRepository.findPlace(id).orElseThrow();
    }

    public List<ManagedPlace> findManaged(Long userId) {
        requirePro(userId);
        return proPlaceRepository.findManagedBy(userId);
    }

    /** Aperçu d'un lieu géré, quel que soit son statut. */
    public ManagedPlace findManaged(Long userId, Long id) {
        return managedPlace(userId, id);
    }

    // ---------------------------------------------------------------- outils

    /** Lieu géré par ce professionnel : 403 sans accès professionnel, 404 s'il ne le gère pas. */
    ManagedPlace managedPlace(Long userId, Long id) {
        requirePro(userId);
        if (!managerRepository.isManager(id, userId)) {
            throw ProActionException.notFound("Lieu introuvable.");
        }
        return proPlaceRepository.findPlace(id).orElseThrow(() -> ProActionException.notFound("Lieu introuvable."));
    }

    /** Contrôle et complète (position) une saisie de lieu ; aussi utilisé pour les propositions de modification. */
    ProPlaceFields validate(PlaceInput input) {
        if (input == null) {
            throw ProActionException.invalid("Corps de requête manquant.");
        }
        String name = required(input.name(), MAX_NAME_LENGTH, "Le nom");
        String placeType = clean(input.placeType());
        if (placeType == null || !PLACE_TYPES.contains(placeType)) {
            throw ProActionException.invalid("Catégorie de lieu inconnue.");
        }
        String address = optional(input.address(), MAX_ADDRESS_LENGTH, "L'adresse");
        String city = required(input.city(), MAX_CITY_LENGTH, "La commune");
        String postalCode = clean(input.postalCode());
        if (postalCode != null && !POSTAL_CODE.matcher(postalCode).matches()) {
            throw ProActionException.invalid("Le code postal doit comporter 5 chiffres.");
        }
        String website = optional(input.website(), MAX_WEBSITE_LENGTH, "Le site");
        if (website != null && !website.toLowerCase(Locale.ROOT).matches("^https?://\\S+$")) {
            throw ProActionException.invalid("Le site doit commencer par http:// ou https://.");
        }
        String email = optional(input.email(), MAX_EMAIL_LENGTH, "L'email");
        if (email != null && !EMAIL.matcher(email).matches()) {
            throw ProActionException.invalid("L'email n'est pas valide.");
        }
        String phone = optional(input.phone(), MAX_PHONE_LENGTH, "Le téléphone");
        if (phone != null && !PHONE.matcher(phone).matches()) {
            throw ProActionException.invalid("Le téléphone n'est pas valide.");
        }
        Double latitude = input.latitude();
        Double longitude = input.longitude();
        if (latitude == null || longitude == null) {
            if (address == null) {
                throw ProActionException.invalid("Indique l'adresse du lieu ou sa position sur la carte.");
            }
            Coordinates coordinates = locate(address, postalCode, city);
            latitude = coordinates.latitude();
            longitude = coordinates.longitude();
        }
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw ProActionException.invalid("Position hors limites.");
        }
        return new ProPlaceFields(name, placeType, address, postalCode, city, latitude, longitude,
                optional(input.description(), MAX_DESCRIPTION_LENGTH, "La description"), website, phone, email);
    }

    private Coordinates locate(String address, String postalCode, String city) {
        try {
            return geocodingService.geocode(fullAddress(address, postalCode, city));
        } catch (GeocodingUnavailableException exception) {
            throw ProActionException.unavailable("Service de localisation indisponible : place le lieu sur la carte.");
        } catch (GeocodingException exception) {
            throw ProActionException.invalid("Adresse introuvable : vérifie-la ou place le lieu sur la carte.");
        }
    }

    /** Position de l'adresse, ou {@code null} : la recherche préalable se contente alors du nom et de la commune. */
    private Double[] locateQuietly(String address, String postalCode, String city) {
        if (clean(address) == null || city == null) {
            return new Double[] {null, null};
        }
        try {
            Coordinates coordinates = geocodingService.geocode(fullAddress(clean(address), clean(postalCode), city));
            return new Double[] {coordinates.latitude(), coordinates.longitude()};
        } catch (GeocodingException | IllegalArgumentException exception) {
            return new Double[] {null, null};
        }
    }

    private static String fullAddress(String address, String postalCode, String city) {
        return Stream.of(address, postalCode, city).filter(part -> part != null && !part.isBlank())
                .collect(Collectors.joining(" "));
    }

    private void requirePro(Long userId) {
        if (!proAccessPolicy.hasProAccess(userId)) {
            throw ProActionException.forbidden("Compte professionnel requis.");
        }
    }

    private static String required(String value, int maxLength, String label) {
        String cleaned = optional(value, maxLength, label);
        if (cleaned == null) {
            throw ProActionException.invalid(label + " est obligatoire.");
        }
        return cleaned;
    }

    private static String optional(String value, int maxLength, String label) {
        return PlaceClaimService.text(value, maxLength, label);
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
