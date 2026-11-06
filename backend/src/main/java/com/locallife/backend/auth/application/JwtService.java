package com.locallife.backend.auth.application;

import com.locallife.backend.user.domain.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service dédié à la génération des tokens JWT.
 * Ce service est utilisé par AuthService pour créer des tokens après un login réussi.
 */
@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String jwtSecret;

    private final long jwtExpirationMillis = 86400000L; // 24 heures

    /** Session d'un administrateur : 8 heures (décision du 22/10/2026). */
    static final long ADMIN_EXPIRATION_MILLIS = 8 * 3600000L;

    /**
     * Génère un token JWT pour un utilisateur donné.
     *
     * @param userId l'ID de l'utilisateur
     * @param email  l'email de l'utilisateur
     * @param role   le rôle de l'utilisateur
     * @return le token JWT généré
     */
    public String generateToken(Long userId, String email, Role role) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));

        return Jwts.builder()
                // Identifiant unique du jeton (jti) : permet de révoquer CETTE session précise à la déconnexion,
                // sans toucher aux autres appareils de l'utilisateur (voir TokenRevocationRepository, JwtFilter).
                .id(UUID.randomUUID().toString())
                .claim("userId", userId)
                .claim("email", email)
                .claim("role", role.name())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis()
                        + (role == Role.ADMIN ? ADMIN_EXPIRATION_MILLIS : jwtExpirationMillis)))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Claims d'un jeton dont la signature est vérifiée, ou {@link Optional#empty()} si le jeton est absent,
     * illisible, mal signé ou expiré. Utilisé par la déconnexion pour retrouver le {@code jti} et l'expiration du
     * jeton à révoquer, sans jamais faire échouer la requête sur un jeton déjà invalide (il n'y a alors rien à
     * révoquer).
     */
    public Optional<Claims> parseClaims(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        try {
            return Optional.of(Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload());
        } catch (JwtException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
