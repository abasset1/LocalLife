package com.locallife.backend.collector.application;

import com.locallife.backend.collector.domain.DiscoveryScope;
import java.time.LocalDateTime;

/**
 * Bilan d'un lot de découverte ({@link AgendaDiscoveryService#discover}) : état du parcours après ce lot.
 * Les compteurs sont cumulés sur tout le parcours, depuis sa première page.
 *
 * @param complete       vrai seulement si le marqueur de fin de l'annuaire a été atteint ({@code COMPLETE})
 * @param resumable      vrai si un nouvel appel reprend ce parcours là où il s'est arrêté ({@code IN_PROGRESS})
 * @param stopReason     raison de l'arrêt de ce lot, en clair, sans donnée sensible
 * @param pagesThisBatch pages lues par ce lot
 * @param pagesRead      pages lues depuis le début du parcours
 * @param examined       agendas renvoyés par l'API (identifiants absents compris)
 * @param newAgendas     agendas entrés au catalogue pendant ce parcours
 * @param alreadyKnown   agendas déjà au catalogue (lus lors d'un parcours précédent ou deux fois dans celui-ci)
 * @param ignored        agendas sans identifiant, non enregistrables
 * @param errors         appels en échec après reprises (chacun a interrompu un lot)
 * @param providerTotal  total annoncé par OpenAgenda, {@code null} s'il n'en annonce pas
 */
public record AgendaDiscoveryResult(
        Long runId,
        DiscoveryScope scope,
        AgendaDiscoveryRun.Status status,
        boolean complete,
        boolean resumable,
        String stopReason,
        int pagesThisBatch,
        int pagesRead,
        int examined,
        int newAgendas,
        int alreadyKnown,
        int ignored,
        int errors,
        Integer providerTotal,
        LocalDateTime startedAt,
        LocalDateTime updatedAt,
        LocalDateTime endedAt) {

    static AgendaDiscoveryResult of(AgendaDiscoveryRun run, int pagesThisBatch) {
        return new AgendaDiscoveryResult(run.id(), run.scope(), run.status(),
                run.status() == AgendaDiscoveryRun.Status.COMPLETE,
                run.status() == AgendaDiscoveryRun.Status.IN_PROGRESS,
                run.stopReason(), pagesThisBatch, run.pagesRead(), run.examined(), run.newCount(), run.alreadyKnown(),
                run.ignored(), run.errors(), run.providerTotal(), run.startedAt(), run.updatedAt(), run.endedAt());
    }
}
