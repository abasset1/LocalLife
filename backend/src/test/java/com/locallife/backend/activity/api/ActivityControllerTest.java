package com.locallife.backend.activity.api;

import com.locallife.backend.activity.application.ActivityService;
import com.locallife.backend.activity.domain.Activity;
import com.locallife.backend.common.ErrorResponse;
import com.locallife.backend.geocoding.application.AddressNotFoundException;
import com.locallife.backend.geocoding.application.GeocodingUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivityControllerTest {

    @Mock
    private ActivityService activityService;

    @Mock
    private HttpServletRequest httpRequest;

    @InjectMocks
    private ActivityController activityController;

    @Test
    void getAllActivities_ShouldReturnListOfActivities() {
        // Given
        List<Activity> activities = List.of(
                new Activity(1L, "Test Activity", "Description", "Category", 0.0, 0.0,
                        LocalDateTime.now(), LocalDateTime.now(), "ACTIVE"),
                new Activity(2L, "Another Activity", "Another Description", "Another Category", 1.0, 1.0,
                        LocalDateTime.now(), LocalDateTime.now(), "ACTIVE")
        );
        when(activityService.findAll()).thenReturn(activities);

        // When
        ResponseEntity<List<Activity>> response = activityController.getAllActivities();

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().size());
    }

    @Test
    void getAllActivities_ShouldReturnEmptyList_WhenNoActivities() {
        // Given
        when(activityService.findAll()).thenReturn(List.of());

        // When
        ResponseEntity<List<Activity>> response = activityController.getAllActivities();

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(0, response.getBody().size());
    }

    @Test
    void getActivityById_ShouldReturnActivity_WhenFound() {
        // Given
        Activity activity = new Activity(1L, "Test Activity", "Description", "Category", 0.0, 0.0,
                LocalDateTime.now(), LocalDateTime.now(), "ACTIVE");
        when(activityService.findById(1L)).thenReturn(Optional.of(activity));

        // When
        ResponseEntity<Activity> response = activityController.getActivityById(1L);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Test Activity", response.getBody().title());
        assertEquals("Description", response.getBody().description());
    }

    @Test
    void getActivityById_ShouldReturnNotFound_WhenNotFound() {
        // Given
        when(activityService.findById(999L)).thenReturn(Optional.empty());

        // When
        ResponseEntity<Activity> response = activityController.getActivityById(999L);

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void createActivity_ShouldReturnCreated_WithActivity() {
        // Given
        Activity created = new Activity(1L, "Pique-nique", "Pique-nique au parc", "loisir", 43.29, 5.37,
                LocalDateTime.now(), null, "PENDING");
        when(activityService.createActivity(
                "Pique-nique", "Pique-nique au parc", "loisir", "1 rue de la Paix, Marseille"))
                .thenReturn(created);

        // When
        ResponseEntity<Object> response = activityController.createActivity(
                new ActivityController.CreateActivityRequest(
                        "Pique-nique", "Pique-nique au parc", "loisir", "1 rue de la Paix, Marseille"),
                httpRequest);

        // Then
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        Activity body = (Activity) response.getBody();
        assertEquals("Pique-nique", body.title());
        assertEquals("PENDING", body.status());
    }

    @Test
    void createActivity_ShouldReturnBadRequest_WhenAddressNotFound() {
        // Given
        when(activityService.createActivity("Pique-nique", "Pique-nique au parc", "loisir", "adresse inconnue"))
                .thenThrow(new AddressNotFoundException("adresse inconnue"));
        when(httpRequest.getRequestURI()).thenReturn("/api/v1/activities");

        // When
        ResponseEntity<Object> response = activityController.createActivity(
                new ActivityController.CreateActivityRequest(
                        "Pique-nique", "Pique-nique au parc", "loisir", "adresse inconnue"),
                httpRequest);

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST.value(), ((ErrorResponse) response.getBody()).status());
    }

    @Test
    void createActivity_ShouldReturnServiceUnavailable_WhenGeocodingFails() {
        // Given
        when(activityService.createActivity("Pique-nique", "Pique-nique au parc", "loisir", "1 rue de la Paix"))
                .thenThrow(new GeocodingUnavailableException(new RuntimeException("timeout")));
        when(httpRequest.getRequestURI()).thenReturn("/api/v1/activities");

        // When
        ResponseEntity<Object> response = activityController.createActivity(
                new ActivityController.CreateActivityRequest(
                        "Pique-nique", "Pique-nique au parc", "loisir", "1 rue de la Paix"),
                httpRequest);

        // Then
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), ((ErrorResponse) response.getBody()).status());
    }

    @Test
    void getNearbyActivities_ShouldReturnOk_WithActivities() {
        // Given
        Activity nearby = new Activity(1L, "Concert", "Description", "concert", 43.29, 5.37,
                LocalDateTime.now(), null, "PUBLISHED");
        when(activityService.findNearby("43.2951", "5.3739", "5", "PUBLISHED", "concert"))
                .thenReturn(List.of(nearby));

        // When
        ResponseEntity<Object> response = activityController.getNearbyActivities(
                "43.2951", "5.3739", "5", "PUBLISHED", "concert", httpRequest);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(List.of(nearby), response.getBody());
    }

    @Test
    void getNearbyActivities_ShouldReturnBadRequest_WhenParamsInvalid() {
        // Given
        when(activityService.findNearby(null, "5.3739", "5", null, null))
                .thenThrow(new IllegalArgumentException("Le paramètre 'latitude' est obligatoire."));
        when(httpRequest.getRequestURI()).thenReturn("/api/v1/activities/nearby");

        // When
        ResponseEntity<Object> response = activityController.getNearbyActivities(
                null, "5.3739", "5", null, null, httpRequest);

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST.value(), ((ErrorResponse) response.getBody()).status());
    }
}
