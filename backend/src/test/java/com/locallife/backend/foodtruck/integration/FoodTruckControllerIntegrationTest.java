package com.locallife.backend.foodtruck.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.locallife.backend.auth.api.LoginRequest;
import com.locallife.backend.auth.api.LoginResponse;
import com.locallife.backend.auth.api.RegisterRequest;
import com.locallife.backend.common.IntegrationTestCleaner;
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
 * Tests d'intégration bout en bout pour {@code FoodTruckController} (LL-6009) : {@code GET /api/v1/foodtrucks}
 * public ; l'ancienne création directe {@code POST /api/v1/foodtrucks} n'existe plus (22/10/2026).
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
    void legacyCreationRoute_ShouldNoLongerCreateAFoodTruck() {
        // Retirée le 22/10/2026 : elle publiait sans validation. La création passe par /api/v1/pro/foodtrucks.
        String body = "{\"name\":\"Ancienne route\",\"category\":\"burger\",\"latitude\":43.29,"
                + "\"longitude\":5.37}";
        restTestClient().post().uri("/api/v1/foodtrucks").contentType(MediaType.APPLICATION_JSON).body(body)
                .exchange().expectStatus().isUnauthorized();
        String token = registerAndLoginAsPro(uniqueEmail());
        int status = restTestClient().post().uri("/api/v1/foodtrucks")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).body(body)
                .exchange().returnResult(String.class).getStatus().value();
        assertThat(status).isGreaterThanOrEqualTo(400);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM food_truck WHERE name = 'Ancienne route'",
                Long.class)).isZero();
    }
}
