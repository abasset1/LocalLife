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
}
