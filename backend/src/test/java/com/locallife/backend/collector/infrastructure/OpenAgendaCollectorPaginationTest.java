package com.locallife.backend.collector.infrastructure;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.locallife.backend.collector.domain.CollectionReport;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Exhaustivité de la pagination des événements (sprint « agendas exhaustifs », AGD-04/06) : la collecte
 * n'est complète que si la fin réelle des pages est atteinte, et tout autre arrêt est signalé.
 */
class OpenAgendaCollectorPaginationTest {

    /** Horloge fixe : les événements de test (2027) restent à venir quelle que soit la date d'exécution. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-20T08:00:00Z"), ZoneOffset.UTC);

    private MockRestServiceServer mockServer;

    private OpenAgendaCollector newCollector(int maxPages, int maxAttempts) {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        return new OpenAgendaCollector(builder, "key", "12345", "OpenAgenda Marseille", "", CLOCK, maxPages,
                OpenAgendaRetry.immediate(maxAttempts));
    }

    private static String event(int number) {
        return "{\"slug\": \"evenement-" + number + "\", \"title\": {\"fr\": \"Événement " + number + "\"}, "
                + "\"location\": {\"latitude\": 43.3, \"longitude\": 5.4}, "
                + "\"timings\": [{\"begin\": \"2027-01-10T10:00:00+0100\", \"end\": \"2027-01-10T12:00:00+0100\"}]}";
    }

    /** Une page de {@code count} événements numérotés à partir de {@code first}, avec le curseur donné. */
    private static String page(int first, int count, String after) {
        StringBuilder events = new StringBuilder();
        for (int number = first; number < first + count; number++) {
            events.append(number == first ? "" : ",").append(event(number));
        }
        return "{\"events\": [" + events + "], \"after\": " + after + "}";
    }

    private void expectPage(String body) {
        mockServer.expect(requestTo(containsString("/v2/agendas/12345/events")))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    @Test
    void collectWithReport_ShouldReadBeyondTheHistoricalLimitOfFiveHundredPages_UntilTheRealEnd() {
        OpenAgendaCollector collector = newCollector(OpenAgendaCollector.DEFAULT_MAX_PAGES, 1);
        int pages = 520;
        for (int index = 0; index < pages; index++) {
            boolean last = index == pages - 1;
            expectPage(page(index * 3, 3, last ? "null" : "[" + index + ", \"c" + index + "\"]"));
        }

        CollectionReport report = collector.collectWithReport();

        assertTrue(report.isComplete());
        assertEquals(pages, report.pagesRead());
        assertEquals(pages * 3, report.received());
        assertEquals(pages * 3, report.activities().size());
        mockServer.verify();
    }

    @Test
    void collectWithReport_ShouldBeComplete_WhenAnEmptyPageEndsThePagination() {
        OpenAgendaCollector collector = newCollector(10, 1);
        expectPage(page(0, 2, "[1, 2]"));
        expectPage("{\"events\": [], \"after\": [1, 3]}");

        CollectionReport report = collector.collectWithReport();

        assertTrue(report.isComplete());
        assertEquals(1, report.pagesRead());
        assertEquals(2, report.activities().size());
        mockServer.verify();
    }

    @Test
    void collectWithReport_ShouldBeComplete_WhenTheCursorIsAbsentOrEmpty() {
        OpenAgendaCollector collector = newCollector(10, 1);
        expectPage("{\"events\": [" + event(1) + "]}");

        assertTrue(collector.collectWithReport().isComplete());

        OpenAgendaCollector second = newCollector(10, 1);
        expectPage("{\"events\": [" + event(1) + "], \"after\": []}");

        assertTrue(second.collectWithReport().isComplete());
    }

    @Test
    void collectWithReport_ShouldBePartial_WhenTheSafetyLimitIsReachedBeforeTheEnd() {
        OpenAgendaCollector collector = newCollector(3, 1);
        expectPage(page(0, 2, "[1]"));
        expectPage(page(2, 2, "[2]"));
        expectPage(page(4, 2, "[3]"));

        CollectionReport report = collector.collectWithReport();

        assertFalse(report.isComplete());
        assertEquals(3, report.pagesRead());
        assertEquals(6, report.activities().size());
        assertTrue(report.stopReason().contains("limite de sécurité de 3 pages"), report.stopReason());
        mockServer.verify();
    }

    @Test
    void collect_ShouldFail_RatherThanReturnATruncatedList() {
        OpenAgendaCollector collector = newCollector(1, 1);
        expectPage(page(0, 2, "[1]"));

        CollectorException exception = assertThrows(CollectorException.class, collector::collect);
        assertTrue(exception.getMessage().contains("incomplète"));
    }

    @Test
    void collectWithReport_ShouldBePartial_WhenTheApiRepeatsACursor() {
        OpenAgendaCollector collector = newCollector(100, 1);
        expectPage(page(0, 2, "[1, \"a\"]"));
        expectPage(page(2, 2, "[2, \"b\"]"));
        expectPage(page(4, 2, "[1, \"a\"]"));

        CollectionReport report = collector.collectWithReport();

        assertFalse(report.isComplete());
        assertEquals(3, report.pagesRead());
        assertTrue(report.stopReason().contains("curseur de pagination répété"), report.stopReason());
        mockServer.verify();
    }

    @Test
    void collectWithReport_ShouldBePartial_WhenAPageFailsMidwayAfterBoundedRetries() {
        OpenAgendaCollector collector = newCollector(100, 2);
        expectPage(page(0, 2, "[1]"));
        mockServer.expect(ExpectedCount.times(2), requestTo(containsString("/v2/agendas/12345/events")))
                .andRespond(withServerError());

        CollectionReport report = collector.collectWithReport();

        assertFalse(report.isComplete());
        assertEquals(1, report.pagesRead());
        assertEquals(2, report.activities().size());
        assertTrue(report.stopReason().contains("page 2 en échec après 2 essai(s)"), report.stopReason());
        assertFalse(report.stopReason().contains("key="));
        mockServer.verify();
    }

    @Test
    void collectWithReport_ShouldRecover_WhenAQuotaErrorIsFollowedBySuccess() {
        OpenAgendaCollector collector = newCollector(100, 3);
        expectPage(page(0, 2, "[1]"));
        mockServer.expect(requestTo(containsString("/v2/agendas/12345/events")))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        expectPage(page(2, 2, "null"));

        CollectionReport report = collector.collectWithReport();

        assertTrue(report.isComplete());
        assertEquals(4, report.activities().size());
        mockServer.verify();
    }

    @Test
    void collectWithReport_ShouldBePartial_WhenAPageIsMalformed() {
        OpenAgendaCollector collector = newCollector(100, 1);
        expectPage(page(0, 2, "[1]"));
        expectPage("{\"events\": [ {\"slug\": ");

        CollectionReport report = collector.collectWithReport();

        assertFalse(report.isComplete());
        assertEquals(2, report.activities().size());
        assertTrue(report.stopReason().contains("illisible"), report.stopReason());
    }

    @Test
    void collectWithReport_ShouldBePartial_WhenAPageHasNoEventsArray() {
        OpenAgendaCollector collector = newCollector(100, 1);
        expectPage(page(0, 2, "[1]"));
        expectPage("{\"after\": [2]}");

        CollectionReport report = collector.collectWithReport();

        assertFalse(report.isComplete());
        assertTrue(report.stopReason().contains("réponse incohérente à la page 2"), report.stopReason());
    }

    @Test
    void collectWithReport_ShouldFail_WhenTheFirstPageHasNoEventsArray() {
        OpenAgendaCollector collector = newCollector(100, 1);
        expectPage("{\"message\": \"maintenance\"}");

        assertThrows(CollectorException.class, collector::collectWithReport);
    }

    @Test
    void collectWithReport_ShouldFail_WhenTheFirstPageFails() {
        OpenAgendaCollector collector = newCollector(100, 1);
        mockServer.expect(requestTo(containsString("/v2/agendas/12345/events"))).andRespond(withServerError());

        assertThrows(CollectorException.class, collector::collectWithReport);
    }

    @Test
    void collectWithReport_ShouldBePartial_WhenFewerEventsThanTheAnnouncedTotalWereReceived() {
        OpenAgendaCollector collector = newCollector(100, 1);
        expectPage("{\"total\": 5, \"events\": [" + event(1) + "," + event(2) + "], \"after\": [1]}");
        expectPage("{\"events\": [], \"after\": null}");

        CollectionReport report = collector.collectWithReport();

        assertFalse(report.isComplete());
        assertTrue(report.stopReason().contains("annonçait 5"), report.stopReason());
    }

    @Test
    void collectWithReport_ShouldCountRawEventsSeparatelyFromKeptActivities() {
        OpenAgendaCollector collector = newCollector(100, 1);
        String withoutLocation = "{\"slug\": \"en-ligne\", \"title\": {\"fr\": \"En ligne\"}, "
                + "\"timings\": [{\"begin\": \"2027-01-10T10:00:00+0100\"}]}";
        expectPage("{\"total\": 2, \"events\": [" + event(1) + "," + withoutLocation + "], \"after\": null}");

        CollectionReport report = collector.collectWithReport();

        assertTrue(report.isComplete());
        assertEquals(2, report.received());
        assertEquals(1, report.activities().size());
    }
}
