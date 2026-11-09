package com.locallife.backend.auth.application;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Limite des tentatives de connexion par mot de passe (décision du 22/10/2026 ; limite par IP ajoutée le
 * 06/11/2026).
 *
 * <p>Deux limites complémentaires, sur une fenêtre glissante de {@value #WINDOW_MINUTES} minutes :
 * <ul>
 *   <li><b>par adresse e-mail</b> : après {@value #MAX_FAILURES} échecs pour une même adresse, la connexion est
 *       refusée (429). Protège un compte précis du forçage de son mot de passe.</li>
 *   <li><b>par IP cliente</b> : après {@value #MAX_IP_FAILURES} échecs depuis une même IP, toutes adresses
 *       confondues, la connexion est refusée (429). Protège contre le « credential stuffing » (une tentative sur
 *       chacune de très nombreuses adresses, où la limite par e-mail ne se déclenche jamais). Le seuil est plus
 *       élevé que par e-mail pour tolérer plusieurs personnes derrière une même IP (bureau, NAT), mais reste bien
 *       en-dessous du volume d'une attaque automatisée. Ajustable selon l'exposition.</li>
 * </ul>
 * Vaut pour tous les comptes, administrateurs compris. Ni l'adresse e-mail ni l'IP ne sont stockées en clair
 * ({@code login_failure}, V73/V87) : seulement leur empreinte HMAC-SHA256 avec la clé du serveur, qu'on ne peut
 * pas inverser sans cette clé (RGPD). Les échecs d'une adresse sont effacés à sa connexion réussie ; ceux d'une IP
 * ne le sont pas (une connexion réussie sur un compte ne doit pas remettre à zéro le compteur d'une attaque qui
 * balaie d'autres comptes). Les lignes de plus d'un jour sont purgées au fil de l'eau.
 *
 * <p>La connexion depuis la boucle locale ({@code 127.0.0.1}, {@code ::1}) ou sans IP exploitable n'est pas
 * limitée par IP : ce sont les appels internes, les sondes de santé et les tests, jamais le client visé par le
 * « credential stuffing ». En production, l'IP cliente réelle provient de l'en-tête {@code X-Forwarded-For} posé
 * par le proxy inverse (voir {@code server.forward-headers-strategy}), pas de la boucle locale.
 */
@Service
public class LoginThrottle {

    static final int MAX_FAILURES = 5;
    static final int MAX_IP_FAILURES = 30;
    static final int WINDOW_MINUTES = 15;

    /** IP jamais limitées : boucle locale et adresse non spécifiée (appels internes, sondes, tests). */
    private static final Set<String> UNLIMITED_IPS = Set.of("127.0.0.1", "0:0:0:0:0:0:0:1", "::1", "0.0.0.0");

    private final JdbcTemplate jdbcTemplate;
    private final byte[] key;

    public LoginThrottle(JdbcTemplate jdbcTemplate, @Value("${jwt.secret}") String secret) {
        this.jdbcTemplate = jdbcTemplate;
        this.key = ("login-failure:" + secret).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * @throws TooManyLoginAttemptsException trop d'échecs récents pour cette adresse, ou depuis cette IP
     */
    public void checkAllowed(String email, String clientIp) {
        Integer byEmail = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM login_failure
                WHERE email_hash = ? AND occurred_at > now() - make_interval(mins => ?)
                """, Integer.class, hash(email), WINDOW_MINUTES);
        if (byEmail != null && byEmail >= MAX_FAILURES) {
            throw new TooManyLoginAttemptsException();
        }

        String ipHash = throttledIpHash(clientIp);
        if (ipHash == null) {
            return;
        }
        Integer byIp = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM login_failure
                WHERE ip_hash = ? AND occurred_at > now() - make_interval(mins => ?)
                """, Integer.class, ipHash, WINDOW_MINUTES);
        if (byIp != null && byIp >= MAX_IP_FAILURES) {
            throw new TooManyLoginAttemptsException();
        }
    }

    /** Enregistré même si la connexion échoue ensuite par une exception (transaction indépendante). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String email, String clientIp) {
        jdbcTemplate.update("INSERT INTO login_failure (email_hash, ip_hash) VALUES (?, ?)",
                hash(email), throttledIpHash(clientIp));
        jdbcTemplate.update("DELETE FROM login_failure WHERE occurred_at < now() - interval '1 day'");
    }

    public void clear(String email) {
        jdbcTemplate.update("DELETE FROM login_failure WHERE email_hash = ?", hash(email));
    }

    /** Empreinte de l'IP à limiter, ou {@code null} si l'IP est absente ou exemptée (boucle locale). */
    private String throttledIpHash(String clientIp) {
        if (clientIp == null || clientIp.isBlank() || UNLIMITED_IPS.contains(clientIp.trim())) {
            return null;
        }
        return hash(clientIp.trim().toLowerCase(Locale.ROOT));
    }

    private String hash(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256 indisponible", exception);
        }
    }
}
