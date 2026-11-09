package com.locallife.backend.auth.infrastructure;

import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Liste des jetons révoqués par leur {@code jti} (identifiant unique de session), {@code revoked_token} (V90).
 * Sert à fermer UNE session côté serveur à la déconnexion, sans attendre l'expiration du jeton ni toucher aux
 * autres sessions de l'utilisateur (contrairement à {@code users.tokens_valid_after}, qui les ferme toutes).
 *
 * <p>Seuls les jetons révoqués y figurent, et seulement jusqu'à leur expiration naturelle : la table reste petite.
 */
@Repository
public class TokenRevocationRepository {

    private final JdbcTemplate jdbcTemplate;

    public TokenRevocationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Révoque le jeton {@code jti} jusqu'à son expiration {@code expiresAt}. Idempotent (une même session peut
     * être « déconnectée » plusieurs fois). Purge au passage les révocations déjà expirées (inutiles : ces jetons
     * sont de toute façon refusés comme expirés).
     */
    public void revoke(String jti, Instant expiresAt) {
        if (jti == null || jti.isBlank() || expiresAt == null) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO revoked_token (jti, expires_at) VALUES (?, ?)
                ON CONFLICT (jti) DO NOTHING
                """, jti, Timestamp.from(expiresAt));
        jdbcTemplate.update("DELETE FROM revoked_token WHERE expires_at < now()");
    }

    /** Vrai si ce {@code jti} a été révoqué (et n'est pas encore purgé). */
    public boolean isRevoked(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM revoked_token WHERE jti = ?", Integer.class, jti);
        return count != null && count > 0;
    }
}
