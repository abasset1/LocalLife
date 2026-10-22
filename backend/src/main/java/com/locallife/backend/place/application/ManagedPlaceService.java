package com.locallife.backend.place.application;

import com.locallife.backend.place.application.ProPlaceService.PlaceInput;
import com.locallife.backend.place.domain.PlaceStatus;
import com.locallife.backend.place.infrastructure.PlaceChangeRepository;
import com.locallife.backend.place.infrastructure.PlaceChangeRepository.ChangeFields;
import com.locallife.backend.place.infrastructure.PlaceChangeRepository.ChangeStatus;
import com.locallife.backend.place.infrastructure.PlaceChangeRepository.PlaceChange;
import com.locallife.backend.place.infrastructure.PlaceManagerRepository;
import com.locallife.backend.place.infrastructure.PlaceManagerRepository.Manager;
import com.locallife.backend.place.infrastructure.PlaceManagerRepository.RevokeReason;
import com.locallife.backend.place.infrastructure.ProPlaceRepository;
import com.locallife.backend.place.infrastructure.ProPlaceRepository.ManagedPlace;
import com.locallife.backend.place.infrastructure.ProPlaceRepository.ProPlaceFields;
import com.locallife.backend.pro.application.ProActionException;
import com.locallife.backend.pro.application.ProNotifier;
import com.locallife.backend.pro.infrastructure.ProContentRepository;
import java.util.Collection;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lieux publiés gérés par un professionnel (décisions du 22/10/2026, D5) :
 * <ul>
 *   <li>le gestionnaire propose une modification de l'adresse, de la position, de la description ou des contacts ;
 *       la fiche publiée ne change qu'à la validation par un administrateur (jamais le demandeur lui-même), et la
 *       modification validée prime ensuite sur les réimports ;</li>
 *   <li>un gestionnaire peut renoncer à un lieu, un administrateur peut lui retirer ce droit (motif obligatoire).
 *       Plusieurs gestionnaires par lieu restent possibles (D3).</li>
 * </ul>
 */
@Service
public class ManagedPlaceService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ManagedPlaceService.class);

    private final ProPlaceService proPlaceService;
    private final ProPlaceRepository proPlaceRepository;
    private final PlaceChangeRepository changeRepository;
    private final PlaceManagerRepository managerRepository;
    private final ProContentRepository proContentRepository;
    private final ProNotifier notifier;

    public ManagedPlaceService(ProPlaceService proPlaceService, ProPlaceRepository proPlaceRepository,
            PlaceChangeRepository changeRepository, PlaceManagerRepository managerRepository,
            ProContentRepository proContentRepository, ProNotifier notifier) {
        this.proPlaceService = proPlaceService;
        this.proPlaceRepository = proPlaceRepository;
        this.changeRepository = changeRepository;
        this.managerRepository = managerRepository;
        this.proContentRepository = proContentRepository;
        this.notifier = notifier;
    }

    /** Proposition vue par le modérateur, avec la fiche actuelle pour comparer. */
    public record ChangeReview(PlaceChange change, ManagedPlace current, List<Manager> managers) {
    }

    // ---------------------------------------------------------------- gestionnaire

    /**
     * Propose une modification d'un lieu publié qu'il gère ; une proposition déjà en attente est remplacée. Nom et
     * catégorie ne se modifient pas ici.
     */
    @Transactional
    public PlaceChange propose(Long userId, Long placeId, PlaceInput input) {
        ManagedPlace managed = proPlaceService.managedPlace(userId, placeId);
        if (managed.place().status() != PlaceStatus.PUBLISHED) {
            throw ProActionException.conflict("Seul un lieu publié se modifie par une proposition ; un brouillon "
                    + "ou un lieu en attente se modifie directement.");
        }
        if (input == null) {
            throw ProActionException.invalid("Corps de requête manquant.");
        }
        ProPlaceFields fields = proPlaceService.validate(new PlaceInput(managed.place().name(),
                managed.place().placeType(), input.address(), input.postalCode(), input.city(), input.latitude(),
                input.longitude(), input.description(), input.website(), input.phone(), input.email()));
        Long id;
        try {
            id = changeRepository.upsertPending(placeId, userId, new ChangeFields(fields.address(),
                    fields.postalCode(), fields.city(), fields.latitude(), fields.longitude(), fields.description(),
                    fields.website(), fields.phone(), fields.email()));
        } catch (DuplicateKeyException exception) {
            throw ProActionException.conflict("Une proposition vient d'être envoyée, recharge la page.");
        }
        LOGGER.info("Proposition de modification {} du lieu {} par le gestionnaire {}", id, placeId, userId);
        return changeRepository.findById(id).orElseThrow();
    }

    public List<PlaceChange> findChanges(Long userId, Long placeId) {
        proPlaceService.managedPlace(userId, placeId);
        return changeRepository.findForPlace(placeId, userId);
    }

    @Transactional
    public PlaceChange withdraw(Long userId, Long changeId) {
        PlaceChange change = changeRepository.findById(changeId).filter(found -> userId.equals(found.requesterId()))
                .orElseThrow(() -> ProActionException.notFound("Proposition introuvable."));
        if (!changeRepository.close(changeId, ChangeStatus.WITHDRAWN, null, null)) {
            throw ProActionException.conflict("Cette proposition n'est plus en attente.");
        }
        return changeRepository.findById(change.id()).orElseThrow();
    }

    /** Le gestionnaire renonce au lieu : ses propositions en attente sont retirées. */
    @Transactional
    public void renounce(Long userId, Long placeId) {
        proPlaceService.managedPlace(userId, placeId);
        managerRepository.revoke(placeId, userId, RevokeReason.RENOUNCED, userId, null);
        proContentRepository.withdrawPlaceChanges(userId, placeId);
        LOGGER.info("Le gestionnaire {} renonce au lieu {}", userId, placeId);
    }

    // ---------------------------------------------------------------- administration

    public List<PlaceChange> queue(Collection<ChangeStatus> statuses, int limit, int offset) {
        return changeRepository.findByStatuses(statuses, limit, offset);
    }

    public ChangeReview review(Long changeId) {
        PlaceChange change = changeRepository.findById(changeId)
                .orElseThrow(() -> ProActionException.notFound("Proposition introuvable."));
        return new ChangeReview(change, proPlaceRepository.findPlace(change.locationId()).orElseThrow(),
                managerRepository.findActive(change.locationId()));
    }

    /** Validation : la fiche publiée prend les valeurs proposées. Jamais par le demandeur lui-même. */
    @Transactional
    public PlaceChange approve(Long adminId, Long changeId) {
        PlaceChange change = pendingForModeration(adminId, changeId);
        if (change.requesterId() == null || !managerRepository.isManager(change.locationId(), change.requesterId())) {
            throw ProActionException.conflict("Le demandeur ne gère plus ce lieu.");
        }
        if (!changeRepository.close(changeId, ChangeStatus.APPROVED, adminId, null)) {
            throw ProActionException.conflict("Cette proposition n'est plus en attente.");
        }
        changeRepository.applyToPlace(change.locationId(), change.fields());
        LOGGER.info("Proposition {} validée par l'administrateur {} : lieu {} modifié", changeId, adminId,
                change.locationId());
        notifier.notify(change.requesterId(), "Modification de « " + change.placeName() + " » publiée",
                "L'équipe LocalLife a validé ta proposition : la fiche de « " + change.placeName()
                        + " » est à jour.", null);
        return changeRepository.findById(changeId).orElseThrow();
    }

    @Transactional
    public PlaceChange reject(Long adminId, Long changeId, String reason) {
        PlaceChange change = pendingForModeration(adminId, changeId);
        String cleanReason = PlaceClaimService.text(reason, PlaceClaimService.MAX_REASON_LENGTH, "Le motif");
        if (cleanReason == null) {
            throw ProActionException.invalid("Un motif est obligatoire pour refuser une proposition.");
        }
        if (!changeRepository.close(changeId, ChangeStatus.REJECTED, adminId, cleanReason)) {
            throw ProActionException.conflict("Cette proposition n'est plus en attente.");
        }
        notifier.notify(change.requesterId(), "Modification de « " + change.placeName() + " » refusée",
                "L'équipe LocalLife n'a pas validé ta proposition de modification de « " + change.placeName()
                        + " ».", cleanReason);
        return changeRepository.findById(changeId).orElseThrow();
    }

    /** Retire à un gestionnaire le droit de gérer un lieu ; motif obligatoire, journalisé sur le droit révoqué. */
    @Transactional
    public void revokeManager(Long adminId, Long placeId, Long userId, String reason) {
        String cleanReason = PlaceClaimService.text(reason, PlaceClaimService.MAX_REASON_LENGTH, "Le motif");
        if (cleanReason == null) {
            throw ProActionException.invalid("Un motif est obligatoire pour retirer un gestionnaire.");
        }
        if (!managerRepository.revoke(placeId, userId, RevokeReason.ADMIN, adminId, cleanReason)) {
            throw ProActionException.notFound("Ce compte ne gère pas ce lieu.");
        }
        proContentRepository.withdrawPlaceChanges(userId, placeId);
        LOGGER.info("Gestionnaire {} retiré du lieu {} par l'administrateur {}", userId, placeId, adminId);
        String place = proPlaceRepository.findPlace(placeId).map(found -> "« " + found.place().name() + " »")
                .orElse("un lieu");
        notifier.notify(userId, "Tu ne gères plus " + place,
                "L'équipe LocalLife t'a retiré la gestion de " + place + ".", cleanReason);
    }

    public List<Manager> managers(Long placeId) {
        proPlaceRepository.findPlace(placeId).orElseThrow(() -> ProActionException.notFound("Lieu introuvable."));
        return managerRepository.findActive(placeId);
    }

    private PlaceChange pendingForModeration(Long adminId, Long changeId) {
        PlaceChange change = changeRepository.findById(changeId)
                .orElseThrow(() -> ProActionException.notFound("Proposition introuvable."));
        if (adminId.equals(change.requesterId())) {
            throw ProActionException.forbidden("Un administrateur ne valide pas sa propre proposition.");
        }
        if (change.status() != ChangeStatus.PENDING) {
            throw ProActionException.conflict("Cette proposition n'est plus en attente.");
        }
        return change;
    }
}
