package com.locallife.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.locallife.backend.common.IntegrationTestCleaner;
import com.locallife.backend.common.TestAccounts;
import com.locallife.backend.user.application.PasswordHashingService;
import com.locallife.backend.user.domain.Role;
import com.locallife.backend.user.domain.User;
import com.locallife.backend.user.infrastructure.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Sprint « Rôles, permissions et gestion des comptes » (ACC-01/02/03, AUD-01, QA-01) sur la vraie base et le vrai
 * filtre de sécurité : accès 401/403/200 selon USER, PRO et ADMIN, DTO sans secret, transitions de rôle, suspension
 * effective sur un jeton déjà émis, journal d'audit en ajout seul.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AccountAdministrationIntegrationTest {

    private static final String PASSWORD = "motDePasse123";
    private static final ObjectMapper JSON = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private IntegrationTestCleaner cleaner;

    @Autowired
    private TestAccounts testAccounts;

    @Autowired
    private PasswordHashingService passwordHashingService;

    @Autowired
    private UserRepository userRepository;

    private final String marker = UUID.randomUUID().toString().substring(0, 8);

    @BeforeEach
    void start() {
        cleaner.start();
    }

    @AfterEach
    void cleanUp() {
        cleaner.cleanUp();
    }

    /** Compte propre au test, connectable par mot de passe. */
    private Long account(Role role) {
        String email = cleaner.trackUser("acc-" + marker + "-" + UUID.randomUUID().toString().substring(0, 6)
                + "@example.com");
        jdbcTemplate.update("INSERT INTO users (username, email, password_hash, role, created_at, email_verified) "
                + "VALUES (?, ?, ?, ?, now(), TRUE)", "acc-" + UUID.randomUUID().toString().substring(0, 12), email,
                passwordHashingService.hash(PASSWORD), role.name());
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private String emailOf(Long id) {
        return jdbcTemplate.queryForObject("SELECT email FROM users WHERE id = ?", String.class, id);
    }

    private String bearer(Long id, Role role) {
        return "Bearer " + testAccounts.token(id, role);
    }

    private RestTestClient client() {
        return RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    /** Appel et vérification du statut ; renvoie le corps JSON (ou {@code null}). */
    private JsonNode call(HttpMethod method, String uri, String token, Object body, int expectedStatus)
            throws Exception {
        RestTestClient.RequestBodySpec request = client().method(method).uri(uri);
        if (token != null) {
            // Mot de passe du compte administrateur partagé : redemandé pour suspendre (22/10/2026).
            request = request.header(HttpHeaders.AUTHORIZATION, token)
                    .header("X-Confirm-Password", TestAccounts.ADMIN_PASSWORD);
        }
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).body(JSON.writeValueAsString(body));
        }
        String response = request.exchange().expectStatus().isEqualTo(expectedStatus).expectBody(String.class)
                .returnResult().getResponseBody();
        return response == null || response.isBlank() ? null : JSON.readTree(response);
    }

    private long auditCount(Long target, String action, String outcome) {
        Long count = jdbcTemplate.queryForObject("SELECT count(*) FROM account_audit_event WHERE target_user_id = ? "
                + "AND action = ? AND outcome = ?", Long.class, target, action, outcome);
        return count == null ? 0 : count;
    }

    // --- Accès ---

    @Test
    void accountEndpoints_ShouldAnswer401WithoutToken_403ForUserAndPro_AndServeAdmins() throws Exception {
        Long target = account(Role.USER);
        String user = bearer(account(Role.USER), Role.USER);
        String pro = bearer(account(Role.PRO), Role.PRO);
        String admin = testAccounts.bearer(Role.ADMIN);
        List<Object[]> endpoints = List.of(
                new Object[] {HttpMethod.GET, "/api/v1/admin/accounts", null},
                new Object[] {HttpMethod.GET, "/api/v1/admin/accounts/" + target, null},
                new Object[] {HttpMethod.GET, "/api/v1/admin/accounts/audit", null},
                new Object[] {HttpMethod.PATCH, "/api/v1/admin/accounts/" + target + "/role", Map.of("role", "PRO")},
                new Object[] {HttpMethod.POST, "/api/v1/admin/accounts/" + target + "/suspend",
                    Map.of("reason", "Test d'accès")},
                new Object[] {HttpMethod.POST, "/api/v1/admin/accounts/" + target + "/reactivate", Map.of()},
                new Object[] {HttpMethod.POST, "/api/v1/admin/accounts/" + target + "/logout-everywhere", null});
        for (Object[] endpoint : endpoints) {
            call((HttpMethod) endpoint[0], (String) endpoint[1], null, endpoint[2], 401);
            call((HttpMethod) endpoint[0], (String) endpoint[1], user, endpoint[2], 403);
            call((HttpMethod) endpoint[0], (String) endpoint[1], pro, endpoint[2], 403);
        }
        // Aucune des tentatives refusées par la sécurité n'a touché au compte.
        assertThat(jdbcTemplate.queryForObject("SELECT role || '/' || account_status FROM users WHERE id = ?",
                String.class, target)).isEqualTo("USER/ACTIVE");
        call(HttpMethod.GET, "/api/v1/admin/accounts", admin, null, 200);
    }

    @Test
    void forgedAdminRoleInAToken_ShouldNotGrantAdminRights_BecauseTheRoleIsReadFromTheDatabase() throws Exception {
        Long user = account(Role.USER);

        call(HttpMethod.GET, "/api/v1/admin/accounts", bearer(user, Role.ADMIN), null, 403);
    }

    @Test
    void tokenOfAnUnknownAccount_ShouldBeRejected() throws Exception {
        call(HttpMethod.GET, "/api/v1/users/me", bearer(-42L, Role.ADMIN), null, 401);
    }

    // --- Consultation ---

    @Test
    void search_ShouldFilterAndPaginate_WithoutExposingAnySecret() throws Exception {
        Long first = account(Role.USER);
        Long second = account(Role.PRO);
        String admin = testAccounts.bearer(Role.ADMIN);

        JsonNode page = call(HttpMethod.GET, "/api/v1/admin/accounts?q=acc-" + marker + "&size=1", admin, null, 200);
        assertThat(page.get("total").asLong()).isEqualTo(2);
        assertThat(page.get("items")).hasSize(1);
        JsonNode item = page.get("items").get(0);
        assertThat(item.has("passwordHash")).isFalse();
        assertThat(item.toString()).doesNotContain("password").doesNotContain("token");
        assertThat(item.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(item.has("emailVerified")).isTrue();

        JsonNode pros = call(HttpMethod.GET, "/api/v1/admin/accounts?q=acc-" + marker + "&role=pro", admin, null, 200);
        assertThat(pros.get("items")).singleElement()
                .satisfies(node -> assertThat(node.get("id").asLong()).isEqualTo(second));
        JsonNode byId = call(HttpMethod.GET, "/api/v1/admin/accounts?q=" + first, admin, null, 200);
        assertThat(byId.get("items").findValuesAsText("id")).contains(String.valueOf(first));

        call(HttpMethod.GET, "/api/v1/admin/accounts?role=SUPERADMIN", admin, null, 400);
        call(HttpMethod.GET, "/api/v1/admin/accounts?status=DELETED", admin, null, 400);
        call(HttpMethod.GET, "/api/v1/admin/accounts?size=101", admin, null, 400);
        call(HttpMethod.GET, "/api/v1/admin/accounts?page=-1", admin, null, 400);
        call(HttpMethod.GET, "/api/v1/admin/accounts/-1", admin, null, 404);
    }

    // --- Rôles ---

    @Test
    void roleChanges_ShouldOnlySwitchUserAndPro_AndBeAudited() throws Exception {
        Long user = account(Role.USER);
        Long otherAdmin = account(Role.ADMIN);
        Long adminId = testAccounts.sharedAccount(Role.ADMIN);
        String admin = testAccounts.bearer(Role.ADMIN);
        String userToken = bearer(user, Role.USER);

        assertThat(call(HttpMethod.PATCH, "/api/v1/admin/accounts/" + user + "/role", admin, Map.of("role", "pro"), 200)
                .get("role").asText()).isEqualTo("PRO");
        // Effet immédiat, sans nouvelle connexion : l'ancien jeton (rôle USER inscrit) ouvre l'espace pro.
        call(HttpMethod.GET, "/api/v1/pro/claims", userToken, null, 200);
        call(HttpMethod.PATCH, "/api/v1/admin/accounts/" + user + "/role", admin, Map.of("role", "USER"), 200);
        call(HttpMethod.GET, "/api/v1/pro/claims", userToken, null, 403);

        call(HttpMethod.PATCH, "/api/v1/admin/accounts/" + user + "/role", admin, Map.of("role", "ADMIN"), 403);
        call(HttpMethod.PATCH, "/api/v1/admin/accounts/" + user + "/role", admin, Map.of("role", "MODERATOR"), 400);
        call(HttpMethod.PATCH, "/api/v1/admin/accounts/" + otherAdmin + "/role", admin, Map.of("role", "USER"), 403);
        call(HttpMethod.PATCH, "/api/v1/admin/accounts/" + adminId + "/role", admin, Map.of("role", "PRO"), 403);
        call(HttpMethod.PATCH, "/api/v1/admin/accounts/-5/role", admin, Map.of("role", "PRO"), 404);

        assertThat(jdbcTemplate.queryForObject("SELECT role FROM users WHERE id = ?", String.class, user))
                .isEqualTo("USER");
        assertThat(jdbcTemplate.queryForObject("SELECT role FROM users WHERE id = ?", String.class, otherAdmin))
                .isEqualTo("ADMIN");
        assertThat(auditCount(user, "ROLE_CHANGE", "SUCCESS")).isEqualTo(2);
        assertThat(auditCount(user, "ROLE_CHANGE", "REFUSED")).isEqualTo(2);
        assertThat(auditCount(otherAdmin, "ROLE_CHANGE", "REFUSED")).isEqualTo(1);
        Map<String, Object> entry = jdbcTemplate.queryForMap("SELECT * FROM account_audit_event WHERE "
                + "target_user_id = ? AND outcome = 'SUCCESS' ORDER BY id LIMIT 1", user);
        assertThat(entry.get("actor_user_id")).isEqualTo(adminId);
        assertThat(entry.get("old_value")).isEqualTo("USER");
        assertThat(entry.get("new_value")).isEqualTo("PRO");
        assertThat(entry.get("occurred_at")).isNotNull();
    }

    @Test
    void aUser_ShouldNotChangeTheirOwnRole_ThroughTheProfileEndpoint() throws Exception {
        Long user = account(Role.USER);
        String token = bearer(user, Role.USER);
        String email = emailOf(user);

        call(HttpMethod.PATCH, "/api/v1/users/me", token,
                Map.of("username", "acc-moi-" + marker, "email", email, "role", "ADMIN", "status", "ACTIVE"), 200);

        assertThat(jdbcTemplate.queryForObject("SELECT role FROM users WHERE id = ?", String.class, user))
                .isEqualTo("USER");
        call(HttpMethod.PATCH, "/api/v1/admin/accounts/" + user + "/role", token, Map.of("role", "PRO"), 403);
    }

    // --- Suspension ---

    @Test
    void suspension_ShouldBlockLoginAndTheAlreadyIssuedToken_KeepTheData_AndBeReversible() throws Exception {
        Long user = account(Role.PRO);
        String token = bearer(user, Role.PRO);
        String email = emailOf(user);
        String admin = testAccounts.bearer(Role.ADMIN);
        String usernameBefore = jdbcTemplate.queryForObject("SELECT username FROM users WHERE id = ?", String.class,
                user);
        call(HttpMethod.GET, "/api/v1/users/me", token, null, 200);

        call(HttpMethod.POST, "/api/v1/admin/accounts/" + user + "/suspend", admin, Map.of(), 400);
        call(HttpMethod.POST, "/api/v1/admin/accounts/" + user + "/suspend", admin, Map.of("reason", " "), 400);
        JsonNode suspended = call(HttpMethod.POST, "/api/v1/admin/accounts/" + user + "/suspend", admin,
                Map.of("reason", "Annonces frauduleuses signalées"), 200);
        assertThat(suspended.get("status").asText()).isEqualTo("SUSPENDED");
        assertThat(suspended.get("statusReason").asText()).isEqualTo("Annonces frauduleuses signalées");
        call(HttpMethod.POST, "/api/v1/admin/accounts/" + user + "/suspend", admin, Map.of("reason", "Encore"), 409);

        // Le jeton déjà émis ne sert plus, ni pour le profil ni pour l'espace pro ; la connexion est refusée.
        assertThat(call(HttpMethod.GET, "/api/v1/users/me", token, null, 401).get("message").asText())
                .contains("suspendu");
        call(HttpMethod.GET, "/api/v1/pro/claims", token, null, 401);
        assertThat(call(HttpMethod.POST, "/api/v1/auth/login", null, Map.of("email", email, "password", PASSWORD), 423)
                .get("message").asText()).contains("suspendu");
        // Mauvais mot de passe : même réponse qu'avant, la suspension ne révèle rien.
        call(HttpMethod.POST, "/api/v1/auth/login", null, Map.of("email", email, "password", "mauvais-mdp"), 401);
        // La consultation publique reste ouverte sans jeton.
        call(HttpMethod.GET, "/api/v1/activities", null, null, 200);

        // Une sauvegarde ordinaire du compte (profil, mot de passe…) ne le réactive pas.
        User entity = userRepository.findById(user).orElseThrow();
        userRepository.save(entity);
        assertThat(jdbcTemplate.queryForObject("SELECT account_status FROM users WHERE id = ?", String.class, user))
                .isEqualTo("SUSPENDED");

        assertThat(call(HttpMethod.POST, "/api/v1/admin/accounts/" + user + "/reactivate", admin,
                Map.of("reason", "Vérification faite"), 200).get("status").asText()).isEqualTo("ACTIVE");
        call(HttpMethod.POST, "/api/v1/admin/accounts/" + user + "/reactivate", admin, Map.of(), 409);
        call(HttpMethod.GET, "/api/v1/users/me", token, null, 200);
        call(HttpMethod.GET, "/api/v1/pro/claims", token, null, 200);
        call(HttpMethod.POST, "/api/v1/auth/login", null, Map.of("email", email, "password", PASSWORD), 200);
        assertThat(jdbcTemplate.queryForObject("SELECT username || '/' || role FROM users WHERE id = ?", String.class,
                user)).isEqualTo(usernameBefore + "/PRO");

        assertThat(auditCount(user, "SUSPEND", "SUCCESS")).isEqualTo(1);
        assertThat(auditCount(user, "SUSPEND", "REFUSED")).isEqualTo(3);
        assertThat(auditCount(user, "REACTIVATE", "SUCCESS")).isEqualTo(1);
        assertThat(auditCount(user, "REACTIVATE", "REFUSED")).isEqualTo(1);
    }

    @Test
    void administrators_ShouldNeitherBeSuspended_NorSuspendThemselves() throws Exception {
        Long otherAdmin = account(Role.ADMIN);
        Long adminId = testAccounts.sharedAccount(Role.ADMIN);
        String admin = testAccounts.bearer(Role.ADMIN);

        call(HttpMethod.POST, "/api/v1/admin/accounts/" + otherAdmin + "/suspend", admin,
                Map.of("reason", "Départ de l'équipe"), 403);
        call(HttpMethod.POST, "/api/v1/admin/accounts/" + adminId + "/suspend", admin,
                Map.of("reason", "Erreur de manipulation"), 403);

        assertThat(jdbcTemplate.queryForObject("SELECT account_status FROM users WHERE id = ?", String.class,
                otherAdmin)).isEqualTo("ACTIVE");
        call(HttpMethod.GET, "/api/v1/admin/accounts", admin, null, 200);
        assertThat(auditCount(otherAdmin, "SUSPEND", "REFUSED")).isEqualTo(1);
    }

    // --- Journal ---

    @Test
    void auditLog_ShouldBeReadableByAdmins_AndImpossibleToAlter() throws Exception {
        Long user = account(Role.USER);
        String admin = testAccounts.bearer(Role.ADMIN);
        call(HttpMethod.POST, "/api/v1/admin/accounts/" + user + "/suspend", admin, Map.of("reason", "Spam"), 200);

        JsonNode audit = call(HttpMethod.GET, "/api/v1/admin/accounts/audit?targetUserId=" + user, admin, null, 200);
        assertThat(audit.get("total").asLong()).isEqualTo(1);
        JsonNode entry = audit.get("items").get(0);
        assertThat(entry.get("action").asText()).isEqualTo("SUSPEND");
        assertThat(entry.get("reason").asText()).isEqualTo("Spam");
        assertThat(entry.get("outcome").asText()).isEqualTo("SUCCESS");
        assertThat(entry.toString()).doesNotContain("Bearer").doesNotContain("password");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE account_audit_event SET reason = 'effacé' WHERE target_user_id = ?", user))
                .hasMessageContaining("ajout seul");
        assertThatThrownBy(() -> jdbcTemplate.update(
                "DELETE FROM account_audit_event WHERE target_user_id = ?", user))
                .hasMessageContaining("ajout seul");
        // Aucune route d'écriture n'existe sur le journal (seul GET /audit est exposé).
    }
}
