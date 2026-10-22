package com.locallife.backend.pro.infrastructure;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Contenus professionnels d'un compte quand son accès prend fin, puis revient (décision du 22/10/2026) : food
 * trucks publiés suspendus et republiés, droits de gestion révoqués et rétablis, demandes en cours retirées.
 */
@Repository
public class ProContentRepository {

    static final String ACCESS_ENDED = "Accès professionnel terminé";

    /** Bilan d'un retrait ou d'un rétablissement, pour le journal. */
    public record Outcome(int foodTrucks, int managedPlaces, int claims, int placeChanges) {

        public boolean isEmpty() {
            return foodTrucks == 0 && managedPlaces == 0 && claims == 0 && placeChanges == 0;
        }
    }

    private final NamedParameterJdbcTemplate jdbc;

    public ProContentRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Fin de l'accès : food trucks publiés suspendus (republiés au retour), food trucks en attente de validation
     * ramenés en brouillon, droits de gestion révoqués (rétablis au retour), demandes de gestion et propositions de
     * modification en cours retirées (définitif).
     */
    public Outcome withdraw(Long userId) {
        MapSqlParameterSource params = new MapSqlParameterSource("userId", userId).addValue("reason", ACCESS_ENDED);
        int foodTrucks = jdbc.update("""
                UPDATE food_truck SET status = 'SUSPENDED', suspended_for_access_at = now(), updated_at = now()
                WHERE owner_id = :userId AND status = 'PUBLISHED'
                """, params);
        foodTrucks += jdbc.update("""
                UPDATE food_truck SET status = 'DRAFT', updated_at = now()
                WHERE owner_id = :userId AND status = 'PENDING'
                """, params);
        int places = jdbc.update("""
                UPDATE place_manager SET revoked_at = now(), revoked_reason = 'ACCESS_LOST'
                WHERE user_id = :userId AND revoked_at IS NULL
                """, params);
        int claims = jdbc.update("""
                WITH active AS (
                    SELECT id, status FROM place_claim
                    WHERE requester_id = :userId AND status IN ('DRAFT', 'SUBMITTED', 'IN_REVIEW', 'INFO_REQUESTED')
                    FOR UPDATE
                ), withdrawn AS (
                    UPDATE place_claim c SET status = 'WITHDRAWN', updated_at = now()
                    FROM active a WHERE c.id = a.id
                    RETURNING c.id, a.status AS from_status
                )
                INSERT INTO place_claim_event (claim_id, actor_id, from_status, to_status, reason)
                SELECT id, NULL, from_status, 'WITHDRAWN', :reason FROM withdrawn
                """, params);
        int changes = withdrawPlaceChanges(userId, null);
        return new Outcome(foodTrucks, places, claims, changes);
    }

    /**
     * Retour de l'accès : ce que {@link #withdraw} avait suspendu ou révoqué revient (sauf suspension par l'équipe).
     */
    public Outcome restore(Long userId) {
        MapSqlParameterSource params = new MapSqlParameterSource("userId", userId);
        int foodTrucks = jdbc.update("""
                UPDATE food_truck
                SET status = CASE WHEN suspended_by_admin_at IS NULL THEN 'PUBLISHED' ELSE status END,
                    suspended_for_access_at = NULL, updated_at = now()
                WHERE owner_id = :userId AND status = 'SUSPENDED' AND suspended_for_access_at IS NOT NULL
                """, params);
        int places = jdbc.update("""
                UPDATE place_manager m SET revoked_at = NULL, revoked_reason = NULL
                WHERE m.id IN (
                    SELECT DISTINCT ON (location_id) id FROM place_manager
                    WHERE user_id = :userId AND revoked_reason = 'ACCESS_LOST'
                    ORDER BY location_id, revoked_at DESC, id DESC)
                  AND NOT EXISTS (SELECT 1 FROM place_manager active
                                  WHERE active.location_id = m.location_id AND active.user_id = m.user_id
                                    AND active.revoked_at IS NULL)
                """, params);
        return new Outcome(foodTrucks, places, 0, 0);
    }

    /**
     * Retire les propositions de modification en attente d'un gestionnaire, pour un lieu ou ({@code locationId}
     * nul) pour tous.
     */
    public int withdrawPlaceChanges(Long userId, Long locationId) {
        return jdbc.update("""
                UPDATE place_change_request SET status = 'WITHDRAWN', decided_at = now()
                WHERE requester_id = :userId AND status = 'PENDING'
                  AND (CAST(:locationId AS bigint) IS NULL OR location_id = :locationId)
                """, new MapSqlParameterSource("userId", userId).addValue("locationId", locationId));
    }
}
