package com.locallife.backend.auth.application;

import com.locallife.backend.user.application.PasswordHashingService;
import com.locallife.backend.user.domain.User;
import com.locallife.backend.user.infrastructure.UserRepository;
import org.springframework.stereotype.Service;

/**
 * Confirmation du mot de passe avant les actions les plus destructrices d'un administrateur (décision du 22/10/2026) :
 * suppression en masse, suppression d'une source, suspension ou suppression d'un compte. Le mot de passe est envoyé
 * dans l'en-tête {@value #HEADER} de la requête concernée, jamais journalisé.
 */
@Service
public class ReauthenticationService {

    public static final String HEADER = "X-Confirm-Password";

    private final UserRepository userRepository;
    private final PasswordHashingService passwordHashingService;

    public ReauthenticationService(UserRepository userRepository, PasswordHashingService passwordHashingService) {
        this.userRepository = userRepository;
        this.passwordHashingService = passwordHashingService;
    }

    /** @throws ReauthenticationRequiredException mot de passe absent ou incorrect (428) */
    public void require(Long userId, String password) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ReauthenticationRequiredException("Compte introuvable."));
        if (user.passwordHash() == null || user.passwordHash().isBlank()) {
            throw new ReauthenticationRequiredException("Définis un mot de passe sur ton profil pour confirmer "
                    + "cette action.");
        }
        if (password == null || password.isEmpty()) {
            throw new ReauthenticationRequiredException("Confirme ton mot de passe pour cette action.");
        }
        if (!passwordHashingService.matches(password, user.passwordHash())) {
            throw new ReauthenticationRequiredException("Mot de passe incorrect : action non effectuée.");
        }
    }
}
