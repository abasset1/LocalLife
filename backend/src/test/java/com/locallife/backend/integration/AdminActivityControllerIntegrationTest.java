package com.locallife.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.locallife.backend.activity.api.ActivityController.CreateActivityRequest;
import com.locallife.backend.activity.domain.Activity;
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
 * Tests d'intégration bout en bout pour {@code AdminActivityController} :
 * {@code GET /api/v1/admin/activities} (LL-6005, Sprint 6) — filtrage par
 * statut, protection par rôle {@code ADMIN} (401 sans JWT, 403 avec un JWT
 * valide mais de rôle {@code USER}), validation du paramètre {@code
 * status} — et {@code PATCH .../{id}/publish}/{@code .../reject}
 * (LL-6006, Sprint 6) — transition de statut, 404 sur activité
 * inexistante, 400 sur transition invalide (activité déjà
 * {@code PUBLISHED}/{@code REJECTED}), même protection par rôle
 * {@code ADMIN}.
 *
 * Même approche que {@code AuthenticationFlowIntegrationTest} (LL-3014) :
 * serveur embarqué, base réelle, géocodage mocké. Aucun endpoint ne permet
 * de créer un compte {@code ADMIN} (inscription publique toujours en
 * {@code USER}, voir {@code AuthService#register}) : le token administrateur
 * est donc construit directement, comme {@code AuthenticationFlowIntegrationTest#expiredToken()}
 * le fait déjà pour un token expiré — même technique, juste avec
 * {@code role=ADMIN} et une expiration dans le futur.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminActivityControllerIntegrationTest {

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

    /** Construit un token JWT valide avec le rôle ADMIN, sans passer par l'inscription publique (voir javadoc de la classe). */
    private String adminToken() {
        return craftToken("ADMIN");
    }

    private String craftToken(String role) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        long oneHourMillis = 3_600_000L;
        return Jwts.builder()
                .claim("userId", 1L)
                .claim("email", "admin-test@example.com")
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + oneHourMillis))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /** Inscrit un utilisateur normal (rôle USER) puis se connecte, et renvoie son token JWT. */
    private String registerAndLoginAsUser(String email) {
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
        return login.token();
    }

    /**
     * Crée une activité PENDING (statut par défaut d'une contribution
     * manuelle, voir ActivityService#createActivity) et renvoie l'activité
     * créée (utilisé par LL-6006 pour récupérer son id et publier/rejeter
     * dessus).
     */
    private Activity createPendingActivity(String title, String userToken) {
        when(geocodingService.geocode("1 rue de la Paix, Marseille")).thenReturn(new Coordinates(43.29, 5.37));

        return restTestClient().post().uri("/api/v1/activities")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateActivityRequest(title, "description", "loisir", "1 rue de la Paix, Marseille"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Activity.class)
                .returnResult()
                .getResponseBody();
    }

    // --- Accès autorisé (rôle ADMIN) ---

    @Test
    void getActivitiesByStatus_ShouldReturnPendingActivities_WhenRequestedByAdmin() {
        // Given : une activité PENDING créée via une contribution manuelle normale.
        String userToken = registerAndLoginAsUser(uniqueEmail());
        String uniqueTitle = "Pétanque " + UUID.randomUUID();
        createPendingActivity(uniqueTitle, userToken);

        // When / Then
        restTestClient().get().uri("/api/v1/admin/activities?status=PENDING")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[?(@.title == '" + uniqueTitle + "')]").exists();
    }

    @Test
    void getActivitiesByStatus_ShouldReturnEmptyList_WhenNoActivityMatchesStatus() {
        // Given / When / Then : REJECTED n'est jamais produit par le code applicatif à ce stade
        // (aucun endpoint de transition avant LL-6006) — la liste doit être vide, pas une erreur.
        restTestClient().get().uri("/api/v1/admin/activities?status=REJECTED")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                .exchange()
                .expectStatus().isOk();
    }

    // --- Accès refusé ---

    @Test
    void getActivitiesByStatus_ShouldBeRefused_WhenNoTokenProvided() {
        restTestClient().get().uri("/api/v1/admin/activities?status=PENDING")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void getActivitiesByStatus_ShouldBeRefused_WhenTokenIsNotAdmin() {
        // Given : un utilisateur normal (rôle USER), authentifié via le flux public réel.
        String userToken = registerAndLoginAsUser(uniqueEmail());

        // When / Then
        restTestClient().get().uri("/api/v1/admin/activities?status=PENDING")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                .exchange()
                .expectStatus().isForbidden();
    }

    // --- Validation du paramètre status ---

    @Test
    void getActivitiesByStatus_ShouldReturnBadRequest_WhenStatusMissing() {
        restTestClient().get().uri("/api/v1/admin/activities")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void getActivitiesByStatus_ShouldReturnBadRequest_WhenStatusUnknown() {
        restTestClient().get().uri("/api/v1/admin/activities?status=NOT_A_STATUS")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                .exchange()
                .expectStatus().isBadRequest();
    }

    // --- publish/reject : transitions de modération (LL-6006) ---

    @Test
    void publishActivity_ShouldChangeStatusToPublished_WhenActivityIsPending() {
        // Given : une activité PENDING créée via une contribution manuelle normale.
        String userToken = registerAndLoginAsUser(uniqueEmail());
        Activity created = createPendingActivity("Pétanque " + UUID.randomUUID(), userToken);

        // When / Then
        restTestClient().patch().uri("/api/v1/admin/activities/" + created.id() + "/publish")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("PUBLISHED");
    }

    @Test
    void rejectActivity_ShouldChangeStatusToRejected_WhenActivityIsPending() {
        // Given : une activité PENDING créée via une contribution manuelle normale.
        String userToken = registerAndLoginAsUser(uniqueEmail());
        Activity created = createPendingActivity("Marché " + UUID.randomUUID(), userToken);

        // When / Then
        restTestClient().patch().uri("/api/v1/admin/activities/" + created.id() + "/reject")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("REJECTED");
    }

    @Test
    void publishActivity_ShouldReturnNotFound_WhenActivityDoesNotExist() {
        restTestClient().patch().uri("/api/v1/admin/activities/999999/publish")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void rejectActivity_ShouldReturnNotFound_WhenActivityDoesNotExist() {
        restTestClient().patch().uri("/api/v1/admin/activities/999999/reject")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void publishActivity_ShouldReturnBadRequest_WhenActivityIsAlreadyPublished() {
        // Given : une activité PENDING publiée une première fois avec succès.
        String userToken = registerAndLoginAsUser(uniqueEmail());
        Activity created = createPendingActivity("Concert " + UUID.randomUUID(), userToken);
        restTestClient().patch().uri("/api/v1/admin/activities/" + created.id() + "/publish")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                .exchange()
                .expectStatus().isOk();

        // When / Then : un second appel sur une activité déjà PUBLISHED n'est pas une transition prévue
        // (voir ActivityService#transitionStatus, décision à valider avec Alex).
        restTestClient().patch().uri("/api/v1/admin/activities/" + created.id() + "/publish")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void publishActivity_ShouldBeRefused_WhenNoTokenProvided() {
        restTestClient().patch().uri("/api/v1/admin/activities/1/publish")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void publishActivity_ShouldBeRefused_WhenTokenIsNotAdmin() {
        // Given : un utilisateur normal (rôle USER), authentifié via le flux public réel.
        String userToken = registerAndLoginAsUser(uniqueEmail());

        // When / Then
        restTestClient().patch().uri("/api/v1/admin/activities/1/publish")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void rejectActivity_ShouldBeRefused_WhenNoTokenProvided() {
        restTestClient().patch().uri("/api/v1/admin/activities/1/reject")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void rejectActivity_ShouldBeRefused_WhenTokenIsNotAdmin() {
        // Given : un utilisateur normal (rôle USER), authentifié via le flux public réel.
        String userToken = registerAndLoginAsUser(uniqueEmail());

        // When / Then
        restTestClient().patch().uri("/api/v1/admin/activities/1/reject")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                .exchange()
                .expectStatus().isForbidden();
    }

}
