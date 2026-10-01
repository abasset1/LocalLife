package com.locallife.backend.category.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.locallife.backend.category.application.CategoryService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class CategoryControllerTest {

    @Mock
    private CategoryService categoryService;

    @InjectMocks
    private CategoryController categoryController;

    @Test
    void getAllCategories_ShouldReturnListOfCategories() {
        // Given : deux des 9 catégories V1 (CAT-001), avec leur label fr-FR résolu
        List<CategoryResponse> categories = List.of(
                new CategoryResponse("CULTURE", "Culture", "Spectacle vivant, musique, exposition...", true),
                new CategoryResponse("MARKETS", "Marchés", "Marchés de producteurs, brocantes...", true)
        );
        when(categoryService.getAllCategories("fr-FR")).thenReturn(categories);

        // When
        ResponseEntity<List<CategoryResponse>> response = categoryController.getAllCategories("fr-FR");

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().size());
        assertEquals("CULTURE", response.getBody().get(0).code());
    }

    @Test
    void getAllCategories_ShouldReturnEmptyList_WhenNoCategories() {
        // Given
        when(categoryService.getAllCategories("fr-FR")).thenReturn(List.of());

        // When
        ResponseEntity<List<CategoryResponse>> response = categoryController.getAllCategories("fr-FR");

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(0, response.getBody().size());
    }

}
