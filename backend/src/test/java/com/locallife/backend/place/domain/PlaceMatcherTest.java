package com.locallife.backend.place.domain;

import static com.locallife.backend.place.domain.CoordinatePrecision.APPROXIMATE;
import static com.locallife.backend.place.domain.CoordinatePrecision.UNKNOWN;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.locallife.backend.place.domain.PlaceMatcher.Candidate;
import com.locallife.backend.place.domain.PlaceMatcher.Level;
import org.junit.jupiter.api.Test;

/** Rapprochement lieu d'événement ↔ lieu permanent (ADR-0011, § 2.3). */
class PlaceMatcherTest {

    /** Environ 1,11 m par 0,00001 degré de latitude. */
    private static Candidate at(String name, String address, double latitudeOffsetMeters) {
        return new Candidate(name, address, "84000", 43.9508 + latitudeOffsetMeters / 111_000, 4.8075, UNKNOWN);
    }

    @Test
    void sameNameNearby_ShouldBeCertain() {
        assertEquals(Level.CERTAIN, PlaceMatcher.compare(
                at("Palais des Papes", "Place du Palais", 0), at("PALAIS DES PAPES", null, 20)));
    }

    @Test
    void sameAddressAndCloseName_ShouldBeCertain() {
        assertEquals(Level.CERTAIN, PlaceMatcher.compare(
                at("Musée Calvet", "65 rue Joseph Vernet", 0),
                at("Musée Calvet - Fondation Calvet", "65, rue Joseph-Vernet", 10)));
    }

    @Test
    void closeNameUpToOneHundredMeters_ShouldBeProbable() {
        assertEquals(Level.PROBABLE, PlaceMatcher.compare(
                at("Théâtre du Chêne Noir", null, 0), at("Chêne Noir", null, 60)));
        assertEquals(Level.PROBABLE, PlaceMatcher.compare(
                at("Palais des Papes", null, 0), at("Palais des Papes", null, 80)));
    }

    @Test
    void sameAddressDifferentNames_ShouldBeAmbiguous() {
        assertEquals(Level.AMBIGUOUS, PlaceMatcher.compare(
                at("Salle des fêtes", "Place de l'Horloge", 0), at("Hôtel de ville", "Place de l'Horloge", 10)));
    }

    @Test
    void shouldNeverMatch_FarAwayOtherPostalCodeOrApproximate() {
        assertEquals(Level.NONE, PlaceMatcher.compare(
                at("Palais des Papes", null, 0), at("Palais des Papes", null, 150)));
        Candidate otherCity = new Candidate("Palais des Papes", null, "84800", 43.9508, 4.8075, UNKNOWN);
        assertEquals(Level.NONE, PlaceMatcher.compare(at("Palais des Papes", null, 0), otherCity));
        Candidate zone = new Candidate("Centre ville", null, "84000", 43.9508, 4.8075, APPROXIMATE);
        assertEquals(Level.NONE, PlaceMatcher.compare(zone, at("Centre ville", null, 0)));
        assertEquals(Level.NONE, PlaceMatcher.compare(at("Église", null, 0), at("Musée", null, 5)));
    }

    @Test
    void stopWordsAlone_ShouldNotMakeNamesClose() {
        assertEquals(Level.NONE, PlaceMatcher.compare(
                at("Chapelle Saint-Michel", null, 0), at("Église Saint-Pierre", null, 50)));
    }

    @Test
    void unnamedEventLocation_ShouldMatchAPlaceNamedInItsAddress() {
        Candidate cinema = at("Cinéma Le Luberon", "31 rue Giraud", 0);
        assertEquals(Level.CERTAIN, PlaceMatcher.compare(at("", "31 rue Giraud Cinéma Le Luberon", 7), cinema));
        assertEquals(Level.PROBABLE, PlaceMatcher.compare(at(null, "6 rue de Croze Cinéma Le Luberon", 90), cinema));
        assertEquals(Level.NONE, PlaceMatcher.compare(at(null, "Espace de Croze Amphithéâtre", 7), cinema));
        assertEquals(Level.NONE, PlaceMatcher.compare(at(null, "2 rue Vox", 5), at("Vox", null, 0)),
                "un seul mot significatif ne suffit pas");
        assertEquals(Level.NONE,
                PlaceMatcher.compare(at("Salle des fêtes", "31 rue Giraud Cinéma Le Luberon", 7), cinema),
                "un lieu d'événement nommé garde la règle des noms");
        assertEquals(Level.NONE, PlaceMatcher.compare(at(null, "Eglise des Célestins Place des Corps Saints", 19),
                at("Place des Corps Saints", null, 0)), "une place est citée par les adresses sans être le lieu");
        assertEquals(Level.NONE, PlaceMatcher.compare(at(null, "21 cours Mirabeau Espace culturel", 10),
                at("Le cours Mirabeau", null, 0)), "article initial ignoré");
    }
}
