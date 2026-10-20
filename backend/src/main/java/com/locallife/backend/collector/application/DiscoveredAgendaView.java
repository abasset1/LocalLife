package com.locallife.backend.collector.application;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Aperçu du catalogue des agendas découverts (AGD-03), avant toute création de source.
 *
 * @param items lignes de la page demandée
 * @param total nombre de lignes correspondant au filtre, toutes pages confondues
 */
public record DiscoveredAgendaView(List<Item> items, long total, int page, int size) {

    /**
     * @param url          URL publique de l'agenda, {@code null} sans slug
     * @param sourceId     source déjà créée pour cet agenda, {@code null} s'il n'en a pas
     * @param sourceStatus statut de cette source ({@code ACTIVE}, {@code INACTIVE}...), {@code null} sans source
     */
    public record Item(
            String agendaUid,
            String title,
            String url,
            Boolean official,
            LocalDateTime firstSeenAt,
            LocalDateTime lastSeenAt,
            Long sourceId,
            String sourceName,
            String sourceStatus) {
    }
}
