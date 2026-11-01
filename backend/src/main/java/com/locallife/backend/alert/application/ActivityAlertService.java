package com.locallife.backend.alert.application;

import com.locallife.backend.alert.domain.ActivityAlert;
import com.locallife.backend.alert.infrastructure.ActivityAlertRepository;
import com.locallife.backend.category.domain.Category;
import com.locallife.backend.category.infrastructure.CategoryRepository;
import com.locallife.backend.geocoding.application.Coordinates;
import com.locallife.backend.geocoding.application.GeocodingService;
import com.locallife.backend.subscription.application.PlanLimits;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    /** Mots-clés par alerte (V80). */
    static final int MAX_KEYWORDS = 10;

    /** Longueur d'un mot-clé ou d'une expression, espaces superflus retirés. */
    static final int MIN_KEYWORD_LENGTH = 2;
    static final int MAX_KEYWORD_LENGTH = 60;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ActivityAlertRepository activityAlertRepository;
    private final CategoryRepository categoryRepository;
    private final GeocodingService geocodingService;
    private final PlanLimits planLimits;

    public ActivityAlertService(
            ActivityAlertRepository activityAlertRepository, CategoryRepository categoryRepository,
            GeocodingService geocodingService, PlanLimits planLimits) {
        this.activityAlertRepository = activityAlertRepository;
        this.categoryRepository = categoryRepository;
        this.geocodingService = geocodingService;
        this.planLimits = planLimits;
    }

    public List<ActivityAlert> getAlerts(Long userId) {
        return activityAlertRepository.findByUserId(userId);
    }

    /**
     * Crée une alerte. La ville est choisie par position (géolocalisation,
     * prioritaire) ou par nom, et doit exister — même règle que les
     * paramètres utilisateur ({@link GeocodingService#resolveCity}).
     *
     * Canaux : e-mail seul si {@code notifyEmail}/{@code notifyPush} ne sont
     * pas précisés (site web) ; au moins un des deux doit être actif.
     *
     * {@code includeFoodTrucks} : l'alerte couvre aussi les nouveaux food trucks ; elle peut alors
     * n'avoir aucune catégorie.
     *
     * {@code keywords} (Premium et Pro) : mots-clés ou expressions, voir {@link #normalizeKeywords} ; avec eux,
     * l'alerte peut aussi n'avoir aucune catégorie (toutes les catégories sont alors couvertes).
     *
     * @throws IllegalArgumentException si ni catégorie, ni mot-clé, ni food trucks, une catégorie inconnue, un
     *         mot-clé invalide, un rayon hors de [1, 50], aucune ville ou aucun canal
     * @throws com.locallife.backend.subscription.application.PlanLimitException nombre maximal d'alertes atteint,
     *         ou mots-clés demandés par un compte gratuit
     * @throws com.locallife.backend.geocoding.application.CityNotFoundException si la ville n'existe pas
     * @throws com.locallife.backend.geocoding.application.GeocodingUnavailableException si le géocodage échoue
     */
    @Transactional
    public ActivityAlert createAlert(
            Long userId, List<String> categoryCodes, String city, Double latitude, Double longitude,
            Integer radiusKm, Boolean includeFoodTrucks, Boolean notifyEmail, Boolean notifyPush,
            List<String> keywords) {
        // Sans précision, e-mail seul : comportement des alertes créées depuis le site web.
        boolean email = notifyEmail == null || notifyEmail;
        boolean push = notifyPush != null && notifyPush;
        validateChannels(email, push);
        boolean foodTrucks = includeFoodTrucks != null && includeFoodTrucks;
        List<String> cleanKeywords = normalizeKeywords(keywords);
        List<String> codes = validateCategoryCodes(categoryCodes, foodTrucks || !cleanKeywords.isEmpty());
        if (radiusKm == null || radiusKm < 1 || radiusKm > MAX_RADIUS_KM) {
            throw new IllegalArgumentException("Le rayon doit être compris entre 1 et " + MAX_RADIUS_KM + " km.");
        }
        boolean hasCity = city != null && !city.isBlank();
        if (!hasCity && latitude == null && longitude == null) {
            throw new IllegalArgumentException("Choisis une ville pour ton alerte.");
        }
        planLimits.checkCanAddAlert(userId, activityAlertRepository.countByUserId(userId));
        if (!cleanKeywords.isEmpty()) {
            planLimits.checkCanUseAlertKeywords(userId);
        }

        Coordinates found = geocodingService.resolveCity(hasCity ? city.trim() : null, latitude, longitude);
        ActivityAlert alert = new ActivityAlert(
                null, userId, found.city(), found.latitude(), found.longitude(), radiusKm, codes, foodTrucks, email,
                push, null, cleanKeywords);
        return activityAlertRepository.create(alert, newUnsubscribeToken());
    }

    /**
     * Change les canaux d'une alerte (au moins un actif).
     *
     * @throws IllegalArgumentException si aucun canal n'est actif
     * @throws NoSuchElementException si l'alerte n'existe pas ou n'appartient pas à l'utilisateur
     */
    public ActivityAlert updateChannels(Long userId, Long alertId, boolean notifyEmail, boolean notifyPush) {
        validateChannels(notifyEmail, notifyPush);
        if (!activityAlertRepository.updateChannels(alertId, userId, notifyEmail, notifyPush)) {
            throw new NoSuchElementException("Alerte introuvable.");
        }
        return activityAlertRepository.findByUserId(userId).stream()
                .filter(alert -> alert.id().equals(alertId))
                .findFirst()
                .orElseThrow();
    }

    /**
     * Remplace les mots-clés d'une alerte. Ajouter ou changer des mots-clés est réservé à Premium et Pro ; les retirer
     * (liste vide) est toujours permis, tant que l'alerte garde une catégorie ou les food trucks.
     *
     * @throws IllegalArgumentException mot-clé invalide, ou alerte qui n'aurait plus rien à surveiller
     * @throws com.locallife.backend.subscription.application.PlanLimitException compte gratuit
     * @throws NoSuchElementException si l'alerte n'existe pas ou n'appartient pas à l'utilisateur
     */
    @Transactional
    public ActivityAlert updateKeywords(Long userId, Long alertId, List<String> keywords) {
        ActivityAlert alert = findOwned(userId, alertId);
        List<String> cleanKeywords = normalizeKeywords(keywords);
        if (cleanKeywords.equals(alert.keywords())) {
            return alert;
        }
        if (cleanKeywords.isEmpty() && alert.categoryCodes().isEmpty() && !alert.includeFoodTrucks()) {
            throw new IllegalArgumentException("Garde au moins un mot-clé : cette alerte n'a ni catégorie ni food "
                    + "trucks.");
        }
        if (!cleanKeywords.isEmpty()) {
            planLimits.checkCanUseAlertKeywords(userId);
        }
        activityAlertRepository.replaceKeywords(alertId, userId, cleanKeywords);
        return findOwned(userId, alertId);
    }

    private ActivityAlert findOwned(Long userId, Long alertId) {
        return activityAlertRepository.findByUserId(userId).stream()
                .filter(alert -> alert.id().equals(alertId))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Alerte introuvable."));
    }

    /**
     * Mots-clés d'une alerte : espaces superflus retirés, doublons écartés sans tenir compte des majuscules ni des
     * accents, ordre de saisie gardé. Chacun fait de {@value #MIN_KEYWORD_LENGTH} à {@value #MAX_KEYWORD_LENGTH}
     * caractères et contient au moins une lettre ou un chiffre ; {@value #MAX_KEYWORDS} au plus.
     *
     * @throws IllegalArgumentException mot-clé trop court, trop long ou sans lettre, ou trop de mots-clés
     */
    static List<String> normalizeKeywords(List<String> keywords) {
        if (keywords == null) {
            return List.of();
        }
        Map<String, String> unique = new LinkedHashMap<>();
        for (String raw : keywords) {
            if (raw == null) {
                continue;
            }
            String keyword = raw.trim().replaceAll("\\s+", " ");
            if (keyword.isEmpty()) {
                continue;
            }
            if (keyword.length() < MIN_KEYWORD_LENGTH || keyword.length() > MAX_KEYWORD_LENGTH) {
                throw new IllegalArgumentException("Un mot-clé fait de " + MIN_KEYWORD_LENGTH + " à "
                        + MAX_KEYWORD_LENGTH + " caractères : « " + keyword + " ».");
            }
            String comparable = comparable(keyword);
            if (comparable.isEmpty()) {
                throw new IllegalArgumentException("Un mot-clé doit contenir au moins une lettre ou un chiffre : « "
                        + keyword + " ».");
            }
            unique.putIfAbsent(comparable, keyword);
        }
        if (unique.size() > MAX_KEYWORDS) {
            throw new IllegalArgumentException("Une alerte a " + MAX_KEYWORDS + " mots-clés au plus.");
        }
        return List.copyOf(unique.values());
    }

    /** Forme de comparaison : minuscules, sans accents ni ponctuation (proche de {@code ll_search_normalize}). */
    private static String comparable(String keyword) {
        String withoutAccents = Normalizer.normalize(keyword.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace("œ", "oe").replace("æ", "ae").replace("ß", "ss");
        return withoutAccents.replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }

    private static void validateChannels(boolean notifyEmail, boolean notifyPush) {
        if (!notifyEmail && !notifyPush) {
            throw new IllegalArgumentException("Choisis au moins un moyen d'être prévenu : e-mail ou notification.");
        }
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

    /**
     * Catégories : au moins une, sauf si l'alerte couvre les food trucks ou a des mots-clés ({@code optional}) ; elle
     * peut alors n'en avoir aucune.
     */
    private List<String> validateCategoryCodes(List<String> requestedCodes, boolean optional) {
        Set<String> codes = requestedCodes == null ? Set.of() : requestedCodes.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(code -> !code.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (codes.isEmpty()) {
            if (optional) {
                return List.of();
            }
            throw new IllegalArgumentException("Choisis au moins une catégorie, un mot-clé ou les food trucks.");
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
