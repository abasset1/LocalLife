package com.locallife.backend.category.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.locallife.backend.activitytype.domain.ActivityType;
import com.locallife.backend.activitytype.infrastructure.ActivityTypeRepository;
import com.locallife.backend.audience.domain.Audience;
import com.locallife.backend.audience.infrastructure.AudienceRepository;
import com.locallife.backend.category.domain.Category;
import com.locallife.backend.category.domain.CategoryTranslation;
import com.locallife.backend.organizertype.domain.OrganizerType;
import com.locallife.backend.organizertype.infrastructure.OrganizerTypeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Régression : les 4 référentiels (catégories, types d'activité, types
 * d'organisateur, publics) doivent se charger depuis la base réelle avec leurs
 * traductions. Les services étaient testés avec des mocks seulement ; la
 * collection {@code translations} déclarée en {@code List} faisait chercher à
 * Spring Data JDBC une colonne de clé inexistante ({@code category_key}), donc
 * toute lecture réelle (ex. {@code categoryRepository.findByCode} à la création
 * d'une activité) échouait en 500. Elle est désormais un {@code Set}.
 */
@SpringBootTest
@Transactional
class ReferentialTranslationsIntegrationTest {

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ActivityTypeRepository activityTypeRepository;

    @Autowired
    private OrganizerTypeRepository organizerTypeRepository;

    @Autowired
    private AudienceRepository audienceRepository;

    @Test
    void findByCode_ShouldLoadCategoryWithItsFrenchTranslation() {
        Category culture = categoryRepository.findByCode("CULTURE").orElseThrow();

        assertThat(culture.translations())
                .extracting(CategoryTranslation::locale)
                .containsExactly("fr-FR");
    }

    @Test
    void findAll_ShouldLoadEveryReferentialWithTranslations() {
        assertThat(categoryRepository.findAll())
                .isNotEmpty()
                .allSatisfy(category -> assertThat(category.translations()).isNotEmpty());
        assertThat(activityTypeRepository.findAll())
                .isNotEmpty()
                .allSatisfy((ActivityType type) -> assertThat(type.translations()).isNotEmpty());
        assertThat(organizerTypeRepository.findAll())
                .isNotEmpty()
                .allSatisfy((OrganizerType type) -> assertThat(type.translations()).isNotEmpty());
        assertThat(audienceRepository.findAll())
                .isNotEmpty()
                .allSatisfy((Audience audience) -> assertThat(audience.translations()).isNotEmpty());
    }
}
