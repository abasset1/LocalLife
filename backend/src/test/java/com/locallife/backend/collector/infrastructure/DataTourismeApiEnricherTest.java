package com.locallife.backend.collector.infrastructure;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.locallife.backend.collector.domain.CollectedActivity;
import com.locallife.backend.collector.domain.CollectedLink;
import com.locallife.backend.collector.domain.CollectedLocation;
import com.locallife.backend.collector.domain.CollectedMedia;
import com.locallife.backend.collector.domain.CollectedTiming;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Tests sur des fiches réelles de l'API DATAtourisme (lues le 07/11/2026, {@code datatourisme/api-catalog.json}) :
 * « Programme Cinéma de L'Eden » à Noves (programme séance par séance, tarif en clair, affiche, site, téléphone,
 * horaires en clair), un marché en accès libre à Cassis, et une visite de Fayence qui n'a que des montants.
 */
class DataTourismeApiEnricherTest {

    private static final String BASE_URL = "https://api.datatourisme.fr/v1/catalog";
    private static final String EMPTY_PAGE = "{\"objects\":[],\"meta\":{\"total\":0}}";
    private static final String KEY = "cle-de-test";
    private static final String EDEN = "https://data.datatourisme.fr/58/db7a083f-4199-3a42-9a27-612139cecc06";
    private static final String CASSIS = "https://data.datatourisme.fr/58/6c27bcaa-8334-3291-945f-d5a5355b04b7";
    private static final String FAYENCE = "https://data.datatourisme.fr/13/34276d7d-c42b-3a47-ae71-2eac2af8250d";
    private static final String UNKNOWN = "https://data.datatourisme.fr/13/absent-de-l-api";

    private MockRestServiceServer mockServer;
    private int pauses;

    private DataTourismeApiEnricher newEnricher(String key) {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        pauses = 0;
        return new DataTourismeApiEnricher(builder, key, BASE_URL, new ObjectMapper(), Duration.ZERO, pause -> pauses++);
    }

    private static String fixture(String name) throws IOException {
        try (InputStream input = DataTourismeApiEnricherTest.class.getResourceAsStream("/datatourisme/" + name)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Événement tel que le produit le collecteur CSV : description courte seule, lien du CSV. */
    private static CollectedActivity csvEvent(String uri, String title, String description, String link) {
        List<CollectedLink> links = link == null ? List.of() : List.of(new CollectedLink("WEBSITE", null, link));
        return new CollectedActivity(title, description, "projection", link == null ? uri : link, uri,
                "DATAtourisme PACA", null, null, null, null,
                new CollectedLocation(null, "Rue du 19 mars 1962", "Noves", "13550", null, "FR", 43.87608,
                        4.902423, "Europe/Paris"),
                List.of(new CollectedTiming(LocalDateTime.parse("2026-11-09T00:00"),
                        LocalDateTime.parse("2026-11-15T23:59"))),
                null, List.of(), links, null, List.of("projection"), null);
    }

    /** Une requête par lot : les URI demandées dans le filtre, la clé dans l'en-tête. */
    private void expectCatalogFor(String... uris) throws IOException {
        var expectation = mockServer.expect(requestTo(startsWith(BASE_URL + "?filters=uri%5Bin%5D%3D")))
                .andExpect(requestTo(containsString("page_size=100")))
                .andExpect(header("X-API-Key", KEY));
        for (String uri : uris) {
            expectation = expectation.andExpect(requestTo(containsString(uri)));
        }
        expectation.andRespond(withSuccess(fixture("api-catalog.json"), MediaType.APPLICATION_JSON));
    }

    @Test
    void enrich_ShouldAddProgrammePriceImageAndContacts_ToTheCinemaProgramme() throws IOException {
        DataTourismeApiEnricher enricher = newEnricher(KEY);
        expectCatalogFor(EDEN);
        CollectedActivity eden = csvEvent(EDEN, "Programme Cinéma de L'Eden",
                "Programme Cinéma, semaine du 09 au 15 Novembre", "https://noves.fr/cinema-leden/");

        CollectedActivity enriched = enricher.enrich(List.of(eden)).get(0);

        mockServer.verify();
        assertEquals("Programme Cinéma, semaine du 09 au 15 Novembre", enriched.description());
        assertTrue(enriched.longDescription().startsWith("Lundi 09 Novembre 14h00: Carmen, l'oiseau rebelle\n"
                + "Mardi 10 Novembre 20h30:  Le Corset\n"), enriched.longDescription());
        assertTrue(enriched.longDescription().contains("Dimanche 15 Novembre 18h00: La maison de nos rêves"));
        assertFalse(enriched.longDescription().contains("\r"));
        assertTrue(enriched.longDescription().endsWith(
                "\n\nHoraires : Du 09/11 au 15/11/2026 le lundi, mardi, mercredi et les week-ends."));
        assertEquals("Plein tarif : 5€ / Tarif adhérent : 3€.", enriched.conditions());
        assertEquals(List.of(new CollectedMedia(
                "https://static.apidae-tourisme.com/filestore/objets-touristiques/images/215/176/43495639.jpg",
                "Programme Cinéma de L'Eden_Noves", "Le Cinoche", 2000, 1414)), enriched.media());
        // Le site du CSV n'est pas doublé ; le téléphone de l'API s'y ajoute.
        assertEquals(List.of(
                new CollectedLink("WEBSITE", null, "https://noves.fr/cinema-leden/"),
                new CollectedLink("PHONE", null, "+33 4 90 24 43 00")), enriched.links());
        // Ce que le CSV porte déjà est conservé.
        assertEquals(eden.externalId(), enriched.externalId());
        assertEquals(eden.title(), enriched.title());
        assertEquals(eden.timings(), enriched.timings());
        assertEquals(eden.location(), enriched.location());
        assertEquals("https://noves.fr/cinema-leden/", enriched.sourceUrl());
    }

    /** Marché resté continu après le CSV : jours lus dans les horaires de l'API (fiche réelle de L'Eden). */
    @Test
    void enrich_ShouldSplitAContinuousMarket_IntoTheDaysOfTheApiOpeningHours() throws IOException {
        DataTourismeApiEnricher enricher = newEnricher(KEY);
        expectCatalogFor(EDEN);
        CollectedActivity csv = csvEvent(EDEN, "Marché", null, null);
        CollectedActivity market = new CollectedActivity(csv.title(), csv.description(), "marché", csv.sourceUrl(),
                csv.externalId(), csv.source(), null, null, null, null, csv.location(),
                List.of(new CollectedTiming(LocalDateTime.parse("2026-11-01T00:00"),
                        LocalDateTime.parse("2026-11-30T23:59"))),
                null, List.of(), List.of(), null, List.of("marché"), null);

        CollectedActivity enriched = enricher.enrich(List.of(market)).get(0);

        mockServer.verify();
        // « le lundi, mardi, mercredi et les week-ends » : 22 jours en novembre 2026.
        assertEquals(22, enriched.timings().size());
        assertTrue(enriched.timings().stream().noneMatch(timing -> timing.begin().getDayOfWeek()
                == java.time.DayOfWeek.THURSDAY || timing.begin().getDayOfWeek() == java.time.DayOfWeek.FRIDAY));
    }

    @Test
    void enrich_ShouldTakePricesFromTheText_OrFromTheAmounts_AndLeaveUnknownEventsUntouched() throws IOException {
        DataTourismeApiEnricher enricher = newEnricher(KEY);
        expectCatalogFor(CASSIS, FAYENCE, UNKNOWN);
        CollectedActivity cassis = csvEvent(CASSIS, "Marché italien - Portofino è Cassis", null, null);
        CollectedActivity fayence = csvEvent(FAYENCE, "Visite commentée de Fayence", "Visite", null);
        CollectedActivity unknown = csvEvent(UNKNOWN, "Absent de l'API", "Description", null);

        List<CollectedActivity> enriched = enricher.enrich(List.of(cassis, fayence, unknown));

        assertEquals("Accès libre.", enriched.get(0).conditions());
        // Pas de description courte dans le CSV : celle de l'API la remplace.
        assertTrue(enriched.get(0).description().startsWith("Du 6 au 11 novembre 2026, retrouvez la 17"));
        assertTrue(enriched.get(0).longDescription().startsWith("PROGRAMME (sous réserve de modifications) :"));
        assertEquals("5 €.", enriched.get(1).conditions());
        assertEquals("Visite", enriched.get(1).description());
        assertNull(enriched.get(1).longDescription());
        assertSame(unknown, enriched.get(2));
    }

    @Test
    void enrich_ShouldKeepTheCsvEvents_WhenTheApiFails() {
        DataTourismeApiEnricher enricher = newEnricher(KEY);
        mockServer.expect(times(DataTourismeApiEnricher.MAX_ATTEMPTS), requestTo(startsWith(BASE_URL)))
                .andRespond(withServerError());
        List<CollectedActivity> events = List.of(csvEvent(EDEN, "Programme", "Court", null));

        assertSame(events, enricher.enrich(events));
        mockServer.verify();
    }

    @Test
    void enrich_ShouldNotRetry_WhenTheKeyIsRefused() {
        DataTourismeApiEnricher enricher = newEnricher(KEY);
        mockServer.expect(times(1), requestTo(startsWith(BASE_URL))).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        List<CollectedActivity> events = List.of(csvEvent(EDEN, "Programme", "Court", null));

        assertSame(events, enricher.enrich(events));
        mockServer.verify();
    }

    @Test
    void enrich_ShouldCallNothing_WithoutKey_OrWithoutDataTourismeEvents() {
        List<CollectedActivity> events = List.of(csvEvent(EDEN, "Programme", "Court", null));

        DataTourismeApiEnricher withoutKey = newEnricher(" ");
        assertFalse(withoutKey.isEnabled());
        assertSame(events, withoutKey.enrich(events));
        mockServer.verify();

        DataTourismeApiEnricher otherIds = newEnricher(KEY);
        List<CollectedActivity> notDataTourisme = List.of(csvEvent("openagenda-123", "Autre", "Court", null));
        assertEquals(notDataTourisme, otherIds.enrich(notDataTourisme));
        mockServer.verify();
    }

    @Test
    void enrich_ShouldAskTheApiByBatchesOfFifty_WhateverTheDepartmentFilter() {
        DataTourismeApiEnricher enricher = newEnricher(KEY);
        mockServer.expect(times(3), requestTo(startsWith(BASE_URL + "?filters=uri%5Bin%5D%3D")))
                .andRespond(withSuccess(EMPTY_PAGE, MediaType.APPLICATION_JSON));
        List<CollectedActivity> events = new ArrayList<>();
        for (int i = 0; i < 120; i++) {
            events.add(csvEvent(DataTourismeApiEnricher.URI_PREFIX + "13/evenement-" + i, "Événement " + i, "C", null));
        }

        assertEquals(events, enricher.enrich(events));

        mockServer.verify();
        // Une pause entre deux requêtes (quota ~10 requêtes par seconde).
        assertEquals(2, pauses);
    }

    @Test
    void enrich_ShouldSkip_BeyondTheRequestGuard() {
        DataTourismeApiEnricher enricher = newEnricher(KEY);
        List<CollectedActivity> events = new ArrayList<>();
        int tooMany = DataTourismeApiEnricher.BATCH_SIZE * DataTourismeApiEnricher.MAX_REQUESTS + 1;
        for (int i = 0; i < tooMany; i++) {
            events.add(csvEvent(DataTourismeApiEnricher.URI_PREFIX + "13/e-" + i, "E", "C", null));
        }

        assertSame(events, enricher.enrich(events));
        mockServer.verify();
    }

    @Test
    void priceText_ShouldDescribeTheAmounts_WhenTheSourceHasNoText() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        assertEquals("De 10 à 35 €.", DataTourismeApiEnricher.priceText(mapper.readTree(
                "[{\"priceSpecification\":[{\"minPrice\":[10],\"maxPrice\":[35]}]}]")));
        assertEquals("12,50 €.", DataTourismeApiEnricher.priceText(mapper.readTree(
                "[{\"priceSpecification\":[{\"minPrice\":[\"12.5\"],\"maxPrice\":[12.5]}]}]")));
        assertEquals("Gratuit.", DataTourismeApiEnricher.priceText(mapper.readTree(
                "[{\"priceSpecification\":[{\"minPrice\":[0],\"maxPrice\":[0]}]}]")));
        assertNull(DataTourismeApiEnricher.priceText(mapper.readTree("[{\"acceptedPaymentMethod\":[]}]")));
        assertNull(DataTourismeApiEnricher.priceText(mapper.readTree("[]")));
    }
}
