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
        String source) {
}
