package com.locallife.backend.collector.domain;

import java.time.LocalDateTime;

/**
 * Modèle interne représentant une donnée collectée avant conversion vers
 * {@link com.locallife.backend.activity.domain.Activity} (LL-5004). C'est
 * le type de donnée « brute normalisable » retourné par un
 * {@code Collector} (voir {@code COLLECTOR_CONTRACT.md}, LL-5003).
 *
 * Simple porteur de données, comme {@code Activity} et {@code Source} :
 * aucune validation ici. La conversion vers {@code Activity} et le rejet
 * des données invalides relèvent du pipeline de normalisation (LL-5005),
 * pas de ce modèle.
 *
 * {@code source} identifie la source par son nom (voir la décision
 * {@code getSourceName()} de {@code COLLECTOR_CONTRACT.md}), pas par un
 * identifiant technique — ce modèle n'a pas connaissance de la
 * persistance.
 *
 * {@code address}/{@code city}/{@code postalCode} ajoutés en LL-EF-008 :
 * repris tels quels de l'objet {@code location} de la source (ex.
 * OpenAgenda, voir {@code OpenAgendaCollector}), au même titre que
 * {@code sourceUrl} (LL-6002) — voir {@code Activity} pour le détail de
 * leur usage après normalisation. Tous trois optionnels : une source peut
 * ne pas les fournir (ex. lieu imprécis).
 *
 * {@code longDescription}/{@code conditions}/{@code ageMin}/{@code
 * ageMax} ajoutés en LL-11006 : même principe, repris tels quels côté
 * collecteur (une seule langue déjà retenue, comme {@code description}) —
 * voir {@code Activity} pour la justification du choix d'une forme
 * générique plutôt que calquée sur OpenAgenda.
 */
public record CollectedActivity(
        String title,
        String description,
        LocalDateTime startDate,
        LocalDateTime endDate,
        String category,
        double latitude,
        double longitude,
        String sourceUrl,
        String externalId,
        String source,
        String address,
        String city,
        String postalCode,
        String longDescription,
        String conditions,
        Integer ageMin,
        Integer ageMax) {
}
