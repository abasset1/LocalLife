package com.locallife.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.locallife.backend.common.TestAccounts;
import com.locallife.backend.user.domain.Role;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Ce qu'un visiteur non connecté peut faire (décision du 21/10/2026, {@code ROLES_ET_PERMISSIONS.md} § « Visiteur ») :
 * consulter gratuitement carte, liste, fiches et référentiels, gérer l'accès à son compte, se désabonner d'une alerte
 * par le lien de l'e-mail. Tout le reste exige une connexion, y compris une route que personne n'a déclarée
 * (sécurité fermée par défaut).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class VisitorAccessIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestAccounts testAccounts;

    private int status(HttpMethod method, String uri, String authorization, String jsonBody) {
        RestTestClient.RequestBodySpec request = RestTestClient.bindToServer().baseUrl("http://localhost:" + port)
                .build().method(method).uri(uri);
        if (authorization != null) {
            request = request.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        if (jsonBody != null) {
            request.contentType(MediaType.APPLICATION_JSON).body(jsonBody);
        }
        return request.exchange().returnResult(String.class).getStatus().value();
    }

    @Test
    void visitor_ShouldBrowseEverythingPublic_WithoutAnAccount() {
        List<String> publicPages = List.of(
                "/api/v1/activities",
                "/api/v1/activities/search?q=concert",
                "/api/v1/activities/nearby?latitude=43.95&longitude=4.81&radius=10",
                "/api/v1/activities/within-bounds?swLat=43.9&swLng=4.7&neLat=44.0&neLng=4.9",
                "/api/v1/places/within-bounds?swLat=43.9&swLng=4.7&neLat=44.0&neLng=4.9",
                "/api/v1/foodtrucks",
                "/api/v1/categories",
                "/api/v1/activitytypes",
                "/api/v1/audiences",
                "/api/v1/organizertypes",
                "/api/v1/auth/username-availability?username=visiteur-test",
                "/actuator/health");
        for (String page : publicPages) {
            assertThat(status(HttpMethod.GET, page, null, null)).as(page).isNotIn(401, 403);
        }
        // Fiches : publiques (404 pour un identifiant inconnu, jamais 401).
        assertThat(status(HttpMethod.GET, "/api/v1/activities/-1", null, null)).isEqualTo(404);
        assertThat(status(HttpMethod.GET, "/api/v1/places/-1", null, null)).isNotIn(401, 403);
        assertThat(status(HttpMethod.GET, "/api/v1/foodtrucks/-1", null, null)).isNotIn(401, 403);
    }

    @Test
    void visitor_ShouldManageAccessToTheirAccount_WithoutBeingConnected() {
        // Identifiants refusés ou corps invalide : la route répond elle-même (400/401 métier), elle n'est pas fermée.
        assertThat(status(HttpMethod.POST, "/api/v1/auth/login", null,
                "{\"email\":\"inconnu@example.com\",\"password\":\"mauvais-mdp\"}")).isEqualTo(401);
        for (String route : List.of("/api/v1/auth/register", "/api/v1/auth/forgot-password",
                "/api/v1/auth/reset-password", "/api/v1/auth/verify-email", "/api/v1/auth/resend-verification",
                "/api/v1/alerts/unsubscribe")) {
            assertThat(status(HttpMethod.POST, route, null, "{}")).as(route).isNotIn(401, 403);
        }
    }

    @Test
    void visitor_ShouldBeAskedToConnect_ForPersonalActions() {
        assertThat(status(HttpMethod.POST, "/api/v1/activities/1/like", null, null)).isEqualTo(401);
        assertThat(status(HttpMethod.GET, "/api/v1/users/me/likes", null, null)).isEqualTo(401);
        assertThat(status(HttpMethod.GET, "/api/v1/users/me/alerts", null, null)).isEqualTo(401);
        assertThat(status(HttpMethod.POST, "/api/v1/activities", null, "{}")).isEqualTo(401);
        assertThat(status(HttpMethod.GET, "/api/v1/activities/mine", null, null)).isEqualTo(401);
        assertThat(status(HttpMethod.GET, "/api/v1/users/me/subscription", null, null)).isEqualTo(401);
        assertThat(status(HttpMethod.GET, "/api/v1/pro/places", null, null)).isEqualTo(401);
    }

    @Test
    void anUndeclaredRoute_ShouldRequireAConnection_RatherThanBeingOpen() {
        assertThat(status(HttpMethod.GET, "/api/v1/route-inexistante", null, null)).isEqualTo(401);
        assertThat(status(HttpMethod.POST, "/api/v1/categories", null, "{}")).isEqualTo(401);
        assertThat(status(HttpMethod.DELETE, "/api/v1/foodtrucks/1", null, null)).isEqualTo(401);
    }

    @Test
    void internalSourceList_ShouldBeReservedToAdministrators() {
        for (String route : List.of("/api/v1/sources", "/api/v1/sources/1", "/api/v1/sources/default-categories")) {
            assertThat(status(HttpMethod.GET, route, null, null)).as(route).isEqualTo(401);
            assertThat(status(HttpMethod.GET, route, testAccounts.bearer(Role.USER), null)).as(route).isEqualTo(403);
            assertThat(status(HttpMethod.GET, route, testAccounts.bearer(Role.PRO), null)).as(route).isEqualTo(403);
        }
        assertThat(status(HttpMethod.GET, "/api/v1/sources", testAccounts.bearer(Role.ADMIN), null)).isEqualTo(200);
        assertThat(status(HttpMethod.GET, "/api/v1/sources/default-categories", testAccounts.bearer(Role.ADMIN), null))
                .isEqualTo(200);
    }
}
