package com.locallife.backend.like.api;

import com.locallife.backend.activity.api.ActivityResponse;
import com.locallife.backend.activity.domain.Activity;
import com.locallife.backend.activity.infrastructure.ActivityCoordinatePrecisionRepository;
import com.locallife.backend.activity.infrastructure.ActivityNextOccurrenceRepository;
import com.locallife.backend.auth.application.JwtAuthentication;
import com.locallife.backend.like.application.LikeService;
import com.locallife.backend.media.application.ActivityMediaService;
import com.locallife.backend.place.domain.CoordinatePrecision;
import com.locallife.backend.source.application.SourceService;
import com.locallife.backend.source.domain.Source;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contrôleur REST pour la consultation des favoris (LS-002, section
 * « Consulter ses événements likés »). Les actions de like/unlike
 * elles-mêmes vivent sur {@code ActivityController}
 * ({@code POST}/{@code DELETE .../activities/{id}/like}) — sous-ressources
 * naturelles d'une activité précise, même patron que
 * {@code ActivityController#cancelActivity} (LL-EF-010) — alors que cet
 * endpoint-ci porte sur l'utilisateur courant, même convention {@code
 * .../me/...} que {@code UserController#getCurrentUser} (LL-EF-006).
 */
@RestController
@RequestMapping("/api/v1/users/me/likes")
public class LikeController {

    private static final String UNKNOWN_SOURCE_NAME = "Source inconnue";

    private final LikeService likeService;
    private final SourceService sourceService;
    private final ActivityMediaService activityMediaService;

    private final ActivityCoordinatePrecisionRepository coordinatePrecisionRepository;
    private final ActivityNextOccurrenceRepository nextOccurrenceRepository;

    public LikeController(
            LikeService likeService, SourceService sourceService, ActivityMediaService activityMediaService,
            ActivityCoordinatePrecisionRepository coordinatePrecisionRepository,
            ActivityNextOccurrenceRepository nextOccurrenceRepository) {
        this.likeService = likeService;
        this.sourceService = sourceService;
        this.activityMediaService = activityMediaService;
        this.coordinatePrecisionRepository = coordinatePrecisionRepository;
        this.nextOccurrenceRepository = nextOccurrenceRepository;
    }

    /**
     * Liste des activités actuellement likées par l'utilisateur connecté
     * (critère d'acceptation « les événements likés sont accessibles
     * depuis l'interface utilisateur » / « la liste [...] est
     * persistante »). {@code id} provient exclusivement du JWT, jamais de
     * la requête — même garantie que {@code UserController#getCurrentUser}.
     *
     * {@link ActivityResponse} réutilisé tel quel (mêmes champs que
     * {@code /nearby}/{@code /within-bounds}, y compris
     * {@code sourceName}/{@code createdByUsername}/
     * {@code likedByCurrentUser} — ce dernier vaut ici toujours
     * {@code true} par construction, mais reste peuplé pour que le
     * frontend n'ait pas à distinguer cette réponse des autres) plutôt
     * qu'une projection dédiée : les informations attendues par le
     * ticket (nom, date, ville, catégorie, image éventuelle) en sont
     * déjà toutes un sous-ensemble — {@code mainImageUrl} effectivement
     * résolu depuis LL-11015, en une requête pour toute la liste
     * ({@code ActivityMediaService#findMainImageUrls}).
     */
    @GetMapping
    public List<ActivityResponse> getLikedActivities(Authentication authentication) {
        Long userId = ((JwtAuthentication) authentication).getUserId();
        List<Activity> likedActivities = likeService.getLikedActivities(userId);

        Map<Long, String> sourceNamesById = sourceService.getAllSources().stream()
                .collect(Collectors.toMap(Source::id, Source::name));

        Map<Long, CoordinatePrecision> precisions = coordinatePrecisionRepository.findByActivityIds(
                likedActivities.stream().map(Activity::id).toList());
        // Date affichée : prochaine occurrence plutôt que la première date (voir ActivityNextOccurrenceRepository).
        Map<Long, LocalDateTime> nextStarts = nextOccurrenceRepository.findNextStartByActivityIds(
                likedActivities.stream().map(Activity::id).toList(), null);

        Map<Long, String> mainImageUrls = activityMediaService.findMainImageUrls(
                likedActivities.stream().map(Activity::id).toList());

        return likedActivities.stream()
                .map(activity -> ActivityResponse.from(
                        activity, sourceNamesById.getOrDefault(activity.sourceId(), UNKNOWN_SOURCE_NAME),
                        null, true, mainImageUrls == null ? null : mainImageUrls.get(activity.id()))
                        .withCoordinatePrecision(precisions == null ? null : precisions.get(activity.id()))
                        .withNextOccurrenceStart(nextStarts == null ? null : nextStarts.get(activity.id())))
                .collect(Collectors.toList());
    }
}
