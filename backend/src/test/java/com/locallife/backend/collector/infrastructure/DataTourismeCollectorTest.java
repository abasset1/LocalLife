package com.locallife.backend.collector.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.locallife.backend.collector.domain.CollectedActivity;
import com.locallife.backend.collector.domain.CollectedLink;
import com.locallife.backend.collector.domain.CollectedTiming;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Tests sur un extrait réel de l'export DATAtourisme PACA publié sur
 * data.gouv.fr le 13/10/2026 ({@code datatourisme/datatourisme-extrait.csv}) :
 * <ul>
 *   <li>un camping (pas un événement) ;</li>
 *   <li>Avignon : « Parcours de l'Art » (festival, « Divers lieux »), « Les Hivernales » (passé), un cycle
 *       de visites à quatre dates ;</li>
 *   <li>« Hidden heritage in Aix » (visite publiée d'avril à octobre) et le « Grand marché provençal du jeudi
 *       matin » (toute l'année, ligne en double) : périodes de validité ;</li>
 *   <li>deux spectacles à Aubagne et Marseille (billetterie parmi les contacts, description sur plusieurs
 *       lignes) ;</li>
 *   <li>une exposition de 58 jours à Apt et « Octobre Rose » à Pertuis (« Centre ville », sans lien).</li>
 * </ul>
 * Les fichiers invalides sont construits en ligne, avec l'en-tête réel.
 */
class DataTourismeCollectorTest {

    private static final String RESOURCE_URL = "https://www.data.gouv.fr/api/1/datasets/r/pac";

    /** 13/10/2026 midi, heure de Paris. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-13T10:00:00Z"), ZoneId.of("UTC"));

    private static final String HEADER = "Nom_du_POI,Categories_de_POI,Latitude,Longitude,Adresse_postale,"
            + "Code_postal_et_commune,Periodes_regroupees,Covid19_mesures_specifiques,Createur_de_la_donnee,"
            + "SIT_diffuseur,Date_de_mise_a_jour,Contacts_du_POI,Classements_du_POI,Description,URI_ID_du_POI\n";

    private static final String EVENT_ROW = "Concert,https://www.datatourisme.fr/ontology/core#EntertainmentAndEvent|"
            + "https://www.datatourisme.fr/ontology/core#Concert,43.95,4.81,1 rue de la République,84000#Avignon,"
            + "2026-11-01<->2026-11-01,,OT,SIT,2026-10-01,#https://example.org/,,Un concert,"
            + "https://data.datatourisme.fr/13/concert\n";

    private static final String PLACE_ROW = "Camping,https://www.datatourisme.fr/ontology/core#PlaceOfInterest,"
            + "44.3,6.3,Route,04140#Seyne,,,OT,SIT,2026-01-16,#https://example.org/,,Un camping,"
            + "https://data.datatourisme.fr/13/camping\n";

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

    private List<CollectedActivity> collectBody(String body, String regionFilter) {
        DataTourismeCollector collector = newCollector(RESOURCE_URL, regionFilter);
        mockServer.expect(requestTo(RESOURCE_URL))
                .andRespond(withSuccess(body, MediaType.parseMediaType("text/csv")));
        List<CollectedActivity> result = collector.collect();
        mockServer.verify();
        return result;
    }

    private List<CollectedActivity> collect(String regionFilter) throws IOException {
        return collectBody(extract(), regionFilter);
    }

    private void assertRejected(String body) {
        DataTourismeCollector collector = newCollector(RESOURCE_URL, null);
        mockServer.expect(requestTo(RESOURCE_URL))
                .andRespond(withSuccess(body, MediaType.parseMediaType("text/csv")));

        assertThrows(CollectorException.class, collector::collect);
    }

    private static CollectedActivity byTitle(List<CollectedActivity> activities, String prefix) {
        return activities.stream().filter(activity -> activity.title().startsWith(prefix)).findFirst().orElseThrow();
    }

    private static List<String> titles(List<CollectedActivity> activities) {
        return activities.stream().map(CollectedActivity::title).toList();
    }

    @Test
    void collect_ShouldKeepOnlyUpcomingEventsWithRealDates() throws IOException {
        List<String> titles = titles(collect(""));

        assertEquals(6, titles.size());
        assertTrue(titles.stream().noneMatch(title -> title.startsWith("Camping")));
        assertTrue(titles.stream().noneMatch(title -> title.startsWith("Les Hivernales")));
        assertTrue(titles.stream().noneMatch(title -> title.startsWith("Hidden heritage")));
        assertTrue(titles.stream().noneMatch(title -> title.startsWith("Grand marché")));
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

    /** Anomalie 2 : une saison n'est pas présentée comme un intervalle continu ; une exposition l'est. */
    @Test
    void collect_ShouldDropValidityWindows_ButKeepLongExhibitions() throws IOException {
        List<CollectedActivity> result = collect("");

        assertFalse(titles(result).stream().anyMatch(title -> title.startsWith("Grand marché")));
        assertFalse(titles(result).stream().anyMatch(title -> title.startsWith("Hidden heritage")));
        CollectedActivity exhibition = byTitle(result, "Exposition \"Luc MAUREL");
        assertEquals(List.of(new CollectedTiming(
                LocalDateTime.of(2026, 9, 4, 0, 0), LocalDateTime.of(2026, 10, 31, 23, 59))), exhibition.timings());
    }

    /**
     * Règle métier validée : au-delà de 31 jours, la période d'un type non continu est une période de
     * validité et l'événement n'est pas importé ; de 8 à 31 jours, la période est gardée telle quelle.
     */
    @Test
    void collect_ShouldApplyThirtyOneDayRule_AtItsBoundaries() {
        String eightDays = EVENT_ROW.replace("2026-11-01<->2026-11-01", "2026-11-01<->2026-11-08")
                .replace("13/concert", "13/huit-jours");
        String thirtyOneDays = EVENT_ROW.replace("2026-11-01<->2026-11-01", "2026-11-01<->2026-12-01")
                .replace("13/concert", "13/trente-et-un-jours");
        String thirtyTwoDays = EVENT_ROW.replace("2026-11-01<->2026-11-01", "2026-11-01<->2026-12-02")
                .replace("13/concert", "13/trente-deux-jours");

        List<CollectedActivity> result = collectBody(HEADER + eightDays + thirtyOneDays + thirtyTwoDays, null);

        assertEquals(31, DataTourismeCollector.MAX_OCCURRENCE_DAYS);
        assertEquals(List.of("https://data.datatourisme.fr/13/huit-jours",
                "https://data.datatourisme.fr/13/trente-et-un-jours"),
                result.stream().map(CollectedActivity::externalId).toList());
        assertEquals(LocalDateTime.of(2026, 12, 1, 23, 59), result.get(1).timings().get(0).end());
    }

    @Test
    void collect_ShouldKeepContinuousTypes_BeyondThirtyOneDays() {
        String longExhibition = EVENT_ROW.replace("core#Concert", "core#Exhibition")
                .replace("2026-11-01<->2026-11-01", "2026-11-01<->2027-03-31");

        List<CollectedActivity> result = collectBody(HEADER + longExhibition, null);

        assertEquals(1, result.size());
        assertEquals(LocalDateTime.of(2027, 3, 31, 23, 59), result.get(0).timings().get(0).end());
    }

    @Test
    void collect_ShouldKeepShortPeriods_WhenMixedWithValidityWindow() {
        String row = EVENT_ROW.replace("2026-11-01<->2026-11-01", "2026-01-01<->2026-12-31|2026-11-01<->2026-11-01");

        List<CollectedActivity> result = collectBody(HEADER + row, null);

        assertEquals(1, result.size());
        assertEquals(List.of(new CollectedTiming(
                LocalDateTime.of(2026, 11, 1, 0, 0), LocalDateTime.of(2026, 11, 1, 23, 59))), result.get(0).timings());
    }

    /** Anomalie 3 : une adresse générique devient le nom du lieu, sans adresse précise. */
    @Test
    void collect_ShouldNotPresentGenericAddressAsPreciseAddress() throws IOException {
        List<CollectedActivity> result = collect("");

        CollectedActivity parcours = byTitle(result, "Parcours de l'Art");
        assertEquals("Divers lieux", parcours.location().name());
        assertNull(parcours.location().address());
        CollectedActivity octobreRose = byTitle(result, "Octobre Rose");
        assertEquals("Centre ville", octobreRose.location().name());
        assertNull(octobreRose.location().address());
        assertEquals("Pertuis", octobreRose.location().city());
        CollectedActivity theatre = byTitle(result, "Ces choses là");
        assertNull(theatre.location().name());
        assertTrue(theatre.location().address().startsWith("Théâtre des Chartreux"));
    }

    @Test
    void isGenericAddress_ShouldRecognizeOnlyAreasAndMultiplePlaces() {
        assertTrue(DataTourismeCollector.isGenericAddress("Divers lieux à Aix-en-Provence", "Aix-en-Provence"));
        assertTrue(DataTourismeCollector.isGenericAddress("Différents lieux de la commune", "Arles"));
        assertTrue(DataTourismeCollector.isGenericAddress("Centre ville et divers lieux", "Hyères"));
        assertTrue(DataTourismeCollector.isGenericAddress("Centre ville Saint-Rémy-de-Provence",
                "Saint-Rémy-de-Provence"));
        assertTrue(DataTourismeCollector.isGenericAddress("Dans les rues du village", "Saint-Paul-de-Vence"));
        assertFalse(DataTourismeCollector.isGenericAddress("Centre ville Cours Hyacinthe Bellon", "Ollioules"));
        assertFalse(DataTourismeCollector.isGenericAddress("Place du village", "Bonnieux"));
        assertFalse(DataTourismeCollector.isGenericAddress("90 Boulevard Victor Hugo Théâtre de Pertuis", "Pertuis"));
    }

    /** Anomalie 4 : site de l'événement, puis tiers, puis billetterie — quel que soit l'ordre de la source. */
    @Test
    void collect_ShouldRankEventSiteBeforeThirdPartiesAndBooking() throws IOException {
        List<CollectedActivity> result = collect("");

        CollectedActivity chartreux = byTitle(result, "Ces choses là");
        assertTrue(chartreux.sourceUrl().startsWith("https://www.theatredeschartreux.com/"));
        assertTrue(chartreux.links().get(chartreux.links().size() - 1).value().startsWith("https://www.helloasso.com/"));

        CollectedActivity eiffel = byTitle(result, "Gustave Eiffel");
        assertTrue(eiffel.description().contains("\n"));
        assertEquals("https://www.aubagne.fr/", eiffel.sourceUrl());
        assertEquals(List.of(
                new CollectedLink("WEBSITE", null, "https://www.aubagne.fr/"),
                new CollectedLink("WEBSITE", "Réservation", "https://comoedia-aubagnebilletterie.mapado.com/"),
                new CollectedLink("WEBSITE", "Comoedia", "https://comoedia-aubagnebilletterie.mapado.com/event/"
                        + "832195-gustave-eiffel-en-fer-et-contre-tous")), eiffel.links());

        CollectedActivity octobreRose = byTitle(result, "Octobre Rose");
        assertTrue(octobreRose.links().isEmpty());
        assertEquals(octobreRose.externalId(), octobreRose.sourceUrl());
    }

    @Test
    void collect_ShouldFilterByDepartments() throws IOException {
        List<CollectedActivity> vaucluse = collect("84");
        assertEquals(4, vaucluse.size());
        assertTrue(vaucluse.stream().allMatch(activity -> activity.location().postalCode().startsWith("84")));

        assertEquals(6, collect("84, 13").size());
    }

    @Test
    void collect_ShouldKeepOneActivityPerUri() {
        assertEquals(1, collectBody(HEADER + EVENT_ROW + EVENT_ROW, null).size());
    }

    /** Anomalie 1 : un fichier valide sans événement retenu est un import vide légitime. */
    @Test
    void collect_ShouldReturnEmptyList_WhenValidFileHasNoMatchingEvent() throws IOException {
        assertTrue(collect("06").isEmpty());
    }

    /** Anomalie 1 : un fichier suspect interrompt la collecte, donc aucun archivage. */
    @Test
    void collect_ShouldThrow_WhenFileIsSuspicious() {
        assertRejected("");
        assertRejected(HEADER);
        assertRejected(HEADER.replace("Periodes_regroupees", "Periodes") + EVENT_ROW);
        assertRejected("<!doctype html><html><body>Maintenance</body></html>\n");
        assertRejected(HEADER + PLACE_ROW);
        assertRejected(HEADER + EVENT_ROW + "ligne,tronquée\n");
    }

    @Test
    void collect_ShouldThrow_WhenDownloadIsCutOff() {
        DataTourismeCollector collector = newCollector(RESOURCE_URL, null);
        InputStream cutOff = new SequenceInputStream(
                new ByteArrayInputStream((HEADER + EVENT_ROW).getBytes(StandardCharsets.UTF_8)),
                new InputStream() {
                    @Override
                    public int read() throws IOException {
                        throw new IOException("connexion interrompue");
                    }
                });
        mockServer.expect(requestTo(RESOURCE_URL))
                .andRespond(request -> new MockClientHttpResponse(cutOff, HttpStatus.OK));

        assertThrows(CollectorException.class, collector::collect);
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
