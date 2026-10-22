package com.locallife.backend.common;

import com.locallife.backend.subscription.application.SubscriptionService;
import com.locallife.backend.subscription.domain.Plan;
import com.locallife.backend.user.domain.Role;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Comptes réels pour les jetons des tests d'intégration. Depuis le sprint « Rôles et comptes », {@code JwtFilter}
 * relit le rôle et le statut en base : un jeton fabriqué pour un identifiant qui n'existe pas (l'ancien
 * {@code userId = 1}) est refusé (401), et le rôle inscrit dans le jeton ne compte plus.
 *
 * <p>Un compte partagé par rôle ({@code it-admin@locallife.test}...), créé au premier besoin et jamais modifié ni
 * supprimé : les tests qui changent un rôle ou un statut créent leurs propres comptes.
 */
@Component
public class TestAccounts {

    private final JdbcTemplate jdbcTemplate;
    private final String jwtSecret;
    private final SubscriptionService subscriptionService;

    public TestAccounts(
            JdbcTemplate jdbcTemplate, @Value("${jwt.secret}") String jwtSecret,
            SubscriptionService subscriptionService) {
        this.jdbcTemplate = jdbcTemplate;
        this.jwtSecret = jwtSecret;
        this.subscriptionService = subscriptionService;
    }

    /**
     * Donne l'offre Premium (activation immédiate, paiement simulé) au compte de cette adresse : depuis le
     * 21/10/2026, proposer une activité exige Premium ou Pro. Sans effet si le compte l'a déjà.
     */
    public void grantPremium(String email) {
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        grantPremium(userId);
    }

    public void grantPremium(Long userId) {
        if (subscriptionService.effectivePlan(userId) != Plan.PREMIUM) {
            subscriptionService.subscribe(userId, Plan.PREMIUM);
        }
    }

    /** Identifiant du compte partagé de ce rôle, créé s'il n'existe pas encore. */
    public synchronized Long sharedAccount(Role role) {
        String email = "it-" + role.name().toLowerCase() + "@locallife.test";
        jdbcTemplate.update("""
                INSERT INTO users (username, email, password_hash, role, created_at, email_verified)
                SELECT ?, ?, '', ?, now(), TRUE
                WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = ?)
                """, "it-" + role.name().toLowerCase(), email, role.name(), email);
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    /** {@code "Bearer …"} d'un compte partagé de ce rôle. */
    public String bearer(Role role) {
        return "Bearer " + token(sharedAccount(role), role);
    }

    /** Jeton valide une heure pour ce compte ; le rôle n'y est qu'indicatif (relu en base). */
    public String token(Long userId, Role role) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .claim("userId", userId)
                .claim("email", "it-account-" + userId + "@locallife.test")
                .claim("role", role.name())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3_600_000L))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }
}
