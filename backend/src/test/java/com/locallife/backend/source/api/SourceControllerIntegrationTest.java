package com.locallife.backend.source.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Tests d'intégration bout en bout pour {@code SourceController} (LL-6007,
 * Sprint 6). Périmètre volontairement réduit (contrairement à
 * {@code AdminActivityControllerIntegrationTest}) : endpoints non
 * protégés, aucun cas d'authentification/autorisation à couvrir — seuls
 * les comportements HTTP de base (200, 404) sont vérifiés ici, la
 * logique elle-même (délégation à {@code SourceService}) est déjà
 * couverte par {@code SourceControllerTest} (tests unitaires).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SourceControllerIntegrationTest {

    @LocalServerPort
    private int port;

    private RestTestClient restTestClient() {
        return RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    void getAllSources_ShouldReturnOk_WithoutAuthentication() {
        // Confirme que l'endpoint reste accessible sans JWT (décision documentée dans SourceController).
        restTestClient().get().uri("/api/v1/sources").exchange().expectStatus().isOk();
    }

    @Test
    void getSourceById_ShouldReturnOk_WhenSourceExists() {
        // La source réservée MANUAL (migration V8) existe toujours, id stable dès le premier jeu de données.
        restTestClient().get().uri("/api/v1/sources/1").exchange().expectStatus().isOk();
    }

    @Test
    void getSourceById_ShouldReturnNotFound_WhenSourceDoesNotExist() {
        restTestClient().get().uri("/api/v1/sources/999999").exchange().expectStatus().isNotFound();
    }

}
