package com.locallife.backend.collector.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import com.locallife.backend.activity.domain.Activity;
import com.locallife.backend.activity.infrastructure.ActivityRepository;
import com.locallife.backend.collector.domain.CollectedActivity;
import com.locallife.backend.collector.domain.CollectedLocation;
import com.locallife.backend.collector.domain.CollectedTiming;
import com.locallife.backend.collector.domain.CollectionReport;
import com.locallife.backend.collector.domain.Collector;
import com.locallife.backend.collector.infrastructure.CollectorException;
import com.locallife.backend.collector.infrastructure.ImportTestSourceService;
import com.locallife.backend.collector.infrastructure.SingleMockCollectorConfig;
import com.locallife.backend.collector.infrastructure.SourceSyncStateRepository;
import com.locallife.backend.source.domain.Source;
import com.locallife.backend.source.infrastructure.SourceRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tests du pipeline complet (LL-5010) : contexte Spring réel, base réelle
 * (comme {@code ActivityRepositoryIntegrationTest}/
 * {@code UserRepositoryIntegrationTest}) — seul {@code Collector} est
 * remplacé par un mock ({@code SingleMockCollectorConfig}, dont
 * {@code OpenAgendaCollectorFactory} mocké renvoie ce même {@code Collector}
 * pour n'importe quelle {@code Source}, voir sa Javadoc) : c'est la seule
 * véritable frontière externe du pipeline (appel réseau vers OpenAgenda).
 * {@code NormalizationService}, {@code DeduplicationService},
 * {@code SourceService}/{@code SourceRepository} et
 * {@code ActivityRepository} sont les implémentations réelles.
 *
 * <b>Sources dynamiques (LL-EF-005)</b> : depuis ce ticket,
 * {@code ImportService} ne collecte que les sources déjà présentes en
 * base (type {@code API}, statut {@code ACTIVE}, {@code agendaUid} non
 * vide) — chaque test doit donc persister une telle source avant
 * d'appeler {@code importAll()} (voir {@link #createCollectibleSource}),
 * alors qu'avant ce ticket la source était créée automatiquement à la
 * volée à partir de {@code collector.getSourceName()}
 * ({@code SourceService#findOrCreateByName}, supprimée).
 *
 * Couvre les 7 cas demandés par {@code SPRINT_5.md} : donnée valide,
 * donnée invalide, doublon, nouvelle activité, mise à jour, erreur du
 * collecteur, import vide.
 *
 * Un nom de source unique ({@code UUID}) par test évite toute
 * interférence entre tests, comme {@code activityAt} dans
 * {@code ActivityRepositoryIntegrationTest}.
 */
@SpringBootTest
@Import(SingleMockCollectorConfig.class)
@Transactional
class ImportServiceIntegrationTest {

    @Autowired
    private ImportService importService;

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private SourceRepository sourceRepository;

    @Autowired
    private ImportTestSourceService importTestSourceService;

    @Autowired
    private Collector collector;

    /**
     * {@code @MockitoBean} réinitialisait automatiquement le mock entre
     * chaque test (comportement par défaut, {@code MockReset.AFTER}) —
     * un simple {@code @Autowired} sur le bean fourni par
     * {@code SingleMockCollectorConfig} (LL-8009) ne le fait plus,
     * puisque ce n'est plus {@code @MockitoBean} qui gère ce bean.
     * Reproduit le même comportement explicitement.
     */
    @BeforeEach
    void resetCollectorMock() {
        Mockito.reset(collector);
    }

    private String uniqueSourceName() {
        return "Test Source " + UUID.randomUUID();
    }

    /**
     * Persiste une source collectible (type {@code API}, statut
     * {@code ACTIVE}, {@code agendaUid} non vide) pour que
     * {@code ImportService} appelle {@code OpenAgendaCollectorFactory}
     * dessus — remplace, depuis LL-EF-005, le stub
     * {@code collector.getSourceName()} d'avant ce ticket : la source
     * n'est plus déduite du collecteur après collecte
     * ({@code SourceService#findOrCreateByName}, supprimée), elle doit
     * exister en base avant l'import. Passe par
     * {@link ImportTestSourceService} : seule cette source sera parcourue
     * par {@code importAll()}, jamais les agendas réels présents en base.
     */
    private void createCollectibleSource(String name) {
        importTestSourceService.createCollectibleTestSource(name);
    }

    private CollectedActivity validItem(String sourceName, String externalId, String title) {
        return new CollectedActivity(
                title, "description", "marché", "https://example.com", externalId, sourceName,
                null, null, null, null,
                new CollectedLocation(null, null, null, null, null, null, 43.2965, 5.3698, null),
                List.of(new CollectedTiming(LocalDateTime.now().plusDays(1), null)),
                null, List.of(), List.of(), null, List.of(), null);
    }

    private CollectedActivity invalidItem(String sourceName) {
        // Titre vide : rejeté par NormalizationService (LL-5005).
        return new CollectedActivity(
                "   ", "description", "marché", "https://example.com", "ext-invalid", sourceName,
                null, null, null, null,
                new CollectedLocation(null, null, null, null, null, null, 43.2965, 5.3698, null),
                List.of(new CollectedTiming(LocalDateTime.now().plusDays(1), null)),
                null, List.of(), List.of(), null, List.of(), null);
    }

    private Long sourceIdFor(String sourceName) {
        return sourceRepository.findByName(sourceName).map(Source::id).orElseThrow();
    }

    /**
     * Isole le résultat correspondant à la source de ce test.
     *
     * <p>{@code importAll()} ne parcourt que les sources créées par le test
     * ({@link ImportTestSourceService}) : les agendas réels présents en base
     * (seedés par {@code V14__add_agenda_fields_to_source.sql} ou ajoutés
     * depuis l'administration) ne sont ni collectés ni modifiés. Le résultat
     * reste identifié par nom plutôt que par position dans la liste.
     */
    private ImportResult resultFor(List<ImportResult> results, String sourceName) {
        return results.stream()
                .filter(result -> sourceName.equals(result.sourceName()))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void importAll_ShouldPersistNewActivity_WhenDataIsValid() {
        // Given : donnée valide, nouvelle activité.
        String sourceName = uniqueSourceName();
        createCollectibleSource(sourceName);
        when(collector.collect()).thenReturn(List.of(validItem(sourceName, "ext-1", "Marché de Noël")));

        // When
        List<ImportResult> results = importService.importAll();

        // Then
        ImportResult result = resultFor(results, sourceName);
        assertThat(result.fetched()).isEqualTo(1);
        assertThat(result.created()).isEqualTo(1);
        assertThat(result.ignored()).isZero();
        List<Activity> persisted = activityRepository.findBySourceId(sourceIdFor(sourceName));
        assertThat(persisted).hasSize(1);
        assertThat(persisted.get(0).title()).isEqualTo("Marché de Noël");
        assertThat(persisted.get(0).status()).isEqualTo("PUBLISHED");
    }

    @Test
    void importAll_ShouldRejectData_WhenInvalid() {
        // Given : donnée invalide (titre vide).
        String sourceName = uniqueSourceName();
        createCollectibleSource(sourceName);
        when(collector.collect()).thenReturn(List.of(invalidItem(sourceName)));

        // When
        List<ImportResult> results = importService.importAll();

        // Then
        ImportResult result = resultFor(results, sourceName);
        assertThat(result.ignored()).isEqualTo(1);
        assertThat(result.created()).isZero();
        assertThat(activityRepository.findBySourceId(sourceIdFor(sourceName))).isEmpty();
    }

    @Test
    void importAll_ShouldNotCreateDuplicate_WhenSameDataImportedTwice() {
        // Given : même donnée collectée deux imports de suite.
        String sourceName = uniqueSourceName();
        createCollectibleSource(sourceName);
        when(collector.collect()).thenReturn(List.of(validItem(sourceName, "ext-1", "Marché de Noël")));

        // When
        importService.importAll();
        List<ImportResult> secondRun = importService.importAll();

        // Then : toujours une seule activité en base pour cette source.
        assertThat(activityRepository.findBySourceId(sourceIdFor(sourceName))).hasSize(1);
        assertThat(resultFor(secondRun, sourceName).created()).isZero();
        // Import incrémental : contenu identique, l'activité n'est pas réécrite.
        assertThat(resultFor(secondRun, sourceName).updated()).isZero();
        assertThat(resultFor(secondRun, sourceName).unchanged()).isEqualTo(1);
    }

    @Test
    void importAll_ShouldCreateNewActivity_OnFirstImport() {
        // Given
        String sourceName = uniqueSourceName();
        createCollectibleSource(sourceName);
        when(collector.collect()).thenReturn(List.of(validItem(sourceName, "ext-1", "Concert")));

        // When
        List<ImportResult> results = importService.importAll();

        // Then
        assertThat(resultFor(results, sourceName).created()).isEqualTo(1);
        assertThat(activityRepository.findBySourceId(sourceIdFor(sourceName))).hasSize(1);
    }

    @Test
    void importAll_ShouldUpdateExistingActivity_WhenDataChangedOnSecondImport() {
        // Given : première collecte, puis la même donnée avec un titre modifié.
        String sourceName = uniqueSourceName();
        createCollectibleSource(sourceName);
        when(collector.collect()).thenReturn(List.of(validItem(sourceName, "ext-1", "Marché de Noël")));
        importService.importAll();
        Long activityId = activityRepository.findBySourceId(sourceIdFor(sourceName)).get(0).id();

        when(collector.collect()).thenReturn(List.of(validItem(sourceName, "ext-1", "Marché de Noël (modifié)")));

        // When
        List<ImportResult> results = importService.importAll();

        // Then : même ligne mise à jour, pas une nouvelle.
        assertThat(resultFor(results, sourceName).updated()).isEqualTo(1);
        assertThat(resultFor(results, sourceName).created()).isZero();
        List<Activity> persisted = activityRepository.findBySourceId(sourceIdFor(sourceName));
        assertThat(persisted).hasSize(1);
        assertThat(persisted.get(0).id()).isEqualTo(activityId);
        assertThat(persisted.get(0).title()).isEqualTo("Marché de Noël (modifié)");
    }

    @Test
    void importAll_ShouldKeepAnActivityCancelledByAnAdministrator_OnLaterImports() {
        // Given : activité importée, annulée par un administrateur, puis modifiée par la source,
        // absente d'une collecte, et enfin revenue.
        String sourceName = uniqueSourceName();
        createCollectibleSource(sourceName);
        when(collector.collect()).thenReturn(List.of(validItem(sourceName, "ext-1", "Marché de Noël")));
        importService.importAll();
        Activity imported = activityRepository.findBySourceId(sourceIdFor(sourceName)).get(0);
        activityRepository.save(new Activity(
                imported.id(), imported.title(), imported.description(), imported.category(),
                imported.latitude(), imported.longitude(), imported.startDate(), imported.endDate(), "CANCELLED",
                imported.sourceId(), imported.importKey(), imported.url(), imported.address(), imported.city(),
                imported.postalCode(), imported.longDescription(), imported.conditions(), imported.ageMin(),
                imported.ageMax(), imported.createdBy()));

        // When : contenu modifié par la source.
        when(collector.collect()).thenReturn(List.of(validItem(sourceName, "ext-1", "Marché de Noël (modifié)")));
        importService.importAll();

        // Then : contenu mis à jour, toujours annulée.
        Activity updated = activityRepository.findById(imported.id()).orElseThrow();
        assertThat(updated.title()).isEqualTo("Marché de Noël (modifié)");
        assertThat(updated.status()).isEqualTo("CANCELLED");

        // When : absente d'une collecte, puis revenue.
        when(collector.collect()).thenReturn(List.of());
        assertThat(resultFor(importService.importAll(), sourceName).archived()).isZero();
        when(collector.collect()).thenReturn(List.of(validItem(sourceName, "ext-1", "Marché de Noël (revenu)")));
        importService.importAll();

        // Then : jamais archivée ni republiée.
        assertThat(activityRepository.findById(imported.id()).orElseThrow().status()).isEqualTo("CANCELLED");
    }

    @Test
    void importAll_ShouldReturnDegradedResult_WhenCollectorThrows() {
        // Given : le collecteur échoue entièrement (ex. panne réseau, configuration manquante).
        String sourceName = uniqueSourceName();
        createCollectibleSource(sourceName);
        when(collector.collect()).thenThrow(new CollectorException("panne réseau", null));

        // When
        List<ImportResult> results = importService.importAll();

        // Then : pas d'exception propagée, aucune activité créée.
        ImportResult result = resultFor(results, sourceName);
        assertThat(result.fetched()).isZero();
        assertThat(result.errors()).isEqualTo(1);
        assertThat(activityRepository.findBySourceId(sourceIdFor(sourceName))).isEmpty();
    }

    @Test
    void importAll_ShouldArchiveActivity_WhenNoLongerInSource() {
        // Given : une activité importée une première fois, puis absente
        // de la collecte suivante (LL-7003 : reproduit le blocage réel où
        // chk_activity_status n'autorisait pas ARCHIVED, corrigé en LL-7007).
        String sourceName = uniqueSourceName();
        createCollectibleSource(sourceName);
        when(collector.collect()).thenReturn(List.of(validItem(sourceName, "ext-1", "Marché de Noël")));
        importService.importAll();
        Long activityId = activityRepository.findBySourceId(sourceIdFor(sourceName)).get(0).id();

        when(collector.collect()).thenReturn(List.of());

        // When
        List<ImportResult> results = importService.importAll();

        // Then : l'activité disparue de la source est archivée, pas supprimée.
        assertThat(resultFor(results, sourceName).archived()).isEqualTo(1);
        Activity archived = activityRepository.findBySourceId(sourceIdFor(sourceName)).stream()
                .filter(activity -> activity.id().equals(activityId))
                .findFirst()
                .orElseThrow();
        assertThat(archived.status()).isEqualTo("ARCHIVED");
    }

    @Test
    void importAll_ShouldHandleEmptyImport_WithoutError() {
        // Given : le collecteur ne retourne rien.
        String sourceName = uniqueSourceName();
        createCollectibleSource(sourceName);
        when(collector.collect()).thenReturn(List.of());

        // When
        List<ImportResult> results = importService.importAll();

        // Then
        ImportResult result = resultFor(results, sourceName);
        assertThat(result.fetched()).isZero();
        assertThat(result.created()).isZero();
        assertThat(result.updated()).isZero();
        assertThat(result.errors()).isZero();
        assertThat(activityRepository.findBySourceId(sourceIdFor(sourceName))).isEmpty();
    }

    /** CAT-008 : classification non décidable -> aucune activité, compté dans needsReview (pas ignored). */
    @Test
    void importAll_ShouldNotPublishAndCountNeedsReview_WhenCategoryIsNotDecidable() {
        String sourceName = uniqueSourceName();
        createCollectibleSource(sourceName);
        // Un public n'est pas une catégorie (aucun mot-clé) : non décidable. (« Recrutement » ne l'est plus
        // depuis la catégorie EMPLOYMENT, issue #49.)
        CollectedActivity item = validItem(sourceName, "ext-review", "Après-midi récréatif");
        CollectedActivity ambiguous = new CollectedActivity(
                item.title(), item.description(), "Jeune public", item.sourceUrl(), item.externalId(),
                item.source(), null, null, null, null, item.location(), item.timings(),
                null, List.of(), List.of(), null, List.of(), null);
        when(collector.collect()).thenReturn(List.of(ambiguous));

        List<ImportResult> results = importService.importAll();

        ImportResult result = resultFor(results, sourceName);
        assertThat(result.fetched()).isEqualTo(1);
        assertThat(result.created()).isZero();
        assertThat(result.ignored()).isZero();
        assertThat(result.needsReview()).isEqualTo(1);
        assertThat(result.errors()).isZero();
        assertThat(activityRepository.findBySourceId(sourceIdFor(sourceName))).isEmpty();
    }


    // --- Sprint « agendas exhaustifs » (AGD-04/05) : collecte partielle ou en échec ---

    @Autowired
    private SourceSyncStateRepository sourceSyncStateRepository;

    private List<CollectedActivity> items(String sourceName, int count) {
        List<CollectedActivity> items = new java.util.ArrayList<>();
        for (int index = 0; index < count; index++) {
            items.add(validItem(sourceName, "ext-" + index, "Marché " + index));
        }
        return items;
    }

    private SourceSyncState syncStateFor(String sourceName) {
        return sourceSyncStateRepository.findAll().stream()
                .filter(state -> sourceName.equals(state.sourceName()))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void importAll_ShouldNeverArchive_WhenTheCollectionIsPartial() {
        String sourceName = uniqueSourceName();
        createCollectibleSource(sourceName);
        when(collector.collect()).thenReturn(items(sourceName, 20));
        importService.importAll();
        Long sourceId = sourceIdFor(sourceName);
        LocalDateTime lastCompleteSync = sourceRepository.findById(sourceId).orElseThrow().lastSyncAt();
        assertThat(lastCompleteSync).isNotNull();
        assertThat(syncStateFor(sourceName).lastStatus()).isEqualTo(SyncStatus.SUCCESS);

        // Collecte interrompue après 2 éléments sur 20 : 18 sont absents, mais sur des pages non lues.
        doReturn(new CollectionReport(items(sourceName, 2), CollectionReport.Status.PARTIAL, 1, 2,
                "Collecte incomplète : limite de sécurité de 1 pages atteinte."))
                .when(collector).collectWithReport();

        ImportResult result = resultFor(importService.importAll(), sourceName);

        assertThat(result.status()).isEqualTo(SyncStatus.PARTIAL);
        assertThat(result.archived()).isZero();
        assertThat(result.blockedReason()).contains("Aucun archivage");
        assertThat(activityRepository.findBySourceId(sourceId))
                .hasSize(20)
                .allMatch(activity -> "PUBLISHED".equals(activity.status()));
        // Le dernier succès complet ne bouge pas ; le dernier essai est consigné comme partiel.
        assertThat(sourceRepository.findById(sourceId).orElseThrow().lastSyncAt()).isEqualTo(lastCompleteSync);
        SourceSyncState state = syncStateFor(sourceName);
        assertThat(state.lastStatus()).isEqualTo(SyncStatus.PARTIAL);
        assertThat(state.lastMessage()).contains("limite de sécurité");
        assertThat(state.lastSuccessAt()).isEqualTo(lastCompleteSync);
    }

    @Test
    void importAll_ShouldStillCreateTheItemsReceived_WhenTheCollectionIsPartial() {
        String sourceName = uniqueSourceName();
        createCollectibleSource(sourceName);
        doReturn(new CollectionReport(items(sourceName, 3), CollectionReport.Status.PARTIAL, 1, 3,
                "Collecte incomplète : page 2 en échec."))
                .when(collector).collectWithReport();

        ImportResult result = resultFor(importService.importAll(), sourceName);

        assertThat(result.status()).isEqualTo(SyncStatus.PARTIAL);
        assertThat(result.created()).isEqualTo(3);
        assertThat(sourceRepository.findById(sourceIdFor(sourceName)).orElseThrow().lastSyncAt()).isNull();
    }

    @Test
    void importAll_ShouldRecordAFailedAttempt_WithoutTouchingTheLastCompleteSync() {
        String sourceName = uniqueSourceName();
        createCollectibleSource(sourceName);
        when(collector.collect()).thenThrow(new CollectorException("OpenAgenda injoignable.", null));

        ImportResult result = resultFor(importService.importAll(), sourceName);

        assertThat(result.status()).isEqualTo(SyncStatus.FAILED);
        SourceSyncState state = syncStateFor(sourceName);
        assertThat(state.lastStatus()).isEqualTo(SyncStatus.FAILED);
        assertThat(state.lastMessage()).contains("OpenAgenda injoignable.");
        assertThat(state.lastSuccessAt()).isNull();
    }

}
