package com.locallife.backend.foodtruck.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.locallife.backend.auth.api.LoginRequest;
import com.locallife.backend.auth.api.LoginResponse;
import com.locallife.backend.auth.api.RegisterRequest;
import com.locallife.backend.foodtruck.api.FoodTruckController;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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

    private RestTestClient restTestClient() {
        return RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    private String uniqueEmail() {
        return "test-" + UUID.randomUUID() + "@example.com";
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

    @Test
    void getAllFoodTrucks_ShouldReturnOk_WithoutAuthentication() {
        restTestClient().get().uri("/api/v1/foodtrucks").exchange().expectStatus().isOk();
    }

    @Test
    void createFoodTruck_ShouldReturnCreated_WhenAuthenticatedAndValid() {
        // Given
        String token = registerAndLoginAsUser(uniqueEmail());

        // When / Then
        restTestClient().post().uri("/api/v1/foodtrucks")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new FoodTruckController.CreateFoodTruckRequest(
                        "Le Camion qui Fume " + UUID.randomUUID(), "Burgers gourmet", 43.29, 5.37, "burger", null))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.status").isEqualTo("PUBLISHED");
    }

    @Test
    void createFoodTruck_ShouldBeVisibleOnPublicListing_AfterCreation() {
        // Given : un food truck créé via le flux protégé normal.
        String token = registerAndLoginAsUser(uniqueEmail());
        String uniqueName = "Le Camion qui Fume " + UUID.randomUUID();
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
        String token = registerAndLoginAsUser(uniqueEmail());

        restTestClient().post().uri("/api/v1/foodtrucks")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new FoodTruckController.CreateFoodTruckRequest(
                        "", "desc", 43.29, 5.37, "burger", null))
                .exchange()
                .expectStatus().isBadRequest();
    }

}
