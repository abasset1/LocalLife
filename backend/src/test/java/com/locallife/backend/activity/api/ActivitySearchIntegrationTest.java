package com.locallife.backend.activity.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.locallife.backend.activity.api.ActivitySearchResponse.Item;
import com.locallife.backend.activity.application.SearchRelevance;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * {@code GET /api/v1/activities/search} (#91) de bout en bout : HTTP réel, PostgreSQL/PostGIS réel.
 *
 * <p>Isolation : la recherche est globale, donc chaque exécution emploie un jeton aléatoire dans les
 * textes cherchés et des dates lointaines (dans environ 3 ans) — les autres données de la base ne
 * peuvent pas correspondre. Les données créées sont supprimées après chaque test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ActivitySearchIntegrationTest {

    private static final double[] LYON = {45.7640, 4.8357};
    private static final double[] PARIS = {48.8566, 2.3522};
    private static final double[] MARSEILLE = {43.2965, 5.3698};
    private static final double[] BORDEAUX = {44.8378, -0.5792};

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private final List<Long> activityIds = new ArrayList<>();
    private final List<Long> locationIds = new ArrayList<>();

    /** Jeton propre à l'exécution : lettres seules, pour rester un mot après normalisation. */
    private String token;
    /** Début d'une fenêtre de dates propre à l'exécution, loin dans le futur. */
    private LocalDate day;

    @BeforeEach
    void setUp() {
        Random random = new Random();
        StringBuilder builder = new StringBuilder("zq");
        for (int i = 0; i < 8; i++) {
            builder.append((char) ('a' + random.nextInt(26)));
        }
        token = builder.toString();
        day = LocalDate.now().plusYears(3).plusDays(random.nextInt(300));
    }

    @AfterEach
    void cleanUp() {
        transactionTemplate.executeWithoutResult(status -> {
            for (Long id : activityIds) {
                jdbcTemplate.update("DELETE FROM occurrence WHERE schedule_id IN "
                        + "(SELECT id FROM schedule WHERE activity_id = ?)", id);
                jdbcTemplate.update("DELETE FROM schedule WHERE activity_id = ?", id);
                jdbcTemplate.update("DELETE FROM activity WHERE id = ?", id);
            }
            for (Long id : locationIds) {
                jdbcTemplate.update("DELETE FROM location WHERE id = ?", id);
            }
        });
    }

    // --- Données ---

    /** Activité à enregistrer ; par défaut publiée, à Bordeaux, le jour {@link #day}, catégorie SPORT. */
    private final class Act {
        private final String title;
        private String description;
        private String category;
        private String city = "Bordeaux";
        private String address;
        private double[] position = BORDEAUX;
        private String status = "PUBLISHED";
        private LocalDateTime start = day.atTime(10, 0);
        private LocalDateTime end;
        private String categoryCode = "SPORT";

        Act(String title) {
            this.title = title;
        }

        Act description(String value) {
            description = value;
            return this;
        }

        Act category(String value) {
            category = value;
            return this;
        }

        Act city(String value) {
            city = value;
            return this;
        }

        Act address(String value) {
            address = value;
            return this;
        }

        Act at(double[] value) {
            position = value;
            return this;
        }

        Act status(String value) {
            status = value;
            return this;
        }

        Act starts(LocalDateTime value) {
            start = value;
            return this;
        }

        Act ends(LocalDateTime value) {
            end = value;
            return this;
        }

        Act categoryCode(String value) {
            categoryCode = value;
            return this;
        }

        long save() {
            Long id = transactionTemplate.execute(status -> {
                Long created = jdbcTemplate.queryForObject(
                        "INSERT INTO activity (title, description, category, city, address, latitude, longitude, "
                                + "status, start_date, end_date, source_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, "
                                + "(SELECT id FROM source WHERE type = 'MANUAL' LIMIT 1)) RETURNING id",
                        Long.class, title, description, category, city, address, position[0], position[1],
                        this.status, start, end);
                jdbcTemplate.update("INSERT INTO activity_category (activity_id, category_id) "
                        + "SELECT ?, id FROM category WHERE code = ?", created, categoryCode);
                return created;
            });
            activityIds.add(id);
            return id;
        }
    }

    private long location(String city, double[] position) {
        Long id = jdbcTemplate.queryForObject(
                "INSERT INTO location (name, city, address, latitude, longitude, timezone) "
                        + "VALUES (?, ?, ?, ?, ?, 'Europe/Paris') RETURNING id",
                Long.class, "Lieu " + city, city, "1 place de " + city, position[0], position[1]);
        locationIds.add(id);
        return id;
    }

    private long schedule(long activityId, Long locationId) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO schedule (activity_id, location_id) VALUES (?, ?) RETURNING id",
                Long.class, activityId, locationId);
    }

    private void occurrence(long scheduleId, LocalDateTime localStart, Long locationId, String status) {
        jdbcTemplate.update("INSERT INTO occurrence (schedule_id, start_at, end_at, location_id, status, is_exception) "
                        + "VALUES (?, ?::timestamp AT TIME ZONE 'Europe/Paris', "
                        + "(?::timestamp + interval '2 hours') AT TIME ZONE 'Europe/Paris', ?, ?, ?)",
                scheduleId, localStart, localStart, locationId, status, locationId != null);
    }

    // --- Appels ---

    private RestTestClient client() {
        return RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    /** URI déjà encodée, passée telle quelle (une chaîne serait encodée une seconde fois : %20 → %2520). */
    private URI uri(Map<String, Object> params) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString("http://localhost:" + port)
                .path("/api/v1/activities/search");
        params.forEach(builder::queryParam);
        return builder.encode().build().toUri();
    }

    private ActivitySearchResponse search(Map<String, Object> params) {
        return client().get().uri(uri(params)).exchange()
                .expectStatus().isOk()
                .expectBody(ActivitySearchResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private ActivitySearchResponse search(String q) {
        return search(Map.of("q", q));
    }

    private static List<Long> ids(ActivitySearchResponse response) {
        return response.items().stream().map(Item::id).toList();
    }

    private Item only(ActivitySearchResponse response) {
        assertThat(response.items()).hasSize(1);
        return response.items().get(0);
    }

    // --- Visibilité ---

    @Test
    void onlyPublishedActivitiesAreFound() {
        long published = new Act("Atelier " + token).save();
        for (String status : List.of("PENDING", "REJECTED", "CANCELLED", "ARCHIVED")) {
            new Act("Atelier " + token).status(status).save();
        }

        ActivitySearchResponse response = search(token);

        assertThat(ids(response)).containsExactly(published);
        assertThat(response.total()).isEqualTo(1);
    }

    // --- Champs cherchés ---

    @Test
    void searchesEachField_WithMatchingRelevance() {
        long title = new Act(token + "titre").save();
        long description = new Act("Sans rapport").description("Animé par " + token + "desc").save();
        long city = new Act("Sans rapport").city(token + "ville").save();
        long address = new Act("Sans rapport").address("12 rue " + token + "adresse").save();
        long category = new Act("Sans rapport").category(token + "cat").save();

        assertThat(only(search(token + "titre")).id()).isEqualTo(title);
        assertThat(only(search(token + "titre")).relevance()).isEqualTo(SearchRelevance.TITLE_EXACT);
        assertThat(only(search(token + "desc")).id()).isEqualTo(description);
        assertThat(only(search(token + "desc")).relevance()).isEqualTo(SearchRelevance.DESCRIPTION_MATCH);
        assertThat(only(search(token + "ville")).id()).isEqualTo(city);
        assertThat(only(search(token + "ville")).relevance()).isEqualTo(SearchRelevance.LOCATION_MATCH);
        assertThat(only(search(token + "adresse")).id()).isEqualTo(address);
        assertThat(only(search(token + "adresse")).relevance()).isEqualTo(SearchRelevance.LOCATION_MATCH);
        assertThat(only(search(token + "cat")).id()).isEqualTo(category);
        assertThat(only(search(token + "cat")).relevance()).isEqualTo(SearchRelevance.CATEGORY_MATCH);
    }

    @Test
    void searchesCategoryLabelAndCode_FromTheReferential() {
        long gastronomy = new Act("Sans rapport").categoryCode("GASTRONOMY").save();
        new Act("Sans rapport").categoryCode("SPORT").save();

        ActivitySearchResponse byLabel = search(Map.of("q", "Gastronomie", "date", day.toString()));
        ActivitySearchResponse byCode = search(Map.of("q", "gastronomy", "date", day.toString()));

        assertThat(ids(byLabel)).contains(gastronomy);
        assertThat(byLabel.items()).filteredOn(item -> item.id() == gastronomy)
                .extracting(Item::relevance).containsExactly(SearchRelevance.CATEGORY_MATCH);
        assertThat(ids(byCode)).contains(gastronomy);
        assertThat(byLabel.items()).allMatch(item -> item.categoryCodes().contains("GASTRONOMY"));
    }

    @Test
    void searchesTheCityOfAnEffectiveOccurrenceLocation() {
        long activity = new Act("Sans rapport").save();
        long venue = location(token + "lieu", LYON);
        occurrence(schedule(activity, venue), day.atTime(18, 0), null, "SCHEDULED");

        Item item = only(search(token + "lieu"));

        assertThat(item.id()).isEqualTo(activity);
        assertThat(item.relevance()).isEqualTo(SearchRelevance.LOCATION_MATCH);
        assertThat(item.city()).isEqualTo(token + "lieu");
        assertThat(item.latitude()).isEqualTo(LYON[0]);
    }

    // --- Normalisation ---

    @Test
    void ignoresCaseAccentsPunctuationAndExtraSpaces() {
        long fete = new Act("Fête de la Brocante " + token).save();

        assertThat(only(search("FÊTE DE LA BROCANTE " + token.toUpperCase())).relevance())
                .isEqualTo(SearchRelevance.TITLE_EXACT);
        assertThat(only(search("  fete   de-la brocante!! " + token + "  ")).id()).isEqualTo(fete);
        // Tous les termes significatifs, dans un autre ordre, mots vides ignorés.
        Item terms = only(search(token + " brocante la fete"));
        assertThat(terms.id()).isEqualTo(fete);
        assertThat(terms.relevance()).isEqualTo(SearchRelevance.TITLE_CONTAINS);
    }

    // --- Classement ---

    @Test
    void ranksByRelevanceLevelBeforeDistance() {
        // Le titre exact est le plus éloigné ; la description seule est la plus proche.
        long exact = new Act(token).at(MARSEILLE).save();
        long prefix = new Act(token + " festival").at(PARIS).save();
        long contains = new Act("Grand " + token).at(PARIS).save();
        long category = new Act("Sans rapport").category(token).at(LYON).save();
        long location = new Act("Sans rapport").city(token).at(LYON).save();
        long description = new Act("Sans rapport").description("avec " + token).at(LYON).save();

        ActivitySearchResponse response = search(Map.of(
                "q", token, "latitude", LYON[0], "longitude", LYON[1]));

        assertThat(ids(response)).containsExactly(exact, prefix, contains, category, location, description);
        assertThat(response.items()).extracting(Item::relevance).containsExactly(
                SearchRelevance.TITLE_EXACT, SearchRelevance.TITLE_PREFIX, SearchRelevance.TITLE_CONTAINS,
                SearchRelevance.CATEGORY_MATCH, SearchRelevance.LOCATION_MATCH, SearchRelevance.DESCRIPTION_MATCH);
    }

    @Test
    void ranksByDistanceWithinALevel_WithoutAnyMaximumRadius() {
        long marseille = new Act("Concert " + token).at(MARSEILLE).starts(day.atTime(9, 0)).save();
        long paris = new Act("Concert " + token).at(PARIS).starts(day.atTime(10, 0)).save();
        long lyon = new Act("Concert " + token).at(LYON).starts(day.atTime(11, 0)).save();

        ActivitySearchResponse near = search(Map.of("q", token, "latitude", LYON[0], "longitude", LYON[1]));

        // Lyon–Marseille ≈ 277 km, Lyon–Paris ≈ 392 km : aucun résultat écarté, quelle que soit la distance.
        assertThat(ids(near)).containsExactly(lyon, marseille, paris);
        assertThat(near.items().get(0).distanceMeters()).isLessThan(100);
        assertThat(near.items().get(1).distanceMeters()).isBetween(270_000L, 290_000L);
        assertThat(near.items().get(2).distanceMeters()).isBetween(380_000L, 410_000L);
    }

    @Test
    void withoutCoordinates_ordersByStartDateThenId_AndReportsNoDistance() {
        long later = new Act("Concert " + token).at(LYON).starts(day.atTime(20, 0)).save();
        long earlier = new Act("Concert " + token).at(MARSEILLE).starts(day.atTime(8, 0)).save();
        long sameTimeFirst = new Act("Concert " + token).at(PARIS).starts(day.atTime(12, 0)).save();
        long sameTimeSecond = new Act("Concert " + token).at(PARIS).starts(day.atTime(12, 0)).save();

        ActivitySearchResponse response = search(token);

        assertThat(ids(response)).containsExactly(earlier, sameTimeFirst, sameTimeSecond, later);
        assertThat(response.items()).extracting(Item::distanceMeters).containsOnlyNulls();
    }

    @Test
    void moreMatchingFieldsRankFirstWithinALevel() {
        long titleOnly = new Act("Concert " + token).at(LYON).save();
        long titleAndDescription = new Act("Concert " + token).description("Concert " + token).at(MARSEILLE).save();

        ActivitySearchResponse response = search(Map.of("q", token, "latitude", LYON[0], "longitude", LYON[1]));

        assertThat(ids(response)).containsExactly(titleAndDescription, titleOnly);
    }

    // --- Filtres ---

    @Test
    void filtersByCategory_CodesAndFreeText() {
        long culture = new Act("Expo " + token).categoryCode("CULTURE").save();
        long sport = new Act("Course " + token).categoryCode("SPORT").save();
        long freeText = new Act("Bal " + token).category("guinguette").save();

        assertThat(ids(search(Map.of("q", token, "category", "CULTURE")))).containsExactly(culture);
        assertThat(ids(search(Map.of("q", token, "category", " CULTURE , guinguette "))))
                .containsExactlyInAnyOrder(culture, freeText);
        assertThat(search(Map.of("q", token, "category", "INCONNUE")).total()).isZero();
        assertThat(sport).isPositive();
    }

    @Test
    void filtersByDateAndPeriod_OnActivitiesAndOccurrences() {
        long first = new Act("Stage " + token).starts(day.atTime(10, 0)).ends(day.plusDays(2).atTime(18, 0)).save();
        long later = new Act("Stage " + token).starts(day.plusDays(10).atTime(10, 0)).save();
        long withOccurrence = new Act("Stage " + token).starts(day.minusYears(1).atTime(10, 0)).save();
        occurrence(schedule(withOccurrence, location("Lyon", LYON)), day.plusDays(20).atTime(15, 0), null, "SCHEDULED");

        assertThat(ids(search(Map.of("q", token, "date", day.plusDays(1).toString())))).containsExactly(first);
        assertThat(ids(search(Map.of("q", token, "date", day.plusDays(5).toString(),
                "dateTo", day.plusDays(25).toString())))).containsExactly(later, withOccurrence);
        assertThat(ids(search(Map.of("q", token, "date", day.plusDays(20).toString()))))
                .containsExactly(withOccurrence);
    }

    @Test
    void withoutDate_keepsUnfinishedActivities_AndExcludesFinishedOnes() {
        long future = new Act("Salon " + token).save();
        long ongoing = new Act("Salon " + token).starts(LocalDateTime.now().minusDays(3))
                .ends(LocalDateTime.now().plusDays(3)).save();
        new Act("Salon " + token).starts(LocalDateTime.now().minusDays(10)).ends(LocalDateTime.now().minusDays(2))
                .save();

        assertThat(ids(search(token))).containsExactlyInAnyOrder(future, ongoing);
    }

    @Test
    void paginatesWithAStableOrderAndTotal() {
        List<Long> created = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            created.add(new Act("Marché " + token).starts(day.atTime(8 + i, 0)).save());
        }

        ActivitySearchResponse first = search(Map.of("q", token, "limit", 2));
        ActivitySearchResponse second = search(Map.of("q", token, "limit", 2, "offset", 2));
        ActivitySearchResponse third = search(Map.of("q", token, "limit", 2, "offset", 4));
        ActivitySearchResponse beyond = search(Map.of("q", token, "limit", 2, "offset", 10));

        assertThat(first.total()).isEqualTo(5);
        assertThat(first.limit()).isEqualTo(2);
        assertThat(second.offset()).isEqualTo(2);
        List<Long> all = new ArrayList<>(ids(first));
        all.addAll(ids(second));
        all.addAll(ids(third));
        assertThat(all).containsExactlyElementsOf(created);
        assertThat(beyond.items()).isEmpty();
        assertThat(beyond.total()).isEqualTo(5);
        ActivitySearchResponse defaults = search(token);
        assertThat(defaults.limit()).isEqualTo(20);
        assertThat(defaults.offset()).isZero();
    }

    // --- Modèle riche : occurrences, exceptions de lieu, annulations ---

    @Test
    void severalOccurrences_OneResult_RetainingTheNearestEffectiveLocation() {
        long activity = new Act("Tournée " + token).at(BORDEAUX).save();
        long tour = schedule(activity, null);
        occurrence(tour, day.atTime(20, 0), location("Lyon", LYON), "SCHEDULED");
        occurrence(tour, day.plusDays(1).atTime(20, 0), location("Paris", PARIS), "SCHEDULED");
        occurrence(tour, day.plusDays(2).atTime(20, 0), location("Marseille", MARSEILLE), "SCHEDULED");

        Item nearMarseille = only(search(Map.of("q", token, "latitude", MARSEILLE[0], "longitude", MARSEILLE[1])));
        Item nearParis = only(search(Map.of("q", token, "latitude", PARIS[0], "longitude", PARIS[1])));
        Item noReference = only(search(token));

        assertThat(nearMarseille.city()).isEqualTo("Marseille");
        assertThat(nearMarseille.latitude()).isEqualTo(MARSEILLE[0]);
        assertThat(nearMarseille.distanceMeters()).isLessThan(100);
        assertThat(nearMarseille.startDate()).isEqualTo(day.plusDays(2).atTime(20, 0));
        assertThat(nearParis.city()).isEqualTo("Paris");
        // Sans position : l'occurrence la plus tôt, jamais les coordonnées historiques (Bordeaux).
        assertThat(noReference.city()).isEqualTo("Lyon");
        assertThat(noReference.latitude()).isEqualTo(LYON[0]);
        assertThat(noReference.distanceMeters()).isNull();
    }

    @Test
    void locationExceptionOfAnOccurrence_IsTheEffectiveLocation() {
        long activity = new Act("Bal " + token).save();
        long usual = schedule(activity, location("Lyon", LYON));
        occurrence(usual, day.atTime(21, 0), location("Marseille", MARSEILLE), "SCHEDULED");

        Item item = only(search(Map.of("q", token, "latitude", MARSEILLE[0], "longitude", MARSEILLE[1])));

        assertThat(item.city()).isEqualTo("Marseille");
        assertThat(item.distanceMeters()).isLessThan(100);
    }

    @Test
    void cancelledOccurrences_AreIgnored() {
        long activity = new Act("Visite " + token).save();
        long visits = schedule(activity, null);
        occurrence(visits, day.atTime(10, 0), location("Marseille", MARSEILLE), "CANCELLED");
        occurrence(visits, day.plusDays(1).atTime(10, 0), location("Lyon", LYON), "SCHEDULED");
        long onlyCancelled = new Act("Visite " + token).save();
        occurrence(schedule(onlyCancelled, null), day.atTime(10, 0), location("Paris", PARIS), "CANCELLED");

        ActivitySearchResponse response = search(Map.of(
                "q", token, "latitude", MARSEILLE[0], "longitude", MARSEILLE[1]));

        assertThat(ids(response)).containsExactly(activity);
        assertThat(response.items().get(0).city()).isEqualTo("Lyon");
        assertThat(response.items().get(0).distanceMeters()).isGreaterThan(250_000);
        // La ville d'une occurrence annulée ne rend pas l'activité trouvable.
        assertThat(search("Marseille " + token).total()).isZero();
    }

    @Test
    void periodFilter_SelectsTheOccurrencesUsedForLocationAndDistance() {
        long activity = new Act("Cirque " + token).save();
        long shows = schedule(activity, null);
        occurrence(shows, day.atTime(20, 0), location("Lyon", LYON), "SCHEDULED");
        occurrence(shows, day.plusDays(7).atTime(20, 0), location("Marseille", MARSEILLE), "SCHEDULED");

        Item item = only(search(Map.of("q", token, "latitude", LYON[0], "longitude", LYON[1],
                "date", day.plusDays(7).toString())));

        assertThat(item.city()).isEqualTo("Marseille");
        assertThat(item.distanceMeters()).isGreaterThan(250_000);
    }

    @Test
    void activityWithoutAnyEffectivePosition_IsKept_WithNullDistance_AfterLocatedOnes() {
        long unlocated = new Act("Atelier " + token).save();
        occurrence(schedule(unlocated, null), day.atTime(10, 0), null, "SCHEDULED");
        long located = new Act("Atelier " + token).save();
        occurrence(schedule(located, location("Paris", PARIS)), day.atTime(11, 0), null, "SCHEDULED");

        ActivitySearchResponse response = search(Map.of("q", token, "latitude", LYON[0], "longitude", LYON[1]));

        assertThat(ids(response)).containsExactly(located, unlocated);
        Item last = response.items().get(1);
        assertThat(last.distanceMeters()).isNull();
        assertThat(last.latitude()).isNull();
        assertThat(last.city()).isNull();
    }

    // --- Multi-champs : chaque terme peut correspondre à un champ différent ---

    /** Recherche isolée par la date propre à l'exécution (les exemples n'emploient pas le jeton). */
    private ActivitySearchResponse searchOnDay(String q) {
        return search(Map.of("q", q, "date", day.toString()));
    }

    private Item found(ActivitySearchResponse response, long id) {
        return response.items().stream().filter(item -> item.id() == id).findFirst()
                .orElseThrow(() -> new AssertionError("Activité " + id + " absente de " + ids(response)));
    }

    @Test
    void concertLyon_TitleAndCity_IsFound_AsLocationMatch() {
        long lyon = new Act("Concert acoustique").city("Lyon").save();

        ActivitySearchResponse response = searchOnDay("concert Lyon");

        assertThat(ids(response)).containsExactly(lyon);
        // Pertinence globale : « lyon » n'est trouvé que dans le lieu, pas TITLE_CONTAINS.
        assertThat(response.items().get(0).relevance()).isEqualTo(SearchRelevance.LOCATION_MATCH);
    }

    @Test
    void concertLyon_WithoutAnyLyonMatch_IsNotFound() {
        new Act("Concert acoustique").city("Marseille").address("Vieux-Port").save();

        assertThat(searchOnDay("concert Lyon").total()).isZero();
    }

    @Test
    void termsSpreadAcrossTitleCategoryAndCity_AreFound_WhenAllCovered() {
        long all = new Act("Soirée jazz").categoryCode("GASTRONOMY").city("Lyon").save();
        new Act("Soirée jazz").categoryCode("GASTRONOMY").city("Marseille").save();
        new Act("Soirée jazz").categoryCode("SPORT").city("Lyon").save();

        ActivitySearchResponse response = searchOnDay("jazz gastronomie lyon");

        assertThat(ids(response)).containsExactly(all);
        assertThat(response.items().get(0).relevance()).isEqualTo(SearchRelevance.LOCATION_MATCH);
    }

    @Test
    void termFoundOnlyInEffectiveOccurrenceLocation_CountsAsLocation_UnlessCancelled() {
        long scheduled = new Act("Concert acoustique").city("Bordeaux").save();
        occurrence(schedule(scheduled, null), day.atTime(20, 0), location("Lyon", LYON), "SCHEDULED");
        long cancelled = new Act("Concert acoustique").city("Bordeaux").save();
        long tour = schedule(cancelled, null);
        occurrence(tour, day.atTime(20, 0), location("Lyon", LYON), "CANCELLED");
        occurrence(tour, day.atTime(21, 0), location("Nantes", new double[] {47.2184, -1.5536}), "SCHEDULED");

        ActivitySearchResponse response = searchOnDay("concert lyon");

        assertThat(ids(response)).containsExactly(scheduled);
        assertThat(response.items().get(0).city()).isEqualTo("Lyon");
    }

    @Test
    void sixRelevanceLevels_WithAMultiTermQuery_InStrictOrder() {
        // q = « jazz culture » ; une activité par niveau, la plus proche étant la moins pertinente.
        long exact = new Act("Jazz Culture").at(MARSEILLE).save();
        long prefix = new Act("Jazz culture en plein air").at(MARSEILLE).save();
        long contains = new Act("Festival de culture et de jazz").at(PARIS).save();
        long category = new Act("Soirée jazz").categoryCode("CULTURE").at(PARIS).save();
        long location = new Act("Soirée jazz").address("12 rue de la Culture").at(LYON).save();
        long description = new Act("Soirée jazz").description("Une soirée dédiée à la culture").at(LYON).save();
        new Act("Soirée jazz").at(LYON).save();

        ActivitySearchResponse response = search(Map.of(
                "q", "jazz culture", "date", day.toString(), "latitude", LYON[0], "longitude", LYON[1]));

        assertThat(ids(response)).containsExactly(exact, prefix, contains, category, location, description);
        assertThat(response.items()).extracting(Item::relevance).containsExactly(
                SearchRelevance.TITLE_EXACT, SearchRelevance.TITLE_PREFIX, SearchRelevance.TITLE_CONTAINS,
                SearchRelevance.CATEGORY_MATCH, SearchRelevance.LOCATION_MATCH, SearchRelevance.DESCRIPTION_MATCH);
    }

    @Test
    void globalRelevance_IsTheWeakestTermsBestField_NotTheBestTerm() {
        // « jazz » est dans le titre, mais « culture » seulement dans la description.
        long titleAndDescription = new Act("Grand jazz").description("Ouvert à la culture").save();

        Item item = found(searchOnDay("jazz culture"), titleAndDescription);

        assertThat(item.relevance()).isEqualTo(SearchRelevance.DESCRIPTION_MATCH);
    }

    @Test
    void multiField_SecondaryOrder_IsDistanceThenStartDateThenId() {
        long far = new Act("Concert acoustique").city("Lyon").at(MARSEILLE).starts(day.atTime(8, 0)).save();
        long near = new Act("Concert acoustique").city("Lyon").at(LYON).starts(day.atTime(22, 0)).save();
        long nearLater = new Act("Concert acoustique").city("Lyon").at(LYON).starts(day.atTime(23, 0)).save();

        ActivitySearchResponse byDistance = search(Map.of(
                "q", "concert lyon", "date", day.toString(), "latitude", LYON[0], "longitude", LYON[1]));
        ActivitySearchResponse byDate = searchOnDay("concert lyon");

        assertThat(ids(byDistance)).containsExactly(near, nearLater, far);
        assertThat(ids(byDate)).containsExactly(far, near, nearLater);
    }

    @Test
    void multiField_StopwordsAreIgnored_WithFallbackWhenOnlyStopwords() {
        // Aucun champ ne contient « les » : il est ignoré, la recherche porte sur « concert » et « lyon ».
        long lyon = new Act("Concert acoustique").city("Lyon").save();
        long onlyStopwords = new Act("De la musique").city("Nantes").save();

        assertThat(ids(searchOnDay("les concert lyon"))).containsExactly(lyon);
        // Requête faite uniquement de mots vides : tous ses mots sont utilisés.
        assertThat(ids(searchOnDay("de la"))).contains(onlyStopwords).doesNotContain(lyon);
    }

    @Test
    void multiField_IgnoresAccentsAndCaseInEveryField() {
        long fete = new Act("FÊTE foraine").city("Saint-Étienne").categoryCode("LEISURE").save();

        assertThat(ids(searchOnDay("fete saint etienne"))).containsExactly(fete);
        assertThat(ids(searchOnDay("Fête LOISIRS saint-étienne"))).containsExactly(fete);
        assertThat(found(searchOnDay("fete etienne"), fete).relevance()).isEqualTo(SearchRelevance.LOCATION_MATCH);
    }

    // --- Erreurs ---

    @ParameterizedTest
    @ValueSource(strings = {
        "", "q=", "q=%20%20", "q=a", "q=!!!",
        "q=concert&latitude=45.7", "q=concert&longitude=4.8", "q=concert&latitude=91&longitude=4.8",
        "q=concert&latitude=45&longitude=181", "q=concert&latitude=abc&longitude=4.8",
        "q=concert&date=2026-13-01", "q=concert&dateTo=2026-10-01",
        "q=concert&date=2026-10-05&dateTo=2026-10-01",
        "q=concert&limit=0", "q=concert&limit=101", "q=concert&limit=dix", "q=concert&offset=-1"
    })
    void invalidRequests_AreRejectedWithTheStandardErrorFormat(String queryString) {
        client().get().uri(URI.create("http://localhost:" + port + "/api/v1/activities/search?" + queryString))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.status").isEqualTo(400)
                .jsonPath("$.error").isEqualTo("Bad Request")
                .jsonPath("$.message").isNotEmpty()
                .jsonPath("$.path").isEqualTo("/api/v1/activities/search")
                .jsonPath("$.timestamp").isNotEmpty();
    }

    @Test
    void queryLongerThan120Characters_IsRejected() {
        client().get().uri(uri(Map.of("q", "a".repeat(121)))).exchange().expectStatus().isBadRequest();
        client().get().uri(uri(Map.of("q", "a".repeat(120)))).exchange().expectStatus().isOk();
    }
}
