package com.locallife.backend.source.api;

import com.locallife.backend.common.TestAccounts;
import com.locallife.backend.user.domain.Role;
import static org.assertj.core.api.Assertions.assertThat;

import com.locallife.backend.common.IntegrationTestCleaner;
import com.locallife.backend.source.api.SourceController.CreateSourceRequest;
import com.locallife.backend.source.api.SourceController.UpdateSourceRequest;
import com.locallife.backend.source.domain.Source;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Tests d'intégration bout en bout pour {@code SourceController}. Les
 * lectures (LL-6007, Sprint 6) restent non protégées, comme documenté sur
 * le contrôleur — seuls les comportements HTTP de base (200, 404) sont
 * vérifiés. Depuis LL-EF-005, les écritures (création/modification/
 * suppression d'un agenda) sont réservées au rôle {@code ADMIN} : cette
 * classe suit donc désormais le même schéma que
 * {@code AdminActivityControllerIntegrationTest} (401 sans JWT, 403 avec
 * un JWT valide mais de rôle {@code USER}, token {@code ADMIN} construit
 * directement — pas d'endpoint public pour créer un compte {@code ADMIN}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SourceControllerIntegrationTest {

    /** Comptes réels : le rôle est relu en base par JwtFilter (voir TestAccounts). */
    @Autowired
    private TestAccounts testAccounts;

    @LocalServerPort
    private int port;

    @Value("${jwt.secret}")
    private String jwtSecret;

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
        return RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    /** Construit un token JWT valide avec le rôle donné, comme {@code AdminActivityControllerIntegrationTest}. */
    private String craftToken(String role) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        long oneHourMillis = 3_600_000L;
        return Jwts.builder()
                .claim("userId", testAccounts.sharedAccount(Role.valueOf(role)))
                .claim("email", "source-test@example.com")
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + oneHourMillis))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    private String adminToken() {
        return craftToken("ADMIN");
    }

    private String userToken() {
        return craftToken("USER");
    }

    private String uniqueName() {
        return cleaner.trackSource("Source de test " + UUID.randomUUID());
    }

    // --- Lecture (LL-6007 ; réservée au rôle ADMIN depuis le 21/10/2026, auparavant publique) ---

    @Test
    void getAllSources_ShouldBeReservedToAdmins() {
        restTestClient().get().uri("/api/v1/sources").exchange().expectStatus().isUnauthorized();
        restTestClient().get().uri("/api/v1/sources").header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken())
                .exchange().expectStatus().isForbidden();
        restTestClient().get().uri("/api/v1/sources").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                .exchange().expectStatus().isOk();
    }

    @Test
    void getSourceById_ShouldReturnOk_WhenSourceExists() {
        // La source réservée MANUAL (migration V8) existe toujours, id stable dès le premier jeu de données.
        restTestClient().get().uri("/api/v1/sources/1").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                .exchange().expectStatus().isOk();
    }

    @Test
    void getSourceById_ShouldReturnNotFound_WhenSourceDoesNotExist() {
        restTestClient().get().uri("/api/v1/sources/999999")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                .exchange().expectStatus().isNotFound();
    }

    // --- Création (LL-EF-005) ---

    @Test
    void createSource_ShouldReturnCreated_WhenRequestedByAdmin() {
        CreateSourceRequest request = new CreateSourceRequest(uniqueName(), "API", null, "agenda-uid", null);

        restTestClient().post().uri("/api/v1/sources")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Source.class)
                .value(source -> assertThat(source.status()).isEqualTo("ACTIVE"));
    }

    @Test
    void createSource_ShouldReturnBadRequest_WhenNameIsBlank() {
        CreateSourceRequest request = new CreateSourceRequest("", "API", null, null, null);

        restTestClient().post().uri("/api/v1/sources")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange()
                .expectStatus().isBadRequest();
    }

    /**
     * LL-11016 : bug corrigé — cette requête réussissait auparavant
     * (aucune validation ne rejetait le type {@code MANUAL}), créant une
     * seconde source réservée et cassant {@code
     * SourceRepository#findByType("MANUAL")} partout ailleurs dans le
     * projet.
     */
    @Test
    void createSource_ShouldReturnBadRequest_WhenTypeIsManual() {
        CreateSourceRequest request = new CreateSourceRequest(uniqueName(), "MANUAL", null, null, null);

        restTestClient().post().uri("/api/v1/sources")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void createSource_ShouldBeRefused_WhenNoTokenProvided() {
        CreateSourceRequest request = new CreateSourceRequest(uniqueName(), "API", null, null, null);

        restTestClient().post().uri("/api/v1/sources")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void createSource_ShouldBeRefused_WhenTokenIsNotAdmin() {
        CreateSourceRequest request = new CreateSourceRequest(uniqueName(), "API", null, null, null);

        restTestClient().post().uri("/api/v1/sources")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange()
                .expectStatus().isForbidden();
    }

    // --- Modification (LL-EF-005) ---

    private Source createSourceAsAdmin(String name) {
        CreateSourceRequest request = new CreateSourceRequest(name, "API", null, "agenda-uid", null);
        return restTestClient().post().uri("/api/v1/sources")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Source.class)
                .returnResult()
                .getResponseBody();
    }

    @Test
    void updateSource_ShouldReturnOk_WhenRequestedByAdmin() {
        Source created = createSourceAsAdmin(uniqueName());
        UpdateSourceRequest request = new UpdateSourceRequest(
                created.name(), "API", null, "INACTIVE", created.agendaUid(), null);

        restTestClient().put().uri("/api/v1/sources/" + created.id())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Source.class)
                .value(source -> assertThat(source.status()).isEqualTo("INACTIVE"));
    }

    @Test
    void updateSource_ShouldReturnNotFound_WhenSourceDoesNotExist() {
        UpdateSourceRequest request = new UpdateSourceRequest("Nom", "API", null, "ACTIVE", null, null);

        restTestClient().put().uri("/api/v1/sources/999999")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void updateSource_ShouldBeRefused_WhenTokenIsNotAdmin() {
        UpdateSourceRequest request = new UpdateSourceRequest("Nom", "API", null, "ACTIVE", null, null);

        restTestClient().put().uri("/api/v1/sources/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange()
                .expectStatus().isForbidden();
    }

    // --- Suppression (LL-EF-005) ---

    @Test
    void deleteSource_ShouldReturnNoContent_WhenRequestedByAdmin() {
        Source created = createSourceAsAdmin(uniqueName());

        restTestClient().delete().uri("/api/v1/sources/" + created.id())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                .header("X-Confirm-Password", TestAccounts.ADMIN_PASSWORD)
                .exchange()
                .expectStatus().isNoContent();

        restTestClient().get().uri("/api/v1/sources/" + created.id())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void deleteSource_ShouldReturnBadRequest_WhenSourceIsReservedManual() {
        // La source réservée MANUAL (migration V8) a l'id 1, stable dès le premier jeu de données.
        restTestClient().delete().uri("/api/v1/sources/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                .header("X-Confirm-Password", TestAccounts.ADMIN_PASSWORD)
                .exchange()
                .expectStatus().isBadRequest();

        restTestClient().get().uri("/api/v1/sources/1").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                .exchange().expectStatus().isOk();
    }

    @Test
    void deleteSource_ShouldReturnNotFound_WhenSourceDoesNotExist() {
        restTestClient().delete().uri("/api/v1/sources/999999")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                .header("X-Confirm-Password", TestAccounts.ADMIN_PASSWORD)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void deleteSource_ShouldBeRefused_WhenNoTokenProvided() {
        Source created = createSourceAsAdmin(uniqueName());

        restTestClient().delete().uri("/api/v1/sources/" + created.id())
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void deleteSource_ShouldBeRefused_WhenTokenIsNotAdmin() {
        Source created = createSourceAsAdmin(uniqueName());

        restTestClient().delete().uri("/api/v1/sources/" + created.id())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken())
                .exchange()
                .expectStatus().isForbidden();
    }

}
