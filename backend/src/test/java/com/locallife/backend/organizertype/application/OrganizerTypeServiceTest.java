package com.locallife.backend.organizertype.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.locallife.backend.organizertype.api.OrganizerTypeResponse;
import com.locallife.backend.organizertype.domain.OrganizerType;
import com.locallife.backend.organizertype.domain.OrganizerTypeTranslation;
import com.locallife.backend.organizertype.infrastructure.OrganizerTypeRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Couvre la résolution de traduction (CAT-004), même principe que pour les
 * trois référentiels précédents.
 */
@ExtendWith(MockitoExtension.class)
class OrganizerTypeServiceTest {

    @Mock
    private OrganizerTypeRepository organizerTypeRepository;

    @InjectMocks
    private OrganizerTypeService organizerTypeService;

    private OrganizerType typeWithFrTranslation() {
        OrganizerTypeTranslation fr = new OrganizerTypeTranslation(1L, "fr-FR", "Association", "Description FR");
        return new OrganizerType(1L, "ASSOCIATION", true, LocalDateTime.now(), LocalDateTime.now(), Set.of(fr));
    }

    @Test
    void getAllOrganizerTypes_ShouldResolveRequestedLocale_WhenPresent() {
        when(organizerTypeRepository.findAll()).thenReturn(List.of(typeWithFrTranslation()));

        List<OrganizerTypeResponse> result = organizerTypeService.getAllOrganizerTypes("fr-FR");

        assertEquals(1, result.size());
        assertEquals("ASSOCIATION", result.get(0).code());
        assertEquals("Association", result.get(0).label());
        assertEquals("Description FR", result.get(0).description());
    }

    @Test
    void getAllOrganizerTypes_ShouldFallBackToFrFr_WhenRequestedLocaleMissing() {
        when(organizerTypeRepository.findAll()).thenReturn(List.of(typeWithFrTranslation()));

        List<OrganizerTypeResponse> result = organizerTypeService.getAllOrganizerTypes("en-US");

        assertEquals("Association", result.get(0).label());
    }

    @Test
    void getAllOrganizerTypes_ShouldFallBackToBaseLanguage_WhenRequestedRegionMissing() {
        // CAT-005 : fr-BE absent -> langue de base "fr" -> fr-FR (maillon intermédiaire
        // de la chaîne de repli, introduit par l'harmonisation CAT-005)
        when(organizerTypeRepository.findAll()).thenReturn(List.of(typeWithFrTranslation()));

        var result = organizerTypeService.getAllOrganizerTypes("fr-BE");

        assertEquals("Association", result.get(0).label());
    }

    @Test
    void getAllOrganizerTypes_ShouldFallBackToCode_WhenNoTranslationAtAll() {
        OrganizerType withoutTranslation =
                new OrganizerType(2L, "INDIVIDUAL", true, LocalDateTime.now(), LocalDateTime.now(), Set.of());
        when(organizerTypeRepository.findAll()).thenReturn(List.of(withoutTranslation));

        List<OrganizerTypeResponse> result = organizerTypeService.getAllOrganizerTypes("fr-FR");

        assertEquals("INDIVIDUAL", result.get(0).label());
        assertEquals(null, result.get(0).description());
    }

    @Test
    void getOrganizerTypeByCode_ShouldReturnEmpty_WhenCodeUnknown() {
        when(organizerTypeRepository.findByCode("UNKNOWN")).thenReturn(Optional.empty());

        Optional<OrganizerTypeResponse> result = organizerTypeService.getOrganizerTypeByCode("UNKNOWN", "fr-FR");

        assertEquals(Optional.empty(), result);
    }

}
