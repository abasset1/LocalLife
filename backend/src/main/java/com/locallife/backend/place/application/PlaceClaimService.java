package com.locallife.backend.place.application;

import com.locallife.backend.place.domain.ClaimStatus;
import com.locallife.backend.place.domain.ClaimTransitions;
import com.locallife.backend.place.domain.ClaimTransitions.Action;
import com.locallife.backend.place.domain.Place;
import com.locallife.backend.place.domain.PlaceClaim;
import com.locallife.backend.place.domain.PlaceStatus;
import com.locallife.backend.place.infrastructure.PlaceClaimRepository;
import com.locallife.backend.place.infrastructure.PlaceClaimRepository.ClaimEvent;
import com.locallife.backend.place.infrastructure.PlaceManagerRepository;
import com.locallife.backend.place.infrastructure.PlaceManagerRepository.Manager;
import com.locallife.backend.place.infrastructure.PlaceRepository;
import com.locallife.backend.pro.application.ProAccessPolicy;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Demandes d'administration d'un lieu existant (ADR-0012, parcours A). Le professionnel rédige, soumet, complète
 * ou retire sa demande ; un administrateur la prend en charge, l'approuve, la refuse ou demande des informations.
 * Seule l'approbation ouvre un droit de gestion ; la fiche du lieu n'est jamais modifiée ici. Chaque transition est
 * tracée ({@code place_claim_event}).
 */
@Service
public class PlaceClaimService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlaceClaimService.class);

    static final int MAX_RELATIONSHIP_LENGTH = 2000;
    static final int MAX_EVIDENCE_LENGTH = 4000;
    static final int MAX_REASON_LENGTH = 2000;

    /** Lieux revendicables : importés ou créés, en attente ou publiés. */
    private static final Set<PlaceStatus> CLAIMABLE = EnumSet.of(PlaceStatus.PENDING, PlaceStatus.PUBLISHED);

    private final PlaceClaimRepository claimRepository;
    private final PlaceManagerRepository managerRepository;
    private final PlaceRepository placeRepository;
    private final ProAccessPolicy proAccessPolicy;

    public PlaceClaimService(PlaceClaimRepository claimRepository, PlaceManagerRepository managerRepository,
            PlaceRepository placeRepository, ProAccessPolicy proAccessPolicy) {
        this.claimRepository = claimRepository;
        this.managerRepository = managerRepository;
        this.placeRepository = placeRepository;
        this.proAccessPolicy = proAccessPolicy;
    }

    /** Demande vue par le modérateur : historique, demandes concurrentes sur le même lieu et gestionnaires actuels. */
    public record ClaimReview(
            PlaceClaim claim, Place place, List<ClaimEvent> events, List<PlaceClaim> concurrentClaims,
            List<Manager> managers) {
    }

    // ---------------------------------------------------------------- professionnel

    @Transactional
    public PlaceClaim create(Long userId, Long locationId, String relationship, String evidence) {
        requirePro(userId);
        Place place = placeRepository.findById(locationId)
                .filter(found -> found.status() != PlaceStatus.DRAFT)
                .orElseThrow(() -> ProActionException.notFound("Lieu introuvable."));
        if (!CLAIMABLE.contains(place.status())) {
            throw ProActionException.conflict("Ce lieu ne peut pas faire l'objet d'une demande de gestion.");
        }
        if (managerRepository.isManager(locationId, userId)) {
            throw ProActionException.conflict("Tu gères déjà ce lieu.");
        }
        Long claimId;
        try {
            claimId = claimRepository.insertDraft(locationId, userId,
                    text(relationship, MAX_RELATIONSHIP_LENGTH, "Le lien avec le lieu"),
                    text(evidence, MAX_EVIDENCE_LENGTH, "Les justificatifs"));
        } catch (DuplicateKeyException exception) {
            throw ProActionException.conflict("Une demande est déjà en cours pour ce lieu.");
        }
        claimRepository.insertEvent(claimId, userId, null, ClaimStatus.DRAFT, null);
        return claimRepository.findById(claimId).orElseThrow();
    }

    @Transactional
    public PlaceClaim update(Long userId, Long claimId, String relationship, String evidence) {
        PlaceClaim claim = ownClaim(userId, claimId);
        if (!ClaimTransitions.isEditable(claim.status())) {
            throw ProActionException.conflict("Cette demande ne peut plus être modifiée.");
        }
        claimRepository.updateText(claimId, text(relationship, MAX_RELATIONSHIP_LENGTH, "Le lien avec le lieu"),
                text(evidence, MAX_EVIDENCE_LENGTH, "Les justificatifs"));
        return claimRepository.findById(claimId).orElseThrow();
    }

    @Transactional
    public PlaceClaim submit(Long userId, Long claimId) {
        PlaceClaim claim = ownClaim(userId, claimId);
        if (claim.relationship() == null) {
            throw ProActionException.invalid("Explique ton lien avec le lieu avant d'envoyer la demande.");
        }
        return apply(claim, Action.SUBMIT, userId, null);
    }

    @Transactional
    public PlaceClaim withdraw(Long userId, Long claimId) {
        return apply(ownClaim(userId, claimId), Action.WITHDRAW, userId, null);
    }

    public List<PlaceClaim> findMine(Long userId) {
        requirePro(userId);
        return claimRepository.findByRequester(userId);
    }

    public PlaceClaim findMine(Long userId, Long claimId) {
        return ownClaim(userId, claimId);
    }

    // ---------------------------------------------------------------- modération (ADMIN)

    public List<PlaceClaim> queue(Collection<ClaimStatus> statuses, int limit, int offset) {
        return claimRepository.findByStatuses(statuses, limit, offset);
    }

    public ClaimReview review(Long claimId) {
        PlaceClaim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> ProActionException.notFound("Demande introuvable."));
        Place place = placeRepository.findById(claim.locationId()).orElseThrow();
        List<PlaceClaim> concurrent = claimRepository.findSubmittedForLocation(claim.locationId()).stream()
                .filter(other -> !other.id().equals(claimId)).toList();
        return new ClaimReview(claim, place, claimRepository.findEvents(claimId), concurrent,
                managerRepository.findActive(claim.locationId()));
    }

    /**
     * Décision ou prise en charge par un administrateur. Un administrateur ne statue jamais sur sa propre demande.
     * Approuver ouvre le droit de gestion au demandeur, sans toucher à la fiche du lieu ni aux autres demandes.
     */
    @Transactional
    public PlaceClaim moderate(Long adminId, Long claimId, Action action, String reason) {
        if (!action.isModeration()) {
            throw ProActionException.invalid("Action de modération inconnue.");
        }
        PlaceClaim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> ProActionException.notFound("Demande introuvable."));
        if (adminId.equals(claim.requesterId())) {
            throw ProActionException.forbidden("Un administrateur ne statue pas sur sa propre demande.");
        }
        String cleanReason = text(reason, MAX_REASON_LENGTH, "Le motif");
        if (ClaimTransitions.requiresReason(action) && cleanReason == null) {
            throw ProActionException.invalid("Un motif est obligatoire pour refuser ou demander des informations.");
        }
        if (action == Action.APPROVE && claim.requesterId() == null) {
            throw ProActionException.conflict("Le compte du demandeur n'existe plus.");
        }
        PlaceClaim updated = apply(claim, action, adminId, cleanReason);
        if (action == Action.APPROVE) {
            managerRepository.grant(claim.locationId(), claim.requesterId(), claimId, adminId);
        }
        LOGGER.info("Demande de gestion {} (lieu {}) : {} → {} par l'administrateur {}", claimId, claim.locationId(),
                claim.status(), updated.status(), adminId);
        return updated;
    }

    // ---------------------------------------------------------------- outils

    private PlaceClaim apply(PlaceClaim claim, Action action, Long actorId, String reason) {
        if (!ClaimTransitions.isAllowed(claim.status(), action)) {
            throw ProActionException.conflict("Action impossible pour une demande au statut " + claim.status() + ".");
        }
        ClaimStatus target = ClaimTransitions.target(action);
        if (!claimRepository.transition(claim.id(), claim.status(), target,
                action.isModeration() ? actorId : null, reason)) {
            throw ProActionException.conflict("La demande a changé entre-temps, recharge-la.");
        }
        claimRepository.insertEvent(claim.id(), actorId, claim.status(), target, reason);
        return claimRepository.findById(claim.id()).orElseThrow();
    }

    /** Demande du professionnel connecté ; celle d'un autre est introuvable pour lui. */
    private PlaceClaim ownClaim(Long userId, Long claimId) {
        requirePro(userId);
        return claimRepository.findById(claimId).filter(claim -> userId.equals(claim.requesterId()))
                .orElseThrow(() -> ProActionException.notFound("Demande introuvable."));
    }

    private void requirePro(Long userId) {
        if (!proAccessPolicy.hasProAccess(userId)) {
            throw ProActionException.forbidden("Compte professionnel requis.");
        }
    }

    /** Texte libre nettoyé : {@code null} si vide. */
    static String text(String value, int maxLength, String label) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw ProActionException.invalid(label + " ne doit pas dépasser " + maxLength + " caractères.");
        }
        return trimmed;
    }
}
