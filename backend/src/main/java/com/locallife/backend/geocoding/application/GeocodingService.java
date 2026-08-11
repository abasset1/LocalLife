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
        return new Coordinates(Double.parseDouble(result.lat()), Double.parseDouble(result.lon()));
    }

    /**
     * Sous-ensemble de la réponse JSON de Nominatim qui nous intéresse.
     * Nominatim renvoie lat/lon sous forme de chaînes, pas de nombres.
     */
    private record NominatimResult(String lat, String lon) {
    }
}
