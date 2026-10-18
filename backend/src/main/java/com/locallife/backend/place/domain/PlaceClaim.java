package com.locallife.backend.place.domain;

import java.time.OffsetDateTime;

/**
 * Demande d'administration d'un lieu existant (ADR-0012, § 3.1), avec le nom du lieu et le demandeur pour
 * l'affichage. {@code relationship} et {@code evidence} sont nuls une fois effacés ({@code evidencePurgedAt}).
 */
public record PlaceClaim(
        Long id,
        Long locationId,
        String placeName,
        String placeCity,
        Long requesterId,
        String requesterEmail,
        ClaimStatus status,
        String relationship,
        String evidence,
        String decisionReason,
        Long moderatorId,
        OffsetDateTime createdAt,
        OffsetDateTime submittedAt,
        OffsetDateTime decidedAt,
        OffsetDateTime updatedAt,
        OffsetDateTime evidencePurgedAt) {
}
