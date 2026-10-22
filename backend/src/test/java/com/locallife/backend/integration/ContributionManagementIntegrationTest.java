package com.locallife.backend.integration;

import com.locallife.backend.common.TestAccounts;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.locallife.backend.activity.api.ActivityController.CreateActivityRequest;
import com.locallife.backend.activity.api.ActivityController.RecurrenceRequest;
import com.locallife.backend.activity.api.ContributionResponse;
import com.locallife.backend.activity.domain.Activity;
import com.locallife.backend.auth.api.LoginRequest;
import com.locallife.backend.auth.api.LoginResponse;
import com.locallife.backend.auth.api.RegisterRequest;
import com.locallife.backend.common.IntegrationTestCleaner;
import com.locallife.backend.geocoding.application.Coordinates;
import com.locallife.backend.geocoding.application.GeocodingService;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Gestion de ses contributions de bout en bout : « Mes contributions », modification (qui garde le
 * statut, sauf une activité refusée, resoumise en validation), suppression, et refus pour un autre
 * utilisateur.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ContributionManagementIntegrationTest {

    /** Contribuer exige Premium depuis le 21/10/2026 : les contributeurs de ces tests le reçoivent. */
    @Autowired
    private TestAccounts testAccounts;

    private static final String PASSWORD = "motDePasse123";

    @LocalServerPort
    private int port;

    @MockitoBean
    private GeocodingService geocodingService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private IntegrationTestCleaner cleaner;

    private String city;

    @BeforeEach
    void setUp() {
        cleaner.start();
        city = "Ville-test-" + UUID.randomUUID();
        when(geocodingService.geocode(anyString())).thenReturn(new Coordinates(45.76, 4.83, city, "69001"));
    }

    @AfterEach
    void cleanUpTestData() {
        cleaner.cleanUp();
    }

    @Test
    void creatorCanEditPublishedContribution_WhichGoesBackToModeration() {
        String creator = registerAndLogin();
        Activity created = create(creator, request("Pétanque", null));
        publish(created.id());

        ContributionResponse[] mine = get("/api/v1/activities/mine", creator, ContributionResponse[].class);
        assertThat(mine).extracting(ContributionResponse::id).containsExactly(created.id());
        assertThat(mine[0].status()).isEqualTo("PUBLISHED");

        Activity updated = restTestClient().put().uri("/api/v1/activities/" + created.id())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + creator)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request("Pétanque du dimanche", null))
                .exchange()
                .expectStatus().isOk()
                .expectBody(Activity.class)
                .returnResult()
                .getResponseBody();

        assertThat(updated).isNotNull();
        assertThat(updated.title()).isEqualTo("Pétanque du dimanche");
        // 21/10/2026 : modifiée par son auteur, une activité publiée repart en validation et quitte la carte.
        assertThat(updated.status()).isEqualTo("PENDING");
        restTestClient().get().uri("/api/v1/activities/" + created.id()).exchange().expectStatus().isNotFound();

        ContributionResponse contribution = get(
                "/api/v1/activities/" + created.id() + "/contribution", creator, ContributionResponse.class);
        assertThat(contribution.title()).isEqualTo("Pétanque du dimanche");
        assertThat(contribution.categoryCodes()).containsExactly("CULTURE");
        assertThat(contribution.phone()).isEqualTo("0612345678");
        assertThat(contribution.recurrence()).isNull();
    }

    @Test
    void creatorCanCorrectRejectedContribution_WhichIsResubmittedForValidation() {
        String creator = registerAndLogin();
        Activity created = create(creator, request("Pétanque", null));
        jdbcTemplate.update("UPDATE activity SET status = 'REJECTED' WHERE id = ?", created.id());

        Activity updated = restTestClient().put().uri("/api/v1/activities/" + created.id())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + creator)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request("Pétanque corrigée", null))
                .exchange()
                .expectStatus().isOk()
                .expectBody(Activity.class)
                .returnResult()
                .getResponseBody();

        assertThat(updated).isNotNull();
        assertThat(updated.status()).isEqualTo("PENDING");
        restTestClient().get().uri("/api/v1/activities/" + created.id()).exchange().expectStatus().isNotFound();
    }

    @Test
    void activityPage_ExposesItsCreator_SoTheAppCanOfferEditAndDelete() {
        String creator = registerAndLogin();
        Activity created = create(creator, request("Pétanque", null));

        restTestClient().get().uri("/api/v1/activities/" + created.id())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + creator)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.createdBy").isEqualTo(created.createdBy().intValue());
    }

    @Test
    void creatorCanTurnContributionIntoRecurringOne_AndBack() {
        String creator = registerAndLogin();
        Activity created = create(creator, request("Food truck", null));
        LocalDate monday = LocalDate.now().plusDays(7).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        RecurrenceRequest recurrence = new RecurrenceRequest(
                List.of("MO", "TH"), "11:30", "14:00", monday.toString(), monday.plusDays(13).toString());

        put(creator, created.id(), request("Food truck", recurrence));

        ContributionResponse contribution = get(
                "/api/v1/activities/" + created.id() + "/contribution", creator, ContributionResponse.class);
        assertThat(contribution.recurrence()).isEqualTo(recurrence);
        assertThat(occurrenceCount(created.id())).isEqualTo(4);

        put(creator, created.id(), request("Food truck", null));
        assertThat(occurrenceCount(created.id())).isZero();
    }

    @Test
    void otherUserCanNeitherEditNorDeleteContribution() {
        String creator = registerAndLogin();
        String otherUser = registerAndLogin();
        Activity created = create(creator, request("Pétanque", null));

        restTestClient().put().uri("/api/v1/activities/" + created.id())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherUser)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request("Piraté", null))
                .exchange()
                .expectStatus().isForbidden();
        restTestClient().delete().uri("/api/v1/activities/" + created.id())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherUser)
                .exchange()
                .expectStatus().isForbidden();
        restTestClient().delete().uri("/api/v1/activities/" + created.id())
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void withoutPremium_AnAuthorCanNoLongerProposeOrEdit_ButCanStillCancelAndDelete() {
        String creator = registerAndLogin();
        Activity first = create(creator, request("Vide-grenier", null));
        Activity second = create(creator, request("Loto", null));
        // L'offre Premium prend fin.
        jdbcTemplate.update("UPDATE subscription SET status = 'EXPIRED' WHERE user_id = "
                + "(SELECT created_by FROM activity WHERE id = ?) AND status = 'ACTIVE'", first.id());

        restTestClient().post().uri("/api/v1/activities")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + creator)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request("Nouvelle idée", null))
                .exchange()
                .expectStatus().isForbidden();
        restTestClient().put().uri("/api/v1/activities/" + first.id())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + creator)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request("Vide-grenier modifié", null))
                .exchange()
                .expectStatus().isForbidden();
        restTestClient().patch().uri("/api/v1/activities/" + first.id() + "/cancel")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + creator)
                .exchange()
                .expectStatus().isOk();
        restTestClient().delete().uri("/api/v1/activities/" + second.id())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + creator)
                .exchange()
                .expectStatus().isNoContent();
        assertThat(get("/api/v1/activities/mine", creator, ContributionResponse[].class))
                .extracting(ContributionResponse::id).containsExactly(first.id());
    }

    @Test
    void creatorCanDeleteContribution_EvenWhenLikedAndRecurring() {
        String creator = registerAndLogin();
        String otherUser = registerAndLogin();
        LocalDate monday = LocalDate.now().plusDays(7).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        Activity created = create(creator, request("Marché", new RecurrenceRequest(
                List.of("MO"), "08:00", null, monday.toString(), monday.plusDays(7).toString())));
        publish(created.id());
        restTestClient().post().uri("/api/v1/activities/" + created.id() + "/like")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherUser)
                .exchange()
                .expectStatus().isOk();

        restTestClient().delete().uri("/api/v1/activities/" + created.id())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + creator)
                .exchange()
                .expectStatus().isNoContent();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM activity WHERE id = ?", Integer.class, created.id())).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM schedule WHERE activity_id = ?", Integer.class, created.id())).isZero();
        assertThat(get("/api/v1/activities/mine", creator, ContributionResponse[].class)).isEmpty();
    }

    private CreateActivityRequest request(String title, RecurrenceRequest recurrence) {
        return new CreateActivityRequest(
                title, "Description", null, "Place Bellecour, " + city, null, null, null, "0612345678", null,
                "CULTURE", null, recurrence);
    }

    private Activity create(String token, CreateActivityRequest request) {
        Activity created = restTestClient().post().uri("/api/v1/activities")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Activity.class)
                .returnResult()
                .getResponseBody();
        assertThat(created).isNotNull();
        return created;
    }

    private void put(String token, Long id, CreateActivityRequest request) {
        restTestClient().put().uri("/api/v1/activities/" + id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange()
                .expectStatus().isOk();
    }

    private <T> T get(String uri, String token, Class<T> type) {
        T body = restTestClient().get().uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(type)
                .returnResult()
                .getResponseBody();
        assertThat(body).isNotNull();
        return body;
    }

    private void publish(Long activityId) {
        jdbcTemplate.update("UPDATE activity SET status = 'PUBLISHED' WHERE id = ?", activityId);
    }

    private int occurrenceCount(Long activityId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM occurrence o JOIN schedule s ON s.id = o.schedule_id WHERE s.activity_id = ?",
                Integer.class, activityId);
    }

    private RestTestClient restTestClient() {
        return RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    private String registerAndLogin() {
        String email = cleaner.trackUser("test-" + UUID.randomUUID() + "@example.com");
        restTestClient().post().uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new RegisterRequest("u-" + UUID.randomUUID().toString().substring(0, 12), email, PASSWORD))
                .exchange()
                .expectStatus().isCreated();
        jdbcTemplate.update("UPDATE users SET email_verified = TRUE WHERE email = ?", email);
        testAccounts.grantPremium(email);

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
}
