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
 */
public record UserPreferences(
        Long userId,
        String defaultCity,
        Double defaultLatitude,
        Double defaultLongitude,
        Integer defaultRadiusKm,
        List<String> defaultCategoryCodes) {

    /** Préférences d'un utilisateur qui n'en a encore enregistré aucune. */
    public static UserPreferences empty(Long userId) {
        return new UserPreferences(userId, null, null, null, null, List.of());
    }

}
