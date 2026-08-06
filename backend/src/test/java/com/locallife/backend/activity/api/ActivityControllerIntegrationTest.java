package com.locallife.backend.activity.api;

import com.locallife.backend.activity.domain.Activity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ActivityControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void getAllActivities_ShouldReturnOk() {
        ResponseEntity<List> response = restTemplate.exchange(
                "/api/v1/activities",
                HttpMethod.GET,
                null,
                List.class
        );
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void getActivityById_ShouldReturnOk_WhenExists() {
        ResponseEntity<Activity> response = restTemplate.exchange(
                "/api/v1/activities/1",
                HttpMethod.GET,
                null,
                Activity.class
        );
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void getActivityById_ShouldReturnNotFound_WhenNotExists() {
        ResponseEntity<Activity> response = restTemplate.exchange(
                "/api/v1/activities/9999",
                HttpMethod.GET,
                null,
                Activity.class
        );
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
}
