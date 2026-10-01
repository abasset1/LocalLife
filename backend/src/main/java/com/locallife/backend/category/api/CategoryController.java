package com.locallife.backend.category.api;

import com.locallife.backend.category.application.CategoryService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contrôleur REST pour la consultation du référentiel des catégories
 * (CAT-001). Endpoint de consultation uniquement — pas d'écriture (les 9
 * catégories V1 sont fixées par migration, voir {@code CategoryRepository}).
 *
 * Expose {@link CategoryResponse} (code + label/description résolus),
 * jamais l'entité {@code Category} brute : conformément à la règle
 * ID/CODE/LABEL du sprint, l'id technique interne et la structure de
 * traduction ({@code List<CategoryTranslation>}) ne doivent pas fuiter en
 * API.
 *
 * Non protégé en lecture (même choix que l'ancien {@code CategoryController}
 * et que {@code SourceController} pour leurs endpoints de liste) : un
 * référentiel de catégories n'est pas une donnée sensible.
 */
@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    /**
     * {@code locale} : optionnel, {@code fr-FR} par défaut (seule locale
     * disponible en V1). Prévu pour les locales futures (CAT-005) sans
     * changement de signature de cet endpoint.
     */
    @GetMapping
    public ResponseEntity<List<CategoryResponse>> getAllCategories(
            @RequestParam(name = "locale", required = false, defaultValue = "fr-FR") String locale) {
        List<CategoryResponse> categories = categoryService.getAllCategories(locale);
        return ResponseEntity.ok(categories);
    }

}
