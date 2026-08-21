package com.locallife.backend.auth.application;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Initialise le premier compte {@code ADMIN} au démarrage de l'application
 * (LL-8002), pour remplacer le contournement SQL manuel
 * ({@code UPDATE users SET role = 'ADMIN' ...}) utilisé jusqu'au Sprint 7.
 *
 * <p>Ne fait rien par défaut : le bootstrap ne se déclenche que si les
 * trois variables d'environnement {@code LOCALLIFE_BOOTSTRAP_ADMIN_EMAIL},
 * {@code LOCALLIFE_BOOTSTRAP_ADMIN_PASSWORD} et, optionnellement,
 * {@code LOCALLIFE_BOOTSTRAP_ADMIN_USERNAME} sont renseignées au lancement.
 * Aucun compte ADMIN par défaut n'est créé, aucun mot de passe connu n'est
 * codé en dur, et un compte ADMIN existant n'est jamais recréé ni modifié
 * (voir {@link AuthService#bootstrapFirstAdmin}).
 */
@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final AuthService authService;
    private final String bootstrapUsername;
    private final String bootstrapEmail;
    private final String bootstrapPassword;

    public AdminBootstrapRunner(
            AuthService authService,
            @Value("${LOCALLIFE_BOOTSTRAP_ADMIN_USERNAME:admin}") String bootstrapUsername,
            @Value("${LOCALLIFE_BOOTSTRAP_ADMIN_EMAIL:}") String bootstrapEmail,
            @Value("${LOCALLIFE_BOOTSTRAP_ADMIN_PASSWORD:}") String bootstrapPassword) {
        this.authService = authService;
        this.bootstrapUsername = bootstrapUsername;
        this.bootstrapEmail = bootstrapEmail;
        this.bootstrapPassword = bootstrapPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (bootstrapEmail.isBlank() || bootstrapPassword.isBlank()) {
            LOG.info("Bootstrap admin non configuré (LOCALLIFE_BOOTSTRAP_ADMIN_EMAIL/PASSWORD absents) : ignoré.");
            return;
        }

        try {
            Optional<?> created = authService.bootstrapFirstAdmin(bootstrapUsername, bootstrapEmail, bootstrapPassword);
            if (created.isPresent()) {
                LOG.info("Compte ADMIN de bootstrap créé pour l'email {}.", bootstrapEmail);
            } else {
                LOG.info("Un compte ADMIN existe déjà : bootstrap ignoré.");
            }
        } catch (IllegalArgumentException e) {
            LOG.warn("Bootstrap admin ignoré : identifiants fournis invalides ({}).", e.getMessage());
        }
    }
}
