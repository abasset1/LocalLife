package com.locallife.backend.auth.application;

import com.locallife.backend.auth.domain.PasswordResetToken;
import com.locallife.backend.auth.infrastructure.PasswordResetTokenRepository;
import com.locallife.backend.user.application.PasswordHashingService;
import com.locallife.backend.user.domain.User;
import com.locallife.backend.user.infrastructure.UserRepository;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service de réinitialisation de mot de passe (LL-EF-007).
 *
 * <p>Points de sécurité (voir critères d'acceptation du ticket) :
 * <ul>
 *   <li>{@link #requestReset(String)} ne révèle jamais si l'email existe en
 *       base : le comportement observable côté appelant est identique que
 *       l'email corresponde à un compte ou non (voir {@code AuthController}
 *       qui renvoie systématiquement le même message générique) ;</li>
 *   <li>le token de réinitialisation n'est jamais stocké en clair : seul son
 *       empreinte SHA-256 est persistée ({@link PasswordResetToken#tokenHash()}) ;</li>
 *   <li>le token est à usage unique ({@code used}) et a une durée de
 *       validité limitée ({@link #TOKEN_VALIDITY_MINUTES}) ;</li>
 *   <li>toute demande de réinitialisation invalide les tokens précédents
 *       encore actifs pour l'utilisateur concerné ;</li>
 *   <li>le token en clair n'est jamais loggé (voir {@code SmtpEmailService}).</li>
 * </ul>
 */
@Service
public class PasswordResetService {

    private static final long TOKEN_VALIDITY_MINUTES = 30;
    private static final int TOKEN_BYTES = 32;
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final String GENERIC_INVALID_TOKEN_MESSAGE =
            "Ce lien de réinitialisation est invalide, expiré, ou a déjà été utilisé.";

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordHashingService passwordHashingService;
    private final EmailService emailService;
    private final SecureRandom secureRandom = new SecureRandom();
    private final String frontendBaseUrl;

    public PasswordResetService(
            UserRepository userRepository,
            PasswordResetTokenRepository tokenRepository,
            PasswordHashingService passwordHashingService,
            EmailService emailService,
            @Value("${app.frontend-base-url:http://localhost:5173}") String frontendBaseUrl) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordHashingService = passwordHashingService;
        this.emailService = emailService;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    /**
     * Déclenche une demande de réinitialisation pour l'adresse {@code email}.
     *
     * <p>Ne lève jamais d'exception liée à l'existence du compte : si aucun
     * utilisateur ne correspond à {@code email}, la méthode ne fait rien
     * silencieusement, pour ne pas permettre l'énumération des comptes.
     */
    public void requestReset(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("L'adresse email est requise.");
        }

        Optional<User> userOptional = userRepository.findByEmail(email);
        if (userOptional.isEmpty()) {
            return;
        }
        User user = userOptional.get();

        invalidateActiveTokens(user.id());

        String rawToken = generateRawToken();
        String tokenHash = hash(rawToken);
        PasswordResetToken token = new PasswordResetToken(
                null,
                user.id(),
                tokenHash,
                LocalDateTime.now().plusMinutes(TOKEN_VALIDITY_MINUTES),
                false,
                LocalDateTime.now());
        tokenRepository.save(token);

        String resetLink = frontendBaseUrl + "/reset-password?token=" + rawToken;
        emailService.sendPasswordResetEmail(user.email(), resetLink);
    }

    /**
     * Définit un nouveau mot de passe à partir d'un token de réinitialisation.
     *
     * @throws IllegalArgumentException si le token est invalide, expiré,
     *         déjà utilisé, ou si le nouveau mot de passe ne respecte pas
     *         la politique de sécurité — toujours avec le même message
     *         générique côté token, pour ne rien révéler sur la raison
     *         exacte de l'échec.
     */
    public void resetPassword(String rawToken, String newPassword) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException(GENERIC_INVALID_TOKEN_MESSAGE);
        }
        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException(
                    "Le mot de passe doit contenir au moins " + MIN_PASSWORD_LENGTH + " caractères.");
        }

        String tokenHash = hash(rawToken);
        PasswordResetToken token = tokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException(GENERIC_INVALID_TOKEN_MESSAGE));

        if (token.used() || token.expiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException(GENERIC_INVALID_TOKEN_MESSAGE);
        }

        User user = userRepository.findById(token.userId())
                .orElseThrow(() -> new IllegalArgumentException(GENERIC_INVALID_TOKEN_MESSAGE));

        String newPasswordHash = passwordHashingService.hash(newPassword);
        User updatedUser = new User(
                user.id(), user.username(), user.email(), newPasswordHash, user.role(), user.createdAt());
        userRepository.save(updatedUser);

        tokenRepository.save(new PasswordResetToken(
                token.id(), token.userId(), token.tokenHash(), token.expiresAt(), true, token.createdAt()));
    }

    private void invalidateActiveTokens(Long userId) {
        List<PasswordResetToken> activeTokens = tokenRepository.findAllByUserIdAndUsedFalse(userId);
        for (PasswordResetToken active : activeTokens) {
            tokenRepository.save(new PasswordResetToken(
                    active.id(), active.userId(), active.tokenHash(), active.expiresAt(), true, active.createdAt()));
        }
    }

    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }

}
