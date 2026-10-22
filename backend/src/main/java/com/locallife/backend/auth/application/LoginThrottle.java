package com.locallife.backend.auth.application;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Limite des tentatives de connexion par mot de passe (décision du 22/10/2026) : après {@value #MAX_FAILURES}
 * échecs en {@value #WINDOW_MINUTES} minutes pour une même adresse, la connexion est refusée (429) jusqu'à ce que
 * la fenêtre glisse. Vaut pour tous les comptes, administrateurs compris. Adresse jamais stockée en clair
 * ({@code login_failure}, V73) : empreinte HMAC-SHA256 avec la clé du serveur, qu'on ne peut pas retrouver en
 * essayant des adresses sans cette clé (RGPD, 22/10/2026). Échecs effacés à la connexion réussie, lignes de plus d'un
 * jour purgées au fil de l'eau.
 */
@Service
public class LoginThrottle {

    static final int MAX_FAILURES = 5;
    static final int WINDOW_MINUTES = 15;

    private final JdbcTemplate jdbcTemplate;
    private final byte[] key;

    public LoginThrottle(JdbcTemplate jdbcTemplate, @Value("${jwt.secret}") String secret) {
        this.jdbcTemplate = jdbcTemplate;
        this.key = ("login-failure:" + secret).getBytes(StandardCharsets.UTF_8);
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

    private String hash(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256 indisponible", exception);
        }
    }
}
