package com.locallife.backend.user.api;

import com.locallife.backend.auth.api.UserResponse;
import com.locallife.backend.auth.application.JwtAuthentication;
import com.locallife.backend.common.ErrorResponse;
import com.locallife.backend.activity.application.ContributionAccessPolicy;
import com.locallife.backend.pro.application.ProAccessPolicy;
import com.locallife.backend.user.application.AccountActionException;
import com.locallife.backend.user.application.AccountSelfService;
import com.locallife.backend.user.application.UserService;
import com.locallife.backend.user.domain.User;
import io.swagger.v3.oas.annotations.Operation;
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
    private final AccountSelfService accountSelfService;
    private final ContributionAccessPolicy contributionAccessPolicy;
    private final ProAccessPolicy proAccessPolicy;

    public UserController(
            UserService userService, AccountSelfService accountSelfService,
            ContributionAccessPolicy contributionAccessPolicy, ProAccessPolicy proAccessPolicy) {
        this.userService = userService;
        this.accountSelfService = accountSelfService;
        this.contributionAccessPolicy = contributionAccessPolicy;
        this.proAccessPolicy = proAccessPolicy;
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
    public ResponseEntity<CurrentUserResponse> getCurrentUser(Authentication authentication) {
        Long userId = ((JwtAuthentication) authentication).getUserId();
        User user = userService.getUserById(userId).orElseThrow();
        return ResponseEntity.ok(current(user));
    }

    /**
     * Modifie le profil de l'utilisateur actuellement connecté (LL-EF-006). Le nom d'utilisateur change tout de
     * suite ; une nouvelle adresse e-mail n'est appliquée qu'après confirmation depuis cette adresse (21/10/2026) :
     * la réponse la montre dans {@code pendingEmail}. {@code id} provient du JWT, jamais de la requête ; rôle, statut
     * et offre ne sont pas modifiables ici.
     */
    @PatchMapping("/me")
    public ResponseEntity<Object> updateCurrentUser(
            Authentication authentication, @RequestBody UpdateProfileRequest request, HttpServletRequest httpRequest) {
        Long userId = ((JwtAuthentication) authentication).getUserId();
        try {
            User existing = userService.getUserById(userId).orElseThrow();
            User updated = userService.updateProfile(userId, request.username(), existing.email());
            String requestedEmail = request.email() == null ? null : request.email().trim();
            if (requestedEmail != null && !requestedEmail.isEmpty()
                    && !requestedEmail.equalsIgnoreCase(existing.email())) {
                accountSelfService.requestEmailChange(userId, requestedEmail);
            }
            return ResponseEntity.ok(current(updated));
        } catch (IllegalArgumentException exception) {
            return errorResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), httpRequest);
        } catch (AccountActionException exception) {
            return errorResponse(exception.status(), exception.getMessage(), httpRequest);
        } catch (NoSuchElementException exception) {
            return errorResponse(HttpStatus.NOT_FOUND, "Utilisateur introuvable.", httpRequest);
        }
    }

    /** Corps de {@code POST /me/password} ; {@code currentPassword} ignoré pour un compte sans mot de passe. */
    public record ChangePasswordRequest(String currentPassword, String newPassword) {
    }

    /** Nouveau jeton de la session en cours : les jetons émis avant le changement sont refusés. */
    public record TokenResponse(String token) {
    }

    @Operation(summary = "Changer (ou définir) son mot de passe",
            description = "Mot de passe actuel exigé s'il en existe un (compte créé avec Google : non). Les autres "
                    + "sessions sont fermées ; la réponse contient le nouveau jeton de la session en cours.")
    @PostMapping("/me/password")
    public ResponseEntity<Object> changePassword(
            Authentication authentication, @RequestBody(required = false) ChangePasswordRequest request,
            HttpServletRequest httpRequest) {
        Long userId = ((JwtAuthentication) authentication).getUserId();
        try {
            String token = accountSelfService.changePassword(userId,
                    request == null ? null : request.currentPassword(), request == null ? null : request.newPassword());
            return ResponseEntity.ok(new TokenResponse(token));
        } catch (AccountActionException exception) {
            return errorResponse(exception.status(), exception.getMessage(), httpRequest);
        }
    }

    /** Corps de {@code POST /me/deletion} : mot de passe, ou {@code SUPPRIMER} pour un compte sans mot de passe. */
    public record DeletionRequest(String password, String confirmation) {
    }

    @Operation(summary = "Supprimer son compte",
            description = "Définitif. Activités publiées conservées sans auteur ; contributions non publiées, "
                    + "favoris, alertes, préférences et connexions supprimés ; historique d'abonnement anonymisé. "
                    + "Mot de passe exigé (ou SUPPRIMER pour un compte sans mot de passe). 403 pour un "
                    + "administrateur.")
    @PostMapping("/me/deletion")
    public ResponseEntity<Object> deleteAccount(
            Authentication authentication, @RequestBody(required = false) DeletionRequest request,
            HttpServletRequest httpRequest) {
        Long userId = ((JwtAuthentication) authentication).getUserId();
        try {
            accountSelfService.deleteAccount(userId, request == null ? null : request.password(),
                    request == null ? null : request.confirmation());
            return ResponseEntity.noContent().build();
        } catch (AccountActionException exception) {
            return errorResponse(exception.status(), exception.getMessage(), httpRequest);
        }
    }

    private CurrentUserResponse current(User user) {
        return CurrentUserResponse.from(user, accountSelfService.pendingEmail(user.id()).orElse(null),
                AccountSelfService.hasPassword(user), contributionAccessPolicy.canContribute(user.id()),
                proAccessPolicy.hasProAccess(user.id()));
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
