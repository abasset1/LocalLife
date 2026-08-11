package com.locallife.backend.geocoding.application;

/**
 * Aucun résultat trouvé pour l'adresse fournie. Erreur côté utilisateur
 * (adresse mal saisie ou trop imprécise) : traitée comme un 400 par le contrôleur.
 */
public class AddressNotFoundException extends GeocodingException {

    public AddressNotFoundException(String address) {
        super("Adresse introuvable : \"" + address + "\".");
    }
}
