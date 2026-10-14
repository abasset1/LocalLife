package com.locallife.backend.category.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.locallife.backend.category.api.AdminCategoryController.CreateCategoryRequest;
import com.locallife.backend.category.api.AdminCategoryController.CreateKeywordRequest;
import com.locallife.backend.category.api.AdminCategoryController.UpdateCategoryRequest;
import com.locallife.backend.category.api.AdminCategoryController.UpdateKeywordRequest;
import com.locallife.backend.category.application.CategoryAdminService;
import com.locallife.backend.category.application.CategoryAdminService.CategoryView;
import com.locallife.backend.category.application.CategoryAdminService.ConflictException;
import com.locallife.backend.category.application.CategoryAdminService.KeywordView;
import com.locallife.backend.common.ErrorResponse;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(MockitoExtension.class)
class AdminCategoryControllerTest {

    @Mock
    private CategoryAdminService service;

    @InjectMocks
    private AdminCategoryController controller;

    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/admin/categories");

    @Test
    void create_returns201() {
        CategoryView view = new CategoryView("BIEN_ETRE", "Bien-être", null, true, List.of());
        when(service.createCategory("BIEN_ETRE", "Bien-être", null)).thenReturn(view);

        ResponseEntity<Object> response = controller.create(
                new CreateCategoryRequest("BIEN_ETRE", "Bien-être", null), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(view);
    }

    @Test
    void create_mapsConflictAndValidationErrors() {
        when(service.createCategory("CULTURE", "Culture", null)).thenThrow(new ConflictException("existe déjà"));
        when(service.createCategory("x", "X", null)).thenThrow(new IllegalArgumentException("Code invalide"));

        ResponseEntity<Object> conflict = controller.create(new CreateCategoryRequest("CULTURE", "Culture", null),
                request);
        ResponseEntity<Object> invalid = controller.create(new CreateCategoryRequest("x", "X", null), request);

        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(((ErrorResponse) invalid.getBody()).message()).isEqualTo("Code invalide");
    }

    @Test
    void update_unknownCategory_returns404() {
        when(service.updateCategory("NOPE", null, null, false)).thenReturn(Optional.empty());

        ResponseEntity<Object> response = controller.update("NOPE", new UpdateCategoryRequest(null, null, false),
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void addKeyword_returns201_andUpdateKeyword_returns200() {
        KeywordView keyword = new KeywordView(30L, "opéra", "ALL", true);
        when(service.addKeyword("CULTURE", "opéra", null)).thenReturn(Optional.of(keyword));
        when(service.updateKeyword(30L, false, null)).thenReturn(Optional.of(
                new KeywordView(30L, "opéra", "ALL", false)));

        ResponseEntity<Object> created = controller.addKeyword("CULTURE", new CreateKeywordRequest("opéra", null),
                request);
        ResponseEntity<Object> updated = controller.updateKeyword(30L, new UpdateKeywordRequest(false, null), request);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).isEqualTo(keyword);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
