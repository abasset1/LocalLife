package com.locallife.backend.activity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.locallife.backend.activity.domain.Activity;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tests d'intégration PostGIS (LL-4002), filtre par statut (LL-4003),
 * filtre par catégorie (LL-4004) et filtre par date (LL-4005), contre la
 * base réelle (comme {@code UserRepositoryIntegrationTest}) : chaque test
 * est englobé dans une transaction annulée à la fin. Point de référence :
 * Vieux-Port de Marseille (43.2951, 5.3739).
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

    private Activity activityAt(
            double latitude, double longitude, String status, String category,
            LocalDateTime startDate, LocalDateTime endDate) {
        String uniqueTitle = "test-" + UUID.randomUUID();
        return activityRepository.save(new Activity(
                null, uniqueTitle, "description", category,
                latitude, longitude, startDate, endDate, status));
    }

    private Activity activityAt(double latitude, double longitude, String status, String category) {
        return activityAt(latitude, longitude, status, category, LocalDateTime.now(), null);
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
                .findWithinRadius(MARSEILLE_LAT, MARSEILLE_LON, 5_000, null, null, null)
                .stream().map(Activity::id).toList();

        assertThat(resultIds).contains(near.id());
        assertThat(resultIds).doesNotContain(far.id());
    }

    @Test
    void findWithinRadius_ShouldExcludeActivity_WhenOutsideRequestedRadius() {
        // ~5,5 km au sud du point de référence : hors du rayon de 1 km testé ci-dessous.
        Activity justOutside = activityAt(MARSEILLE_LAT - 0.05, MARSEILLE_LON);

        List<Long> resultIds = activityRepository
                .findWithinRadius(MARSEILLE_LAT, MARSEILLE_LON, 1_000, null, null, null)
                .stream().map(Activity::id).toList();

        assertThat(resultIds).doesNotContain(justOutside.id());
    }

    @Test
    void findWithinRadius_ShouldOrderResultsByDistanceAscending() {
        Activity closer = activityAt(MARSEILLE_LAT + 0.001, MARSEILLE_LON);
        Activity farther = activityAt(MARSEILLE_LAT + 0.02, MARSEILLE_LON);

        List<Long> resultIds = activityRepository
                .findWithinRadius(MARSEILLE_LAT, MARSEILLE_LON, 5_000, null, null, null)
                .stream().map(Activity::id).toList();

        assertThat(resultIds).contains(closer.id(), farther.id());
        assertThat(resultIds.indexOf(closer.id())).isLessThan(resultIds.indexOf(farther.id()));
    }

    @Test
    void findWithinRadius_ShouldOnlyReturnMatchingStatus_WhenStatusProvided() {
        Activity published = activityAt(MARSEILLE_LAT + 0.001, MARSEILLE_LON, "PUBLISHED");
        Activity pending = activityAt(MARSEILLE_LAT + 0.002, MARSEILLE_LON, "PENDING");

        List<Long> resultIds = activityRepository
                .findWithinRadius(MARSEILLE_LAT, MARSEILLE_LON, 5_000, "PUBLISHED", null, null)
                .stream().map(Activity::id).toList();

        assertThat(resultIds).contains(published.id());
        assertThat(resultIds).doesNotContain(pending.id());
    }

    @Test
    void findWithinRadius_ShouldReturnAllStatuses_WhenStatusNotProvided() {
        Activity published = activityAt(MARSEILLE_LAT + 0.001, MARSEILLE_LON, "PUBLISHED");
        Activity pending = activityAt(MARSEILLE_LAT + 0.002, MARSEILLE_LON, "PENDING");

        List<Long> resultIds = activityRepository
                .findWithinRadius(MARSEILLE_LAT, MARSEILLE_LON, 5_000, null, null, null)
                .stream().map(Activity::id).toList();

        assertThat(resultIds).contains(published.id(), pending.id());
    }

    @Test
    void findWithinRadius_ShouldOnlyReturnMatchingCategory_WhenSingleCategoryProvided() {
        Activity concert = activityAt(MARSEILLE_LAT + 0.001, MARSEILLE_LON, "PUBLISHED", "concert");
        Activity sport = activityAt(MARSEILLE_LAT + 0.002, MARSEILLE_LON, "PUBLISHED", "sport");

        List<Long> resultIds = activityRepository
                .findWithinRadius(MARSEILLE_LAT, MARSEILLE_LON, 5_000, null, "concert", null)
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
                .findWithinRadius(MARSEILLE_LAT, MARSEILLE_LON, 5_000, null, "concert,marché", null)
                .stream().map(Activity::id).toList();

        assertThat(resultIds).contains(concert.id(), marche.id());
        assertThat(resultIds).doesNotContain(sport.id());
    }

    @Test
    void findWithinRadius_ShouldReturnEmpty_WhenCategoryDoesNotMatchAnyActivity() {
        activityAt(MARSEILLE_LAT + 0.001, MARSEILLE_LON, "PUBLISHED", "concert");

        List<Long> resultIds = activityRepository
                .findWithinRadius(MARSEILLE_LAT, MARSEILLE_LON, 5_000, null, "catégorie-inexistante-xyz", null)
                .stream().map(Activity::id).toList();

        assertThat(resultIds).isEmpty();
    }

    @Test
    void findWithinRadius_ShouldReturnAllCategories_WhenCategoryNotProvided() {
        Activity concert = activityAt(MARSEILLE_LAT + 0.001, MARSEILLE_LON, "PUBLISHED", "concert");
        Activity sport = activityAt(MARSEILLE_LAT + 0.002, MARSEILLE_LON, "PUBLISHED", "sport");

        List<Long> resultIds = activityRepository
                .findWithinRadius(MARSEILLE_LAT, MARSEILLE_LON, 5_000, null, null, null)
                .stream().map(Activity::id).toList();

        assertThat(resultIds).contains(concert.id(), sport.id());
    }

    @Test
    void findWithinRadius_ShouldIncludeOneDayActivity_WhenDateMatchesExactly() {
        // Activité d'une seule journée (2026-09-05, 20h-23h, comme les données de démo).
        Activity oneDayActivity = activityAt(
                MARSEILLE_LAT + 0.001, MARSEILLE_LON, "PUBLISHED", "concert",
                LocalDateTime.of(2026, 9, 5, 20, 0), LocalDateTime.of(2026, 9, 5, 23, 0));

        List<Long> resultIds = activityRepository
                .findWithinRadius(MARSEILLE_LAT, MARSEILLE_LON, 5_000, null, null, LocalDate.of(2026, 9, 5))
                .stream().map(Activity::id).toList();

        assertThat(resultIds).contains(oneDayActivity.id());
    }

    @Test
    void findWithinRadius_ShouldExcludeOneDayActivity_WhenDateDoesNotMatch() {
        Activity oneDayActivity = activityAt(
                MARSEILLE_LAT + 0.001, MARSEILLE_LON, "PUBLISHED", "concert",
                LocalDateTime.of(2026, 9, 5, 20, 0), LocalDateTime.of(2026, 9, 5, 23, 0));

        List<Long> resultIds = activityRepository
                .findWithinRadius(MARSEILLE_LAT, MARSEILLE_LON, 5_000, null, null, LocalDate.of(2026, 9, 6))
                .stream().map(Activity::id).toList();

        assertThat(resultIds).doesNotContain(oneDayActivity.id());
    }

    @Test
    void findWithinRadius_ShouldIncludeMultiDayActivity_ForEveryDateWithinItsPeriod() {
        // Activité sur plusieurs jours, comme l'exposition de démo (2026-09-01 au 2026-10-15).
        Activity multiDayActivity = activityAt(
                MARSEILLE_LAT + 0.001, MARSEILLE_LON, "PUBLISHED", "exposition",
                LocalDateTime.of(2026, 9, 1, 10, 0), LocalDateTime.of(2026, 9, 10, 18, 0));

        List<Long> firstDay = activityRepository
                .findWithinRadius(MARSEILLE_LAT, MARSEILLE_LON, 5_000, null, null, LocalDate.of(2026, 9, 1))
                .stream().map(Activity::id).toList();
        List<Long> middleDay = activityRepository
                .findWithinRadius(MARSEILLE_LAT, MARSEILLE_LON, 5_000, null, null, LocalDate.of(2026, 9, 5))
                .stream().map(Activity::id).toList();
        List<Long> lastDay = activityRepository
                .findWithinRadius(MARSEILLE_LAT, MARSEILLE_LON, 5_000, null, null, LocalDate.of(2026, 9, 10))
                .stream().map(Activity::id).toList();
        List<Long> dayAfter = activityRepository
                .findWithinRadius(MARSEILLE_LAT, MARSEILLE_LON, 5_000, null, null, LocalDate.of(2026, 9, 11))
                .stream().map(Activity::id).toList();

        assertThat(firstDay).contains(multiDayActivity.id());
        assertThat(middleDay).contains(multiDayActivity.id());
        assertThat(lastDay).contains(multiDayActivity.id());
        assertThat(dayAfter).doesNotContain(multiDayActivity.id());
    }

    @Test
    void findWithinRadius_ShouldTreatMissingEndDate_AsSameDayAsStartDate() {
        // endDate absente (NULL en base) : cas des activités créées via le formulaire de
        // contribution (voir ActivityService#createActivity), qui ne renseigne pas de date de fin.
        Activity noEndDate = activityAt(
                MARSEILLE_LAT + 0.001, MARSEILLE_LON, "PENDING", "loisir",
                LocalDateTime.of(2026, 9, 5, 14, 0), null);

        List<Long> sameDay = activityRepository
                .findWithinRadius(MARSEILLE_LAT, MARSEILLE_LON, 5_000, null, null, LocalDate.of(2026, 9, 5))
                .stream().map(Activity::id).toList();
        List<Long> nextDay = activityRepository
                .findWithinRadius(MARSEILLE_LAT, MARSEILLE_LON, 5_000, null, null, LocalDate.of(2026, 9, 6))
                .stream().map(Activity::id).toList();

        assertThat(sameDay).contains(noEndDate.id());
        assertThat(nextDay).doesNotContain(noEndDate.id());
    }

    @Test
    void findWithinRadius_ShouldReturnAllDates_WhenDateNotProvided() {
        Activity anyDate = activityAt(
                MARSEILLE_LAT + 0.001, MARSEILLE_LON, "PUBLISHED", "concert",
                LocalDateTime.of(2026, 9, 5, 20, 0), LocalDateTime.of(2026, 9, 5, 23, 0));

        List<Long> resultIds = activityRepository
                .findWithinRadius(MARSEILLE_LAT, MARSEILLE_LON, 5_000, null, null, null)
                .stream().map(Activity::id).toList();

        assertThat(resultIds).contains(anyDate.id());
    }

}
