package com.locallife.backend.activity.api;

import com.locallife.backend.activity.application.ActivitySearchService.SearchResult;
import com.locallife.backend.activity.application.SearchRelevance;
import com.locallife.backend.activity.infrastructure.ActivitySearchRepository.SearchRow;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Réponse de {@code GET /api/v1/activities/search} (#91) : page de résultats et total. DTO dédié, distinct
 * de {@link ActivitySummaryResponse} (carte et liste), pour ne pas en changer le contrat.
 */
public record ActivitySearchResponse(List<Item> items, int limit, int offset, long total) {

    static ActivitySearchResponse from(SearchResult result) {
        return new ActivitySearchResponse(
                result.page().rows().stream().map(Item::from).toList(), result.limit(), result.offset(),
                result.page().total());
    }

    /**
     * Résultat. Lieu, coordonnées, dates et distance sont ceux du lieu effectif retenu (occurrence la plus
     * proche de la position de référence, sinon la plus tôt) ; champs de lieu {@code null} s'ils sont
     * inconnus. {@code distanceMeters} est {@code null} sans position de référence ou sans position
     * effective.
     */
    public record Item(
            Long id, String title, String description, String category, List<String> categoryCodes, String city,
            String address, Double latitude, Double longitude, LocalDateTime startDate, LocalDateTime endDate,
            SearchRelevance relevance, Long distanceMeters) {

        static Item from(SearchRow row) {
            return new Item(
                    row.id(), row.title(), row.description(), row.category(), row.categoryCodes(), row.city(),
                    row.address(), row.latitude(), row.longitude(), row.startDate(), row.endDate(),
                    SearchRelevance.fromRank(row.relevanceRank()),
                    row.distanceMeters() == null ? null : Math.round(row.distanceMeters()));
        }
    }
}
