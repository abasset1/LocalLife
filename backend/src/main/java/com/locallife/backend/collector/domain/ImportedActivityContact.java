package com.locallife.backend.collector.domain;

import java.time.LocalDateTime;

/**
 * Activité importée d'une source externe et l'adresse e-mail de contact (lien {@code EMAIL}) à prévenir de son
 * référencement : le strict nécessaire à l'avis envoyé par {@code ImportedActivityContactNotifier}. {@code email}
 * est normalisée (espaces retirés, minuscules).
 */
public record ImportedActivityContact(Long activityId, String title, String city, LocalDateTime startDate,
        String email) {
}
