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
 *       d'{@code ignored}, qui est un rejet « normal » et anticipé ;</li>
 *   <li>{@code archiveWithheld} : activités absentes de la collecte mais
 *       <b>non</b> archivées, parce que la disparition d'activités à venir
 *       était trop massive pour être crédible (garde-fou d'{@code
 *       ImportService}) ; 0 quand l'archivage a eu lieu normalement ;</li>
 *   <li>{@code blockedReason} : pourquoi l'import a été bloqué, en clair —
 *       collecte en échec (rien n'est alors ni créé, ni mis à jour, ni
 *       archivé) ou archivage suspendu par le garde-fou ; {@code null} quand
 *       rien n'a été bloqué.</li>
 *   <li>{@code status} (AGD-05) : {@code SUCCESS} si la collecte est
 *       complète, {@code PARTIAL} si elle s'est arrêtée avant la fin réelle
 *       des données (aucun archivage alors), {@code FAILED} si rien n'a pu
 *       être collecté — voir {@link SyncStatus} ;</li>
 *   <li>{@code pagesRead}/{@code received} : pages lues et éléments bruts
 *       reçus de la source, avant les filtres du collecteur ({@code fetched}
 *       compte les éléments retenus après ces filtres) ;</li>
 *   <li>{@code stopReason} : raison de l'arrêt de la collecte, sans donnée
 *       sensible (fin de pagination atteinte, limite de sécurité, curseur
 *       répété, échec...).</li>
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
        int needsReview,
        int unchanged,
        int duplicates,
        int archiveWithheld,
        String blockedReason,
        SyncStatus status,
        int pagesRead,
        int received,
        String stopReason) {

    /**
     * Résultat d'une collecte complète sans élément inchangé ni doublon ({@code unchanged = 0},
     * {@code duplicates = 0}).
     */
    public ImportResult(
            String sourceName, LocalDateTime startedAt, LocalDateTime endedAt, int fetched, int created,
            int updated, int ignored, int errors, int archived, int needsReview) {
        this(sourceName, startedAt, endedAt, fetched, created, updated, ignored, errors, archived, needsReview, 0,
                0, 0, null, SyncStatus.SUCCESS, 1, fetched, null);
    }

    /** Durée de l'import, en millisecondes. */
    public long durationMs() {
        return startedAt == null || endedAt == null ? 0 : java.time.Duration.between(startedAt, endedAt).toMillis();
    }
}
