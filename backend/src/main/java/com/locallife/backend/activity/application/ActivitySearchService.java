package com.locallife.backend.activity.application;

import com.locallife.backend.activity.infrastructure.ActivitySearchRepository;
import com.locallife.backend.activity.infrastructure.ActivitySearchRepository.SearchPage;
import com.locallife.backend.activity.infrastructure.ActivitySearchRepository.SearchQuery;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Recherche textuelle globale (#91) : validation des paramètres, puis requête
 * {@link ActivitySearchRepository}. Contrat : {@code docs/02_Architecture/GLOBAL_SEARCH_CONTRACT.md}.
 *
 * <p>Paramètres reçus en texte et validés ici, comme {@link ActivityService#findNearby} : chaque erreur
 * lève une {@link IllegalArgumentException} au message destiné à l'appelant, traduite en 400 au format
 * {@code ErrorResponse} par le contrôleur. La position de référence ne sert qu'au tri : elle n'est
 * jamais enregistrée.
 */
@Service
public class ActivitySearchService {

    static final int MIN_QUERY_LENGTH = 2;
    static final int MAX_QUERY_LENGTH = 120;
    static final int DEFAULT_LIMIT = 20;
    static final int MAX_LIMIT = 100;

    /**
     * Mots vides ignorés pour la correspondance « tous les termes » (déjà normalisés : minuscules, sans
     * accents). Si la requête n'est faite que de mots vides, tous ses mots sont utilisés.
     */
    static final List<String> STOPWORDS = List.of(
            "a", "au", "aux", "d", "de", "des", "du", "en", "et", "l", "la", "le", "les", "ou", "un", "une",
            "the", "of", "and");

    private static final Pattern SEARCHABLE = Pattern.compile("[\\p{L}\\p{N}]");

    private final ActivitySearchRepository searchRepository;

    public ActivitySearchService(ActivitySearchRepository searchRepository) {
        this.searchRepository = searchRepository;
    }

    /** Page de résultats, avec la pagination effectivement appliquée (valeurs par défaut comprises). */
    public record SearchResult(SearchPage page, int limit, int offset) {
    }

    /**
     * @throws IllegalArgumentException si un paramètre est invalide (voir le contrat, section Erreurs)
     */
    public SearchResult search(
            String qRaw, String latitudeRaw, String longitudeRaw, String category, String dateRaw, String dateToRaw,
            String limitRaw, String offsetRaw) {
        String text = validateText(qRaw);

        boolean hasLatitude = latitudeRaw != null && !latitudeRaw.isBlank();
        boolean hasLongitude = longitudeRaw != null && !longitudeRaw.isBlank();
        if (hasLatitude != hasLongitude) {
            throw new IllegalArgumentException(
                    "Les paramètres 'latitude' et 'longitude' doivent être fournis ensemble.");
        }
        Double latitude = hasLatitude ? parseDouble("latitude", latitudeRaw) : null;
        Double longitude = hasLongitude ? parseDouble("longitude", longitudeRaw) : null;
        if (latitude != null && (latitude < -90 || latitude > 90)) {
            throw new IllegalArgumentException("Le paramètre 'latitude' doit être compris entre -90 et 90.");
        }
        if (longitude != null && (longitude < -180 || longitude > 180)) {
            throw new IllegalArgumentException("Le paramètre 'longitude' doit être compris entre -180 et 180.");
        }

        LocalDate date = parseDate("date", dateRaw);
        LocalDate dateTo = parseDate("dateTo", dateToRaw);
        if (dateTo != null && date == null) {
            throw new IllegalArgumentException(
                    "Le paramètre 'dateTo' ne peut être fourni sans 'date' (borne de début de la période).");
        }
        if (dateTo != null && dateTo.isBefore(date)) {
            throw new IllegalArgumentException("Le paramètre 'dateTo' doit être postérieur ou égal à 'date'.");
        }

        int limit = parseInt("limit", limitRaw, DEFAULT_LIMIT);
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("Le paramètre 'limit' doit être compris entre 1 et " + MAX_LIMIT + ".");
        }
        int offset = parseInt("offset", offsetRaw, 0);
        if (offset < 0) {
            throw new IllegalArgumentException("Le paramètre 'offset' doit être positif ou nul.");
        }

        SearchPage page = searchRepository.search(new SearchQuery(
                text, STOPWORDS, latitude, longitude, normalizeCategories(category), date, dateTo, limit, offset));
        return new SearchResult(page, limit, offset);
    }

    private static String validateText(String qRaw) {
        String text = qRaw == null ? "" : qRaw.trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Le paramètre 'q' est obligatoire.");
        }
        if (text.length() < MIN_QUERY_LENGTH || text.length() > MAX_QUERY_LENGTH) {
            throw new IllegalArgumentException(
                    "Le paramètre 'q' doit contenir entre " + MIN_QUERY_LENGTH + " et " + MAX_QUERY_LENGTH
                            + " caractères.");
        }
        if (!SEARCHABLE.matcher(text).find()) {
            throw new IllegalArgumentException("Le paramètre 'q' doit contenir au moins une lettre ou un chiffre.");
        }
        return text;
    }

    private static String normalizeCategories(String categoryRaw) {
        if (categoryRaw == null) {
            return null;
        }
        String cleaned = Arrays.stream(categoryRaw.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.joining(","));
        return cleaned.isEmpty() ? null : cleaned;
    }

    private static double parseDouble(String name, String raw) {
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Le paramètre '" + name + "' doit être un nombre.");
        }
    }

    private static int parseInt(String name, String raw, int defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Le paramètre '" + name + "' doit être un nombre entier.");
        }
    }

    private static LocalDate parseDate(String name, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    "Le paramètre '" + name + "' doit être au format ISO-8601 (yyyy-MM-dd).");
        }
    }
}
