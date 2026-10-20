package com.locallife.backend.collector.domain;

/**
 * Périmètre de l'annuaire OpenAgenda parcouru par la découverte (AGD-01).
 *
 * <ul>
 *   <li>{@link #ALL} : tous les agendas que l'endpoint de recherche {@code GET /v2/agendas} renvoie avec la
 *       clé utilisée, sans filtre ; c'est le sens retenu pour « tous les agendas accessibles » ;</li>
 *   <li>{@link #OFFICIAL} : seulement les agendas marqués officiels ({@code official=1}), le comportement
 *       historique.</li>
 * </ul>
 * Ni l'un ni l'autre ne couvre les agendas privés auxquels la clé n'a pas accès : l'API ne les liste pas.
 */
public enum DiscoveryScope {
    ALL,
    OFFICIAL
}
