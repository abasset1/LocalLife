package com.locallife.backend.audience.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.locallife.backend.audience.application.AudienceService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class AudienceControllerTest {

    @Mock
    private AudienceService audienceService;

    @InjectMocks
    private AudienceController audienceController;

    @Test
    void getAllAudiences_ShouldReturnListOfAudiences() {
        // Given : deux des 7 publics V1 (CAT-003), avec leur label fr-FR résolu
        List<AudienceResponse> audiences = List.of(
                new AudienceResponse("FAMILY", "Famille", "Activité pensée pour être vécue en famille...", true),
                new AudienceResponse("CHILDREN", "Enfants", "Activité destinée aux enfants.", true)
        );
        when(audienceService.getAllAudiences("fr-FR")).thenReturn(audiences);

        // When
        ResponseEntity<List<AudienceResponse>> response = audienceController.getAllAudiences("fr-FR");

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().size());
        assertEquals("FAMILY", response.getBody().get(0).code());
    }

    @Test
    void getAllAudiences_ShouldReturnEmptyList_WhenNoAudiences() {
        // Given
        when(audienceService.getAllAudiences("fr-FR")).thenReturn(List.of());

        // When
        ResponseEntity<List<AudienceResponse>> response = audienceController.getAllAudiences("fr-FR");

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(0, response.getBody().size());
    }

}
