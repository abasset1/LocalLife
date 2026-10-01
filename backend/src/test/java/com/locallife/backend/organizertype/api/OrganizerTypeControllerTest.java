package com.locallife.backend.organizertype.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.locallife.backend.organizertype.application.OrganizerTypeService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class OrganizerTypeControllerTest {

    @Mock
    private OrganizerTypeService organizerTypeService;

    @InjectMocks
    private OrganizerTypeController organizerTypeController;

    @Test
    void getAllOrganizerTypes_ShouldReturnListOfOrganizerTypes() {
        // Given : deux des 9 types d'organisateurs V1 (CAT-004), label fr-FR résolu
        List<OrganizerTypeResponse> types = List.of(
                new OrganizerTypeResponse("ASSOCIATION", "Association", "Association loi 1901...", true),
                new OrganizerTypeResponse("MUNICIPALITY", "Municipalité", "Commune ou intercommunalité.", true)
        );
        when(organizerTypeService.getAllOrganizerTypes("fr-FR")).thenReturn(types);

        // When
        ResponseEntity<List<OrganizerTypeResponse>> response = organizerTypeController.getAllOrganizerTypes("fr-FR");

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().size());
        assertEquals("ASSOCIATION", response.getBody().get(0).code());
    }

    @Test
    void getAllOrganizerTypes_ShouldReturnEmptyList_WhenNoOrganizerTypes() {
        // Given
        when(organizerTypeService.getAllOrganizerTypes("fr-FR")).thenReturn(List.of());

        // When
        ResponseEntity<List<OrganizerTypeResponse>> response = organizerTypeController.getAllOrganizerTypes("fr-FR");

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(0, response.getBody().size());
    }

}
