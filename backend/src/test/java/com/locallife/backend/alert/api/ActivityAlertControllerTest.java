package com.locallife.backend.alert.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.locallife.backend.alert.api.ActivityAlertController.AlertResponse;
import com.locallife.backend.alert.api.ActivityAlertController.CreateAlertRequest;
import com.locallife.backend.alert.api.ActivityAlertController.UnsubscribeRequest;
import com.locallife.backend.alert.application.ActivityAlertService;
import com.locallife.backend.alert.domain.ActivityAlert;
import com.locallife.backend.auth.application.JwtAuthentication;
import com.locallife.backend.geocoding.application.CityNotFoundException;
import com.locallife.backend.geocoding.application.GeocodingUnavailableException;
import com.locallife.backend.user.domain.Role;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class ActivityAlertControllerTest {

    @Mock
    private ActivityAlertService activityAlertService;

    @Mock
    private HttpServletRequest httpRequest;

    @InjectMocks
    private ActivityAlertController controller;

    private final JwtAuthentication authentication = new JwtAuthentication(7L, "alice@example.com", Role.USER);
    private final ActivityAlert alert =
            new ActivityAlert(3L, 7L, "Lyon", 45.76, 4.83, 10, List.of("SPORT"), LocalDateTime.now());

    @Test
    void getAlerts_ShouldUseUserIdFromJwt() {
        when(activityAlertService.getAlerts(7L)).thenReturn(List.of(alert));

        List<AlertResponse> alerts = controller.getAlerts(authentication);

        assertEquals(List.of(new AlertResponse(3L, List.of("SPORT"), "Lyon", 45.76, 4.83, 10)), alerts);
    }

    @Test
    void createAlert_ShouldReturnCreated() {
        when(activityAlertService.createAlert(7L, List.of("SPORT"), "Lyon", null, null, 10)).thenReturn(alert);

        ResponseEntity<Object> response = controller.createAlert(
                authentication, new CreateAlertRequest(List.of("SPORT"), "Lyon", null, null, 10), httpRequest);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    @Test
    void createAlert_ShouldMapErrors() {
        when(activityAlertService.createAlert(7L, List.of(), "Lyon", null, null, 10))
                .thenThrow(new IllegalArgumentException("Choisis au moins une catégorie."));
        when(activityAlertService.createAlert(7L, List.of("SPORT"), "Zzzz", null, null, 10))
                .thenThrow(new CityNotFoundException("Ville introuvable."));
        when(activityAlertService.createAlert(7L, List.of("SPORT"), "Lyon", null, null, 10))
                .thenThrow(new GeocodingUnavailableException(new RuntimeException("down")));

        assertEquals(HttpStatus.BAD_REQUEST, controller.createAlert(authentication,
                new CreateAlertRequest(List.of(), "Lyon", null, null, 10), httpRequest).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, controller.createAlert(authentication,
                new CreateAlertRequest(List.of("SPORT"), "Zzzz", null, null, 10), httpRequest).getStatusCode());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, controller.createAlert(authentication,
                new CreateAlertRequest(List.of("SPORT"), "Lyon", null, null, 10), httpRequest).getStatusCode());
    }

    @Test
    void deleteAlert_ShouldReturnNotFound_WhenNotOwned() {
        doThrow(new NoSuchElementException("Alerte introuvable.")).when(activityAlertService).deleteAlert(7L, 99L);

        assertEquals(HttpStatus.NOT_FOUND, controller.deleteAlert(authentication, 99L, httpRequest).getStatusCode());
        assertEquals(HttpStatus.NO_CONTENT, controller.deleteAlert(authentication, 3L, httpRequest).getStatusCode());
    }

    @Test
    void unsubscribe_ShouldReturnNoContent_OrNotFound() {
        when(activityAlertService.unsubscribe("good")).thenReturn(true);
        when(activityAlertService.unsubscribe("used")).thenReturn(false);

        assertEquals(HttpStatus.NO_CONTENT,
                controller.unsubscribe(new UnsubscribeRequest("good"), httpRequest).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND,
                controller.unsubscribe(new UnsubscribeRequest("used"), httpRequest).getStatusCode());
    }

}
