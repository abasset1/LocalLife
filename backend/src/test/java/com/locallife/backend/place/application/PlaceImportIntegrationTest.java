package com.locallife.backend.place.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.locallife.backend.collector.domain.CollectedPlace;
import com.locallife.backend.collector.infrastructure.CollectorException;
import com.locallife.backend.collector.infrastructure.DataTourismeCollectorFactory;
import com.locallife.backend.collector.infrastructure.DataTourismePlaceCollector;
import com.locallife.backend.place.application.PlaceImportReport.Verdict;
import com.locallife.backend.place.domain.Place;
import com.locallife.backend.place.domain.PlaceFlag;
import com.locallife.backend.place.domain.PlaceStatus;
import com.locallife.backend.place.infrastructure.PlaceRepository;
import com.locallife.backend.place.infrastructure.PlaceRepository.ModerationItem;
import com.locallife.backend.place.infrastructure.PlaceRepository.ModerationQuery;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Import et modération des lieux permanents (ADR-0011, phase B) sur PostgreSQL réel : collecteur simulé, chaque
 * test dans une transaction annulée.
 */
@SpringBootTest
class PlaceImportIntegrationTest {

    private static final Set<String> MUSEUM = Set.of("PointOfInterest", "PlaceOfInterest", "CulturalSite", "Museum");
    private static final Set<String> CHURCH =
            Set.of("PointOfInterest", "PlaceOfInterest", "CulturalSite", "ReligiousSite", "Church");
    private static final Set<String> RESTAURANT =
            Set.of("PointOfInterest", "PlaceOfInterest", "FoodEstablishment", "Restaurant");

    @MockitoBean
    private DataTourismeCollectorFactory collectorFactory;

    @Autowired
    private PlaceImportService importService;

    @Autowired
    private PlaceRepository placeRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private final DataTourismePlaceCollector collector = mock(DataTourismePlaceCollector.class);

    private Long sourceId;

    @BeforeEach
    void setUp() {
        when(collectorFactory.createPlaceCollector(any())).thenReturn(collector);
        sourceId = jdbcTemplate.queryForObject(
                "SELECT id FROM source WHERE type = 'DATATOURISME_LIEUX' ORDER BY id LIMIT 1", Long.class);
    }

    private void inRolledBackTransaction(Consumer<Void> body) {
        transactionTemplate.executeWithoutResult(status -> {
            body.accept(null);
            status.setRollbackOnly();
        });
    }

    private static CollectedPlace place(String id, String name, Set<String> types, double latitude, String address) {
        return new CollectedPlace("https://data.datatourisme.fr/test/" + id, name, types, address, "84000",
                "Avignon", latitude, 4.80, "Description", "https://example.org/" + id);
    }

    private PlaceImportReport importOnce(List<CollectedPlace> places) {
        when(collector.collect()).thenReturn(places);
        return importService.run(sourceId, false).orElseThrow();
    }

    private PlaceStatus statusOf(String id) {
        return PlaceStatus.valueOf(jdbcTemplate.queryForObject("""
                SELECT l.place_status FROM location l JOIN location_source ls ON ls.location_id = l.id
                WHERE ls.external_id = ?""", String.class, "https://data.datatourisme.fr/test/" + id));
    }

    private Long locationIdOf(String id) {
        return jdbcTemplate.queryForObject("SELECT location_id FROM location_source WHERE external_id = ?",
                Long.class, "https://data.datatourisme.fr/test/" + id);
    }

    @Test
    void firstImport_ShouldCreateWhitelistedPlacesOnly_AllPending() {
        inRolledBackTransaction(ignored -> {
            PlaceImportReport report = importOnce(List.of(
                    place("m", "Musée Calvet", MUSEUM, 43.944, "65 rue Joseph Vernet"),
                    place("c", "Église Saint-Pierre", CHURCH, 43.949, "Place Saint-Pierre"),
                    place("r", "Le Bistrot", RESTAURANT, 43.950, "1 rue X")));

            assertThat(report.verdict()).isEqualTo(Verdict.OK);
            assertThat(report.collected()).isEqualTo(3);
            assertThat(report.whitelisted()).isEqualTo(2);
            assertThat(report.created()).isEqualTo(2);
            assertThat(statusOf("m")).isEqualTo(PlaceStatus.PENDING);
            assertThat(statusOf("c")).isEqualTo(PlaceStatus.PENDING);
            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM location_source WHERE external_id = ?",
                    Integer.class, "https://data.datatourisme.fr/test/r")).isZero();
            assertThat(jdbcTemplate.queryForObject("SELECT place_type FROM location WHERE id = ?", String.class,
                    locationIdOf("c"))).isEqualTo("Church");

            PlaceImportReport again = importOnce(List.of(
                    place("m", "Musée Calvet", MUSEUM, 43.944, "65 rue Joseph Vernet"),
                    place("c", "Église Saint-Pierre", CHURCH, 43.949, "Place Saint-Pierre")));
            assertThat(again.created()).isZero();
            assertThat(again.updated()).isZero();
            assertThat(again.archived()).isZero();
        });
    }

    @Test
    void reimport_ShouldKeepRejectedPlaces_AndSendMovedPublishedPlacesBackToModeration() {
        inRolledBackTransaction(ignored -> {
            importOnce(List.of(
                    place("m", "Musée Calvet", MUSEUM, 43.944, "65 rue Joseph Vernet"),
                    place("c", "Église Saint-Pierre", CHURCH, 43.949, "Place Saint-Pierre")));
            assertThat(placeRepository.moderate(List.of(locationIdOf("m")), PlaceStatus.PUBLISHED, null)).hasSize(1);
            assertThat(placeRepository.moderate(List.of(locationIdOf("c")), PlaceStatus.REJECTED, null)).hasSize(1);

            // Le musée bouge d'environ 550 m ; l'église change de description.
            PlaceImportReport report = importOnce(List.of(
                    place("m", "Musée Calvet", MUSEUM, 43.949, "65 rue Joseph Vernet"),
                    new CollectedPlace("https://data.datatourisme.fr/test/c", "Église Saint-Pierre", CHURCH,
                            "Place Saint-Pierre", "84000", "Avignon", 43.949, 4.80, "Nouvelle description", null)));

            assertThat(statusOf("m")).isEqualTo(PlaceStatus.PENDING);
            assertThat(statusOf("c")).isEqualTo(PlaceStatus.REJECTED);
            assertThat(report.backToPending()).isEqualTo(1);
            assertThat(report.rejectedKept()).isEqualTo(1);
        });
    }

    @Test
    void disappearance_ShouldArchive_UnlessMassive() {
        inRolledBackTransaction(ignored -> {
            List<CollectedPlace> twelve = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                twelve.add(place("p" + i, "Musée " + i, MUSEUM, 43.9 + i / 1000.0, "Rue " + i));
            }
            importOnce(twelve);

            // 11 lieux sur 12 disparaissent d'un coup : suspendu.
            PlaceImportReport massive = importOnce(twelve.subList(0, 1));
            assertThat(massive.verdict()).isEqualTo(Verdict.SUSPICIOUS);
            assertThat(massive.archiveWithheld()).isEqualTo(11);
            assertThat(massive.archived()).isZero();
            assertThat(statusOf("p5")).isEqualTo(PlaceStatus.PENDING);

            // 1 lieu sur 12 disparaît : archivé.
            PlaceImportReport normal = importOnce(twelve.subList(0, 11));
            assertThat(normal.archived()).isEqualTo(1);
            assertThat(statusOf("p11")).isEqualTo(PlaceStatus.ARCHIVED);

            // Il réapparaît : de nouveau en attente.
            PlaceImportReport back = importOnce(twelve);
            assertThat(back.reappeared()).isEqualTo(1);
            assertThat(statusOf("p11")).isEqualTo(PlaceStatus.PENDING);
        });
    }

    @Test
    void failedCollection_AndDryRun_ShouldWriteNothing() {
        inRolledBackTransaction(ignored -> {
            Integer before = jdbcTemplate.queryForObject("SELECT count(*) FROM location", Integer.class);

            when(collector.collect()).thenThrow(new CollectorException("Fichier DATAtourisme vide", null));
            PlaceImportReport failed = importService.run(sourceId, false).orElseThrow();
            assertThat(failed.verdict()).isEqualTo(Verdict.FAILED);
            assertThat(failed.problems().get(0)).contains("Fichier DATAtourisme vide");

            doReturn(List.of(place("m", "Musée Calvet", MUSEUM, 43.944, "Rue"))).when(collector).collect();
            PlaceImportReport dryRun = importService.run(sourceId, true).orElseThrow();
            assertThat(dryRun.dryRun()).isTrue();
            assertThat(dryRun.created()).isEqualTo(1);

            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM location", Integer.class)).isEqualTo(before);
        });
    }

    @Test
    void moderation_ShouldFlagPlaces_AndPublicReadShouldOnlySeePublished() {
        inRolledBackTransaction(ignored -> {
            importOnce(List.of(
                    place("m", "Musée Calvet", MUSEUM, 43.944, "65 rue Joseph Vernet"),
                    place("a", "Circuit piéton du centre", CHURCH, 43.945, null),
                    place("d1", "Chapelle Sainte-Anne", CHURCH, 43.946, "Rue A"),
                    place("d2", "Chapelle Sainte-Anne", CHURCH, 43.947, "Rue B")));

            long[] total = {0};
            List<ModerationItem> flagged = placeRepository.findForModeration(
                    new ModerationQuery(PlaceStatus.PENDING, null, "84", true, 50, 0), total);
            assertThat(flagged).extracting(item -> item.place().name())
                    .contains("Circuit piéton du centre", "Chapelle Sainte-Anne")
                    .doesNotContain("Musée Calvet");
            ModerationItem route = flagged.stream()
                    .filter(item -> item.place().name().startsWith("Circuit")).findFirst().orElseThrow();
            assertThat(route.flags()).contains(PlaceFlag.ROUTE_NAME, PlaceFlag.NO_ADDRESS);

            assertThat(placeRepository.findPublishedWithinBounds(43, 45, 4, 6, List.of(), 100))
                    .extracting(Place::name).doesNotContain("Musée Calvet");
            placeRepository.moderate(List.of(locationIdOf("m")), PlaceStatus.PUBLISHED, null);
            assertThat(placeRepository.findPublishedWithinBounds(43, 45, 4, 6, List.of("Museum"), 100))
                    .extracting(Place::name).contains("Musée Calvet");
            assertThat(placeRepository.findById(locationIdOf("m")).orElseThrow().publishedAt()).isNotNull();
            // Transition non permise : refuser un lieu déjà refusé ne change rien.
            placeRepository.moderate(List.of(locationIdOf("a")), PlaceStatus.REJECTED, null);
            assertThat(placeRepository.moderate(List.of(locationIdOf("a")), PlaceStatus.REJECTED, null)).isEmpty();
        });
    }

    @Autowired
    private PlaceModerationService moderationService;

    @Test
    void activation_ShouldRequireASuccessfulDryRun() {
        inRolledBackTransaction(ignored -> {
            // Aucun lieu retenu : verdict suspect, source laissée inactive.
            when(collector.collect()).thenReturn(List.of(place("r", "Le Bistrot", RESTAURANT, 43.95, "Rue")));
            PlaceImportReport refused = moderationService.activate(sourceId).orElseThrow();
            assertThat(refused.verdict()).isEqualTo(Verdict.SUSPICIOUS);
            assertThat(jdbcTemplate.queryForObject("SELECT status FROM source WHERE id = ?", String.class, sourceId))
                    .isEqualTo("INACTIVE");

            doReturn(List.of(place("m", "Musée Calvet", MUSEUM, 43.944, "Rue"))).when(collector).collect();
            PlaceImportReport accepted = moderationService.activate(sourceId).orElseThrow();
            assertThat(accepted.verdict()).isEqualTo(Verdict.OK);
            assertThat(jdbcTemplate.queryForObject("SELECT status FROM source WHERE id = ?", String.class, sourceId))
                    .isEqualTo("ACTIVE");
            // L'essai à blanc n'a rien créé.
            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM location_source WHERE source_id = ?",
                    Integer.class, sourceId)).isZero();
        });
    }
}
