package com.locallife.backend.geocoding.application;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Suggestions de communes pendant la saisie dans la barre de recherche de la carte.
 *
 * <p>Interroge l'API Découpage administratif de l'État ({@code geo.api.gouv.fr}), faite pour
 * l'autocomplétion : gratuite, sans clé, avec les codes postaux et le centre de chaque commune, les
 * plus peuplées d'abord. Nominatim, utilisé ailleurs, interdit expressément l'autocomplétion dans sa
 * politique d'usage. Couverture : communes françaises.
 *
 * <p>Une saisie de 5 chiffres est cherchée comme code postal, toute autre comme début ou partie de
 * nom. Les dernières réponses sont gardées en mémoire : la même saisie revient souvent (frappe,
 * effacement, plusieurs visiteurs).
 */
@Service
public class CitySuggestionService {

    static final int MIN_QUERY_LENGTH = 2;
    static final int MAX_SUGGESTIONS = 8;

    private static final String GEO_API_BASE_URL = "https://geo.api.gouv.fr";
    private static final Pattern POSTAL_CODE = Pattern.compile("^\\d{5}$");
    private static final int CACHE_SIZE = 1_000;

    private final RestClient restClient;
    private final Map<String, List<CitySuggestion>> cache = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, List<CitySuggestion>> eldest) {
                    return size() > CACHE_SIZE;
                }
            });

    @Autowired
    public CitySuggestionService() {
        this(RestClient.builder());
    }

    /** Réservé aux tests : {@link RestClient.Builder} lié à un {@code MockRestServiceServer}. */
    CitySuggestionService(RestClient.Builder builder) {
        this.restClient = builder
                .baseUrl(GEO_API_BASE_URL)
                .defaultHeader("User-Agent", "LocalLife/0.1 (contact: dev@locallife.local)")
                .build();
    }

    /**
     * Commune proposée.
     *
     * @param name        nom officiel
     * @param postalCodes codes postaux, triés (plusieurs pour une grande ville : Lyon en a 9)
     * @param department  code du département
     * @param latitude    centre de la commune
     * @param longitude   centre de la commune
     */
    public record CitySuggestion(
            String name, List<String> postalCodes, String department, double latitude, double longitude) {
    }

    /**
     * @return au plus {@value #MAX_SUGGESTIONS} communes, liste vide si la saisie fait moins de
     *     {@value #MIN_QUERY_LENGTH} caractères ou si rien ne correspond
     * @throws GeocodingUnavailableException si l'API est injoignable
     */
    public List<CitySuggestion> suggest(String query) {
        String trimmed = query == null ? "" : query.trim();
        if (trimmed.length() < MIN_QUERY_LENGTH) {
            return List.of();
        }
        String key = trimmed.toLowerCase(Locale.ROOT);
        List<CitySuggestion> cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        List<CitySuggestion> suggestions = fetch(trimmed);
        cache.put(key, suggestions);
        return suggestions;
    }

    private List<CitySuggestion> fetch(String query) {
        boolean byPostalCode = POSTAL_CODE.matcher(query).matches();
        List<GeoApiCommune> communes;
        try {
            communes = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/communes")
                            .queryParam(byPostalCode ? "codePostal" : "nom", query)
                            .queryParam("fields", "nom,codesPostaux,codeDepartement,centre")
                            .queryParam("boost", "population")
                            .queryParam("limit", MAX_SUGGESTIONS)
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<GeoApiCommune>>() { });
        } catch (RestClientException exception) {
            throw new GeocodingUnavailableException(exception);
        }
        if (communes == null) {
            return List.of();
        }
        return communes.stream()
                .filter(commune -> commune.nom() != null && commune.centre() != null
                        && commune.centre().coordinates() != null && commune.centre().coordinates().size() == 2)
                .limit(MAX_SUGGESTIONS)
                .map(commune -> new CitySuggestion(
                        commune.nom(),
                        commune.codesPostaux() == null ? List.of() : commune.codesPostaux().stream().sorted().toList(),
                        commune.codeDepartement(),
                        commune.centre().coordinates().get(1),
                        commune.centre().coordinates().get(0)))
                .toList();
    }

    /** Commune telle que renvoyée par {@code geo.api.gouv.fr} (champs demandés seulement). */
    private record GeoApiCommune(String nom, List<String> codesPostaux, String codeDepartement, GeoPoint centre) {
    }

    /** Point GeoJSON : {@code coordinates} = [longitude, latitude]. */
    private record GeoPoint(List<Double> coordinates) {
    }
}
