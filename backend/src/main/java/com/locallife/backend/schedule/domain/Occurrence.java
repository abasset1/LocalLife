package com.locallife.backend.schedule.domain;

import java.time.Instant;
import org.springframework.data.annotation.Id;

/**
 * Entité de domaine Occurrence (LL-11004, Sprint 11) : une occurrence
 * concrète d'un {@link Schedule} — voir
 * {@code docs/05_Sprints/SPRINT_11.md}, section 7. Un {@code Schedule}
 * « tous les mardis 11:30 → 14:00 » se matérialise en plusieurs
 * {@code Occurrence} (08/09/2026, 15/09/2026, 22/09/2026...) ; chaque
 * {@code Occurrence} correspond typiquement à l'une des dates renvoyées
 * par {@code RecurrenceRuleService#computeOccurrenceDates} (LL-11003).
 * Ce ticket ne fait que poser le modèle ; le service qui matérialise
 * effectivement des {@code Occurrence} à partir d'un {@code Schedule}
 * (appelant {@code RecurrenceRuleService}) est du ressort d'un ticket
 * ultérieur, non encore traité à ce stade du sprint.
 *
 * Référence {@code Schedule}/{@code Location} par id uniquement (même
 * convention que {@code Schedule.activityId}/{@code locationId},
 * LL-11002).
 *
 * {@code startAt}/{@code endAt} en {@link Instant} plutôt qu'en
 * {@code LocalDateTime} (contrairement à {@code Activity.startDate}/
 * {@code endDate}, qui restent naïfs vis-à-vis du fuseau horaire) :
 * volontaire — une {@code Occurrence} est précisément le résultat déjà
 * résolu de {@code RecurrenceRuleService#computeOccurrenceStarts}
 * (date + heure + fuseau combinés en {@code ZonedDateTime}, DST déjà
 * pris en compte, voir ADR-0003) ; la reconvertir en type naïf à la
 * persistance perdrait cette résolution. {@code endAt} nullable (un
 * schedule peut ne pas avoir d'heure de fin connue, LL-11002).
 *
 * {@code locationId} nullable et distinct de {@code Schedule.locationId}
 * : c'est le « lieu effectif » de cette occurrence précise (peut
 * diverger du lieu par défaut du schedule — voir LL-11005, « 22
 * septembre → Marseille » alors que le schedule pointe Avignon — donc
 * pas fusionné avec {@code Schedule.locationId} au niveau du modèle).
 *
 * {@code status} : {@code SCHEDULED} par convention pour une occurrence
 * matérialisée normalement ; {@code CANCELLED} pour une occurrence
 * annulée (LL-11005, « 15 septembre → annulé »). Ce ticket ne fait que
 * réserver un statut libre (pas d'enum, même convention que
 * {@code Activity.status}) — la sémantique des valeurs autres que
 * {@code SCHEDULED} est définie par LL-11005.
 */
public record Occurrence(
        @Id Long id,
        Long scheduleId,
        Instant startAt,
        Instant endAt,
        Long locationId,
        String status) {
}
