package com.locallife.backend.place.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Liste blanche et règles de modération des lieux permanents (ADR-0011, phase B). */
class PlaceRulesTest {

    private static Set<String> types(String... specific) {
        Set<String> all = new HashSet<>(Set.of("PointOfInterest", "PlaceOfInterest"));
        all.addAll(Set.of(specific));
        return all;
    }

    // ---- Liste blanche ----

    @Test
    void placeType_ShouldRetainSpecificTypes_WithTheirParents() {
        assertEquals(Optional.of("Museum"), PlaceTypeWhitelist.placeType(types("CulturalSite", "Museum")));
        assertEquals(Optional.of("Church"),
                PlaceTypeWhitelist.placeType(types("CulturalSite", "ReligiousSite", "Church")));
        assertEquals(Optional.of("SwimmingPool"),
                PlaceTypeWhitelist.placeType(types("SportsAndLeisurePlace", "SwimmingPool")));
    }

    @Test
    void placeType_ShouldRetainGeneralType_OnlyWithoutOtherType() {
        // « Théâtre antique d'Orange » : site culturel sans sous-type.
        assertEquals(Optional.of("CulturalSite"), PlaceTypeWhitelist.placeType(types("CulturalSite")));
        assertEquals(Optional.of("TechnicalHeritage"),
                PlaceTypeWhitelist.placeType(types("CulturalSite", "TechnicalHeritage")));
        // Un village (CityHeritage) porte aussi CulturalSite : jamais retenu implicitement.
        assertEquals(Optional.empty(), PlaceTypeWhitelist.placeType(types("CulturalSite", "CityHeritage")));
        // Galerie (groupe 2, exclu au départ) : porte des types non retenus.
        assertEquals(Optional.empty(), PlaceTypeWhitelist.placeType(
                types("SportsAndLeisurePlace", "Store", "ArtGalleryOrExhibitionGallery")));
    }

    @Test
    void placeType_ShouldNeverRetainExcludedTypes() {
        // Château-hôtel, domaine viticole avec jardin : exclus même avec un type de la liste.
        assertEquals(Optional.empty(),
                PlaceTypeWhitelist.placeType(types("CulturalSite", "Castle", "Accommodation", "Hotel")));
        assertEquals(Optional.empty(),
                PlaceTypeWhitelist.placeType(types("CulturalSite", "ParkAndGarden", "TastingProvider")));
        assertFalse(PlaceTypeWhitelist.isWhitelisted(types("FoodEstablishment", "Restaurant")));
    }

    @Test
    void placeType_ShouldChooseFirstSpecificTypeInListOrder() {
        assertEquals(Optional.of("Museum"),
                PlaceTypeWhitelist.placeType(types("CulturalSite", "Church", "Museum", "ReligiousSite")));
    }

    @Test
    void whitelist_ShouldHoldFortySixTypes() {
        assertEquals(46, PlaceTypeWhitelist.SPECIFIC_TYPES.size() + PlaceTypeWhitelist.GENERAL_TYPES.size());
    }

    // ---- Modération ----

    @Test
    void onReimport_ShouldKeepRejectedPlacesRejected() {
        assertEquals(PlaceStatus.REJECTED, PlaceModerationPolicy.onReimport(PlaceStatus.REJECTED, true, 0, false));
        assertEquals(PlaceStatus.REJECTED, PlaceModerationPolicy.onReimport(PlaceStatus.REJECTED, true, 5000, true));
    }

    @Test
    void onReimport_ShouldSendPublishedPlaceBackToModeration_OnImportantChange() {
        assertEquals(PlaceStatus.PUBLISHED, PlaceModerationPolicy.onReimport(PlaceStatus.PUBLISHED, true, 100, false));
        assertEquals(PlaceStatus.PENDING, PlaceModerationPolicy.onReimport(PlaceStatus.PUBLISHED, true, 100.5, false));
        assertEquals(PlaceStatus.PENDING, PlaceModerationPolicy.onReimport(PlaceStatus.PUBLISHED, true, 0, true));
        assertEquals(PlaceStatus.PENDING, PlaceModerationPolicy.onReimport(PlaceStatus.PUBLISHED, false, 0, false));
    }

    @Test
    void onReimport_ShouldHandlePendingAndArchivedPlaces() {
        assertEquals(PlaceStatus.PENDING, PlaceModerationPolicy.onReimport(PlaceStatus.PENDING, true, 900, true));
        assertEquals(PlaceStatus.ARCHIVED, PlaceModerationPolicy.onReimport(PlaceStatus.PENDING, false, 0, false));
        assertEquals(PlaceStatus.PENDING, PlaceModerationPolicy.onReimport(PlaceStatus.ARCHIVED, true, 0, false));
        assertEquals(PlaceStatus.ARCHIVED, PlaceModerationPolicy.onReimport(PlaceStatus.ARCHIVED, false, 0, false));
    }

    @Test
    void onDisappearance_ShouldArchiveOnlyPendingOrPublished() {
        assertEquals(PlaceStatus.ARCHIVED, PlaceModerationPolicy.onDisappearance(PlaceStatus.PENDING));
        assertEquals(PlaceStatus.ARCHIVED, PlaceModerationPolicy.onDisappearance(PlaceStatus.PUBLISHED));
        assertEquals(PlaceStatus.REJECTED, PlaceModerationPolicy.onDisappearance(PlaceStatus.REJECTED));
    }

    @Test
    void isSuspiciousDisappearance_ShouldApplyLimitsExactly() {
        assertTrue(PlaceModerationPolicy.isSuspiciousDisappearance(10, 19));
        assertFalse(PlaceModerationPolicy.isSuspiciousDisappearance(10, 20));
        assertFalse(PlaceModerationPolicy.isSuspiciousDisappearance(9, 9));
    }
}
