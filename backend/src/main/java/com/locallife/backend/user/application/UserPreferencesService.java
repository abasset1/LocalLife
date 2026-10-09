package com.locallife.backend.user.application;

import com.locallife.backend.category.domain.Category;
import com.locallife.backend.category.infrastructure.CategoryRepository;
import com.locallife.backend.geocoding.application.CityNotFoundException;
import com.locallife.backend.geocoding.application.Coordinates;
import com.locallife.backend.geocoding.application.GeocodingService;
import com.locallife.backend.user.domain.UserPreferences;
import com.locallife.backend.user.infrastructure.UserPreferencesRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Paramètres utilisateur : catégories, ville et rayon de recherche par
 * défaut de l'Explorer. {@code userId} est toujours fourni par l'appelant
 * (résolu depuis le JWT, voir {@code UserPreferencesController}), jamais
 * lu dans la requête.
 */
@Service
public class UserPreferencesService {

    /** Même borne que la recherche {@code /nearby} (voir {@code ActivityService#findNearby}). */
    static final int MAX_RADIUS_KM = 50;

    private final UserPreferencesRepository userPreferencesRepository;
    private final CategoryRepository categoryRepository;
    private final GeocodingService geocodingService;

    public UserPreferencesService(
            UserPreferencesRepository userPreferencesRepository, CategoryRepository categoryRepository,
            GeocodingService geocodingService) {
        this.userPreferencesRepository = userPreferencesRepository;
        this.categoryRepository = categoryRepository;
        this.geocodingService = geocodingService;
    }

    /** Préférences de l'utilisateur, vides s'il n'en a jamais enregistré. */
    public UserPreferences getPreferences(Long userId) {
        return userPreferencesRepository.findByUserId(userId).orElseGet(() -> UserPreferences.empty(userId));
    }

    /**
     * Ville correspondant à un nom saisi ou à une position (géolocalisation),
     * pour que le formulaire affiche la ville retenue avant l'enregistrement.
     * La position est prioritaire quand elle est fournie.
     *
     * @throws IllegalArgumentException si ni nom ni position complète ne sont fournis
     * @throws CityNotFoundException si aucune ville ne correspond
     * @throws com.locallife.backend.geocoding.application.GeocodingUnavailableException si le géocodage échoue
     */
    public Coordinates lookupCity(String name, Double latitude, Double longitude) {
        return geocodingService.resolveCity(name, latitude, longitude);
    }

    /**
     * Remplace les préférences de l'utilisateur. La ville par défaut est
     * choisie soit par une position ({@code latitude}/{@code longitude},
     * géolocalisation du navigateur — prioritaire), soit par son nom ; dans
     * les deux cas elle doit correspondre à une ville existante, dont le nom
     * officiel et les coordonnées sont enregistrés. Une ville identique à
     * celle déjà enregistrée garde ses coordonnées (aucun appel réseau). Ni
     * nom ni position : la ville par défaut est effacée.
     *
     * @throws IllegalArgumentException si le rayon est hors de [1, 50], si une catégorie est inconnue
     *         ou si la position est incomplète/hors plage
     * @throws CityNotFoundException si la ville n'existe pas
     * @throws com.locallife.backend.geocoding.application.GeocodingUnavailableException si le géocodage échoue
     */
    @Transactional
    public UserPreferences updatePreferences(
            Long userId, String defaultCity, Double defaultLatitude, Double defaultLongitude,
            Integer defaultRadiusKm, List<String> defaultCategoryCodes) {
        if (defaultRadiusKm != null && (defaultRadiusKm < 1 || defaultRadiusKm > MAX_RADIUS_KM)) {
            throw new IllegalArgumentException(
                    "Le rayon par défaut doit être compris entre 1 et " + MAX_RADIUS_KM + " km.");
        }
        List<String> categoryCodes = validateCategoryCodes(defaultCategoryCodes);

        String name = defaultCity == null || defaultCity.isBlank() ? null : defaultCity.trim();
        boolean hasPosition = defaultLatitude != null || defaultLongitude != null;
        String city = null;
        Double latitude = null;
        Double longitude = null;
        if (hasPosition) {
            Coordinates found = lookupCity(null, defaultLatitude, defaultLongitude);
            city = found.city();
            latitude = found.latitude();
            longitude = found.longitude();
        } else if (name != null) {
            UserPreferences existing = getPreferences(userId);
            if (name.equalsIgnoreCase(existing.defaultCity()) && existing.defaultLatitude() != null) {
                city = existing.defaultCity();
                latitude = existing.defaultLatitude();
                longitude = existing.defaultLongitude();
            } else {
                Coordinates found = geocodingService.findCity(name);
                city = found.city();
                latitude = found.latitude();
                longitude = found.longitude();
            }
        }

        UserPreferences updated = new UserPreferences(
                userId, city, latitude, longitude, defaultRadiusKm, categoryCodes);
        userPreferencesRepository.save(updated);
        return updated;
    }

    /** Codes nettoyés (blancs et doublons retirés, triés), chacun devant être une catégorie active. */
    private List<String> validateCategoryCodes(List<String> requestedCodes) {
        if (requestedCodes == null || requestedCodes.isEmpty()) {
            return List.of();
        }
        Set<String> activeCodes = categoryRepository.findAll().stream()
                .filter(Category::active)
                .map(Category::code)
                .collect(Collectors.toSet());
        Set<String> codes = requestedCodes.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(code -> !code.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        for (String code : codes) {
            if (!activeCodes.contains(code)) {
                throw new IllegalArgumentException("Catégorie inconnue : \"" + code + "\".");
            }
        }
        return codes.stream().sorted().toList();
    }

}
