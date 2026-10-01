package com.locallife.backend.activitytype.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.locallife.backend.activitytype.application.ActivityTypeService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class ActivityTypeControllerTest {

    @Mock
    private ActivityTypeService activityTypeService;

    @InjectMocks
    private ActivityTypeController activityTypeController;

    @Test
    void getAllActivityTypes_ShouldReturnListOfTypes() {
        // Given : deux des 38 types V1 (CAT-002), avec leur label fr-FR résolu
        List<ActivityTypeResponse> types = List.of(
                new ActivityTypeResponse("FESTIVAL", "Festival", "Événement pluridisciplinaire...", true),
                new ActivityTypeResponse("ATELIER", "Atelier", "Activité pratique encadrée...", true)
        );
        when(activityTypeService.getAllActivityTypes("fr-FR")).thenReturn(types);

        // When
        ResponseEntity<List<ActivityTypeResponse>> response = activityTypeController.getAllActivityTypes("fr-FR");

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().size());
        assertEquals("FESTIVAL", response.getBody().get(0).code());
    }

    @Test
    void getAllActivityTypes_ShouldReturnEmptyList_WhenNoTypes() {
        // Given
        when(activityTypeService.getAllActivityTypes("fr-FR")).thenReturn(List.of());

        // When
        ResponseEntity<List<ActivityTypeResponse>> response = activityTypeController.getAllActivityTypes("fr-FR");

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(0, response.getBody().size());
    }

}
