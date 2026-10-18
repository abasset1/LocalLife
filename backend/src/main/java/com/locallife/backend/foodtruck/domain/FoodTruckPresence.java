package com.locallife.backend.foodtruck.domain;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Présence d'un food truck à un emplacement (ADR-0013) : le food truck, l'emplacement (une ligne de
 * {@code location}) et le créneau. Vue de lecture, avec ce qu'il faut pour l'afficher sur la carte.
 *
 * <p>{@code daysOfWeek} : codes {@code MO..SU} séparés par des virgules, {@code null} = tous les jours ;
 * {@code startTime}/{@code endTime} : {@code null} = horaires non précisés ; {@code validFrom}/{@code validUntil} :
 * bornes facultatives. {@code placeId} : lieu LocalLife publié auquel correspond l'emplacement (l'emplacement
 * lui-même, ou le lieu auquel il est relié, ADR-0011 § 7.2.9), sinon {@code null}.
 */
public record FoodTruckPresence(
        Long id,
        Long foodTruckId,
        String foodTruckName,
        String description,
        String category,
        String contact,
        Double latitude,
        Double longitude,
        String spotName,
        SpotKind spotKind,
        String daysOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        LocalDate validFrom,
        LocalDate validUntil,
        Long placeId) {
}
