package com.locallife.backend.collector.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.locallife.backend.accessibility.application.AccessibilityService;
import com.locallife.backend.activity.domain.Activity;
import com.locallife.backend.activity.infrastructure.ActivityRepository;
import com.locallife.backend.activityclassification.application.ActivityClassificationService;
import com.locallife.backend.activityclassification.application.ActivityClassifier.Decision;
import com.locallife.backend.activityclassification.application.ClassificationOutcome;
import com.locallife.backend.activityclassification.application.ClassificationService;
import com.locallife.backend.activityclassification.domain.ClassificationStatus;
import com.locallife.backend.collector.domain.CollectedActivity;
import com.locallife.backend.collector.domain.CollectedLocation;
import com.locallife.backend.collector.domain.CollectedTiming;
import com.locallife.backend.collector.domain.Collector;
import com.locallife.backend.collector.infrastructure.CollectorException;
import com.locallife.backend.collector.infrastructure.OpenAgendaCollectorFactory;
import com.locallife.backend.link.application.ActivityLinkService;
import com.locallife.backend.media.application.ActivityMediaService;
import com.locallife.backend.media.application.MediaService;
import com.locallife.backend.metadata.application.ActivityMetadataService;
import com.locallife.backend.place.application.LocationService;
import com.locallife.backend.schedule.application.OccurrenceService;
import com.locallife.backend.schedule.application.ScheduleService;
import com.locallife.backend.schedule.domain.Schedule;
import com.locallife.backend.source.application.SourceService;
import com.locallife.backend.source.domain.Source;
import com.locallife.backend.tag.application.ActivityTagService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * LL-EF-005 : {@code ImportService} ne reçoit plus un {@code List<Collector>}
 * fixé au démarrage — il relit {@code sourceService.getAllSources()} à
 * chaque appel et construit un collecteur via
 * {@code OpenAgendaCollectorFactory} pour chaque source collectible. Chaque
 * test stub donc {@code sourceService.getAllSources()} pour renvoyer
 * {@link #SOURCE} (type {@code API}, statut {@code ACTIVE}, un
 * {@code agendaUid} non vide — les trois conditions de
 * {@code ImportService#isCollectible}), et
 * {@code openAgendaCollectorFactory.create(SOURCE)} pour renvoyer le
 * {@code collector} mocké — {@code collector.getSourceName()} n'est plus
 * consulté par {@code ImportService} (le nom vient directement de
 * {@code SOURCE.name()}) et n'a donc plus besoin d'être stubbé.
 */
@ExtendWith(MockitoExtension.class)
class ImportServiceTest {

    @Mock
    private Collector collector;

    @Mock
    private NormalizationService normalizationService;

    @Mock
    private DeduplicationService deduplicationService;

    @Mock
    private SourceService sourceService;

    @Mock
    private OpenAgendaCollectorFactory openAgendaCollectorFactory;

    @Mock
    private ActivityRepository activityRepository;

    // LL-11011 : nouvelles dépendances, voir la javadoc de ImportService (enrichActivity).
    // Aucun stub explicite nécessaire dans les tests ci-dessous : NormalizationService étant lui
    // aussi mocké, ses méthodes normalizeXxx renvoient par défaut Optional.empty()/List.of()
    // (comportement par défaut de Mockito), donc ces services ne sont jamais réellement invoqués
    // au-delà de clearEnrichment (méthodes void, sans effet sur un mock non stubbé).
    @Mock
    private LocationService locationService;

    @Mock
    private ScheduleService scheduleService;

    @Mock
    private OccurrenceService occurrenceService;

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

    // CAT-008 : classification centralisée, mockée ici ; sa logique est testée dans
    // ActivityClassifierTest / ClassificationServiceTest.
    @Mock
    private ClassificationService classificationService;

    @Mock
    private ActivityClassificationService activityClassificationService;

    private static final ClassificationOutcome AUTO_CLASSIFIED_AS_MARKETS = new ClassificationOutcome(
            new Decision(ClassificationStatus.AUTO_CLASSIFIED, List.of("MARKETS"), null, List.of(), "AUTO:MARKETS"),
            List.of(7L));

    private static final ClassificationOutcome NEEDS_REVIEW = new ClassificationOutcome(
            new Decision(ClassificationStatus.NEEDS_REVIEW, List.of(), "UNRECOGNIZED",
                    List.of(), "REVIEW:UNRECOGNIZED"), List.of());

    private ImportService importService() {
        // Par défaut un nouvel élément est classé automatiquement (lenient : tous les tests
        // n'atteignent pas l'étape de classification, ex. mise à jour d'une activité existante).
        lenient().when(classificationService.decide(any(), any(), any())).thenReturn(AUTO_CLASSIFIED_AS_MARKETS);
        // Issue #45 : un nouvel élément est créé avec ses catégories par ActivityClassificationService (une
        // transaction) ; ici l'activité est simplement sauvegardée via le repository mocké.
        lenient().doAnswer(invocation -> activityRepository.save(invocation.getArgument(0)))
                .when(activityClassificationService).createActivity(any(), any());
        return new ImportService(
                sourceService, openAgendaCollectorFactory, normalizationService, deduplicationService,
                activityRepository, locationService, scheduleService, occurrenceService, mediaService,
                activityMediaService, activityLinkService, accessibilityService, activityTagService,
                activityMetadataService, classificationService, activityClassificationService);
    }

    private static final Source SOURCE = new Source(
            10L, "OpenAgenda Marseille", "API", null, "ACTIVE", null, "agenda-uid-marseille", null);

    /** Stub commun à tous les tests : une seule source collectible, {@link #SOURCE}. */
    private void givenOneCollectibleSource() {
        when(sourceService.getAllSources()).thenReturn(List.of(SOURCE));
        when(openAgendaCollectorFactory.create(SOURCE)).thenReturn(collector);
    }

    private CollectedActivity collectedActivity(String externalId) {
        return new CollectedActivity(
                "Marché de Noël", "description", "marché", "https://example.com", externalId,
                "OpenAgenda Marseille", null, null, null, null,
                new CollectedLocation(null, null, null, null, null, null, 43.2965, 5.3698, null),
                List.of(new CollectedTiming(LocalDateTime.of(2026, 12, 1, 10, 0), null)),
                null, List.of(), List.of(), null, List.of(), null);
    }

    private Activity normalizedActivity() {
        return new Activity(
                null, "Marché de Noël", "description", "marché",
                43.2965, 5.3698, LocalDateTime.of(2026, 12, 1, 10, 0), null, "PUBLISHED", null, null,
                "https://example.com", null, null, null, null, null, null, null, null);
    }

    @Test
    void importAll_ShouldCreateNewActivity_WhenNoExistingMatchFound() {
        // Given
        givenOneCollectibleSource();
        CollectedActivity item = collectedActivity("ext-1");
        when(collector.collect()).thenReturn(List.of(item));
        when(deduplicationService.computeDeduplicationKey(item)).thenReturn("external:OpenAgenda Marseille:ext-1");
        when(normalizationService.normalize(item)).thenReturn(Optional.of(normalizedActivity()));
        when(activityRepository.findBySourceIdAndImportKey(10L, "external:OpenAgenda Marseille:ext-1"))
                .thenReturn(Optional.empty());
        when(activityRepository.findBySourceId(10L)).thenReturn(List.of());
        givenSaveAssignsId();

        // When
        List<ImportResult> results = importService().importAll();

        // Then
        assertEquals(1, results.size());
        ImportResult result = results.get(0);
        assertEquals("OpenAgenda Marseille", result.sourceName());
        assertEquals(1, result.fetched());
        assertEquals(1, result.created());
        assertEquals(0, result.updated());
        assertEquals(0, result.ignored());
        assertEquals(0, result.errors());
        assertEquals(0, result.archived());
        assertFalse(result.startedAt().isAfter(result.endedAt()));
        verify(activityRepository).save(argThatMatchesNewActivity());
    }

    /**
     * LL-11011 : {@code ImportService} lit désormais l'id de l'activité
     * sauvegardée pour persister le reste du modèle événementiel riche
     * ({@code enrichActivity}) — sans ce stub, l'id resterait {@code
     * null} (comportement par défaut d'un mock) et l'appel échouerait
     * (NullPointerException). Conserve l'id déjà présent sur l'activité
     * passée en argument (cas d'une mise à jour, voir {@code
     * ImportService#withSourceAndKey}) ou en assigne un nouveau (cas
     * d'une création).
     */
    private void givenSaveAssignsId() {
        when(activityRepository.save(any())).thenAnswer(invocation -> {
            Activity toSave = invocation.getArgument(0);
            Long id = toSave.id() != null ? toSave.id() : 99L;
            return new Activity(
                    id, toSave.title(), toSave.description(), toSave.category(),
                    toSave.latitude(), toSave.longitude(), toSave.startDate(), toSave.endDate(),
                    toSave.status(), toSave.sourceId(), toSave.importKey(), toSave.url(),
                    toSave.address(), toSave.city(), toSave.postalCode(),
                    toSave.longDescription(), toSave.conditions(), toSave.ageMin(), toSave.ageMax(),
                    toSave.createdBy());
        });
    }

    private Activity argThatMatchesNewActivity() {
        return argThat(activity ->
                activity.id() == null
                        && activity.sourceId().equals(10L)
                        && "external:OpenAgenda Marseille:ext-1".equals(activity.importKey()));
    }

    /**
     * LL-11016 (bug révélé par {@code mvn verify} en CI) : {@code
     * created}/{@code updated} s'incrémentaient auparavant après
     * l'enrichissement ({@code enrichActivity}) — une exception dans
     * cette étape (ici {@code scheduleService.create}) faisait compter
     * l'élément entier en erreur, alors que l'{@code Activity} elle-même
     * venait d'être sauvegardée avec succès. Voir {@code
     * ImportService#enrichActivitySafely}.
     */
    @Test
    void importAll_ShouldStillCountCreated_WhenEnrichmentFailsAfterActivitySaved() {
        // Given
        givenOneCollectibleSource();
        CollectedActivity item = collectedActivity("ext-1");
        when(collector.collect()).thenReturn(List.of(item));
        when(deduplicationService.computeDeduplicationKey(item)).thenReturn("external:OpenAgenda Marseille:ext-1");
        when(normalizationService.normalize(item)).thenReturn(Optional.of(normalizedActivity()));
        when(activityRepository.findBySourceIdAndImportKey(10L, "external:OpenAgenda Marseille:ext-1"))
                .thenReturn(Optional.empty());
        when(activityRepository.findBySourceId(10L)).thenReturn(List.of());
        givenSaveAssignsId();
        when(normalizationService.normalizeSchedule(eq(item), any(), any()))
                .thenReturn(Optional.of(new Schedule(null, 99L, null, null, null, null, null, null, null)));
        when(scheduleService.create(any())).thenThrow(new RuntimeException("boom-schedule"));

        // When
        List<ImportResult> results = importService().importAll();

        // Then : l'activité compte bien comme créée, l'échec de l'enrichissement
        // n'est pas comptabilisé comme une erreur de l'élément (voir la javadoc
        // d'ImportService#enrichActivitySafely).
        ImportResult result = results.get(0);
        assertEquals(1, result.created());
        assertEquals(0, result.errors());
        verify(activityRepository).save(argThatMatchesNewActivity());
    }

    @Test
    void importAll_ShouldUpdateExistingActivity_WhenMatchFoundForSameSource() {
        // Given
        givenOneCollectibleSource();
        CollectedActivity item = collectedActivity("ext-1");
        when(collector.collect()).thenReturn(List.of(item));
        when(deduplicationService.computeDeduplicationKey(item)).thenReturn("external:OpenAgenda Marseille:ext-1");
        when(normalizationService.normalize(item)).thenReturn(Optional.of(normalizedActivity()));
        Activity existing = new Activity(
                42L, "Marché de Noël", "old description", "marché",
                43.2965, 5.3698, LocalDateTime.of(2026, 12, 1, 10, 0), null, "PUBLISHED",
                10L, "external:OpenAgenda Marseille:ext-1", "https://example.com/old",
                null, null, null, null, null, null, null, null);
        when(activityRepository.findBySourceIdAndImportKey(10L, "external:OpenAgenda Marseille:ext-1"))
                .thenReturn(Optional.of(existing));
        when(activityRepository.findBySourceId(10L)).thenReturn(List.of(existing));
        givenSaveAssignsId();

        // When
        List<ImportResult> results = importService().importAll();

        // Then
        ImportResult result = results.get(0);
        assertEquals(0, result.created());
        assertEquals(1, result.updated());
        verify(activityRepository).save(argThat(activity -> activity.id() != null && activity.id().equals(42L)));
    }

    @Test
    void importAll_ShouldCountIgnored_WhenNormalizationRejectsData() {
        // Given
        givenOneCollectibleSource();
        CollectedActivity item = collectedActivity("ext-1");
        when(collector.collect()).thenReturn(List.of(item));
        when(deduplicationService.computeDeduplicationKey(item)).thenReturn("external:OpenAgenda Marseille:ext-1");
        when(normalizationService.normalize(item)).thenReturn(Optional.empty());
        when(activityRepository.findBySourceId(10L)).thenReturn(List.of());

        // When
        List<ImportResult> results = importService().importAll();

        // Then
        ImportResult result = results.get(0);
        assertEquals(1, result.fetched());
        assertEquals(0, result.created());
        assertEquals(0, result.updated());
        assertEquals(1, result.ignored());
        assertEquals(0, result.errors());
        verify(activityRepository, never()).findBySourceIdAndImportKey(any(), any());
    }

    @Test
    void importAll_ShouldCountError_WhenUnexpectedExceptionThrownForOneItem() {
        // Given : la déduplication échoue de façon inattendue sur cet élément précis.
        givenOneCollectibleSource();
        CollectedActivity item = collectedActivity("ext-1");
        when(collector.collect()).thenReturn(List.of(item));
        when(deduplicationService.computeDeduplicationKey(item)).thenThrow(new RuntimeException("boom"));
        when(activityRepository.findBySourceId(10L)).thenReturn(List.of());

        // When
        List<ImportResult> results = importService().importAll();

        // Then : l'erreur sur cet élément ne fait pas planter tout l'import.
        ImportResult result = results.get(0);
        assertEquals(1, result.fetched());
        assertEquals(0, result.created());
        assertEquals(0, result.ignored());
        assertEquals(1, result.errors());
        verify(activityRepository, never()).save(any());
    }

    @Test
    void importAll_ShouldReturnDegradedResult_WhenCollectorFailsEntirely() {
        // Given
        givenOneCollectibleSource();
        when(collector.collect()).thenThrow(new CollectorException("panne réseau", null));

        // When
        List<ImportResult> results = importService().importAll();

        // Then : pas d'exception propagée, un résultat dégradé est renvoyé pour cette source.
        assertEquals(1, results.size());
        ImportResult result = results.get(0);
        assertEquals(0, result.fetched());
        assertEquals(0, result.created());
        assertEquals(1, result.errors());
        verify(activityRepository, never()).findBySourceId(any());
    }

    @Test
    void importAll_ShouldRecordSync_WhenCollectSucceeds() {
        // Given : bug corrigé — lastSyncAt restait toujours null (voir SourceService#recordSync).
        givenOneCollectibleSource();
        when(collector.collect()).thenReturn(List.of());
        when(activityRepository.findBySourceId(10L)).thenReturn(List.of());

        // When
        List<ImportResult> results = importService().importAll();

        // Then : la date de synchronisation reportée est celle de fin d'import (endedAt).
        verify(sourceService).recordSync(10L, results.get(0).endedAt());
    }

    @Test
    void importAll_ShouldNotRecordSync_WhenCollectorFailsEntirely() {
        // Given : « dernier import réussi » (SOURCE_CONTRACT.md) — un échec total de collecte
        // n'est pas un import réussi.
        givenOneCollectibleSource();
        when(collector.collect()).thenThrow(new CollectorException("panne réseau", null));

        // When
        importService().importAll();

        // Then
        verify(sourceService, never()).recordSync(any(), any());
    }

    @Test
    void importAll_ShouldArchiveActivity_WhenNoLongerReturnedByCollector() {
        // Given: aucune donnée collectée cette fois, mais une activité existante pour cette source.
        givenOneCollectibleSource();
        when(collector.collect()).thenReturn(List.of());
        Activity previouslyImported = new Activity(
                42L, "Marché de Noël", "description", "marché",
                43.2965, 5.3698, LocalDateTime.of(2026, 12, 1, 10, 0), null, "PUBLISHED",
                10L, "external:OpenAgenda Marseille:ext-1", "https://example.com",
                null, null, null, null, null, null, null, null);
        when(activityRepository.findBySourceId(10L)).thenReturn(List.of(previouslyImported));

        // When
        List<ImportResult> results = importService().importAll();

        // Then
        ImportResult result = results.get(0);
        assertEquals(1, result.archived());
        verify(activityRepository).save(argThat(activity ->
                activity.id().equals(42L) && "ARCHIVED".equals(activity.status())));
    }

    @Test
    void importAll_ShouldNotReArchive_WhenActivityAlreadyArchived() {
        // Given
        givenOneCollectibleSource();
        when(collector.collect()).thenReturn(List.of());
        Activity alreadyArchived = new Activity(
                42L, "Marché de Noël", "description", "marché",
                43.2965, 5.3698, LocalDateTime.of(2026, 12, 1, 10, 0), null, "ARCHIVED",
                10L, "external:OpenAgenda Marseille:ext-1", "https://example.com",
                null, null, null, null, null, null, null, null);
        when(activityRepository.findBySourceId(10L)).thenReturn(List.of(alreadyArchived));

        // When
        List<ImportResult> results = importService().importAll();

        // Then
        assertEquals(0, results.get(0).archived());
        verify(activityRepository, never()).save(any());
    }

    @Test
    void importAll_ShouldNeverTouchManualActivities_BecauseScopedToImportedSourceId() {
        // Given : findBySourceId(10L) ne renvoie que les activités de cette source — les
        // activités manuelles (sourceId différent) ne sont jamais dans cette liste, par
        // construction du repository. On vérifie ici que le service interroge bien
        // findBySourceId avec l'id de la source importée, pas une recherche globale.
        givenOneCollectibleSource();
        when(collector.collect()).thenReturn(List.of());
        when(activityRepository.findBySourceId(10L)).thenReturn(List.of());

        // When
        importService().importAll();

        // Then
        verify(activityRepository, times(1)).findBySourceId(eq(10L));
    }

    @Test
    void importAll_ShouldIgnoreNonCollectibleSources() {
        // Given : une source MANUAL (jamais collectible) et une source API sans agendaUid
        // (type API mais pas encore configurée pour la collecte) ne doivent déclencher ni
        // collecte ni construction de collecteur.
        Source manualSource = new Source(1L, "Saisie manuelle", "MANUAL", null, "ACTIVE", null, null, null);
        Source apiSourceWithoutAgendaUid = new Source(2L, "Futur RSS", "API", null, "ACTIVE", null, null, null);
        Source inactiveSource = new Source(3L, "Agenda désactivé", "API", null, "INACTIVE", null, "uid-3", null);
        when(sourceService.getAllSources())
                .thenReturn(List.of(manualSource, apiSourceWithoutAgendaUid, inactiveSource));

        // When
        List<ImportResult> results = importService().importAll();

        // Then
        assertEquals(0, results.size());
        verify(openAgendaCollectorFactory, never()).create(any());
    }

    /** CAT-008 / issue #45 : un nouvel élément classé est créé avec ses catégories, et la trace est consignée. */
    @Test
    void importAll_ShouldCreateActivityWithDecidedCategories_AndRecordClassification() {
        // Given
        givenOneCollectibleSource();
        CollectedActivity item = collectedActivity("ext-1");
        when(collector.collect()).thenReturn(List.of(item));
        when(deduplicationService.computeDeduplicationKey(item)).thenReturn("external:OpenAgenda Marseille:ext-1");
        when(normalizationService.normalize(item)).thenReturn(Optional.of(normalizedActivity()));
        when(activityRepository.findBySourceIdAndImportKey(10L, "external:OpenAgenda Marseille:ext-1"))
                .thenReturn(Optional.empty());
        when(activityRepository.findBySourceId(10L)).thenReturn(List.of());
        givenSaveAssignsId();

        // When
        importService().importAll();

        // Then
        verify(activityClassificationService).createActivity(argThat(activity ->
                activity.id() == null && "external:OpenAgenda Marseille:ext-1".equals(activity.importKey())),
                eq(List.of(7L)));
        verify(classificationService).recordClassified(
                eq(10L), eq("external:OpenAgenda Marseille:ext-1"), eq(99L), eq("Marché de Noël"), eq("marché"), any(),
                eq(AUTO_CLASSIFIED_AS_MARKETS));
        verify(classificationService, never()).recordQuarantined(any(), any(), any(), any(), any(), any());
    }

    /** Issue #45 : plusieurs catégories détectées sont toutes enregistrées avec l'activité créée. */
    @Test
    void importAll_ShouldCreateActivityWithAllDetectedCategories() {
        // Given
        givenOneCollectibleSource();
        CollectedActivity item = collectedActivity("ext-1");
        when(collector.collect()).thenReturn(List.of(item));
        when(deduplicationService.computeDeduplicationKey(item)).thenReturn("external:OpenAgenda Marseille:ext-1");
        when(normalizationService.normalize(item)).thenReturn(Optional.of(normalizedActivity()));
        when(activityRepository.findBySourceIdAndImportKey(10L, "external:OpenAgenda Marseille:ext-1"))
                .thenReturn(Optional.empty());
        when(activityRepository.findBySourceId(10L)).thenReturn(List.of());
        givenSaveAssignsId();
        ClassificationOutcome severalCategories = new ClassificationOutcome(
                new Decision(ClassificationStatus.AUTO_CLASSIFIED, List.of("MARKETS", "GASTRONOMY", "CULTURE"), null,
                        List.of(), "AUTO:MARKETS+GASTRONOMY+CULTURE"),
                List.of(7L, 4L, 3L));
        ImportService service = importService();
        when(classificationService.decide(any(), any(), any())).thenReturn(severalCategories);

        // When
        ImportResult result = service.importAll().get(0);

        // Then
        assertEquals(1, result.created());
        assertEquals(0, result.needsReview());
        verify(activityClassificationService).createActivity(any(), eq(List.of(7L, 4L, 3L)));
    }

    /**
     * CAT-008 : classification ambiguë → aucune activité créée (au moins une catégorie obligatoire,
     * aucune catégorie inventée), l'élément est consigné pour relecture et compté dans needsReview, pas dans ignored.
     */
    @Test
    void importAll_ShouldNotCreateActivity_AndQuarantine_WhenClassificationNeedsReview() {
        // Given
        givenOneCollectibleSource();
        CollectedActivity item = collectedActivity("ext-1");
        when(collector.collect()).thenReturn(List.of(item));
        when(deduplicationService.computeDeduplicationKey(item)).thenReturn("external:OpenAgenda Marseille:ext-1");
        when(normalizationService.normalize(item)).thenReturn(Optional.of(normalizedActivity()));
        when(activityRepository.findBySourceIdAndImportKey(10L, "external:OpenAgenda Marseille:ext-1"))
                .thenReturn(Optional.empty());
        when(activityRepository.findBySourceId(10L)).thenReturn(List.of());
        // importService() pose le stub par défaut (classé) : le construire AVANT de surcharger le stub.
        ImportService service = importService();
        when(classificationService.decide(any(), any(), any())).thenReturn(NEEDS_REVIEW);

        // When
        List<ImportResult> results = service.importAll();

        // Then
        ImportResult result = results.get(0);
        assertEquals(0, result.created());
        assertEquals(0, result.ignored());
        assertEquals(1, result.needsReview());
        assertEquals(0, result.errors());
        verify(activityRepository, never()).save(any());
        verify(classificationService).recordQuarantined(
                eq(10L), eq("external:OpenAgenda Marseille:ext-1"), eq("Marché de Noël"), eq("marché"), any(),
                eq(NEEDS_REVIEW));
        verify(classificationService, never()).recordClassified(any(), any(), any(), any(), any(), any(), any());
    }

    /** Fallback titre : ImportService transmet titre, catégorie d'origine et tags à ClassificationService. */
    @Test
    void importAll_ShouldPassTitleCategoryAndTagsToClassificationService() {
        // Given
        givenOneCollectibleSource();
        CollectedActivity item = collectedActivity("ext-1");
        when(collector.collect()).thenReturn(List.of(item));
        when(deduplicationService.computeDeduplicationKey(item)).thenReturn("external:OpenAgenda Marseille:ext-1");
        when(normalizationService.normalize(item)).thenReturn(Optional.of(normalizedActivity()));
        when(activityRepository.findBySourceIdAndImportKey(10L, "external:OpenAgenda Marseille:ext-1"))
                .thenReturn(Optional.empty());
        when(activityRepository.findBySourceId(10L)).thenReturn(List.of());
        givenSaveAssignsId();

        // When
        importService().importAll();

        // Then : la décision (source d'abord, titre en repli) reste dans ClassificationService.
        verify(classificationService).decide(eq(item.title()), eq(item.category()), eq(item.tags()));
    }

    /** CAT-008 : un ré-import ne reclasse pas une activité existante (pas d'écrasement d'une classification). */
    @Test
    void importAll_ShouldKeepExistingCategories_WithoutReclassifying() {
        // Given
        givenOneCollectibleSource();
        CollectedActivity item = collectedActivity("ext-1");
        when(collector.collect()).thenReturn(List.of(item));
        when(deduplicationService.computeDeduplicationKey(item)).thenReturn("external:OpenAgenda Marseille:ext-1");
        when(normalizationService.normalize(item)).thenReturn(Optional.of(normalizedActivity()));
        Activity existing = new Activity(
                42L, "Marché de Noël", "old description", "marché",
                43.2965, 5.3698, LocalDateTime.of(2026, 12, 1, 10, 0), null, "PUBLISHED",
                10L, "external:OpenAgenda Marseille:ext-1", "https://example.com/old",
                null, null, null, null, null, null, null, null);
        when(activityRepository.findBySourceIdAndImportKey(10L, "external:OpenAgenda Marseille:ext-1"))
                .thenReturn(Optional.of(existing));
        when(activityRepository.findBySourceId(10L)).thenReturn(List.of(existing));
        givenSaveAssignsId();

        // When
        importService().importAll();

        // Then
        verify(classificationService, never()).decide(any(), any(), any());
        verify(activityRepository).save(argThat(activity -> Long.valueOf(42L).equals(activity.id())));
        // Les catégories existantes ne sont ni recalculées ni recréées.
        verify(activityClassificationService, never()).createActivity(any(), any());
        verify(activityClassificationService, never()).replaceCategories(any(), any());
    }

}
