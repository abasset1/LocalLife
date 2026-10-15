package com.locallife.backend.category.api;

import com.locallife.backend.category.application.CategoryAdminService;
import com.locallife.backend.category.application.CategoryAdminService.CategoryView;
import com.locallife.backend.category.application.CategoryAdminService.ConflictException;
import com.locallife.backend.common.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administration du référentiel des catégories et des mots-clés de classification : ajout et
 * activation / désactivation (aucune suppression). Réservé au rôle {@code ADMIN} ({@code /api/v1/admin/**}, voir
 * {@code SecurityConfig}). La lecture publique reste {@code GET /api/v1/categories} ({@link CategoryController}).
 */
@RestController
@RequestMapping("/api/v1/admin/categories")
public class AdminCategoryController {

    private final CategoryAdminService service;

    public AdminCategoryController(CategoryAdminService service) {
        this.service = service;
    }

    public record CreateCategoryRequest(String code, String label, String description) {
    }

    public record UpdateCategoryRequest(String label, String description, Boolean active) {
    }

    public record CreateKeywordRequest(String keyword, String scope) {
    }

    public record UpdateKeywordRequest(Boolean active, String scope) {
    }

    @Operation(summary = "Catégories et mots-clés",
            description = "Toutes les catégories (actives ou non) avec tous leurs mots-clés (actifs ou non).")
    @GetMapping
    public ResponseEntity<List<CategoryView>> list() {
        return ResponseEntity.ok(service.findAll());
    }

    @Operation(summary = "Ajouter une catégorie",
            description = "Code en SCREAMING_SNAKE_CASE (identité stable, non modifiable) et libellé fr-FR. "
                    + "409 si le code existe déjà.")
    @PostMapping
    public ResponseEntity<Object> create(@RequestBody CreateCategoryRequest body, HttpServletRequest request) {
        if (body == null) {
            return error(HttpStatus.BAD_REQUEST, "Corps de requête obligatoire.", request);
        }
        return handle(() -> Optional.of(service.createCategory(body.code(), body.label(), body.description())),
                HttpStatus.CREATED, request);
    }

    @Operation(summary = "Modifier une catégorie",
            description = "Libellé, description et/ou activation. Une catégorie désactivée n'est plus proposée ni "
                    + "attribuée par la classification ; les activités qui la portent la conservent.")
    @PatchMapping("/{code}")
    public ResponseEntity<Object> update(@PathVariable String code, @RequestBody UpdateCategoryRequest body,
            HttpServletRequest request) {
        if (body == null) {
            return error(HttpStatus.BAD_REQUEST, "Corps de requête obligatoire.", request);
        }
        return handle(() -> service.updateCategory(code, body.label(), body.description(), body.active()),
                HttpStatus.OK, request);
    }

    @Operation(summary = "Ajouter un mot-clé à une catégorie",
            description = "Portée ALL (titres et sources, défaut) ou SOURCE (catégories d'origine et tags "
                    + "seulement). Un mot-clé identique désactivé est réactivé ; 409 s'il est déjà actif.")
    @PostMapping("/{code}/keywords")
    public ResponseEntity<Object> addKeyword(@PathVariable String code, @RequestBody CreateKeywordRequest body,
            HttpServletRequest request) {
        if (body == null) {
            return error(HttpStatus.BAD_REQUEST, "Corps de requête obligatoire.", request);
        }
        return handle(() -> service.addKeyword(code, body.keyword(), body.scope()), HttpStatus.CREATED, request);
    }

    @Operation(summary = "Modifier un mot-clé", description = "Activation et/ou portée (ALL ou SOURCE).")
    @PatchMapping("/keywords/{id}")
    public ResponseEntity<Object> updateKeyword(@PathVariable Long id, @RequestBody UpdateKeywordRequest body,
            HttpServletRequest request) {
        if (body == null) {
            return error(HttpStatus.BAD_REQUEST, "Corps de requête obligatoire.", request);
        }
        return handle(() -> service.updateKeyword(id, body.active(), body.scope()), HttpStatus.OK, request);
    }

    private static <T> ResponseEntity<Object> handle(
            Supplier<Optional<T>> action, HttpStatus success, HttpServletRequest request) {
        try {
            return action.get().<ResponseEntity<Object>>map(value -> ResponseEntity.status(success).body(value))
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (IllegalArgumentException exception) {
            return error(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
        } catch (ConflictException exception) {
            return error(HttpStatus.CONFLICT, exception.getMessage(), request);
        }
    }

    private static ResponseEntity<Object> error(HttpStatus status, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(new ErrorResponse(
                Instant.now(), status.value(), status.getReasonPhrase(), message, request.getRequestURI()));
    }
}
