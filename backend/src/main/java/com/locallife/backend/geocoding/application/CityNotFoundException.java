package com.locallife.backend.geocoding.application;

/**
 * Aucune ville (city/town/village au sens Nominatim) ne correspond au nom ou
 * à la position fournis. Erreur côté utilisateur : traitée comme un 400.
 */
public class CityNotFoundException extends GeocodingException {

    public CityNotFoundException(String message) {
        super(message);
    }
}
