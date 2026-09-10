package com.locallife.backend.collector.infrastructure;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    private OpenAgendaCollector newCollectorWithRegionFilter(String apiKey, String agendaUid, String regionFilter) {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        return new OpenAgendaCollector(builder, apiKey, agendaUid, "OpenAgenda Marseille", regionFilter);
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
    void collect_ShouldReturnAddressCityAndPostalCode_WhenPresentInLocation() {
        // LL-10004 : vérifie que address/city/postalCode (LL-EF-008) sont bien
        // repris de l'objet "location" OpenAgenda, au même titre que
        // latitude/longitude — jusqu'ici seul EVENT_JSON (sans ces champs)
        // était utilisé par les tests de ce fichier, laissant ce chemin non
        // couvert.
        String eventJsonWithAddress = """
                {
                  "slug": "marche-de-noel-2026",
                  "title": {"fr": "Marché de Noël"},
                  "description": {"fr": "Marché de Noël sur le Vieux-Port"},
                  "keywords": {"fr": ["marché", "noël"]},
                  "location": {
                    "latitude": 43.2965,
                    "longitude": 5.3698,
                    "address": "Quai du Port",
                    "city": "Marseille",
                    "postalCode": "13002"
                  },
                  "nextTiming": {"begin": "2026-12-01T10:00:00+0100", "end": "2026-12-24T20:00:00+0100"}
                }
                """;
        OpenAgendaCollector collector = newCollector("key", "12345");
        mockServer.expect(requestTo(containsString("/v2/agendas/12345/events")))
                .andRespond(withSuccess("{\"events\": [" + eventJsonWithAddress + "]}", MediaType.APPLICATION_JSON));

        List<CollectedActivity> result = collector.collect();

        assertEquals(1, result.size());
        CollectedActivity activity = result.get(0);
        assertEquals("Quai du Port", activity.address());
        assertEquals("Marseille", activity.city());
        assertEquals("13002", activity.postalCode());
    }

    @Test
    void collect_ShouldReturnNullAddressCityPostalCode_WhenAbsentFromLocation() {
        // LL-10004 : une source ne fournissant pas ces champs (cas de
        // EVENT_JSON) ne doit rien inventer — null propagé tel quel, pas de
        // valeur par défaut.
        OpenAgendaCollector collector = newCollector("key", "12345");
        mockServer.expect(requestTo(containsString("/v2/agendas/12345/events")))
                .andRespond(withSuccess("{\"events\": [" + EVENT_JSON + "]}", MediaType.APPLICATION_JSON));

        List<CollectedActivity> result = collector.collect();

        CollectedActivity activity = result.get(0);
        assertNull(activity.address());
        assertNull(activity.city());
        assertNull(activity.postalCode());
    }

    @Test
    void collect_ShouldReturnLongDescriptionConditionsAndAge_WhenPresent() {
        // LL-11006 : longDescription/conditions/age (age.min/age.max) repris de l'événement
        // OpenAgenda, au même titre que address/city/postalCode ci-dessus.
        String eventJsonWithRichFields = """
                {
                  "slug": "marche-de-noel-2026",
                  "title": {"fr": "Marché de Noël"},
                  "description": {"fr": "Marché de Noël sur le Vieux-Port"},
                  "longDescription": {"fr": "Description détaillée sur plusieurs lignes."},
                  "conditions": {"fr": "Gratuit, réservation conseillée."},
                  "age": {"min": 6, "max": 12},
                  "keywords": {"fr": ["marché", "noël"]},
                  "location": {"latitude": 43.2965, "longitude": 5.3698},
                  "nextTiming": {"begin": "2026-12-01T10:00:00+0100", "end": "2026-12-24T20:00:00+0100"}
                }
                """;
        OpenAgendaCollector collector = newCollector("key", "12345");
        mockServer.expect(requestTo(containsString("/v2/agendas/12345/events")))
                .andRespond(withSuccess(
                        "{\"events\": [" + eventJsonWithRichFields + "]}", MediaType.APPLICATION_JSON));

        List<CollectedActivity> result = collector.collect();

        assertEquals(1, result.size());
        CollectedActivity activity = result.get(0);
        assertEquals("Description détaillée sur plusieurs lignes.", activity.longDescription());
        assertEquals("Gratuit, réservation conseillée.", activity.conditions());
        assertEquals(6, activity.ageMin());
        assertEquals(12, activity.ageMax());
    }

    @Test
    void collect_ShouldReturnNullLongDescriptionConditionsAndAge_WhenAbsent() {
        // « conservées lorsqu'elles existent » : leur absence de la source (cas de EVENT_JSON) ne
        // doit rien inventer, y compris quand age est totalement absent (pas seulement min/max).
        OpenAgendaCollector collector = newCollector("key", "12345");
        mockServer.expect(requestTo(containsString("/v2/agendas/12345/events")))
                .andRespond(withSuccess("{\"events\": [" + EVENT_JSON + "]}", MediaType.APPLICATION_JSON));

        List<CollectedActivity> result = collector.collect();

        CollectedActivity activity = result.get(0);
        assertNull(activity.longDescription());
        assertNull(activity.conditions());
        assertNull(activity.ageMin());
        assertNull(activity.ageMax());
    }

    @Test
    void collect_ShouldReturnAgeMinWithoutMax_WhenOnlyMinimumIsSpecified() {
        // La documentation OpenAgenda cite explicitement ce cas (ex. « interdit aux moins de 18
        // ans » sans limite haute) — voir la javadoc de OpenAgendaAge.
        String eventJsonWithAgeMinOnly = """
                {
                  "slug": "marche-de-noel-2026",
                  "title": {"fr": "Marché de Noël"},
                  "description": {"fr": "Marché de Noël sur le Vieux-Port"},
                  "age": {"min": 18},
                  "keywords": {"fr": ["marché", "noël"]},
                  "location": {"latitude": 43.2965, "longitude": 5.3698},
                  "nextTiming": {"begin": "2026-12-01T10:00:00+0100", "end": "2026-12-24T20:00:00+0100"}
                }
                """;
        OpenAgendaCollector collector = newCollector("key", "12345");
        mockServer.expect(requestTo(containsString("/v2/agendas/12345/events")))
                .andRespond(withSuccess(
                        "{\"events\": [" + eventJsonWithAgeMinOnly + "]}", MediaType.APPLICATION_JSON));

        List<CollectedActivity> result = collector.collect();

        CollectedActivity activity = result.get(0);
        assertEquals(18, activity.ageMin());
        assertNull(activity.ageMax());
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

    // --- filtre région temporaire (OPENAGENDA_REGION_FILTER) ---

    @Test
    void collect_ShouldKeepOnlyMatchingRegion_WhenRegionFilterIsSet() {
        OpenAgendaCollector collector =
                newCollectorWithRegionFilter("key", "12345", "Provence-Alpes-Côte d'Azur");
        String eventInRegion = """
                {
                  "slug": "concert-marseille",
                  "title": {"fr": "Concert Marseille"},
                  "description": {"fr": "Description"},
                  "keywords": {"fr": ["concert"]},
                  "location": {"latitude": 43.2965, "longitude": 5.3698, "region": "Provence-Alpes-Côte d'Azur"},
                  "nextTiming": {"begin": "2026-12-01T10:00:00+0100", "end": null}
                }
                """;
        String eventOutsideRegion = """
                {
                  "slug": "concert-paris",
                  "title": {"fr": "Concert Paris"},
                  "description": {"fr": "Description"},
                  "keywords": {"fr": ["concert"]},
                  "location": {"latitude": 48.8566, "longitude": 2.3522, "region": "Île-de-France"},
                  "nextTiming": {"begin": "2026-12-01T10:00:00+0100", "end": null}
                }
                """;
        mockServer.expect(requestTo(containsString("/v2/agendas/12345/events")))
                .andExpect(requestTo(containsString("detailed=1")))
                .andRespond(withSuccess(
                        "{\"events\": [" + eventInRegion + "," + eventOutsideRegion + "]}",
                        MediaType.APPLICATION_JSON));

        List<CollectedActivity> result = collector.collect();

        assertEquals(1, result.size());
        assertEquals("concert-marseille", result.get(0).externalId());
    }

    @Test
    void collect_ShouldExcludeEvent_WhenRegionFilterSetAndRegionFieldMissing() {
        OpenAgendaCollector collector =
                newCollectorWithRegionFilter("key", "12345", "Provence-Alpes-Côte d'Azur");
        String eventWithoutRegion = """
                {
                  "slug": "concert-sans-region",
                  "title": {"fr": "Concert"},
                  "description": {"fr": "Description"},
                  "keywords": {"fr": ["concert"]},
                  "location": {"latitude": 43.2965, "longitude": 5.3698},
                  "nextTiming": {"begin": "2026-12-01T10:00:00+0100", "end": null}
                }
                """;
        mockServer.expect(requestTo(containsString("/v2/agendas/12345/events")))
                .andRespond(withSuccess(
                        "{\"events\": [" + eventWithoutRegion + "]}", MediaType.APPLICATION_JSON));

        assertTrue(collector.collect().isEmpty());
    }

    @Test
    void collect_ShouldNotAddDetailedParam_WhenRegionFilterIsNotSet() {
        OpenAgendaCollector collector = newCollector("key", "12345");
        mockServer.expect(requestTo(containsString("/v2/agendas/12345/events")))
                .andRespond(withSuccess("{\"events\": [" + EVENT_JSON + "]}", MediaType.APPLICATION_JSON));

        List<CollectedActivity> result = collector.collect();

        assertEquals(1, result.size());
    }

    // --- pagination (paramètres size + after) ---

    @Test
    void collect_ShouldSendPageSize_OnFirstRequest() {
        OpenAgendaCollector collector = newCollector("key", "12345");
        mockServer.expect(requestTo(containsString("/v2/agendas/12345/events")))
                .andExpect(requestTo(containsString("size=300")))
                .andRespond(withSuccess("{\"events\": [" + EVENT_JSON + "]}", MediaType.APPLICATION_JSON));

        List<CollectedActivity> result = collector.collect();

        assertEquals(1, result.size());
    }

    @Test
    void collect_ShouldFollowAfterCursor_UntilEmptyPage() {
        OpenAgendaCollector collector = newCollector("key", "12345");
        String secondEvent = """
                {
                  "slug": "brocante-du-cours",
                  "title": {"fr": "Brocante"},
                  "description": {"fr": "Brocante mensuelle"},
                  "keywords": {"fr": ["brocante"]},
                  "location": {"latitude": 43.2, "longitude": 5.4},
                  "nextTiming": {"begin": "2026-12-05T09:00:00+0100", "end": null}
                }
                """;

        // Page 1 : un événement + un curseur "after" à repasser
        mockServer.expect(requestTo(containsString("/v2/agendas/12345/events")))
                .andRespond(withSuccess(
                        "{\"events\": [" + EVENT_JSON + "], \"after\": [1700000000, 42]}",
                        MediaType.APPLICATION_JSON));
        // Page 2 : le curseur reçu doit être repassé, un second événement, plus de curseur
        mockServer.expect(requestTo(allOf(
                        containsString("/v2/agendas/12345/events"),
                        containsString("after=1700000000"),
                        containsString("after=42"))))
                .andRespond(withSuccess(
                        "{\"events\": [" + secondEvent + "], \"after\": null}", MediaType.APPLICATION_JSON));

        List<CollectedActivity> result = collector.collect();

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(a -> a.externalId().equals("marche-de-noel-2026")));
        assertTrue(result.stream().anyMatch(a -> a.externalId().equals("brocante-du-cours")));
    }

    @Test
    void collect_ShouldStop_WhenAfterPageIsEmpty() {
        OpenAgendaCollector collector = newCollector("key", "12345");

        // Page 1 : un événement + un curseur "after"
        mockServer.expect(requestTo(containsString("/v2/agendas/12345/events")))
                .andRespond(withSuccess(
                        "{\"events\": [" + EVENT_JSON + "], \"after\": [1700000000, 42]}",
                        MediaType.APPLICATION_JSON));
        // Page 2 : liste vide -> la boucle doit s'arrêter, pas de 3e appel
        mockServer.expect(requestTo(containsString("/v2/agendas/12345/events")))
                .andRespond(withSuccess("{\"events\": [], \"after\": null}", MediaType.APPLICATION_JSON));

        List<CollectedActivity> result = collector.collect();

        assertEquals(1, result.size());
        mockServer.verify();
    }

}
