package com.locallife.backend.place.api;

import com.locallife.backend.place.application.PlaceMatchReportService.Match;
import com.locallife.backend.place.application.PlaceMatchReportService.Report;
import com.locallife.backend.place.domain.PlaceMatcher.Level;
import java.util.List;
import java.util.Map;

/** Rapport de rapprochement (lecture seule, ADR-0011, § 2.3). */
public record PlaceMatchResponse(
        Map<Level, Integer> pairsByLevel, Map<Level, Integer> eventLocationsByLevel,
        Map<Level, Integer> activitiesByLevel, List<Item> matches) {

    /**
     * Paire classée : lieu d'événement (et nombre d'activités qui l'utilisent), lieu permanent, distance.
     * {@code linkedPlaceId} : lieu permanent auquel le lieu d'événement est déjà relié, ou {@code null}.
     */
    public record Item(
            String level, String reason, long distanceMeters,
            Long eventLocationId, String eventLocationName, String eventLocationAddress, String eventLocationCity,
            int activityCount,
            Long placeId, String placeName, String placeAddress, String placeType, String placeStatus,
            Long linkedPlaceId) {
    }

    static PlaceMatchResponse from(Report report) {
        return new PlaceMatchResponse(report.pairsByLevel(), report.eventLocationsByLevel(),
                report.activitiesByLevel(), report.matches().stream().map(PlaceMatchResponse::item).toList());
    }

    private static Item item(Match match) {
        var pair = match.pair();
        return new Item(match.level().name(), match.reason(), match.distanceMeters(),
                pair.eventLocationId(), pair.eventLocation().name(), pair.eventLocation().address(), pair.eventCity(),
                pair.activityCount(),
                pair.placeId(), pair.place().name(), pair.place().address(), pair.placeType(), pair.placeStatus(),
                pair.linkedPlaceId());
    }
}
