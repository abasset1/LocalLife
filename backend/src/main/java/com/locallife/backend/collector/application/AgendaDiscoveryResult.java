package com.locallife.backend.collector.application;

import java.util.List;

/**
 * Résultat d'une découverte d'agendas ({@link AgendaDiscoveryService#discoverOfficialAgendas}).
 *
 * @param listed       agendas renvoyés par OpenAgenda (au plus la limite demandée)
 * @param created      sources créées
 * @param alreadyKnown agendas déjà présents comme source (active ou non), laissés tels quels
 * @param createdNames noms des sources créées, dans l'ordre de création
 */
public record AgendaDiscoveryResult(int listed, int created, int alreadyKnown, List<String> createdNames) {
}
