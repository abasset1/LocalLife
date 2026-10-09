package com.locallife.backend.push.infrastructure;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Jetons Expo Push des appareils (table {@code user_push_token}, V53). */
@Repository
public class PushTokenRepository {

    private final JdbcTemplate jdbcTemplate;

    public PushTokenRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Enregistre le jeton pour cet utilisateur, en le lui réattribuant s'il appartenait à un autre compte. */
    public void save(String token, Long userId, String platform) {
        jdbcTemplate.update(
                "INSERT INTO user_push_token (token, user_id, platform) VALUES (?, ?, ?) "
                        + "ON CONFLICT (token) DO UPDATE SET user_id = EXCLUDED.user_id, "
                        + "platform = EXCLUDED.platform, last_seen_at = now()",
                token, userId, platform);
    }

    public void deleteByTokenAndUserId(String token, Long userId) {
        jdbcTemplate.update("DELETE FROM user_push_token WHERE token = ? AND user_id = ?", token, userId);
    }

    public void deleteByTokens(List<String> tokens) {
        jdbcTemplate.batchUpdate("DELETE FROM user_push_token WHERE token = ?",
                tokens.stream().map(token -> new Object[] {token}).toList());
    }

    public List<String> findTokensByUserId(Long userId) {
        return jdbcTemplate.queryForList(
                "SELECT token FROM user_push_token WHERE user_id = ? ORDER BY created_at", String.class, userId);
    }

}
