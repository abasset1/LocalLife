package com.locallife.backend.alert.domain;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Alerte email (table {@code activity_alert}, V52) : nouvelles activités
 * publiées d'au moins une des {@code categoryCodes}, à moins de
 * {@code radiusKm} de {@code city}, envoyées par e-mail
 * ({@code notifyEmail}) et/ou par notification push ({@code notifyPush},
 * application mobile — au moins un des deux). Le jeton de désinscription n'est
 * volontairement pas porté ici : il ne sort de la base que pour construire
 * le lien de l'email (voir {@code AlertRecipient}).
 */
public record ActivityAlert(
        Long id,
        Long userId,
        String city,
        double latitude,
        double longitude,
        int radiusKm,
        List<String> categoryCodes,
        boolean notifyEmail,
        boolean notifyPush,
        LocalDateTime createdAt) {
}
