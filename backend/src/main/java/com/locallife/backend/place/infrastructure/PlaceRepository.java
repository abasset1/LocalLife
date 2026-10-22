package com.locallife.backend.place.infrastructure;

import com.locallife.backend.place.domain.CoordinatePrecision;
import com.locallife.backend.place.domain.Place;
import com.locallife.backend.place.domain.PlaceFlag;
import com.locallife.backend.place.domain.PlaceStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/**
 * Lieux permanents (ADR-0011, phase B) : colonnes {@code place_*} de {@code location} et provenance
 * {@code location_source}. Ces colonnes ne sont pas des champs de {@code Location} : l'import d'événements, qui
 * enregistre des {@code Location}, ne peut donc ni les lire ni les modifier.
 */
@Repository
public class PlaceRepository {

    /** Lieu connu d'une source, tel qu'enregistré au dernier import. */
    public record TrackedPlace(
            Long locationId, String externalId, String placeType, PlaceStatus status, Double latitude,
            Double longitude, String address, CoordinatePrecision coordinatePrecision, String contentHash) {
    }

    /** Champs d'un lieu fournis par une source. */
    public record PlaceFields(
            String name, String placeType, String address, String postalCode, String city, Double latitude,
            Double longitude, String description, String website, CoordinatePrecision coordinatePrecision) {
    }

    /** Lieu de la file de modération, avec ses signalements. */
    public record ModerationItem(Place place, Set<PlaceFlag> flags, String sourceName) {
    }

    /** Critères de la file de modération ; {@code null} = pas de filtre. */
    public record ModerationQuery(
            PlaceStatus status, String placeType, String department, Boolean flagged, int limit, int offset) {
    }

    static final String PLACE_COLUMNS = "l.id, l.name, l.place_type, l.place_status, l.address, "
            + "l.postal_code, l.city, l.latitude, l.longitude, l.description, l.website, l.coordinate_precision, "
            + "l.published_at, l.moderated_at";

    /** Itinéraires présentés comme des lieux (même règle que {@code place_types_report.py}). */
    private static final String ROUTE_NAME_REGEX =
            "^\\W*(circuit|balade|parcours|sentier|randonn|itin[ée]raire|boucle)";

    private static final String MODERATION_SQL = """
            WITH places AS (
                SELECT %s,
                       (l.address IS NULL OR btrim(l.address) = '') AS no_address,
                       (l.name ~* :routeRegex) AS route_name,
                       (count(*) OVER (PARTITION BY lower(btrim(l.name)), l.postal_code) > 1) AS duplicate_name,
                       (l.coordinate_precision = 'APPROXIMATE') AS approximate,
                       (SELECT s.name FROM location_source ls JOIN source s ON s.id = ls.source_id
                        WHERE ls.location_id = l.id ORDER BY ls.id LIMIT 1) AS source_name
                FROM location l
                -- Un brouillon de professionnel (ADR-0012) n'entre en modération qu'à sa soumission.
                WHERE l.place_status IS NOT NULL AND l.place_status <> 'DRAFT'
            ), filtered AS (
                SELECT * FROM places p
                WHERE (CAST(:status AS text) IS NULL OR p.place_status = :status)
                  AND (CAST(:placeType AS text) IS NULL OR p.place_type = :placeType)
                  AND (CAST(:department AS text) IS NULL OR p.postal_code LIKE :department || '%%')
                  AND (CAST(:flagged AS boolean) IS NULL
                       OR (p.no_address OR p.route_name OR p.duplicate_name OR p.approximate) = :flagged)
            )
            SELECT (SELECT count(*) FROM filtered) AS total, f.*
            FROM filtered f
            ORDER BY f.postal_code NULLS LAST, f.name, f.id
            LIMIT :limit OFFSET :offset
            """.formatted(PLACE_COLUMNS);

    private final NamedParameterJdbcTemplate jdbc;

    public PlaceRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---------------------------------------------------------------- import

    public Map<String, TrackedPlace> findTrackedBySource(Long sourceId) {
        Map<String, TrackedPlace> tracked = new HashMap<>();
        jdbc.query("""
                SELECT ls.external_id, ls.content_hash, l.id, l.place_type, l.place_status, l.latitude,
                       l.longitude, l.address, l.coordinate_precision
                FROM location_source ls JOIN location l ON l.id = ls.location_id
                WHERE ls.source_id = :sourceId AND l.place_status IS NOT NULL
                """, new MapSqlParameterSource("sourceId", sourceId), row -> {
                    tracked.put(row.getString("external_id"), new TrackedPlace(
                            row.getLong("id"), row.getString("external_id"), row.getString("place_type"),
                            PlaceStatus.valueOf(row.getString("place_status")),
                            row.getObject("latitude", Double.class), row.getObject("longitude", Double.class),
                            row.getString("address"),
                            CoordinatePrecision.valueOf(row.getString("coordinate_precision")),
                            row.getString("content_hash")));
                });
        return tracked;
    }

    /** Nouveau lieu permanent et sa provenance ; renvoie l'id du lieu. */
    public Long insert(Long sourceId, String externalId, String sourceTypes, String contentHash, PlaceFields fields,
            PlaceStatus status) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO location (name, address, postal_code, city, country_code, latitude, longitude, timezone,
                                      website, description, place_type, place_status, coordinate_precision)
                VALUES (:name, :address, :postalCode, :city, 'FR', :latitude, :longitude, 'Europe/Paris',
                        :website, :description, :placeType, :status, :precision)
                """, fieldParams(fields).addValue("status", status.name()), key, new String[] {"id"});
        Long locationId = key.getKey().longValue();
        jdbc.update("""
                INSERT INTO location_source (location_id, source_id, external_id, source_types, content_hash,
                                             last_seen_at)
                VALUES (:locationId, :sourceId, :externalId, :sourceTypes, :contentHash, now())
                """, new MapSqlParameterSource("locationId", locationId).addValue("sourceId", sourceId)
                .addValue("externalId", externalId).addValue("sourceTypes", sourceTypes)
                .addValue("contentHash", contentHash));
        return locationId;
    }

    /**
     * Met à jour un lieu connu et sa provenance. Une précision {@code EXACT} (validation humaine) n'est jamais
     * remplacée par celle de la source ; une modification d'un gestionnaire validée par l'équipe
     * ({@code manager_edited_at}) prime sur la source pour l'adresse, la position, le site et la description.
     */
    public void update(Long locationId, Long sourceId, String externalId, String sourceTypes, String contentHash,
            PlaceFields fields, PlaceStatus status) {
        jdbc.update("""
                UPDATE location SET name = :name,
                       address = CASE WHEN manager_edited_at IS NULL THEN :address ELSE address END,
                       postal_code = CASE WHEN manager_edited_at IS NULL THEN :postalCode ELSE postal_code END,
                       city = CASE WHEN manager_edited_at IS NULL THEN :city ELSE city END,
                       latitude = CASE WHEN manager_edited_at IS NULL THEN :latitude ELSE latitude END,
                       longitude = CASE WHEN manager_edited_at IS NULL THEN :longitude ELSE longitude END,
                       website = CASE WHEN manager_edited_at IS NULL THEN :website ELSE website END,
                       description = CASE WHEN manager_edited_at IS NULL THEN :description ELSE description END,
                       place_type = :placeType, place_status = :status,
                       coordinate_precision = CASE WHEN coordinate_precision = 'EXACT' THEN coordinate_precision
                                                   ELSE :precision END
                WHERE id = :id
                """, fieldParams(fields).addValue("status", status.name()).addValue("id", locationId));
        jdbc.update("""
                UPDATE location_source SET source_types = :sourceTypes, content_hash = :contentHash,
                       last_seen_at = now()
                WHERE source_id = :sourceId AND external_id = :externalId
                """, new MapSqlParameterSource("sourceId", sourceId).addValue("externalId", externalId)
                .addValue("sourceTypes", sourceTypes).addValue("contentHash", contentHash));
    }

    public void updateStatus(Long locationId, PlaceStatus status) {
        jdbc.update("UPDATE location SET place_status = :status WHERE id = :id",
                new MapSqlParameterSource("id", locationId).addValue("status", status.name()));
    }

    /** Date de dernière présence dans la collecte, pour les lieux inchangés. */
    public void markSeen(Long sourceId, Collection<String> externalIds) {
        if (externalIds.isEmpty()) {
            return;
        }
        jdbc.update("UPDATE location_source SET last_seen_at = now() "
                        + "WHERE source_id = :sourceId AND external_id IN (:externalIds)",
                new MapSqlParameterSource("sourceId", sourceId).addValue("externalIds", externalIds));
    }

    // ---------------------------------------------------------------- modération

    public List<ModerationItem> findForModeration(ModerationQuery query, long[] total) {
        List<ModerationItem> items = new ArrayList<>();
        total[0] = 0;
        jdbc.query(MODERATION_SQL, new MapSqlParameterSource()
                .addValue("routeRegex", ROUTE_NAME_REGEX)
                .addValue("status", query.status() == null ? null : query.status().name(), java.sql.Types.VARCHAR)
                .addValue("placeType", query.placeType(), java.sql.Types.VARCHAR)
                .addValue("department", query.department(), java.sql.Types.VARCHAR)
                .addValue("flagged", query.flagged(), java.sql.Types.BOOLEAN)
                .addValue("limit", query.limit())
                .addValue("offset", query.offset()), row -> {
                    total[0] = row.getLong("total");
                    Set<PlaceFlag> flags = EnumSet.noneOf(PlaceFlag.class);
                    if (row.getBoolean("no_address")) {
                        flags.add(PlaceFlag.NO_ADDRESS);
                    }
                    if (row.getBoolean("route_name")) {
                        flags.add(PlaceFlag.ROUTE_NAME);
                    }
                    if (row.getBoolean("duplicate_name")) {
                        flags.add(PlaceFlag.DUPLICATE_NAME);
                    }
                    if (row.getBoolean("approximate")) {
                        flags.add(PlaceFlag.APPROXIMATE_POSITION);
                    }
                    items.add(new ModerationItem(toPlace(row), flags, row.getString("source_name")));
                });
        return items;
    }

    /**
     * Publie ou refuse des lieux ; seules les transitions permises sont appliquées (publier : depuis
     * {@code PENDING} ou {@code REJECTED} ; refuser : depuis {@code PENDING} ou {@code PUBLISHED}). Un lieu créé par
     * l'administrateur lui-même n'est jamais modéré par lui (décision du 22/10/2026). Renvoie les ids effectivement
     * modifiés.
     */
    public List<Long> moderate(Collection<Long> ids, PlaceStatus target, Long adminId) {
        if (ids.isEmpty()) {
            return List.of();
        }
        List<String> from = target == PlaceStatus.PUBLISHED ? List.of("PENDING", "REJECTED")
                : List.of("PENDING", "PUBLISHED");
        return jdbc.queryForList("""
                UPDATE location SET place_status = :target, moderated_by = :adminId, moderated_at = now(),
                       published_at = CASE WHEN :target = 'PUBLISHED' THEN COALESCE(published_at, now())
                                           ELSE published_at END
                WHERE id IN (:ids) AND place_status IN (:from)
                  AND (created_by IS NULL OR CAST(:adminId AS bigint) IS NULL OR created_by <> :adminId)
                RETURNING id
                """, new MapSqlParameterSource("ids", ids).addValue("target", target.name())
                .addValue("adminId", adminId).addValue("from", from), Long.class);
    }

    /** Le lieu a-t-il été créé par ce compte (espace professionnel) ? */
    public boolean isCreatedBy(Long id, Long userId) {
        return userId != null && Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM location WHERE id = :id AND created_by = :userId)",
                new MapSqlParameterSource("id", id).addValue("userId", userId), Boolean.class));
    }

    public Optional<Place> findById(Long id) {
        List<Place> places = jdbc.query("SELECT " + PLACE_COLUMNS + " FROM location l "
                + "WHERE l.id = :id AND l.place_status IS NOT NULL",
                new MapSqlParameterSource("id", id), (row, index) -> toPlace(row));
        return places.stream().findFirst();
    }

    // ---------------------------------------------------------------- lecture publique

    /** Lieux publiés dans une zone, éventuellement limités à certains types. */
    public List<Place> findPublishedWithinBounds(
            double minLatitude, double maxLatitude, double minLongitude, double maxLongitude,
            Collection<String> placeTypes, int limit) {
        return jdbc.query("SELECT " + PLACE_COLUMNS + " FROM location l "
                        + "WHERE l.place_status = 'PUBLISHED' "
                        + "AND l.latitude BETWEEN :minLatitude AND :maxLatitude "
                        + "AND l.longitude BETWEEN :minLongitude AND :maxLongitude "
                        + (placeTypes.isEmpty() ? "" : "AND l.place_type IN (:placeTypes) ")
                        + "ORDER BY l.id LIMIT :limit",
                new MapSqlParameterSource("minLatitude", minLatitude).addValue("maxLatitude", maxLatitude)
                        .addValue("minLongitude", minLongitude).addValue("maxLongitude", maxLongitude)
                        .addValue("placeTypes", placeTypes.isEmpty() ? List.of("") : placeTypes)
                        .addValue("limit", limit),
                (row, index) -> toPlace(row));
    }

    // ---------------------------------------------------------------- outils

    private static MapSqlParameterSource fieldParams(PlaceFields fields) {
        return new MapSqlParameterSource()
                .addValue("name", fields.name())
                .addValue("placeType", fields.placeType())
                .addValue("address", fields.address())
                .addValue("postalCode", fields.postalCode())
                .addValue("city", fields.city())
                .addValue("latitude", fields.latitude())
                .addValue("longitude", fields.longitude())
                .addValue("description", fields.description())
                .addValue("website", fields.website())
                .addValue("precision", fields.coordinatePrecision().name());
    }

    static Place toPlace(ResultSet row) throws SQLException {
        return new Place(
                row.getLong("id"), row.getString("name"), row.getString("place_type"),
                PlaceStatus.valueOf(row.getString("place_status")), row.getString("address"),
                row.getString("postal_code"), row.getString("city"), row.getObject("latitude", Double.class),
                row.getObject("longitude", Double.class), row.getString("description"), row.getString("website"),
                CoordinatePrecision.valueOf(row.getString("coordinate_precision")),
                row.getObject("published_at", OffsetDateTime.class),
                row.getObject("moderated_at", OffsetDateTime.class));
    }
}
