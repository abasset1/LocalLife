package com.locallife.backend.place.infrastructure;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Droits de gestion des lieux (ADR-0012, table {@code place_manager}) : seule source de vérité de « ce professionnel
 * gère ce lieu ». Plusieurs gestionnaires par lieu (décision D3).
 */
@Repository
public class PlaceManagerRepository {

    /** Gestionnaire actif d'un lieu. */
    public record Manager(Long userId, String email, Long claimId, OffsetDateTime grantedAt) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    public PlaceManagerRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Ouvre le droit ; sans effet s'il est déjà ouvert. */
    public void grant(Long locationId, Long userId, Long claimId, Long grantedBy) {
        jdbc.update("""
                INSERT INTO place_manager (location_id, user_id, claim_id, granted_by)
                VALUES (:locationId, :userId, :claimId, :grantedBy)
                ON CONFLICT (location_id, user_id) WHERE revoked_at IS NULL DO NOTHING
                """, new MapSqlParameterSource("locationId", locationId).addValue("userId", userId)
                .addValue("claimId", claimId).addValue("grantedBy", grantedBy));
    }

    public boolean isManager(Long locationId, Long userId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM place_manager
                               WHERE location_id = :locationId AND user_id = :userId AND revoked_at IS NULL)
                """, new MapSqlParameterSource("locationId", locationId).addValue("userId", userId), Boolean.class));
    }

    public List<Manager> findActive(Long locationId) {
        return jdbc.query("""
                SELECT m.user_id, u.email, m.claim_id, m.granted_at FROM place_manager m
                JOIN users u ON u.id = m.user_id
                WHERE m.location_id = :locationId AND m.revoked_at IS NULL ORDER BY m.granted_at
                """, new MapSqlParameterSource("locationId", locationId), (row, index) -> new Manager(
                        row.getLong("user_id"), row.getString("email"), row.getObject("claim_id", Long.class),
                        row.getObject("granted_at", OffsetDateTime.class)));
    }
}
