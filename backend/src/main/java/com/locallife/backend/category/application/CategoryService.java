package com.locallife.backend.category.application;

import com.locallife.backend.category.api.CategoryResponse;
import com.locallife.backend.category.domain.Category;
import com.locallife.backend.category.domain.CategoryTranslation;
import com.locallife.backend.category.infrastructure.CategoryRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Service Category (CAT-001). Résout, pour chaque catégorie, le label et la
 * description de la locale demandée à partir de ses
 * {@link CategoryTranslation} — la résolution reste volontairement simple
 * en V1 : une seule locale existe ({@code fr-FR}, voir la migration
 * {@code V32__refactor_category_referential.sql}), donc la chaîne de
 * repli complète décrite par CAT-005 (locale demandée → langue de base →
 * {@code fr-FR} → {@code CODE}) se réduit ici aux deux derniers maillons
 * ({@code fr-FR} si présente, sinon le {@code code} lui-même). Elle pourra
 * être étendue sans changer la signature de ce service lorsque CAT-005
 * ajoutera des locales supplémentaires.
 */
@Service
public class CategoryService {

    /** V1 : seule locale disponible (SPRINT_CATEG, "Stratégie de traduction"). */
    private static final String DEFAULT_LOCALE = "fr-FR";

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public List<CategoryResponse> getAllCategories(String locale) {
        return categoryRepository.findAll().stream()
                .map(category -> toResponse(category, locale))
                .toList();
    }

    public Optional<CategoryResponse> getCategoryByCode(String code, String locale) {
        return categoryRepository.findByCode(code)
                .map(category -> toResponse(category, locale));
    }

    private CategoryResponse toResponse(Category category, String locale) {
        CategoryTranslation translation = resolveTranslation(category, locale);
        String label = translation != null ? translation.label() : category.code();
        String description = translation != null ? translation.description() : null;
        return new CategoryResponse(category.code(), label, description, category.active());
    }

    /**
     * Repli : locale demandée, puis {@code fr-FR}, puis {@code null}
     * (auquel cas {@link #toResponse} retombe sur le {@code code} comme
     * label — dernier maillon de la chaîne de repli décrite par CAT-005).
     */
    private CategoryTranslation resolveTranslation(Category category, String locale) {
        CategoryTranslation exact = findByLocale(category, locale);
        if (exact != null) {
            return exact;
        }
        return findByLocale(category, DEFAULT_LOCALE);
    }

    private CategoryTranslation findByLocale(Category category, String locale) {
        return category.translations().stream()
                .filter(t -> t.locale().equalsIgnoreCase(locale))
                .findFirst()
                .orElse(null);
    }

}
