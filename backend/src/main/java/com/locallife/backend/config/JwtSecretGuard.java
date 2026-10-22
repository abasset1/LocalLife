package com.locallife.backend.config;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Refuse de démarrer en production avec la clé de signature des jetons par défaut ou une clé trop courte (RGPD,
 * art. 32 : sécurité du traitement, 22/10/2026). Sans cette garde, un oubli de {@code JWT_SECRET} laissait signer
 * des jetons avec une clé publique, présente dans le dépôt.
 */
@Component
public class JwtSecretGuard {

    static final String DEFAULT_SECRET = "default_secret_for_dev_only_change_in_production";
    static final int MIN_BYTES = 32;

    public JwtSecretGuard(@Value("${jwt.secret}") String secret, Environment environment) {
        boolean production = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        if (production && (DEFAULT_SECRET.equals(secret)
                || secret.getBytes(StandardCharsets.UTF_8).length < MIN_BYTES)) {
            throw new IllegalStateException("JWT_SECRET absent, par défaut ou trop court (" + MIN_BYTES
                    + " octets au moins) : démarrage refusé en production.");
        }
    }
}
