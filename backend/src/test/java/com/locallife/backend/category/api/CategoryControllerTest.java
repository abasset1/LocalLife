package com.locallife.backend.category.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.locallife.backend.category.application.CategoryService;
import com.locallife.backend.category.domain.Category;
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
        // Given
        List<Category> categories = List.of(
                new Category(1L, "Concert", "Événements musicaux"),
                new Category(2L, "Marché", "Marchés et brocantes")
        );
        when(categoryService.getAllCategories()).thenReturn(categories);

        // When
        ResponseEntity<List<Category>> response = categoryController.getAllCategories();

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().size());
    }

    @Test
    void getAllCategories_ShouldReturnEmptyList_WhenNoCategories() {
        // Given
        when(categoryService.getAllCategories()).thenReturn(List.of());

        // When
        ResponseEntity<List<Category>> response = categoryController.getAllCategories();

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(0, response.getBody().size());
    }

}
