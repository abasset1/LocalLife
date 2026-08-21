package com.locallife.backend.collector.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.locallife.backend.activity.domain.Activity;
import com.locallife.backend.activity.infrastructure.ActivityRepository;
import com.locallife.backend.collector.domain.CollectedActivity;
import com.locallife.backend.collector.domain.Collector;
import com.locallife.backend.collector.infrastructure.CollectorException;
import com.locallife.backend.source.domain.Source;
import com.locallife.backend.source.infrastructure.SourceRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tests du pipeline complet (LL-5010) : contexte Spring réel, base réelle
 * (comme {@code ActivityRepositoryIntegrationTest}/
 * {@code UserRepositoryIntegrationTest}) — seul {@code Collector} est
 * remplacé par un mock ({@code @MockitoBean}, remplacement recommandé
 * depuis Spring Boot 3.4 pour l'ancien {@code @MockBean}, retiré en 4.0) :
 * c'est la seule véritable frontière externe du pipeline (appel réseau
 * vers OpenAgenda). {@code NormalizationService}, {@code
 * DeduplicationService}, {@code SourceService}/{@code SourceRepository}
 * et {@code ActivityRepository} sont les implémentations réelles.
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
@Transactional
class ImportServiceIntegrationTest {

    @Autowired
    private ImportService importService;

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private SourceRepository sourceRepository;

    @MockitoBean
    private Collector collector;

    private String uniqueSourceName() {
        return "Test Source " + UUID.randomUUID();
    }

    private CollectedActivity validItem(String sourceName, String externalId, String title) {
        return new CollectedActivity(
                title, "description", LocalDateTime.now().plusDays(1), null,
                "marché", 43.2965, 5.3698, "https://example.com", externalId, sourceName);
    }

    private CollectedActivity invalidItem(String sourceName) {
        // Titre vide : rejeté par NormalizationService (LL-5005).
        return new CollectedActivity(
                "   ", "description", LocalDateTime.now().plusDays(1), null,
                "marché", 43.2965, 5.3698, "https://example.com", "ext-invalid", sourceName);
    }

    private Long sourceIdFor(String sourceName) {
        return sourceRepository.findByName(sourceName).map(Source::id).orElseThrow();
    }

    @Test
    void importAll_ShouldPersistNewActivity_WhenDataIsValid() {
        // Given : donnée valide, nouvelle activité.
        String sourceName = uniqueSourceName();
        when(collector.getSourceName()).thenReturn(sourceName);
        when(collector.collect()).thenReturn(List.of(validItem(sourceName, "ext-1", "Marché de Noël")));

        // When
        List<ImportResult> results = importService.importAll();

        // Then
        ImportResult result = results.get(0);
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
        when(collector.getSourceName()).thenReturn(sourceName);
        when(collector.collect()).thenReturn(List.of(invalidItem(sourceName)));

        // When
        List<ImportResult> results = importService.importAll();

        // Then
        ImportResult result = results.get(0);
        assertThat(result.ignored()).isEqualTo(1);
        assertThat(result.created()).isZero();
        assertThat(activityRepository.findBySourceId(sourceIdFor(sourceName))).isEmpty();
    }

    @Test
    void importAll_ShouldNotCreateDuplicate_WhenSameDataImportedTwice() {
        // Given : même donnée collectée deux imports de suite.
        String sourceName = uniqueSourceName();
        when(collector.getSourceName()).thenReturn(sourceName);
        when(collector.collect()).thenReturn(List.of(validItem(sourceName, "ext-1", "Marché de Noël")));

        // When
        importService.importAll();
        List<ImportResult> secondRun = importService.importAll();

        // Then : toujours une seule activité en base pour cette source.
        assertThat(activityRepository.findBySourceId(sourceIdFor(sourceName))).hasSize(1);
        assertThat(secondRun.get(0).created()).isZero();
        assertThat(secondRun.get(0).updated()).isEqualTo(1);
    }

    @Test
    void importAll_ShouldCreateNewActivity_OnFirstImport() {
        // Given
        String sourceName = uniqueSourceName();
        when(collector.getSourceName()).thenReturn(sourceName);
        when(collector.collect()).thenReturn(List.of(validItem(sourceName, "ext-1", "Concert")));

        // When
        List<ImportResult> results = importService.importAll();

        // Then
        assertThat(results.get(0).created()).isEqualTo(1);
        assertThat(activityRepository.findBySourceId(sourceIdFor(sourceName))).hasSize(1);
    }

    @Test
    void importAll_ShouldUpdateExistingActivity_WhenDataChangedOnSecondImport() {
        // Given : première collecte, puis la même donnée avec un titre modifié.
        String sourceName = uniqueSourceName();
        when(collector.getSourceName()).thenReturn(sourceName);
        when(collector.collect()).thenReturn(List.of(validItem(sourceName, "ext-1", "Marché de Noël")));
        importService.importAll();
        Long activityId = activityRepository.findBySourceId(sourceIdFor(sourceName)).get(0).id();

        when(collector.collect()).thenReturn(List.of(validItem(sourceName, "ext-1", "Marché de Noël (modifié)")));

        // When
        List<ImportResult> results = importService.importAll();

        // Then : même ligne mise à jour, pas une nouvelle.
        assertThat(results.get(0).updated()).isEqualTo(1);
        assertThat(results.get(0).created()).isZero();
        List<Activity> persisted = activityRepository.findBySourceId(sourceIdFor(sourceName));
        assertThat(persisted).hasSize(1);
        assertThat(persisted.get(0).id()).isEqualTo(activityId);
        assertThat(persisted.get(0).title()).isEqualTo("Marché de Noël (modifié)");
    }

    @Test
    void importAll_ShouldReturnDegradedResult_WhenCollectorThrows() {
        // Given : le collecteur échoue entièrement (ex. panne réseau, configuration manquante).
        String sourceName = uniqueSourceName();
        when(collector.getSourceName()).thenReturn(sourceName);
        when(collector.collect()).thenThrow(new CollectorException("panne réseau", null));

        // When
        List<ImportResult> results = importService.importAll();

        // Then : pas d'exception propagée, aucune activité créée.
        ImportResult result = results.get(0);
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
        when(collector.getSourceName()).thenReturn(sourceName);
        when(collector.collect()).thenReturn(List.of(validItem(sourceName, "ext-1", "Marché de Noël")));
        importService.importAll();
        Long activityId = activityRepository.findBySourceId(sourceIdFor(sourceName)).get(0).id();

        when(collector.collect()).thenReturn(List.of());

        // When
        List<ImportResult> results = importService.importAll();

        // Then : l'activité disparue de la source est archivée, pas supprimée.
        assertThat(results.get(0).archived()).isEqualTo(1);
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
        when(collector.getSourceName()).thenReturn(sourceName);
        when(collector.collect()).thenReturn(List.of());

        // When
        List<ImportResult> results = importService.importAll();

        // Then
        ImportResult result = results.get(0);
        assertThat(result.fetched()).isZero();
        assertThat(result.created()).isZero();
        assertThat(result.updated()).isZero();
        assertThat(result.errors()).isZero();
        assertThat(activityRepository.findBySourceId(sourceIdFor(sourceName))).isEmpty();
    }

}
