package com.locallife.backend.activity.api;

import com.locallife.backend.activity.domain.Activity;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ActivityControllerIntegrationTest {

    @LocalServerPort
    private int port;

    private RestTestClient restTestClient() {
        return RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    void getAllActivities_ShouldReturnOk() {
        restTestClient().get().uri("/api/v1/activities").exchange().expectStatus().isOk();
    }

    /**
     * LL-8006 : ne plus supposer qu'une activité d'id={@code 1} existe.
     * Ce test échouait en environnement réel (base Postgres locale
     * persistante) : après les imports automatiques LL-8004/LL-8005 et les
     * manipulations manuelles effectuées pendant leur test, rien ne
     * garantit qu'une activité porte encore précisément l'id {@code 1}
     * (id auto-incrémenté, jamais réutilisé) — {@code mvn verify} a
     * échoué avec {@code 404} au lieu de {@code 200} pour cette raison,
     * sans lien avec un bug applicatif. Correction : récupérer un id
     * réellement présent via {@code GET /api/v1/activities} avant de
     * tester {@code GET /api/v1/activities/{id}}, plutôt que de figer un
     * id arbitraire.
     */
    @Test
    void getActivityById_ShouldReturnOk_WhenExists() {
        Activity[] activities = restTestClient().get().uri("/api/v1/activities")
                .exchange()
                .expectStatus().isOk()
                .expectBody(Activity[].class)
                .returnResult()
                .getResponseBody();

        assertNotNull(activities);
        assertTrue(activities.length > 0,
                "Aucune activité en base : la migration de démonstration V3 a-t-elle bien été appliquée ?");

        restTestClient().get().uri("/api/v1/activities/" + activities[0].id())
                .exchange()
                .expectStatus().isOk();
    }

    /**
     * LL-8006 : {@code 9999} n'est plus une garantie d'absence maintenant
     * que l'import automatique OpenAgenda (LL-8004/LL-8005, pagination
     * {@code size=300}) peut créer un nombre significatif d'activités —
     * un id proche de {@code 9999} pourrait un jour exister réellement.
     * {@code Long.MAX_VALUE - 1} reste hors de portée d'une colonne
     * {@code BIGINT} auto-incrémentée dans n'importe quel scénario
     * réaliste de ce projet.
     */
    @Test
    void getActivityById_ShouldReturnNotFound_WhenNotExists() {
        restTestClient().get().uri("/api/v1/activities/" + (Long.MAX_VALUE - 1))
                .exchange().expectStatus().isNotFound();
    }

    @Test
    void getNearbyActivities_ShouldReturnOk_WhenParamsValid() {
        restTestClient().get()
                .uri("/api/v1/activities/nearby?latitude=43.2951&longitude=5.3739&radius=5")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void getNearbyActivities_ShouldReturnBadRequest_WhenLatitudeMissing() {
        restTestClient().get()
                .uri("/api/v1/activities/nearby?longitude=5.3739&radius=5")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void getNearbyActivities_ShouldReturnBadRequest_WhenRadiusExceedsFiftyKilometers() {
        restTestClient().get()
                .uri("/api/v1/activities/nearby?latitude=43.2951&longitude=5.3739&radius=51")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void getNearbyActivities_ShouldReturnOk_WhenCategoryProvided() {
        restTestClient().get()
                .uri("/api/v1/activities/nearby?latitude=43.2951&longitude=5.3739&radius=50&category=concert")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void getNearbyActivities_ShouldReturnOk_WhenCategoryUnknown() {
        // Catégorie inexistante : liste vide attendue, pas d'erreur (voir ActivityService#findNearby).
        restTestClient().get()
                .uri("/api/v1/activities/nearby?latitude=43.2951&longitude=5.3739&radius=50"
                        + "&category=categorie-inexistante-xyz")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void getNearbyActivities_ShouldReturnOk_WhenDateProvided() {
        restTestClient().get()
                .uri("/api/v1/activities/nearby?latitude=43.2951&longitude=5.3739&radius=50&date=2026-09-05")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void getNearbyActivities_ShouldReturnBadRequest_WhenDateFormatInvalid() {
        restTestClient().get()
                .uri("/api/v1/activities/nearby?latitude=43.2951&longitude=5.3739&radius=50&date=05-09-2026")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void getNearbyActivities_ShouldReturnOk_WhenStatusCategoryAndDateProvided() {
        // LL-4014 : combinaison des trois filtres optionnels en même temps sur /nearby
        // (symétrique au test équivalent sur /within-bounds ci-dessous).
        restTestClient().get()
                .uri("/api/v1/activities/nearby?latitude=43.2951&longitude=5.3739&radius=50"
                        + "&status=PUBLISHED&category=concert&date=2026-09-05")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void getActivitiesWithinBounds_ShouldReturnOk_WhenParamsValid() {
        restTestClient().get()
                .uri("/api/v1/activities/within-bounds?swLatitude=43.20&swLongitude=5.30"
                        + "&neLatitude=43.35&neLongitude=5.45")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void getActivitiesWithinBounds_ShouldReturnBadRequest_WhenSwLatitudeMissing() {
        restTestClient().get()
                .uri("/api/v1/activities/within-bounds?swLongitude=5.30&neLatitude=43.35&neLongitude=5.45")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void getActivitiesWithinBounds_ShouldReturnBadRequest_WhenSwLatitudeNotLessThanNeLatitude() {
        restTestClient().get()
                .uri("/api/v1/activities/within-bounds?swLatitude=43.35&swLongitude=5.30"
                        + "&neLatitude=43.20&neLongitude=5.45")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void getActivitiesWithinBounds_ShouldReturnBadRequest_WhenSwLongitudeNotLessThanNeLongitude() {
        restTestClient().get()
                .uri("/api/v1/activities/within-bounds?swLatitude=43.20&swLongitude=5.45"
                        + "&neLatitude=43.35&neLongitude=5.30")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void getActivitiesWithinBounds_ShouldReturnOk_WhenStatusCategoryAndDateProvided() {
        restTestClient().get()
                .uri("/api/v1/activities/within-bounds?swLatitude=43.20&swLongitude=5.30"
                        + "&neLatitude=43.35&neLongitude=5.45&status=PUBLISHED&category=concert&date=2026-09-05")
                .exchange()
                .expectStatus().isOk();
    }
}
