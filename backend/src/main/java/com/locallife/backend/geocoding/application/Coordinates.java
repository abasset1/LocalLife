package com.locallife.backend.geocoding.application;

/**
 * Coordonnées géographiques résultant d'un géocodage (LL-3012), enrichies
 * en LL-EF-008 de {@code city}/{@code postalCode} : Nominatim renvoie déjà
 * ces informations dans la même réponse dès lors que l'appel demande
 * {@code addressdetails=1} (voir {@link GeocodingService}) — aucun appel
 * réseau supplémentaire n'était donc nécessaire pour permettre le
 * regroupement par ville de la vue liste (LL-EF-008). Toutes deux
 * nullables : Nominatim ne garantit pas leur présence (adresse rurale
 * imprécise, ville absente de sa base d'{@code addressdetails}, etc.).
 */
public record Coordinates(double latitude, double longitude, String city, String postalCode) {
}
