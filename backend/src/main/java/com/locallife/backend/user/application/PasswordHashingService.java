package com.locallife.backend.user.application;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Service de hachage des mots de passe (BCrypt). Le mot de passe en clair
 * n'est jamais stocké : seul le résultat de {@link #hash(String)} est
 * persisté (champ {@code passwordHash} de {@link com.locallife.backend.user.domain.User}).
 */
@Service
public class PasswordHashingService {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String hashedPassword) {
        return encoder.matches(rawPassword, hashedPassword);
    }

}
