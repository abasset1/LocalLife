package com.locallife.backend.schedule.domain;

import java.time.LocalDate;
import java.time.LocalTime;
import org.springframework.data.annotation.Id;

/**
 * Entité de domaine Schedule (LL-11002, Sprint 11) : la programmation
 * d'une activité — voir {@code docs/05_Sprints/SPRINT_11.md}, section 5.
 * Référence {@code Activity} et {@code Location} par id uniquement
 * ({@code activityId}/{@code locationId}), pas de relation objet — même
 * convention que {@code Activity.sourceId}, voir {@code ARCHITECTURE.md}
 * (« pas de dépendance vers un autre module, sauf référence par id »).
 * Nouveau module {@code schedule} (aucun scaffold réservé existant,
 * contrairement à {@code place}/LL-11001).
 *
 * Un même modèle représente aussi bien un événement ponctuel qu'une
 * programmation répétée (critères d'acceptation du ticket) : la
 * récurrence elle-même ({@code recurrenceRule}, format RRULE) est
 * délibérément laissée opaque ici et sera définie/validée par LL-11003
 * (« implémenter les règles de récurrence »— dépendance explicite de ce
 * ticket) ; ce ticket-ci ne fait que réserver le champ pour la
 * persister. {@code recurrenceRule == null} représente un événement
 * ponctuel : {@code validFrom}/{@code validUntil} désignent alors la
 * même date unique (la « période de validité » se réduit à un jour).
 * Pour une programmation répétée, {@code validFrom}/{@code validUntil}
 * bornent la fenêtre sur laquelle la règle de récurrence s'applique
 * ({@code validUntil == null} : pas de date de fin connue, cas explicite
 * de LL-11003 — « absence de date de fin lorsque la source le permet »).
 * Les occurrences concrètes (dates effectives calculées à partir de ce
 * modèle) sont du ressort de LL-11004 (Occurrence), pas de ce ticket.
 *
 * {@code timezone} porté par Schedule et non uniquement par
 * {@code Location} (qui a pourtant déjà son propre champ
 * {@code timezone}, LL-11001) : un lieu peut ne pas avoir de fuseau
 * connu ({@code Location.timezone} nullable), et {@code startTime}/
 * {@code endTime} doivent rester interprétables même sans lieu résolu
 * ({@code locationId} nullable, voir plus bas) — c'est explicitement
 * une des choses qu'« un Schedule doit pouvoir représenter » selon le
 * ticket.
 *
 * Tous les champs nullables sauf {@code id}/{@code activityId} (un
 * schedule appartient toujours à une activité, {@code NOT NULL} +
 * contrainte {@code FOREIGN KEY} en base, voir
 * {@code V18__create_schedule_table.sql}) : même tolérance aux données
 * incomplètes qu'ailleurs dans le projet (ex. {@code Location}, LL-11001)
 * — une source peut donner un jour et un lieu sans horaire précis, ou un
 * horaire sans lieu résolu, sans que cela invalide le schedule pour
 * autant.
 */
public record Schedule(
        @Id Long id,
        Long activityId,
        Long locationId,
        LocalTime startTime,
        LocalTime endTime,
        LocalDate validFrom,
        LocalDate validUntil,
        String timezone,
        String recurrenceRule) {
}
