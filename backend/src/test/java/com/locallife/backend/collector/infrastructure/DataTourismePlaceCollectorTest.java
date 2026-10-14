package com.locallife.backend.collector.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.locallife.backend.collector.domain.CollectedPlace;
import com.locallife.backend.place.domain.PlaceTypeWhitelist;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * Collecteur des lieux DATAtourisme (ADR-0011, phase B), sur un extrait réel de l'export PACA du 13/10/2026
 * (Vaucluse) : un événement, un hôtel-restaurant, un village et cinq lieux du groupe 1.
 */
class DataTourismePlaceCollectorTest {

    private static String fixture() throws IOException {
        try (InputStream in = DataTourismePlaceCollectorTest.class.getResourceAsStream(
                "/datatourisme/datatourisme-lieux-extrait.csv")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static DataTourismePlaceCollector collector(String regionFilter) {
        return new DataTourismePlaceCollector(RestClient.builder(), "https://example.org", "Lieux", regionFilter);
    }

    private static Map<String, CollectedPlace> byName(List<CollectedPlace> places) {
        return places.stream().collect(Collectors.toMap(CollectedPlace::name, place -> place));
    }

    @Test
    void parse_ShouldKeepPlacesOnly_WithAllTheirTypes() throws IOException {
        List<CollectedPlace> places = collector("84").parse(new StringReader(fixture()));

        assertEquals(7, places.size(), "l'événement n'est pas un lieu");
        CollectedPlace museum = byName(places).get("Palais du Roure, centre de culture provençale");
        assertNotNull(museum);
        assertTrue(museum.types().containsAll(List.of("Museum", "CulturalSite", "PlaceOfInterest")));
        assertTrue(museum.externalId().startsWith("https://data.datatourisme.fr/"));
        // Types schema.org ignorés : seule l'ontologie DATAtourisme compte pour la liste blanche.
        CollectedPlace hotel = byName(places).get("Hôtel Restaurant les Florets");
        assertTrue(hotel.types().contains("Accommodation"));
        assertTrue(hotel.types().stream().noneMatch(type -> type.equals("LocalBusiness") || type.contains("/")));
        assertNotNull(museum.latitude());
        assertEquals("Avignon", museum.city());
    }

    @Test
    void parse_ShouldFeedWhitelist_WithTheExpectedResult() throws IOException {
        Map<String, Optional<String>> placeTypes = collector("84").parse(new StringReader(fixture())).stream()
                .collect(Collectors.toMap(CollectedPlace::name, place -> PlaceTypeWhitelist.placeType(place.types())));

        assertEquals(Optional.of("Museum"), placeTypes.get("Palais du Roure, centre de culture provençale"));
        assertEquals(Optional.of("Castle"), placeTypes.get("Château de Lourmarin"));
        assertEquals(Optional.of("CulturalSite"), placeTypes.get("Théâtre Antique d'Orange"));
        assertEquals(Optional.of("Church"), placeTypes.get("Eglise Saint-Symphorien les Carmes"));
        assertEquals(Optional.of("TennisComplex"), placeTypes.get("Court de tennis"));
        assertEquals(Optional.empty(), placeTypes.get("Hôtel Restaurant les Florets"));
        assertEquals(Optional.empty(), placeTypes.get("Ansouis"), "un village n'est pas un lieu");
    }

    @Test
    void parse_ShouldApplyDepartmentFilter() throws IOException {
        assertTrue(collector("13").parse(new StringReader(fixture())).isEmpty());
    }

    @Test
    void parse_ShouldFail_OnSuspiciousFile() throws IOException {
        String header = fixture().lines().findFirst().orElseThrow();
        assertThrows(CollectorException.class, () -> collector("").parse(new StringReader("")));
        assertThrows(CollectorException.class,
                () -> collector("").parse(new StringReader("Nom_du_POI,Latitude\nx,1\n")));
        assertThrows(CollectorException.class, () -> collector("").parse(new StringReader(header + "\n")));
        // Seulement un événement : aucun lieu, ressource inattendue.
        String eventOnly = fixture().lines().limit(2).collect(Collectors.joining("\n"));
        assertThrows(CollectorException.class, () -> collector("").parse(new StringReader(eventOnly)));
    }

    @Test
    void website_ShouldPreferUnlabelledContact() {
        assertEquals("https://www.palais-du-roure.fr",
                DataTourismePlaceCollector.website(
                        "Billetterie#https://billet.example|#https://www.palais-du-roure.fr"));
        assertEquals("https://billet.example", DataTourismePlaceCollector.website("Billetterie#https://billet.example"));
        assertEquals(null, DataTourismePlaceCollector.website("#+33 4 90 00 00 00"));
    }
}
