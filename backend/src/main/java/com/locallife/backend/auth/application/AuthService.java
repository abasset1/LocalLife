package com.locallife.backend.auth.application;

import com.locallife.backend.auth.api.LoginRequest;
import com.locallife.backend.auth.api.LoginResponse;
import com.locallife.backend.user.application.PasswordHashingService;
import com.locallife.backend.user.domain.AuthProvider;
import com.locallife.backend.user.domain.Role;
import com.locallife.backend.user.domain.User;
import com.locallife.backend.user.domain.UserIdentity;
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

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final int MIN_PASSWORD_LENGTH = 8;

    private final UserRepository userRepository;
    private final UserIdentityRepository userIdentityRepository;
    private final JwtService jwtService;
    private final PasswordHashingService passwordHashingService;
    private final GoogleAuthService googleAuthService;

    public AuthService(
            UserRepository userRepository,
            UserIdentityRepository userIdentityRepository,
            JwtService jwtService,
            PasswordHashingService passwordHashingService,
            GoogleAuthService googleAuthService) {
        this.userRepository = userRepository;
        this.userIdentityRepository = userIdentityRepository;
        this.jwtService = jwtService;
        this.passwordHashingService = passwordHashingService;
        this.googleAuthService = googleAuthService;
    }

    /**
     * Inscrit un nouvel utilisateur : valide les entrées, hache le mot de
     * passe (jamais stocké en clair) et crée le compte avec le rôle
     * {@code USER} par défaut.
     *
     * @throws IllegalArgumentException si les entrées sont invalides ou si l'email est déjà utilisé
     */
    public User register(String username, String email, String password) {
        validateCredentials(username, email, password);

        String passwordHash = passwordHashingService.hash(password);
        User user = new User(null, username, email, passwordHash, Role.USER, LocalDateTime.now());
        return userRepository.save(user);
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
     * HTTP accessible publiquement.
     *
     * @throws IllegalArgumentException si les entrées sont invalides ou si l'email est déjà utilisé
     */
    public Optional<User> bootstrapFirstAdmin(String username, String email, String password) {
        if (userRepository.existsByRole(Role.ADMIN)) {
            return Optional.empty();
        }

        validateCredentials(username, email, password);

        String passwordHash = passwordHashingService.hash(password);
        User admin = new User(null, username, email, passwordHash, Role.ADMIN, LocalDateTime.now());
        return Optional.of(userRepository.save(admin));
    }

    private void validateCredentials(String username, String email, String password) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Le nom d'utilisateur est requis.");
        }
        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
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
     * (claim {@code name} absent) — {@code username} restant {@code NOT
     * NULL} sur {@code users} (voir {@code V4__create_users_table.sql})
     * sans être encore garanti unique à ce stade du projet (LL-EF-017 non
     * traité), aucune vérification d'unicité n'est donc faite ici, à
     * l'identique de {@link #register} ci-dessus.
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
            return new LoginResponse(issueToken(user));
        }

        User user = userRepository.findByEmail(identity.email())
                .orElseGet(() -> userRepository.save(new User(
                        null,
                        identity.name() != null ? identity.name() : identity.email().split("@")[0],
                        identity.email(),
                        "",
                        Role.USER,
                        LocalDateTime.now())));

        userIdentityRepository.save(new UserIdentity(null, user.id(), AuthProvider.GOOGLE, identity.sub(),
                LocalDateTime.now()));

        return new LoginResponse(issueToken(user));
    }

    private String issueToken(User user) {
        return jwtService.generateToken(user.id(), user.email(), user.role());
    }
}
