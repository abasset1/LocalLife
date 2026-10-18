package com.locallife.backend.place.infrastructure;

import com.locallife.backend.place.domain.Place;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/**
 * Lieux de l'espace professionnel (ADR-0012, lot B) : création par un professionnel, lieux qu'il gère et recherche
 * des lieux proches avant création. Un lieu créé ici n'a pas de provenance ({@code location_source}) : l'import ne
 * le voit jamais. Ne modifie jamais un lieu importé.
 */
@Repository
public class ProPlaceRepository {

    /** Champs saisis par le professionnel. */
    public record ProPlaceFields(
            String name, String placeType, String address, String postalCode, String city, double latitude,
            double longitude, String description, String website, String phone, String email) {
    }

    /** Lieu vu par un de ses gestionnaires : tous statuts, contacts compris. */
    public record ManagedPlace(
            Place place, String phone, String email, boolean imported, Long createdBy, Long reportedSimilarTo) {
    }

    /** Lieu existant proche d'un lieu à créer ; distance nulle si l'une des positions est inconnue. */
    public record SimilarPlace(Place place, Double distanceMeters) {
    }

    private static final String MANAGED_SELECT = "SELECT " + PlaceRepository.PLACE_COLUMNS + """
            , l.phone, l.email, l.created_by, l.reported_similar_to,
              EXISTS (SELECT 1 FROM location_source ls WHERE ls.location_id = l.id) AS imported
            FROM location l
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public ProPlaceRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Nouveau lieu en brouillon ; renvoie son id. */
    public Long insertDraft(ProPlaceFields fields, Long createdBy, Long reportedSimilarTo) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO location (name, address, postal_code, city, country_code, latitude, longitude, timezone,
                                      website, email, phone, description, place_type, place_status,
                                      coordinate_precision, created_by, reported_similar_to)
                VALUES (:name, :address, :postalCode, :city, 'FR', :latitude, :longitude, 'Europe/Paris',
                        :website, :email, :phone, :description, :placeType, 'DRAFT', 'EXACT', :createdBy,
                        :reportedSimilarTo)
                """, params(fields).addValue("createdBy", createdBy).addValue("reportedSimilarTo", reportedSimilarTo),
                key, new String[] {"id"});
        return key.getKey().longValue();
    }

    public void update(Long id, ProPlaceFields fields) {
        jdbc.update("""
                UPDATE location SET name = :name, address = :address, postal_code = :postalCode, city = :city,
                       latitude = :latitude, longitude = :longitude, website = :website, email = :email,
                       phone = :phone, description = :description, place_type = :placeType
                WHERE id = :id
                """, params(fields).addValue("id", id));
    }

    /** Brouillon soumis à la modération existante ; {@code false} s'il n'était plus en brouillon. */
    public boolean submit(Long id) {
        return jdbc.update("UPDATE location SET place_status = 'PENDING' WHERE id = :id AND place_status = 'DRAFT'",
                new MapSqlParameterSource("id", id)) == 1;
    }

    public Optional<ManagedPlace> findPlace(Long id) {
        return jdbc.query(MANAGED_SELECT + "WHERE l.id = :id AND l.place_status IS NOT NULL",
                new MapSqlParameterSource("id", id), (row, index) -> toManaged(row)).stream().findFirst();
    }

    /** Lieux dont {@code userId} est gestionnaire actif. */
    public List<ManagedPlace> findManagedBy(Long userId) {
        return jdbc.query(MANAGED_SELECT + """
                JOIN place_manager m ON m.location_id = l.id AND m.revoked_at IS NULL
                WHERE m.user_id = :userId AND l.place_status IS NOT NULL
                ORDER BY l.name, l.id
                """, new MapSqlParameterSource("userId", userId), (row, index) -> toManaged(row));
    }

    /**
     * Lieux existants qui pourraient être celui que le professionnel veut créer : à moins de {@code radiusMeters}
     * de la position, ou dans la même commune avec un nom qui contient l'autre (casse, accents et ponctuation
     * ignorés). Brouillons et lieux refusés exclus. Les plus proches d'abord.
     */
    public List<SimilarPlace> findSimilar(
            String name, Double latitude, Double longitude, String city, double radiusMeters, int limit) {
        boolean hasPosition = latitude != null && longitude != null;
        return jdbc.query("SELECT " + PlaceRepository.PLACE_COLUMNS + """
                , CASE WHEN CAST(:hasPosition AS boolean) AND l.geo_location IS NOT NULL
                       THEN ST_Distance(l.geo_location,
                                        ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography)
                  END AS distance
                FROM location l
                WHERE l.place_status IN ('PENDING', 'PUBLISHED', 'ARCHIVED')
                  AND ((CAST(:hasPosition AS boolean) AND l.geo_location IS NOT NULL
                        AND ST_DWithin(l.geo_location,
                                       ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography, :radius))
                       OR (CAST(:city AS text) IS NOT NULL
                           AND ll_search_normalize(l.city) = ll_search_normalize(:city)
                           AND ll_search_normalize(:name) <> ''
                           AND (ll_search_normalize(l.name) LIKE '%' || ll_search_normalize(:name) || '%'
                                OR ll_search_normalize(:name) LIKE '%' || ll_search_normalize(l.name) || '%')))
                ORDER BY distance NULLS LAST, l.name, l.id
                LIMIT :limit
                """, new MapSqlParameterSource("hasPosition", hasPosition)
                .addValue("latitude", hasPosition ? latitude : 0d).addValue("longitude", hasPosition ? longitude : 0d)
                .addValue("radius", radiusMeters).addValue("city", city).addValue("name", name == null ? "" : name)
                .addValue("limit", limit),
                (row, index) -> new SimilarPlace(
                        PlaceRepository.toPlace(row), row.getObject("distance", Double.class)));
    }

    private static MapSqlParameterSource params(ProPlaceFields fields) {
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
                .addValue("phone", fields.phone())
                .addValue("email", fields.email());
    }

    private static ManagedPlace toManaged(ResultSet row) throws SQLException {
        return new ManagedPlace(PlaceRepository.toPlace(row), row.getString("phone"), row.getString("email"),
                row.getBoolean("imported"), row.getObject("created_by", Long.class),
                row.getObject("reported_similar_to", Long.class));
    }
}
