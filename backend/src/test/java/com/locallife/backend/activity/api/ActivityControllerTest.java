package com.locallife.backend.activity.api;

import com.locallife.backend.activity.infrastructure.ActivityCoordinatePrecisionRepository;
import com.locallife.backend.accessibility.application.AccessibilityService;
import com.locallife.backend.activity.application.ActivityAccessDeniedException;
import com.locallife.backend.activity.application.ActivityService;
import com.locallife.backend.activity.application.ContributionRecurrence;
import com.locallife.backend.activity.application.ContributionService;
import com.locallife.backend.activity.application.RecurringContributionService;
import com.locallife.backend.activity.domain.Activity;
import com.locallife.backend.auth.application.JwtAuthentication;
import com.locallife.backend.category.domain.Category;
import com.locallife.backend.category.infrastructure.CategoryRepository;
import com.locallife.backend.common.ErrorResponse;
import com.locallife.backend.geocoding.application.AddressNotFoundException;
import com.locallife.backend.geocoding.application.GeocodingUnavailableException;
import com.locallife.backend.like.application.LikeService;
import com.locallife.backend.link.application.ActivityLinkService;
import com.locallife.backend.link.domain.ActivityLink;
import com.locallife.backend.media.application.ActivityMediaService;
import com.locallife.backend.media.application.MediaService;
import com.locallife.backend.metadata.application.ActivityMetadataService;
import com.locallife.backend.place.application.LocationService;
import com.locallife.backend.place.domain.CoordinatePrecision;
import com.locallife.backend.premium.application.PremiumAccessPolicy;
import com.locallife.backend.schedule.application.OccurrenceService;
import com.locallife.backend.schedule.application.ScheduleService;
import com.locallife.backend.source.application.SourceService;
import com.locallife.backend.source.domain.Source;
import com.locallife.backend.tag.application.ActivityTagService;
import com.locallife.backend.user.application.UserService;
import com.locallife.backend.user.domain.Role;
import com.locallife.backend.user.domain.User;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
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
    private ScheduleService scheduleService;

    @Mock
    private OccurrenceService occurrenceService;

    @Mock
    private LocationService locationService;

    @Mock
    private MediaService mediaService;

    @Mock
    private ActivityMediaService activityMediaService;

    @Mock
    private ActivityLinkService activityLinkService;

    @Mock
    private AccessibilityService accessibilityService;

    @Mock
    private ActivityTagService activityTagService;

    @Mock
    private ActivityMetadataService activityMetadataService;

    @Mock
    private PremiumAccessPolicy premiumAccessPolicy;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private RecurringContributionService recurringContributionService;

    @Mock
    private ContributionService contributionService;

    /**
     * Fixture CAT-006 (correctif) : catégorie résolue par
     * {@code categoryRepository.findByCode("CULTURE")} dans les tests
     * {@code createActivity_*} qui atteignent cette résolution (toutes
     * sauf {@code ShouldReturnBadRequest_AndNeverCreateActivity_WhenEmailInvalid},
     * où la validation email échoue avant).
     */
    private static final Category CULTURE_CATEGORY =
            new Category(1L, "CULTURE", true, null, null, Set.of());

    /** Catégorie désactivée depuis l'administration : refusée pour une nouvelle contribution. */
    private static final Category INACTIVE_SPORT_CATEGORY =
            new Category(5L, "SPORT", false, null, null, Set.of());

    @Mock
    private HttpServletRequest httpRequest;

    @Mock
    private ActivityCoordinatePrecisionRepository coordinatePrecisionRepository;

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
        // LL-11013 : GET /api/v1/activities retourne désormais ActivitySummaryResponse (mainImageUrl
        // null ici : activityMediaService non stubbé, renvoie une liste vide par défaut).
        assertEquals(List.of(ActivitySummaryResponse.from(activity, null)), response.getBody());
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
    void getActivityById_ShouldReturnDetailedActivity_WhenPremiumAccessGranted() {
        // Given
        Activity activity = new Activity(1L, "Test Activity", "Description", "Category", 0.0, 0.0,
                LocalDateTime.now(), LocalDateTime.now(), "PUBLISHED", 1L, null, null, null, null, null,
                null, null, null, null, null);
        when(activityService.findById(1L)).thenReturn(Optional.of(activity));
        when(premiumAccessPolicy.hasPremiumAccess(authentication)).thenReturn(true);

        // When
        ResponseEntity<Object> response = activityController.getActivityById(1L, authentication);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody() instanceof ActivityDetailResponse);
        ActivityDetailResponse body = (ActivityDetailResponse) response.getBody();
        assertEquals("Test Activity", body.title());
        assertEquals("Description", body.description());
    }

    @Test
    void getActivityById_ShouldReturnSummary_WhenPremiumAccessDenied() {
        // LL-11014 : un appelant sans accès Premium reçoit le résumé, jamais la fiche détaillée —
        // la décision revient exclusivement à PremiumAccessPolicy, jamais au domaine.
        Activity activity = new Activity(1L, "Test Activity", "Description", "Category", 0.0, 0.0,
                LocalDateTime.now(), LocalDateTime.now(), "PUBLISHED", 1L, null, null, null, null, null,
                null, null, null, null, null);
        when(activityService.findById(1L)).thenReturn(Optional.of(activity));
        when(premiumAccessPolicy.hasPremiumAccess(authentication)).thenReturn(false);

        // When
        ResponseEntity<Object> response = activityController.getActivityById(1L, authentication);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody() instanceof ActivitySummaryResponse);
        assertEquals("Test Activity", ((ActivitySummaryResponse) response.getBody()).title());
    }

    @Test
    void getActivityById_ShouldReturnNotFound_WhenNotFound() {
        // Given
        when(activityService.findById(999L)).thenReturn(Optional.empty());

        // When
        ResponseEntity<Object> response = activityController.getActivityById(999L, authentication);

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    /** Activité non publiée créée par l'utilisateur 7 ({@link #authentication}). */
    private Activity unpublishedActivity(String status) {
        return new Activity(1L, "Test Activity", "Description", "Category", 0.0, 0.0,
                LocalDateTime.now(), LocalDateTime.now(), status, 1L, null, null, null, null, null,
                null, null, null, null, 7L);
    }

    @Test
    void getActivityById_ShouldReturnNotFound_WhenNotPublishedAndCallerIsAnonymous() {
        // Une activité non publiée ne doit pas être lisible publiquement, quel que soit son statut.
        for (String status : List.of("PENDING", "REJECTED", "ARCHIVED", "CANCELLED")) {
            when(activityService.findById(1L)).thenReturn(Optional.of(unpublishedActivity(status)));

            ResponseEntity<Object> response = activityController.getActivityById(
                    1L, new AnonymousAuthenticationToken(
                            "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode(), status);
        }
    }

    @Test
    void getActivityById_ShouldReturnNotFound_WhenNotPublishedAndCallerIsAnotherUser() {
        when(activityService.findById(1L)).thenReturn(Optional.of(unpublishedActivity("PENDING")));

        ResponseEntity<Object> response = activityController.getActivityById(
                1L, new JwtAuthentication(8L, "other@example.com", Role.USER));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void getActivityById_ShouldReturnActivity_WhenNotPublishedAndCallerIsAuthor() {
        when(activityService.findById(1L)).thenReturn(Optional.of(unpublishedActivity("PENDING")));
        when(premiumAccessPolicy.hasPremiumAccess(authentication)).thenReturn(true);

        ResponseEntity<Object> response = activityController.getActivityById(1L, authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void getActivityById_ShouldReturnActivity_WhenNotPublishedAndCallerIsAdmin() {
        when(activityService.findById(1L)).thenReturn(Optional.of(unpublishedActivity("REJECTED")));
        when(premiumAccessPolicy.hasPremiumAccess(adminAuthentication)).thenReturn(true);

        ResponseEntity<Object> response = activityController.getActivityById(1L, adminAuthentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void createActivity_ShouldReturnCreated_WithActivity() {
        // Given
        Activity created = new Activity(1L, "Pique-nique", "Pique-nique au parc", "loisir", 43.29, 5.37,
                LocalDateTime.now(), null, "PENDING", 1L, null, null, null, null, null, null, null, null, null, 7L);
        when(activityService.createActivity(
                "Pique-nique", "Pique-nique au parc", "loisir", "1 rue de la Paix, Marseille", null, null, 7L,
                List.of(1L)))
                .thenReturn(created);

        // When
        when(categoryRepository.findByCode("CULTURE")).thenReturn(Optional.of(CULTURE_CATEGORY));
        ResponseEntity<Object> response = activityController.createActivity(
                new ActivityController.CreateActivityRequest(
                        "Pique-nique", "Pique-nique au parc", "loisir", "1 rue de la Paix, Marseille",
                        null, null, null, null, null,
                "CULTURE"),
                authentication, httpRequest);

        // Then
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        Activity body = (Activity) response.getBody();
        assertEquals("Pique-nique", body.title());
        assertEquals("PENDING", body.status());
    }

    @Test
    void createActivity_ShouldCreateRecurringActivity_WhenRecurrenceProvided() {
        // Food truck présent tous les mardis et jeudis : startDate/endDate ignorés au profit de la récurrence.
        Activity created = new Activity(1L, "Food truck", "Burgers", null, 43.29, 5.37,
                LocalDateTime.now(), null, "PENDING", 1L, null, null, null, null, null, null, null, null, null, 7L);
        when(categoryRepository.findByCode("CULTURE")).thenReturn(Optional.of(CULTURE_CATEGORY));
        ContributionRecurrence expected = ContributionRecurrence.of(
                List.of("TU", "TH"), "11:30", "14:00", "2026-10-01", "2026-12-31", null);
        when(recurringContributionService.createActivity(
                "Food truck", "Burgers", null, "1 rue de la Paix, Marseille", 7L, List.of(1L), expected))
                .thenReturn(created);

        ResponseEntity<Object> response = activityController.createActivity(
                new ActivityController.CreateActivityRequest(
                        "Food truck", "Burgers", null, "1 rue de la Paix, Marseille",
                        "2026-01-01", null, null, null, null, "CULTURE", null,
                        new ActivityController.RecurrenceRequest(
                                List.of("TU", "TH"), "11:30", "14:00", "2026-10-01", "2026-12-31")),
                authentication, httpRequest);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(activityService, never()).createActivity(
                any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void createActivity_ShouldReturnBadRequest_WhenRecurrenceHasNoDay() {
        when(categoryRepository.findByCode("CULTURE")).thenReturn(Optional.of(CULTURE_CATEGORY));

        ResponseEntity<Object> response = activityController.createActivity(
                new ActivityController.CreateActivityRequest(
                        "Food truck", "Burgers", null, "1 rue de la Paix, Marseille",
                        null, null, null, null, null, "CULTURE", null,
                        new ActivityController.RecurrenceRequest(List.of(), "11:30", null, null, null)),
                authentication, httpRequest);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verifyNoInteractions(recurringContributionService);
    }

    @Test
    void updateActivity_ShouldReturnUpdatedActivity_AndReplaceContactLinks() {
        Activity updated = new Activity(1L, "Food truck", "Burgers", null, 43.29, 5.37,
                LocalDateTime.now(), null, "PENDING", 1L, null, null, null, null, null, null, null, null, null, 7L);
        when(categoryRepository.findByCode("CULTURE")).thenReturn(Optional.of(CULTURE_CATEGORY));
        when(contributionService.update(1L, 7L, false, "Food truck", "Burgers", null, "1 rue de la Paix, Marseille",
                null, null, List.of(1L), null))
                .thenReturn(Optional.of(updated));

        ResponseEntity<Object> response = activityController.updateActivity(1L,
                new ActivityController.CreateActivityRequest(
                        "Food truck", "Burgers", null, "1 rue de la Paix, Marseille",
                        null, null, null, "06 12 34 56 78", null, "CULTURE"),
                authentication, httpRequest);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("PENDING", ((Activity) response.getBody()).status());
        verify(activityLinkService).deleteByActivityId(1L);
        verify(activityLinkService).create(any());
    }

    @Test
    void updateActivity_ShouldKeepAnInactiveCategoryAlreadyAttached_ButRejectANewOne() {
        Activity updated = new Activity(1L, "Food truck", "Burgers", null, 43.29, 5.37,
                LocalDateTime.now(), null, "PUBLISHED", 1L, null, null, null, null, null, null, null, null, null, 7L);
        when(categoryRepository.findByCode("SPORT")).thenReturn(Optional.of(INACTIVE_SPORT_CATEGORY));
        when(activityService.findCategoryIds(List.of(1L))).thenReturn(Map.of(1L, List.of(5L)));
        when(activityService.findCategoryIds(List.of(2L))).thenReturn(Map.of(2L, List.of(1L)));
        when(contributionService.update(1L, 7L, false, "Food truck", "Burgers", null, "1 rue de la Paix, Marseille",
                null, null, List.of(5L), null))
                .thenReturn(Optional.of(updated));
        ActivityController.CreateActivityRequest request = new ActivityController.CreateActivityRequest(
                "Food truck", "Burgers", null, "1 rue de la Paix, Marseille",
                null, null, null, null, null, "SPORT");

        ResponseEntity<Object> kept = activityController.updateActivity(1L, request, authentication, httpRequest);
        ResponseEntity<Object> rejected = activityController.updateActivity(2L, request, authentication, httpRequest);

        assertEquals(HttpStatus.OK, kept.getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, rejected.getStatusCode());
        verify(contributionService, never()).update(eq(2L), any(), anyBoolean(), any(), any(), any(), any(), any(),
                any(), any(), any());
    }

    @Test
    void updateActivity_ShouldReturnForbidden_WhenRequesterIsNotTheCreator() {
        when(categoryRepository.findByCode("CULTURE")).thenReturn(Optional.of(CULTURE_CATEGORY));
        when(contributionService.update(any(), any(), anyBoolean(), any(), any(), any(), any(), any(), any(), any(),
                any()))
                .thenThrow(new ActivityAccessDeniedException("refusé"));

        ResponseEntity<Object> response = activityController.updateActivity(1L,
                new ActivityController.CreateActivityRequest(
                        "Food truck", "Burgers", null, "1 rue de la Paix, Marseille",
                        null, null, null, null, null, "CULTURE"),
                authentication, httpRequest);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verify(activityLinkService, never()).deleteByActivityId(any());
    }

    @Test
    void updateActivity_ShouldReturnNotFound_WhenActivityDoesNotExist() {
        when(categoryRepository.findByCode("CULTURE")).thenReturn(Optional.of(CULTURE_CATEGORY));
        when(contributionService.update(any(), any(), anyBoolean(), any(), any(), any(), any(), any(), any(), any(),
                any()))
                .thenReturn(Optional.empty());

        ResponseEntity<Object> response = activityController.updateActivity(42L,
                new ActivityController.CreateActivityRequest(
                        "Food truck", "Burgers", null, "1 rue de la Paix, Marseille",
                        null, null, null, null, null, "CULTURE"),
                authentication, httpRequest);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void deleteActivity_ShouldReturnNoContent_WhenDeleted() {
        when(contributionService.delete(1L, 7L, false)).thenReturn(true);

        assertEquals(HttpStatus.NO_CONTENT,
                activityController.deleteActivity(1L, authentication, httpRequest).getStatusCode());
    }

    @Test
    void deleteActivity_ShouldReturnForbidden_WhenRequesterIsNotTheCreator() {
        when(contributionService.delete(1L, 7L, false)).thenThrow(new ActivityAccessDeniedException("refusé"));

        assertEquals(HttpStatus.FORBIDDEN,
                activityController.deleteActivity(1L, authentication, httpRequest).getStatusCode());
    }

    @Test
    void deleteActivity_ShouldReturnNotFound_WhenActivityDoesNotExist() {
        when(contributionService.delete(1L, 7L, false)).thenReturn(false);

        assertEquals(HttpStatus.NOT_FOUND,
                activityController.deleteActivity(1L, authentication, httpRequest).getStatusCode());
    }

    @Test
    void createActivity_ShouldPassAllSelectedCategories_WhenCategoryCodesProvided() {
        // Plusieurs catégories cochées dans le formulaire : toutes transmises, dans l'ordre, sans doublon ;
        // categoryCodes prime sur l'ancien categoryCode unique.
        Activity created = new Activity(1L, "Pique-nique", "Pique-nique au parc", null, 43.29, 5.37,
                LocalDateTime.now(), null, "PENDING", 1L, null, null, null, null, null, null, null, null, null, 7L);
        when(categoryRepository.findByCode("CULTURE")).thenReturn(Optional.of(CULTURE_CATEGORY));
        when(categoryRepository.findByCode("SPORT"))
                .thenReturn(Optional.of(new Category(5L, "SPORT", true, null, null, Set.of())));
        when(activityService.createActivity(
                "Pique-nique", "Pique-nique au parc", null, "1 rue de la Paix, Marseille", null, null, 7L,
                List.of(1L, 5L)))
                .thenReturn(created);

        ResponseEntity<Object> response = activityController.createActivity(
                new ActivityController.CreateActivityRequest(
                        "Pique-nique", "Pique-nique au parc", null, "1 rue de la Paix, Marseille",
                        null, null, null, null, null,
                        "NATURE", List.of("CULTURE", "SPORT", "CULTURE")),
                authentication, httpRequest);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(activityService).createActivity(
                "Pique-nique", "Pique-nique au parc", null, "1 rue de la Paix, Marseille", null, null, 7L,
                List.of(1L, 5L));
    }

    @Test
    void createActivity_ShouldReturnBadRequest_WhenOneOfTheCategoryCodesIsUnknown() {
        when(categoryRepository.findByCode("CULTURE")).thenReturn(Optional.of(CULTURE_CATEGORY));
        when(categoryRepository.findByCode("INCONNUE")).thenReturn(Optional.empty());

        ResponseEntity<Object> response = activityController.createActivity(
                new ActivityController.CreateActivityRequest(
                        "Pique-nique", "Pique-nique au parc", null, "1 rue de la Paix, Marseille",
                        null, null, null, null, null,
                        null, List.of("CULTURE", "INCONNUE")),
                authentication, httpRequest);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verifyNoInteractions(activityService);
    }

    @Test
    void createActivity_ShouldReturnBadRequest_WhenACategoryIsInactive() {
        when(categoryRepository.findByCode("SPORT")).thenReturn(Optional.of(INACTIVE_SPORT_CATEGORY));

        ResponseEntity<Object> response = activityController.createActivity(
                new ActivityController.CreateActivityRequest(
                        "Pique-nique", "Pique-nique au parc", null, "1 rue de la Paix, Marseille",
                        null, null, null, null, null,
                        null, List.of("SPORT")),
                authentication, httpRequest);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Catégorie désactivée : SPORT", ((ErrorResponse) response.getBody()).message());
        verify(activityService, never()).createActivity(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void createActivity_ShouldPassCreatedByFromAuthentication_ToService() {
        // LL-EF-010 : createdBy provient exclusivement du JWT (JwtAuthentication),
        // jamais d'un champ du corps de requête — même principe que UserController
        // pour userId (voir sa javadoc).
        Activity created = new Activity(1L, "Pique-nique", "Pique-nique au parc", "loisir", 43.29, 5.37,
                LocalDateTime.now(), null, "PENDING", 1L, null, null, null, null, null, null, null, null, null, 7L);
        when(activityService.createActivity(
                "Pique-nique", "Pique-nique au parc", "loisir", "1 rue de la Paix, Marseille", null, null, 7L,
                List.of(1L)))
                .thenReturn(created);

        when(categoryRepository.findByCode("CULTURE")).thenReturn(Optional.of(CULTURE_CATEGORY));
        activityController.createActivity(
                new ActivityController.CreateActivityRequest(
                        "Pique-nique", "Pique-nique au parc", "loisir", "1 rue de la Paix, Marseille",
                        null, null, null, null, null,
                "CULTURE"),
                authentication, httpRequest);

        verify(activityService).createActivity(
                "Pique-nique", "Pique-nique au parc", "loisir", "1 rue de la Paix, Marseille", null, null, 7L,
                List.of(1L));
    }

    @Test
    void createActivity_ShouldPassStartAndEndDate_WhenProvided() {
        // Given : demande Alex — startDate/endDate saisis manuellement transmis tels quels au service.
        Activity created = new Activity(1L, "Festival", "desc", "loisir", 43.29, 5.37,
                LocalDateTime.of(2026, 9, 20, 0, 0), LocalDateTime.of(2026, 9, 22, 0, 0),
                "PENDING", 1L, null, null, null, null, null, null, null, null, null, 7L);
        when(activityService.createActivity(
                "Festival", "desc", "loisir", "1 rue de la Paix, Marseille", "2026-09-20", "2026-09-22", 7L,
                List.of(1L)))
                .thenReturn(created);

        // When
        when(categoryRepository.findByCode("CULTURE")).thenReturn(Optional.of(CULTURE_CATEGORY));
        ResponseEntity<Object> response = activityController.createActivity(
                new ActivityController.CreateActivityRequest(
                        "Festival", "desc", "loisir", "1 rue de la Paix, Marseille", "2026-09-20", "2026-09-22",
                        null, null, null,
                "CULTURE"),
                authentication, httpRequest);

        // Then
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(activityService).createActivity(
                "Festival", "desc", "loisir", "1 rue de la Paix, Marseille", "2026-09-20", "2026-09-22", 7L,
                List.of(1L));
    }

    @Test
    void createActivity_ShouldReturnBadRequest_WhenAddressNotFound() {
        // Given
        when(activityService.createActivity(
                "Pique-nique", "Pique-nique au parc", "loisir", "adresse inconnue", null, null, 7L,
                List.of(1L)))
                .thenThrow(new AddressNotFoundException("adresse inconnue"));
        when(httpRequest.getRequestURI()).thenReturn("/api/v1/activities");

        // When
        when(categoryRepository.findByCode("CULTURE")).thenReturn(Optional.of(CULTURE_CATEGORY));
        ResponseEntity<Object> response = activityController.createActivity(
                new ActivityController.CreateActivityRequest(
                        "Pique-nique", "Pique-nique au parc", "loisir", "adresse inconnue",
                        null, null, null, null, null,
                "CULTURE"),
                authentication, httpRequest);

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST.value(), ((ErrorResponse) response.getBody()).status());
    }

    @Test
    void createActivity_ShouldReturnServiceUnavailable_WhenGeocodingFails() {
        // Given
        when(activityService.createActivity(
                "Pique-nique", "Pique-nique au parc", "loisir", "1 rue de la Paix", null, null, 7L,
                List.of(1L)))
                .thenThrow(new GeocodingUnavailableException(new RuntimeException("timeout")));
        when(httpRequest.getRequestURI()).thenReturn("/api/v1/activities");

        // When
        when(categoryRepository.findByCode("CULTURE")).thenReturn(Optional.of(CULTURE_CATEGORY));
        ResponseEntity<Object> response = activityController.createActivity(
                new ActivityController.CreateActivityRequest(
                        "Pique-nique", "Pique-nique au parc", "loisir", "1 rue de la Paix",
                        null, null, null, null, null,
                "CULTURE"),
                authentication, httpRequest);

        // Then
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), ((ErrorResponse) response.getBody()).status());
    }

    /**
     * LL-EF-016 : url/téléphone/email optionnels d'une contribution
     * manuelle deviennent chacun un {@code ActivityLink} attaché à
     * l'activité créée.
     */
    @Test
    void createActivity_ShouldAttachContactLinks_WhenUrlPhoneEmailProvided() {
        // Given
        Activity created = new Activity(42L, "Pique-nique", "Pique-nique au parc", "loisir", 43.29, 5.37,
                LocalDateTime.now(), null, "PENDING", 1L, null, null, null, null, null, null, null, null, null, 7L);
        when(activityService.createActivity(
                "Pique-nique", "Pique-nique au parc", "loisir", "1 rue de la Paix, Marseille", null, null, 7L,
                List.of(1L)))
                .thenReturn(created);

        // When
        when(categoryRepository.findByCode("CULTURE")).thenReturn(Optional.of(CULTURE_CATEGORY));
        ResponseEntity<Object> response = activityController.createActivity(
                new ActivityController.CreateActivityRequest(
                        "Pique-nique", "Pique-nique au parc", "loisir", "1 rue de la Paix, Marseille",
                        null, null, "https://example.com", "06 12 34 56 78", "contact@example.com",
                "CULTURE"),
                authentication, httpRequest);

        // Then
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(activityLinkService).validateBeforeCreate("WEBSITE", "https://example.com");
        verify(activityLinkService).validateBeforeCreate(ActivityLinkService.TYPE_PHONE, "06 12 34 56 78");
        verify(activityLinkService).validateBeforeCreate(ActivityLinkService.TYPE_EMAIL, "contact@example.com");
        verify(activityLinkService).create(new ActivityLink(null, 42L, "WEBSITE", null, "https://example.com"));
        verify(activityLinkService).create(
                new ActivityLink(null, 42L, ActivityLinkService.TYPE_PHONE, null, "06 12 34 56 78"));
        verify(activityLinkService).create(
                new ActivityLink(null, 42L, ActivityLinkService.TYPE_EMAIL, null, "contact@example.com"));
    }

    @Test
    void createActivity_ShouldNotAttachAnyLink_WhenUrlPhoneEmailAbsent() {
        // Given
        Activity created = new Activity(42L, "Pique-nique", "Pique-nique au parc", "loisir", 43.29, 5.37,
                LocalDateTime.now(), null, "PENDING", 1L, null, null, null, null, null, null, null, null, null, 7L);
        when(activityService.createActivity(
                "Pique-nique", "Pique-nique au parc", "loisir", "1 rue de la Paix, Marseille", null, null, 7L,
                List.of(1L)))
                .thenReturn(created);

        // When
        when(categoryRepository.findByCode("CULTURE")).thenReturn(Optional.of(CULTURE_CATEGORY));
        activityController.createActivity(
                new ActivityController.CreateActivityRequest(
                        "Pique-nique", "Pique-nique au parc", "loisir", "1 rue de la Paix, Marseille",
                        null, null, null, null, null,
                "CULTURE"),
                authentication, httpRequest);

        // Then
        verifyNoInteractions(activityLinkService);
    }

    /**
     * LL-EF-016 : un lien/contact invalide doit être rejeté **avant** la
     * création de l'activité elle-même (voir la javadoc de {@code
     * ActivityController#createActivity}) — pas d'activité orpheline
     * sans son contact en cas d'échec de validation.
     */
    @Test
    void createActivity_ShouldReturnBadRequest_AndNeverCreateActivity_WhenEmailInvalid() {
        // Given
        doThrow(new IllegalArgumentException("Adresse email invalide : pas-un-email"))
                .when(activityLinkService).validateBeforeCreate(ActivityLinkService.TYPE_EMAIL, "pas-un-email");
        when(httpRequest.getRequestURI()).thenReturn("/api/v1/activities");

        // When
        ResponseEntity<Object> response = activityController.createActivity(
                new ActivityController.CreateActivityRequest(
                        "Pique-nique", "Pique-nique au parc", "loisir", "1 rue de la Paix, Marseille",
                        null, null, null, null, "pas-un-email",
                "CULTURE"),
                authentication, httpRequest);

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verifyNoInteractions(activityService);
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

    /** ADR-0011, phase A : la précision géographique de chaque activité est exposée par la carte. */
    @Test
    void getNearbyActivities_ShouldExposeCoordinatePrecision() {
        Activity zone = new Activity(1L, "Fête au village", "Description", "fete", 43.91, 5.20,
                LocalDateTime.now(), null, "PUBLISHED", 1L, null, null, null, null, null, null, null, null, null, null);
        Activity theatre = new Activity(2L, "Concert", "Description", "concert", 43.95, 4.81,
                LocalDateTime.now(), null, "PUBLISHED", 1L, null, null, null, null, null, null, null, null, null, null);
        when(activityService.findNearby("43.9", "5.0", "50", null, null, null)).thenReturn(List.of(zone, theatre));
        when(sourceService.getAllSources()).thenReturn(List.of());
        when(coordinatePrecisionRepository.findByActivityIds(List.of(1L, 2L)))
                .thenReturn(Map.of(1L, CoordinatePrecision.APPROXIMATE));

        ResponseEntity<Object> response = activityController.getNearbyActivities(
                "43.9", "5.0", "50", null, null, null, null, httpRequest);

        @SuppressWarnings("unchecked")
        List<ActivityResponse> body = (List<ActivityResponse>) response.getBody();
        assertEquals("APPROXIMATE", body.get(0).coordinatePrecision());
        assertEquals("UNKNOWN", body.get(1).coordinatePrecision());
    }

    @Test
    void getNearbyActivities_ShouldResolveCreatedByUsername_WhenActivityWasManuallyCreated() {
        // LL-EF-010 : createdBy (id technique) résolu en createdByUsername (nom affiché,
        // « Créée par : ... »), même principe que sourceId -> sourceName (LL-8006).
        Activity nearby = new Activity(1L, "Pique-nique", "Description", "loisir", 43.29, 5.37,
                LocalDateTime.now(), null, "PUBLISHED", 1L, null, null, null, null, null, null, null, null, null,
                42L);
        Source source = new Source(1L, "MANUAL", "MANUAL", null, "ACTIVE", null, null, null);
        User creator = new User(42L, "jean.dupont", "jean@example.com", null, Role.USER, null, true);
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
