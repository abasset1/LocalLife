package com.locallife.backend.integration;

import com.locallife.backend.common.TestAccounts;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.locallife.backend.activity.api.ActivityController.CreateActivityRequest;
import com.locallife.backend.auth.api.LoginRequest;
import com.locallife.backend.auth.api.LoginResponse;
import com.locallife.backend.auth.api.RegisterRequest;
import com.locallife.backend.auth.api.VerifyEmailRequest;
import com.locallife.backend.auth.application.EmailService;
import com.locallife.backend.common.IntegrationTestCleaner;
import com.locallife.backend.geocoding.application.Coordinates;
import com.locallife.backend.geocoding.application.GeocodingService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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

    /** Contribuer exige Premium depuis le 21/10/2026 : les contributeurs de ces tests le reçoivent. */
    @Autowired
    private TestAccounts testAccounts;

    private static final String PASSWORD = "motDePasse123";

    @LocalServerPort
    private int port;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @MockitoBean
    private GeocodingService geocodingService;

    /** Évite tout envoi SMTP réel et permet de récupérer le lien de confirmation envoyé. */
    @MockitoBean
    private EmailService emailService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** Simule le clic sur le lien de confirmation reçu par email (voir EmailVerificationService). */
    private void markEmailVerified(String email) {
        jdbcTemplate.update("UPDATE users SET email_verified = TRUE WHERE email = ?", email);
    }

    @Autowired
    private IntegrationTestCleaner cleaner;

    /** Test non transactionnel (serveur embarqué) : ce qu'il crée est supprimé après chaque test. */
    @BeforeEach
    void startCleaner() {
        cleaner.start();
    }

    @AfterEach
    void cleanUpTestData() {
        cleaner.cleanUp();
    }

    private RestTestClient restTestClient() {
        return RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    private String uniqueEmail() {
        return cleaner.trackUser("test-" + UUID.randomUUID() + "@example.com");
    }

    /** Inscrit un utilisateur puis se connecte, et renvoie son token JWT. */
    private String registerAndLogin(String email) {
        restTestClient().post().uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new RegisterRequest("u-" + UUID.randomUUID().toString().substring(0, 12), email, PASSWORD))
                .exchange()
                .expectStatus().isCreated();
        markEmailVerified(email);

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

    /**
     * JWT syntaxiquement valide mais non signé ("plaintext JWT", RFC 7519).
     * {@code JwtFilter} attend systématiquement un JWS (jeton signé) via
     * {@code parseSignedClaims} : un jeton non signé déclenche
     * {@code UnsupportedJwtException} plutôt que {@code SignatureException}
     * (LL-9006, constat non bloquant de l'audit LL-9004 — la classe
     * d'exception n'était jusqu'ici pas capturée).
     */
    private String unsupportedToken() {
        return Jwts.builder()
                .claim("userId", 1L)
                .claim("email", "unsupported@example.com")
                .claim("role", "USER")
                .compact();
    }

    // --- Confirmation d'adresse email ---

    @Test
    void login_ShouldBeRefusedUntilEmailIsConfirmedThroughTheEmailedLink() {
        String email = uniqueEmail();
        restTestClient().post().uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new RegisterRequest("u-" + UUID.randomUUID().toString().substring(0, 12), email, PASSWORD))
                .exchange()
                .expectStatus().isCreated();

        // Identifiants corrects mais adresse non confirmée -> 403 (pas 401).
        restTestClient().post().uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new LoginRequest(email, PASSWORD))
                .exchange()
                .expectStatus().isForbidden();

        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendEmailVerificationEmail(eq(email), link.capture());
        String token = link.getValue().substring(link.getValue().indexOf("token=") + "token=".length());

        restTestClient().post().uri("/api/v1/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new VerifyEmailRequest(token))
                .exchange()
                .expectStatus().isOk();

        restTestClient().post().uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new LoginRequest(email, PASSWORD))
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void verifyEmail_ShouldReturnBadRequest_WhenTokenIsUnknown() {
        restTestClient().post().uri("/api/v1/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new VerifyEmailRequest("token-inconnu"))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void login_ShouldReturnUnauthorized_WhenPasswordIsWrongOnUnconfirmedAccount() {
        // Ne doit pas révéler l'existence d'un compte non confirmé : 401 comme pour tout mauvais mot de passe.
        String email = uniqueEmail();
        restTestClient().post().uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new RegisterRequest("u-" + UUID.randomUUID().toString().substring(0, 12), email, PASSWORD))
                .exchange()
                .expectStatus().isCreated();

        restTestClient().post().uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new LoginRequest(email, "mauvaisMotDePasse"))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    // --- Inscription -> connexion -> accès protégé (succès) ---

    @Test
    void registerThenLogin_ShouldAllowAccessToProtectedEndpoint() {
        String email = uniqueEmail();
        String token = registerAndLogin(email);
        CreateActivityRequest request = new CreateActivityRequest(
                "Pétanque entre voisins", "Partie amicale", "sport", "1 rue de la Paix, Marseille",
                null, null, null, null, null,
                "CULTURE");

        when(geocodingService.geocode("1 rue de la Paix, Marseille")).thenReturn(new Coordinates(43.29, 5.37, null, null));

        // Le jeton ouvre les routes du compte ; proposer une activité exige en plus Premium (21/10/2026).
        restTestClient().get().uri("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.canContribute").isEqualTo(false);
        restTestClient().post().uri("/api/v1/activities")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange()
                .expectStatus().isForbidden();

        testAccounts.grantPremium(email);

        restTestClient().post().uri("/api/v1/activities")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange()
                .expectStatus().isCreated();
    }

    // --- Soumission d'une activité avec une adresse -> géocodage sauvegardé ---

    @Test
    void createActivity_ShouldPersistCoordinatesReturnedByGeocoding() {
        String email = uniqueEmail();
        String token = registerAndLogin(email);
        testAccounts.grantPremium(email);

        when(geocodingService.geocode("Vieux-Port, Marseille")).thenReturn(new Coordinates(43.2951, 5.3739, null, null));

        restTestClient().post().uri("/api/v1/activities")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateActivityRequest(
                        "Rassemblement", "Description", "sport", "Vieux-Port, Marseille", null, null, null, null, null,
                "CULTURE"))
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
                .body(new RegisterRequest("u-" + UUID.randomUUID().toString().substring(0, 12), email, PASSWORD))
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
                .body(new CreateActivityRequest(
                        "Titre", "Description", "sport", "Une adresse", null, null, null, null, null,
                "CULTURE"))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void createActivity_ShouldBeRefused_WhenTokenIsExpired() {
        restTestClient().post().uri("/api/v1/activities")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateActivityRequest(
                        "Titre", "Description", "sport", "Une adresse", null, null, null, null, null,
                "CULTURE"))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void createActivity_ShouldBeRefused_WhenTokenIsUnsupported() {
        restTestClient().post().uri("/api/v1/activities")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + unsupportedToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateActivityRequest(
                        "Titre", "Description", "sport", "Une adresse", null, null, null, null, null,
                "CULTURE"))
                .exchange()
                .expectStatus().isUnauthorized();
    }

}
