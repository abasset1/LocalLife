package com.locallife.backend.activityclassification.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.locallife.backend.activityclassification.domain.ActivityActivityType;
import com.locallife.backend.activityclassification.domain.ActivityAudience;
import com.locallife.backend.activityclassification.domain.ActivityOrganizerType;
import com.locallife.backend.activityclassification.domain.ActivityPrimaryCategory;
import com.locallife.backend.activityclassification.domain.ActivitySecondaryCategory;
import com.locallife.backend.activityclassification.infrastructure.ActivityActivityTypeRepository;
import com.locallife.backend.activityclassification.infrastructure.ActivityAudienceRepository;
import com.locallife.backend.activityclassification.infrastructure.ActivityOrganizerTypeRepository;
import com.locallife.backend.activityclassification.infrastructure.ActivityPrimaryCategoryRepository;
import com.locallife.backend.activityclassification.infrastructure.ActivitySecondaryCategoryRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Couvre les critères d'acceptation CAT-006 : toutes les dimensions sont
 * supportées, la catégorie principale est obligatoire, les secondaires
 * sont facultatives, types/publics/organisateur sont séparés des
 * catégories, et les relations restent cohérentes et sans doublons.
 */
@ExtendWith(MockitoExtension.class)
class ActivityClassificationServiceTest {

    private static final Long ACTIVITY_ID = 42L;

    @Mock
    private ActivityPrimaryCategoryRepository primaryCategoryRepository;
    @Mock
    private ActivitySecondaryCategoryRepository secondaryCategoryRepository;
    @Mock
    private ActivityActivityTypeRepository activityTypeRepository;
    @Mock
    private ActivityAudienceRepository audienceRepository;
    @Mock
    private ActivityOrganizerTypeRepository organizerTypeRepository;

    @InjectMocks
    private ActivityClassificationService classificationService;

    @Test
    void getClassification_ShouldAggregateAllFiveDimensions() {
        when(primaryCategoryRepository.findByActivityId(ACTIVITY_ID))
                .thenReturn(Optional.of(new ActivityPrimaryCategory(1L, ACTIVITY_ID, 100L)));
        when(secondaryCategoryRepository.findByActivityId(ACTIVITY_ID))
                .thenReturn(List.of(new ActivitySecondaryCategory(2L, ACTIVITY_ID, 101L)));
        when(activityTypeRepository.findByActivityId(ACTIVITY_ID))
                .thenReturn(List.of(new ActivityActivityType(3L, ACTIVITY_ID, 200L)));
        when(audienceRepository.findByActivityId(ACTIVITY_ID))
                .thenReturn(List.of(new ActivityAudience(4L, ACTIVITY_ID, 300L)));
        when(organizerTypeRepository.findByActivityId(ACTIVITY_ID))
                .thenReturn(Optional.of(new ActivityOrganizerType(5L, ACTIVITY_ID, 400L)));

        ActivityClassification result = classificationService.getClassification(ACTIVITY_ID);

        assertEquals(100L, result.primaryCategoryId());
        assertEquals(List.of(101L), result.secondaryCategoryIds());
        assertEquals(List.of(200L), result.activityTypeIds());
        assertEquals(List.of(300L), result.audienceIds());
        assertEquals(400L, result.organizerTypeId());
    }

    @Test
    void getClassification_ShouldReturnNullPrimaryCategory_WhenNotYetClassified() {
        when(primaryCategoryRepository.findByActivityId(ACTIVITY_ID)).thenReturn(Optional.empty());
        when(secondaryCategoryRepository.findByActivityId(ACTIVITY_ID)).thenReturn(List.of());
        when(activityTypeRepository.findByActivityId(ACTIVITY_ID)).thenReturn(List.of());
        when(audienceRepository.findByActivityId(ACTIVITY_ID)).thenReturn(List.of());
        when(organizerTypeRepository.findByActivityId(ACTIVITY_ID)).thenReturn(Optional.empty());

        ActivityClassification result = classificationService.getClassification(ACTIVITY_ID);

        assertNull(result.primaryCategoryId());
        assertNull(result.organizerTypeId());
    }

    @Test
    void setPrimaryCategory_ShouldReplaceExisting() {
        classificationService.setPrimaryCategory(ACTIVITY_ID, 100L);

        verify(primaryCategoryRepository).deleteByActivityId(ACTIVITY_ID);
        verify(primaryCategoryRepository).save(new ActivityPrimaryCategory(null, ACTIVITY_ID, 100L));
    }

    @Test
    void setPrimaryCategory_ShouldReject_WhenCategoryIdIsNull() {
        // Critère : la catégorie principale est obligatoire
        assertThrows(IllegalArgumentException.class,
                () -> classificationService.setPrimaryCategory(ACTIVITY_ID, null));

        verify(primaryCategoryRepository, never()).save(any());
    }

    @Test
    void replaceSecondaryCategories_ShouldAcceptEmptyList() {
        // Critère : les secondaires sont facultatives
        when(primaryCategoryRepository.findByActivityId(ACTIVITY_ID)).thenReturn(Optional.empty());

        classificationService.replaceSecondaryCategories(ACTIVITY_ID, List.of());

        verify(secondaryCategoryRepository).deleteByActivityId(ACTIVITY_ID);
        verify(secondaryCategoryRepository, never()).save(any());
    }

    @Test
    void replaceSecondaryCategories_ShouldDeduplicate() {
        when(primaryCategoryRepository.findByActivityId(ACTIVITY_ID)).thenReturn(Optional.empty());

        classificationService.replaceSecondaryCategories(ACTIVITY_ID, List.of(101L, 101L, 102L));

        verify(secondaryCategoryRepository, times(2)).save(any());
    }

    @Test
    void replaceSecondaryCategories_ShouldReject_WhenDuplicatesPrimaryCategory() {
        // Critère : relations cohérentes et sans doublons (catégorie
        // principale/secondaire identique pour la même activité)
        when(primaryCategoryRepository.findByActivityId(ACTIVITY_ID))
                .thenReturn(Optional.of(new ActivityPrimaryCategory(1L, ACTIVITY_ID, 100L)));

        assertThrows(IllegalArgumentException.class,
                () -> classificationService.replaceSecondaryCategories(ACTIVITY_ID, List.of(100L, 101L)));

        verify(secondaryCategoryRepository, never()).deleteByActivityId(any());
    }

    @Test
    void replaceActivityTypes_ShouldAcceptEmptyList() {
        classificationService.replaceActivityTypes(ACTIVITY_ID, List.of());

        verify(activityTypeRepository).deleteByActivityId(ACTIVITY_ID);
        verify(activityTypeRepository, never()).save(any());
    }

    @Test
    void replaceAudiences_ShouldAcceptMultipleValues() {
        // Exemple du sprint : AUDIENCE = CHILDREN, FAMILY (une activité
        // peut porter plusieurs publics à la fois)
        classificationService.replaceAudiences(ACTIVITY_ID, List.of(300L, 301L));

        verify(audienceRepository, times(2)).save(any());
    }

    @Test
    void setOrganizerType_ShouldAcceptNull_AsDefinitiveAbsence() {
        // Critère : l'organisateur est 0..1, contrairement à la catégorie
        // principale qui est obligatoire
        classificationService.setOrganizerType(ACTIVITY_ID, null);

        verify(organizerTypeRepository).deleteByActivityId(ACTIVITY_ID);
        verify(organizerTypeRepository, never()).save(any());
    }

    @Test
    void setOrganizerType_ShouldReplaceExisting_WhenProvided() {
        classificationService.setOrganizerType(ACTIVITY_ID, 400L);

        verify(organizerTypeRepository).deleteByActivityId(ACTIVITY_ID);
        verify(organizerTypeRepository).save(new ActivityOrganizerType(null, ACTIVITY_ID, 400L));
    }

}
