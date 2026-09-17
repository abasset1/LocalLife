package com.locallife.backend.like.api;

import com.locallife.backend.activity.api.ActivityResponse;
import com.locallife.backend.activity.domain.Activity;
import com.locallife.backend.auth.application.JwtAuthentication;
import com.locallife.backend.like.application.LikeService;
import com.locallife.backend.media.application.ActivityMediaService;
import com.locallife.backend.media.application.MediaService;
import com.locallife.backend.media.domain.ActivityMedia;
import com.locallife.backend.media.domain.Media;
import com.locallife.backend.source.application.SourceService;
import com.locallife.backend.source.domain.Source;
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
    private final MediaService mediaService;

    public LikeController(
            LikeService likeService, SourceService sourceService, ActivityMediaService activityMediaService,
            MediaService mediaService) {
        this.likeService = likeService;
        this.sourceService = sourceService;
        this.activityMediaService = activityMediaService;
        this.mediaService = mediaService;
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
     * résolu depuis LL-11015 (voir {@link #resolveMainImageUrl}, même
     * logique que {@code ActivityController#resolveMainImageUrl}).
     */
    @GetMapping
    public List<ActivityResponse> getLikedActivities(Authentication authentication) {
        Long userId = ((JwtAuthentication) authentication).getUserId();
        List<Activity> likedActivities = likeService.getLikedActivities(userId);

        Map<Long, String> sourceNamesById = sourceService.getAllSources().stream()
                .collect(Collectors.toMap(Source::id, Source::name));

        return likedActivities.stream()
                .map(activity -> ActivityResponse.from(
                        activity, sourceNamesById.getOrDefault(activity.sourceId(), UNKNOWN_SOURCE_NAME),
                        null, true, resolveMainImageUrl(activity.id())))
                .collect(Collectors.toList());
    }

    /**
     * Url du premier média attaché à une activité (LL-11015), ou
     * {@code null} si aucun média n'est attaché — copie volontaire de
     * {@code ActivityController#resolveMainImageUrl} (même logique,
     * même limitation assumée : une requête par activité, sans mise en
     * cache ni résolution en lot, voir sa javadoc) plutôt qu'une
     * factorisation entre les deux contrôleurs, pour rester cohérent
     * avec l'absence de classe de base partagée entre eux dans ce
     * projet.
     */
    private String resolveMainImageUrl(Long activityId) {
        return activityMediaService.findByActivityId(activityId).stream()
                .findFirst()
                .map(ActivityMedia::mediaId)
                .flatMap(mediaService::findById)
                .map(Media::url)
                .orElse(null);
    }
}
