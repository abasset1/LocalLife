package com.locallife.backend.source.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.locallife.backend.category.domain.Category;
import com.locallife.backend.category.infrastructure.CategoryRepository;
import com.locallife.backend.source.domain.Source;
import com.locallife.backend.source.infrastructure.SourceRepository;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link SourceDefaultCategoryService} contre la vraie base (table {@code source_default_category},
 * V56). Transactionnel : tout est annulé en fin de test, rien ne reste en base.
 */
@SpringBootTest
@Transactional
class SourceDefaultCategoryServiceIntegrationTest {

    @Autowired
    private SourceDefaultCategoryService service;

    @Autowired
    private SourceRepository sourceRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private Long newSourceId() {
        return sourceRepository.save(new Source(
                null, "Source de test " + UUID.randomUUID(), "API", null, "ACTIVE", null, null, null)).id();
    }

    private Long categoryId(String code) {
        return categoryRepository.findByCode(code).map(Category::id).orElseThrow();
    }

    @Test
    void aSourceHasNoDefaultCategory_UntilOneIsSet() {
        Long sourceId = newSourceId();

        assertThat(service.findCategoryIds(sourceId)).isEmpty();
        assertThat(service.findCategoryCodes(sourceId)).isEmpty();
        assertThat(service.findAllCategoryCodes()).doesNotContainKey(sourceId);
    }

    @Test
    void replace_ShouldStoreTheGivenCategories_WithoutDuplicates() {
        Long sourceId = newSourceId();

        List<String> stored = service.replace(sourceId, List.of("CULTURE", " TOURISM ", "CULTURE", " "));

        assertThat(stored).containsExactly("CULTURE", "TOURISM");
        assertThat(service.findCategoryCodes(sourceId)).containsExactlyInAnyOrder("CULTURE", "TOURISM");
        assertThat(service.findCategoryIds(sourceId))
                .containsExactlyInAnyOrder(categoryId("CULTURE"), categoryId("TOURISM"));
        assertThat(service.findAllCategoryCodes().get(sourceId)).containsExactlyInAnyOrder("CULTURE", "TOURISM");
    }

    @Test
    void replace_ShouldReplaceThePreviousCategories_AndAnEmptyListClearsThem() {
        Long sourceId = newSourceId();
        service.replace(sourceId, List.of("CULTURE", "TOURISM"));

        service.replace(sourceId, List.of("SPORT"));
        assertThat(service.findCategoryCodes(sourceId)).containsExactly("SPORT");

        service.replace(sourceId, List.of());
        assertThat(service.findCategoryCodes(sourceId)).isEmpty();
    }

    @Test
    void replace_ShouldRejectAnUnknownCode_AndKeepThePreviousCategories() {
        Long sourceId = newSourceId();
        service.replace(sourceId, List.of("CULTURE"));

        assertThatThrownBy(() -> service.replace(sourceId, List.of("SPORT", "CATEGORIE_INEXISTANTE")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CATEGORIE_INEXISTANTE");

        assertThat(service.findCategoryCodes(sourceId)).containsExactly("CULTURE");
    }

    @Test
    void replace_ShouldFail_WhenTheSourceDoesNotExist() {
        assertThatThrownBy(() -> service.replace(-1L, List.of("CULTURE")))
                .isInstanceOf(NoSuchElementException.class);
    }

}
