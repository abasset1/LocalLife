package com.locallife.backend.activity.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.servlet.client.RestTestClient;

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

    @Test
    void getActivityById_ShouldReturnOk_WhenExists() {
        restTestClient().get().uri("/api/v1/activities/1").exchange().expectStatus().isOk();
    }

    @Test
    void getActivityById_ShouldReturnNotFound_WhenNotExists() {
        restTestClient().get().uri("/api/v1/activities/9999").exchange().expectStatus().isNotFound();
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
