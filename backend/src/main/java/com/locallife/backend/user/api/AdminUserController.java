package com.locallife.backend.user.api;

import com.locallife.backend.auth.api.UserResponse;
import com.locallife.backend.common.ErrorResponse;
import com.locallife.backend.user.application.UserService;
import com.locallife.backend.user.domain.Role;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Locale;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Comptes professionnels (ADR-0012, décision D1) : un administrateur retrouve un compte par son email et lui
 * attribue ou retire le rôle {@code PRO}. Réservé au rôle {@code ADMIN} ({@code /api/v1/admin/**}).
 */
@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
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
            description = "Bascule USER ↔ PRO. 400 pour un autre rôle ou un compte administrateur ; effet immédiat "
                    + "sur l'espace professionnel (le rôle est relu en base).")
    @PatchMapping("/{id}/role")
    public ResponseEntity<Object> changeRole(
            @PathVariable Long id, @RequestBody RoleRequest body, HttpServletRequest request) {
        Role role;
        try {
            role = Role.valueOf(body == null || body.role() == null ? "" : body.role().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return error(HttpStatus.BAD_REQUEST, "Rôle inconnu : USER ou PRO.", request);
        }
        try {
            return ResponseEntity.ok(UserResponse.from(userService.changeRole(id, role)));
        } catch (IllegalArgumentException exception) {
            return error(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
        } catch (NoSuchElementException exception) {
            return error(HttpStatus.NOT_FOUND, "Utilisateur introuvable.", request);
        }
    }

    private static ResponseEntity<Object> error(HttpStatus status, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(new ErrorResponse(
                Instant.now(), status.value(), status.getReasonPhrase(), message, request.getRequestURI()));
    }
}
