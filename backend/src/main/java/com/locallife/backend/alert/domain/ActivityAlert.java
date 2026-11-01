package com.locallife.backend.alert.domain;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Alerte (table {@code activity_alert}, V52) : nouvelles activités
 * publiées d'au moins une des {@code categoryCodes} et/ou nouveaux food
 * trucks ({@code includeFoodTrucks}, V55 — au moins l'un des deux), à moins de
 * {@code radiusKm} de {@code city}, envoyées par e-mail
 * ({@code notifyEmail}) et/ou par notification push ({@code notifyPush},
 * application mobile — au moins un des deux). Le jeton de désinscription n'est
 * volontairement pas porté ici : il ne sort de la base que pour construire
 * le lien de l'email (voir {@code AlertRecipient}).
 *
 * {@code keywords} (V80, Premium et Pro) : mots-clés ou expressions dont l'un doit apparaître dans le titre ou la
 * description d'une activité (ou le nom, la cuisine ou la description d'un food truck). Vide : aucun filtre. Une alerte
 * a au moins une catégorie, un mot-clé ou les food trucks.
 */
public record ActivityAlert(
        Long id,
        Long userId,
        String city,
        double latitude,
        double longitude,
        int radiusKm,
        List<String> categoryCodes,
        boolean includeFoodTrucks,
        boolean notifyEmail,
        boolean notifyPush,
        LocalDateTime createdAt,
        List<String> keywords) {

    public ActivityAlert {
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
    }

    /** Alerte sans mot-clé. */
    public ActivityAlert(Long id, Long userId, String city, double latitude, double longitude, int radiusKm,
            List<String> categoryCodes, boolean includeFoodTrucks, boolean notifyEmail, boolean notifyPush,
            LocalDateTime createdAt) {
        this(id, userId, city, latitude, longitude, radiusKm, categoryCodes, includeFoodTrucks, notifyEmail,
                notifyPush, createdAt, List.of());
    }

    /** Même alerte avec d'autres mots-clés. */
    public ActivityAlert withKeywords(List<String> newKeywords) {
        return new ActivityAlert(id, userId, city, latitude, longitude, radiusKm, categoryCodes, includeFoodTrucks,
                notifyEmail, notifyPush, createdAt, newKeywords);
    }
}
