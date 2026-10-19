package com.locallife.backend.foodtruck.domain;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Règles d'une présence de food truck (ADR-0013, FOODTRUCK-01), en fonctions pures : jours, horaires, période et
 * chevauchement. Un food truck ne peut pas être annoncé à deux endroits au même moment.
 */
public final class PresenceRules {

    /** Codes des jours, dans l'ordre de la semaine. */
    public static final List<String> DAY_CODES = List.of("MO", "TU", "WE", "TH", "FR", "SA", "SU");

    /** Créneau d'une présence : jours ({@code null} = tous), horaires et période ({@code null} = sans borne). */
    public record Slot(
            String daysOfWeek, LocalTime startTime, LocalTime endTime, LocalDate validFrom, LocalDate validUntil) {
    }

    private PresenceRules() {
    }

    /**
     * Jours sous leur forme stockée : codes dans l'ordre de la semaine, séparés par des virgules ; {@code null} pour
     * aucun jour choisi ou les sept (tous les jours).
     *
     * @throws IllegalArgumentException code inconnu
     */
    public static String normalizeDays(Collection<String> days) {
        if (days == null || days.isEmpty()) {
            return null;
        }
        Set<String> chosen = new LinkedHashSet<>();
        for (String day : days) {
            String code = day == null ? "" : day.trim().toUpperCase(Locale.ROOT);
            if (!DAY_CODES.contains(code)) {
                throw new IllegalArgumentException("Jour inconnu : " + day + " (MO, TU, WE, TH, FR, SA ou SU).");
            }
            chosen.add(code);
        }
        if (chosen.size() == DAY_CODES.size()) {
            return null;
        }
        List<String> ordered = new ArrayList<>(DAY_CODES);
        ordered.retainAll(chosen);
        return String.join(",", ordered);
    }

    /**
     * Vérifie un créneau saisi : horaires tous deux renseignés ou tous deux absents, début avant fin, période dans
     * l'ordre et pas entièrement passée.
     *
     * @throws IllegalArgumentException créneau invalide, avec un message lisible
     */
    public static void validate(Slot slot, LocalDate today) {
        if ((slot.startTime() == null) != (slot.endTime() == null)) {
            throw new IllegalArgumentException("Indique l'heure d'arrivée et l'heure de départ, ou aucune des deux.");
        }
        if (slot.startTime() != null && !slot.startTime().isBefore(slot.endTime())) {
            throw new IllegalArgumentException("L'heure de départ doit suivre l'heure d'arrivée.");
        }
        if (slot.validFrom() != null && slot.validUntil() != null && slot.validFrom().isAfter(slot.validUntil())) {
            throw new IllegalArgumentException("La fin de la période doit suivre son début.");
        }
        if (slot.validUntil() != null && slot.validUntil().isBefore(today)) {
            throw new IllegalArgumentException("La période est déjà passée.");
        }
    }

    /** Deux créneaux se chevauchent : un jour commun, des périodes qui se croisent et des horaires qui se croisent. */
    public static boolean overlaps(Slot first, Slot second) {
        return daysIntersect(first.daysOfWeek(), second.daysOfWeek())
                && periodsIntersect(first, second)
                && hoursIntersect(first, second);
    }

    private static boolean daysIntersect(String first, String second) {
        if (first == null || second == null) {
            return true;
        }
        Set<String> days = Set.of(first.split(","));
        for (String day : second.split(",")) {
            if (days.contains(day)) {
                return true;
            }
        }
        return false;
    }

    private static boolean periodsIntersect(Slot first, Slot second) {
        boolean firstEndsBefore = first.validUntil() != null && second.validFrom() != null
                && first.validUntil().isBefore(second.validFrom());
        boolean secondEndsBefore = second.validUntil() != null && first.validFrom() != null
                && second.validUntil().isBefore(first.validFrom());
        return !firstEndsBefore && !secondEndsBefore;
    }

    /**
     * Horaires non précisés = toute la journée ; des créneaux bout à bout (12 h – 14 h, 14 h – 18 h) ne se
     * croisent pas.
     */
    private static boolean hoursIntersect(Slot first, Slot second) {
        if (first.startTime() == null || second.startTime() == null) {
            return true;
        }
        return first.startTime().isBefore(second.endTime()) && second.startTime().isBefore(first.endTime());
    }
}
