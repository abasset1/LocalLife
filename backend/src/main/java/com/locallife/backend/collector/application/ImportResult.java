package com.locallife.backend.collector.application;

/**
 * Récapitulatif du résultat d'un import pour une source donnée (LL-5008).
 * Simple porteur de données, retourné par {@code ImportService} — la
 * journalisation détaillée (compteurs affichés/stockés) relève de
 * LL-5009, pas de ce ticket.
 */
public record ImportResult(String sourceName, int created, int updated, int archived, int rejected) {
}
