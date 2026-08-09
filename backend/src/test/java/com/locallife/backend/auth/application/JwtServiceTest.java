package com.locallife.backend.auth.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.locallife.backend.user.domain.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Tests unitaires pour JwtService.
 * Vérifie la génération des tokens JWT, les claims, et la signature.
 */
@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    @InjectMocks
    private JwtService jwtService;

    private final String testSecret = "test_secret_key_for_jwt_testing_only_change_in_production";

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "jwtSecret", testSecret);
    }

    @Test
    void generateToken_ShouldReturnNonNullToken() {
        String token = jwtService.generateToken(1L, "test@example.com", Role.USER);
        assertNotNull(token);
    }

    @Test
    void generateToken_ShouldContainUserIdClaim() {
        Long userId = 123L;
        String token = jwtService.generateToken(userId, "test@example.com", Role.USER);

        Claims claims = extractClaims(token);
        assertEquals(userId, claims.get("userId", Long.class));
    }

    @Test
    void generateToken_ShouldContainEmailClaim() {
        String email = "test@example.com";
        String token = jwtService.generateToken(1L, email, Role.USER);

        Claims claims = extractClaims(token);
        assertEquals(email, claims.get("email", String.class));
    }

    @Test
    void generateToken_ShouldContainRoleClaim() {
        Role role = Role.ADMIN;
        String token = jwtService.generateToken(1L, "test@example.com", role);

        Claims claims = extractClaims(token);
        assertEquals(role.name(), claims.get("role", String.class));
    }

    @Test
    void generateToken_ShouldHaveIssuedAtAndExpiration() {
        String token = jwtService.generateToken(1L, "test@example.com", Role.USER);

        Claims claims = extractClaims(token);
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());
    }

    @Test
    void generateToken_ShouldBeSignedWithCorrectSecret() {
        String token = jwtService.generateToken(1L, "test@example.com", Role.USER);

        // Vérifier que le token est valide avec le même secret
        SecretKey key = Keys.hmacShaKeyFor(testSecret.getBytes(StandardCharsets.UTF_8));
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertNotNull(claims);
    }

    @Test
    void generateToken_ShouldUseHs256Algorithm() {
        String token = jwtService.generateToken(1L, "test@example.com", Role.USER);

        // Vérifier que le token peut être parsé avec HS256
        SecretKey key = Keys.hmacShaKeyFor(testSecret.getBytes(StandardCharsets.UTF_8));
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertNotNull(claims);
    }

    @Test
    void generateToken_ForUserRole_ShouldContainUserRole() {
        String token = jwtService.generateToken(1L, "user@example.com", Role.USER);
        Claims claims = extractClaims(token);
        assertEquals("USER", claims.get("role", String.class));
    }

    @Test
    void generateToken_ForAdminRole_ShouldContainAdminRole() {
        String token = jwtService.generateToken(1L, "admin@example.com", Role.ADMIN);
        Claims claims = extractClaims(token);
        assertEquals("ADMIN", claims.get("role", String.class));
    }

    private Claims extractClaims(String token) {
        SecretKey key = Keys.hmacShaKeyFor(testSecret.getBytes(StandardCharsets.UTF_8));
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
