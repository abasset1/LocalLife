package com.locallife.backend.auth.application;

import com.locallife.backend.auth.api.LoginRequest;
import com.locallife.backend.auth.api.LoginResponse;
import com.locallife.backend.user.application.PasswordHashingService;
import com.locallife.backend.user.domain.User;
import com.locallife.backend.user.infrastructure.UserRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Service d'authentification pour la gestion du login.
 * Utilise PasswordHashingService pour vérifier les mots de passe et JwtService pour générer les tokens.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordHashingService passwordHashingService;

    public AuthService(UserRepository userRepository, JwtService jwtService, PasswordHashingService passwordHashingService) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.passwordHashingService = passwordHashingService;
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

        if (user.passwordHash() == null || !passwordHashingService.matches(request.password(), user.passwordHash())) {
            throw new IllegalArgumentException("Email ou mot de passe incorrect");
        }

        String token = jwtService.generateToken(user.id(), user.email(), user.role());
        return new LoginResponse(token);
    }
}
