package com.locallife.backend.admin.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Journal des actions d'administration ({@code admin_action_log}, V73) : en ajout seul, la base refuse toute
 * modification. L'adresse de l'auteur est figée au moment de l'action.
 */
@Repository
public class AdminActionLogRepository {

    /** Une action ; {@code before}/{@code after} : JSON, ou {@code null}. */
    public record Entry(
            Long id, OffsetDateTime occurredAt, Long actorId, String actorEmail, String action, String targetType,
            String targetId, String summary, String before, String after, String reason) {
    }

    /** Filtres de consultation ; {@code null} = pas de filtre. */
    public record Filter(String action, String targetType, String targetId, Long actorId) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    public AdminActionLogRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void append(Long actorId, String action, String targetType, String targetId, String summary,
            String beforeJson, String afterJson, String reason) {
        jdbc.update("""
                INSERT INTO admin_action_log (actor_id, actor_email, action, target_type, target_id, summary,
                                              before_value, after_value, reason)
                VALUES (:actorId, (SELECT email FROM users WHERE id = :actorId), :action, :targetType, :targetId,
                        :summary, CAST(:before AS jsonb), CAST(:after AS jsonb), :reason)
                """, new MapSqlParameterSource("actorId", actorId).addValue("action", action)
                .addValue("targetType", targetType).addValue("targetId", targetId).addValue("summary", summary)
                .addValue("before", beforeJson).addValue("after", afterJson).addValue("reason", reason));
    }

    public List<Entry> find(Filter filter, int offset, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource("offset", offset).addValue("limit", limit);
        return jdbc.query("SELECT * FROM admin_action_log" + where(filter, params)
                + " ORDER BY occurred_at DESC, id DESC LIMIT :limit OFFSET :offset", params,
                (row, index) -> toEntry(row));
    }

    public long count(Filter filter) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        Long count = jdbc.queryForObject("SELECT count(*) FROM admin_action_log" + where(filter, params), params,
                Long.class);
        return count == null ? 0 : count;
    }

    private static String where(Filter filter, MapSqlParameterSource params) {
        List<String> conditions = new ArrayList<>();
        if (filter.action() != null) {
            conditions.add("action = :action");
            params.addValue("action", filter.action());
        }
        if (filter.targetType() != null) {
            conditions.add("target_type = :targetType");
            params.addValue("targetType", filter.targetType());
        }
        if (filter.targetId() != null) {
            conditions.add("target_id = :targetId");
            params.addValue("targetId", filter.targetId());
        }
        if (filter.actorId() != null) {
            conditions.add("actor_id = :actorId");
            params.addValue("actorId", filter.actorId());
        }
        return conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
    }

    private static Entry toEntry(ResultSet row) throws SQLException {
        return new Entry(row.getLong("id"), row.getObject("occurred_at", OffsetDateTime.class),
                row.getObject("actor_id", Long.class), row.getString("actor_email"), row.getString("action"),
                row.getString("target_type"), row.getString("target_id"), row.getString("summary"),
                row.getString("before_value"), row.getString("after_value"), row.getString("reason"));
    }
}
