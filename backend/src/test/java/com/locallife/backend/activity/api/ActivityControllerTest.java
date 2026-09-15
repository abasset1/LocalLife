package com.locallife.backend.activity.api;

import com.locallife.backend.activity.application.ActivityAccessDeniedException;
import com.locallife.backend.activity.application.ActivityService;
import com.locallife.backend.activity.domain.Activity;
import com.locallife.backend.auth.application.JwtAuthentication;
import com.locallife.backend.common.ErrorResponse;
import com.locallife.backend.geocoding.application.AddressNotFoundException;
import com.locallife.backend.geocoding.application.GeocodingUnavailableException;
import com.locallife.backend.like.application.LikeService;
import com.locallife.backend.source.application.SourceService;
import com.locallife.backend.source.domain.Source;
import com.locallife.backend.user.application.UserService;
import com.locallife.backend.user.domain.Role;
import com.locallife.backend.user.domain.User;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivityControllerTest {

    @Mock
    private ActivityService activityService;

    @Mock
    private SourceService sourceService;

    @Mock
    private UserService userService;

    @Mock
    private LikeService likeService;

    @Mock
    private HttpServletRequest httpRequest;

    @InjectMocks
    private ActivityController activityController;

    /**
     * Instance réelle plutôt qu'un mock Mockito (même pattern que
     * {@code UserControllerTest}) : {@link JwtAuthentication} est un
     * simple porte-valeurs, pas la peine de le mocker. Utilisateur non-admin
     * par défaut ; voir {@link #adminAuthentication} pour les tests LL-EF-010
     * nécessitant un administrateur.
     */
    private final JwtAuthentication authentication =
            new JwtAuthentication(7L, "contributor@example.com", Role.USER);

    private final JwtAuthentication adminAuthentication =
            new JwtAuthentication(99L, "admin@example.com", Role.ADMIN);

    @Test
    void getAllActivities_ShouldReturnListOfActivities() {
        // Given
        List<Activity> activities = List.of(
                new Activity(1L, "Test Activity", "Description", "Category", 0.0, 0.0,
                        LocalDateTime.now(), LocalDateTime.now(), "ACTIVE", 1L, null, null, null, null, null,
                        null, null, null, null, null),
                new Activity(2L, "Another Activity", "Another Description", "Another Category", 1.0, 1.0,
                        LocalDateTime.now(), LocalDateTime.now(), "ACTIVE", 1L, null, null, null, null, null,
                        null, null, null, null, null)
        );
        when(activityService.findAll(null, null, null)).thenReturn(activities);

        // When
        ResponseEntity<Object> response = activityController.getAllActivities(null, null, null, httpRequest);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, ((List<?>) response.getBody()).size());
    }

    @Test
    void getAllActivities_ShouldReturnEmptyList_WhenNoActivities() {
        // Given
        when(activityService.findAll(null, null, null)).thenReturn(List.of());

        // When
        ResponseEntity<Object> response = activityController.getAllActivities(null, null, null, httpRequest);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(0, ((List<?>) response.getBody()).size());
    }

    @Test
    void getAllActivities_ShouldPassCityAndSortThrough_ToService() {
        // LL-10006 : les paramètres query 'city'/'sort' doivent être transmis tels quels au service,
        // qui porte toute la logique de normalisation/validation (même approche que getNearbyActivities).
        Activity activity = new Activity(1L, "Marché", "Description", "marché", 43.9493, 4.8055,
                LocalDateTime.now(), null, "PUBLISHED", 1L, null, null, "Place Pie", "Avignon", "84000",
                null, null, null, null, null);
        when(activityService.findAll("Avignon", "city,date", null)).thenReturn(List.of(activity));

        // When
        ResponseEntity<Object> response =
                activityController.getAllActivities("Avignon", null, "city,date", httpRequest);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(List.of(activity), response.getBody());
    }

    @Test
    void getAllActivities_ShouldReturnBadRequest_WhenSortInvalid() {
        // Given
        when(activityService.findAll(null, "unknown", null))
                .thenThrow(new IllegalArgumentException(
                        "Le paramètre 'sort' ne peut contenir que 'city' et/ou 'date' (valeur reçue : 'unknown')."));
        when(httpRequest.getRequestURI()).thenReturn("/api/v1/activities");

        // When
        ResponseEntity<Object> response = activityController.getAllActivities(null, null, "unknown", httpRequest);

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST.value(), ((ErrorResponse) response.getBody()).status());
    }

    @Test
    void getActivityById_ShouldReturnActivity_WhenFound() {
        // Given
        Activity activity = new Activity(1L, "Test Activity", "Description", "Category", 0.0, 0.0,
                LocalDateTime.now(), LocalDateTime.now(), "ACTIVE", 1L, null, null, null, null, null,
                null, null, null, null, null);
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
                LocalDateTime.now(), null, "PENDING", 1L, null, null, null, null, null, null, null, null, null, 7L);
        when(activityService.createActivity(
                "Pique-nique", "Pique-nique au parc", "loisir", "1 rue de la Paix, Marseille", null, null, 7L))
                .thenReturn(created);

        // When
        ResponseEntity<Object> response = activityController.createActivity(
                new ActivityController.CreateActivityRequest(
                        "Pique-nique", "Pique-nique au parc", "loisir", "1 rue de la Paix, Marseille", null, null),
                authentication, httpRequest);

        // Then
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        Activity body = (Activity) response.getBody();
        assertEquals("Pique-nique", body.title());
        assertEquals("PENDING", body.status());
    }

    @Test
    void createActivity_ShouldPassCreatedByFromAuthentication_ToService() {
        // LL-EF-010 : createdBy provient exclusivement du JWT (JwtAuthentication),
        // jamais d'un champ du corps de requête — même principe que UserController
        // pour userId (voir sa javadoc).
        Activity created = new Activity(1L, "Pique-nique", "Pique-nique au parc", "loisir", 43.29, 5.37,
                LocalDateTime.now(), null, "PENDING", 1L, null, null, null, null, null, null, null, null, null, 7L);
        when(activityService.createActivity(
                "Pique-nique", "Pique-nique au parc", "loisir", "1 rue de la Paix, Marseille", null, null, 7L))
                .thenReturn(created);

        activityController.createActivity(
                new ActivityController.CreateActivityRequest(
                        "Pique-nique", "Pique-nique au parc", "loisir", "1 rue de la Paix, Marseille", null, null),
                authentication, httpRequest);

        verify(activityService).createActivity(
                "Pique-nique", "Pique-nique au parc", "loisir", "1 rue de la Paix, Marseille", null, null, 7L);
    }

    @Test
    void createActivity_ShouldPassStartAndEndDate_WhenProvided() {
        // Given : demande Alex — startDate/endDate saisis manuellement transmis tels quels au service.
        Activity created = new Activity(1L, "Festival", "desc", "loisir", 43.29, 5.37,
                LocalDateTime.of(2026, 9, 20, 0, 0), LocalDateTime.of(2026, 9, 22, 0, 0),
                "PENDING", 1L, null, null, null, null, null, null, null, null, null, 7L);
        when(activityService.createActivity(
                "Festival", "desc", "loisir", "1 rue de la Paix, Marseille", "2026-09-20", "2026-09-22", 7L))
                .thenReturn(created);

        // When
        ResponseEntity<Object> response = activityController.createActivity(
                new ActivityController.CreateActivityRequest(
                        "Festival", "desc", "loisir", "1 rue de la Paix, Marseille", "2026-09-20", "2026-09-22"),
                authentication, httpRequest);

        // Then
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(activityService).createActivity(
                "Festival", "desc", "loisir", "1 rue de la Paix, Marseille", "2026-09-20", "2026-09-22", 7L);
    }

    @Test
    void createActivity_ShouldReturnBadRequest_WhenAddressNotFound() {
        // Given
        when(activityService.createActivity(
                "Pique-nique", "Pique-nique au parc", "loisir", "adresse inconnue", null, null, 7L))
                .thenThrow(new AddressNotFoundException("adresse inconnue"));
        when(httpRequest.getRequestURI()).thenReturn("/api/v1/activities");

        // When
        ResponseEntity<Object> response = activityController.createActivity(
                new ActivityController.CreateActivityRequest(
                        "Pique-nique", "Pique-nique au parc", "loisir", "adresse inconnue", null, null),
                authentication, httpRequest);

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST.value(), ((ErrorResponse) response.getBody()).status());
    }

    @Test
    void createActivity_ShouldReturnServiceUnavailable_WhenGeocodingFails() {
        // Given
        when(activityService.createActivity(
                "Pique-nique", "Pique-nique au parc", "loisir", "1 rue de la Paix", null, null, 7L))
                .thenThrow(new GeocodingUnavailableException(new RuntimeException("timeout")));
        when(httpRequest.getRequestURI()).thenReturn("/api/v1/activities");

        // When
        ResponseEntity<Object> response = activityController.createActivity(
                new ActivityController.CreateActivityRequest(
                        "Pique-nique", "Pique-nique au parc", "loisir", "1 rue de la Paix", null, null),
                authentication, httpRequest);

        // Then
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), ((ErrorResponse) response.getBody()).status());
    }

    @Test
    void getNearbyActivities_ShouldReturnOk_WithActivities() {
        // Given
        Activity nearby = new Activity(1L, "Concert", "Description", "concert", 43.29, 5.37,
                LocalDateTime.now(), null, "PUBLISHED", 1L, null, null, null, null, null, null, null, null, null, null);
        Source source = new Source(1L, "OpenAgenda — Avignon", "API", "https://openagenda.com", "ACTIVE", null, null, null);
        when(activityService.findNearby("43.2951", "5.3739", "5", "concert", "2026-09-05", null))
                .thenReturn(List.of(nearby));
        when(sourceService.getAllSources()).thenReturn(List.of(source));

        // When
        ResponseEntity<Object> response = activityController.getNearbyActivities(
                "43.2951", "5.3739", "5", "concert", "2026-09-05", null, null, httpRequest);

        // Then
        // LL-8006 : sourceId (technique) est résolu en sourceName (lisible) dans la réponse.
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(List.of(ActivityResponse.from(nearby, "OpenAgenda — Avignon")), response.getBody());
    }

    @Test
    void getNearbyActivities_ShouldResolveCreatedByUsername_WhenActivityWasManuallyCreated() {
        // LL-EF-010 : createdBy (id technique) résolu en createdByUsername (nom affiché,
        // « Créée par : ... »), même principe que sourceId -> sourceName (LL-8006).
        Activity nearby = new Activity(1L, "Pique-nique", "Description", "loisir", 43.29, 5.37,
                LocalDateTime.now(), null, "PUBLISHED", 1L, null, null, null, null, null, null, null, null, null,
                42L);
        Source source = new Source(1L, "MANUAL", "MANUAL", null, "ACTIVE", null, null, null);
        User creator = new User(42L, "jean.dupont", "jean@example.com", null, Role.USER, null);
        when(activityService.findNearby("43.2951", "5.3739", "5", "loisir", "2026-09-05", null))
                .thenReturn(List.of(nearby));
        when(sourceService.getAllSources()).thenReturn(List.of(source));
        when(userService.getUserById(42L)).thenReturn(Optional.of(creator));

        // When
        ResponseEntity<Object> response = activityController.getNearbyActivities(
                "43.2951", "5.3739", "5", "loisir", "2026-09-05", null, null, httpRequest);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(
                List.of(ActivityResponse.from(nearby, "MANUAL", "jean.dupont")),
                response.getBody());
    }

    @Test
    void getNearbyActivities_ShouldLeaveCreatedByUsernameNull_WhenActivityHasNoCreator() {
        // Activité importée (createdBy null, hors périmètre LL-EF-010) : pas d'appel
        // inutile à userService pour un id qui n'existe pas.
        Activity imported = new Activity(1L, "Marché", "Description", "marché", 43.29, 5.37,
                LocalDateTime.now(), null, "PUBLISHED", 2L, "external:key", null, null, null, null, null, null,
                null, null, null);
        Source source = new Source(2L, "OpenAgenda — Avignon", "API", "https://openagenda.com", "ACTIVE", null,
                null, null);
        when(activityService.findNearby("43.2951", "5.3739", "5", "marché", "2026-09-05", null))
                .thenReturn(List.of(imported));
        when(sourceService.getAllSources()).thenReturn(List.of(source));

        // When
        ResponseEntity<Object> response = activityController.getNearbyActivities(
                "43.2951", "5.3739", "5", "marché", "2026-09-05", null, null, httpRequest);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(List.of(ActivityResponse.from(imported, "OpenAgenda — Avignon")), response.getBody());
        verifyNoInteractions(userService);
    }

    @Test
    void getNearbyActivities_ShouldReturnUnknownSourceName_WhenSourceNotFound() {
        // Given : sourceId référencé par l'activité absent des sources connues (cas défensif LL-8006).
        Activity nearby = new Activity(1L, "Concert", "Description", "concert", 43.29, 5.37,
                LocalDateTime.now(), null, "PUBLISHED", 99L, null, null, null, null, null, null, null, null, null, null);
        when(activityService.findNearby("43.2951", "5.3739", "5", "concert", "2026-09-05", null))
                .thenReturn(List.of(nearby));
        when(sourceService.getAllSources()).thenReturn(List.of());

        // When
        ResponseEntity<Object> response = activityController.getNearbyActivities(
                "43.2951", "5.3739", "5", "concert", "2026-09-05", null, null, httpRequest);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(List.of(ActivityResponse.from(nearby, "Source inconnue")), response.getBody());
    }

    @Test
    void getNearbyActivities_ShouldExposeAddressCityAndPostalCode() {
        // LL-10005 : postalCode manquait jusqu'ici sur ActivityResponse
        // (seul endpoint exposant address/city sans lui, voir
        // LOCATION_CONTRACT.md). Les tests précédents utilisaient tous des
        // activités avec address/city/postalCode à null, ce qui ne
        // démontrait pas la propagation d'une valeur réelle.
        Activity nearby = new Activity(1L, "Concert", "Description", "concert", 43.29, 5.37,
                LocalDateTime.now(), null, "PUBLISHED", 1L, null, null,
                "Quai du Port", "Marseille", "13002", null, null, null, null, null);
        Source source = new Source(1L, "OpenAgenda — Avignon", "API", "https://openagenda.com", "ACTIVE", null, null, null);
        when(activityService.findNearby("43.2951", "5.3739", "5", "concert", "2026-09-05", null))
                .thenReturn(List.of(nearby));
        when(sourceService.getAllSources()).thenReturn(List.of(source));

        // When
        ResponseEntity<Object> response = activityController.getNearbyActivities(
                "43.2951", "5.3739", "5", "concert", "2026-09-05", null, null, httpRequest);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        @SuppressWarnings("unchecked")
        List<ActivityResponse> body = (List<ActivityResponse>) response.getBody();
        ActivityResponse activityResponse = body.get(0);
        assertEquals("Quai du Port", activityResponse.address());
        assertEquals("Marseille", activityResponse.city());
        assertEquals("13002", activityResponse.postalCode());
        assertEquals(43.29, activityResponse.latitude());
        assertEquals(5.37, activityResponse.longitude());
    }

    @Test
    void getNearbyActivities_ShouldReturnBadRequest_WhenParamsInvalid() {
        // Given
        when(activityService.findNearby(null, "5.3739", "5", null, null, null))
                .thenThrow(new IllegalArgumentException("Le paramètre 'latitude' est obligatoire."));
        when(httpRequest.getRequestURI()).thenReturn("/api/v1/activities/nearby");

        // When
        ResponseEntity<Object> response = activityController.getNearbyActivities(
                null, "5.3739", "5", null, null, null, null, httpRequest);

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST.value(), ((ErrorResponse) response.getBody()).status());
    }

    @Test
    void getNearbyActivities_ShouldReturnBadRequest_WhenDateFormatInvalid() {
        // Given
        when(activityService.findNearby("43.2951", "5.3739", "5", null, "05/09/2026", null))
                .thenThrow(new IllegalArgumentException(
                        "Le paramètre 'date' doit être au format ISO-8601 (yyyy-MM-dd)."));
        when(httpRequest.getRequestURI()).thenReturn("/api/v1/activities/nearby");

        // When
        ResponseEntity<Object> response = activityController.getNearbyActivities(
                "43.2951", "5.3739", "5", null, "05/09/2026", null, null, httpRequest);

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST.value(), ((ErrorResponse) response.getBody()).status());
    }

    @Test
    void getNearbyActivities_ShouldPassDateToToService_WhenProvided() {
        // LL-11003 : le contrôleur transmet simplement dateTo au service, qui porte
        // toute la validation (voir ActivityServiceTest pour le détail des cas).
        Activity nearby = new Activity(1L, "Concert", "Description", "concert", 43.29, 5.37,
                LocalDateTime.now(), null, "PUBLISHED", 1L, null, null, null, null, null, null, null, null, null, null);
        Source source = new Source(1L, "OpenAgenda — Avignon", "API", "https://openagenda.com", "ACTIVE", null, null, null);
        when(activityService.findNearby("43.2951", "5.3739", "5", "concert", "2026-09-05", "2026-09-10"))
                .thenReturn(List.of(nearby));
        when(sourceService.getAllSources()).thenReturn(List.of(source));

        ResponseEntity<Object> response = activityController.getNearbyActivities(
                "43.2951", "5.3739", "5", "concert", "2026-09-05", "2026-09-10", null, httpRequest);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(List.of(ActivityResponse.from(nearby, "OpenAgenda — Avignon")), response.getBody());
    }

    @Test
    void getNearbyActivities_ShouldReturnBadRequest_WhenDateToBeforeDate() {
        // Given
        when(activityService.findNearby("43.2951", "5.3739", "5", null, "2026-09-10", "2026-09-05"))
                .thenThrow(new IllegalArgumentException(
                        "Le paramètre 'dateTo' doit être postérieure ou égale à 'date'."));
        when(httpRequest.getRequestURI()).thenReturn("/api/v1/activities/nearby");

        // When
        ResponseEntity<Object> response = activityController.getNearbyActivities(
                "43.2951", "5.3739", "5", null, "2026-09-10", "2026-09-05", null, httpRequest);

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST.value(), ((ErrorResponse) response.getBody()).status());
    }

    @Test
    void getActivitiesWithinBounds_ShouldReturnOk_WithActivities() {
        // Given
        Activity inBounds = new Activity(1L, "Concert", "Description", "concert", 43.29, 5.37,
                LocalDateTime.now(), null, "PUBLISHED", 1L, null, null, null, null, null, null, null, null, null, null);
        Source source = new Source(1L, "OpenAgenda — Avignon", "API", "https://openagenda.com", "ACTIVE", null, null, null);
        when(activityService.findWithinBounds(
                "43.28", "5.35", "43.31", "5.40", "concert", "2026-09-05", null))
                .thenReturn(List.of(inBounds));
        when(sourceService.getAllSources()).thenReturn(List.of(source));

        // When
        ResponseEntity<Object> response = activityController.getActivitiesWithinBounds(
                "43.28", "5.35", "43.31", "5.40", "concert", "2026-09-05", null, null, httpRequest);

        // Then
        // LL-8006 : sourceId (technique) est résolu en sourceName (lisible) dans la réponse.
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(List.of(ActivityResponse.from(inBounds, "OpenAgenda — Avignon")), response.getBody());
    }

    @Test
    void getActivitiesWithinBounds_ShouldExposeAddressCityAndPostalCode() {
        // LL-10005 : même vérification que getNearbyActivities ci-dessus,
        // pour le second endpoint concerné par le contrat LOCATION_CONTRACT.md.
        Activity inBounds = new Activity(1L, "Concert", "Description", "concert", 43.29, 5.37,
                LocalDateTime.now(), null, "PUBLISHED", 1L, null, null,
                "Quai du Port", "Marseille", "13002", null, null, null, null, null);
        Source source = new Source(1L, "OpenAgenda — Avignon", "API", "https://openagenda.com", "ACTIVE", null, null, null);
        when(activityService.findWithinBounds(
                "43.28", "5.35", "43.31", "5.40", "concert", "2026-09-05", null))
                .thenReturn(List.of(inBounds));
        when(sourceService.getAllSources()).thenReturn(List.of(source));

        // When
        ResponseEntity<Object> response = activityController.getActivitiesWithinBounds(
                "43.28", "5.35", "43.31", "5.40", "concert", "2026-09-05", null, null, httpRequest);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        @SuppressWarnings("unchecked")
        List<ActivityResponse> body = (List<ActivityResponse>) response.getBody();
        ActivityResponse activityResponse = body.get(0);
        assertEquals("Quai du Port", activityResponse.address());
        assertEquals("Marseille", activityResponse.city());
        assertEquals("13002", activityResponse.postalCode());
    }

    @Test
    void getActivitiesWithinBounds_ShouldReturnBadRequest_WhenParamsInvalid() {
        // Given
        when(activityService.findWithinBounds(null, "5.35", "43.31", "5.40", null, null, null))
                .thenThrow(new IllegalArgumentException("Le paramètre 'swLatitude' est obligatoire."));
        when(httpRequest.getRequestURI()).thenReturn("/api/v1/activities/within-bounds");

        // When
        ResponseEntity<Object> response = activityController.getActivitiesWithinBounds(
                null, "5.35", "43.31", "5.40", null, null, null, null, httpRequest);

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST.value(), ((ErrorResponse) response.getBody()).status());
    }

    @Test
    void getActivitiesWithinBounds_ShouldReturnBadRequest_WhenSwLatitudeNotLessThanNeLatitude() {
        // Given
        when(activityService.findWithinBounds("43.31", "5.35", "43.31", "5.40", null, null, null))
                .thenThrow(new IllegalArgumentException(
                        "Le paramètre 'swLatitude' doit être strictement inférieur à 'neLatitude'."));
        when(httpRequest.getRequestURI()).thenReturn("/api/v1/activities/within-bounds");

        // When
        ResponseEntity<Object> response = activityController.getActivitiesWithinBounds(
                "43.31", "5.35", "43.31", "5.40", null, null, null, null, httpRequest);

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST.value(), ((ErrorResponse) response.getBody()).status());
    }

    // --- cancelActivity (LL-EF-010) ---

    @Test
    void cancelActivity_ShouldReturnOk_WhenRequestedByCreator() {
        // Given
        Activity cancelled = new Activity(1L, "Pique-nique", "desc", "loisir", 43.29, 5.37,
                LocalDateTime.now(), null, "CANCELLED", 1L, null, null, null, null, null, null, null, null, null,
                7L);
        when(activityService.cancel(1L, 7L, false)).thenReturn(Optional.of(cancelled));

        // When
        ResponseEntity<Object> response = activityController.cancelActivity(1L, authentication, httpRequest);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("CANCELLED", ((Activity) response.getBody()).status());
    }

    @Test
    void cancelActivity_ShouldPassIsAdminTrue_WhenRequesterIsAdmin() {
        // LL-EF-010 : un administrateur peut annuler même sans être le créateur —
        // le contrôleur transmet isAdmin=true au service, qui porte la vérification.
        Activity cancelled = new Activity(1L, "Pique-nique", "desc", "loisir", 43.29, 5.37,
                LocalDateTime.now(), null, "CANCELLED", 1L, null, null, null, null, null, null, null, null, null,
                7L);
        when(activityService.cancel(1L, 99L, true)).thenReturn(Optional.of(cancelled));

        // When
        ResponseEntity<Object> response = activityController.cancelActivity(1L, adminAuthentication, httpRequest);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(activityService).cancel(1L, 99L, true);
    }

    @Test
    void cancelActivity_ShouldReturnNotFound_WhenActivityDoesNotExist() {
        // Given
        when(activityService.cancel(99L, 7L, false)).thenReturn(Optional.empty());

        // When
        ResponseEntity<Object> response = activityController.cancelActivity(99L, authentication, httpRequest);

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void cancelActivity_ShouldReturnBadRequest_WhenActivityHasNoCreator() {
        // Given
        when(activityService.cancel(1L, 7L, false))
                .thenThrow(new IllegalArgumentException("L'activité 1 n'a pas de créateur identifié."));
        when(httpRequest.getRequestURI()).thenReturn("/api/v1/activities/1/cancel");

        // When
        ResponseEntity<Object> response = activityController.cancelActivity(1L, authentication, httpRequest);

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST.value(), ((ErrorResponse) response.getBody()).status());
    }

    @Test
    void cancelActivity_ShouldReturnForbidden_WhenRequesterIsNeitherCreatorNorAdmin() {
        // Given
        when(activityService.cancel(1L, 7L, false))
                .thenThrow(new ActivityAccessDeniedException(
                        "Seul le créateur de l'activité 1 ou un administrateur peut l'annuler."));
        when(httpRequest.getRequestURI()).thenReturn("/api/v1/activities/1/cancel");

        // When
        ResponseEntity<Object> response = activityController.cancelActivity(1L, authentication, httpRequest);

        // Then
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN.value(), ((ErrorResponse) response.getBody()).status());
    }

    // --- likedByCurrentUser sur /nearby (LS-002) ---

    @Test
    void getNearbyActivities_ShouldSetLikedByCurrentUserTrue_WhenAuthenticatedAndActivityIsLiked() {
        // Given
        Activity nearby = new Activity(1L, "Concert", "Description", "concert", 43.29, 5.37,
                LocalDateTime.now(), null, "PUBLISHED", 1L, null, null, null, null, null, null, null, null, null,
                null);
        Source source = new Source(1L, "MANUAL", "MANUAL", null, "ACTIVE", null, null, null);
        when(activityService.findNearby("43.2951", "5.3739", "5", null, null, null))
                .thenReturn(List.of(nearby));
        when(sourceService.getAllSources()).thenReturn(List.of(source));
        when(likeService.findLikedActivityIds(7L, List.of(1L))).thenReturn(List.of(1L));

        // When : authentication (userId=7L) plutôt que null, contrairement aux autres tests
        // getNearbyActivities_* ci-dessus qui simulent un appelant anonyme.
        ResponseEntity<Object> response = activityController.getNearbyActivities(
                "43.2951", "5.3739", "5", null, null, null, authentication, httpRequest);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        @SuppressWarnings("unchecked")
        List<ActivityResponse> body = (List<ActivityResponse>) response.getBody();
        assertEquals(true, body.get(0).likedByCurrentUser());
    }

    @Test
    void getNearbyActivities_ShouldSetLikedByCurrentUserFalse_WhenAnonymous() {
        // Given : appelant anonyme (authentication == null dans ce test unitaire — un
        // AnonymousAuthenticationToken en conditions réelles, voir la javadoc de
        // ActivityController#withSourceNames) : ne doit jamais appeler likeService.
        Activity nearby = new Activity(1L, "Concert", "Description", "concert", 43.29, 5.37,
                LocalDateTime.now(), null, "PUBLISHED", 1L, null, null, null, null, null, null, null, null, null,
                null);
        Source source = new Source(1L, "MANUAL", "MANUAL", null, "ACTIVE", null, null, null);
        when(activityService.findNearby("43.2951", "5.3739", "5", null, null, null))
                .thenReturn(List.of(nearby));
        when(sourceService.getAllSources()).thenReturn(List.of(source));

        // When
        ResponseEntity<Object> response = activityController.getNearbyActivities(
                "43.2951", "5.3739", "5", null, null, null, null, httpRequest);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        @SuppressWarnings("unchecked")
        List<ActivityResponse> body = (List<ActivityResponse>) response.getBody();
        assertEquals(false, body.get(0).likedByCurrentUser());
        verifyNoInteractions(likeService);
    }

    // --- likeActivity / unlikeActivity (LS-002) ---

    @Test
    void likeActivity_ShouldReturnOkWithLikedTrue_WhenSuccessful() {
        // Given
        when(likeService.like(7L, 1L)).thenReturn(true);

        // When
        ResponseEntity<Object> response = activityController.likeActivity(1L, authentication);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(new ActivityController.LikeStatusResponse(true), response.getBody());
    }

    @Test
    void likeActivity_ShouldReturnNotFound_WhenActivityDoesNotExist() {
        // Given
        when(likeService.like(7L, 99L)).thenReturn(false);

        // When
        ResponseEntity<Object> response = activityController.likeActivity(99L, authentication);

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void unlikeActivity_ShouldReturnOkWithLikedFalse() {
        // When
        ResponseEntity<Object> response = activityController.unlikeActivity(1L, authentication);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(new ActivityController.LikeStatusResponse(false), response.getBody());
        verify(likeService).unlike(7L, 1L);
    }
}
