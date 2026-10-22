package com.locallife.backend.auth.application;

import com.locallife.backend.auth.domain.EmailVerificationToken;
import com.locallife.backend.auth.infrastructure.EmailVerificationTokenRepository;
import com.locallife.backend.user.domain.User;
import com.locallife.backend.user.infrastructure.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;

/**
 * Service de vérification de l'adresse email d'un compte créé par
 * inscription classique.
 *
 * <p>Même patron de sécurité que {@link PasswordResetService} :
 * <ul>
 *   <li>le token n'est jamais stocké en clair : seule son empreinte SHA-256
 *       est persistée ({@link EmailVerificationToken#tokenHash()}) ;</li>
 *   <li>le token est à usage unique et valable {@link #TOKEN_VALIDITY_HOURS}
 *       heures ;</li>
 *   <li>l'émission d'un nouveau token invalide les précédents encore
 *       actifs ;</li>
 *   <li>{@link #resend(String)} ne révèle jamais si l'email existe ni s'il
 *       est déjà vérifié ;</li>
 *   <li>le token en clair n'est jamais loggé (voir {@code SmtpEmailService}).</li>
 * </ul>
 *
 * <p>Un échec d'envoi de l'email n'annule pas l'inscription : le compte
 * existe, non vérifié, et l'utilisateur peut demander un nouveau lien
 * ({@link #resend(String)}) plutôt que de perdre son inscription sur une
 * indisponibilité SMTP passagère.
 */
@Service
public class EmailVerificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);

    private static final long TOKEN_VALIDITY_HOURS = 24;
    private static final long RESEND_COOLDOWN_SECONDS = 60;
    private static final int TOKEN_BYTES = 32;
    private static final String GENERIC_INVALID_TOKEN_MESSAGE =
            "Ce lien de confirmation est invalide, expiré, ou a déjà été utilisé.";

    private final UserRepository userRepository;
    private final EmailVerificationTokenRepository tokenRepository;
    private final EmailService emailService;
    private final SecureRandom secureRandom = new SecureRandom();
    private final String frontendBaseUrl;

    public EmailVerificationService(
            UserRepository userRepository,
            EmailVerificationTokenRepository tokenRepository,
            EmailService emailService,
            @Value("${app.frontend-base-url:http://localhost:5173}") String frontendBaseUrl) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.emailService = emailService;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    /**
     * Génère un token de confirmation pour {@code user} et envoie le lien par
     * email. Invalide les tokens précédents encore actifs de ce compte.
     *
     * <p>Ne lève pas d'exception si l'envoi échoue (voir la javadoc de la
     * classe) : l'échec est journalisé, sans le lien.
     */
    public void sendVerification(User user) {
        invalidateActiveTokens(user.id());

        String rawToken = generateRawToken();
        tokenRepository.save(new EmailVerificationToken(
                null,
                user.id(),
                hash(rawToken),
                LocalDateTime.now().plusHours(TOKEN_VALIDITY_HOURS),
                false,
                LocalDateTime.now()));

        String verificationLink = frontendBaseUrl + "/verify-email?token=" + rawToken;
        try {
            emailService.sendEmailVerificationEmail(user.email(), verificationLink);
        } catch (MailException exception) {
            log.error("Échec de l'envoi de l'email de confirmation à {} : {}", EmailMask.mask(user.email()),
                    exception.getClass().getSimpleName());
        }
    }

    /**
     * Confirme l'adresse email associée au token.
     *
     * <p>Idempotent : si le compte est déjà vérifié, la méthode ne fait rien
     * (double clic sur le lien, prévisualisation par un client mail, etc.).
     *
     * @throws IllegalArgumentException si le token est absent, inconnu, expiré
     *         ou déjà utilisé — toujours avec le même message générique.
     */
    public void verify(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException(GENERIC_INVALID_TOKEN_MESSAGE);
        }

        EmailVerificationToken token = tokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new IllegalArgumentException(GENERIC_INVALID_TOKEN_MESSAGE));
        User user = userRepository.findById(token.userId())
                .orElseThrow(() -> new IllegalArgumentException(GENERIC_INVALID_TOKEN_MESSAGE));

        if (user.emailVerified()) {
            return;
        }
        if (token.used() || token.expiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException(GENERIC_INVALID_TOKEN_MESSAGE);
        }

        userRepository.save(new User(
                user.id(), user.username(), user.email(), user.passwordHash(), user.role(), user.createdAt(), true));
        tokenRepository.save(new EmailVerificationToken(
                token.id(), token.userId(), token.tokenHash(), token.expiresAt(), true, token.createdAt()));
    }

    /**
     * Renvoie un lien de confirmation si {@code email} correspond à un compte
     * non vérifié.
     *
     * <p>Ne fait rien, silencieusement, si le compte n'existe pas, est déjà
     * vérifié, ou si un lien a été émis il y a moins de
     * {@link #RESEND_COOLDOWN_SECONDS} secondes (limite le spam d'emails).
     *
     * @throws IllegalArgumentException si {@code email} est absent
     */
    public void resend(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("L'adresse email est requise.");
        }

        Optional<User> userOptional = userRepository.findByEmail(email.trim());
        if (userOptional.isEmpty() || userOptional.get().emailVerified()) {
            return;
        }
        User user = userOptional.get();

        LocalDateTime cooldownLimit = LocalDateTime.now().minusSeconds(RESEND_COOLDOWN_SECONDS);
        boolean issuedRecently = tokenRepository.findAllByUserIdAndUsedFalse(user.id()).stream()
                .anyMatch(active -> active.createdAt().isAfter(cooldownLimit));
        if (issuedRecently) {
            return;
        }

        sendVerification(user);
    }

    private void invalidateActiveTokens(Long userId) {
        List<EmailVerificationToken> activeTokens = tokenRepository.findAllByUserIdAndUsedFalse(userId);
        for (EmailVerificationToken active : activeTokens) {
            tokenRepository.save(new EmailVerificationToken(
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
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }

}
