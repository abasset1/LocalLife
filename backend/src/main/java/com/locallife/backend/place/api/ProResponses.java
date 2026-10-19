package com.locallife.backend.place.api;

import com.locallife.backend.common.ErrorResponse;
import com.locallife.backend.place.application.PlaceClaimService.ClaimReview;
import com.locallife.backend.pro.application.ProActionException;
import com.locallife.backend.place.application.ProPlaceService.SimilarResult;
import com.locallife.backend.place.domain.ClaimStatus;
import com.locallife.backend.place.domain.PlaceClaim;
import com.locallife.backend.place.domain.PlaceStatus;
import com.locallife.backend.place.infrastructure.PlaceClaimRepository.ClaimEvent;
import com.locallife.backend.place.infrastructure.PlaceManagerRepository.Manager;
import com.locallife.backend.place.infrastructure.ProPlaceRepository.ManagedPlace;
import com.locallife.backend.place.infrastructure.ProPlaceRepository.SimilarPlace;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Réponses de l'espace professionnel et de la modération des revendications (ADR-0012). */
final class ProResponses {

    private ProResponses() {
    }

    /** Demande vue par son auteur : sans l'identité du modérateur. */
    record ClaimResponse(
            Long id, Long placeId, String placeName, String placeCity, ClaimStatus status, String relationship,
            String evidence, String decisionReason, OffsetDateTime createdAt, OffsetDateTime submittedAt,
            OffsetDateTime decidedAt, OffsetDateTime updatedAt, boolean evidencePurged) {

        static ClaimResponse from(PlaceClaim claim) {
            return new ClaimResponse(claim.id(), claim.locationId(), claim.placeName(), claim.placeCity(),
                    claim.status(), claim.relationship(), claim.evidence(), claim.decisionReason(), claim.createdAt(),
                    claim.submittedAt(), claim.decidedAt(), claim.updatedAt(), claim.evidencePurgedAt() != null);
        }
    }

    /** Demande vue par un modérateur. */
    record AdminClaimResponse(
            Long id, Long placeId, String placeName, String placeCity, Long requesterId, String requesterEmail,
            ClaimStatus status, String relationship, String evidence, String decisionReason, Long moderatorId,
            OffsetDateTime createdAt, OffsetDateTime submittedAt, OffsetDateTime decidedAt, OffsetDateTime updatedAt,
            boolean evidencePurged) {

        static AdminClaimResponse from(PlaceClaim claim) {
            return new AdminClaimResponse(claim.id(), claim.locationId(), claim.placeName(), claim.placeCity(),
                    claim.requesterId(), claim.requesterEmail(), claim.status(), claim.relationship(),
                    claim.evidence(), claim.decisionReason(), claim.moderatorId(), claim.createdAt(),
                    claim.submittedAt(), claim.decidedAt(), claim.updatedAt(), claim.evidencePurgedAt() != null);
        }
    }

    record ClaimEventResponse(
            Long actorId, ClaimStatus fromStatus, ClaimStatus toStatus, String reason, OffsetDateTime occurredAt) {

        static ClaimEventResponse from(ClaimEvent event) {
            return new ClaimEventResponse(event.actorId(), event.fromStatus(), event.toStatus(), event.reason(),
                    event.occurredAt());
        }
    }

    record ManagerResponse(Long userId, String email, Long claimId, OffsetDateTime grantedAt) {

        static ManagerResponse from(Manager manager) {
            return new ManagerResponse(manager.userId(), manager.email(), manager.claimId(), manager.grantedAt());
        }
    }

    /** Dossier d'une demande pour le modérateur. */
    record ClaimReviewResponse(
            AdminClaimResponse claim, PlaceResponse place, PlaceStatus placeStatus, List<ClaimEventResponse> events,
            List<AdminClaimResponse> concurrentClaims, List<ManagerResponse> managers) {

        static ClaimReviewResponse from(ClaimReview review) {
            return new ClaimReviewResponse(AdminClaimResponse.from(review.claim()), PlaceResponse.from(review.place()),
                    review.place().status(), review.events().stream().map(ClaimEventResponse::from).toList(),
                    review.concurrentClaims().stream().map(AdminClaimResponse::from).toList(),
                    review.managers().stream().map(ManagerResponse::from).toList());
        }
    }

    /**
     * Lieu vu par un de ses gestionnaires. {@code origin} : {@code CREATED} (créé par un professionnel) ou
     * {@code IMPORTED} (fiche importée, gérée après revendication) ; {@code editable} : modifiable maintenant.
     */
    record ManagedPlaceResponse(
            Long id, String name, String placeType, PlaceStatus status, String address, String postalCode, String city,
            Double latitude, Double longitude, String description, String website, String phone, String email,
            String origin, boolean editable, Long reportedSimilarTo) {

        static ManagedPlaceResponse from(ManagedPlace managed) {
            var place = managed.place();
            boolean editable = !managed.imported()
                    && (place.status() == PlaceStatus.DRAFT || place.status() == PlaceStatus.PENDING);
            return new ManagedPlaceResponse(place.id(), place.name(), place.placeType(), place.status(),
                    place.address(), place.postalCode(), place.city(), place.latitude(), place.longitude(),
                    place.description(), place.website(), managed.phone(), managed.email(),
                    managed.imported() ? "IMPORTED" : "CREATED", editable, managed.reportedSimilarTo());
        }
    }

    record SimilarPlaceResponse(PlaceResponse place, PlaceStatus status, Double distanceMeters) {

        static SimilarPlaceResponse from(SimilarPlace similar) {
            return new SimilarPlaceResponse(PlaceResponse.from(similar.place()), similar.place().status(),
                    similar.distanceMeters() == null ? null : (double) Math.round(similar.distanceMeters()));
        }
    }

    record SimilarResponse(Double latitude, Double longitude, List<SimilarPlaceResponse> candidates) {

        static SimilarResponse from(SimilarResult result) {
            return new SimilarResponse(result.latitude(), result.longitude(),
                    result.candidates().stream().map(SimilarPlaceResponse::from).toList());
        }
    }

    /** Création refusée faute d'avoir écarté les lieux proches : le message et les candidats. */
    record SimilarConflictResponse(
            Instant timestamp, int status, String error, String message, String path,
            List<SimilarPlaceResponse> candidates) {
    }

    static ResponseEntity<Object> error(ProActionException exception, HttpServletRequest request) {
        HttpStatus status = switch (exception.reason()) {
            case INVALID -> HttpStatus.BAD_REQUEST;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
        return error(status, exception.getMessage(), request);
    }

    static ResponseEntity<Object> error(HttpStatus status, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(new ErrorResponse(
                Instant.now(), status.value(), status.getReasonPhrase(), message, request.getRequestURI()));
    }
}
