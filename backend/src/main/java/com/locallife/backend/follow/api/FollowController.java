package com.locallife.backend.follow.api;

import com.locallife.backend.auth.application.JwtAuthentication;
import com.locallife.backend.follow.application.FollowService;
import com.locallife.backend.follow.domain.FollowTargetType;
import com.locallife.backend.follow.domain.FollowedItem;
import io.swagger.v3.oas.annotations.Operation;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cloche « Suivre » : suivre une activité ou un lieu pour être prévenu de ses mises à jour (Premium et Pro, 403 pour un
 * compte gratuit), et liste des suivis de l'utilisateur connecté. L'identifiant de l'utilisateur vient du JWT.
 */
@RestController
@RequestMapping("/api/v1")
public class FollowController {

    private final FollowService followService;

    public FollowController(FollowService followService) {
        this.followService = followService;
    }

    /** Une activité ou un lieu suivi. {@code type} : {@code activity} ou {@code place}. */
    public record FollowedItemResponse(String type, Long id, String title, String city, String status,
            OffsetDateTime followedAt) {

        static FollowedItemResponse from(FollowedItem item) {
            return new FollowedItemResponse(item.type() == FollowTargetType.ACTIVITY ? "activity" : "place",
                    item.targetId(), item.title(), item.city(), item.status(), item.followedAt());
        }
    }

    public record FollowStatusResponse(boolean following) {
    }

    @Operation(summary = "Activités et lieux suivis par l'utilisateur connecté, du plus récent au plus ancien")
    @GetMapping("/users/me/follows")
    public List<FollowedItemResponse> findFollowed(Authentication authentication) {
        return followService.findFollowed(userId(authentication)).stream().map(FollowedItemResponse::from).toList();
    }

    @Operation(summary = "Suivre une activité",
            description = "Idempotent. 404 si l'activité n'existe pas ou n'est pas visible du public ; 403 pour un "
                    + "compte gratuit.")
    @PostMapping("/activities/{id}/follow")
    public ResponseEntity<FollowStatusResponse> followActivity(@PathVariable Long id, Authentication authentication) {
        return follow(FollowTargetType.ACTIVITY, id, authentication);
    }

    @Operation(summary = "Ne plus suivre une activité", description = "Idempotent, quelle que soit l'offre.")
    @DeleteMapping("/activities/{id}/follow")
    public FollowStatusResponse unfollowActivity(@PathVariable Long id, Authentication authentication) {
        followService.unfollow(userId(authentication), FollowTargetType.ACTIVITY, id);
        return new FollowStatusResponse(false);
    }

    @Operation(summary = "Suivre un lieu",
            description = "Idempotent. 404 si le lieu n'existe pas ou n'est pas publié ; 403 pour un compte gratuit.")
    @PostMapping("/places/{id}/follow")
    public ResponseEntity<FollowStatusResponse> followPlace(@PathVariable Long id, Authentication authentication) {
        return follow(FollowTargetType.PLACE, id, authentication);
    }

    @Operation(summary = "Ne plus suivre un lieu", description = "Idempotent, quelle que soit l'offre.")
    @DeleteMapping("/places/{id}/follow")
    public FollowStatusResponse unfollowPlace(@PathVariable Long id, Authentication authentication) {
        followService.unfollow(userId(authentication), FollowTargetType.PLACE, id);
        return new FollowStatusResponse(false);
    }

    private ResponseEntity<FollowStatusResponse> follow(FollowTargetType type, Long id, Authentication authentication) {
        if (!followService.follow(userId(authentication), type, id)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(new FollowStatusResponse(true));
    }

    private static Long userId(Authentication authentication) {
        return ((JwtAuthentication) authentication).getUserId();
    }
}
