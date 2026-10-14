package com.locallife.backend.category.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.locallife.backend.category.application.CategoryAdminService.CategoryView;
import com.locallife.backend.category.application.CategoryAdminService.ConflictException;
import com.locallife.backend.category.application.CategoryAdminService.KeywordView;
import com.locallife.backend.category.infrastructure.CategoryAdminRepository;
import com.locallife.backend.category.infrastructure.CategoryAdminRepository.CategoryRow;
import com.locallife.backend.category.infrastructure.CategoryAdminRepository.KeywordRow;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CategoryAdminServiceTest {

    private static final CategoryRow CULTURE = new CategoryRow(1L, "CULTURE", true, "Culture", "Spectacles");

    @Mock
    private CategoryAdminRepository repository;

    @InjectMocks
    private CategoryAdminService service;

    @Test
    void findAll_groupsKeywordsByCategory_includingInactiveOnes() {
        when(repository.findAllCategories()).thenReturn(List.of(CULTURE,
                new CategoryRow(2L, "SPORT", false, null, null)));
        when(repository.findAllKeywords()).thenReturn(List.of(
                new KeywordRow(10L, 1L, "concert", "ALL", true),
                new KeywordRow(11L, 1L, "conférence", "ALL", false)));

        List<CategoryView> categories = service.findAll();

        assertThat(categories).extracting(CategoryView::code).containsExactly("CULTURE", "SPORT");
        assertThat(categories.get(0).keywords()).extracting(KeywordView::keyword)
                .containsExactly("concert", "conférence");
        assertThat(categories.get(1).keywords()).isEmpty();
        assertThat(categories.get(1).label()).isEqualTo("SPORT");
        assertThat(categories.get(1).active()).isFalse();
    }

    @Test
    void createCategory_normalizesCodeAndStoresLabel() {
        when(repository.categoryCodeExists("BIEN_ETRE")).thenReturn(false);
        when(repository.insertCategory("BIEN_ETRE")).thenReturn(20L);
        when(repository.findAllCategories()).thenReturn(List.of(
                new CategoryRow(20L, "BIEN_ETRE", true, "Bien-être", null)));
        when(repository.findAllKeywords()).thenReturn(List.of());

        CategoryView created = service.createCategory(" bien_etre ", " Bien-être ", "  ");

        verify(repository).upsertTranslation(20L, "Bien-être", null);
        assertThat(created.code()).isEqualTo("BIEN_ETRE");
        assertThat(created.active()).isTrue();
    }

    @Test
    void createCategory_rejectsInvalidCodeAndMissingLabel() {
        assertThatThrownBy(() -> service.createCategory("bien-être", "Bien-être", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.createCategory("WELLNESS", " ", null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).insertCategory(anyString());
    }

    @Test
    void createCategory_conflictsWhenCodeExists() {
        when(repository.categoryCodeExists("CULTURE")).thenReturn(true);

        assertThatThrownBy(() -> service.createCategory("CULTURE", "Culture", null))
                .isInstanceOf(ConflictException.class);
        verify(repository, never()).insertCategory(anyString());
    }

    @Test
    void updateCategory_deactivatesWithoutTouchingLabel() {
        when(repository.findCategoryByCode("CULTURE")).thenReturn(Optional.of(CULTURE));
        when(repository.findAllCategories()).thenReturn(List.of(CULTURE));
        when(repository.findAllKeywords()).thenReturn(List.of());

        assertThat(service.updateCategory("CULTURE", null, null, false)).isPresent();

        verify(repository).setCategoryActive(1L, false);
        verify(repository, never()).upsertTranslation(anyLong(), anyString(), any());
    }

    @Test
    void updateCategory_keepsDescriptionWhenOnlyLabelChanges() {
        when(repository.findCategoryByCode("CULTURE")).thenReturn(Optional.of(CULTURE));
        when(repository.findAllCategories()).thenReturn(List.of(CULTURE));
        when(repository.findAllKeywords()).thenReturn(List.of());

        service.updateCategory("CULTURE", "Arts et culture", null, null);

        verify(repository).upsertTranslation(1L, "Arts et culture", "Spectacles");
        verify(repository, never()).setCategoryActive(anyLong(), anyBoolean());
    }

    @Test
    void updateCategory_unknownCode_isEmpty() {
        when(repository.findCategoryByCode("NOPE")).thenReturn(Optional.empty());

        assertThat(service.updateCategory("NOPE", null, null, false)).isEmpty();
    }

    @Test
    void addKeyword_insertsNormalizedKeywordWithDefaultScope() {
        when(repository.findCategoryByCode("CULTURE")).thenReturn(Optional.of(CULTURE));
        when(repository.findKeyword(1L, "café théâtre")).thenReturn(Optional.empty());
        when(repository.insertKeyword(1L, "café théâtre", "ALL")).thenReturn(30L);
        when(repository.findKeywordById(30L)).thenReturn(Optional.of(
                new KeywordRow(30L, 1L, "café théâtre", "ALL", true)));

        Optional<KeywordView> keyword = service.addKeyword("CULTURE", "  Café   Théâtre ", null);

        assertThat(keyword).contains(new KeywordView(30L, "café théâtre", "ALL", true));
    }

    @Test
    void addKeyword_reactivatesAnInactiveDuplicate() {
        when(repository.findCategoryByCode("CULTURE")).thenReturn(Optional.of(CULTURE));
        when(repository.findKeyword(1L, "conférence")).thenReturn(Optional.of(
                new KeywordRow(11L, 1L, "conférence", "ALL", false)));
        when(repository.findKeywordById(11L)).thenReturn(Optional.of(
                new KeywordRow(11L, 1L, "conférence", "SOURCE", true)));

        assertThat(service.addKeyword("CULTURE", "conférence", "source")).isPresent();

        verify(repository).updateKeyword(11L, true, "SOURCE");
        verify(repository, never()).insertKeyword(anyLong(), anyString(), anyString());
    }

    @Test
    void addKeyword_conflictsWhenAlreadyActive_andRejectsBadInput() {
        when(repository.findCategoryByCode("CULTURE")).thenReturn(Optional.of(CULTURE));
        when(repository.findKeyword(1L, "concert")).thenReturn(Optional.of(
                new KeywordRow(10L, 1L, "concert", "ALL", true)));

        assertThatThrownBy(() -> service.addKeyword("CULTURE", "Concert", null)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.addKeyword("CULTURE", "  ", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.addKeyword("CULTURE", "opéra", "TITLE"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateKeyword_deactivatesAndKeepsScope() {
        when(repository.findKeywordById(10L)).thenReturn(
                Optional.of(new KeywordRow(10L, 1L, "concert", "SOURCE", true)),
                Optional.of(new KeywordRow(10L, 1L, "concert", "SOURCE", false)));

        Optional<KeywordView> updated = service.updateKeyword(10L, false, null);

        verify(repository).updateKeyword(10L, false, "SOURCE");
        assertThat(updated).map(KeywordView::active).contains(false);
    }

    @Test
    void updateKeyword_unknownId_isEmpty() {
        when(repository.findKeywordById(99L)).thenReturn(Optional.empty());

        assertThat(service.updateKeyword(99L, false, null)).isEmpty();
    }
}
