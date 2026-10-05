package com.locallife.backend.collector.application;

import java.time.LocalDateTime;

/**
 * Récapitulatif du résultat d'un import pour une source donnée. Contient
 * au minimum les champs demandés par LL-5009 (source, début, fin, nombre
 * récupéré/créé/mis à jour/ignoré/en erreur) ; {@code archived} est un
 * ajout au-delà du minimum (décision de suppression douce, LL-5008) —
 * « au minimum » n'interdit pas d'autres champs utiles.
 *
 * <ul>
 *   <li>{@code fetched} : nombre d'éléments retournés par
 *       {@code Collector#collect()} ;</li>
 *   <li>{@code ignored} : parmi ceux-ci, nombre rejetés par
 *       {@code NormalizationService} (donnée invalide) ;</li>
 *   <li>{@code needsReview} (CAT-008) : nombre de nouveaux éléments valides
 *       mais non publiés faute de catégorie V1 décidable ({@code NEEDS_REVIEW}
 *       ou {@code UNCLASSIFIED}) — consignés dans {@code classification_record}
 *       pour relecture, <b>distincts d'{@code ignored}</b> (donnée invalide) ;
 *       {@code fetched} = {@code created} + {@code updated} + {@code ignored}
 *       + {@code needsReview} + {@code errors} ;</li>
 *   <li>{@code errors} : nombre d'éléments dont le traitement a levé une
 *       exception inattendue (voir {@code ImportService}) — distinct
 *       d'{@code ignored}, qui est un rejet « normal » et anticipé.</li>
 * </ul>
 */
public record ImportResult(
        String sourceName,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        int fetched,
        int created,
        int updated,
        int ignored,
        int errors,
        int archived,
        int needsReview) {
}
