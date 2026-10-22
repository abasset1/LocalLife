package com.locallife.backend.auth.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Limite des tentatives de connexion par mot de passe (décision du 22/10/2026) : après {@value #MAX_FAILURES}
 * échecs en {@value #WINDOW_MINUTES} minutes pour une même adresse, la connexion est refusée (429) jusqu'à ce que
 * la fenêtre glisse. Vaut pour tous les comptes, administrateurs compris. Adresse stockée hachée
 * ({@code login_failure}, V73), échecs effacés à la connexion réussie, lignes anciennes purgées au fil de l'eau.
 */
@Service
public class LoginThrottle {

    static final int MAX_FAILURES = 5;
    static final int WINDOW_MINUTES = 15;

    private final JdbcTemplate jdbcTemplate;

    public LoginThrottle(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** @throws TooManyLoginAttemptsException trop d'échecs récents pour cette adresse */
    public void checkAllowed(String email) {
        Integer failures = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM login_failure
                WHERE email_hash = ? AND occurred_at > now() - make_interval(mins => ?)
                """, Integer.class, hash(email), WINDOW_MINUTES);
        if (failures != null && failures >= MAX_FAILURES) {
            throw new TooManyLoginAttemptsException();
        }
    }

    /** Enregistré même si la connexion échoue ensuite par une exception (transaction indépendante). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String email) {
        jdbcTemplate.update("INSERT INTO login_failure (email_hash) VALUES (?)", hash(email));
        jdbcTemplate.update("DELETE FROM login_failure WHERE occurred_at < now() - interval '1 day'");
    }

    public void clear(String email) {
        jdbcTemplate.update("DELETE FROM login_failure WHERE email_hash = ?", hash(email));
    }

    private static String hash(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponible", exception);
        }
    }
}
