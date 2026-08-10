package com.locallife.backend.auth.application;

import com.locallife.backend.auth.api.LoginRequest;
import com.locallife.backend.auth.api.LoginResponse;
import com.locallife.backend.user.application.PasswordHashingService;
import com.locallife.backend.user.domain.Role;
import com.locallife.backend.user.domain.User;
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
    private final JwtService jwtService;
    private final PasswordHashingService passwordHashingService;

    public AuthService(
            UserRepository userRepository,
            JwtService jwtService,
            PasswordHashingService passwordHashingService) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.passwordHashingService = passwordHashingService;
    }

    /**
     * Inscrit un nouvel utilisateur : valide les entrées, hache le mot de
     * passe (jamais stocké en clair) et crée le compte avec le rôle
     * {@code USER} par défaut.
     *
     * @throws IllegalArgumentException si les entrées sont invalides ou si l'email est déjà utilisé
     */
    public User register(String username, String email, String password) {
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

        String passwordHash = passwordHashingService.hash(password);
        User user = new User(null, username, email, passwordHash, Role.USER, LocalDateTime.now());
        return userRepository.save(user);
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

        if (user.passwordHash() == null
                || !passwordHashingService.matches(request.password(), user.passwordHash())) {
            throw new IllegalArgumentException("Email ou mot de passe incorrect");
        }

        String token = jwtService.generateToken(user.id(), user.email(), user.role());
        return new LoginResponse(token);
    }
}
