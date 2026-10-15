package com.locallife.backend.place.api;

import com.locallife.backend.place.application.EventLocationDuplicateReportService.Cluster;
import com.locallife.backend.place.application.EventLocationDuplicateReportService.Report;
import com.locallife.backend.place.domain.PlaceMatcher.Level;
import com.locallife.backend.place.infrastructure.EventLocationDuplicateRepository.EventLocation;
import java.util.List;
import java.util.Map;

/** Rapport des doublons de lieux d'événement (lecture seule, ADR-0011, § 7.2.13). */
public record EventLocationDuplicateResponse(
        Map<Level, Integer> clustersByLevel, Map<Level, Integer> locationsByLevel,
        Map<Level, Integer> extraRowsByLevel, Map<Level, Integer> activitiesByLevel, List<Group> clusters) {

    /** Grappe : un même lieu réel, plusieurs lignes ; la première est la plus ancienne. */
    public record Group(String level, long maxDistanceMeters, int activityCount, List<Item> locations) {
    }

    /** Lieu d'événement de la grappe ; {@code linkedPlaceId} : lieu permanent relié (V63), ou {@code null}. */
    public record Item(Long id, String name, String address, String postalCode, String city,
            String coordinatePrecision, int activityCount, Long linkedPlaceId) {
    }

    static EventLocationDuplicateResponse from(Report report) {
        return new EventLocationDuplicateResponse(report.clustersByLevel(), report.locationsByLevel(),
                report.extraRowsByLevel(), report.activitiesByLevel(),
                report.clusters().stream().map(EventLocationDuplicateResponse::group).toList());
    }

    private static Group group(Cluster cluster) {
        return new Group(cluster.level().name(), cluster.maxDistanceMeters(), cluster.activityCount(),
                cluster.locations().stream().map(EventLocationDuplicateResponse::item).toList());
    }

    private static Item item(EventLocation location) {
        return new Item(location.id(), location.candidate().name(), location.candidate().address(),
                location.candidate().postalCode(), location.city(), location.candidate().precision().name(),
                location.activityCount(), location.linkedPlaceId());
    }
}
