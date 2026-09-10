package com.locallife.backend.schedule.domain;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;

/**
 * Entité de domaine Occurrence (LL-11004/LL-11005, Sprint 11) : une
 * occurrence concrète d'un {@link Schedule} — voir
 * {@code docs/05_Sprints/SPRINT_11.md}, sections 7 et 8. Un
 * {@code Schedule} « tous les mardis 11:30 → 14:00 » se matérialise en
 * plusieurs {@code Occurrence} (08/09/2026, 15/09/2026, 22/09/2026...) ;
 * chaque {@code Occurrence} correspond typiquement à l'une des dates
 * renvoyées par {@code RecurrenceRuleService#computeOccurrenceDates}
 * (LL-11003). Ce ticket ne fait que poser le modèle ; le service qui
 * matérialise effectivement des {@code Occurrence} à partir d'un
 * {@code Schedule} (appelant {@code RecurrenceRuleService}) est du
 * ressort d'un ticket ultérieur, non encore traité à ce stade du
 * sprint.
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
 * annulée (LL-11005, « 15 septembre → annulé »). Pas d'enum (même
 * convention que {@code Activity.status}).
 *
 * {@code exceptional} (LL-11005, critère d'acceptation « une exception
 * est identifiable ») : distingue explicitement une occurrence qui
 * diverge de ce que la récurrence de son {@code Schedule} produirait
 * normalement (annulée, déplacée, réhoraire, relocalisée — les 4 cas du
 * ticket, tous représentés directement par des valeurs de
 * {@code startAt}/{@code endAt}/{@code locationId}/{@code status}
 * différentes de ce que {@code Schedule} donnerait par défaut, sans
 * modéliser de « type » d'exception séparé) d'une occurrence «
 * normale » (LL-11005, « 29 septembre → normal », {@code exceptional
 * == false}). Modifier ou annuler une {@code Occurrence} ne modifie
 * jamais {@link Schedule} (tables et entités entièrement séparées,
 * critère d'acceptation « elle ne détruit pas la règle générale ») :
 * {@code exceptional} vit uniquement sur la ligne {@code Occurrence}
 * concernée.
 */
public record Occurrence(
        @Id Long id,
        Long scheduleId,
        Instant startAt,
        Instant endAt,
        Long locationId,
        String status,
        @Column("is_exception") boolean exceptional) {
}
