package com.locallife.backend.activity.application;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Récurrence hebdomadaire saisie par un contributeur (food truck, marché, cours… « tous les mardis et
 * jeudis de 11h30 à 14h00 »), déjà validée. Traduite en {@code Schedule} RRULE ({@link #recurrenceRule()},
 * seul sous-ensemble {@code FREQ=WEEKLY;BYDAY=...} que le frontend sait afficher, voir
 * {@code frontend/src/utils/recurrence.ts}) par {@link RecurringContributionService}.
 *
 * @param daysOfWeek jours de passage, au moins un
 * @param startTime heure de début de chaque passage
 * @param endTime heure de fin, {@code null} si inconnue ; antérieure ou égale à {@code startTime} :
 *     le passage se termine le lendemain
 * @param from premier jour possible de la récurrence
 * @param until dernier jour possible, {@code null} : sans date de fin connue
 */
public record ContributionRecurrence(
        Set<DayOfWeek> daysOfWeek, LocalTime startTime, LocalTime endTime, LocalDate from, LocalDate until) {

    private static final Map<String, DayOfWeek> DAY_CODES = Map.of(
            "MO", DayOfWeek.MONDAY,
            "TU", DayOfWeek.TUESDAY,
            "WE", DayOfWeek.WEDNESDAY,
            "TH", DayOfWeek.THURSDAY,
            "FR", DayOfWeek.FRIDAY,
            "SA", DayOfWeek.SATURDAY,
            "SU", DayOfWeek.SUNDAY);

    /**
     * Valide et convertit les champs bruts de la requête.
     *
     * @param days codes RFC 5545 des jours ({@code MO}…{@code SU})
     * @param startTime {@code HH:mm}, obligatoire
     * @param endTime {@code HH:mm}, facultatif
     * @param from {@code yyyy-MM-dd}, facultatif ({@code today} par défaut)
     * @param until {@code yyyy-MM-dd}, facultatif
     * @throws IllegalArgumentException si un champ est manquant ou illisible
     */
    public static ContributionRecurrence of(
            List<String> days, String startTime, String endTime, String from, String until, LocalDate today) {
        if (days == null || days.isEmpty()) {
            throw new IllegalArgumentException("Choisis au moins un jour de passage pour la récurrence.");
        }
        Set<DayOfWeek> daysOfWeek = EnumSet.noneOf(DayOfWeek.class);
        for (String day : days) {
            DayOfWeek dayOfWeek = day == null ? null : DAY_CODES.get(day.trim().toUpperCase());
            if (dayOfWeek == null) {
                throw new IllegalArgumentException("Jour de récurrence inconnu : " + day + " (attendu : MO…SU).");
            }
            daysOfWeek.add(dayOfWeek);
        }
        if (isBlank(startTime)) {
            throw new IllegalArgumentException("L'heure de début de la récurrence est obligatoire.");
        }
        LocalTime resolvedStartTime = parseTime("startTime", startTime);
        LocalTime resolvedEndTime = isBlank(endTime) ? null : parseTime("endTime", endTime);
        LocalDate resolvedFrom = isBlank(from) ? today : parseDate("from", from);
        LocalDate resolvedUntil = isBlank(until) ? null : parseDate("until", until);
        if (resolvedUntil != null && resolvedUntil.isBefore(resolvedFrom)) {
            throw new IllegalArgumentException("La date de fin de la récurrence précède sa date de début.");
        }
        return new ContributionRecurrence(
                daysOfWeek, resolvedStartTime, resolvedEndTime, resolvedFrom, resolvedUntil);
    }

    /**
     * Relit une récurrence enregistrée ({@code FREQ=WEEKLY;BYDAY=...}, voir {@link #recurrenceRule()}).
     * Vide pour toute autre règle, qu'un contributeur ne peut pas saisir.
     */
    public static Optional<ContributionRecurrence> fromSchedule(
            String recurrenceRule, LocalTime startTime, LocalTime endTime, LocalDate from, LocalDate until) {
        Map<String, String> parts = new HashMap<>();
        for (String part : recurrenceRule.split(";")) {
            String[] keyValue = part.split("=", 2);
            if (keyValue.length == 2) {
                parts.put(keyValue[0].trim().toUpperCase(), keyValue[1].trim().toUpperCase());
            }
        }
        if (!"WEEKLY".equals(parts.get("FREQ")) || parts.get("BYDAY") == null || from == null
                || parts.keySet().stream().anyMatch(key -> !key.equals("FREQ") && !key.equals("BYDAY"))) {
            return Optional.empty();
        }
        Set<DayOfWeek> daysOfWeek = EnumSet.noneOf(DayOfWeek.class);
        for (String day : parts.get("BYDAY").split(",")) {
            DayOfWeek dayOfWeek = DAY_CODES.get(day);
            if (dayOfWeek == null) {
                return Optional.empty();
            }
            daysOfWeek.add(dayOfWeek);
        }
        return Optional.of(new ContributionRecurrence(daysOfWeek, startTime, endTime, from, until));
    }

    /** Codes RFC 5545 des jours, dans l'ordre de la semaine ({@code ["TU", "TH"]}). */
    public List<String> dayCodes() {
        Map<DayOfWeek, String> codesByDay = DAY_CODES.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));
        return daysOfWeek.stream().sorted().map(codesByDay::get).toList();
    }

    /** RRULE RFC 5545, jours dans l'ordre de la semaine : {@code FREQ=WEEKLY;BYDAY=TU,TH}. */
    public String recurrenceRule() {
        return "FREQ=WEEKLY;BYDAY=" + String.join(",", dayCodes());
    }

    private static LocalTime parseTime(String fieldName, String value) {
        try {
            return LocalTime.parse(value.trim());
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Le champ '" + fieldName + "' doit être une heure (HH:mm) valide.");
        }
    }

    private static LocalDate parseDate(String fieldName, String value) {
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Le champ '" + fieldName + "' doit être une date (yyyy-MM-dd) valide.");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
