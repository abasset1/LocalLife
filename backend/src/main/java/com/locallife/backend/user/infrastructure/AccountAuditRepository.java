package com.locallife.backend.user.infrastructure;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Journal d'audit des actions sur les comptes ({@code account_audit_event}, V69) : ajout et lecture seulement. La
 * base refuse toute modification ou suppression d'une entrée (déclencheur V69).
 */
@Repository
public class AccountAuditRepository {

    private static final int MAX_TEXT_LENGTH = 500;

    private final JdbcTemplate jdbcTemplate;

    public AccountAuditRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Une entrée. Dates en UTC. Valeurs : rôles ou statuts, jamais de secret. */
    public record Event(
            Long id,
            LocalDateTime occurredAt,
            Long actorUserId,
            String actorEmail,
            Long targetUserId,
            String action,
            String oldValue,
            String newValue,
            String reason,
            String outcome,
            String detail) {
    }

    public void append(Event event) {
        jdbcTemplate.update("""
                INSERT INTO account_audit_event (occurred_at, actor_user_id, actor_email, target_user_id, action,
                    old_value, new_value, reason, outcome, detail)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, Timestamp.valueOf(event.occurredAt()), event.actorUserId(), truncate(event.actorEmail()),
                event.targetUserId(), event.action(), truncate(event.oldValue()), truncate(event.newValue()),
                truncate(event.reason()), event.outcome(), truncate(event.detail()));
    }

    /** Entrées les plus récentes d'abord ; {@code targetUserId} {@code null} : tout le journal. */
    public List<Event> find(Long targetUserId, int offset, int limit) {
        List<Object> arguments = new ArrayList<>();
        String where = "";
        if (targetUserId != null) {
            where = " WHERE target_user_id = ?";
            arguments.add(targetUserId);
        }
        arguments.add(limit);
        arguments.add(offset);
        return jdbcTemplate.query("SELECT * FROM account_audit_event" + where
                + " ORDER BY occurred_at DESC, id DESC LIMIT ? OFFSET ?", EVENT, arguments.toArray());
    }

    public long count(Long targetUserId) {
        Long count = targetUserId == null
                ? jdbcTemplate.queryForObject("SELECT count(*) FROM account_audit_event", Long.class)
                : jdbcTemplate.queryForObject("SELECT count(*) FROM account_audit_event WHERE target_user_id = ?",
                        Long.class, targetUserId);
        return count == null ? 0 : count;
    }

    private static final RowMapper<Event> EVENT = (resultSet, rowNumber) -> new Event(
            resultSet.getLong("id"),
            resultSet.getTimestamp("occurred_at").toLocalDateTime(),
            (Long) resultSet.getObject("actor_user_id"),
            resultSet.getString("actor_email"),
            (Long) resultSet.getObject("target_user_id"),
            resultSet.getString("action"),
            resultSet.getString("old_value"),
            resultSet.getString("new_value"),
            resultSet.getString("reason"),
            resultSet.getString("outcome"),
            resultSet.getString("detail"));

    private static String truncate(String value) {
        return value == null || value.length() <= MAX_TEXT_LENGTH ? value : value.substring(0, MAX_TEXT_LENGTH);
    }
}
