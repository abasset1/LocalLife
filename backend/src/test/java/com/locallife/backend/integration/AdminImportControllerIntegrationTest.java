package com.locallife.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.locallife.backend.activity.domain.Activity;
import com.locallife.backend.activity.infrastructure.ActivityRepository;
import com.locallife.backend.auth.api.LoginRequest;
import com.locallife.backend.auth.api.LoginResponse;
import com.locallife.backend.auth.api.RegisterRequest;
import com.locallife.backend.collector.application.ImportResult;
import com.locallife.backend.collector.domain.CollectedActivity;
import com.locallife.backend.collector.domain.Collector;
import com.locallife.backend.source.domain.Source;
import com.locallife.backend.source.infrastructure.SourceRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Tests d'intégration bout en bout pour {@code AdminImportController}
 * (LL-7002, Sprint 7) : {@code POST /api/v1/admin/import}.
 *
 * Même approche que {@code AdminActivityControllerIntegrationTest}
 * (LL-6005/LL-6006) pour la construction des tokens ({@code adminToken()}
 * fabriqué directement, aucun endpoint ne permettant de créer un compte
 * {@code ADMIN}) — et même approche que {@code ImportServiceIntegrationTest}
 * (LL-5010) pour isoler le pipeline d'un appel réseau réel : le
 * {@code Collector} enregistré ({@code OpenAgendaCollector}) est remplacé
 * par un mock ({@code @MockitoBean}), seule frontière externe du pipeline.
 *
 * Ne revérifie pas le détail du pipeline lui-même (création/mise à
 * jour/rejet/erreurs), déjà couvert exhaustivement par
 * {@code ImportServiceTest}/{@code ImportServiceIntegrationTest}
 * (LL-5008/LL-5010) : se concentre sur ce qui est propre à LL-7002 — la
 * protection par rôle {@code ADMIN} et le fait que l'appel HTTP déclenche
 * réellement {@code ImportService#importAll()} via le pipeline existant.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminImportControllerIntegrationTest {

    private static final String PASSWORD = "motDePasse123";

    @LocalServerPort
    private int port;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @MockitoBean
    private Collector collector;

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private SourceRepository sourceRepository;

    private RestTestClient restTestClient() {
        return RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    private String uniqueEmail() {
        return "test-" + UUID.randomUUID() + "@example.com";
    }

    /** Construit un token JWT valide avec le rôle ADMIN, voir AdminActivityControllerIntegrationTest. */
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

    private String uniqueSourceName() {
        return "Test Source " + UUID.randomUUID();
    }

    private CollectedActivity validItem(String sourceName, String title) {
        return new CollectedActivity(
                title, "description", LocalDateTime.now().plusDays(1), null,
                "marché", 43.2965, 5.3698, "https://example.com", "ext-1", sourceName);
    }

    // --- Accès autorisé (rôle ADMIN) ---

    @Test
    void triggerImport_ShouldReturnOkWithResult_WhenCalledByAdmin() {
        // Given : un collecteur renvoyant une donnée valide, pour vérifier que l'appel HTTP
        // déclenche réellement le pipeline existant (pas de duplication de logique).
        String sourceName = uniqueSourceName();
        when(collector.getSourceName()).thenReturn(sourceName);
        when(collector.collect()).thenReturn(List.of(validItem(sourceName, "Marché de Noël")));

        // When / Then
        List<ImportResult> results = restTestClient().post().uri("/api/v1/admin/import")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<List<ImportResult>>() { })
                .returnResult()
                .getResponseBody();

        assertThat(results).isNotNull().hasSize(1);
        assertThat(results.get(0).created()).isEqualTo(1);

        // Le pipeline existant a bien été exécuté (pas seulement invoqué en apparence) : l'activité
        // est réellement persistée.
        Long sourceId = sourceRepository.findByName(sourceName).map(Source::id).orElseThrow();
        List<Activity> persisted = activityRepository.findBySourceId(sourceId);
        assertThat(persisted).hasSize(1);
        assertThat(persisted.get(0).title()).isEqualTo("Marché de Noël");
    }

    @Test
    void triggerImport_ShouldReturnOkWithDegradedResult_WhenCollectorFails() {
        // Given : échec de collecte (ex. panne réseau, configuration manquante) — capturé par
        // ImportService, ne doit pas faire échouer l'appel HTTP (voir AdminImportController).
        String sourceName = uniqueSourceName();
        when(collector.getSourceName()).thenReturn(sourceName);
        when(collector.collect()).thenThrow(new RuntimeException("panne réseau"));

        // When / Then
        restTestClient().post().uri("/api/v1/admin/import")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                .exchange()
                .expectStatus().isOk();
    }

    // --- Accès refusé ---

    @Test
    void triggerImport_ShouldBeRefused_WhenNoTokenProvided() {
        restTestClient().post().uri("/api/v1/admin/import")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void triggerImport_ShouldBeRefused_WhenTokenIsNotAdmin() {
        // Given : un utilisateur normal (rôle USER), authentifié via le flux public réel —
        // critère d'acceptation explicite du ticket : « un utilisateur non administrateur ne
        // peut pas déclencher l'import ».
        String userToken = registerAndLoginAsUser(uniqueEmail());

        // When / Then
        restTestClient().post().uri("/api/v1/admin/import")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                .exchange()
                .expectStatus().isForbidden();
    }

}
