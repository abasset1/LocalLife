package com.locallife.backend.alert.application;

import com.locallife.backend.alert.domain.ActivityAlert;
import com.locallife.backend.alert.infrastructure.ActivityAlertRepository;
import com.locallife.backend.category.domain.Category;
import com.locallife.backend.category.infrastructure.CategoryRepository;
import com.locallife.backend.geocoding.application.Coordinates;
import com.locallife.backend.geocoding.application.GeocodingService;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alertes email de l'utilisateur connecté : création, consultation,
 * suppression, et désinscription par le lien de l'email. L'envoi lui-même
 * est fait par {@link ActivityAlertNotifier}.
 */
@Service
public class ActivityAlertService {

    /** Même borne que la recherche {@code /nearby}. */
    static final int MAX_RADIUS_KM = 50;

    /** Garde-fou : chaque alerte coûte une requête PostGIS à chaque envoi planifié. */
    static final int MAX_ALERTS_PER_USER = 10;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ActivityAlertRepository activityAlertRepository;
    private final CategoryRepository categoryRepository;
    private final GeocodingService geocodingService;

    public ActivityAlertService(
            ActivityAlertRepository activityAlertRepository, CategoryRepository categoryRepository,
            GeocodingService geocodingService) {
        this.activityAlertRepository = activityAlertRepository;
        this.categoryRepository = categoryRepository;
        this.geocodingService = geocodingService;
    }

    public List<ActivityAlert> getAlerts(Long userId) {
        return activityAlertRepository.findByUserId(userId);
    }

    /**
     * Crée une alerte. La ville est choisie par position (géolocalisation,
     * prioritaire) ou par nom, et doit exister — même règle que les
     * paramètres utilisateur ({@link GeocodingService#resolveCity}).
     *
     * @throws IllegalArgumentException si aucune catégorie, une catégorie inconnue, un rayon hors de
     *         [1, 50], aucune ville, ou le nombre maximal d'alertes est atteint
     * @throws com.locallife.backend.geocoding.application.CityNotFoundException si la ville n'existe pas
     * @throws com.locallife.backend.geocoding.application.GeocodingUnavailableException si le géocodage échoue
     */
    @Transactional
    public ActivityAlert createAlert(
            Long userId, List<String> categoryCodes, String city, Double latitude, Double longitude,
            Integer radiusKm) {
        List<String> codes = validateCategoryCodes(categoryCodes);
        if (radiusKm == null || radiusKm < 1 || radiusKm > MAX_RADIUS_KM) {
            throw new IllegalArgumentException("Le rayon doit être compris entre 1 et " + MAX_RADIUS_KM + " km.");
        }
        boolean hasCity = city != null && !city.isBlank();
        if (!hasCity && latitude == null && longitude == null) {
            throw new IllegalArgumentException("Choisis une ville pour ton alerte.");
        }
        if (activityAlertRepository.countByUserId(userId) >= MAX_ALERTS_PER_USER) {
            throw new IllegalArgumentException(
                    "Tu as atteint le nombre maximal d'alertes (" + MAX_ALERTS_PER_USER + ").");
        }

        Coordinates found = geocodingService.resolveCity(hasCity ? city.trim() : null, latitude, longitude);
        ActivityAlert alert = new ActivityAlert(
                null, userId, found.city(), found.latitude(), found.longitude(), radiusKm, codes, null);
        return activityAlertRepository.create(alert, newUnsubscribeToken());
    }

    /** @throws NoSuchElementException si l'alerte n'existe pas ou n'appartient pas à l'utilisateur */
    public void deleteAlert(Long userId, Long alertId) {
        if (!activityAlertRepository.deleteByIdAndUserId(alertId, userId)) {
            throw new NoSuchElementException("Alerte introuvable.");
        }
    }

    /** @return {@code false} si le lien a déjà servi ou n'est pas valide */
    public boolean unsubscribe(String token) {
        return token != null && !token.isBlank() && activityAlertRepository.deleteByUnsubscribeToken(token.trim());
    }

    private List<String> validateCategoryCodes(List<String> requestedCodes) {
        Set<String> codes = requestedCodes == null ? Set.of() : requestedCodes.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(code -> !code.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (codes.isEmpty()) {
            throw new IllegalArgumentException("Choisis au moins une catégorie.");
        }
        Set<String> activeCodes = categoryRepository.findAll().stream()
                .filter(Category::active)
                .map(Category::code)
                .collect(Collectors.toSet());
        for (String code : codes) {
            if (!activeCodes.contains(code)) {
                throw new IllegalArgumentException("Catégorie inconnue : \"" + code + "\".");
            }
        }
        return codes.stream().sorted().toList();
    }

    private static String newUnsubscribeToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

}
