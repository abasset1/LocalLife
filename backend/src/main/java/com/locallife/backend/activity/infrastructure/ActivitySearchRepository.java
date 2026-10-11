package com.locallife.backend.activity.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Recherche textuelle globale (#91), en une requête PostgreSQL : voir le contrat
 * {@code docs/02_Architecture/GLOBAL_SEARCH_CONTRACT.md}.
 *
 * <p>Principe, étape par étape (CTE) :
 * <ol>
 *   <li>{@code published} : activités {@code PUBLISHED}, filtre de catégories (même sémantique que
 *       {@code /nearby}) ;</li>
 *   <li>{@code occ} : leurs occurrences non annulées qui respectent le filtre temporel, avec le lieu
 *       effectif {@code COALESCE(occurrence.location_id, schedule.location_id)} (ADR-0006) et la
 *       distance à la position de référence s'il y en a une — <b>aucun rayon</b> ;</li>
 *   <li>{@code eligible} : une activité avec des horaires est retenue si au moins une occurrence
 *       convient ; sans horaire, ses colonnes historiques (dates, lieu) s'appliquent ;</li>
 *   <li>{@code retained} : l'occurrence retenue par activité (la plus proche avec position, sinon la
 *       plus tôt) — elle fournit lieu, coordonnées, dates et distance de la réponse, sans mélange avec
 *       les colonnes historiques ;</li>
 *   <li>{@code matched} : textes déjà normalisés à l'écriture (colonnes {@code search_*}, V59) et niveau de
 *       pertinence ; une activité n'apparaît qu'une fois, quel que soit son nombre d'occurrences ;</li>
 *   <li>tri : niveau, nombre de champs correspondants, distance, début, identifiant ; pagination et
 *       total dans la même requête.</li>
 * </ol>
 */
@Repository
public class ActivitySearchRepository {

    /** Fuseau d'une occurrence sans lieu ou sans fuseau connu : même règle que {@code /nearby}. */
    private static final String DEFAULT_TIMEZONE = "Europe/Paris";

    private static final String SEARCH_SQL = """
            WITH q AS MATERIALIZED (
                SELECT ll_search_normalize(:q) AS qn
            ),
            terms AS MATERIALIZED (
                -- Termes significatifs (mots vides retirés, sauf si la requête n'est faite que de mots vides),
                -- sous forme de motifs LIKE : « tous les termes » devient champ LIKE ALL (motifs), sans
                -- sous-requête par ligne. Sans risque : la normalisation ne laisse que lettres, chiffres et
                -- espaces, donc ni % ni _ dans les motifs.
                SELECT ARRAY(SELECT '%' || t || '%' FROM unnest(COALESCE(
                           (SELECT array_agg(t) FROM unnest(string_to_array(q.qn, ' ')) t
                            WHERE t <> '' AND NOT (t = ANY (string_to_array(:stopwords, ',')))),
                           string_to_array(q.qn, ' '))) t
                       WHERE t <> '') AS patterns
                FROM q
            ),
            ref AS (
                SELECT CASE WHEN CAST(:latitude AS float8) IS NULL THEN NULL
                            ELSE ST_SetSRID(ST_MakePoint(CAST(:longitude AS float8), CAST(:latitude AS float8)),
                                            4326)::geography END AS point
            ),
            published AS MATERIALIZED (
                -- Colonnes utiles seulement : matérialiser les lignes entières (descriptions longues…)
                -- déborde de work_mem sur disque. Titre et description sont relus pour la page seule.
                SELECT a.id, a.start_date, a.end_date, a.city, a.address, a.latitude, a.longitude, a.location,
                       a.search_title, a.search_description, a.search_category, a.search_place
                FROM activity a
                WHERE a.status = 'PUBLISHED'
                  AND (CAST(:categories AS text) IS NULL
                       OR a.category = ANY (string_to_array(:categories, ','))
                       OR EXISTS (SELECT 1 FROM activity_category ac JOIN category c ON c.id = ac.category_id
                                  WHERE ac.activity_id = a.id
                                    AND c.code = ANY (string_to_array(:categories, ','))))
            ),
            occ AS MATERIALIZED (
                SELECT s.activity_id, o.id AS occurrence_id,
                       (o.start_at AT TIME ZONE tz.zone) AS local_start,
                       (o.end_at AT TIME ZONE tz.zone) AS local_end,
                       l.city, l.address, l.latitude, l.longitude, l.geo_location, l.search_place
                FROM published p
                JOIN schedule s ON s.activity_id = p.id
                JOIN occurrence o ON o.schedule_id = s.id
                LEFT JOIN location l ON l.id = COALESCE(o.location_id, s.location_id)
                CROSS JOIN LATERAL (SELECT COALESCE(l.timezone, :defaultTimezone) AS zone) tz
                CROSS JOIN LATERAL (
                    SELECT (o.start_at AT TIME ZONE tz.zone)::date AS day_start,
                           (COALESCE(o.end_at, o.start_at) AT TIME ZONE tz.zone)::date AS day_end) d
                WHERE o.status <> 'CANCELLED'
                  AND ((CAST(:date AS date) IS NULL AND d.day_end >= CURRENT_DATE)
                       OR (CAST(:date AS date) IS NOT NULL AND CAST(:dateTo AS date) IS NULL
                           AND CAST(:date AS date) BETWEEN d.day_start AND d.day_end)
                       OR (CAST(:date AS date) IS NOT NULL AND CAST(:dateTo AS date) IS NOT NULL
                           AND d.day_start <= CAST(:dateTo AS date) AND d.day_end >= CAST(:date AS date)))
            ),
            occ_places AS (
                SELECT activity_id, string_agg(search_place, ' ') AS places
                FROM occ GROUP BY activity_id
            ),
            scheduled AS (
                SELECT DISTINCT s.activity_id FROM schedule s JOIN published p ON p.id = s.activity_id
            ),
            eligible AS MATERIALIZED (
                SELECT p.*, (sc.activity_id IS NOT NULL) AS has_schedule, op.places
                FROM published p
                LEFT JOIN scheduled sc ON sc.activity_id = p.id
                LEFT JOIN occ_places op ON op.activity_id = p.id
                WHERE CASE
                    WHEN sc.activity_id IS NOT NULL THEN op.activity_id IS NOT NULL
                    ELSE (CAST(:date AS date) IS NULL
                          AND COALESCE(p.end_date, p.start_date)::date >= CURRENT_DATE)
                      OR (CAST(:date AS date) IS NOT NULL AND CAST(:dateTo AS date) IS NULL
                          AND CAST(:date AS date) BETWEEN p.start_date::date
                              AND COALESCE(p.end_date, p.start_date)::date)
                      OR (CAST(:date AS date) IS NOT NULL AND CAST(:dateTo AS date) IS NOT NULL
                          AND p.start_date::date <= CAST(:dateTo AS date)
                          AND COALESCE(p.end_date, p.start_date)::date >= CAST(:date AS date))
                    END
            ),
            category_text AS (
                SELECT c.id AS category_id,
                       COALESCE(ll_search_normalize(c.code || ' ' || COALESCE(string_agg(t.label, ' '), '')), '')
                           AS text
                FROM category c LEFT JOIN category_translation t ON t.category_id = c.id
                GROUP BY c.id, c.code
            ),
            category_labels AS (
                SELECT ac.activity_id, string_agg(ct.text, ' ') AS labels
                FROM activity_category ac JOIN category_text ct ON ct.category_id = ac.category_id
                WHERE ac.activity_id IN (SELECT id FROM eligible)
                GROUP BY ac.activity_id
            ),
            fields AS (
                SELECT e.id,
                       e.search_title AS title_n,
                       e.search_description AS description_n,
                       concat_ws(' ', e.search_category, cl.labels) AS category_n,
                       concat_ws(' ', e.search_place, e.places) AS location_n
                FROM eligible e
                LEFT JOIN category_labels cl ON cl.activity_id = e.id
            ),
            matched AS MATERIALIZED (
                -- « Contient la requête » implique « contient tous ses termes » : LIKE ALL suffit.
                SELECT * FROM (
                    SELECT f.id,
                           f.title_n = q.qn AS title_exact,
                           starts_with(f.title_n, q.qn) AS title_prefix,
                           f.title_n LIKE ALL (terms.patterns) AS title_match,
                           f.category_n LIKE ALL (terms.patterns) AS category_match,
                           f.location_n LIKE ALL (terms.patterns) AS location_match,
                           f.description_n LIKE ALL (terms.patterns) AS description_match
                    FROM fields f CROSS JOIN q CROSS JOIN terms
                    WHERE q.qn <> '' AND cardinality(terms.patterns) > 0
                ) candidates
                WHERE title_match OR category_match OR location_match OR description_match
            ),
            retained AS (
                SELECT DISTINCT ON (occ.activity_id) occ.activity_id, occ.local_start, occ.local_end,
                       occ.city, occ.address, occ.latitude, occ.longitude,
                       CASE WHEN occ.geo_location IS NOT NULL AND ref.point IS NOT NULL
                            THEN ST_Distance(occ.geo_location, ref.point) END AS distance
                FROM occ
                JOIN matched m ON m.id = occ.activity_id
                CROSS JOIN ref
                ORDER BY occ.activity_id, distance ASC NULLS LAST, occ.local_start ASC, occ.occurrence_id ASC
            ),
            ranked AS (
                SELECT e.id,
                       CASE WHEN m.title_exact THEN 1 WHEN m.title_prefix THEN 2 WHEN m.title_match THEN 3
                            WHEN m.category_match THEN 4 WHEN m.location_match THEN 5 ELSE 6 END AS relevance_rank,
                       (m.title_match::int + m.category_match::int + m.location_match::int
                        + m.description_match::int) AS matched_fields,
                       CASE WHEN e.has_schedule THEN r.city ELSE e.city END AS city,
                       CASE WHEN e.has_schedule THEN r.address ELSE e.address END AS address,
                       CASE WHEN e.has_schedule THEN r.latitude ELSE e.latitude END AS latitude,
                       CASE WHEN e.has_schedule THEN r.longitude ELSE e.longitude END AS longitude,
                       CASE WHEN e.has_schedule THEN r.local_start ELSE e.start_date END AS start_date,
                       CASE WHEN e.has_schedule THEN r.local_end ELSE e.end_date END AS end_date,
                       CASE WHEN e.has_schedule THEN r.distance
                            WHEN e.location IS NOT NULL AND ref.point IS NOT NULL
                                THEN ST_Distance(e.location, ref.point) END AS distance
                FROM matched m
                JOIN eligible e ON e.id = m.id
                LEFT JOIN retained r ON r.activity_id = e.id
                CROSS JOIN ref
            )
            SELECT (SELECT COUNT(*) FROM ranked) AS total, page.*,
                   a.title, a.description, a.category,
                   (SELECT string_agg(c.code, ',' ORDER BY c.code)
                    FROM activity_category ac JOIN category c ON c.id = ac.category_id
                    WHERE ac.activity_id = page.id) AS category_codes
            FROM (SELECT 1) AS one
            LEFT JOIN LATERAL (
                SELECT * FROM ranked
                ORDER BY relevance_rank ASC, matched_fields DESC, distance ASC NULLS LAST,
                         start_date ASC NULLS LAST, id ASC
                LIMIT :limit OFFSET :offset
            ) page ON TRUE
            LEFT JOIN activity a ON a.id = page.id
            ORDER BY page.relevance_rank ASC, page.matched_fields DESC, page.distance ASC NULLS LAST,
                     page.start_date ASC NULLS LAST, page.id ASC
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ActivitySearchRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Critères déjà validés par le service. {@code latitude}/{@code longitude} : tous deux ou aucun. */
    public record SearchQuery(
            String text, List<String> stopwords, Double latitude, Double longitude, String categoriesCsv,
            LocalDate date, LocalDate dateTo, int limit, int offset) {
    }

    /** Ligne de résultat ; {@code relevanceRank} de 1 (titre exact) à 6 (description seule). */
    public record SearchRow(
            long id, String title, String description, String category, List<String> categoryCodes, String city,
            String address, Double latitude, Double longitude, LocalDateTime startDate, LocalDateTime endDate,
            int relevanceRank, Double distanceMeters) {
    }

    /** Page de résultats et nombre total de résultats (tous filtres appliqués, avant pagination). */
    public record SearchPage(List<SearchRow> rows, long total) {
    }

    public SearchPage search(SearchQuery query) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("q", query.text())
                .addValue("stopwords", String.join(",", query.stopwords()))
                .addValue("latitude", query.latitude(), Types.DOUBLE)
                .addValue("longitude", query.longitude(), Types.DOUBLE)
                .addValue("categories", query.categoriesCsv(), Types.VARCHAR)
                .addValue("date", query.date(), Types.DATE)
                .addValue("dateTo", query.dateTo(), Types.DATE)
                .addValue("defaultTimezone", DEFAULT_TIMEZONE)
                .addValue("limit", query.limit())
                .addValue("offset", query.offset());

        List<SearchRow> rows = new ArrayList<>();
        long[] total = {0};
        jdbcTemplate.query(SEARCH_SQL, params, (ResultSet rs) -> {
            total[0] = rs.getLong("total");
            if (rs.getObject("id") != null) {
                rows.add(toRow(rs));
            }
        });
        return new SearchPage(rows, total[0]);
    }

    private static SearchRow toRow(ResultSet rs) throws SQLException {
        String codes = rs.getString("category_codes");
        return new SearchRow(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("description"),
                rs.getString("category"),
                codes == null ? List.of() : Arrays.asList(codes.split(",")),
                rs.getString("city"),
                rs.getString("address"),
                (Double) rs.getObject("latitude", Double.class),
                (Double) rs.getObject("longitude", Double.class),
                toLocalDateTime(rs.getTimestamp("start_date")),
                toLocalDateTime(rs.getTimestamp("end_date")),
                rs.getInt("relevance_rank"),
                (Double) rs.getObject("distance", Double.class));
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
