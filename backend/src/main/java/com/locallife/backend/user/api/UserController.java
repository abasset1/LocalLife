package com.locallife.backend.user.api;

import com.locallife.backend.auth.api.UserResponse;
import com.locallife.backend.auth.application.JwtAuthentication;
import com.locallife.backend.common.ErrorResponse;
import com.locallife.backend.user.application.UserService;
import com.locallife.backend.user.domain.User;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contrôleur REST pour la gestion des utilisateurs.
 * Création et consultation par id (réservées au rôle ADMIN, voir
 * {@code SecurityConfig}), et depuis LL-EF-006, consultation/modification
 * de son propre profil ({@code GET}/{@code PATCH .../me}).
 *
 * Correctif (fuite signalée en LL-3010) : les réponses exposaient
 * auparavant l'entité {@code User} complète, y compris {@code
 * passwordHash} (le hash BCrypt, jamais du texte en clair, mais qui ne
 * doit jamais transiter dans une réponse API). Les deux endpoints
 * utilisent désormais {@link UserResponse}, la même projection sûre déjà
 * utilisée par {@code POST /api/v1/auth/register} (LL-3007).
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<UserResponse> createUser(@RequestBody CreateUserRequest request) {
        User user = userService.createUser(request.username(), request.email());
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(user));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long id) {
        Optional<User> user = userService.getUserById(id);
        return user.map(u -> ResponseEntity.ok(UserResponse.from(u)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    /**
     * Profil de l'utilisateur actuellement connecté (LL-EF-006, critère
     * d'acceptation « un utilisateur connecté peut accéder à son
     * interface utilisateur » / « les informations de son compte sont
     * affichées »). {@code id} n'est jamais lu depuis la requête : il
     * provient exclusivement du JWT validé par {@code JwtFilter}
     * ({@link JwtAuthentication#getUserId()}) — un utilisateur ne peut
     * donc consulter que son propre profil, jamais un id arbitraire (voir
     * le critère d'acceptation « un utilisateur non connecté ne peut pas
     * accéder aux données d'un autre utilisateur », qui s'étend ici à
     * « aucun utilisateur, connecté ou non »).
     *
     * {@code Optional#orElseThrow} volontaire plutôt qu'une gestion de
     * cas absent : un JWT valide signifie que l'utilisateur existait au
     * moment de sa délivrance, et aucune fonctionnalité de suppression de
     * compte n'existe encore — l'absence serait un bug ailleurs, pas un
     * cas attendu de cet endpoint (500, capturé par
     * {@code GlobalExceptionHandler}, plutôt qu'un 404 qui laisserait
     * croire à un usage normal).
     */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(Authentication authentication) {
        Long userId = ((JwtAuthentication) authentication).getUserId();
        User user = userService.getUserById(userId).orElseThrow();
        return ResponseEntity.ok(UserResponse.from(user));
    }

    /**
     * Modifie le profil de l'utilisateur actuellement connecté (LL-EF-006,
     * critère d'acceptation « les informations modifiables peuvent être
     * modifiées » / « les modifications sont correctement persistées »).
     * Même garantie que {@link #getCurrentUser} : {@code id} provient du
     * JWT, jamais de la requête — voir {@link UserService#updateProfile}
     * pour le détail des champs modifiables et leur validation.
     */
    @PatchMapping("/me")
    public ResponseEntity<Object> updateCurrentUser(
            Authentication authentication, @RequestBody UpdateProfileRequest request, HttpServletRequest httpRequest) {
        Long userId = ((JwtAuthentication) authentication).getUserId();
        try {
            User updated = userService.updateProfile(userId, request.username(), request.email());
            return ResponseEntity.ok(UserResponse.from(updated));
        } catch (IllegalArgumentException exception) {
            return errorResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), httpRequest);
        } catch (NoSuchElementException exception) {
            return errorResponse(HttpStatus.NOT_FOUND, "Utilisateur introuvable.", httpRequest);
        }
    }

    private ResponseEntity<Object> errorResponse(HttpStatus status, String message, HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                Instant.now(), status.value(), status.getReasonPhrase(), message, request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }

    /**
     * Corps de requête pour la création d'un utilisateur : uniquement les
     * champs fournis par le client (id et createdAt sont générés côté
     * serveur).
     */
    public record CreateUserRequest(String username, String email) {
    }

    /**
     * Corps de requête pour {@code PATCH /api/v1/users/me} (LL-EF-006) :
     * les deux seules informations modifiables (voir
     * {@link UserService#updateProfile}).
     */
    public record UpdateProfileRequest(String username, String email) {
    }

}
