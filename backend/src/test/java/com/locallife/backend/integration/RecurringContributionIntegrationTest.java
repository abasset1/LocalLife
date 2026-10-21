package com.locallife.backend.integration;

import com.locallife.backend.common.TestAccounts;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.locallife.backend.activity.api.ActivityController.CreateActivityRequest;
import com.locallife.backend.activity.api.ActivityController.RecurrenceRequest;
import com.locallife.backend.activity.api.ActivitySummaryResponse;
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
 * Contribution récurrente de bout en bout : un food truck proposé « tous les lundis » n'est trouvé par la
 * recherche par date que les lundis (occurrences matérialisées, ADR-0006), pas sur toute sa période.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RecurringContributionIntegrationTest {

    /** Contribuer exige Premium depuis le 21/10/2026 : les contributeurs de ces tests le reçoivent. */
    @Autowired
    private TestAccounts testAccounts;

    private static final String PASSWORD = "motDePasse123";
    private static final String ADDRESS = "Place du marché, Ville-récurrente";

    @LocalServerPort
    private int port;

    @MockitoBean
    private GeocodingService geocodingService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private IntegrationTestCleaner cleaner;

    @BeforeEach
    void startCleaner() {
        cleaner.start();
    }

    @AfterEach
    void cleanUpTestData() {
        cleaner.cleanUp();
    }

    @Test
    void recurringContribution_ShouldOnlyBeFoundOnItsDaysOfPassage() {
        String city = "Ville-test-" + UUID.randomUUID();
        when(geocodingService.geocode(ADDRESS)).thenReturn(new Coordinates(45.76, 4.83, city, "69001"));
        LocalDate firstMonday = LocalDate.now().plusDays(7).with(TemporalAdjusters.next(DayOfWeek.MONDAY));

        Activity created = restTestClient().post().uri("/api/v1/activities")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + registerAndLogin())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateActivityRequest(
                        "Food truck " + UUID.randomUUID(), "Burgers maison", null, ADDRESS,
                        null, null, null, null, null, "CULTURE", null,
                        new RecurrenceRequest(List.of("MO"), "11:30", "14:00", firstMonday.toString(),
                                firstMonday.plusDays(14).toString())))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Activity.class)
                .returnResult()
                .getResponseBody();

        assertThat(created).isNotNull();
        assertThat(created.startDate()).isEqualTo(firstMonday.atTime(11, 30));
        assertThat(created.endDate()).isEqualTo(firstMonday.plusDays(14).atTime(14, 0));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT recurrence_rule FROM schedule WHERE activity_id = ?", String.class, created.id()))
                .isEqualTo("FREQ=WEEKLY;BYDAY=MO");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM occurrence o JOIN schedule s ON s.id = o.schedule_id WHERE s.activity_id = ?",
                Integer.class, created.id()))
                .isEqualTo(3);

        jdbcTemplate.update("UPDATE activity SET status = 'PUBLISHED' WHERE id = ?", created.id());
        assertThat(searchIds(city, firstMonday.plusDays(7))).containsExactly(created.id());
        assertThat(searchIds(city, firstMonday.plusDays(8))).isEmpty();
    }

    @Test
    void recurringContribution_ShouldBeRejected_WhenNoDayOfPassageIsChosen() {
        restTestClient().post().uri("/api/v1/activities")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + registerAndLogin())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateActivityRequest(
                        "Food truck " + UUID.randomUUID(), "Burgers maison", null, ADDRESS,
                        null, null, null, null, null, "CULTURE", null,
                        new RecurrenceRequest(List.of(), "11:30", null, null, null)))
                .exchange()
                .expectStatus().isBadRequest();
    }

    private List<Long> searchIds(String city, LocalDate date) {
        ActivitySummaryResponse[] activities = restTestClient().get()
                .uri("/api/v1/activities?city=" + city + "&date=" + date)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ActivitySummaryResponse[].class)
                .returnResult()
                .getResponseBody();
        assertThat(activities).isNotNull();
        return List.of(activities).stream().map(ActivitySummaryResponse::id).toList();
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
