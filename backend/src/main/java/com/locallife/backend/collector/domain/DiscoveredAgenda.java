package com.locallife.backend.collector.domain;

/**
 * Agenda OpenAgenda trouvé par la découverte (voir {@code OpenAgendaAgendaDirectory}).
 *
 * @param uid            identifiant numérique de l'agenda, sous forme de chaîne comme {@code Source.agendaUid}
 * @param title          titre affiché de l'agenda, peut être {@code null}
 * @param slug           identifiant lisible, utilisé dans l'URL publique de l'agenda, peut être {@code null}
 * @param official       agenda marqué officiel par OpenAgenda ; {@code null} si la réponse ne le dit pas
 * @param currentEvents  événements publiés en cours ({@code summary.publishedEvents.current}) ; {@code null} si la
 *                       réponse ne le dit pas
 * @param upcomingEvents événements publiés à venir ({@code summary.publishedEvents.upcoming}) ; {@code null} si la
 *                       réponse ne le dit pas
 */
public record DiscoveredAgenda(
        String uid, String title, String slug, Boolean official, Integer currentEvents, Integer upcomingEvents) {

    public DiscoveredAgenda(String uid, String title, String slug, Boolean official) {
        this(uid, title, slug, official, null, null);
    }

    public DiscoveredAgenda(String uid, String title, String slug) {
        this(uid, title, slug, null);
    }
}
