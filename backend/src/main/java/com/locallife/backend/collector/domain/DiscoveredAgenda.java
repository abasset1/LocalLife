package com.locallife.backend.collector.domain;

/**
 * Agenda OpenAgenda trouvé par la découverte (voir {@code OpenAgendaAgendaDirectory}).
 *
 * @param uid      identifiant numérique de l'agenda, sous forme de chaîne comme {@code Source.agendaUid}
 * @param title    titre affiché de l'agenda, peut être {@code null}
 * @param slug     identifiant lisible, utilisé dans l'URL publique de l'agenda, peut être {@code null}
 * @param official agenda marqué officiel par OpenAgenda ; {@code null} si la réponse ne le dit pas
 */
public record DiscoveredAgenda(String uid, String title, String slug, Boolean official) {

    public DiscoveredAgenda(String uid, String title, String slug) {
        this(uid, title, slug, null);
    }
}
