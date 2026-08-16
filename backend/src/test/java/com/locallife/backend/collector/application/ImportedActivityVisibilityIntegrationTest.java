package com.locallife.backend.collector.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.locallife.backend.activity.application.ActivityService;
import com.locallife.backend.activity.domain.Activity;
import com.locallife.backend.activity.infrastructure.ActivityRepository;
import com.locallife.backend.collector.domain.CollectedActivity;
import com.locallife.backend.collector.domain.Collector;
import com.locallife.backend.source.domain.Source;
import com.locallife.backend.source.infrastructure.SourceRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * LL-5011 : vérifie qu'une activité importée (LL-5008) utilise
 * correctement les fonctionnalités du Sprint 4 — pas de fonctionnalité
 * frontend spécifique aux collecteurs (explicitement exclue par
 * {@code SPRINT_5.md}), uniquement une vérification que le pipeline
 * d'import produit des {@code Activity} pleinement compatibles avec la
 * recherche géographique (LL-4002/LL-4003/LL-4006/LL-4007), les filtres
 * catégorie/date (LL-4004/LL-4005) et la consultation individuelle
 * (LL-1007), sans traitement particulier. Depuis LL-6004, ces deux
 * méthodes de recherche ne retournent que les activités {@code
 * PUBLISHED} — {@code status} n'est plus un paramètre qu'il faille
 * fournir (voir {@code importedActivity_ShouldAppearInPublicSearch_WithoutAnyStatusParameter}).
 *
 * Même approche que {@code ImportServiceIntegrationTest} (LL-5010) :
 * contexte Spring réel, base réelle, seul {@code Collector} mocké
 * ({@code @MockitoBean}). Passe par {@code ImportService} pour créer les
 * activités (pipeline réel), puis par {@code ActivityService} (les mêmes
 * méthodes que celles exposées par {@code ActivityController}) pour
 * vérifier qu'elles sont retrouvées — sans distinction entre activité
 * importée et activité manuelle, ce qui est précisément le critère
 * d'acceptation (« consultable comme une activité normale »).
 */
@SpringBootTest
@Transactional
class ImportedActivityVisibilityIntegrationTest {

    @Autowired
    private ImportService importService;

    @Autowired
    private ActivityService activityService;

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private SourceRepository sourceRepository;

    @MockitoBean
    private Collector collector;

    private static final double LATITUDE = 43.2965;
    private static final double LONGITUDE = 5.3698;

    private String uniqueSourceName() {
        return "Test Source " + UUID.randomUUID();
    }

    private Activity importOneActivity(String sourceName, String category, LocalDateTime startDate) {
        when(collector.getSourceName()).thenReturn(sourceName);
        when(collector.collect()).thenReturn(List.of(new CollectedActivity(
                "Marché de Noël", "Marché de Noël sur le Vieux-Port", startDate, null,
                category, LATITUDE, LONGITUDE, "https://example.com", "ext-1", sourceName)));
        importService.importAll();

        Long sourceId = sourceRepository.findByName(sourceName).map(Source::id).orElseThrow();
        return activityRepository.findBySourceId(sourceId).get(0);
    }

    @Test
    void importedActivity_ShouldAppearInNearbySearch() {
        // Given
        importOneActivity(uniqueSourceName(), "marché", LocalDateTime.now().plusDays(1));

        // When : recherche géographique (LL-4002/LL-4003), rayon 5 km autour du point exact.
        List<Activity> results = activityService.findNearby(
                String.valueOf(LATITUDE), String.valueOf(LONGITUDE), "5", null, null);

        // Then
        assertThat(results).anySatisfy(activity -> assertThat(activity.title()).isEqualTo("Marché de Noël"));
    }

    @Test
    void importedActivity_ShouldAppearInBoundingBoxSearch() {
        // Given
        importOneActivity(uniqueSourceName(), "marché", LocalDateTime.now().plusDays(1));

        // When : recherche par zone cartographique (LL-4006/LL-4007), zone englobant Marseille.
        List<Activity> results = activityService.findWithinBounds(
                "43.20", "5.30", "43.40", "5.50", null, null);

        // Then
        assertThat(results).anySatisfy(activity -> assertThat(activity.title()).isEqualTo("Marché de Noël"));
    }

    @Test
    void importedActivity_ShouldBeFilterableByCategory() {
        // Given
        importOneActivity(uniqueSourceName(), "marché", LocalDateTime.now().plusDays(1));

        // When / Then : filtre catégorie correspondant (LL-4004).
        List<Activity> matching = activityService.findNearby(
                String.valueOf(LATITUDE), String.valueOf(LONGITUDE), "5", "marché", null);
        assertThat(matching).anySatisfy(activity -> assertThat(activity.title()).isEqualTo("Marché de Noël"));

        // Filtre catégorie non correspondant : exclue, comme une activité manuelle le serait.
        List<Activity> nonMatching = activityService.findNearby(
                String.valueOf(LATITUDE), String.valueOf(LONGITUDE), "5", "sport", null);
        assertThat(nonMatching).noneMatch(activity -> activity.title().equals("Marché de Noël"));
    }

    @Test
    void importedActivity_ShouldBeFilterableByDate() {
        // Given
        LocalDateTime eventDate = LocalDateTime.of(2026, 12, 1, 10, 0);
        importOneActivity(uniqueSourceName(), "marché", eventDate);

        // When / Then : filtre date correspondant (LL-4005).
        List<Activity> matching = activityService.findNearby(
                String.valueOf(LATITUDE), String.valueOf(LONGITUDE), "5", null, "2026-12-01");
        assertThat(matching).anySatisfy(activity -> assertThat(activity.title()).isEqualTo("Marché de Noël"));

        // Date ne correspondant pas à la période de l'activité : exclue.
        List<Activity> nonMatching = activityService.findNearby(
                String.valueOf(LATITUDE), String.valueOf(LONGITUDE), "5", null, "2026-12-25");
        assertThat(nonMatching).noneMatch(activity -> activity.title().equals("Marché de Noël"));
    }

    @Test
    void importedActivity_ShouldAppearInPublicSearch_WithoutAnyStatusParameter() {
        // Given : les activités importées ont le statut PUBLISHED (décision LL-5005, validée par Alex).
        // Depuis LL-6004, findNearby/findWithinBounds ne prennent plus de paramètre status : ce test
        // vérifie qu'une activité importée (PUBLISHED) apparaît bien sans qu'il faille rien demander de
        // particulier — c'est précisément le seul statut que ces endpoints publics retournent désormais.
        importOneActivity(uniqueSourceName(), "marché", LocalDateTime.now().plusDays(1));

        // When / Then
        List<Activity> matching = activityService.findNearby(
                String.valueOf(LATITUDE), String.valueOf(LONGITUDE), "5", null, null);
        assertThat(matching).anySatisfy(activity -> assertThat(activity.title()).isEqualTo("Marché de Noël"));
    }

    @Test
    void importedActivity_ShouldBeConsultableById_LikeAnyOtherActivity() {
        // Given
        Activity imported = importOneActivity(uniqueSourceName(), "marché", LocalDateTime.now().plusDays(1));

        // When : consultation individuelle (LL-1007), même endpoint qu'une activité manuelle.
        Optional<Activity> found = activityService.findById(imported.id());

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().title()).isEqualTo("Marché de Noël");
        assertThat(found.get().description()).isEqualTo("Marché de Noël sur le Vieux-Port");
        assertThat(found.get().status()).isEqualTo("PUBLISHED");
        assertThat(found.get().sourceId()).isNotNull();
        assertThat(found.get().importKey()).isNotNull();
    }

}
