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
 *       matin » (toute l'année, ligne en double) : longues périodes, importées telles quelles ;</li>
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

        assertEquals(8, titles.size());
        assertTrue(titles.stream().noneMatch(title -> title.startsWith("Camping")));
        assertTrue(titles.stream().noneMatch(title -> title.startsWith("Les Hivernales")));
        assertEquals(1, titles.stream().filter(title -> title.startsWith("Hidden heritage")).count());
        assertEquals(1, titles.stream().filter(title -> title.startsWith("Grand marché")).count());
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

    /** Toutes les périodes sont importées, quelle que soit leur durée : marché à l'année, exposition, saison. */
    @Test
    void collect_ShouldKeepLongPeriods() throws IOException {
        List<CollectedActivity> result = collect("");

        assertEquals(List.of(new CollectedTiming(
                LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2026, 12, 31, 23, 59))),
                byTitle(result, "Grand marché").timings());
        CollectedActivity exhibition = byTitle(result, "Exposition \"Luc MAUREL");
        assertEquals(List.of(new CollectedTiming(
                LocalDateTime.of(2026, 9, 4, 0, 0), LocalDateTime.of(2026, 10, 31, 23, 59))), exhibition.timings());
    }

    @Test
    void collect_ShouldKeepPeriodsOfAnyLength_ForAnyType() {
        String market = EVENT_ROW.replace("core#Concert", "core#Market")
                .replace("2026-11-01<->2026-11-01", "2026-11-27<->2027-01-02");
        String mixed = EVENT_ROW.replace("2026-11-01<->2026-11-01", "2026-01-01<->2026-12-31|2026-11-01<->2026-11-01")
                .replace("13/concert", "13/mixte");

        List<CollectedActivity> result = collectBody(HEADER + market + mixed, null);

        assertEquals(2, result.size());
        assertEquals(List.of(new CollectedTiming(
                LocalDateTime.of(2026, 11, 27, 0, 0), LocalDateTime.of(2027, 1, 2, 23, 59))),
                result.get(0).timings());
        assertEquals(2, result.get(1).timings().size());
    }

    /** Seul critère de date : un événement dont toutes les périodes sont terminées avant aujourd'hui est écarté. */
    @Test
    void collect_ShouldRejectOnlyEventsEndedBeforeToday() {
        String endedYesterday = EVENT_ROW.replace("2026-11-01<->2026-11-01", "2026-01-01<->2026-10-12")
                .replace("13/concert", "13/hier");
        String endsToday = EVENT_ROW.replace("2026-11-01<->2026-11-01", "2026-01-01<->2026-10-13")
                .replace("13/concert", "13/aujourdhui");

        List<CollectedActivity> result = collectBody(HEADER + endedYesterday + endsToday, null);

        assertEquals(List.of("https://data.datatourisme.fr/13/aujourdhui"),
                result.stream().map(CollectedActivity::externalId).toList());
    }

    /** « 2026-11-27<->2026-01-02 » (export réel, marché de Noël d'Avignon) : la fin est l'année suivante. */
    @Test
    void collect_ShouldMoveEndToNextYear_WhenPublishedBeforeBegin() {
        String christmasMarket = EVENT_ROW.replace("core#Concert", "core#Market")
                .replace("2026-11-01<->2026-11-01", "2026-11-27<->2026-01-02");

        List<CollectedActivity> result = collectBody(HEADER + christmasMarket, null);

        assertEquals(1, result.size());
        assertEquals(List.of(new CollectedTiming(
                LocalDateTime.of(2026, 11, 27, 0, 0), LocalDateTime.of(2027, 1, 2, 23, 59))),
                result.get(0).timings());
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

        // Avec « Hidden heritage in Aix » (13), longue période désormais importée.
        assertEquals(7, collect("84, 13").size());
    }

    @Test
    void collect_ShouldKeepOnlySelectedEvents_WhenSourceHasASelection() {
        // Le type d'abord : « Concert, » figure aussi dans « core#Concert, ».
        String garageSale = EVENT_ROW.replace("core#Concert", "core#GarageSale")
                .replace("Concert,", "Vide ta chambre,").replace("13/concert", "13/garage-sale");
        String titledOnly = EVENT_ROW.replace("core#Concert", "core#SaleEvent")
                .replace("Concert,", "Bourse aux jouets,").replace("13/concert", "13/bourse");
        // Typé GarageSale, mais d'un autre genre ; la description annonce ou non une vente d'occasion.
        String otherKind = EVENT_ROW.replace("core#Concert", "core#GarageSale")
                .replace("Concert,", "Marché de Noël,").replace("13/concert", "13/marche-de-noel");
        String otherKindWithSale = otherKind.replace("Un concert", "Artisanat et brocante")
                .replace("13/marche-de-noel", "13/marche-de-noel-brocante");

        List<CollectedActivity> result = collectSelection(
                HEADER + EVENT_ROW + garageSale + titledOnly + otherKind + otherKindWithSale, null);

        assertEquals(List.of("Vide ta chambre", "Bourse aux jouets", "Marché de Noël"), titles(result));
        assertEquals("https://data.datatourisme.fr/13/marche-de-noel-brocante", result.get(2).externalId());
    }

    private List<CollectedActivity> collectSelection(String body, String regionFilter) {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        DataTourismeCollector collector = new DataTourismeCollector(builder, RESOURCE_URL, "DATAtourisme France",
                regionFilter, DataTourismeEventSelection.VIDE_GRENIERS_BROCANTES, CLOCK);
        mockServer.expect(requestTo(RESOURCE_URL)).andRespond(withSuccess(body, MediaType.parseMediaType("text/csv")));
        return collector.collect();
    }

    /**
     * Annulation signalée par le titre seulement (export France du 04/11/2026 : « Brocante de Sancerre annulée »,
     * typée BricABrac) : l'événement n'est pas collecté. « Reporté » ne suffit pas : la période de « REPORTÉ - VIDE
     * DRESSING SOLIDAIRE » était déjà celle de la nouvelle date.
     */
    @Test
    void collect_ShouldDropCancelledEvents_ButKeepPostponedOnes() {
        String cancelled = EVENT_ROW.replace("Concert,", "Brocante de Sancerre annulée,")
                .replace("13/concert", "13/sancerre");
        String cancelledPrefix = EVENT_ROW.replace("Concert,", "ANNULÉ - Feu d'artifice à Cahors,")
                .replace("13/concert", "13/cahors");
        String postponed = EVENT_ROW.replace("Concert,", "REPORTÉ - VIDE DRESSING SOLIDAIRE,")
                .replace("13/concert", "13/vide-dressing");
        String partial = EVENT_ROW.replace("Concert,", "Winter Jazz Festival - China MOSES (annulation Rhoda SCOTT),")
                .replace("13/concert", "13/jazz");

        List<String> result = titles(collectBody(HEADER + cancelled + cancelledPrefix + postponed + partial, null));

        assertEquals(List.of("REPORTÉ - VIDE DRESSING SOLIDAIRE",
                "Winter Jazz Festival - China MOSES (annulation Rhoda SCOTT)"), result);
    }

    /** Export France du 04/11/2026 : « VIDE-GRENIER DE MOVEMBER », Oupia (34210), placé en Floride. */
    @Test
    void collect_ShouldDropPositionsOutsideTheTerritoryOfThePostalCode() {
        String florida = EVENT_ROW.replace("43.95,4.81", "27.4361633,-82.615927")
                .replace("84000#Avignon", "34210#Oupia").replace("13/concert", "13/movember");
        String swapped = EVENT_ROW.replace("43.95,4.81", "4.81,43.95").replace("13/concert", "13/inverse");
        String reunion = EVENT_ROW.replace("43.95,4.81", "-20.88,55.45")
                .replace("84000#Avignon", "97400#Saint-Denis").replace("13/concert", "13/reunion");
        String reunionInParis = reunion.replace("-20.88,55.45", "48.85,2.35").replace("13/reunion", "13/reunion-paris");

        List<CollectedActivity> result = collectBody(HEADER + florida + swapped + EVENT_ROW + reunion + reunionInParis,
                null);

        assertEquals(List.of("https://data.datatourisme.fr/13/concert", "https://data.datatourisme.fr/13/reunion"),
                result.stream().map(CollectedActivity::externalId).toList());
    }

    /** Emprise par territoire : les extrémités et les communes frontalières restent dans la collecte. */
    @Test
    void isPlausiblePosition_ShouldAcceptFrenchExtremesAndBorders() {
        assertTrue(DataTourismeCollector.isPlausiblePosition("29242", 48.46, -5.09)); // Ouessant
        assertTrue(DataTourismeCollector.isPlausiblePosition("59140", 51.03, 2.37)); // Dunkerque
        assertTrue(DataTourismeCollector.isPlausiblePosition("20169", 41.39, 9.16)); // Bonifacio
        assertTrue(DataTourismeCollector.isPlausiblePosition("06500", 43.77, 7.50)); // Menton
        assertTrue(DataTourismeCollector.isPlausiblePosition("67000", 48.58, 7.75)); // Strasbourg
        // Une position dans un département voisin n'est pas écartée.
        assertTrue(DataTourismeCollector.isPlausiblePosition("84000", 43.53, 5.45)); // Aix-en-Provence
        assertTrue(DataTourismeCollector.isPlausiblePosition("97150", 18.07, -63.08)); // Saint-Martin
        assertTrue(DataTourismeCollector.isPlausiblePosition("97300", 4.94, -52.33)); // Cayenne
        assertTrue(DataTourismeCollector.isPlausiblePosition("98714", -17.53, -149.57)); // Papeete : pas d'emprise
        assertTrue(DataTourismeCollector.isPlausiblePosition("", 48.85, 2.35));

        assertFalse(DataTourismeCollector.isPlausiblePosition("34210", 27.4361633, -82.615927));
        assertFalse(DataTourismeCollector.isPlausiblePosition("84000", 0, 0));
        assertFalse(DataTourismeCollector.isPlausiblePosition("97200", 48.85, 2.35));
        assertFalse(DataTourismeCollector.isPlausiblePosition(null, 27.4, -82.6));
    }

    @Test
    void collect_ShouldKeepEveryEvent_WithoutSelection() {
        String garageSale = EVENT_ROW.replace("core#Concert", "core#GarageSale")
                .replace("Concert,", "Vide-grenier,").replace("13/concert", "13/garage-sale");

        assertEquals(List.of("Concert", "Vide-grenier"), titles(collectBody(HEADER + EVENT_ROW + garageSale, null)));
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

    /** Lignes mal formées : 1 % est toléré (limite exacte), au-delà la collecte échoue. */
    @Test
    void collect_ShouldApplyMalformedRowRatio_AtItsBoundary() {
        String onePercent = HEADER + validRows(99) + "ligne,tronquée\n";
        String twoPercent = HEADER + validRows(98) + "ligne,tronquée\n" + "autre,ligne,tronquée\n";

        assertEquals(99, collectBody(onePercent, null).size());
        assertRejected(twoPercent);
    }

    private static String validRows(int count) {
        StringBuilder rows = new StringBuilder();
        for (int i = 0; i < count; i++) {
            rows.append(EVENT_ROW.replace("13/concert", "13/concert-" + i));
        }
        return rows.toString();
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
