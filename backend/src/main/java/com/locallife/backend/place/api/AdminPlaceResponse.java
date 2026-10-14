package com.locallife.backend.place.api;

import com.locallife.backend.place.domain.PlaceFlag;
import com.locallife.backend.place.infrastructure.PlaceRepository.ModerationItem;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

/** Lieu de la file de modération, avec ses signalements (ADR-0011, phase B). */
public record AdminPlaceResponse(
        Long id, String name, String placeType, String status, String address, String postalCode, String city,
        Double latitude, Double longitude, String description, String website, String coordinatePrecision,
        Set<PlaceFlag> flags, String sourceName, OffsetDateTime publishedAt, OffsetDateTime moderatedAt) {

    static AdminPlaceResponse from(ModerationItem item) {
        var place = item.place();
        return new AdminPlaceResponse(place.id(), place.name(), place.placeType(), place.status().name(),
                place.address(), place.postalCode(), place.city(), place.latitude(), place.longitude(),
                place.description(), place.website(), place.coordinatePrecision().name(), item.flags(),
                item.sourceName(), place.publishedAt(), place.moderatedAt());
    }

    /** Page de la file de modération. */
    public record Page(List<AdminPlaceResponse> items, long total, int limit, int offset) {
    }

    /** Résultat d'une action de modération : ids demandés et ids effectivement modifiés. */
    public record ModerationResult(List<Long> requested, List<Long> applied) {
    }

    /** Action groupée : {@code action} vaut {@code PUBLISH} ou {@code REJECT}. */
    public record BulkModerationRequest(List<Long> ids, String action) {
    }
}
