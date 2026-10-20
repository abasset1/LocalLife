package com.locallife.backend.collector.application;

/**
 * Statut d'un import de source (AGD-05).
 *
 * <ul>
 *   <li>{@link #SUCCESS} : collecte complète (fin réelle des données atteinte) ; seul cas où l'archivage des
 *       éléments disparus est appliqué et où {@code Source.lastSyncAt} avance ;</li>
 *   <li>{@link #PARTIAL} : collecte interrompue avant la fin (limite de sécurité, curseur répété, échec en
 *       cours de pagination, réponse incohérente) ; les éléments reçus sont importés, rien n'est archivé ;</li>
 *   <li>{@link #FAILED} : aucune donnée exploitable ; rien n'est créé, modifié ni archivé.</li>
 * </ul>
 */
public enum SyncStatus {
    SUCCESS,
    PARTIAL,
    FAILED
}
