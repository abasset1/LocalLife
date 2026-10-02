package com.locallife.backend.activitytype.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.locallife.backend.activitytype.api.ActivityTypeResponse;
import com.locallife.backend.activitytype.domain.ActivityType;
import com.locallife.backend.activitytype.domain.ActivityTypeTranslation;
import com.locallife.backend.activitytype.infrastructure.ActivityTypeRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Couvre la résolution de traduction (CAT-002), seule logique nouvelle
 * introduite par ce ticket — même couverture que {@code CategoryServiceTest}
 * (CAT-001) pour un principe identique.
 */
@ExtendWith(MockitoExtension.class)
class ActivityTypeServiceTest {

    @Mock
    private ActivityTypeRepository activityTypeRepository;

    @InjectMocks
    private ActivityTypeService activityTypeService;

    private ActivityType typeWithFrTranslation() {
        ActivityTypeTranslation fr = new ActivityTypeTranslation(1L, "fr-FR", "Festival", "Description FR");
        return new ActivityType(1L, "FESTIVAL", true, LocalDateTime.now(), LocalDateTime.now(), List.of(fr));
    }

    @Test
    void getAllActivityTypes_ShouldResolveRequestedLocale_WhenPresent() {
        when(activityTypeRepository.findAll()).thenReturn(List.of(typeWithFrTranslation()));

        List<ActivityTypeResponse> result = activityTypeService.getAllActivityTypes("fr-FR");

        assertEquals(1, result.size());
        assertEquals("FESTIVAL", result.get(0).code());
        assertEquals("Festival", result.get(0).label());
        assertEquals("Description FR", result.get(0).description());
    }

    @Test
    void getAllActivityTypes_ShouldFallBackToFrFr_WhenRequestedLocaleMissing() {
        when(activityTypeRepository.findAll()).thenReturn(List.of(typeWithFrTranslation()));

        List<ActivityTypeResponse> result = activityTypeService.getAllActivityTypes("en-US");

        assertEquals("Festival", result.get(0).label());
    }

    @Test
    void getAllActivityTypes_ShouldFallBackToBaseLanguage_WhenRequestedRegionMissing() {
        // CAT-005 : fr-BE absent -> langue de base "fr" -> fr-FR (maillon intermédiaire
        // de la chaîne de repli, introduit par l'harmonisation CAT-005)
        when(activityTypeRepository.findAll()).thenReturn(List.of(typeWithFrTranslation()));

        var result = activityTypeService.getAllActivityTypes("fr-BE");

        assertEquals("Festival", result.get(0).label());
    }

    @Test
    void getAllActivityTypes_ShouldFallBackToCode_WhenNoTranslationAtAll() {
        ActivityType withoutTranslation =
                new ActivityType(2L, "ATELIER", true, LocalDateTime.now(), LocalDateTime.now(), List.of());
        when(activityTypeRepository.findAll()).thenReturn(List.of(withoutTranslation));

        List<ActivityTypeResponse> result = activityTypeService.getAllActivityTypes("fr-FR");

        assertEquals("ATELIER", result.get(0).label());
        assertEquals(null, result.get(0).description());
    }

    @Test
    void getActivityTypeByCode_ShouldReturnEmpty_WhenCodeUnknown() {
        when(activityTypeRepository.findByCode("UNKNOWN")).thenReturn(Optional.empty());

        Optional<ActivityTypeResponse> result = activityTypeService.getActivityTypeByCode("UNKNOWN", "fr-FR");

        assertEquals(Optional.empty(), result);
    }

}
