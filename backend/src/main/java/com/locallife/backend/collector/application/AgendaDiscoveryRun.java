package com.locallife.backend.collector.application;

import com.locallife.backend.collector.domain.DiscoveryScope;
import java.time.LocalDateTime;

/**
 * Parcours de l'annuaire (table {@code agenda_discovery_run}, V68), avancé par lots de pages et reprenable
 * depuis {@code cursor} tant qu'il est {@link Status#IN_PROGRESS}.
 *
 * @param cursor curseur OpenAgenda de la dernière page traitée (JSON), {@code null} avant la première page
 */
public record AgendaDiscoveryRun(
        Long id,
        DiscoveryScope scope,
        Status status,
        String cursor,
        int pagesRead,
        int examined,
        int newCount,
        int alreadyKnown,
        int ignored,
        int errors,
        Integer providerTotal,
        String stopReason,
        LocalDateTime startedAt,
        LocalDateTime updatedAt,
        LocalDateTime endedAt) {

    /**
     * {@code IN_PROGRESS} : reprenable ; {@code COMPLETE} : marqueur de fin du fournisseur atteint ;
     * {@code FAILED} : anomalie de pagination, non reprenable (relancer un parcours) ; {@code ABANDONED} :
     * remplacé par un nouveau parcours.
     */
    public enum Status { IN_PROGRESS, COMPLETE, FAILED, ABANDONED }
}
