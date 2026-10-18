package com.locallife.backend.place.api;

import com.locallife.backend.auth.application.JwtAuthentication;
import com.locallife.backend.place.api.ProResponses.ClaimResponse;
import com.locallife.backend.place.api.ProResponses.ManagedPlaceResponse;
import com.locallife.backend.place.api.ProResponses.SimilarConflictResponse;
import com.locallife.backend.place.api.ProResponses.SimilarPlaceResponse;
import com.locallife.backend.place.api.ProResponses.SimilarResponse;
import com.locallife.backend.place.application.PlaceClaimService;
import com.locallife.backend.place.application.ProActionException;
import com.locallife.backend.place.application.ProPlaceService;
import com.locallife.backend.place.application.ProPlaceService.PlaceInput;
import com.locallife.backend.place.application.ProPlaceService.SimilarPlacesFoundException;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Espace professionnel (ADR-0012) : demandes de gestion d'un lieu existant (parcours A) et lieux créés par le
 * professionnel (parcours B). Connexion exigée ({@code SecurityConfig}) ; le droit professionnel et la gestion de
 * chaque lieu sont vérifiés dans les services, à chaque appel : 403 sans compte professionnel, 404 pour la demande
 * ou le lieu d'un autre.
 */
@RestController
@RequestMapping("/api/v1/pro")
public class ProPlaceController {

    private final PlaceClaimService claimService;
    private final ProPlaceService placeService;

    public ProPlaceController(PlaceClaimService claimService, ProPlaceService placeService) {
        this.claimService = claimService;
        this.placeService = placeService;
    }

    /** Texte d'une demande de gestion. */
    public record ClaimBody(String relationship, String evidence) {
    }

    /**
     * Lieu à créer ou à modifier. {@code confirmNotDuplicate} : le professionnel a vu les lieux proches et
     * confirme que le sien n'en fait pas partie ; {@code reportedSimilarTo} : lieu proche signalé au modérateur.
     */
    public record PlaceBody(
            String name, String placeType, String address, String postalCode, String city, Double latitude,
            Double longitude, String description, String website, String phone, String email,
            Boolean confirmNotDuplicate, Long reportedSimilarTo) {

        PlaceInput input() {
            return new PlaceInput(name, placeType, address, postalCode, city, latitude, longitude, description,
                    website, phone, email);
        }
    }

    // ---------------------------------------------------------------- parcours A : demandes de gestion

    @Operation(summary = "Demander la gestion d'un lieu existant",
            description = "Crée la demande en brouillon (envoi : POST /pro/claims/{id}/submit). Ne modifie ni le "
                    + "lieu ni ses droits. 409 si une demande est déjà en cours ou si le lieu est déjà géré par toi.")
    @PostMapping("/places/{placeId}/claims")
    public ResponseEntity<Object> createClaim(@PathVariable Long placeId, @RequestBody(required = false) ClaimBody body,
            Authentication authentication, HttpServletRequest request) {
        ClaimBody claim = body == null ? new ClaimBody(null, null) : body;
        return handle(request, () -> ResponseEntity.status(HttpStatus.CREATED).body(ClaimResponse.from(
                claimService.create(userId(authentication), placeId, claim.relationship(), claim.evidence()))));
    }

    @Operation(summary = "Mes demandes de gestion")
    @GetMapping("/claims")
    public ResponseEntity<Object> myClaims(Authentication authentication, HttpServletRequest request) {
        return handle(request, () -> ResponseEntity.ok(
                claimService.findMine(userId(authentication)).stream().map(ClaimResponse::from).toList()));
    }

    @Operation(summary = "Une de mes demandes de gestion")
    @GetMapping("/claims/{id}")
    public ResponseEntity<Object> myClaim(@PathVariable Long id, Authentication authentication,
            HttpServletRequest request) {
        return handle(request, () -> ResponseEntity.ok(ClaimResponse.from(
                claimService.findMine(userId(authentication), id))));
    }

    @Operation(summary = "Compléter une demande", description = "En brouillon ou après une demande d'informations.")
    @PatchMapping("/claims/{id}")
    public ResponseEntity<Object> updateClaim(@PathVariable Long id, @RequestBody(required = false) ClaimBody body,
            Authentication authentication, HttpServletRequest request) {
        ClaimBody claim = body == null ? new ClaimBody(null, null) : body;
        return handle(request, () -> ResponseEntity.ok(ClaimResponse.from(claimService.update(
                userId(authentication), id, claim.relationship(), claim.evidence()))));
    }

    @Operation(summary = "Envoyer une demande à la modération")
    @PostMapping("/claims/{id}/submit")
    public ResponseEntity<Object> submitClaim(@PathVariable Long id, Authentication authentication,
            HttpServletRequest request) {
        return handle(request, () -> ResponseEntity.ok(ClaimResponse.from(
                claimService.submit(userId(authentication), id))));
    }

    @Operation(summary = "Retirer une demande")
    @PostMapping("/claims/{id}/withdraw")
    public ResponseEntity<Object> withdrawClaim(@PathVariable Long id, Authentication authentication,
            HttpServletRequest request) {
        return handle(request, () -> ResponseEntity.ok(ClaimResponse.from(
                claimService.withdraw(userId(authentication), id))));
    }

    // ---------------------------------------------------------------- parcours B : lieux

    @Operation(summary = "Catégories proposées pour un nouveau lieu")
    @GetMapping("/place-types")
    public List<String> placeTypes() {
        return ProPlaceService.PLACE_TYPES;
    }

    @Operation(summary = "Lieux existants proches d'un lieu à créer",
            description = "À appeler avant la création : lieux à moins de 100 m de l'adresse (localisée si la "
                    + "position n'est pas fournie) ou de même nom dans la commune. Renvoie aussi la position retenue.")
    @GetMapping("/places/similar")
    public ResponseEntity<Object> similar(
            @RequestParam(required = false) String name, @RequestParam(required = false) String address,
            @RequestParam(required = false) String postalCode, @RequestParam(required = false) String city,
            @RequestParam(required = false) Double latitude, @RequestParam(required = false) Double longitude,
            Authentication authentication, HttpServletRequest request) {
        return handle(request, () -> ResponseEntity.ok(SimilarResponse.from(placeService.findSimilar(
                userId(authentication), name, address, postalCode, city, latitude, longitude))));
    }

    @Operation(summary = "Créer un lieu",
            description = "Crée le lieu en brouillon, géré par son créateur (envoi : POST /pro/places/{id}/submit). "
                    + "409 avec les lieux proches tant que confirmNotDuplicate n'est pas vrai.")
    @PostMapping("/places")
    public ResponseEntity<Object> create(@RequestBody(required = false) PlaceBody body,
            Authentication authentication, HttpServletRequest request) {
        if (body == null) {
            return ProResponses.error(HttpStatus.BAD_REQUEST, "Corps de requête manquant.", request);
        }
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(ManagedPlaceResponse.from(placeService.create(
                    userId(authentication), body.input(), Boolean.TRUE.equals(body.confirmNotDuplicate()),
                    body.reportedSimilarTo())));
        } catch (SimilarPlacesFoundException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new SimilarConflictResponse(Instant.now(),
                    HttpStatus.CONFLICT.value(), HttpStatus.CONFLICT.getReasonPhrase(), exception.getMessage(),
                    request.getRequestURI(),
                    exception.candidates().stream().map(SimilarPlaceResponse::from).toList()));
        } catch (ProActionException exception) {
            return ProResponses.error(exception, request);
        }
    }

    @Operation(summary = "Lieux que je gère", description = "Créés par moi ou obtenus par une demande approuvée.")
    @GetMapping("/places")
    public ResponseEntity<Object> myPlaces(Authentication authentication, HttpServletRequest request) {
        return handle(request, () -> ResponseEntity.ok(
                placeService.findManaged(userId(authentication)).stream().map(ManagedPlaceResponse::from).toList()));
    }

    @Operation(summary = "Aperçu d'un lieu que je gère", description = "Quel que soit son statut.")
    @GetMapping("/places/{id}")
    public ResponseEntity<Object> myPlace(@PathVariable Long id, Authentication authentication,
            HttpServletRequest request) {
        return handle(request, () -> ResponseEntity.ok(ManagedPlaceResponse.from(
                placeService.findManaged(userId(authentication), id))));
    }

    @Operation(summary = "Modifier un lieu que j'ai créé", description = "En brouillon ou en attente de modération.")
    @PutMapping("/places/{id}")
    public ResponseEntity<Object> update(@PathVariable Long id, @RequestBody(required = false) PlaceBody body,
            Authentication authentication, HttpServletRequest request) {
        if (body == null) {
            return ProResponses.error(HttpStatus.BAD_REQUEST, "Corps de requête manquant.", request);
        }
        return handle(request, () -> ResponseEntity.ok(ManagedPlaceResponse.from(
                placeService.update(userId(authentication), id, body.input()))));
    }

    @Operation(summary = "Envoyer un lieu à la modération", description = "Brouillon → en attente de modération.")
    @PostMapping("/places/{id}/submit")
    public ResponseEntity<Object> submit(@PathVariable Long id, Authentication authentication,
            HttpServletRequest request) {
        return handle(request, () -> ResponseEntity.ok(ManagedPlaceResponse.from(
                placeService.submit(userId(authentication), id))));
    }

    private static ResponseEntity<Object> handle(HttpServletRequest request, Supplier<ResponseEntity<Object>> action) {
        try {
            return action.get();
        } catch (ProActionException exception) {
            return ProResponses.error(exception, request);
        }
    }

    private static Long userId(Authentication authentication) {
        return ((JwtAuthentication) authentication).getUserId();
    }
}
