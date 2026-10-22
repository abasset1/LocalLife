package com.locallife.backend.gdpr.application;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Droit d'accès et à la portabilité (RGPD, art. 15 et 20 ; décision du 22/10/2026) : toutes les données d'un compte
 * dans un document JSON lisible par une machine. Jamais de secret (empreinte du mot de passe, jetons, lien de
 * désinscription) ni l'adresse d'un autre compte (administrateurs auteurs d'une décision).
 */
@Service
public class DataExportService {

    private final JdbcTemplate jdbcTemplate;

    public DataExportService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> export(Long userId) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("exportedAt", OffsetDateTime.now().toString());
        document.put("account", one("""
                SELECT id, username, email, role, email_verified, account_status, status_reason, created_at,
                       terms_accepted_at, terms_version, last_active_at
                FROM users WHERE id = ?""", userId));
        document.put("linkedAccounts", list(
                "SELECT provider, created_at FROM user_identities WHERE user_id = ?", userId));
        document.put("preferences", one("""
                SELECT default_city, default_latitude, default_longitude, default_radius_km, updated_at
                FROM user_preferences WHERE user_id = ?""", userId));
        document.put("preferredCategories", jdbcTemplate.queryForList(
                "SELECT category_code FROM user_preference_category WHERE user_id = ? ORDER BY category_code",
                String.class, userId));
        document.put("alerts", list("""
                SELECT a.id, a.city, a.latitude, a.longitude, a.radius_km, a.notify_email, a.notify_push,
                       a.include_food_trucks, a.created_at,
                       (SELECT string_agg(c.category_code, ',' ORDER BY c.category_code) FROM activity_alert_category c
                        WHERE c.alert_id = a.id) AS categories
                FROM activity_alert a WHERE a.user_id = ? ORDER BY a.id""", userId));
        document.put("favorites", list("""
                SELECT l.activity_id, a.title, l.created_at FROM activity_likes l
                JOIN activity a ON a.id = l.activity_id WHERE l.user_id = ? ORDER BY l.created_at""", userId));
        document.put("contributions", list("""
                SELECT id, title, description, address, city, start_date, end_date, status, created_at, published_at,
                       moderation_reason
                FROM activity WHERE created_by = ? AND status <> 'DELETED' ORDER BY id""", userId));
        document.put("pushDevices", list(
                "SELECT platform, created_at, last_seen_at FROM user_push_token WHERE user_id = ?", userId));
        document.put("subscriptions", list("""
                SELECT s.id, s.plan, s.status, s.current_period_start, s.current_period_end, s.cancel_at_period_end,
                       s.provider, s.decision_reason, s.created_at, s.updated_at,
                       COALESCE((SELECT json_agg(json_build_object('from', e.from_status, 'to', e.to_status,
                                                                    'reason', e.reason, 'at', e.occurred_at)
                                                 ORDER BY e.id)
                                 FROM subscription_event e WHERE e.subscription_id = s.id), '[]'::json)::text AS events
                FROM subscription s WHERE s.user_id = ? ORDER BY s.id""", userId));
        document.put("placeClaims", list("""
                SELECT c.id, c.location_id, l.name AS place, c.status, c.relationship, c.evidence, c.decision_reason,
                       c.created_at, c.submitted_at, c.decided_at
                FROM place_claim c JOIN location l ON l.id = c.location_id WHERE c.requester_id = ? ORDER BY c.id""",
                userId));
        document.put("managedPlaces", list("""
                SELECT m.location_id, l.name AS place, m.granted_at, m.revoked_at, m.revoked_reason, m.revoke_comment
                FROM place_manager m JOIN location l ON l.id = m.location_id WHERE m.user_id = ? ORDER BY m.id""",
                userId));
        document.put("placeChangeProposals", list("""
                SELECT id, location_id, status, address, postal_code, city, description, website, phone, email,
                       decision_reason, created_at, decided_at
                FROM place_change_request WHERE requester_id = ? ORDER BY id""", userId));
        document.put("foodTrucks", list("""
                SELECT id, name, description, category, contact, status, approved_at, review_reason, updated_at
                FROM food_truck WHERE owner_id = ? ORDER BY id""", userId));
        document.put("accountHistory", list("""
                SELECT occurred_at, action, old_value, new_value, reason, outcome, detail
                FROM account_audit_event WHERE target_user_id = ? ORDER BY occurred_at, id""", userId));
        return document;
    }

    private Map<String, Object> one(String sql, Long userId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, userId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private List<Map<String, Object>> list(String sql, Long userId) {
        return jdbcTemplate.queryForList(sql, userId);
    }
}
