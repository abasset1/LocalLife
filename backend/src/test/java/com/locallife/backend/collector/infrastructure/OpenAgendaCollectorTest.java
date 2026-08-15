package com.locallife.backend.collector.infrastructure;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.locallife.backend.collector.domain.CollectedActivity;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Tests avec un MockRestServiceServer, comme {@code GeocodingServiceTest} :
 * aucun appel réseau réel vers OpenAgenda.
 */
class OpenAgendaCollectorTest {

    private static final String EVENT_JSON = """
            {
              "slug": "marche-de-noel-2026",
              "title": {"fr": "Marché de Noël"},
              "description": {"fr": "Marché de Noël sur le Vieux-Port"},
              "keywords": {"fr": ["marché", "noël"]},
              "location": {"latitude": 43.2965, "longitude": 5.3698},
              "nextTiming": {"begin": "2026-12-01T10:00:00+0100", "end": "2026-12-24T20:00:00+0100"}
            }
            """;

    private MockRestServiceServer mockServer;

    private OpenAgendaCollector newCollector(String apiKey, String agendaUid) {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        return new OpenAgendaCollector(builder, apiKey, agendaUid, "OpenAgenda Marseille");
    }

    @Test
    void getSourceName_ShouldReturnConfiguredName() {
        OpenAgendaCollector collector = newCollector("key", "12345");

        assertEquals("OpenAgenda Marseille", collector.getSourceName());
    }

    @Test
    void collect_ShouldReturnCollectedActivities_WhenApiRespondsWithEvents() {
        OpenAgendaCollector collector = newCollector("key", "12345");
        mockServer.expect(requestTo(containsString("/v2/agendas/12345/events")))
                .andRespond(withSuccess("{\"events\": [" + EVENT_JSON + "]}", MediaType.APPLICATION_JSON));

        List<CollectedActivity> result = collector.collect();

        assertEquals(1, result.size());
        CollectedActivity activity = result.get(0);
        assertEquals("Marché de Noël", activity.title());
        assertEquals("Marché de Noël sur le Vieux-Port", activity.description());
        assertEquals("marché", activity.category());
        assertEquals(43.2965, activity.latitude());
        assertEquals(5.3698, activity.longitude());
        assertEquals("marche-de-noel-2026", activity.externalId());
        assertEquals("OpenAgenda Marseille", activity.source());
        assertTrue(activity.sourceUrl().contains("marche-de-noel-2026"));
    }

    @Test
    void collect_ShouldSkipEvent_WhenLocationIsMissing() {
        OpenAgendaCollector collector = newCollector("key", "12345");
        String eventWithoutLocation = """
                {
                  "slug": "webinaire-en-ligne",
                  "title": {"fr": "Webinaire"},
                  "description": {"fr": "Événement en ligne"},
                  "keywords": {"fr": []},
                  "location": null,
                  "nextTiming": {"begin": "2026-12-01T10:00:00+0100", "end": null}
                }
                """;
        mockServer.expect(requestTo(containsString("/v2/agendas/12345/events")))
                .andRespond(withSuccess(
                        "{\"events\": [" + eventWithoutLocation + "]}", MediaType.APPLICATION_JSON));

        List<CollectedActivity> result = collector.collect();

        assertTrue(result.isEmpty());
    }

    @Test
    void collect_ShouldReturnEmptyList_WhenNoEvents() {
        OpenAgendaCollector collector = newCollector("key", "12345");
        mockServer.expect(requestTo(containsString("/v2/agendas/12345/events")))
                .andRespond(withSuccess("{\"events\": []}", MediaType.APPLICATION_JSON));

        assertTrue(collector.collect().isEmpty());
    }

    @Test
    void collect_ShouldThrowCollectorException_WhenApiCallFails() {
        OpenAgendaCollector collector = newCollector("key", "12345");
        mockServer.expect(requestTo(containsString("/v2/agendas/12345/events")))
                .andRespond(withServerError());

        assertThrows(CollectorException.class, collector::collect);
    }

    @Test
    void collect_ShouldThrowCollectorException_WhenApiKeyMissing() {
        OpenAgendaCollector collector = newCollector("", "12345");

        assertThrows(CollectorException.class, collector::collect);
    }

    @Test
    void collect_ShouldThrowCollectorException_WhenAgendaUidMissing() {
        OpenAgendaCollector collector = newCollector("key", "");

        assertThrows(CollectorException.class, collector::collect);
    }

    @Test
    void collect_ShouldParseTiming_WhenOffsetHasColon() {
        OpenAgendaCollector collector = newCollector("key", "12345");
        String eventWithColonOffset = """
                {
                  "slug": "concert-colon",
                  "title": {"fr": "Concert"},
                  "description": {"fr": "Description"},
                  "keywords": {"fr": ["concert"]},
                  "location": {"latitude": 43.2965, "longitude": 5.3698},
                  "nextTiming": {"begin": "2026-12-01T10:00:00+01:00", "end": "2026-12-01T23:00:00+01:00"}
                }
                """;
        mockServer.expect(requestTo(containsString("/v2/agendas/12345/events")))
                .andRespond(withSuccess(
                        "{\"events\": [" + eventWithColonOffset + "]}", MediaType.APPLICATION_JSON));

        List<CollectedActivity> result = collector.collect();

        assertEquals(1, result.size());
        assertEquals(LocalDateTime.of(2026, 12, 1, 10, 0), result.get(0).startDate());
    }

}
