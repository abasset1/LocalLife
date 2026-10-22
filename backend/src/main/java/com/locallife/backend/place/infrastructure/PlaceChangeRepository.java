package com.locallife.backend.place.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/**
 * Modifications proposées par un gestionnaire sur un lieu publié (décision du 22/10/2026, table
 * {@code place_change_request}) : appliquées à la fiche seulement après validation par l'équipe.
 */
@Repository
public class PlaceChangeRepository {

    /** Statut d'une proposition. */
    public enum ChangeStatus {
        PENDING, APPROVED, REJECTED, WITHDRAWN
    }

    /** Champs modifiables par un gestionnaire : adresse, position, description et contacts. */
    public record ChangeFields(
            String address, String postalCode, String city, double latitude, double longitude, String description,
            String website, String phone, String email) {
    }

    /** Proposition avec le nom du lieu et l'adresse du demandeur. */
    public record PlaceChange(
            Long id, Long locationId, String placeName, Long requesterId, String requesterEmail, ChangeStatus status,
            ChangeFields fields, String decisionReason, Long moderatorId, OffsetDateTime createdAt,
            OffsetDateTime decidedAt) {
    }

    private static final String SELECT = """
            SELECT c.*, l.name AS place_name, u.email AS requester_email
            FROM place_change_request c
            JOIN location l ON l.id = c.location_id
            LEFT JOIN users u ON u.id = c.requester_id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public PlaceChangeRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Proposition en attente de ce gestionnaire pour ce lieu, remplacée si elle existe ; renvoie son id. */
    public Long upsertPending(Long locationId, Long requesterId, ChangeFields fields) {
        MapSqlParameterSource params = params(fields).addValue("locationId", locationId)
                .addValue("requesterId", requesterId);
        List<Long> updated = jdbc.queryForList("""
                UPDATE place_change_request SET address = :address, postal_code = :postalCode, city = :city,
                       latitude = :latitude, longitude = :longitude, description = :description, website = :website,
                       phone = :phone, email = :email, created_at = now()
                WHERE location_id = :locationId AND requester_id = :requesterId AND status = 'PENDING'
                RETURNING id
                """, params, Long.class);
        if (!updated.isEmpty()) {
            return updated.get(0);
        }
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO place_change_request (location_id, requester_id, status, address, postal_code, city,
                                                  latitude, longitude, description, website, phone, email)
                VALUES (:locationId, :requesterId, 'PENDING', :address, :postalCode, :city, :latitude, :longitude,
                        :description, :website, :phone, :email)
                """, params, key, new String[] {"id"});
        return key.getKey().longValue();
    }

    public Optional<PlaceChange> findById(Long id) {
        return jdbc.query(SELECT + "WHERE c.id = :id", new MapSqlParameterSource("id", id),
                (row, index) -> toChange(row)).stream().findFirst();
    }

    /** Propositions d'un gestionnaire pour un lieu, les plus récentes d'abord. */
    public List<PlaceChange> findForPlace(Long locationId, Long requesterId) {
        return jdbc.query(SELECT + """
                WHERE c.location_id = :locationId AND c.requester_id = :requesterId
                ORDER BY c.created_at DESC, c.id DESC
                """, new MapSqlParameterSource("locationId", locationId).addValue("requesterId", requesterId),
                (row, index) -> toChange(row));
    }

    /** File de modération, les plus anciennes d'abord. */
    public List<PlaceChange> findByStatuses(Collection<ChangeStatus> statuses, int limit, int offset) {
        return jdbc.query(SELECT + """
                WHERE c.status IN (:statuses) ORDER BY c.created_at, c.id LIMIT :limit OFFSET :offset
                """, new MapSqlParameterSource("statuses", statuses.stream().map(Enum::name).toList())
                .addValue("limit", limit).addValue("offset", offset), (row, index) -> toChange(row));
    }

    /** Décision ou retrait d'une proposition en attente ; {@code false} si elle n'était plus en attente. */
    public boolean close(Long id, ChangeStatus to, Long moderatorId, String reason) {
        return jdbc.update("""
                UPDATE place_change_request SET status = :to, moderator_id = :moderatorId, decision_reason = :reason,
                       decided_at = now()
                WHERE id = :id AND status = 'PENDING'
                """, new MapSqlParameterSource("id", id).addValue("to", to.name()).addValue("moderatorId", moderatorId)
                .addValue("reason", reason)) == 1;
    }

    /**
     * Applique une proposition validée à la fiche et la marque modifiée par un gestionnaire : l'import ne réécrit
     * plus ces champs ({@code location.manager_edited_at}). La position saisie ou géocodée devient exacte.
     */
    public void applyToPlace(Long locationId, ChangeFields fields) {
        jdbc.update("""
                UPDATE location SET address = :address, postal_code = :postalCode, city = :city,
                       latitude = :latitude, longitude = :longitude, description = :description, website = :website,
                       phone = :phone, email = :email, coordinate_precision = 'EXACT', manager_edited_at = now()
                WHERE id = :locationId
                """, params(fields).addValue("locationId", locationId));
    }

    private static MapSqlParameterSource params(ChangeFields fields) {
        return new MapSqlParameterSource()
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

    private static PlaceChange toChange(ResultSet row) throws SQLException {
        return new PlaceChange(row.getLong("id"), row.getLong("location_id"), row.getString("place_name"),
                row.getObject("requester_id", Long.class), row.getString("requester_email"),
                ChangeStatus.valueOf(row.getString("status")),
                new ChangeFields(row.getString("address"), row.getString("postal_code"), row.getString("city"),
                        row.getDouble("latitude"), row.getDouble("longitude"), row.getString("description"),
                        row.getString("website"), row.getString("phone"), row.getString("email")),
                row.getString("decision_reason"), row.getObject("moderator_id", Long.class),
                row.getObject("created_at", OffsetDateTime.class), row.getObject("decided_at", OffsetDateTime.class));
    }
}
