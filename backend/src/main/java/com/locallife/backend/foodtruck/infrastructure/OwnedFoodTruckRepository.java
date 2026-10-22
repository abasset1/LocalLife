package com.locallife.backend.foodtruck.infrastructure;

import com.locallife.backend.foodtruck.domain.FoodTruckStatus;
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
 * Food trucks d'un propriétaire professionnel (PRO-01 lot C) et leur suspension par un administrateur. Le profil
 * seul : nom, description, cuisine, contact, statut ; la position vit dans les présences (ADR-0013).
 */
@Repository
public class OwnedFoodTruckRepository {

    /** Profil saisi par le propriétaire. */
    public record ProfileFields(String name, String description, String category, String contact) {
    }

    /** Food truck avec son propriétaire et son état de suspension. */
    public record OwnedFoodTruck(
            Long id, String name, String description, String category, String contact, FoodTruckStatus status,
            Long ownerId, String ownerEmail, OffsetDateTime updatedAt, OffsetDateTime suspendedByAdminAt,
            OffsetDateTime approvedAt, String reviewReason, OffsetDateTime suspendedForAccessAt) {
    }

    private static final String SELECT = """
            SELECT ft.id, ft.name, ft.description, ft.category, ft.contact, ft.status, ft.owner_id, u.email,
                   ft.updated_at, ft.suspended_by_admin_at, ft.approved_at, ft.review_reason,
                   ft.suspended_for_access_at
            FROM food_truck ft LEFT JOIN users u ON u.id = ft.owner_id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public OwnedFoodTruckRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Long insert(Long ownerId, ProfileFields fields, FoodTruckStatus status) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO food_truck (name, description, category, contact, status, owner_id, updated_at)
                VALUES (:name, :description, :category, :contact, :status, :ownerId, now())
                """, params(fields).addValue("status", status.name()).addValue("ownerId", ownerId), key,
                new String[] {"id"});
        return key.getKey().longValue();
    }

    public void update(Long id, ProfileFields fields) {
        jdbc.update("""
                UPDATE food_truck SET name = :name, description = :description, category = :category,
                       contact = :contact, updated_at = now()
                WHERE id = :id
                """, params(fields).addValue("id", id));
    }

    public Optional<OwnedFoodTruck> findById(Long id) {
        return jdbc.query(SELECT + "WHERE ft.id = :id", new MapSqlParameterSource("id", id),
                (row, index) -> toFoodTruck(row)).stream().findFirst();
    }

    public List<OwnedFoodTruck> findByOwner(Long ownerId) {
        return jdbc.query(SELECT + "WHERE ft.owner_id = :ownerId ORDER BY ft.name, ft.id",
                new MapSqlParameterSource("ownerId", ownerId), (row, index) -> toFoodTruck(row));
    }

    /** Liste d'administration, éventuellement limitée à certains statuts. */
    public List<OwnedFoodTruck> findAll(Collection<FoodTruckStatus> statuses, int limit, int offset) {
        String filter = statuses.isEmpty() ? "" : "WHERE ft.status IN (:statuses) ";
        return jdbc.query(SELECT + filter + "ORDER BY ft.name, ft.id LIMIT :limit OFFSET :offset",
                new MapSqlParameterSource("statuses", statuses.isEmpty() ? List.of("")
                        : statuses.stream().map(Enum::name).toList())
                        .addValue("limit", limit).addValue("offset", offset),
                (row, index) -> toFoodTruck(row));
    }

    /**
     * Change le statut s'il est toujours l'un de {@code from} ; un food truck suspendu par un administrateur n'est
     * jamais republié par cette voie. Renvoie {@code false} si rien n'a changé.
     */
    public boolean transition(Long id, Collection<FoodTruckStatus> from, FoodTruckStatus to) {
        return jdbc.update("""
                UPDATE food_truck SET status = :to, updated_at = now()
                WHERE id = :id AND status IN (:from) AND (suspended_by_admin_at IS NULL OR :to <> 'PUBLISHED')
                """, new MapSqlParameterSource("id", id).addValue("to", to.name())
                .addValue("from", from.stream().map(Enum::name).toList())) == 1;
    }

    /** Suspension par un administrateur : depuis tout statut sauf archivé. */
    public boolean suspendByAdmin(Long id, Long adminId) {
        return jdbc.update("""
                UPDATE food_truck SET status = 'SUSPENDED', suspended_by_admin_at = now(), suspended_by = :adminId,
                       updated_at = now()
                WHERE id = :id AND status <> 'ARCHIVED'
                """, new MapSqlParameterSource("id", id).addValue("adminId", adminId)) == 1;
    }

    /**
     * Validation de la première publication : {@code PENDING} → {@code PUBLISHED} (validé) ou {@code REJECTED}
     * (motif). Renvoie {@code false} si le food truck n'attendait pas de validation.
     */
    public boolean review(Long id, boolean approve, Long adminId, String reason) {
        return jdbc.update("""
                UPDATE food_truck
                SET status = CASE WHEN :approve THEN 'PUBLISHED' ELSE 'REJECTED' END,
                    approved_at = CASE WHEN :approve THEN now() ELSE approved_at END,
                    approved_by = CASE WHEN :approve THEN :adminId ELSE approved_by END,
                    review_reason = :reason, updated_at = now()
                WHERE id = :id AND status = 'PENDING'
                """, new MapSqlParameterSource("id", id).addValue("approve", approve).addValue("adminId", adminId)
                .addValue("reason", reason)) == 1;
    }

    /**
     * Levée de la suspension par un administrateur : le food truck est de nouveau publié, sauf si son propriétaire
     * a perdu son accès professionnel entre-temps (il reste alors suspendu jusqu'au retour de cet accès).
     */
    public boolean reinstate(Long id) {
        return jdbc.update("""
                UPDATE food_truck
                SET status = CASE WHEN suspended_for_access_at IS NULL THEN 'PUBLISHED' ELSE status END,
                    suspended_by_admin_at = NULL, suspended_by = NULL, updated_at = now()
                WHERE id = :id AND status = 'SUSPENDED' AND suspended_by_admin_at IS NOT NULL
                """, new MapSqlParameterSource("id", id)) == 1;
    }

    private static MapSqlParameterSource params(ProfileFields fields) {
        return new MapSqlParameterSource("name", fields.name()).addValue("description", fields.description())
                .addValue("category", fields.category()).addValue("contact", fields.contact());
    }

    private static OwnedFoodTruck toFoodTruck(ResultSet row) throws SQLException {
        return new OwnedFoodTruck(row.getLong("id"), row.getString("name"), row.getString("description"),
                row.getString("category"), row.getString("contact"), FoodTruckStatus.valueOf(row.getString("status")),
                row.getObject("owner_id", Long.class), row.getString("email"),
                row.getObject("updated_at", OffsetDateTime.class),
                row.getObject("suspended_by_admin_at", OffsetDateTime.class),
                row.getObject("approved_at", OffsetDateTime.class), row.getString("review_reason"),
                row.getObject("suspended_for_access_at", OffsetDateTime.class));
    }
}
