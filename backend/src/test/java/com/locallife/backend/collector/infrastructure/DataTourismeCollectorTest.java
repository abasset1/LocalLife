package com.locallife.backend.collector.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.locallife.backend.collector.domain.CollectedActivity;
import com.locallife.backend.collector.domain.CollectedLink;
import com.locallife.backend.collector.domain.CollectedTiming;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Tests sur un extrait réel de l'export DATAtourisme PACA publié sur
 * data.gouv.fr le 13/10/2026 ({@code datatourisme/datatourisme-extrait.csv}) :
 * un camping (non événement), trois événements à Avignon (dont un passé et
 * un à plusieurs périodes), un événement à Aix-en-Provence (description sur
 * plusieurs lignes, plusieurs contacts) et une URI répétée.
 */
class DataTourismeCollectorTest {

    private static final String RESOURCE_URL = "https://www.data.gouv.fr/api/1/datasets/r/pac";

    /** 13/10/2026 midi, heure de Paris. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-13T10:00:00Z"), ZoneId.of("UTC"));

    private MockRestServiceServer mockServer;

    private DataTourismeCollector newCollector(String resourceUrl, String regionFilter) {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        return new DataTourismeCollector(builder, resourceUrl, "DATAtourisme PACA", regionFilter, CLOCK);
    }

    private static String extract() throws IOException {
        try (InputStream input = DataTourismeCollectorTest.class.getResourceAsStream(
                "/datatourisme/datatourisme-extrait.csv")) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private List<CollectedActivity> collect(String regionFilter) throws IOException {
        DataTourismeCollector collector = newCollector(RESOURCE_URL, regionFilter);
        mockServer.expect(requestTo(RESOURCE_URL))
                .andRespond(withSuccess(extract(), MediaType.parseMediaType("text/csv")));
        List<CollectedActivity> result = collector.collect();
        mockServer.verify();
        return result;
    }

    private static CollectedActivity byTitle(List<CollectedActivity> activities, String prefix) {
        return activities.stream().filter(activity -> activity.title().startsWith(prefix)).findFirst().orElseThrow();
    }

    @Test
    void collect_ShouldKeepOnlyUpcomingEvents_WithoutDuplicateUris() throws IOException {
        List<CollectedActivity> result = collect("");

        // Camping (pas un événement) et Hivernales (février 2026, terminé) exclus ; URI répétée gardée une fois.
        assertEquals(4, result.size());
        assertEquals(4, result.stream().map(CollectedActivity::externalId).distinct().count());
        assertTrue(result.stream().noneMatch(activity -> activity.title().startsWith("Camping")));
        assertTrue(result.stream().noneMatch(activity -> activity.title().startsWith("Les Hivernales")));
    }

    @Test
    void collect_ShouldMapRealEventFields() throws IOException {
        CollectedActivity activity = byTitle(collect(""), "Parcours de l'Art");

        assertEquals("https://data.datatourisme.fr/13/e279be99-da0d-31f4-864a-61a99ee424e2", activity.externalId());
        assertEquals("DATAtourisme PACA", activity.source());
        assertTrue(activity.description().startsWith("Le Parcours de l’Art"));
        assertEquals("festival", activity.category());
        assertEquals(List.of("festival", "culture"), activity.tags());
        assertEquals("http://www.parcoursdelart.com/", activity.sourceUrl());
        assertEquals("Avignon", activity.location().city());
        assertEquals("84000", activity.location().postalCode());
        assertEquals("Divers lieux", activity.location().address());
        assertEquals("FR", activity.location().countryCode());
        assertEquals("Europe/Paris", activity.location().timezone());
        assertEquals(43.943953, activity.location().latitude());
        assertEquals(4.804802, activity.location().longitude());
        assertEquals(List.of(new CollectedTiming(
                LocalDateTime.of(2026, 10, 16, 0, 0), LocalDateTime.of(2026, 10, 25, 23, 59))), activity.timings());
        assertNull(activity.recurrenceRule());
    }

    @Test
    void collect_ShouldSortMultiplePeriods() throws IOException {
        List<CollectedTiming> timings = byTitle(collect(""), "Cycle de visites guidées").timings();

        assertEquals(4, timings.size());
        assertEquals(LocalDateTime.of(2026, 10, 24, 0, 0), timings.get(0).begin());
        assertEquals(LocalDateTime.of(2026, 11, 28, 23, 59), timings.get(3).end());
    }

    @Test
    void collect_ShouldKeepMultilineDescriptionAndAllContactUrls() throws IOException {
        CollectedActivity activity = byTitle(collect(""), "Hidden heritage in Aix");

        assertTrue(activity.description().contains("\n"));
        List<String> urls = activity.links().stream().map(CollectedLink::value).toList();
        assertEquals("https://reservation.aixenprovencetourism.com/", urls.get(0));
        assertTrue(urls.size() >= 2);
        assertTrue(activity.links().stream().allMatch(link -> "WEBSITE".equals(link.type())));
    }

    @Test
    void collect_ShouldFilterByDepartments() throws IOException {
        List<CollectedActivity> vaucluse = collect("84");
        assertEquals(2, vaucluse.size());
        assertTrue(vaucluse.stream().allMatch(activity -> activity.location().postalCode().startsWith("84")));

        assertEquals(3, collect("84, 13").size());
    }

    @Test
    void collect_ShouldThrow_WhenUrlMissing() {
        DataTourismeCollector collector = newCollector(" ", null);

        assertThrows(CollectorException.class, collector::collect);
    }

    @Test
    void collect_ShouldThrow_WhenServerFails() {
        DataTourismeCollector collector = newCollector(RESOURCE_URL, null);
        mockServer.expect(requestTo(RESOURCE_URL)).andRespond(withServerError());

        assertThrows(CollectorException.class, collector::collect);
    }

    @Test
    void csvReader_ShouldHandleQuotesEscapedQuotesAndCrLf() throws IOException {
        DataTourismeCollector.CsvReader reader = new DataTourismeCollector.CsvReader(
                new StringReader("a,\"b,\"\"c\"\"\nd\",e\r\nf,,g\r\n"));

        assertEquals(List.of("a", "b,\"c\"\nd", "e"), reader.next());
        assertEquals(List.of("f", "", "g"), reader.next());
        assertNull(reader.next());
    }
}
