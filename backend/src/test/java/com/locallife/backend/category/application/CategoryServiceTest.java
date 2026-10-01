package com.locallife.backend.category.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.locallife.backend.category.api.CategoryResponse;
import com.locallife.backend.category.domain.Category;
import com.locallife.backend.category.domain.CategoryTranslation;
import com.locallife.backend.category.infrastructure.CategoryRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Couvre la résolution de traduction (CAT-001) : c'est la seule logique
 * nouvelle introduite par ce ticket (le reste n'est que persistance/lecture).
 */
@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private CategoryService categoryService;

    private Category categoryWithFrTranslation() {
        CategoryTranslation fr = new CategoryTranslation(1L, "fr-FR", "Culture", "Description FR");
        return new Category(1L, "CULTURE", true, LocalDateTime.now(), LocalDateTime.now(), List.of(fr));
    }

    @Test
    void getAllCategories_ShouldResolveRequestedLocale_WhenPresent() {
        when(categoryRepository.findAll()).thenReturn(List.of(categoryWithFrTranslation()));

        List<CategoryResponse> result = categoryService.getAllCategories("fr-FR");

        assertEquals(1, result.size());
        assertEquals("CULTURE", result.get(0).code());
        assertEquals("Culture", result.get(0).label());
        assertEquals("Description FR", result.get(0).description());
    }

    @Test
    void getAllCategories_ShouldFallBackToFrFr_WhenRequestedLocaleMissing() {
        when(categoryRepository.findAll()).thenReturn(List.of(categoryWithFrTranslation()));

        // en-US n'existe pas en V1 : repli attendu sur fr-FR (chaîne de repli CAT-005)
        List<CategoryResponse> result = categoryService.getAllCategories("en-US");

        assertEquals("Culture", result.get(0).label());
    }

    @Test
    void getAllCategories_ShouldFallBackToCode_WhenNoTranslationAtAll() {
        Category withoutTranslation =
                new Category(2L, "SPORT", true, LocalDateTime.now(), LocalDateTime.now(), List.of());
        when(categoryRepository.findAll()).thenReturn(List.of(withoutTranslation));

        List<CategoryResponse> result = categoryService.getAllCategories("fr-FR");

        // Dernier maillon de la chaîne de repli (CAT-005) : le CODE lui-même
        assertEquals("SPORT", result.get(0).label());
        assertEquals(null, result.get(0).description());
    }

    @Test
    void getCategoryByCode_ShouldReturnEmpty_WhenCodeUnknown() {
        when(categoryRepository.findByCode("UNKNOWN")).thenReturn(Optional.empty());

        Optional<CategoryResponse> result = categoryService.getCategoryByCode("UNKNOWN", "fr-FR");

        assertEquals(Optional.empty(), result);
    }

}
