package com.locallife.backend.alert.domain;

import java.time.LocalDateTime;

/**
 * Activité nouvellement publiée correspondant à une alerte : le strict nécessaire à l'email.
 * {@code approximatePosition} : position {@code APPROXIMATE} (ADR-0011, phase A) ; l'alerte est envoyée avec la
 * mention « emplacement approximatif ».
 */
public record AlertActivity(Long id, String title, String city, LocalDateTime startDate, boolean approximatePosition) {

    /** Activité de position non approximative. */
    public AlertActivity(Long id, String title, String city, LocalDateTime startDate) {
        this(id, title, city, startDate, false);
    }
}
