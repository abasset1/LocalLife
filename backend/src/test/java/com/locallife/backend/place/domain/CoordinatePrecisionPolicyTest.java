package com.locallife.backend.place.domain;

import static com.locallife.backend.place.domain.CoordinatePrecision.APPROXIMATE;
import static com.locallife.backend.place.domain.CoordinatePrecision.EXACT;
import static com.locallife.backend.place.domain.CoordinatePrecision.UNKNOWN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Règles de la précision géographique (ADR-0011, § 7.1, phase A). */
class CoordinatePrecisionPolicyTest {

    @Test
    void isGenericPlaceText_ShouldRecognizeZonesAndMultiplePlaces() {
        assertTrue(CoordinatePrecisionPolicy.isGenericPlaceText("Divers lieux à Aix-en-Provence", "Aix-en-Provence"));
        assertTrue(CoordinatePrecisionPolicy.isGenericPlaceText("Plusieurs lieux", null));
        assertTrue(CoordinatePrecisionPolicy.isGenericPlaceText("CENTRE-VILLE", "Pertuis"));
        assertTrue(CoordinatePrecisionPolicy.isGenericPlaceText("En centre-ville", null));
        assertTrue(CoordinatePrecisionPolicy.isGenericPlaceText("Le Village", "Gordes"));
        assertTrue(CoordinatePrecisionPolicy.isGenericPlaceText("Centre ville Saint-Rémy-de-Provence",
                "Saint-Rémy-de-Provence"));
    }

    @Test
    void isGenericPlaceText_ShouldKeepPrecisePlaces() {
        assertFalse(CoordinatePrecisionPolicy.isGenericPlaceText("Place du village", "Bonnieux"));
        assertFalse(CoordinatePrecisionPolicy.isGenericPlaceText("Centre ville Cours Hyacinthe Bellon", "Ollioules"));
        assertFalse(CoordinatePrecisionPolicy.isGenericPlaceText("Lieux divers", null));
        assertFalse(CoordinatePrecisionPolicy.isGenericPlaceText("Église Saint-Pierre", "Avignon"));
        assertFalse(CoordinatePrecisionPolicy.isGenericPlaceText(null, "Avignon"));
        assertFalse(CoordinatePrecisionPolicy.isGenericPlaceText("", null));
    }

    @Test
    void inferFromText_ShouldReadAddressFirst_ThenName() {
        // Lieu DATAtourisme à adresse générique : texte rangé dans le nom, adresse vide.
        assertEquals(APPROXIMATE, CoordinatePrecisionPolicy.inferFromText("Centre ville", null, "Apt"));
        assertEquals(APPROXIMATE, CoordinatePrecisionPolicy.inferFromText("Centre ville", "  ", "Apt"));
        // Adresse générique fournie telle quelle (autre source).
        assertEquals(APPROXIMATE, CoordinatePrecisionPolicy.inferFromText("Fête du livre", "Divers lieux", "Apt"));
        // Une adresse précise l'emporte sur un nom générique : le point est géocodé à l'adresse.
        assertEquals(UNKNOWN, CoordinatePrecisionPolicy.inferFromText("Divers lieux", "Place de l'Horloge", "Avignon"));
    }

    @Test
    void inferFromText_ShouldNeverInferExact() {
        String[][] samples = {
            {"Théâtre des Halles", "Rue du Roi René", "Avignon"},
            {null, null, null},
            {"Divers lieux", null, null},
            {"", "", ""},
        };
        for (String[] sample : samples) {
            assertTrue(CoordinatePrecisionPolicy.inferFromText(sample[0], sample[1], sample[2]) != EXACT);
        }
    }

    @Test
    void mayReuse_ShouldKeepCurrentRule_BetweenNonApproximatePlaces() {
        assertTrue(CoordinatePrecisionPolicy.mayReuse(UNKNOWN, "A", "Apt", UNKNOWN, "B", "Gordes"));
        assertTrue(CoordinatePrecisionPolicy.mayReuse(UNKNOWN, "A", "Apt", EXACT, "B", "Apt"));
    }

    @Test
    void mayReuse_ShouldNeverMixApproximateAndOtherPlaces() {
        assertFalse(CoordinatePrecisionPolicy.mayReuse(APPROXIMATE, "Centre ville", "Apt", UNKNOWN, "Mairie", "Apt"));
        assertFalse(CoordinatePrecisionPolicy.mayReuse(UNKNOWN, "Mairie", "Apt", APPROXIMATE, "Centre ville", "Apt"));
        assertFalse(CoordinatePrecisionPolicy.mayReuse(APPROXIMATE, "Centre ville", "Apt", EXACT, "Centre ville", "Apt"));
    }

    @Test
    void mayReuse_ShouldShareApproximatePlace_OnlyWithSameNameAndCity() {
        // Réimport d'un même événement : même lieu approximatif, pas un nouveau lieu à chaque import.
        assertTrue(CoordinatePrecisionPolicy.mayReuse(
                APPROXIMATE, "Centre-ville", "L'Isle-sur-la-Sorgue", APPROXIMATE, "centre ville", "L'Isle sur la Sorgue"));
        // Deux zones différentes d'une même commune, géocodées au même centre : lieux distincts.
        assertFalse(CoordinatePrecisionPolicy.mayReuse(
                APPROXIMATE, "Divers lieux", "Apt", APPROXIMATE, "Centre ville", "Apt"));
        assertFalse(CoordinatePrecisionPolicy.mayReuse(
                APPROXIMATE, "Centre ville", "Apt", APPROXIMATE, "Centre ville", "Gordes"));
    }

    @Test
    void onReuse_ShouldNeverChangeExistingPrecision() {
        assertEquals(EXACT, CoordinatePrecisionPolicy.onReuse(EXACT));
        assertEquals(APPROXIMATE, CoordinatePrecisionPolicy.onReuse(APPROXIMATE));
        assertEquals(UNKNOWN, CoordinatePrecisionPolicy.onReuse(UNKNOWN));
        assertEquals(UNKNOWN, CoordinatePrecisionPolicy.onReuse(null));
    }

    @Test
    void isShownAsApproximate_ShouldOnlyChangeDisplayOfApproximate() {
        assertTrue(CoordinatePrecisionPolicy.isShownAsApproximate(APPROXIMATE));
        assertFalse(CoordinatePrecisionPolicy.isShownAsApproximate(UNKNOWN));
        assertFalse(CoordinatePrecisionPolicy.isShownAsApproximate(EXACT));
        assertFalse(CoordinatePrecisionPolicy.isShownAsApproximate(null));
    }
}
