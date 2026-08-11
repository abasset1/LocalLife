package com.locallife.backend.geocoding.application;

/**
 * Le service de géocodage externe (Nominatim) est indisponible ou a échoué.
 * Ce n'est pas une erreur de saisie de l'utilisateur : traitée comme un 503
 * par le contrôleur.
 */
public class GeocodingUnavailableException extends GeocodingException {

    public GeocodingUnavailableException(Throwable cause) {
        super("Le service de géocodage est indisponible, réessaie plus tard.", cause);
    }
}
