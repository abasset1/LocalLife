package com.locallife.backend.collector.application;

import java.util.List;

/**
 * Bilan d'une création de sources depuis le catalogue ({@link AgendaActivationService#createSources}).
 *
 * @param requested    identifiants distincts demandés
 * @param created      sources créées
 * @param alreadyKnown agendas qui avaient déjà une source, laissée telle quelle (même inactive)
 * @param notFound     identifiants absents du catalogue des agendas découverts, rien créé
 */
public record AgendaActivationResult(
        int requested, List<CreatedSource> created, List<String> alreadyKnown, List<String> notFound) {

    public record CreatedSource(Long sourceId, String name, String agendaUid) {
    }
}
