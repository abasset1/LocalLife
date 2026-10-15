package com.locallife.backend.place.domain;

import static com.locallife.backend.place.domain.CoordinatePrecision.APPROXIMATE;
import static com.locallife.backend.place.domain.CoordinatePrecision.UNKNOWN;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.locallife.backend.place.domain.PlaceMatcher.Candidate;
import com.locallife.backend.place.domain.PlaceMatcher.Level;
import org.junit.jupiter.api.Test;

/** Cas tirés de la mesure du 14/10/2026 (événements DATAtourisme du Vaucluse et des Bouches-du-Rhône). */
class EventLocationDuplicateMatcherTest {

    /** Environ 1,11 m par 0,00001 degré de latitude. */
    private static Candidate at(String name, String address, double metersNorth) {
        return new Candidate(name, address, "84000", 43.9508 + metersNorth / 111_000, 4.8075, UNKNOWN);
    }

    @Test
    void sameWordsNearby_ShouldBeCertain_WhateverTheCaseOrOrder() {
        assertEquals(Level.CERTAIN, EventLocationDuplicateMatcher.compare(
                at(null, "8 Place Aristide Briand Château de Simiane", 0),
                at(null, "8 place Aristide Briand Château de Simiane", 1)));
        assertEquals(Level.CERTAIN, EventLocationDuplicateMatcher.compare(
                at(null, "Place du Palais Départ : Palais des Papes - en bas de l’escalier", 0),
                at(null, "place du Palais Palais des Papes - départ en bas de l'escalier", 11)));
    }

    @Test
    void sameWordsFurther_OrOneTextInsideTheOther_ShouldBeProbable() {
        assertEquals(Level.PROBABLE, EventLocationDuplicateMatcher.compare(
                at(null, "8 place Aristide Briand Château de Simiane", 0),
                at(null, "8 place Aristide Briand Château de Simiane", 60)));
        assertEquals(Level.PROBABLE, EventLocationDuplicateMatcher.compare(
                at(null, "Palais des Papes place du Palais", 0),
                at(null, "Place du Palais Départ : Palais des Papes - en bas de l’escalier", 20)));
        assertEquals(Level.PROBABLE, EventLocationDuplicateMatcher.compare(
                at(null, "31 rue Giraud", 0), at(null, "31 rue Giraud Cinéma Le Luberon", 5)));
    }

    @Test
    void nameAndAddressAreComparedTogether() {
        assertEquals(Level.CERTAIN, EventLocationDuplicateMatcher.compare(
                at("Cinéma Le Luberon", "31 rue Giraud", 0), at(null, "31 rue Giraud Cinéma Le Luberon", 5)));
    }

    @Test
    void differentPlaces_ShouldNotBeDuplicates() {
        assertEquals(Level.NONE, EventLocationDuplicateMatcher.compare(
                at(null, "31 rue Giraud Cinéma Le Luberon", 0), at(null, "Espace de Croze Amphithéâtre", 7)));
        assertEquals(Level.NONE, EventLocationDuplicateMatcher.compare(
                at(null, "3 rue Carnot", 0), at(null, "5 rue Carnot", 10)), "autre numéro de rue");
    }

    @Test
    void shouldNeverMatch_FarAwayOtherPostalCodeApproximateOrTooShort() {
        Candidate cinema = at(null, "31 rue Giraud Cinéma Le Luberon", 0);
        assertEquals(Level.NONE, EventLocationDuplicateMatcher.compare(cinema,
                at(null, "31 rue Giraud Cinéma Le Luberon", 150)));
        assertEquals(Level.NONE, EventLocationDuplicateMatcher.compare(cinema,
                new Candidate(null, "31 rue Giraud Cinéma Le Luberon", "84120", 43.9508, 4.8075, UNKNOWN)));
        assertEquals(Level.NONE, EventLocationDuplicateMatcher.compare(cinema,
                new Candidate(null, "31 rue Giraud Cinéma Le Luberon", "84000", 43.9508, 4.8075, APPROXIMATE)));
        assertEquals(Level.NONE, EventLocationDuplicateMatcher.compare(
                at("Mairie", null, 0), at("Mairie", null, 2)), "un seul mot significatif ne suffit pas");
    }
}
