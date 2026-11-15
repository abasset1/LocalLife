package com.locallife.backend.activity.application;

import com.locallife.backend.activity.domain.Activity;
import com.locallife.backend.activity.infrastructure.ActivityRepository;
import com.locallife.backend.media.infrastructure.ActivityPhotoRepository;
import com.locallife.backend.media.infrastructure.ActivityPhotoRepository.Photo;
import com.locallife.backend.user.domain.Role;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Image d'une activité, ajoutée par son contributeur depuis le formulaire « Proposer une activité » (site et
 * application). Mêmes règles d'accès que la modification ({@link ContributionService#findManageable}) : son
 * créateur ou un administrateur, jamais une activité importée. JPEG, PNG ou WebP, 3 Mo au plus (les clients la
 * réduisent avant l'envoi). Changée par son contributeur, une activité publiée ou refusée repart en validation.
 *
 * <p>Visibilité : l'image d'une activité en attente, refusée ou à la corbeille n'est servie qu'à son créateur et
 * à l'équipe (modération), comme l'activité elle-même.
 */
@Service
public class ActivityPhotoService {

    /** Taille maximale d'une image, une fois décodée. */
    static final int MAX_PHOTO_BYTES = 3 * 1024 * 1024;

    private static final String TOO_LARGE = "L'image ne doit pas dépasser 3 Mo.";
    private static final Set<String> EDITABLE_STATUSES = Set.of("PENDING", "PUBLISHED", "REJECTED");
    private static final Set<String> HIDDEN_STATUSES = Set.of("PENDING", "REJECTED", "DELETED");

    private final ActivityPhotoRepository photoRepository;
    private final ActivityRepository activityRepository;
    private final ContributionService contributionService;

    public ActivityPhotoService(ActivityPhotoRepository photoRepository, ActivityRepository activityRepository,
            ContributionService contributionService) {
        this.photoRepository = photoRepository;
        this.activityRepository = activityRepository;
        this.contributionService = contributionService;
    }

    /**
     * Ajoute ou remplace l'image d'une contribution.
     *
     * @return {@code false} si aucune activité ne correspond à {@code id}
     * @throws IllegalArgumentException image manquante, illisible, trop lourde ou d'un format refusé ; activité
     *     importée ou plus modifiable
     * @throws ActivityAccessDeniedException ni créateur ni administrateur
     */
    @Transactional
    public boolean save(Long id, Long requesterId, boolean requesterIsAdmin, String encoded) {
        Optional<Activity> activity = editable(id, requesterId, requesterIsAdmin);
        if (activity.isEmpty()) {
            return false;
        }
        byte[] data = decode(encoded);
        photoRepository.save(id, contentType(data), data);
        if (!requesterIsAdmin) {
            photoRepository.requireRevalidation(id);
        }
        return true;
    }

    /**
     * Retire l'image d'une contribution (sans effet si elle n'en a pas).
     *
     * @return {@code false} si aucune activité ne correspond à {@code id}
     */
    @Transactional
    public boolean delete(Long id, Long requesterId, boolean requesterIsAdmin) {
        Optional<Activity> activity = editable(id, requesterId, requesterIsAdmin);
        if (activity.isEmpty()) {
            return false;
        }
        if (photoRepository.delete(id) && !requesterIsAdmin) {
            photoRepository.requireRevalidation(id);
        }
        return true;
    }

    /**
     * Image à servir, vide si l'activité n'en a pas ou si le demandeur ne peut pas la voir.
     *
     * @param requesterId {@code null} pour un visiteur
     * @param role        {@code null} pour un visiteur
     */
    public Optional<Photo> findVisible(Long id, Long requesterId, Role role) {
        Optional<Activity> activity = activityRepository.findById(id);
        if (activity.isEmpty()) {
            return Optional.empty();
        }
        boolean hidden = HIDDEN_STATUSES.contains(activity.get().status());
        boolean privileged = (role != null && role.isStaff())
                || (requesterId != null && requesterId.equals(activity.get().createdBy()));
        return hidden && !privileged ? Optional.empty() : photoRepository.find(id);
    }

    private Optional<Activity> editable(Long id, Long requesterId, boolean requesterIsAdmin) {
        Optional<Activity> activity = contributionService.findManageable(id, requesterId, requesterIsAdmin);
        activity.ifPresent(found -> {
            if (!EDITABLE_STATUSES.contains(found.status())) {
                throw new IllegalArgumentException("L'activité " + id + " ne peut plus être modifiée (statut : "
                        + found.status() + ").");
            }
        });
        return activity;
    }

    static byte[] decode(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Image manquante.");
        }
        String encoded = value.trim();
        int comma = encoded.indexOf(',');
        if (encoded.startsWith("data:") && comma > 0) {
            encoded = encoded.substring(comma + 1);
        }
        // Estimation avant décodage : 4 caractères base64 pour 3 octets.
        if ((long) encoded.length() * 3 / 4 > MAX_PHOTO_BYTES + 3) {
            throw new IllegalArgumentException(TOO_LARGE);
        }
        byte[] data;
        try {
            data = Base64.getMimeDecoder().decode(encoded);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Image illisible.");
        }
        if (data.length == 0) {
            throw new IllegalArgumentException("Image manquante.");
        }
        if (data.length > MAX_PHOTO_BYTES) {
            throw new IllegalArgumentException(TOO_LARGE);
        }
        return data;
    }

    /** Type de l'image d'après ses premiers octets : JPEG, PNG ou WebP, sinon refusée. */
    static String contentType(byte[] data) {
        if (data.length > 3 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8 && (data[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (data.length > 8 && (data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G') {
            return "image/png";
        }
        if (data.length > 12 && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P') {
            return "image/webp";
        }
        throw new IllegalArgumentException("Format d'image non pris en charge : JPEG, PNG ou WebP.");
    }
}
