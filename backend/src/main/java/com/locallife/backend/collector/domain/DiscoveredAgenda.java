package com.locallife.backend.collector.domain;

/**
 * Agenda OpenAgenda trouvé par la découverte automatique (voir {@code OpenAgendaAgendaDirectory}).
 *
 * @param uid   identifiant numérique de l'agenda, sous forme de chaîne comme {@code Source.agendaUid}
 * @param title titre affiché de l'agenda, peut être {@code null}
 * @param slug  identifiant lisible, utilisé dans l'URL publique de l'agenda, peut être {@code null}
 */
public record DiscoveredAgenda(String uid, String title, String slug) {
}
