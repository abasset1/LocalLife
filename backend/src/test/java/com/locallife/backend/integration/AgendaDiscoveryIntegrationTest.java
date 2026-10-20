package com.locallife.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.locallife.backend.auth.api.LoginRequest;
import com.locallife.backend.auth.api.LoginResponse;
import com.locallife.backend.auth.api.RegisterRequest;
import com.locallife.backend.collector.application.AgendaActivationResult;
import com.locallife.backend.collector.application.AgendaDiscoveryResult;
import com.locallife.backend.collector.application.AgendaDiscoveryRun;
import com.locallife.backend.collector.application.AgendaDiscoveryService;
import com.locallife.backend.collector.application.DiscoveredAgendaView;
import com.locallife.backend.collector.domain.DiscoveredAgenda;
import com.locallife.backend.collector.domain.DiscoveryScope;
import com.locallife.backend.collector.infrastructure.CollectorException;
import com.locallife.backend.collector.infrastructure.OpenAgendaAgendaDirectory;
import com.locallife.backend.collector.infrastructure.OpenAgendaAgendaDirectory.AgendaPage;
import com.locallife.backend.common.IntegrationTestCleaner;
import com.locallife.backend.source.domain.Source;
import com.locallife.backend.source.infrastructure.SourceRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Découverte reprenable, catalogue et création sélective de sources (sprint « agendas exhaustifs »,
 * AGD-02/03/06), sur la vraie base : transactions par page, unicité du catalogue, reprise au curseur
 * enregistré, autorisations ADMIN. L'annuaire OpenAgenda est simulé ({@code @MockitoBean}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AgendaDiscoveryIntegrationTest {

    private static final String PASSWORD = "motDePasse123";

    @LocalServerPort
    private int port;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @MockitoBean
    private OpenAgendaAgendaDirectory directory;

    @Autowired
    private AgendaDiscoveryService discoveryService;

    @Autowired
    private SourceRepository sourceRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private IntegrationTestCleaner cleaner;

    /** Préfixe unique des titres de ce test : les sources créées sont retrouvées et supprimées par nom. */
    private final String prefix = "Agenda IT " + UUID.randomUUID().toString().substring(0, 8) + " ";

    @BeforeEach
    void setUp() {
        cleaner.start();
        // Tables V68 propres à la découverte : seules ces tests y écrivent.
        jdbcTemplate.update("DELETE FROM agenda_discovery_run");
        jdbcTemplate.update("DELETE FROM discovered_agenda");
        doNothing().when(directory).requireApiKey();
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.queryForList("SELECT name FROM source WHERE name LIKE ?", String.class, prefix + "%")
                .forEach(cleaner::trackSource);
        cleaner.cleanUp();
        jdbcTemplate.update("DELETE FROM agenda_discovery_run");
        jdbcTemplate.update("DELETE FROM discovered_agenda");
    }

    // --- Annuaire simulé ---

    /** Agendas d'uid {@code first} à {@code first + count - 1}. */
    private List<DiscoveredAgenda> agendas(int first, int count) {
        List<DiscoveredAgenda> agendas = new ArrayList<>();
        for (int uid = first; uid < first + count; uid++) {
            agendas.add(new DiscoveredAgenda(String.valueOf(900_000 + uid), prefix + uid, "slug-" + uid, uid % 2 == 0));
        }
        return agendas;
    }

    /** L'annuaire simulé : {@code pages} pages de 3 agendas, curseur [n] après la page n, fin sans curseur. */
    private void givenDirectoryOf(DiscoveryScope scope, int pages) {
        for (int index = 0; index < pages; index++) {
            List<Object> request = index == 0 ? null : List.of(index);
            List<Object> next = index == pages - 1 ? null : List.of(index + 1);
            AgendaPage page = new AgendaPage(agendas(index * 3, 3), next, null);
            if (request == null) {
                when(directory.fetchPage(eq(scope), isNull())).thenReturn(page);
            } else {
                when(directory.fetchPage(scope, request)).thenReturn(page);
            }
        }
    }

    private long catalogSize() {
        Long count = jdbcTemplate.queryForObject("SELECT count(*) FROM discovered_agenda", Long.class);
        return count == null ? 0 : count;
    }

    private long sourcesOfThisTest() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM source WHERE name LIKE ?", Long.class, prefix + "%");
        return count == null ? 0 : count;
    }

    // --- Parcours reprenable ---

    @Test
    void discover_ShouldProceedByBatches_ResumeAtTheSavedCursor_AndCompleteOnlyAtTheProviderEnd() {
        givenDirectoryOf(DiscoveryScope.ALL, 5);

        AgendaDiscoveryResult first = discoveryService.discover(DiscoveryScope.ALL, 2, false);
        assertThat(first.status()).isEqualTo(AgendaDiscoveryRun.Status.IN_PROGRESS);
        assertThat(first.complete()).isFalse();
        assertThat(first.resumable()).isTrue();
        assertThat(first.pagesRead()).isEqualTo(2);
        assertThat(first.newAgendas()).isEqualTo(6);

        AgendaDiscoveryResult second = discoveryService.discover(DiscoveryScope.ALL, 2, false);
        assertThat(second.runId()).isEqualTo(first.runId());
        assertThat(second.pagesRead()).isEqualTo(4);
        assertThat(second.complete()).isFalse();

        AgendaDiscoveryResult third = discoveryService.discover(DiscoveryScope.ALL, 2, false);
        assertThat(third.status()).isEqualTo(AgendaDiscoveryRun.Status.COMPLETE);
        assertThat(third.complete()).isTrue();
        assertThat(third.resumable()).isFalse();
        assertThat(third.pagesThisBatch()).isEqualTo(1);
        assertThat(third.examined()).isEqualTo(15);
        assertThat(third.newAgendas()).isEqualTo(15);
        assertThat(third.endedAt()).isNotNull();

        // Chaque page n'a été demandée qu'une fois : la reprise repart du curseur enregistré.
        verify(directory).fetchPage(eq(DiscoveryScope.ALL), isNull());
        verify(directory).fetchPage(DiscoveryScope.ALL, List.of(2));
        verify(directory).fetchPage(DiscoveryScope.ALL, List.of(4));
        assertThat(catalogSize()).isEqualTo(15);
        // Découvrir ne crée aucune source, donc ne déclenche aucune collecte.
        assertThat(sourcesOfThisTest()).isZero();
    }

    @Test
    void discover_ShouldNotDuplicateTheCatalog_WhenRunAgainAfterACompleteRun() {
        givenDirectoryOf(DiscoveryScope.ALL, 2);
        AgendaDiscoveryResult first = discoveryService.discover(DiscoveryScope.ALL, 10, false);
        assertThat(first.complete()).isTrue();

        AgendaDiscoveryResult again = discoveryService.discover(DiscoveryScope.ALL, 10, false);

        assertThat(again.runId()).isNotEqualTo(first.runId());
        assertThat(again.complete()).isTrue();
        assertThat(again.newAgendas()).isZero();
        assertThat(again.alreadyKnown()).isEqualTo(6);
        assertThat(catalogSize()).isEqualTo(6);
    }

    @Test
    void discover_ShouldStayResumable_WhenTheProviderFailsMidway_AndResumeFromTheLastSavedPage() {
        when(directory.fetchPage(eq(DiscoveryScope.ALL), isNull()))
                .thenReturn(new AgendaPage(agendas(0, 3), List.of(1), null));
        when(directory.fetchPage(DiscoveryScope.ALL, List.of(1)))
                .thenThrow(new CollectorException("Échec : trop de requêtes, OpenAgenda limite les appels (HTTP 429).",
                        null))
                .thenReturn(new AgendaPage(agendas(3, 3), null, null));

        AgendaDiscoveryResult interrupted = discoveryService.discover(DiscoveryScope.ALL, 10, false);

        assertThat(interrupted.status()).isEqualTo(AgendaDiscoveryRun.Status.IN_PROGRESS);
        assertThat(interrupted.complete()).isFalse();
        assertThat(interrupted.resumable()).isTrue();
        assertThat(interrupted.errors()).isEqualTo(1);
        assertThat(interrupted.stopReason()).contains("HTTP 429").contains("reprenable depuis la page 2");

        AgendaDiscoveryResult resumed = discoveryService.discover(DiscoveryScope.ALL, 10, false);

        assertThat(resumed.complete()).isTrue();
        assertThat(resumed.examined()).isEqualTo(6);
        assertThat(resumed.newAgendas()).isEqualTo(6);
        verify(directory).fetchPage(eq(DiscoveryScope.ALL), isNull());
    }

    @Test
    void discover_ShouldFail_WhenTheProviderRepeatsACursor_AndRestartFromScratchNextTime() {
        when(directory.fetchPage(eq(DiscoveryScope.ALL), isNull()))
                .thenReturn(new AgendaPage(agendas(0, 3), List.of(1), null));
        when(directory.fetchPage(DiscoveryScope.ALL, List.of(1)))
                .thenReturn(new AgendaPage(agendas(3, 3), List.of(1), null));

        AgendaDiscoveryResult failed = discoveryService.discover(DiscoveryScope.ALL, 10, false);

        assertThat(failed.status()).isEqualTo(AgendaDiscoveryRun.Status.FAILED);
        assertThat(failed.complete()).isFalse();
        assertThat(failed.resumable()).isFalse();
        assertThat(failed.stopReason()).contains("curseur déjà rencontré");

        AgendaDiscoveryResult next = discoveryService.discover(DiscoveryScope.ALL, 1, false);
        assertThat(next.runId()).isNotEqualTo(failed.runId());
    }

    @Test
    void discover_ShouldNotClaimCompleteness_WhenFewerAgendasThanTheAnnouncedTotalWereSeen() {
        when(directory.fetchPage(eq(DiscoveryScope.OFFICIAL), isNull()))
                .thenReturn(new AgendaPage(agendas(0, 3), null, 10));

        AgendaDiscoveryResult result = discoveryService.discover(DiscoveryScope.OFFICIAL, 10, false);

        assertThat(result.status()).isEqualTo(AgendaDiscoveryRun.Status.FAILED);
        assertThat(result.complete()).isFalse();
        assertThat(result.stopReason()).contains("annonçait 10");
    }

    @Test
    void discover_ShouldFail_OnAResponseWithoutAgendasArray() {
        when(directory.fetchPage(eq(DiscoveryScope.ALL), isNull())).thenReturn(new AgendaPage(null, null, null));

        AgendaDiscoveryResult result = discoveryService.discover(DiscoveryScope.ALL, 10, false);

        assertThat(result.status()).isEqualTo(AgendaDiscoveryRun.Status.FAILED);
        assertThat(result.stopReason()).contains("Réponse incohérente");
    }

    @Test
    void discover_ShouldCountAgendasWithoutUidAsIgnored() {
        List<DiscoveredAgenda> page = new ArrayList<>(agendas(0, 2));
        page.add(new DiscoveredAgenda(null, "Sans uid", null, null));
        when(directory.fetchPage(eq(DiscoveryScope.ALL), isNull())).thenReturn(new AgendaPage(page, null, 3));

        AgendaDiscoveryResult result = discoveryService.discover(DiscoveryScope.ALL, 10, false);

        assertThat(result.complete()).isTrue();
        assertThat(result.examined()).isEqualTo(3);
        assertThat(result.ignored()).isEqualTo(1);
        assertThat(result.newAgendas()).isEqualTo(2);
    }

    @Test
    void discover_ShouldAbandonTheRunInProgress_WhenARestartIsRequested() {
        givenDirectoryOf(DiscoveryScope.ALL, 3);
        AgendaDiscoveryResult first = discoveryService.discover(DiscoveryScope.ALL, 1, false);

        AgendaDiscoveryResult restarted = discoveryService.discover(DiscoveryScope.ALL, 1, true);

        assertThat(restarted.runId()).isNotEqualTo(first.runId());
        assertThat(restarted.pagesRead()).isEqualTo(1);
        String abandoned = jdbcTemplate.queryForObject(
                "SELECT status FROM agenda_discovery_run WHERE id = ?", String.class, first.runId());
        assertThat(abandoned).isEqualTo("ABANDONED");
    }

    // --- HTTP : aperçu, sélection, autorisations ---

    private RestTestClient client() {
        return RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    private String adminToken() {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .claim("userId", 1L)
                .claim("email", "admin-test@example.com")
                .claim("role", "ADMIN")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3_600_000L))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    private String userToken() {
        String email = cleaner.trackUser("test-" + UUID.randomUUID() + "@example.com");
        client().post().uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new RegisterRequest("u-" + UUID.randomUUID().toString().substring(0, 12), email, PASSWORD))
                .exchange()
                .expectStatus().isCreated();
        jdbcTemplate.update("UPDATE users SET email_verified = TRUE WHERE email = ?", email);
        LoginResponse login = client().post().uri("/api/v1/auth/login")
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
    void adminFlow_ShouldPreviewTheCatalog_ThenCreateSourcesOnlyForTheSelection() {
        givenDirectoryOf(DiscoveryScope.ALL, 2);
        String token = "Bearer " + adminToken();

        AgendaDiscoveryResult discovery = client().post().uri("/api/v1/admin/import/discover-agendas?maxPages=5")
                .header(HttpHeaders.AUTHORIZATION, token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(AgendaDiscoveryResult.class)
                .returnResult()
                .getResponseBody();
        assertThat(discovery).isNotNull();
        assertThat(discovery.complete()).isTrue();
        assertThat(sourcesOfThisTest()).isZero();

        DiscoveredAgendaView preview = client().get().uri("/api/v1/admin/import/discovered-agendas?filter=NEW&size=4")
                .header(HttpHeaders.AUTHORIZATION, token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(DiscoveredAgendaView.class)
                .returnResult()
                .getResponseBody();
        assertThat(preview).isNotNull();
        assertThat(preview.total()).isEqualTo(6);
        assertThat(preview.items()).hasSize(4);
        assertThat(preview.items().get(0).url()).startsWith("https://openagenda.com/slug-");
        assertThat(preview.items()).allMatch(item -> item.sourceId() == null);

        String selected = preview.items().get(0).agendaUid();
        AgendaActivationResult activation = client().post().uri("/api/v1/admin/import/discovered-agendas/sources")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("agendaUids", List.of(selected, "inconnu")))
                .exchange()
                .expectStatus().isOk()
                .expectBody(AgendaActivationResult.class)
                .returnResult()
                .getResponseBody();
        assertThat(activation).isNotNull();
        assertThat(activation.created()).hasSize(1);
        assertThat(activation.notFound()).containsExactly("inconnu");
        assertThat(sourcesOfThisTest()).isEqualTo(1);
        Source created = sourceRepository.findById(activation.created().get(0).sourceId()).orElseThrow();
        assertThat(created.type()).isEqualTo("API");
        assertThat(created.agendaUid()).isEqualTo(selected);
        assertThat(created.status()).isEqualTo("ACTIVE");

        // Un administrateur désactive la source : ni une nouvelle sélection ni une redécouverte ne la réactive.
        sourceRepository.save(new Source(created.id(), created.name(), created.type(), created.url(), "INACTIVE",
                null, created.agendaUid(), null));
        AgendaActivationResult again = client().post().uri("/api/v1/admin/import/discovered-agendas/sources")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("agendaUids", List.of(selected)))
                .exchange()
                .expectStatus().isOk()
                .expectBody(AgendaActivationResult.class)
                .returnResult()
                .getResponseBody();
        assertThat(again).isNotNull();
        assertThat(again.created()).isEmpty();
        assertThat(again.alreadyKnown()).containsExactly(selected);
        discoveryService.discover(DiscoveryScope.ALL, 5, false);
        assertThat(sourceRepository.findById(created.id()).orElseThrow().status()).isEqualTo("INACTIVE");
        assertThat(sourcesOfThisTest()).isEqualTo(1);

        DiscoveredAgendaView known = client().get().uri("/api/v1/admin/import/discovered-agendas?filter=KNOWN")
                .header(HttpHeaders.AUTHORIZATION, token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(DiscoveredAgendaView.class)
                .returnResult()
                .getResponseBody();
        assertThat(known).isNotNull();
        assertThat(known.items()).singleElement()
                .satisfies(item -> assertThat(item.sourceStatus()).isEqualTo("INACTIVE"));
    }

    @Test
    void createSources_ShouldRefuseAnOversizedSelectionEntirely() {
        givenDirectoryOf(DiscoveryScope.ALL, 1);
        discoveryService.discover(DiscoveryScope.ALL, 1, false);
        List<String> tooMany = new ArrayList<>();
        for (int index = 0; index < 51; index++) {
            tooMany.add("uid-" + index);
        }

        client().post().uri("/api/v1/admin/import/discovered-agendas/sources")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("agendaUids", tooMany))
                .exchange()
                .expectStatus().isBadRequest();

        assertThat(sourcesOfThisTest()).isZero();
    }

    @Test
    void discoverAgendas_ShouldRejectAnInvalidBatchOrScope() {
        client().post().uri("/api/v1/admin/import/discover-agendas?maxPages=0")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                .exchange()
                .expectStatus().isBadRequest();
        client().post().uri("/api/v1/admin/import/discover-agendas?scope=PRIVATE")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                .exchange()
                .expectStatus().isBadRequest();
        verify(directory, never()).fetchPage(any(), any());
    }

    @Test
    void discoveryEndpoints_ShouldBeReservedToAdministrators() {
        String user = "Bearer " + userToken();
        client().post().uri("/api/v1/admin/import/discover-agendas")
                .header(HttpHeaders.AUTHORIZATION, user).exchange().expectStatus().isForbidden();
        client().get().uri("/api/v1/admin/import/discover-agendas/latest")
                .header(HttpHeaders.AUTHORIZATION, user).exchange().expectStatus().isForbidden();
        client().get().uri("/api/v1/admin/import/discovered-agendas")
                .header(HttpHeaders.AUTHORIZATION, user).exchange().expectStatus().isForbidden();
        client().post().uri("/api/v1/admin/import/discovered-agendas/sources")
                .header(HttpHeaders.AUTHORIZATION, user)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("agendaUids", List.of("1")))
                .exchange().expectStatus().isForbidden();
        client().get().uri("/api/v1/admin/import/sync-states")
                .header(HttpHeaders.AUTHORIZATION, user).exchange().expectStatus().isForbidden();
        client().get().uri("/api/v1/admin/import/discovery-settings")
                .header(HttpHeaders.AUTHORIZATION, user).exchange().expectStatus().isForbidden();

        client().post().uri("/api/v1/admin/import/discover-agendas").exchange().expectStatus().isUnauthorized();
        client().get().uri("/api/v1/admin/import/discovered-agendas").exchange().expectStatus().isUnauthorized();
        client().get().uri("/api/v1/admin/import/sync-states").exchange().expectStatus().isUnauthorized();
        verify(directory, never()).fetchPage(any(), any());
    }
}
