package com.locallife.backend.collector.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.locallife.backend.activity.domain.Activity;
import com.locallife.backend.activity.infrastructure.ActivityRepository;
import com.locallife.backend.collector.domain.CollectedActivity;
import com.locallife.backend.collector.domain.Collector;
import com.locallife.backend.collector.infrastructure.CollectorException;
import com.locallife.backend.collector.infrastructure.SingleMockCollectorConfig;
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
     * exister en base avant l'import.
     */
    private void createCollectibleSource(String name) {
        sourceRepository.save(
                new Source(null, name, "API", null, "ACTIVE", null, "agenda-uid-" + UUID.randomUUID(), null));
    }

    private CollectedActivity validItem(String sourceName, String externalId, String title) {
        return new CollectedActivity(
                title, "description", LocalDateTime.now().plusDays(1), null,
                "marché", 43.2965, 5.3698, "https://example.com", externalId, sourceName,
                null, null, null, null, null, null, null);
    }

    private CollectedActivity invalidItem(String sourceName) {
        // Titre vide : rejeté par NormalizationService (LL-5005).
        return new CollectedActivity(
                "   ", "description", LocalDateTime.now().plusDays(1), null,
                "marché", 43.2965, 5.3698, "https://example.com", "ext-invalid", sourceName,
                null, null, null, null, null, null, null);
    }

    private Long sourceIdFor(String sourceName) {
        return sourceRepository.findByName(sourceName).map(Source::id).orElseThrow();
    }

    /**
     * Isole le résultat correspondant à la source de ce test.
     *
     * <p>Depuis la migration {@code V14__add_agenda_fields_to_source.sql}
     * (LL-EF-005), deux sources OpenAgenda réelles sont déjà collectibles
     * en base (type {@code API}, statut {@code ACTIVE}, {@code agendaUid}
     * renseigné) — {@code results} contient donc systématiquement au moins
     * 3 éléments (les 2 sources seedées + la source propre à ce test), pas
     * un seul comme avant ce ticket. {@code collector} étant mocké de
     * façon partagée pour toute {@code Source} ({@code SingleMockCollectorConfig}),
     * le comportement stubbé (succès, erreur...) s'applique aussi aux 2
     * sources seedées, sans conséquence sur les assertions de ce fichier
     * (scopées par {@code sourceIdFor(sourceName)}, jamais par
     * {@code results.get(0)}) tant que ce résultat lui-même est bien
     * identifié par nom plutôt que par position dans la liste.
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
        assertThat(resultFor(secondRun, sourceName).updated()).isEqualTo(1);
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

}
