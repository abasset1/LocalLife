package com.locallife.backend.auth.application;

import com.locallife.backend.user.domain.Role;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
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
                .claim("userId", userId)
                .claim("email", email)
                .claim("role", role.name())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtExpirationMillis))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }
}
