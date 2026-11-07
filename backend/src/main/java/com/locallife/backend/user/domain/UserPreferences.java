package com.locallife.backend.user.domain;

import java.util.List;

/**
 * Préférences de recherche par défaut d'un utilisateur (table
 * {@code user_preferences}, V51) : appliquées par l'Explorer à son
 * ouverture quand l'utilisateur est connecté.
 *
 * Tous les champs sauf {@code userId} sont facultatifs ({@code null} /
 * liste vide = pas de préférence). {@code defaultLatitude}/
 * {@code defaultLongitude} sont les coordonnées géocodées de
 * {@code defaultCity}, renseignées ensemble ou pas du tout.
 * {@code defaultMapLayer} : carte ouverte par défaut (V87, « night » depuis V88), une des
 * {@link #MAP_LAYERS} ; {@code null} = dernière carte choisie dans le navigateur.
 */
public record UserPreferences(
        Long userId,
        String defaultCity,
        Double defaultLatitude,
        Double defaultLongitude,
        Integer defaultRadiusKm,
        List<String> defaultCategoryCodes,
        String defaultMapLayer) {

    /** Cartes de l'Explorer : activités, lieux, food trucks (mêmes valeurs que le frontend). */
    public static final List<String> MAP_LAYERS = List.of("activities", "places", "foodTrucks", "night");

    /** Préférences d'un utilisateur qui n'en a encore enregistré aucune. */
    public static UserPreferences empty(Long userId) {
        return new UserPreferences(userId, null, null, null, null, List.of(), null);
    }

}
