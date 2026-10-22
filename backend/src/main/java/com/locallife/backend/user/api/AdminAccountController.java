package com.locallife.backend.user.api;

import com.locallife.backend.auth.application.JwtAuthentication;
import com.locallife.backend.auth.application.ReauthenticationService;
import com.locallife.backend.common.ErrorResponse;
import com.locallife.backend.user.application.AccountActionException;
import com.locallife.backend.user.application.AccountAdministrationService;
import com.locallife.backend.user.application.AccountAdministrationService.AccountPage;
import com.locallife.backend.user.application.AccountAdministrationService.Actor;
import com.locallife.backend.user.application.AccountAdministrationService.AuditPage;
import com.locallife.backend.user.infrastructure.AccountRepository.Account;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Console générale des comptes (sprint « Rôles et comptes », ACC-01/02/03, AUD-01). Réservée au rôle {@code ADMIN}
 * ({@code /api/v1/admin/**}, rôle relu en base à chaque requête). Les réponses ne contiennent aucun secret : ni hash
 * de mot de passe, ni jeton (voir {@link Account}). Règles et codes d'erreur : {@link AccountAdministrationService}.
 */
@RestController
@RequestMapping("/api/v1/admin/accounts")
public class AdminAccountController {

    private final AccountAdministrationService service;
    private final ReauthenticationService reauthenticationService;

    public AdminAccountController(AccountAdministrationService service,
            ReauthenticationService reauthenticationService) {
        this.service = service;
        this.reauthenticationService = reauthenticationService;
    }

    /** Corps d'un changement de rôle : {@code USER} ou {@code PRO}. */
    public record RoleRequest(String role) {
    }

    /** Corps d'une suspension (motif obligatoire) ou d'une réactivation (motif facultatif). */
    public record StatusRequest(String reason) {
    }

    @Operation(summary = "Liste paginée des comptes",
            description = "q : partie de l'adresse ou du nom d'utilisateur, ou identifiant exact ; role : USER, PRO "
                    + "ou ADMIN ; status : ACTIVE ou SUSPENDED ; size ≤ 100. Plus récents d'abord.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Page de comptes."),
        @ApiResponse(responseCode = "400", description = "Filtre ou pagination invalide."),
        @ApiResponse(responseCode = "401", description = "Authentification requise."),
        @ApiResponse(responseCode = "403", description = "Rôle ADMIN requis.")
    })
    @GetMapping
    public AccountPage search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.search(q, role, status, page, size);
    }

    @Operation(summary = "Un compte", description = "404 si aucun compte ne correspond.")
    @GetMapping("/{id}")
    public ResponseEntity<Account> find(@PathVariable Long id) {
        return service.find(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(summary = "Attribuer ou retirer le rôle PRO",
            description = "USER ↔ PRO. 400 rôle inconnu ; 403 rôle ADMIN demandé, compte administrateur ou propre "
                    + "compte ; 404 compte inconnu. Effet immédiat (rôle relu en base). Journalisé.")
    @PatchMapping("/{id}/role")
    public Account changeRole(
            @PathVariable Long id, @RequestBody(required = false) RoleRequest body, Authentication authentication) {
        return service.changeRole(actor(authentication), id, body == null ? null : body.role());
    }

    @Operation(summary = "Suspendre un compte",
            description = "Motif obligatoire (3 à 500 caractères). Le compte ne peut plus se connecter ni utiliser "
                    + "une route authentifiée, dès sa requête suivante ; ses données sont conservées. 403 pour un "
                    + "compte administrateur ou son propre compte ; 409 si déjà suspendu ; 428 sans le mot de passe de "
                    + "l'administrateur (en-tête X-Confirm-Password). Journalisé.")
    @PostMapping("/{id}/suspend")
    public Account suspend(
            @PathVariable Long id, @RequestBody(required = false) StatusRequest body,
            @RequestHeader(value = ReauthenticationService.HEADER, required = false) String password,
            Authentication authentication) {
        reauthenticationService.require(actor(authentication).userId(), password);
        return service.suspend(actor(authentication), id, body == null ? null : body.reason());
    }

    @Operation(summary = "Fermer toutes les sessions d'un compte",
            description = "Compte compromis : ses jetons déjà émis sont refusés, il doit se reconnecter. 403 pour "
                    + "un compte administrateur ou son propre compte. Journalisé.")
    @PostMapping("/{id}/logout-everywhere")
    public Account logoutEverywhere(@PathVariable Long id, Authentication authentication) {
        return service.logoutEverywhere(actor(authentication), id);
    }

    @Operation(summary = "Supprimer un compte",
            description = "Comme une suppression par son titulaire : activités publiées conservées sans auteur, le "
                    + "reste supprimé. Motif obligatoire (3 à 500 caractères) ; mot de passe de l'administrateur dans "
                    + "l'en-tête X-Confirm-Password (428 sinon). 403 pour un compte administrateur ou son propre "
                    + "compte. Journalisé.")
    @PostMapping("/{id}/deletion")
    public ResponseEntity<Void> delete(
            @PathVariable Long id, @RequestBody(required = false) StatusRequest body,
            @RequestHeader(value = ReauthenticationService.HEADER, required = false) String password,
            Authentication authentication) {
        reauthenticationService.require(actor(authentication).userId(), password);
        service.deleteAccount(actor(authentication), id, body == null ? null : body.reason());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Réactiver un compte suspendu",
            description = "Motif facultatif. 409 si le compte est déjà actif. Journalisé.")
    @PostMapping("/{id}/reactivate")
    public Account reactivate(
            @PathVariable Long id, @RequestBody(required = false) StatusRequest body, Authentication authentication) {
        return service.reactivate(actor(authentication), id, body == null ? null : body.reason());
    }

    @Operation(summary = "Journal d'audit des actions sur les comptes",
            description = "Lecture seule : aucune entrée ne peut être modifiée ni supprimée. targetUserId pour "
                    + "filtrer sur un compte. Dates en UTC.")
    @GetMapping("/audit")
    public AuditPage audit(
            @RequestParam(required = false) Long targetUserId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return service.audit(targetUserId, page, size);
    }

    static Actor actor(Authentication authentication) {
        if (authentication instanceof JwtAuthentication jwt) {
            return new Actor(jwt.getUserId(), jwt.getEmail());
        }
        return new Actor(null, null);
    }

    @ExceptionHandler(AccountActionException.class)
    public ResponseEntity<ErrorResponse> handle(AccountActionException exception, HttpServletRequest request) {
        return ResponseEntity.status(exception.status()).body(new ErrorResponse(Instant.now(),
                exception.status().value(), exception.status().getReasonPhrase(), exception.getMessage(),
                request.getRequestURI()));
    }
}
