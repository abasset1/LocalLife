package com.locallife.backend.activityclassification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.locallife.backend.activityclassification.application.ActivityClassifier.Decision;
import com.locallife.backend.activityclassification.domain.ClassificationStatus;
import com.locallife.backend.activityclassification.infrastructure.CategoryBackfillRepository;
import com.locallife.backend.activityclassification.infrastructure.CategoryBackfillRepository.Candidate;
import com.locallife.backend.category.domain.Category;
import com.locallife.backend.category.infrastructure.CategoryRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CategoryBackfillServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 4, 0, 0);
    private static final Long CHRISTMAS = 11L;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private CategoryBackfillRepository backfillRepository;

    @Mock
    private ClassificationService classificationService;

    @InjectMocks
    private CategoryBackfillService service;

    private static ClassificationOutcome classifiedAs(Long... ids) {
        return new ClassificationOutcome(
                new Decision(ClassificationStatus.AUTO_CLASSIFIED, List.of("X"), null, List.of(), "AUTO:X"),
                List.of(ids));
    }

    @Test
    void addsTheCategoryOnlyToActivitiesWhoseDecisionContainsIt() {
        when(categoryRepository.findByCode("MARCHES_NOEL"))
                .thenReturn(Optional.of(new Category(CHRISTMAS, "MARCHES_NOEL", true, NOW, NOW, Set.of())));
        when(backfillRepository.findCandidates(CHRISTMAS)).thenReturn(List.of(
                new Candidate(1L, "Grand marché de Noël", "Marché", List.of("fête")),
                new Candidate(2L, "Concert du soir", null, List.of())));
        when(classificationService.decide("Grand marché de Noël", "Marché", List.of("fête")))
                .thenReturn(classifiedAs(1L, CHRISTMAS));
        when(classificationService.decide("Concert du soir", null, List.of())).thenReturn(classifiedAs(3L));
        when(backfillRepository.addCategory(CHRISTMAS, List.of(1L))).thenReturn(1);

        CategoryBackfillService.BackfillResult result = service.applyToExistingActivities("MARCHES_NOEL").orElseThrow();

        assertThat(result.examined()).isEqualTo(2);
        assertThat(result.added()).isEqualTo(1);
        InOrder order = inOrder(classificationService);
        order.verify(classificationService).beginBatch();
        order.verify(classificationService).endBatch();
    }

    @Test
    void unknownCategoryIsEmpty_AndAnInactiveOneIsRefused() {
        when(categoryRepository.findByCode("INCONNUE")).thenReturn(Optional.empty());
        when(categoryRepository.findByCode("ANCIENNE"))
                .thenReturn(Optional.of(new Category(12L, "ANCIENNE", false, NOW, NOW, Set.of())));

        assertThat(service.applyToExistingActivities("INCONNUE")).isEmpty();
        assertThrows(IllegalArgumentException.class, () -> service.applyToExistingActivities("ANCIENNE"));
        verify(backfillRepository, never()).addCategory(org.mockito.ArgumentMatchers.any(), anyList());
    }
}
