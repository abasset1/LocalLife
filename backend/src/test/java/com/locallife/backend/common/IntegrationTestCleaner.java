package com.locallife.backend.common;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Nettoyage des données laissées par les tests d'intégration <b>non transactionnels</b> (serveur
 * embarqué : les écritures sont faites sur le thread du serveur et réellement validées, un
 * {@code @Transactional} de test ne peut pas les annuler — voir {@code AuthenticationFlowIntegrationTest}).
 *
 * <p>Ces tests tournent contre la même base que le développement local : sans nettoyage, chaque
 * exécution y laissait des sources, des utilisateurs, des activités, des lieux et des food trucks de
 * test — et une source de test collectible restée en base était ensuite interrogée à chaque import réel.
 *
 * <p>Principe : le test <b>déclare</b> ce qu'il crée ({@code track...}) et {@link #cleanUp()} ne supprime
 * que cela, plus ce qui en dépend :
 * <ul>
 *   <li>les activités des sources suivies et celles créées par les utilisateurs suivis, avec toutes leurs
 *       entités liées (occurrences, schedules, médias, liens, accessibilité, tags, métadonnées...) ;</li>
 *   <li>les {@code classification_record} et {@code import_fingerprint} des sources suivies ;</li>
 *   <li>les lieux et médias que ces activités référençaient, <b>uniquement</b> s'ils ont été créés
 *       pendant le test (id postérieur à {@link #start()}) et ne sont plus référencés par rien — un lieu
 *       préexistant réutilisé par {@code LocationService#findOrCreate} n'est jamais supprimé.</li>
 * </ul>
 * Aucune suppression par motif ({@code LIKE}) : uniquement des noms, emails et ids exacts.
 *
 * <p>Simple {@code @Component} des sources de test : détecté par le scan de {@code BackendApplication}
 * dans tout {@code @SpringBootTest}, sans {@code @Import} (qui créerait un contexte Spring de plus).
 *
 * <p>Usage : {@code @BeforeEach} → {@link #start()}, {@code @AfterEach} → {@link #cleanUp()}.
 */
@Component
public class IntegrationTestCleaner {

    /** Tables rattachées à une activité par {@code activity_id}, sans {@code ON DELETE CASCADE}. */
    private static final List<String> ACTIVITY_CHILD_TABLES = List.of(
            "schedule", "activity_media", "activity_link", "accessibility", "activity_tag", "activity_metadata",
            "activity_activity_type", "activity_audience", "activity_organizer_type");

    private final JdbcTemplate jdbcTemplate;

    private final Set<String> userEmails = new LinkedHashSet<>();
    private final Set<String> sourceNames = new LinkedHashSet<>();
    private final Set<String> foodTruckNames = new LinkedHashSet<>();
    private final Set<Long> locationIds = new LinkedHashSet<>();
    private final Set<Long> mediaIds = new LinkedHashSet<>();

    private long lastLocationIdBeforeTest;
    private long lastMediaIdBeforeTest;

    public IntegrationTestCleaner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** À appeler avant chaque test : repère les lieux et médias déjà présents, jamais supprimés. */
    public void start() {
        reset();
        lastLocationIdBeforeTest = maxId("location");
        lastMediaIdBeforeTest = maxId("media");
    }

    /** Email d'un utilisateur que le test va inscrire ; renvoie l'email pour un usage en ligne. */
    public String trackUser(String email) {
        userEmails.add(email);
        return email;
    }

    /** Nom d'une source que le test va créer ; renvoie le nom pour un usage en ligne. */
    public String trackSource(String name) {
        sourceNames.add(name);
        return name;
    }

    /** Nom d'un food truck que le test va créer ; renvoie le nom pour un usage en ligne. */
    public String trackFoodTruck(String name) {
        foodTruckNames.add(name);
        return name;
    }

    /** Lieu créé directement par le test (pas forcément rattaché à une activité). */
    public void trackLocation(Long id) {
        locationIds.add(id);
    }

    /** Média créé directement par le test (pas forcément rattaché à une activité). */
    public void trackMedia(Long id) {
        mediaIds.add(id);
    }

    /** À appeler après chaque test : supprime ce qui a été déclaré et ce qui en dépend. */
    public void cleanUp() {
        try {
            List<Long> sourceIds = idsFor("SELECT id FROM source WHERE name = ?", sourceNames);
            List<Long> userIds = idsFor("SELECT id FROM users WHERE email = ?", userEmails);

            Set<Long> activityIds = new LinkedHashSet<>();
            activityIds.addAll(idsFor("SELECT id FROM activity WHERE source_id = ?", sourceIds));
            activityIds.addAll(idsFor("SELECT id FROM activity WHERE created_by = ?", userIds));
            activityIds.forEach(this::deleteActivity);

            for (Long sourceId : sourceIds) {
                // Éléments mis en quarantaine (sans activité) ; import_fingerprint part en cascade.
                jdbcTemplate.update("DELETE FROM classification_record WHERE source_id = ?", sourceId);
                jdbcTemplate.update("DELETE FROM source WHERE id = ?", sourceId);
            }
            // Jetons, identités et likes de l'utilisateur partent en cascade.
            userIds.forEach(userId -> jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId));
            foodTruckNames.forEach(name -> jdbcTemplate.update("DELETE FROM food_truck WHERE name = ?", name));

            deleteUnreferencedMedia();
            deleteUnreferencedLocations();
        } finally {
            reset();
        }
    }

    private void deleteActivity(Long activityId) {
        locationIds.addAll(jdbcTemplate.queryForList(
                "SELECT location_id FROM schedule WHERE activity_id = ? AND location_id IS NOT NULL",
                Long.class, activityId));
        locationIds.addAll(jdbcTemplate.queryForList(
                "SELECT o.location_id FROM occurrence o JOIN schedule s ON s.id = o.schedule_id "
                        + "WHERE s.activity_id = ? AND o.location_id IS NOT NULL",
                Long.class, activityId));
        mediaIds.addAll(jdbcTemplate.queryForList(
                "SELECT media_id FROM activity_media WHERE activity_id = ?", Long.class, activityId));

        jdbcTemplate.update(
                "DELETE FROM occurrence WHERE schedule_id IN (SELECT id FROM schedule WHERE activity_id = ?)",
                activityId);
        for (String table : ACTIVITY_CHILD_TABLES) {
            jdbcTemplate.update("DELETE FROM " + table + " WHERE activity_id = ?", activityId);
        }
        // activity_category, activity_likes et classification_record partent en cascade.
        jdbcTemplate.update("DELETE FROM activity WHERE id = ?", activityId);
    }

    private void deleteUnreferencedLocations() {
        for (Long locationId : locationIds) {
            if (locationId > lastLocationIdBeforeTest) {
                jdbcTemplate.update(
                        "DELETE FROM location l WHERE l.id = ? "
                                + "AND NOT EXISTS (SELECT 1 FROM schedule s WHERE s.location_id = l.id) "
                                + "AND NOT EXISTS (SELECT 1 FROM occurrence o WHERE o.location_id = l.id)",
                        locationId);
            }
        }
    }

    private void deleteUnreferencedMedia() {
        for (Long mediaId : mediaIds) {
            if (mediaId > lastMediaIdBeforeTest) {
                jdbcTemplate.update(
                        "DELETE FROM media m WHERE m.id = ? "
                                + "AND NOT EXISTS (SELECT 1 FROM activity_media am WHERE am.media_id = m.id)",
                        mediaId);
            }
        }
    }

    private List<Long> idsFor(String sql, Iterable<?> parameters) {
        Set<Long> ids = new LinkedHashSet<>();
        for (Object parameter : parameters) {
            ids.addAll(jdbcTemplate.queryForList(sql, Long.class, parameter));
        }
        return List.copyOf(ids);
    }

    private long maxId(String table) {
        Long max = jdbcTemplate.queryForObject("SELECT COALESCE(MAX(id), 0) FROM " + table, Long.class);
        return max == null ? 0 : max;
    }

    private void reset() {
        userEmails.clear();
        sourceNames.clear();
        foodTruckNames.clear();
        locationIds.clear();
        mediaIds.clear();
    }
}
