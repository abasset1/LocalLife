package com.locallife.backend.activity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.locallife.backend.activity.domain.Activity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tests d'intégration PostGIS (LL-4002), filtre par statut (LL-4003) et
 * filtre par catégorie (LL-4004), contre la base réelle (comme
 * {@code UserRepositoryIntegrationTest}) : chaque test est englobé dans une
 * transaction annulée à la fin. Point de référence : Vieux-Port de
 * Marseille (43.2951, 5.3739).
 *
 * Assertions faites par id, jamais par vacuité/taille du résultat : les
 * données de démo (V3__insert_demo_activities.sql) sont déjà toutes situées
 * autour de Marseille et apparaîtront donc systématiquement dans les
 * résultats d'une recherche centrée sur Marseille.
 */
@SpringBootTest
@Transactional
class ActivityRepositoryIntegrationTest {

    private static final double MARSEILLE_LAT = 43.2951;
    private static final double MARSEILLE_LON = 5.3739;
    private static final double PARIS_LAT = 48.8566;
    private static final double PARIS_LON = 2.3522;

    @Autowired
    private ActivityRepository activityRepository;

    private Activity activityAt(double latitude, double longitude, String status, String category) {
        String uniqueTitle = "test-" + UUID.randomUUID();
        return activityRepository.save(new Activity(
                null, uniqueTitle, "description", category,
                latitude, longitude, LocalDateTime.now(), null, status));
    }

    private Activity activityAt(double latitude, double longitude, String status) {
        return activityAt(latitude, longitude, status, "sport");
    }

    private Activity activityAt(double latitude, double longitude) {
        return activityAt(latitude, longitude, "PUBLISHED", "sport");
    }

    @Test
    void findWithinRadius_ShouldIncludeNearActivity_AndExcludeFarActivity() {
        // Activité proche : ~1,1 km du point de référence (0,01° de latitude).
        Activity near = activityAt(MARSEILLE_LAT + 0.01, MARSEILLE_LON);
        // Activité hors rayon : Paris, ~660 km de Marseille.
        Activity far = activityAt(PARIS_LAT, PARIS_LON);

        List<Long> resultIds = activityRepository
                .findWithinRadius(MARSEILLE_LAT, MARSEILLE_LON, 5_000, null, null)
                .stream().map(Activity::id).toList();

        assertThat(resultIds).contains(near.id());
        assertThat(resultIds).doesNotContain(far.id());
    }

    @Test
    void findWithinRadius_ShouldExcludeActivity_WhenOutsideRequestedRadius() {
        // ~5,5 km au sud du point de référence : hors du rayon de 1 km testé ci-dessous.
        Activity justOutside = activityAt(MARSEILLE_LAT - 0.05, MARSEILLE_LON);

        List<Long> resultIds = activityRepository
                .findWithinRadius(MARSEILLE_LAT, MARSEILLE_LON, 1_000, null, null)
                .stream().map(Activity::id).toList();

        assertThat(resultIds).doesNotContain(justOutside.id());
    }

    @Test
    void findWithinRadius_ShouldOrderResultsByDistanceAscending() {
        Activity closer = activityAt(MARSEILLE_LAT + 0.001, MARSEILLE_LON);
        Activity farther = activityAt(MARSEILLE_LAT + 0.02, MARSEILLE_LON);

        List<Long> resultIds = activityRepository
                .findWithinRadius(MARSEILLE_LAT, MARSEILLE_LON, 5_000, null, null)
                .stream().map(Activity::id).toList();

        assertThat(resultIds).contains(closer.id(), farther.id());
        assertThat(resultIds.indexOf(closer.id())).isLessThan(resultIds.indexOf(farther.id()));
    }

    @Test
    void findWithinRadius_ShouldOnlyReturnMatchingStatus_WhenStatusProvided() {
        Activity published = activityAt(MARSEILLE_LAT + 0.001, MARSEILLE_LON, "PUBLISHED");
        Activity pending = activityAt(MARSEILLE_LAT + 0.002, MARSEILLE_LON, "PENDING");

        List<Long> resultIds = activityRepository
                .findWithinRadius(MARSEILLE_LAT, MARSEILLE_LON, 5_000, "PUBLISHED", null)
                .stream().map(Activity::id).toList();

        assertThat(resultIds).contains(published.id());
        assertThat(resultIds).doesNotContain(pending.id());
    }

    @Test
    void findWithinRadius_ShouldReturnAllStatuses_WhenStatusNotProvided() {
        Activity published = activityAt(MARSEILLE_LAT + 0.001, MARSEILLE_LON, "PUBLISHED");
        Activity pending = activityAt(MARSEILLE_LAT + 0.002, MARSEILLE_LON, "PENDING");

        List<Long> resultIds = activityRepository
                .findWithinRadius(MARSEILLE_LAT, MARSEILLE_LON, 5_000, null, null)
                .stream().map(Activity::id).toList();

        assertThat(resultIds).contains(published.id(), pending.id());
    }

    @Test
    void findWithinRadius_ShouldOnlyReturnMatchingCategory_WhenSingleCategoryProvided() {
        Activity concert = activityAt(MARSEILLE_LAT + 0.001, MARSEILLE_LON, "PUBLISHED", "concert");
        Activity sport = activityAt(MARSEILLE_LAT + 0.002, MARSEILLE_LON, "PUBLISHED", "sport");

        List<Long> resultIds = activityRepository
                .findWithinRadius(MARSEILLE_LAT, MARSEILLE_LON, 5_000, null, "concert")
                .stream().map(Activity::id).toList();

        assertThat(resultIds).contains(concert.id());
        assertThat(resultIds).doesNotContain(sport.id());
    }

    @Test
    void findWithinRadius_ShouldReturnAnyMatchingCategory_WhenMultipleCategoriesProvided() {
        Activity concert = activityAt(MARSEILLE_LAT + 0.001, MARSEILLE_LON, "PUBLISHED", "concert");
        Activity marche = activityAt(MARSEILLE_LAT + 0.002, MARSEILLE_LON, "PUBLISHED", "marché");
        Activity sport = activityAt(MARSEILLE_LAT + 0.003, MARSEILLE_LON, "PUBLISHED", "sport");

        List<Long> resultIds = activityRepository
                .findWithinRadius(MARSEILLE_LAT, MARSEILLE_LON, 5_000, null, "concert,marché")
                .stream().map(Activity::id).toList();

        assertThat(resultIds).contains(concert.id(), marche.id());
        assertThat(resultIds).doesNotContain(sport.id());
    }

    @Test
    void findWithinRadius_ShouldReturnEmpty_WhenCategoryDoesNotMatchAnyActivity() {
        activityAt(MARSEILLE_LAT + 0.001, MARSEILLE_LON, "PUBLISHED", "concert");

        List<Long> resultIds = activityRepository
                .findWithinRadius(MARSEILLE_LAT, MARSEILLE_LON, 5_000, null, "catégorie-inexistante-xyz")
                .stream().map(Activity::id).toList();

        assertThat(resultIds).isEmpty();
    }

    @Test
    void findWithinRadius_ShouldReturnAllCategories_WhenCategoryNotProvided() {
        Activity concert = activityAt(MARSEILLE_LAT + 0.001, MARSEILLE_LON, "PUBLISHED", "concert");
        Activity sport = activityAt(MARSEILLE_LAT + 0.002, MARSEILLE_LON, "PUBLISHED", "sport");

        List<Long> resultIds = activityRepository
                .findWithinRadius(MARSEILLE_LAT, MARSEILLE_LON, 5_000, null, null)
                .stream().map(Activity::id).toList();

        assertThat(resultIds).contains(concert.id(), sport.id());
    }
    @Test
    void findWithinRadius_ShouldExcludeActivity_WhenCoordinatesAreNull() {
        Activity withoutCoordinates = activityRepository.save(new Activity(
                null, "test-null-" + UUID.randomUUID(), "description", "sport",
                null, null, LocalDateTime.now(), null, "PUBLISHED"));

        List<Long> resultIds = activityRepository
                .findWithinRadius(MARSEILLE_LAT, MARSEILLE_LON, 5_000)
                .stream().map(Activity::id).toList();
        assertThat(resultIds).doesNotContain(withoutCoordinates.id());
    }

    @Test
    void findWithinRadius_ShouldUpdateLocation_WhenCoordinatesAreUpdated() {
        Activity activity = activityAt(MARSEILLE_LAT, MARSEILLE_LON);

        Activity moved = new Activity(
                activity.id(), activity.title(), activity.description(), activity.category(),
                PARIS_LAT, PARIS_LON, activity.startDate(), activity.endDate(), activity.status());
        activityRepository.save(moved);

        List<Long> nearMarseille = activityRepository
                .findWithinRadius(MARSEILLE_LAT, MARSEILLE_LON, 5_000)
                .stream().map(Activity::id).toList();
        List<Long> nearParis = activityRepository
                .findWithinRadius(PARIS_LAT, PARIS_LON, 5_000)
                .stream().map(Activity::id).toList();

        assertThat(nearMarseille).doesNotContain(activity.id());
        assertThat(nearParis).contains(activity.id());
    }

}
