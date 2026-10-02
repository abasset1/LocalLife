package com.locallife.backend.category.application;

import com.locallife.backend.category.api.CategoryResponse;
import com.locallife.backend.category.domain.Category;
import com.locallife.backend.category.domain.CategoryTranslation;
import com.locallife.backend.category.infrastructure.CategoryRepository;
import com.locallife.backend.common.i18n.LocaleResolver;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Service Category (CAT-001). Résout, pour chaque catégorie, le label et la
 * description de la locale demandée à partir de ses
 * {@link CategoryTranslation}, en déléguant la chaîne de repli à
 * {@link com.locallife.backend.common.i18n.LocaleResolver} (CAT-005) :
 * locale demandée → langue de base correspondante → {@code fr-FR} →
 * {@code CODE} (ce dernier maillon géré ici, dans {@link #toResponse}).
 */
@Service
public class CategoryService {

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

    private CategoryTranslation resolveTranslation(Category category, String locale) {
        return LocaleResolver.resolve(category.translations(), locale, CategoryTranslation::locale);
    }

}
