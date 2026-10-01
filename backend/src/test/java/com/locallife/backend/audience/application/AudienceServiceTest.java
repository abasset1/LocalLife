package com.locallife.backend.audience.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.locallife.backend.audience.api.AudienceResponse;
import com.locallife.backend.audience.domain.Audience;
import com.locallife.backend.audience.domain.AudienceTranslation;
import com.locallife.backend.audience.infrastructure.AudienceRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Couvre la résolution de traduction (CAT-003), même principe que
 * {@code CategoryServiceTest} (CAT-001) et {@code ActivityTypeServiceTest}
 * (CAT-002).
 */
@ExtendWith(MockitoExtension.class)
class AudienceServiceTest {

    @Mock
    private AudienceRepository audienceRepository;

    @InjectMocks
    private AudienceService audienceService;

    private Audience audienceWithFrTranslation() {
        AudienceTranslation fr = new AudienceTranslation(1L, "fr-FR", "Famille", "Description FR");
        return new Audience(1L, "FAMILY", true, LocalDateTime.now(), LocalDateTime.now(), List.of(fr));
    }

    @Test
    void getAllAudiences_ShouldResolveRequestedLocale_WhenPresent() {
        when(audienceRepository.findAll()).thenReturn(List.of(audienceWithFrTranslation()));

        List<AudienceResponse> result = audienceService.getAllAudiences("fr-FR");

        assertEquals(1, result.size());
        assertEquals("FAMILY", result.get(0).code());
        assertEquals("Famille", result.get(0).label());
        assertEquals("Description FR", result.get(0).description());
    }

    @Test
    void getAllAudiences_ShouldFallBackToFrFr_WhenRequestedLocaleMissing() {
        when(audienceRepository.findAll()).thenReturn(List.of(audienceWithFrTranslation()));

        List<AudienceResponse> result = audienceService.getAllAudiences("en-US");

        assertEquals("Famille", result.get(0).label());
    }

    @Test
    void getAllAudiences_ShouldFallBackToCode_WhenNoTranslationAtAll() {
        Audience withoutTranslation =
                new Audience(2L, "SENIORS", true, LocalDateTime.now(), LocalDateTime.now(), List.of());
        when(audienceRepository.findAll()).thenReturn(List.of(withoutTranslation));

        List<AudienceResponse> result = audienceService.getAllAudiences("fr-FR");

        assertEquals("SENIORS", result.get(0).label());
        assertEquals(null, result.get(0).description());
    }

    @Test
    void getAudienceByCode_ShouldReturnEmpty_WhenCodeUnknown() {
        when(audienceRepository.findByCode("UNKNOWN")).thenReturn(Optional.empty());

        Optional<AudienceResponse> result = audienceService.getAudienceByCode("UNKNOWN", "fr-FR");

        assertEquals(Optional.empty(), result);
    }

}
