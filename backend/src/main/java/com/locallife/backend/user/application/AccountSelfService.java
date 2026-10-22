package com.locallife.backend.user.application;

import com.locallife.backend.activity.application.ContributionService;
import com.locallife.backend.activity.domain.Activity;
import com.locallife.backend.auth.application.AuthService;
import com.locallife.backend.auth.application.EmailService;
import com.locallife.backend.auth.application.JwtService;
import com.locallife.backend.pro.infrastructure.ProContentRepository;
import com.locallife.backend.subscription.application.SubscriptionService;
import com.locallife.backend.user.domain.Role;
import com.locallife.backend.user.domain.User;
import com.locallife.backend.user.infrastructure.AccountAuditRepository;
import com.locallife.backend.user.infrastructure.AccountAuditRepository.Event;
import com.locallife.backend.user.infrastructure.AccountRepository;
import com.locallife.backend.user.infrastructure.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Actions d'un utilisateur sur son propre compte (décisions du 21/10/2026, {@code ROLES_ET_PERMISSIONS.md} § 3 bis) :
 * changer son mot de passe, changer son adresse e-mail (confirmée depuis la nouvelle adresse), supprimer son compte.
 * Chaque action réussie est consignée dans {@code account_audit_event}, sans secret.
 */
@Service
public class AccountSelfService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AccountSelfService.class);

    /** Mot à saisir pour supprimer un compte sans mot de passe (connexion Google uniquement). */
    public static final String DELETION_CONFIRMATION_WORD = "SUPPRIMER";

    private static final long EMAIL_CHANGE_VALIDITY_HOURS = 24;
    private static final int TOKEN_BYTES = 32;
    private static final String INVALID_EMAIL_CHANGE_LINK =
            "Ce lien de confirmation est invalide, expiré, ou a déjà été utilisé.";
    private static final String PUBLISHED_STATUS = "PUBLISHED";

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final AccountAuditRepository auditRepository;
    private final PasswordHashingService passwordHashingService;
    private final JwtService jwtService;
    private final EmailService emailService;
    private final ContributionService contributionService;
    private final SubscriptionService subscriptionService;
    private final JdbcTemplate jdbcTemplate;
    private final ProContentRepository proContentRepository;
    private final String frontendBaseUrl;
    private final SecureRandom secureRandom = new SecureRandom();

    public AccountSelfService(
            UserRepository userRepository,
            AccountRepository accountRepository,
            AccountAuditRepository auditRepository,
            PasswordHashingService passwordHashingService,
            JwtService jwtService,
            EmailService emailService,
            ContributionService contributionService,
            SubscriptionService subscriptionService,
            JdbcTemplate jdbcTemplate,
            ProContentRepository proContentRepository,
            @Value("${app.frontend-base-url:http://localhost:5173}") String frontendBaseUrl) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.auditRepository = auditRepository;
        this.passwordHashingService = passwordHashingService;
        this.jwtService = jwtService;
        this.emailService = emailService;
        this.contributionService = contributionService;
        this.subscriptionService = subscriptionService;
        this.jdbcTemplate = jdbcTemplate;
        this.proContentRepository = proContentRepository;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    // --- Mot de passe ---

    /**
     * Change (ou définit, pour un compte créé avec Google) le mot de passe. Le mot de passe actuel est exigé s'il en
     * existe un. Les autres sessions sont fermées : les jetons émis avant ce changement sont refusés.
     *
     * @return un nouveau jeton pour la session en cours
     * @throws AccountActionException 400 si le mot de passe actuel est faux ou si le nouveau est trop court
     */
    @Transactional
    public String changePassword(Long userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> AccountActionException.notFound("Compte introuvable."));
        if (hasPassword(user)
                && (currentPassword == null || !passwordHashingService.matches(currentPassword, user.passwordHash()))) {
            throw AccountActionException.badRequest("Mot de passe actuel incorrect.");
        }
        if (newPassword == null || newPassword.length() < AuthService.MIN_PASSWORD_LENGTH) {
            throw AccountActionException.badRequest(
                    "Le mot de passe doit contenir au moins " + AuthService.MIN_PASSWORD_LENGTH + " caractères.");
        }
        userRepository.save(new User(user.id(), user.username(), user.email(), passwordHashingService.hash(newPassword),
                user.role(), user.createdAt(), user.emailVerified()));
        accountRepository.revokeTokensIssuedBefore(userId, nowUtc().truncatedTo(ChronoUnit.SECONDS));
        record(userId, user.email(), "PASSWORD_CHANGE", null, null);
        return jwtService.generateToken(user.id(), user.email(), user.role());
    }

    public static boolean hasPassword(User user) {
        return user.passwordHash() != null && !user.passwordHash().isBlank();
    }

    // --- Adresse e-mail ---

    /** Nouvelle adresse en attente de confirmation, s'il y en a une encore valable. */
    public Optional<String> pendingEmail(Long userId) {
        return jdbcTemplate.query("SELECT new_email FROM email_change_request WHERE user_id = ? AND expires_at > ?",
                (resultSet, rowNumber) -> resultSet.getString("new_email"), userId, Timestamp.valueOf(nowUtc()))
                .stream().findFirst();
    }

    /**
     * Demande de changement d'adresse : rien ne change tant que le lien envoyé à la nouvelle adresse n'est pas suivi.
     * L'ancienne adresse est prévenue. Une nouvelle demande remplace la précédente.
     *
     * @throws AccountActionException 400 adresse invalide ou déjà utilisée
     */
    @Transactional
    public void requestEmailChange(Long userId, String requestedEmail) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> AccountActionException.notFound("Compte introuvable."));
        String newEmail = requestedEmail == null ? null : requestedEmail.trim();
        if (!AuthService.isValidEmail(newEmail)) {
            throw AccountActionException.badRequest("L'adresse email n'est pas valide.");
        }
        if (newEmail.equalsIgnoreCase(user.email())) {
            jdbcTemplate.update("DELETE FROM email_change_request WHERE user_id = ?", userId);
            return;
        }
        if (userRepository.findByEmail(newEmail).isPresent()) {
            throw AccountActionException.badRequest("Cet email est déjà utilisé.");
        }
        String rawToken = generateRawToken();
        LocalDateTime now = nowUtc();
        jdbcTemplate.update("""
                INSERT INTO email_change_request (user_id, new_email, token_hash, expires_at, created_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (user_id) DO UPDATE SET new_email = EXCLUDED.new_email, token_hash = EXCLUDED.token_hash,
                    expires_at = EXCLUDED.expires_at, created_at = EXCLUDED.created_at
                """, userId, newEmail, hash(rawToken), Timestamp.valueOf(now.plusHours(EMAIL_CHANGE_VALIDITY_HOURS)),
                Timestamp.valueOf(now));
        record(userId, user.email(), "EMAIL_CHANGE_REQUEST", mask(user.email()), mask(newEmail));
        try {
            emailService.sendEmailChangeConfirmationEmail(
                    newEmail, frontendBaseUrl + "/confirm-email-change?token=" + rawToken);
            emailService.sendEmailChangeNoticeEmail(user.email(), mask(newEmail));
        } catch (MailException exception) {
            // Le lien n'est pas journalisé : il suffit à confirmer l'adresse.
            LOGGER.error("Échec de l'envoi des emails de changement d'adresse pour le compte {}", userId, exception);
        }
    }

    /**
     * Confirme le changement d'adresse : suivre le lien prouve que l'utilisateur contrôle la nouvelle adresse.
     *
     * @throws AccountActionException 400 lien invalide, expiré ou déjà utilisé (message générique), ou adresse prise
     *         entre-temps par un autre compte
     */
    @Transactional
    public void confirmEmailChange(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw AccountActionException.badRequest(INVALID_EMAIL_CHANGE_LINK);
        }
        record PendingChange(Long userId, String newEmail) {
        }
        PendingChange pending = jdbcTemplate.query(
                "SELECT user_id, new_email FROM email_change_request WHERE token_hash = ? AND expires_at > ?",
                (resultSet, rowNumber) -> new PendingChange(resultSet.getLong("user_id"),
                        resultSet.getString("new_email")), hash(rawToken), Timestamp.valueOf(nowUtc()))
                .stream().findFirst()
                .orElseThrow(() -> AccountActionException.badRequest(INVALID_EMAIL_CHANGE_LINK));
        User user = userRepository.findById(pending.userId())
                .orElseThrow(() -> AccountActionException.badRequest(INVALID_EMAIL_CHANGE_LINK));
        Optional<User> owner = userRepository.findByEmail(pending.newEmail());
        if (owner.isPresent() && !owner.get().id().equals(user.id())) {
            jdbcTemplate.update("DELETE FROM email_change_request WHERE user_id = ?", user.id());
            throw AccountActionException.badRequest("Cette adresse est désormais utilisée par un autre compte.");
        }
        userRepository.save(new User(user.id(), user.username(), pending.newEmail(), user.passwordHash(), user.role(),
                user.createdAt(), true));
        jdbcTemplate.update("DELETE FROM email_change_request WHERE user_id = ?", user.id());
        record(user.id(), pending.newEmail(), "EMAIL_CHANGE", mask(user.email()), mask(pending.newEmail()));
        LOGGER.info("Adresse email du compte {} changée après confirmation", user.id());
    }

    // --- Suppression ---

    /**
     * Supprime définitivement le compte (décision « anonymiser le public ») :
     * <ul>
     *   <li>activités publiées conservées, sans auteur ({@code created_by} passe à {@code NULL}) ; les autres
     *       contributions (en attente, refusées, annulées, archivées) sont supprimées ;</li>
     *   <li>demandes de gestion de lieu supprimées (justificatifs personnels) ; lieux et food trucks créés restent,
     *       sans créateur ni propriétaire ;</li>
     *   <li>abonnement en cours terminé, demande en attente retirée ; historique conservé sans lien vers la
     *       personne ;</li>
     *   <li>favoris, alertes, préférences, connexions Google, jetons et compte supprimés.</li>
     * </ul>
     * Le mot de passe est exigé ; un compte sans mot de passe (Google) confirme avec
     * {@value #DELETION_CONFIRMATION_WORD}.
     * Un administrateur ne supprime pas son compte depuis l'application.
     *
     * @throws AccountActionException 400 mot de passe ou confirmation incorrects, 403 compte administrateur
     */
    @Transactional
    public void deleteAccount(Long userId, String password, String confirmation) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> AccountActionException.notFound("Compte introuvable."));
        if (user.role() == Role.ADMIN) {
            throw AccountActionException.forbidden("Un compte administrateur ne se supprime pas depuis l'application.");
        }
        if (hasPassword(user)) {
            if (password == null || !passwordHashingService.matches(password, user.passwordHash())) {
                throw AccountActionException.badRequest("Mot de passe incorrect.");
            }
        } else if (confirmation == null || !DELETION_CONFIRMATION_WORD.equals(confirmation.trim())) {
            throw AccountActionException.badRequest(
                    "Saisis " + DELETION_CONFIRMATION_WORD + " pour confirmer la suppression du compte.");
        }

        int deletedContributions = deleteAccountData(userId);
        // Trace sans donnée personnelle : seul l'identifiant (désormais sans compte) reste.
        auditRepository.append(new Event(null, nowUtc(), userId, null, userId, "ACCOUNT_DELETION", null, null, null,
                "SUCCESS", deletedContributions + " contribution(s) non publiée(s) supprimée(s)"));
        accountRepository.delete(userId);
        LOGGER.info("Compte {} supprimé par son titulaire ({} contribution(s) non publiée(s) supprimée(s))", userId,
                deletedContributions);
    }

    /**
     * Suppression d'un compte, commune à son titulaire et à un administrateur (décision du 22/10/2026) : activités
     * publiées conservées sans auteur, contributions non publiées supprimées, contenus professionnels retirés,
     * demandes de gestion supprimées, abonnement clos. Le compte lui-même est supprimé par l'appelant, après sa trace.
     *
     * @return nombre de contributions non publiées supprimées
     */
    int deleteAccountData(Long userId) {
        int deletedContributions = 0;
        for (Activity activity : contributionService.findByCreator(userId)) {
            if (!PUBLISHED_STATUS.equals(activity.status())) {
                contributionService.delete(activity.id(), userId, false);
                deletedContributions++;
            }
        }
        // Food trucks publiés masqués (ils resteraient sinon en ligne sans propriétaire), droits de gestion révoqués.
        proContentRepository.withdraw(userId);
        jdbcTemplate.update("DELETE FROM place_claim WHERE requester_id = ?", userId);
        subscriptionService.closeForDeletedAccount(userId);
        return deletedContributions;
    }

    // --- Commun ---

    private void record(Long userId, String actorEmail, String action, String oldValue, String newValue) {
        auditRepository.append(new Event(null, nowUtc(), userId, actorEmail, userId, action, oldValue, newValue, null,
                "SUCCESS", null));
    }

    /** {@code jean.dupont@exemple.fr} → {@code j***@exemple.fr} : assez pour reconnaître, pas pour révéler. */
    static String mask(String email) {
        if (email == null) {
            return null;
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    private static LocalDateTime nowUtc() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 non disponible.", exception);
        }
    }
}
