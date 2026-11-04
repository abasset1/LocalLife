package com.locallife.backend.foodtruck.domain;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

/**
 * Présence d'un food truck à un emplacement (ADR-0013) : le food truck, l'emplacement (une ligne de
 * {@code location}) et le créneau. Vue de lecture, avec ce qu'il faut pour l'afficher sur la carte.
 *
 * <p>{@code daysOfWeek} : codes {@code MO..SU} séparés par des virgules, {@code null} = tous les jours ;
 * {@code startTime}/{@code endTime} : {@code null} = horaires non précisés ; {@code validFrom}/{@code validUntil} :
 * bornes facultatives. {@code placeId} : lieu LocalLife publié auquel correspond l'emplacement (l'emplacement
 * lui-même, ou le lieu auquel il est relié, ADR-0011 § 7.2.9), sinon {@code null}.
 *
 * <p>{@code cuisineType}, {@code markerEmoji}, {@code markerColor} : type de cuisine et pastille du food truck
 * ({@code null} = non renseignés) ; {@code photoUpdatedAt} : dernière mise à jour de sa photo, {@code null} sans photo.
 *
 * <p>{@code spotAddress} : adresse exacte de l'emplacement (« 12 rue des Lilas, 35000 Rennes »), {@code null} si
 * l'emplacement n'en a pas ; {@code instagramUrl}/{@code facebookUrl} : pages du food truck, {@code null} sinon.
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
        Long placeId,
        String cuisineType,
        String markerEmoji,
        String markerColor,
        OffsetDateTime photoUpdatedAt,
        String spotAddress,
        String instagramUrl,
        String facebookUrl) {

    /** Présence d'un food truck sans type de cuisine, pastille ni photo. */
    public FoodTruckPresence(Long id, Long foodTruckId, String foodTruckName, String description, String category,
            String contact, Double latitude, Double longitude, String spotName, SpotKind spotKind, String daysOfWeek,
            LocalTime startTime, LocalTime endTime, LocalDate validFrom, LocalDate validUntil, Long placeId) {
        this(id, foodTruckId, foodTruckName, description, category, contact, latitude, longitude, spotName, spotKind,
                daysOfWeek, startTime, endTime, validFrom, validUntil, placeId, null, null, null, null, null, null,
                null);
    }
}
