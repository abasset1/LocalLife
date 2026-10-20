package com.locallife.backend.user.api;

import com.locallife.backend.auth.api.UserResponse;
import com.locallife.backend.common.ErrorResponse;
import com.locallife.backend.user.application.AccountActionException;
import com.locallife.backend.user.application.AccountAdministrationService;
import com.locallife.backend.user.application.UserService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Comptes professionnels (ADR-0012, décision D1) : un administrateur retrouve un compte par son email et lui
 * attribue ou retire le rôle {@code PRO}. Réservé au rôle {@code ADMIN} ({@code /api/v1/admin/**}). Contrat
 * historique conservé pour l'écran « Professionnels » ; la console générale est {@link AdminAccountController},
 * avec laquelle le changement de rôle partage règles et journal ({@code AccountAdministrationService}).
 */
@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    private final UserService userService;
    private final AccountAdministrationService accountAdministrationService;

    public AdminUserController(UserService userService, AccountAdministrationService accountAdministrationService) {
        this.userService = userService;
        this.accountAdministrationService = accountAdministrationService;
    }

    @Operation(summary = "Retrouver un compte par son email", description = "404 si aucun compte ne correspond.")
    @GetMapping
    public ResponseEntity<Object> findByEmail(@RequestParam String email) {
        return userService.findByEmail(email).<ResponseEntity<Object>>map(user -> ResponseEntity.ok(
                UserResponse.from(user))).orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Rôle demandé : {@code USER} ou {@code PRO}. */
    public record RoleRequest(String role) {
    }

    @Operation(summary = "Attribuer ou retirer le rôle professionnel",
            description = "Bascule USER ↔ PRO, conservé pour compatibilité : même règles et même journal que "
                    + "PATCH /api/v1/admin/accounts/{id}/role. 400 rôle inconnu ; 403 rôle ADMIN demandé, compte "
                    + "administrateur ou propre compte (400 avant le sprint « Rôles et comptes ») ; 404 compte "
                    + "inconnu. Effet immédiat (rôle relu en base).")
    @PatchMapping("/{id}/role")
    public ResponseEntity<Object> changeRole(
            @PathVariable Long id, @RequestBody RoleRequest body, Authentication authentication,
            HttpServletRequest request) {
        try {
            accountAdministrationService.changeRole(
                    AdminAccountController.actor(authentication), id, body == null ? null : body.role());
            return ResponseEntity.ok(UserResponse.from(userService.getUserById(id).orElseThrow()));
        } catch (AccountActionException exception) {
            return error(exception.status(), exception.getMessage(), request);
        }
    }

    private static ResponseEntity<Object> error(HttpStatus status, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(new ErrorResponse(
                Instant.now(), status.value(), status.getReasonPhrase(), message, request.getRequestURI()));
    }
}
