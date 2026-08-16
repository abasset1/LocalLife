package com.locallife.backend.activity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.locallife.backend.activity.domain.Activity;
import com.locallife.backend.source.domain.Source;
import com.locallife.backend.source.infrastructure.SourceRepository;
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
 * filtre par catégorie (LL-4004), filtre par date (LL-4005), recherche
 * par zone rectangulaire (LL-4006/LL-4007), et combinaison de plusieurs
 * filtres appliqués simultanément (LL-4014), contre la base réelle
 * (comme {@code UserRepositoryIntegrationTest}) : chaque test est
 * englobé dans une transaction annulée à la fin. Point de référence :
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

    @Autowired
    private SourceRepository sourceRepository;

    /**
     * Toute {@code Activity} requiert désormais un {@code sourceId}
     * (LL-5008) : les activités créées par ces tests sont rattachées à la
     * source réservée {@code MANUAL} (garantie présente par
     * {@code V8__create_source_table.sql}), ce qui reflète fidèlement des
     * activités qui n'ont rien à voir avec un import.
     */
    private Long manualSourceId() {
        return sourceRepository.findByType("MANUAL")
                .map(Source::id)
                .orElseThrow(() -> new IllegalStateException("Source MANUAL introuvable — migration V8 manquante ?"));
    }

    private Activity activityAt(
            double latitude, double longitude, String status, String category,
            LocalDateTime startDate, LocalDateTime endDate) {
        String uniqueTitle = "test-" + UUID.randomUUID();
        return activityRepository.save(new Activity(
                null, uniqueTitle, "description", category,
                latitude, longitude, startDate, endDate, status, manualSourceId(), null, null));
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

    // --- findWithinBounds (LL-4006/LL-4007) ---
    // Zone de test : rectangle autour de Marseille (43.20/5.30 → 43.35/5.45), couvrant
    // largement MARSEILLE_LAT/MARSEILLE_LON utilisé ci-dessus.

    private static final double BOUNDS_SW_LAT = 43.20;
    private static final double BOUNDS_SW_LON = 5.30;
    private static final double BOUNDS_NE_LAT = 43.35;
    private static final double BOUNDS_NE_LON = 5.45;

    @Test
    void findWithinBounds_ShouldIncludeActivityInsideBounds_AndExcludeActivityOutsideBounds() {
        Activity inside = activityAt(MARSEILLE_LAT, MARSEILLE_LON);
        Activity outside = activityAt(PARIS_LAT, PARIS_LON);

        List<Long> resultIds = activityRepository
                .findWithinBounds(BOUNDS_SW_LAT, BOUNDS_SW_LON, BOUNDS_NE_LAT, BOUNDS_NE_LON, null, null, null)
                .stream().map(Activity::id).toList();

        assertThat(resultIds).contains(inside.id());
        assertThat(resultIds).doesNotContain(outside.id());
    }

    @Test
    void findWithinBounds_ShouldExcludeActivity_JustOutsideBoundsToTheNorth() {
        // Juste au-delà de la borne nord (neLatitude = 43.35).
        Activity justOutside = activityAt(BOUNDS_NE_LAT + 0.01, MARSEILLE_LON);

        List<Long> resultIds = activityRepository
                .findWithinBounds(BOUNDS_SW_LAT, BOUNDS_SW_LON, BOUNDS_NE_LAT, BOUNDS_NE_LON, null, null, null)
                .stream().map(Activity::id).toList();

        assertThat(resultIds).doesNotContain(justOutside.id());
    }

    @Test
    void findWithinBounds_ShouldOnlyReturnMatchingStatus_WhenStatusProvided() {
        Activity published = activityAt(MARSEILLE_LAT, MARSEILLE_LON, "PUBLISHED");
        Activity pending = activityAt(MARSEILLE_LAT + 0.001, MARSEILLE_LON, "PENDING");

        List<Long> resultIds = activityRepository
                .findWithinBounds(
                        BOUNDS_SW_LAT, BOUNDS_SW_LON, BOUNDS_NE_LAT, BOUNDS_NE_LON, "PUBLISHED", null, null)
                .stream().map(Activity::id).toList();

        assertThat(resultIds).contains(published.id());
        assertThat(resultIds).doesNotContain(pending.id());
    }

    @Test
    void findWithinBounds_ShouldOnlyReturnMatchingCategory_WhenCategoryProvided() {
        Activity concert = activityAt(MARSEILLE_LAT, MARSEILLE_LON, "PUBLISHED", "concert");
        Activity sport = activityAt(MARSEILLE_LAT + 0.001, MARSEILLE_LON, "PUBLISHED", "sport");

        List<Long> resultIds = activityRepository
                .findWithinBounds(
                        BOUNDS_SW_LAT, BOUNDS_SW_LON, BOUNDS_NE_LAT, BOUNDS_NE_LON, null, "concert", null)
                .stream().map(Activity::id).toList();

        assertThat(resultIds).contains(concert.id());
        assertThat(resultIds).doesNotContain(sport.id());
    }

    @Test
    void findWithinBounds_ShouldOnlyReturnMatchingDate_WhenDateProvided() {
        Activity onDate = activityAt(
                MARSEILLE_LAT, MARSEILLE_LON, "PUBLISHED", "concert",
                LocalDateTime.of(2026, 9, 5, 20, 0), LocalDateTime.of(2026, 9, 5, 23, 0));
        Activity otherDate = activityAt(
                MARSEILLE_LAT + 0.001, MARSEILLE_LON, "PUBLISHED", "concert",
                LocalDateTime.of(2026, 9, 6, 20, 0), LocalDateTime.of(2026, 9, 6, 23, 0));

        List<Long> resultIds = activityRepository
                .findWithinBounds(
                        BOUNDS_SW_LAT, BOUNDS_SW_LON, BOUNDS_NE_LAT, BOUNDS_NE_LON,
                        null, null, LocalDate.of(2026, 9, 5))
                .stream().map(Activity::id).toList();

        assertThat(resultIds).contains(onDate.id());
        assertThat(resultIds).doesNotContain(otherDate.id());
    }

    @Test
    void findWithinBounds_ShouldOrderResultsByIdAscending_NotByDistance() {
        // farFromCenter est plus proche du bord sud-ouest de la zone, mais son id est
        // supérieur à closeToCenter (créé après) : le tri attendu est par id, pas par distance.
        Activity closeToCenter = activityAt(MARSEILLE_LAT, MARSEILLE_LON);
        Activity farFromCenter = activityAt(BOUNDS_SW_LAT + 0.01, BOUNDS_SW_LON + 0.01);

        List<Long> resultIds = activityRepository
                .findWithinBounds(BOUNDS_SW_LAT, BOUNDS_SW_LON, BOUNDS_NE_LAT, BOUNDS_NE_LON, null, null, null)
                .stream().map(Activity::id).toList();

        assertThat(resultIds).contains(closeToCenter.id(), farFromCenter.id());
        assertThat(resultIds.indexOf(closeToCenter.id())).isLessThan(resultIds.indexOf(farFromCenter.id()));
    }

    // --- Combinaison de filtres (LL-4014) ---
    // Les tests ci-dessus couvrent chaque filtre isolément (les autres à null). Ceux qui
    // suivent vérifient la sémantique « ET » entre plusieurs filtres appliqués en même
    // temps : seule une activité correspondant à TOUS les critères doit être retenue,
    // pas une activité qui n'en satisferait qu'un seul.

    @Test
    void findWithinRadius_ShouldCombineStatusCategoryAndDateFilters_WithAndSemantics() {
        LocalDateTime targetDate = LocalDateTime.of(2026, 9, 5, 20, 0);

        // Correspond aux trois critères à la fois : doit être le seul résultat.
        Activity matchesAll = activityAt(
                MARSEILLE_LAT + 0.001, MARSEILLE_LON, "PUBLISHED", "concert", targetDate, targetDate);
        // Ne correspond pas au statut demandé (sinon identique à matchesAll).
        Activity wrongStatus = activityAt(
                MARSEILLE_LAT + 0.002, MARSEILLE_LON, "PENDING", "concert", targetDate, targetDate);
        // Ne correspond pas à la catégorie demandée (sinon identique à matchesAll).
        Activity wrongCategory = activityAt(
                MARSEILLE_LAT + 0.003, MARSEILLE_LON, "PUBLISHED", "sport", targetDate, targetDate);
        // Ne correspond pas à la date demandée (sinon identique à matchesAll).
        Activity wrongDate = activityAt(
                MARSEILLE_LAT + 0.004, MARSEILLE_LON, "PUBLISHED", "concert",
                targetDate.plusDays(1), targetDate.plusDays(1));

        List<Long> resultIds = activityRepository
                .findWithinRadius(
                        MARSEILLE_LAT, MARSEILLE_LON, 5_000, "PUBLISHED", "concert", LocalDate.of(2026, 9, 5))
                .stream().map(Activity::id).toList();

        assertThat(resultIds).contains(matchesAll.id());
        assertThat(resultIds).doesNotContain(wrongStatus.id(), wrongCategory.id(), wrongDate.id());
    }

    @Test
    void findWithinBounds_ShouldCombineStatusCategoryAndDateFilters_WithAndSemantics() {
        LocalDateTime targetDate = LocalDateTime.of(2026, 9, 5, 20, 0);

        Activity matchesAll = activityAt(
                MARSEILLE_LAT, MARSEILLE_LON, "PUBLISHED", "concert", targetDate, targetDate);
        Activity wrongStatus = activityAt(
                MARSEILLE_LAT + 0.001, MARSEILLE_LON, "PENDING", "concert", targetDate, targetDate);
        Activity wrongCategory = activityAt(
                MARSEILLE_LAT + 0.002, MARSEILLE_LON, "PUBLISHED", "sport", targetDate, targetDate);
        Activity wrongDate = activityAt(
                MARSEILLE_LAT + 0.003, MARSEILLE_LON, "PUBLISHED", "concert",
                targetDate.plusDays(1), targetDate.plusDays(1));
        // Correspond aux trois autres critères mais hors de la zone rectangulaire.
        Activity outsideBounds = activityAt(
                PARIS_LAT, PARIS_LON, "PUBLISHED", "concert", targetDate, targetDate);

        List<Long> resultIds = activityRepository
                .findWithinBounds(
                        BOUNDS_SW_LAT, BOUNDS_SW_LON, BOUNDS_NE_LAT, BOUNDS_NE_LON,
                        "PUBLISHED", "concert", LocalDate.of(2026, 9, 5))
                .stream().map(Activity::id).toList();

        assertThat(resultIds).contains(matchesAll.id());
        assertThat(resultIds)
                .doesNotContain(wrongStatus.id(), wrongCategory.id(), wrongDate.id(), outsideBounds.id());
    }

}
