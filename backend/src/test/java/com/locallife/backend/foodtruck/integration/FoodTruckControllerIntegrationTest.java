package com.locallife.backend.foodtruck.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.locallife.backend.auth.api.LoginRequest;
import com.locallife.backend.auth.api.LoginResponse;
import com.locallife.backend.auth.api.RegisterRequest;
import com.locallife.backend.common.IntegrationTestCleaner;
import com.locallife.backend.foodtruck.api.FoodTruckController;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Tests d'intégration bout en bout pour {@code FoodTruckController}
 * (LL-6009, Sprint 6) : {@code GET /api/v1/foodtrucks} public, {@code
 * POST /api/v1/foodtrucks} protégé (utilisateur connecté requis, même
 * posture que {@code POST /api/v1/activities}, voir {@code
 * SecurityConfig}). Même approche que
 * {@code AdminActivityControllerIntegrationTest} (serveur embarqué, base
 * réelle) mais sans mock de géocodage : la création reçoit directement
 * latitude/longitude (voir {@code FoodTruckService}), aucune dépendance
 * réseau à isoler ici.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FoodTruckControllerIntegrationTest {

    private static final String PASSWORD = "motDePasse123";

    @LocalServerPort
    private int port;

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

    /** Inscrit un utilisateur normal (rôle USER) puis se connecte, et renvoie son token JWT. */
    private String registerAndLoginAsUser(String email) {
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
        return login.token();
    }

    /**
     * Compte professionnel (PRO-01 lot C) : la création d'un food truck lui est réservée. Le rôle est relu en base
     * à chaque appel ({@code RoleProAccessPolicy}) : le jeton obtenu avant le changement de rôle suffit.
     */
    private String registerAndLoginAsPro(String email) {
        String token = registerAndLoginAsUser(email);
        jdbcTemplate.update("UPDATE users SET role = 'PRO' WHERE email = ?", email);
        return token;
    }

    @Test
    void getAllFoodTrucks_ShouldReturnOk_WithoutAuthentication() {
        restTestClient().get().uri("/api/v1/foodtrucks").exchange().expectStatus().isOk();
    }

    @Test
    void createFoodTruck_ShouldReturnCreated_WhenAuthenticatedAndValid() {
        // Given
        String token = registerAndLoginAsPro(uniqueEmail());

        // When / Then
        restTestClient().post().uri("/api/v1/foodtrucks")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new FoodTruckController.CreateFoodTruckRequest(
                        cleaner.trackFoodTruck("Le Camion qui Fume " + UUID.randomUUID()),
                        "Burgers gourmet", 43.29, 5.37, "burger", null))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.status").isEqualTo("PUBLISHED")
                // Date de publication posée par la base (trigger V55) et renvoyée à la création.
                .jsonPath("$.publishedAt").isNotEmpty();
    }

    @Test
    void createFoodTruck_ShouldBeVisibleOnPublicListing_AfterCreation() {
        // Given : un food truck créé via le flux protégé normal.
        String token = registerAndLoginAsPro(uniqueEmail());
        String uniqueName = cleaner.trackFoodTruck("Le Camion qui Fume " + UUID.randomUUID());
        restTestClient().post().uri("/api/v1/foodtrucks")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new FoodTruckController.CreateFoodTruckRequest(
                        uniqueName, "desc", 43.29, 5.37, "burger", null))
                .exchange()
                .expectStatus().isCreated();

        // When / Then : critère d'acceptation « visibilité sur la carte » — retrouvable
        // immédiatement sur la consultation publique, sans étape de modération.
        String body = restTestClient().get().uri("/api/v1/foodtrucks")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
        assertThat(body).contains(uniqueName);
        // ADR-0013 : la position reçue est devenue une présence permanente, plus une colonne du food truck.
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM food_truck_presence p "
                + "JOIN food_truck ft ON ft.id = p.food_truck_id JOIN location l ON l.id = p.location_id "
                + "WHERE ft.name = ? AND ft.latitude IS NULL AND l.latitude = 43.29 AND l.longitude = 5.37 "
                + "AND l.place_type IS NULL AND p.days_of_week IS NULL AND p.valid_until IS NULL", Long.class,
                uniqueName)).isEqualTo(1);
    }

    @Test
    void createFoodTruck_ShouldBeForbidden_ForANonProfessionalAccount() {
        String token = registerAndLoginAsUser(uniqueEmail());
        restTestClient().post().uri("/api/v1/foodtrucks")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new FoodTruckController.CreateFoodTruckRequest(
                        cleaner.trackFoodTruck("Refusé " + UUID.randomUUID()), "desc", 43.29, 5.37, "burger", null))
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void createFoodTruck_ShouldReturnUnauthorized_WhenNoTokenProvided() {
        restTestClient().post().uri("/api/v1/foodtrucks")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new FoodTruckController.CreateFoodTruckRequest(
                        "Le Camion qui Fume", "desc", 43.29, 5.37, "burger", null))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void createFoodTruck_ShouldReturnBadRequest_WhenNameMissing() {
        String token = registerAndLoginAsPro(uniqueEmail());

        restTestClient().post().uri("/api/v1/foodtrucks")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new FoodTruckController.CreateFoodTruckRequest(
                        "", "desc", 43.29, 5.37, "burger", null))
                .exchange()
                .expectStatus().isBadRequest();
    }

}
