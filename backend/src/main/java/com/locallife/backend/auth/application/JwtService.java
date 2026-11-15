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
     * Session de l'application mobile : 30 jours, renouvelée à chaque ouverture de l'application
     * ({@code POST /api/v1/auth/refresh}). L'utilisateur reste connecté tant qu'il ouvre l'application au moins une
     * fois par mois. Le jeton reste révocable à tout moment : déconnexion (jti), changement de mot de passe,
     * suspension ou suppression du compte sont relus en base à chaque requête ({@code JwtFilter}).
     */
    static final long DEVICE_EXPIRATION_MILLIS = 30L * 24 * 3600000L;

    /**
     * Génère un token JWT pour un utilisateur donné.
     *
     * @param userId l'ID de l'utilisateur
     * @param email  l'email de l'utilisateur
     * @param role   le rôle de l'utilisateur
     * @return le token JWT généré
     */
    public String generateToken(Long userId, String email, Role role) {
        return buildToken(userId, email, role, jwtExpirationMillis);
    }

    /**
     * Jeton longue durée de l'application mobile ({@link #DEVICE_EXPIRATION_MILLIS}), stocké dans le coffre sécurisé
     * du téléphone. Un administrateur garde sa session de 8 heures, même sur l'application.
     *
     * @param authTime date de la connexion d'origine, conservée d'un renouvellement à l'autre (claim
     *                 {@code auth_time}, en secondes) : l'application s'en sert comme « date de connexion »
     */
    public String generateDeviceToken(Long userId, String email, Role role, Date authTime) {
        return buildToken(userId, email, role, DEVICE_EXPIRATION_MILLIS, authTime);
    }

    private String buildToken(Long userId, String email, Role role, long lifetimeMillis) {
        return buildToken(userId, email, role, lifetimeMillis, null);
    }

    private String buildToken(Long userId, String email, Role role, long lifetimeMillis, Date authTime) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));

        return Jwts.builder()
                // Identifiant unique du jeton (jti) : permet de révoquer CETTE session précise à la déconnexion,
                // sans toucher aux autres appareils de l'utilisateur (voir TokenRevocationRepository, JwtFilter).
                .id(UUID.randomUUID().toString())
                .claim("userId", userId)
                .claim("email", email)
                .claim("role", role.name())
                .claim("auth_time", authTime == null ? null : authTime.getTime() / 1000)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis()
                        + (role == Role.ADMIN ? ADMIN_EXPIRATION_MILLIS : lifetimeMillis)))
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
