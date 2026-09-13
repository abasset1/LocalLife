package com.locallife.backend.collector.domain;

import java.time.LocalDateTime;

/**
 * Un horaire (créneau) collecté (LL-11011, {@code SPRINT_11.md}, section
 * 14 : « plusieurs horaires supportés »). {@code CollectedActivity} porte
 * désormais une liste de {@code CollectedTiming} plutôt qu'un unique
 * couple {@code startDate}/{@code endDate} — une source (ex. OpenAgenda)
 * peut fournir plusieurs créneaux pour un même événement (répétitions
 * déjà « à plat », voir la javadoc d'{@code OpenAgendaCollector}), chacun
 * devenant une {@link com.locallife.backend.schedule.domain.Occurrence}
 * à la persistance (voir {@code NormalizationService#normalizeOccurrences}).
 *
 * {@code end} nullable (un créneau peut ne pas avoir d'heure de fin
 * connue, même tolérance que {@link
 * com.locallife.backend.schedule.domain.Schedule#endTime()}).
 */
public record CollectedTiming(LocalDateTime begin, LocalDateTime end) {
}
