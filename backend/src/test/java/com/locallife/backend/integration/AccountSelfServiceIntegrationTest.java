package com.locallife.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.locallife.backend.auth.application.EmailService;
import com.locallife.backend.common.IntegrationTestCleaner;
import com.locallife.backend.common.TestAccounts;
import com.locallife.backend.user.application.PasswordHashingService;
import com.locallife.backend.user.domain.Role;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Actions d'un utilisateur sur son propre compte (décisions du 21/10/2026, {@code ROLES_ET_PERMISSIONS.md} § 3 bis) :
 * mot de passe, adresse e-mail confirmée, suppression avec anonymisation, droit de contribuer exposé au client.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AccountSelfServiceIntegrationTest {

    private static final String PASSWORD = "motDePasse123";
    private static final ObjectMapper JSON = new ObjectMapper();

    @LocalServerPort
    private int port;

    @MockitoBean
    private EmailService emailService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private IntegrationTestCleaner cleaner;

    @Autowired
    private TestAccounts testAccounts;

    @Autowired
    private PasswordHashingService passwordHashingService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void start() {
        cleaner.start();
    }

    @AfterEach
    void cleanUp() {
        cleaner.cleanUp();
    }

    private String newEmail() {
        return cleaner.trackUser("self-" + UUID.randomUUID().toString().substring(0, 10) + "@example.com");
    }

    /** Compte vérifié ; {@code password} null : compte créé avec Google, sans mot de passe LocalLife. */
    private Long account(String email, String password, Role role) {
        jdbcTemplate.update("INSERT INTO users (username, email, password_hash, role, created_at, email_verified) "
                + "VALUES (?, ?, ?, ?, now(), TRUE)", "self-" + UUID.randomUUID().toString().substring(0, 12), email,
                password == null ? "" : passwordHashingService.hash(password), role.name());
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private String bearer(Long id) {
        return "Bearer " + testAccounts.token(id, Role.USER);
    }

    private JsonNode call(HttpMethod method, String uri, String token, Object body, int expectedStatus)
            throws Exception {
        RestTestClient.RequestBodySpec request = RestTestClient.bindToServer().baseUrl("http://localhost:" + port)
                .build().method(method).uri(uri);
        if (token != null) {
            request = request.header(HttpHeaders.AUTHORIZATION, token);
        }
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).body(JSON.writeValueAsString(body));
        }
        String response = request.exchange().expectStatus().isEqualTo(expectedStatus).expectBody(String.class)
                .returnResult().getResponseBody();
        return response == null || response.isBlank() ? null : JSON.readTree(response);
    }

    // --- Mot de passe ---

    @Test
    void changePassword_ShouldRequireTheCurrentOne_AndCloseTheOtherSessions() throws Exception {
        String email = newEmail();
        Long id = account(email, PASSWORD, Role.USER);
        String oldSession = bearer(id);
        // Jeton émis une seconde avant le changement (les dates de jeton sont à la seconde).
        Thread.sleep(1100);

        call(HttpMethod.POST, "/api/v1/users/me/password", oldSession,
                Map.of("currentPassword", "mauvais", "newPassword", "nouveauMotDePasse"), 400);
        call(HttpMethod.POST, "/api/v1/users/me/password", oldSession,
                Map.of("currentPassword", PASSWORD, "newPassword", "court"), 400);
        String newToken = call(HttpMethod.POST, "/api/v1/users/me/password", oldSession,
                Map.of("currentPassword", PASSWORD, "newPassword", "nouveauMotDePasse"), 200).get("token").asText();

        call(HttpMethod.GET, "/api/v1/users/me", oldSession, null, 401);
        call(HttpMethod.GET, "/api/v1/users/me", "Bearer " + newToken, null, 200);
        call(HttpMethod.POST, "/api/v1/auth/login", null, Map.of("email", email, "password", PASSWORD), 401);
        call(HttpMethod.POST, "/api/v1/auth/login", null, Map.of("email", email, "password", "nouveauMotDePasse"),
                200);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM account_audit_event WHERE target_user_id = ? "
                + "AND action = 'PASSWORD_CHANGE'", Long.class, id)).isEqualTo(1);
    }

    @Test
    void aGoogleAccount_ShouldDefineAPassword_WithoutACurrentOne() throws Exception {
        String email = newEmail();
        Long id = account(email, null, Role.USER);
        assertThat(call(HttpMethod.GET, "/api/v1/users/me", bearer(id), null, 200).get("hasPassword").asBoolean())
                .isFalse();

        call(HttpMethod.POST, "/api/v1/users/me/password", bearer(id), Map.of("newPassword", "premierMotDePasse"),
                200);

        call(HttpMethod.POST, "/api/v1/auth/login", null, Map.of("email", email, "password", "premierMotDePasse"),
                200);
    }

    // --- Adresse e-mail ---

    @Test
    void emailChange_ShouldOnlyApplyAfterConfirmation_FromTheNewAddress() throws Exception {
        String email = newEmail();
        String target = newEmail();
        Long id = account(email, PASSWORD, Role.USER);
        String taken = newEmail();
        account(taken, PASSWORD, Role.USER);

        call(HttpMethod.PATCH, "/api/v1/users/me", bearer(id), Map.of("username", "self-renomme-" + id, "email", taken),
                400);
        JsonNode pending = call(HttpMethod.PATCH, "/api/v1/users/me", bearer(id),
                Map.of("username", "self-renomme-" + id, "email", target), 200);

        // Le nom change tout de suite ; l'adresse reste l'ancienne jusqu'à la confirmation.
        assertThat(pending.get("username").asText()).isEqualTo("self-renomme-" + id);
        assertThat(pending.get("email").asText()).isEqualTo(email);
        assertThat(pending.get("pendingEmail").asText()).isEqualTo(target);
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendEmailChangeConfirmationEmail(eq(target), link.capture());
        verify(emailService).sendEmailChangeNoticeEmail(eq(email), anyString());
        String token = link.getValue().substring(link.getValue().indexOf("token=") + "token=".length());

        call(HttpMethod.POST, "/api/v1/auth/confirm-email-change", null, Map.of("token", "inconnu"), 400);
        call(HttpMethod.POST, "/api/v1/auth/confirm-email-change", null, Map.of("token", token), 200);
        call(HttpMethod.POST, "/api/v1/auth/confirm-email-change", null, Map.of("token", token), 400);

        JsonNode after = call(HttpMethod.GET, "/api/v1/users/me", bearer(id), null, 200);
        assertThat(after.get("email").asText()).isEqualTo(target);
        assertThat(after.get("pendingEmail").isNull()).isTrue();
        call(HttpMethod.POST, "/api/v1/auth/login", null, Map.of("email", target, "password", PASSWORD), 200);
        assertThat(jdbcTemplate.queryForList("SELECT action FROM account_audit_event WHERE target_user_id = ? "
                + "ORDER BY id", String.class, id)).containsExactly("EMAIL_CHANGE_REQUEST", "EMAIL_CHANGE");
    }

    // --- Droit de contribuer ---

    @Test
    void me_ShouldTellWhetherTheAccountCanContribute() throws Exception {
        Long id = account(newEmail(), PASSWORD, Role.USER);
        assertThat(call(HttpMethod.GET, "/api/v1/users/me", bearer(id), null, 200).get("canContribute").asBoolean())
                .isFalse();

        testAccounts.grantPremium(id);

        assertThat(call(HttpMethod.GET, "/api/v1/users/me", bearer(id), null, 200).get("canContribute").asBoolean())
                .isTrue();
    }

    // --- Suppression ---

    private long activity(Long createdBy, String status) {
        // Activité et catégorie dans la même transaction : « au moins une catégorie » est vérifié au commit (V42).
        return transactionTemplate.execute(tx -> insertActivity(createdBy, status));
    }

    private long insertActivity(Long createdBy, String status) {
        Long id = jdbcTemplate.queryForObject("INSERT INTO activity (title, category, latitude, longitude, status, "
                + "start_date, source_id, created_by) VALUES (?, 'fete', 43.3, 5.4, ?, now() + interval '2 days', "
                + "(SELECT id FROM source WHERE type = 'MANUAL' LIMIT 1), ?) RETURNING id", Long.class,
                "Activité " + status + " " + UUID.randomUUID(), status, createdBy);
        jdbcTemplate.update("INSERT INTO activity_category (activity_id, category_id) "
                + "SELECT ?, id FROM category WHERE code = 'CULTURE'", id);
        return id;
    }

    @Test
    void deletion_ShouldKeepPublishedActivitiesWithoutAuthor_AndRemoveEverythingPersonal() throws Exception {
        Long id = account(newEmail(), PASSWORD, Role.USER);
        String session = bearer(id);
        long published = activity(id, "PUBLISHED");
        long pending = activity(id, "PENDING");
        jdbcTemplate.update("INSERT INTO activity_likes (user_id, activity_id) VALUES (?, ?)", id, published);
        testAccounts.grantPremium(id);
        Long subscription = jdbcTemplate.queryForObject(
                "SELECT id FROM subscription WHERE user_id = ? AND status = 'ACTIVE'", Long.class, id);

        call(HttpMethod.POST, "/api/v1/users/me/deletion", session, Map.of("password", "mauvais"), 400);
        call(HttpMethod.POST, "/api/v1/users/me/deletion", session, Map.of(), 400);
        call(HttpMethod.POST, "/api/v1/users/me/deletion", session, Map.of("password", PASSWORD), 204);

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM users WHERE id = ?", Long.class, id)).isZero();
        call(HttpMethod.GET, "/api/v1/users/me", session, null, 401);
        // Contenu public conservé, sans auteur ; contribution non publiée supprimée.
        assertThat(jdbcTemplate.queryForObject("SELECT created_by FROM activity WHERE id = ?", Long.class, published))
                .isNull();
        call(HttpMethod.GET, "/api/v1/activities/" + published, null, null, 200);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM activity WHERE id = ?", Long.class, pending))
                .isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM activity_likes WHERE user_id = ?", Long.class,
                id)).isZero();
        // Historique d'abonnement conservé, terminé, sans lien vers la personne.
        assertThat(jdbcTemplate.queryForMap("SELECT user_id, status FROM subscription WHERE id = ?", subscription))
                .containsEntry("user_id", null).containsEntry("status", "EXPIRED");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM account_audit_event WHERE target_user_id = ? "
                + "AND action = 'ACCOUNT_DELETION' AND actor_email IS NULL", Long.class, id)).isEqualTo(1);
        transactionTemplate.executeWithoutResult(tx -> {
            jdbcTemplate.update("DELETE FROM activity_category WHERE activity_id = ?", published);
            jdbcTemplate.update("DELETE FROM activity WHERE id = ?", published);
        });
    }

    @Test
    void deletion_OfAGoogleAccount_ShouldBeConfirmedByTyping_AndAnAdminCannotDeleteThemself() throws Exception {
        Long google = account(newEmail(), null, Role.USER);
        call(HttpMethod.POST, "/api/v1/users/me/deletion", bearer(google), Map.of("confirmation", "oui"), 400);
        call(HttpMethod.POST, "/api/v1/users/me/deletion", bearer(google), Map.of("confirmation", "SUPPRIMER"), 204);

        Long admin = account(newEmail(), PASSWORD, Role.ADMIN);
        call(HttpMethod.POST, "/api/v1/users/me/deletion", "Bearer " + testAccounts.token(admin, Role.ADMIN),
                Map.of("password", PASSWORD), 403);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM users WHERE id = ?", Long.class, admin))
                .isEqualTo(1);
    }

    @Test
    void accountActions_ShouldRequireAConnection() throws Exception {
        call(HttpMethod.POST, "/api/v1/users/me/password", null, Map.of("newPassword", "xxxxxxxxx"), 401);
        call(HttpMethod.POST, "/api/v1/users/me/deletion", null, Map.of("password", "x"), 401);
    }
}
