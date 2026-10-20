package com.locallife.backend.auth.application;

import com.locallife.backend.auth.api.LoginRequest;
import com.locallife.backend.auth.api.LoginResponse;
import com.locallife.backend.user.application.PasswordHashingService;
import com.locallife.backend.user.application.UsernamePolicy;
import com.locallife.backend.user.domain.AccountStatus;
import com.locallife.backend.user.domain.AuthProvider;
import com.locallife.backend.user.domain.Role;
import com.locallife.backend.user.domain.User;
import com.locallife.backend.user.domain.UserIdentity;
import com.locallife.backend.user.infrastructure.AccountRepository;
import com.locallife.backend.user.infrastructure.UserIdentityRepository;
import com.locallife.backend.user.infrastructure.UserRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Service d'authentification pour la gestion de l'inscription et du login.
 * Utilise PasswordHashingService pour hacher/vérifier les mots de passe et JwtService pour générer les tokens.
 */
@Service
public class AuthService {

    // Partie locale limitée à 64 caractères (RFC 5321), domaine sans point initial/final ni points
    // consécutifs, extension d'au moins 2 caractères. Volontairement permissif sur les caractères
    // (pas de liste blanche ASCII) pour ne pas rejeter d'adresses internationalisées valides : la
    // vraie preuve de validité est la confirmation par lien (voir EmailVerificationService).
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^@\\s]{1,64}@[^@\\s.]+(\\.[^@\\s.]+)*\\.[^@\\s.]{2,}$");
    private static final int MAX_EMAIL_LENGTH = 254;
    private static final int MIN_PASSWORD_LENGTH = 8;

    private final UserRepository userRepository;
    private final UserIdentityRepository userIdentityRepository;
    private final JwtService jwtService;
    private final PasswordHashingService passwordHashingService;
    private final GoogleAuthService googleAuthService;
    private final EmailVerificationService emailVerificationService;
    private final UsernamePolicy usernamePolicy;
    private final AccountRepository accountRepository;

    public AuthService(
            UserRepository userRepository,
            UserIdentityRepository userIdentityRepository,
            JwtService jwtService,
            PasswordHashingService passwordHashingService,
            GoogleAuthService googleAuthService,
            EmailVerificationService emailVerificationService,
            UsernamePolicy usernamePolicy,
            AccountRepository accountRepository) {
        this.userRepository = userRepository;
        this.userIdentityRepository = userIdentityRepository;
        this.jwtService = jwtService;
        this.passwordHashingService = passwordHashingService;
        this.googleAuthService = googleAuthService;
        this.emailVerificationService = emailVerificationService;
        this.usernamePolicy = usernamePolicy;
        this.accountRepository = accountRepository;
    }

    /**
     * Inscrit un nouvel utilisateur : valide les entrées, hache le mot de
     * passe (jamais stocké en clair) et crée le compte avec le rôle
     * {@code USER} par défaut.
     *
     * <p>Le compte est créé avec une adresse email non vérifiée ({@code
     * emailVerified = false}) : un lien de confirmation est envoyé à cette
     * adresse et la connexion reste refusée tant qu'il n'a pas été suivi
     * (voir {@link #login} et {@link EmailVerificationService}). Les espaces
     * autour de l'email sont retirés avant validation et enregistrement.
     *
     * @throws IllegalArgumentException si les entrées sont invalides ou si l'email est déjà utilisé
     */
    public User register(String username, String email, String password) {
        String normalizedEmail = email == null ? null : email.trim();
        validateCredentials(normalizedEmail, password);
        String validUsername = usernamePolicy.validate(username, null);

        String passwordHash = passwordHashingService.hash(password);
        User user = new User(null, validUsername, normalizedEmail, passwordHash, Role.USER, LocalDateTime.now(), false);
        User saved = usernamePolicy.saveGuarded(() -> userRepository.save(user));
        emailVerificationService.sendVerification(saved);
        return saved;
    }

    /**
     * Crée le premier compte {@code ADMIN}, uniquement si aucun compte
     * {@code ADMIN} n'existe déjà en base (LL-8002).
     *
     * <p>Ne fait rien et retourne {@link Optional#empty()} si un compte
     * {@code ADMIN} existe déjà — cette méthode ne permet donc jamais
     * d'élever un compte {@code USER} existant ni de créer un second
     * administrateur. Elle est destinée à être appelée uniquement au
     * démarrage de l'application, à partir d'identifiants fournis de façon
     * opérationnelle (variables d'environnement), jamais depuis un endpoint
     * HTTP accessible publiquement. L'adresse email de ce compte est
     * marquée vérifiée d'emblée : ses identifiants sont fournis
     * opérationnellement, sans passer par un lien de confirmation (l'adresse
     * peut d'ailleurs ne pas être une vraie boîte mail).
     *
     * @throws IllegalArgumentException si les entrées sont invalides ou si l'email est déjà utilisé
     */
    public Optional<User> bootstrapFirstAdmin(String username, String email, String password) {
        if (userRepository.existsByRole(Role.ADMIN)) {
            return Optional.empty();
        }

        validateCredentials(email, password);
        String validUsername = usernamePolicy.validateForBootstrap(username);

        String passwordHash = passwordHashingService.hash(password);
        User admin = new User(null, validUsername, email, passwordHash, Role.ADMIN, LocalDateTime.now(), true);
        return Optional.of(userRepository.save(admin));
    }

    private void validateCredentials(String email, String password) {
        if (email == null || email.length() > MAX_EMAIL_LENGTH || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("L'adresse email n'est pas valide.");
        }
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException(
                    "Le mot de passe doit contenir au moins " + MIN_PASSWORD_LENGTH + " caractères.");
        }
        if (userRepository.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("Cet email est déjà utilisé.");
        }
    }

    /**
     * Authentifie un utilisateur avec son email et mot de passe.
     *
     * @param request la requête de login contenant email et password
     * @return un LoginResponse contenant le token JWT si l'authentification réussit
     * @throws IllegalArgumentException si l'email ou le mot de passe est invalide
     * @throws EmailNotVerifiedException si les identifiants sont corrects mais que l'adresse
     *         email du compte n'a pas encore été confirmée — levée uniquement après
     *         vérification du mot de passe, pour ne pas révéler l'existence d'un compte
     */
    public LoginResponse login(LoginRequest request) {
        Optional<User> userOptional = userRepository.findByEmail(request.email());

        if (userOptional.isEmpty()) {
            throw new IllegalArgumentException("Email ou mot de passe incorrect");
        }

        User user = userOptional.get();

        // isBlank() (LL-EF-011), pas seulement == null : un compte créé exclusivement via
        // Google (voir #loginOrRegisterWithGoogle) a passwordHash = "" (jamais null, la
        // colonne reste NOT NULL — voir V6__add_password_hash_and_role_to_users.sql, aucune
        // migration nécessaire pour ce ticket) puisqu'aucun mot de passe LocalLife ne lui a
        // jamais été demandé (exigence explicite du ticket). Sans cette garde,
        // passwordHashingService.matches(...) lèverait une IllegalArgumentException interne
        // à BCrypt ("Invalid salt version", chaîne "" n'étant pas un hash BCrypt valide) au
        // lieu du rejet propre attendu ici — un 500 plutôt qu'un 401 pour toute tentative de
        // login classique sur un compte Google-only.
        if (user.passwordHash() == null || user.passwordHash().isBlank()
                || !passwordHashingService.matches(request.password(), user.passwordHash())) {
            throw new IllegalArgumentException("Email ou mot de passe incorrect");
        }

        if (!user.emailVerified()) {
            throw new EmailNotVerifiedException();
        }

        requireActive(user);
        return new LoginResponse(issueToken(user));
    }

    /**
     * Authentifie ou inscrit un utilisateur via Google (LL-EF-011,
     * OpenID Connect) : vérifie le token auprès de {@link GoogleAuthService}
     * (signature, émetteur, audience, email vérifié — voir sa javadoc),
     * puis :
     * <ol>
     *   <li>si une {@link UserIdentity} {@code GOOGLE} existe déjà pour ce
     *       compte Google précis (son {@code sub}), authentifie l'utilisateur
     *       associé ;</li>
     *   <li>sinon, si un compte LocalLife existe déjà pour l'email Google
     *       vérifié (inscription classique préalable), associe cette
     *       nouvelle identité à ce compte plutôt que d'en créer un
     *       second — critère d'acceptation explicite du ticket (« si un
     *       compte existe déjà et peut être associé de manière fiable,
     *       l'utilisateur est connecté à celui-ci »), fiable ici
     *       précisément parce que l'email a été vérifié par Google, pas
     *       simplement fourni par le frontend ;</li>
     *   <li>sinon, crée un nouveau compte {@code USER} — {@code
     *       passwordHash} vide (voir {@link #login} ci-dessus pour la
     *       garde associée), aucun mot de passe LocalLife n'étant demandé
     *       pour ce compte (exigence explicite du ticket) — puis
     *       l'{@link UserIdentity} correspondante.</li>
     * </ol>
     *
     * Nom d'utilisateur d'un compte nouvellement créé : repli sur la
     * partie locale de l'email si Google n'a pas fourni de nom affiché
     * (claim {@code name} absent), rendu conforme et libre par
     * {@link UsernamePolicy#generateAvailable} (LS-000) : « Jean Dupont »
     * devient « Jean-Dupont », ou « Jean-Dupont-2 » s'il est déjà pris.
     * L'utilisateur peut ensuite le changer depuis son profil.
     *
     * @throws IllegalArgumentException si {@link GoogleAuthService#verify}
     *         rejette le token (voir sa javadoc pour le détail des cas).
     */
    public LoginResponse loginOrRegisterWithGoogle(String idToken) {
        GoogleAuthService.GoogleIdentity identity = googleAuthService.verify(idToken);

        Optional<UserIdentity> existingIdentity =
                userIdentityRepository.findByProviderAndProviderUserId(AuthProvider.GOOGLE, identity.sub());
        if (existingIdentity.isPresent()) {
            User user = userRepository.findById(existingIdentity.get().userId())
                    .orElseThrow(() -> new IllegalStateException(
                            "UserIdentity " + existingIdentity.get().id() + " référence un utilisateur inexistant "
                                    + "(" + existingIdentity.get().userId() + ") — cohérence référentielle "
                                    + "violée malgré la contrainte ON DELETE CASCADE."));
            requireActive(user);
            return new LoginResponse(issueToken(user));
        }

        User user = userRepository.findByEmail(identity.email())
                .map(this::confirmEmailViaGoogle)
                .orElseGet(() -> userRepository.save(new User(
                        null,
                        usernamePolicy.generateAvailable(
                                identity.name() != null ? identity.name() : identity.email().split("@")[0]),
                        identity.email(),
                        "",
                        Role.USER,
                        LocalDateTime.now(),
                        true)));

        userIdentityRepository.save(new UserIdentity(null, user.id(), AuthProvider.GOOGLE, identity.sub(),
                LocalDateTime.now()));

        requireActive(user);
        return new LoginResponse(issueToken(user));
    }

    /**
     * L'email vérifié par Google prouve que l'utilisateur contrôle cette
     * adresse : un compte classique encore non vérifié est donc confirmé.
     *
     * <p>Son mot de passe est alors effacé : tant que l'adresse n'était pas
     * vérifiée, n'importe qui a pu créer ce compte avec l'adresse d'autrui et
     * choisir le mot de passe. Le conserver laisserait ce tiers se connecter
     * à un compte désormais confirmé. Le vrai propriétaire se connecte via
     * Google, ou définit un mot de passe via « mot de passe oublié ».
     */
    private User confirmEmailViaGoogle(User existing) {
        if (existing.emailVerified()) {
            return existing;
        }
        return userRepository.save(new User(
                existing.id(), existing.username(), existing.email(), "", existing.role(),
                existing.createdAt(), true));
    }

    /** ACC-02 : aucun jeton pour un compte suspendu (le filtre JWT refuse aussi les jetons déjà émis). */
    private void requireActive(User user) {
        if (accountRepository.findStatus(user.id()).orElse(AccountStatus.ACTIVE) != AccountStatus.ACTIVE) {
            throw new AccountSuspendedException();
        }
    }

    private String issueToken(User user) {
        return jwtService.generateToken(user.id(), user.email(), user.role());
    }
}
