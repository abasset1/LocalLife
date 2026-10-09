package com.locallife.backend.geocoding.application;

import java.util.List;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Convertit une adresse texte en coordonnées géographiques via l'API de
 * géocodage Nominatim (OpenStreetMap), en accord avec LL-3012.
 *
 * Nominatim est un service public gratuit avec une politique d'usage stricte
 * (1 requête/seconde, User-Agent identifiable obligatoire) — voir
 * https://operations.osmfoundation.org/policies/nominatim/. Suffisant pour
 * ce projet (contribution d'activités, faible volume), mais à surveiller si
 * le trafic augmente : il faudrait alors passer à une clé API dédiée
 * (ex : Nominatim self-hosted, ou un fournisseur commercial).
 */
@Service
public class GeocodingService {

    private static final String NOMINATIM_BASE_URL = "https://nominatim.openstreetmap.org";

    private final RestClient restClient;

    public GeocodingService() {
        this(RestClient.builder());
    }

    /**
     * Constructeur visible package-privé pour les tests : permet d'injecter
     * un {@link RestClient.Builder} lié à un {@code MockRestServiceServer}
     * plutôt que d'appeler le vrai service Nominatim.
     */
    GeocodingService(RestClient.Builder builder) {
        this.restClient = builder
                .baseUrl(NOMINATIM_BASE_URL)
                .defaultHeader("User-Agent", "LocalLife/0.1 (contact: dev@locallife.local)")
                .build();
    }

    /**
     * Géocode une adresse.
     *
     * @param address l'adresse à convertir en coordonnées
     * @return les coordonnées du premier résultat trouvé
     * @throws IllegalArgumentException si l'adresse est vide
     * @throws AddressNotFoundException si aucun résultat n'est trouvé
     * @throws GeocodingUnavailableException si l'appel au service échoue
     */
    public Coordinates geocode(String address) {
        if (address == null || address.isBlank()) {
            throw new IllegalArgumentException("L'adresse ne peut pas être vide.");
        }

        List<NominatimResult> results;
        try {
            results = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search")
                            .queryParam("q", address)
                            .queryParam("format", "json")
                            .queryParam("limit", 1)
                            // LL-EF-008 : demande les composants d'adresse détaillés
                            // (ville, code postal...) dans la même réponse, pour
                            // éviter un second appel réseau (reverse-geocoding) —
                            // voir la javadoc de Coordinates.
                            .queryParam("addressdetails", 1)
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<NominatimResult>>() { });
        } catch (RestClientException exception) {
            throw new GeocodingUnavailableException(exception);
        }

        if (results == null || results.isEmpty()) {
            throw new AddressNotFoundException(address);
        }

        NominatimResult result = results.get(0);
        NominatimAddress details = result.address();
        String city = details == null ? null : firstNonBlank(details.city(), details.town(), details.village());
        String postalCode = details == null ? null : details.postcode();
        return new Coordinates(Double.parseDouble(result.lat()), Double.parseDouble(result.lon()), city, postalCode);
    }

    /**
     * Recherche une ville par son nom (paramètres utilisateur) : contrairement
     * à {@link #geocode}, seuls les lieux de type ville/village sont acceptés
     * ({@code featureType=city}), et le résultat doit porter un nom de
     * localité — sinon la saisie ne désigne pas une ville existante.
     *
     * @return le nom officiel de la ville (dans {@code city}) et ses coordonnées
     * @throws IllegalArgumentException si le nom est vide
     * @throws CityNotFoundException si aucune ville ne correspond
     * @throws GeocodingUnavailableException si l'appel au service échoue
     */
    public Coordinates findCity(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Le nom de la ville ne peut pas être vide.");
        }

        List<NominatimResult> results;
        try {
            results = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search")
                            .queryParam("q", name)
                            .queryParam("featureType", "city")
                            .queryParam("format", "json")
                            .queryParam("limit", 1)
                            .queryParam("addressdetails", 1)
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<NominatimResult>>() { });
        } catch (RestClientException exception) {
            throw new GeocodingUnavailableException(exception);
        }

        NominatimResult result = results == null || results.isEmpty() ? null : results.get(0);
        String city = result == null ? null : cityOf(result.address());
        if (city == null) {
            throw new CityNotFoundException("Ville introuvable : \"" + name.trim() + "\".");
        }
        return new Coordinates(Double.parseDouble(result.lat()), Double.parseDouble(result.lon()), city,
                result.address().postcode());
    }

    /**
     * Ville correspondant à une position (géolocalisation du navigateur,
     * paramètres utilisateur), par géocodage inverse au niveau ville
     * ({@code zoom=10}). Les coordonnées renvoyées sont celles du lieu
     * trouvé par Nominatim, pas la position exacte fournie : seule la ville
     * est conservée, jamais l'adresse de l'utilisateur.
     *
     * @throws IllegalArgumentException si la position est hors plage
     * @throws CityNotFoundException si aucune ville n'est trouvée à cette position
     * @throws GeocodingUnavailableException si l'appel au service échoue
     */
    public Coordinates reverseCity(double latitude, double longitude) {
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Position invalide.");
        }

        NominatimResult result;
        try {
            result = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/reverse")
                            .queryParam("lat", latitude)
                            .queryParam("lon", longitude)
                            .queryParam("zoom", 10)
                            .queryParam("format", "json")
                            .queryParam("addressdetails", 1)
                            .build())
                    .retrieve()
                    .body(NominatimResult.class);
        } catch (RestClientException exception) {
            throw new GeocodingUnavailableException(exception);
        }

        // Position sans résultat : Nominatim répond 200 avec {"error": "..."}, sans lat/lon ni address.
        String city = result == null || result.lat() == null ? null : cityOf(result.address());
        if (city == null) {
            throw new CityNotFoundException("Aucune ville trouvée à ta position.");
        }
        return new Coordinates(Double.parseDouble(result.lat()), Double.parseDouble(result.lon()), city,
                result.address().postcode());
    }

    /**
     * Ville choisie par une position (géolocalisation, prioritaire) ou par
     * son nom — point d'entrée commun des paramètres utilisateur et des
     * alertes email.
     *
     * @throws IllegalArgumentException si ni nom ni position complète ne sont fournis
     * @throws CityNotFoundException si aucune ville ne correspond
     * @throws GeocodingUnavailableException si l'appel au service échoue
     */
    public Coordinates resolveCity(String name, Double latitude, Double longitude) {
        if (latitude != null || longitude != null) {
            if (latitude == null || longitude == null) {
                throw new IllegalArgumentException("La latitude et la longitude doivent être fournies ensemble.");
            }
            return reverseCity(latitude, longitude);
        }
        return findCity(name);
    }

    private String cityOf(NominatimAddress details) {
        return details == null
                ? null
                : firstNonBlank(details.city(), details.town(), details.village(), details.municipality());
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    /**
     * Sous-ensemble de la réponse JSON de Nominatim qui nous intéresse.
     * Nominatim renvoie lat/lon sous forme de chaînes, pas de nombres.
     * {@code address} n'est présent que si la requête demande
     * {@code addressdetails=1} (LL-EF-008).
     */
    private record NominatimResult(String lat, String lon, NominatimAddress address) {
    }

    /**
     * Sous-ensemble de l'objet {@code address} de Nominatim (LL-EF-008).
     * Nominatim n'a pas de champ "ville" unique : selon la zone, la
     * localité se trouve dans {@code city}, {@code town} ou
     * {@code village} (voir sa documentation "Place types") — les trois
     * sont donc lus, dans cet ordre de priorité (voir
     * {@link #firstNonBlank}). {@code municipality} : dernier recours pour
     * {@link #findCity}/{@link #reverseCity} (communes sans champ ville).
     */
    private record NominatimAddress(String city, String town, String village, String municipality, String postcode) {
    }
}
