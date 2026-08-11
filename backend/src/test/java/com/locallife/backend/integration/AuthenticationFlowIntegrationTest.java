package com.locallife.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.locallife.backend.activity.api.ActivityController.CreateActivityRequest;
import com.locallife.backend.auth.api.LoginRequest;
import com.locallife.backend.auth.api.LoginResponse;
import com.locallife.backend.auth.api.RegisterRequest;
import com.locallife.backend.geocoding.application.Coordinates;
import com.locallife.backend.geocoding.application.GeocodingService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Tests d'intégration bout en bout (LL-3014) : inscription -> connexion ->
 * accès à un endpoint protégé, soumission d'une activité avec géocodage, et
 * refus d'accès sur JWT expiré ou absent.
 *
 * Tourne contre la base réelle et un serveur embarqué (même approche que
 * {@code ActivityControllerIntegrationTest}), mais isole le géocodage
 * (Nominatim) via un bean mocké : cela évite de dépendre du réseau externe
 * et de la politique de taux de Nominatim (1 req/s) pendant les tests, tout
 * en gardant les coordonnées obtenues déterministes et vérifiables.
 *
 * Chaque test crée son propre utilisateur avec un email unique (UUID) car,
 * contrairement à {@code UserRepositoryIntegrationTest}, ces appels passent
 * par de vraies requêtes HTTP sur un serveur embarqué : une transaction de
 * test ne pourrait pas annuler les écritures faites sur le thread du
 * serveur.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthenticationFlowIntegrationTest {

    private static final String PASSWORD = "motDePasse123";

    @LocalServerPort
    private int port;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @MockitoBean
    private GeocodingService geocodingService;

    private RestTestClient restTestClient() {
        return RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    private String uniqueEmail() {
        return "test-" + UUID.randomUUID() + "@example.com";
    }

    /** Inscrit un utilisateur puis se connecte, et renvoie son token JWT. */
    private String registerAndLogin(String email) {
        restTestClient().post().uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new RegisterRequest("alice", email, PASSWORD))
                .exchange()
                .expectStatus().isCreated();

        LoginResponse login = restTestClient().post().uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new LoginRequest(email, PASSWORD))
                .exchange()
                .expectStatus().isOk()
                .expectBody(LoginResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(login).isNotNull();
        assertThat(login.token()).isNotBlank();
        return login.token();
    }

    private String expiredToken() {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        long oneDayMillis = 86_400_000L;
        return Jwts.builder()
                .claim("userId", 1L)
                .claim("email", "expired@example.com")
                .claim("role", "USER")
                .issuedAt(new Date(System.currentTimeMillis() - (2 * oneDayMillis)))
                .expiration(new Date(System.currentTimeMillis() - oneDayMillis))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    // --- Inscription -> connexion -> accès protégé (succès) ---

    @Test
    void registerThenLogin_ShouldAllowAccessToProtectedEndpoint() {
        String token = registerAndLogin(uniqueEmail());

        when(geocodingService.geocode("1 rue de la Paix, Marseille")).thenReturn(new Coordinates(43.29, 5.37));

        restTestClient().post().uri("/api/v1/activities")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateActivityRequest(
                        "Pétanque entre voisins", "Partie amicale", "sport", "1 rue de la Paix, Marseille"))
                .exchange()
                .expectStatus().isCreated();
    }

    // --- Soumission d'une activité avec une adresse -> géocodage sauvegardé ---

    @Test
    void createActivity_ShouldPersistCoordinatesReturnedByGeocoding() {
        String token = registerAndLogin(uniqueEmail());

        when(geocodingService.geocode("Vieux-Port, Marseille")).thenReturn(new Coordinates(43.2951, 5.3739));

        restTestClient().post().uri("/api/v1/activities")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateActivityRequest(
                        "Rassemblement", "Description", "sport", "Vieux-Port, Marseille"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.latitude").isEqualTo(43.2951)
                .jsonPath("$.longitude").isEqualTo(5.3739)
                .jsonPath("$.status").isEqualTo("PENDING");
    }

    // --- Échecs ---

    @Test
    void register_ShouldFail_WhenEmailAlreadyUsed() {
        String email = uniqueEmail();
        registerAndLogin(email);

        restTestClient().post().uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new RegisterRequest("alice-bis", email, PASSWORD))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void login_ShouldFail_WhenPasswordIsIncorrect() {
        String email = uniqueEmail();
        registerAndLogin(email);

        restTestClient().post().uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new LoginRequest(email, "mauvaisMotDePasse"))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void createActivity_ShouldBeRefused_WhenNoTokenProvided() {
        restTestClient().post().uri("/api/v1/activities")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateActivityRequest("Titre", "Description", "sport", "Une adresse"))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void createActivity_ShouldBeRefused_WhenTokenIsExpired() {
        restTestClient().post().uri("/api/v1/activities")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateActivityRequest("Titre", "Description", "sport", "Une adresse"))
                .exchange()
                .expectStatus().isUnauthorized();
    }

}
