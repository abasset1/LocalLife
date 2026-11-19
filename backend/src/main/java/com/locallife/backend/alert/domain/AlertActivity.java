package com.locallife.backend.alert.domain;

import java.time.LocalDateTime;

/**
 * Activité nouvellement publiée correspondant à une alerte : le strict nécessaire à l'email.
 * {@code approximatePosition} : position {@code APPROXIMATE} (ADR-0011, phase A) ; l'alerte est envoyée avec la
 * mention « emplacement approximatif ». {@code latitude} et {@code longitude} : position de l'activité, pour que
 * le lien de l'email ouvre la carte à son emplacement ; {@code null} si elle n'en a pas.
 */
public record AlertActivity(
        Long id, String title, String city, LocalDateTime startDate, boolean approximatePosition,
        Double latitude, Double longitude) {

    /** Activité sans position connue. */
    public AlertActivity(Long id, String title, String city, LocalDateTime startDate, boolean approximatePosition) {
        this(id, title, city, startDate, approximatePosition, null, null);
    }

    /** Activité de position non approximative, sans position connue. */
    public AlertActivity(Long id, String title, String city, LocalDateTime startDate) {
        this(id, title, city, startDate, false);
    }
}
