package com.locallife.backend.activityclassification.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

import com.locallife.backend.activity.domain.Activity;
import com.locallife.backend.activity.infrastructure.ActivityRepository;
import com.locallife.backend.activityclassification.domain.ActivityActivityType;
import com.locallife.backend.activityclassification.domain.ActivityAudience;
import com.locallife.backend.activityclassification.domain.ActivityCategory;
import com.locallife.backend.activityclassification.domain.ActivityOrganizerType;
import com.locallife.backend.activityclassification.infrastructure.ActivityActivityTypeRepository;
import com.locallife.backend.activityclassification.infrastructure.ActivityAudienceRepository;
import com.locallife.backend.activityclassification.infrastructure.ActivityCategoryRepository;
import com.locallife.backend.activityclassification.infrastructure.ActivityOrganizerTypeRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Couvre les critères CAT-006 et de l'issue #45 : toutes les dimensions sont
 * supportées, une activité a 1..N catégories (aucune principale ni
 * secondaire, au moins une catégorie obligatoire — réellement en base, voir
 * {@code V42__activity_category_n_categories.sql}), types/publics/organisateur
 * sont séparés des catégories, et les relations restent sans doublons.
 */
@ExtendWith(MockitoExtension.class)
class ActivityClassificationServiceTest {

    private static final Long ACTIVITY_ID = 42L;

    @Mock
    private ActivityRepository activityRepository;
    @Mock
    private ActivityCategoryRepository categoryRepository;
    @Mock
    private ActivityActivityTypeRepository activityTypeRepository;
    @Mock
    private ActivityAudienceRepository audienceRepository;
    @Mock
    private ActivityOrganizerTypeRepository organizerTypeRepository;

    @InjectMocks
    private ActivityClassificationService classificationService;

    private Activity activity(Long id) {
        return new Activity(
                id, "Festival communal", "desc", "culture", 43.29, 5.37,
                LocalDateTime.now(), LocalDateTime.now(), "PUBLISHED", 1L, null, null,
                null, null, null, null, null, null, null, null);
    }

    @Test
    void getClassification_ShouldAggregateAllDimensions() {
        when(activityRepository.findById(ACTIVITY_ID)).thenReturn(Optional.of(activity(ACTIVITY_ID)));
        when(categoryRepository.findByActivityIdOrderByIdAsc(ACTIVITY_ID)).thenReturn(List.of(
                new ActivityCategory(1L, ACTIVITY_ID, 100L), new ActivityCategory(2L, ACTIVITY_ID, 101L)));
        when(activityTypeRepository.findByActivityId(ACTIVITY_ID))
                .thenReturn(List.of(new ActivityActivityType(3L, ACTIVITY_ID, 200L)));
        when(audienceRepository.findByActivityId(ACTIVITY_ID))
                .thenReturn(List.of(new ActivityAudience(4L, ACTIVITY_ID, 300L)));
        when(organizerTypeRepository.findByActivityId(ACTIVITY_ID))
                .thenReturn(Optional.of(new ActivityOrganizerType(5L, ACTIVITY_ID, 400L)));

        ActivityClassification result = classificationService.getClassification(ACTIVITY_ID);

        assertEquals(List.of(100L, 101L), result.categoryIds());
        assertEquals(List.of(200L), result.activityTypeIds());
        assertEquals(List.of(300L), result.audienceIds());
        assertEquals(400L, result.organizerTypeId());
    }

    @Test
    void getClassification_ShouldThrow_WhenActivityNotFound() {
        when(activityRepository.findById(ACTIVITY_ID)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> classificationService.getClassification(ACTIVITY_ID));
    }

    @Test
    void getClassification_ShouldReturnNullOrganizerType_WhenNotSet() {
        // Critère : l'organisateur est 0..1, contrairement aux catégories (1..N)
        when(activityRepository.findById(ACTIVITY_ID)).thenReturn(Optional.of(activity(ACTIVITY_ID)));
        when(categoryRepository.findByActivityIdOrderByIdAsc(ACTIVITY_ID))
                .thenReturn(List.of(new ActivityCategory(1L, ACTIVITY_ID, 100L)));
        when(activityTypeRepository.findByActivityId(ACTIVITY_ID)).thenReturn(List.of());
        when(audienceRepository.findByActivityId(ACTIVITY_ID)).thenReturn(List.of());
        when(organizerTypeRepository.findByActivityId(ACTIVITY_ID)).thenReturn(Optional.empty());

        ActivityClassification result = classificationService.getClassification(ACTIVITY_ID);

        assertNull(result.organizerTypeId());
    }

    @Test
    void createActivity_ShouldSaveTheActivityThenAllItsCategories() {
        Activity toSave = activity(null);
        Activity saved = activity(ACTIVITY_ID);
        when(activityRepository.save(toSave)).thenReturn(saved);

        Activity result = classificationService.createActivity(toSave, List.of(100L, 101L, 102L));

        assertSame(saved, result);
        InOrder order = inOrder(activityRepository, categoryRepository);
        order.verify(activityRepository).save(toSave);
        order.verify(categoryRepository).save(new ActivityCategory(null, ACTIVITY_ID, 100L));
        order.verify(categoryRepository).save(new ActivityCategory(null, ACTIVITY_ID, 101L));
        order.verify(categoryRepository).save(new ActivityCategory(null, ACTIVITY_ID, 102L));
    }

    @Test
    void createActivity_ShouldDeduplicateCategories() {
        when(activityRepository.save(any())).thenReturn(activity(ACTIVITY_ID));

        classificationService.createActivity(activity(null), List.of(100L, 100L, 101L));

        verify(categoryRepository, times(2)).save(any());
    }

    @Test
    void createActivity_ShouldReject_WhenNoCategory() {
        // Critère : une activité a au moins une catégorie — rejetée ici en plus de la contrainte en base.
        assertThrows(IllegalArgumentException.class,
                () -> classificationService.createActivity(activity(null), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> classificationService.createActivity(activity(null), null));

        verify(activityRepository, never()).save(any());
    }

    @Test
    void replaceCategories_ShouldReplaceTheWholeSet() {
        when(activityRepository.findById(ACTIVITY_ID)).thenReturn(Optional.of(activity(ACTIVITY_ID)));

        classificationService.replaceCategories(ACTIVITY_ID, List.of(101L, 101L, 102L));

        verify(categoryRepository).deleteByActivityId(ACTIVITY_ID);
        verify(categoryRepository).save(new ActivityCategory(null, ACTIVITY_ID, 101L));
        verify(categoryRepository).save(new ActivityCategory(null, ACTIVITY_ID, 102L));
    }

    @Test
    void replaceCategories_ShouldReject_EmptyListOrNullCategory() {
        assertThrows(IllegalArgumentException.class,
                () -> classificationService.replaceCategories(ACTIVITY_ID, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> classificationService.replaceCategories(ACTIVITY_ID, java.util.Arrays.asList(100L, null)));

        verify(categoryRepository, never()).deleteByActivityId(any());
    }

    @Test
    void replaceCategories_ShouldThrow_WhenActivityNotFound() {
        when(activityRepository.findById(ACTIVITY_ID)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class,
                () -> classificationService.replaceCategories(ACTIVITY_ID, List.of(100L)));
        verify(categoryRepository, never()).deleteByActivityId(any());
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
        // Critère : l'organisateur est 0..1, contrairement aux catégories (1..N)
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
