package com.locallife.backend.schedule.application;

import com.locallife.backend.schedule.domain.Schedule;
import java.text.ParseException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import net.fortuna.ical4j.model.Date;
import net.fortuna.ical4j.model.DateList;
import net.fortuna.ical4j.model.Recur;
import net.fortuna.ical4j.model.parameter.Value;
import org.springframework.stereotype.Service;

/**
 * Règles de récurrence (LL-11003) : {@code Schedule#recurrenceRule}
 * (LL-11002), laissé opaque par ce dernier ticket, est ici un RRULE RFC
 * 5545 — voir {@code docs/02_Architecture/ADR-0003-recurrence-ical4j.md}
 * pour le choix d'ical4j (« sans créer un moteur propriétaire », exigence
 * explicite du ticket) et la répartition des responsabilités entre
 * ical4j (récurrence calendaire : FREQ/BYDAY/UNTIL) et {@code java.time}
 * (résolution du fuseau horaire).
 *
 * {@code recurrenceRule} ne porte volontairement pas de composant
 * {@code UNTIL} : la borne de fin de récurrence reste
 * {@link Schedule#validUntil()} (LL-11002), pour éviter une double
 * source de vérité — voir ADR-0003. Un {@code UNTIL} explicite fourni
 * malgré tout par une source resterait respecté (les deux bornes sont
 * combinées, la plus stricte l'emporte, voir
 * {@link #computeOccurrenceDates}).
 */
@Service
public class RecurrenceRuleService {

    /**
     * Nombre maximum de dates renvoyées par ical4j en un seul appel —
     * garde-fou de la bibliothèque contre un calcul indéfiniment long
     * pour une règle sans date de fin (« absence de date de fin lorsque
     * la source le permet », cas explicite du ticket). Largement
     * suffisant pour une fenêtre d'un an même en {@code FREQ=DAILY}.
     */
    private static final int MAX_OCCURRENCES = 366;

    /**
     * Valide qu'une règle de récurrence est un RRULE RFC 5545 conforme,
     * en la faisant analyser par ical4j plutôt que par un analyseur
     * maison — critère d'acceptation « aucun format propriétaire
     * inventé ».
     *
     * @throws IllegalArgumentException si {@code recurrenceRule} n'est pas un RRULE valide
     */
    public void validate(String recurrenceRule) {
        try {
            new Recur(recurrenceRule);
        } catch (ParseException exception) {
            throw new IllegalArgumentException(
                    "Règle de récurrence invalide (attendu : RRULE RFC 5545, ex. "
                            + "FREQ=WEEKLY;BYDAY=TU) : " + recurrenceRule,
                    exception);
        }
    }

    /**
     * Calcule les dates d'occurrence d'un schedule dans la fenêtre
     * {@code [windowStart, windowEnd]} (bornes incluses) — critère
     * d'acceptation « calcul des occurrences possible ». Volontairement
     * bornée à une fenêtre explicite plutôt que de tenter d'énumérer une
     * récurrence sans fin : à charge de l'appelant (LL-11004,
     * matérialisation des occurrences) de fournir la fenêtre pertinente.
     *
     * Schedule sans {@code recurrenceRule} (événement ponctuel, voir la
     * javadoc de {@link Schedule}) : une seule date, {@code
     * schedule.validFrom()}, si elle tombe dans la fenêtre demandée.
     *
     * {@code schedule.validFrom()} sert de point d'ancrage (DTSTART) à
     * la récurrence — nécessaire à ical4j pour déterminer, par exemple,
     * quel jour de semaine appliquer par défaut. Si {@code validFrom()}
     * est en dehors de la fenêtre demandée, il n'apparaît pas lui-même
     * dans le résultat, mais continue de déterminer le rythme de la
     * récurrence (ex. « tous les mardis » à partir d'un mardi précis).
     *
     * @throws IllegalArgumentException si {@code schedule.recurrenceRule()} n'est pas un RRULE valide
     */
    public List<LocalDate> computeOccurrenceDates(Schedule schedule, LocalDate windowStart, LocalDate windowEnd) {
        if (schedule.recurrenceRule() == null) {
            return computePunctualOccurrence(schedule, windowStart, windowEnd);
        }

        Recur recur;
        try {
            recur = new Recur(schedule.recurrenceRule());
        } catch (ParseException exception) {
            throw new IllegalArgumentException(
                    "Règle de récurrence invalide : " + schedule.recurrenceRule(), exception);
        }

        LocalDate effectiveStart = effectiveStart(schedule, windowStart);
        LocalDate effectiveEnd = effectiveEnd(schedule, windowEnd);
        if (effectiveStart.isAfter(effectiveEnd)) {
            return List.of();
        }

        Date seed = toIcal4jDate(schedule.validFrom() != null ? schedule.validFrom() : effectiveStart);
        Date periodStart = toIcal4jDate(effectiveStart);
        Date periodEnd = toIcal4jDate(effectiveEnd);

        DateList dates = recur.getDates(seed, periodStart, periodEnd, Value.DATE, MAX_OCCURRENCES);

        List<LocalDate> occurrenceDates = new ArrayList<>();
        for (Object date : dates) {
            occurrenceDates.add(toLocalDate((Date) date));
        }
        return occurrenceDates;
    }

    /**
     * Combine {@link #computeOccurrenceDates} avec l'heure de début et
     * le fuseau horaire du schedule — critère d'acceptation « fuseau
     * horaire pris en compte ». {@code ZonedDateTime.of} résout
     * correctement les transitions d'heure d'été/hiver pour l'heure
     * locale demandée (voir ADR-0003 pour la raison de ne pas utiliser
     * l'API {@code TimeZone} d'ical4j ici).
     *
     * {@code schedule.timezone()}/{@code startTime()} nullables
     * (LL-11002, données potentiellement incomplètes à la source) : à
     * défaut, {@link ZoneOffset#UTC} et {@link LocalTime#MIDNIGHT} sont
     * utilisés plutôt qu'une exception — cohérent avec la tolérance aux
     * données incomplètes déjà établie dans le projet.
     */
    public List<ZonedDateTime> computeOccurrenceStarts(
            Schedule schedule, LocalDate windowStart, LocalDate windowEnd) {
        ZoneId zoneId = schedule.timezone() != null ? ZoneId.of(schedule.timezone()) : ZoneOffset.UTC;
        LocalTime startTime = schedule.startTime() != null ? schedule.startTime() : LocalTime.MIDNIGHT;

        return computeOccurrenceDates(schedule, windowStart, windowEnd).stream()
                .map(date -> ZonedDateTime.of(date, startTime, zoneId))
                .toList();
    }

    private List<LocalDate> computePunctualOccurrence(Schedule schedule, LocalDate windowStart, LocalDate windowEnd) {
        if (schedule.validFrom() == null) {
            return List.of();
        }
        boolean withinWindow =
                !schedule.validFrom().isBefore(windowStart) && !schedule.validFrom().isAfter(windowEnd);
        return withinWindow ? List.of(schedule.validFrom()) : List.of();
    }

    private LocalDate effectiveStart(Schedule schedule, LocalDate windowStart) {
        if (schedule.validFrom() == null || schedule.validFrom().isBefore(windowStart)) {
            return windowStart;
        }
        return schedule.validFrom();
    }

    private LocalDate effectiveEnd(Schedule schedule, LocalDate windowEnd) {
        if (schedule.validUntil() == null || schedule.validUntil().isAfter(windowEnd)) {
            return windowEnd;
        }
        return schedule.validUntil();
    }

    private static Date toIcal4jDate(LocalDate localDate) {
        try {
            return new Date(localDate.toString().replace("-", ""));
        } catch (ParseException exception) {
            // Ne peut arriver : LocalDate#toString produit toujours un yyyy-MM-dd valide.
            throw new IllegalStateException(exception);
        }
    }

    private static LocalDate toLocalDate(Date icalDate) {
        return Instant.ofEpochMilli(icalDate.getTime()).atZone(ZoneOffset.UTC).toLocalDate();
    }
}
