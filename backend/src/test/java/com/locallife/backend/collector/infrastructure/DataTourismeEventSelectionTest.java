package com.locallife.backend.collector.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class DataTourismeEventSelectionTest {

    private static final DataTourismeEventSelection SELECTION = DataTourismeEventSelection.VIDE_GRENIERS_BROCANTES;

    private static final List<String> GENERIC = List.of("EntertainmentAndEvent", "SaleEvent");

    @Test
    void matches_ShouldKeepGarageSalesAndBricABrac_ByTypeWhateverTheTitle() {
        assertTrue(SELECTION.matches("Collecte de textile", List.of("EntertainmentAndEvent", "GarageSale")));
        assertTrue(SELECTION.matches("Belle journée du Ménil", List.of("BricABrac")));
    }

    @Test
    void matches_ShouldKeepGenericEvents_WhoseTitleIsExplicit() {
        // Titres réels de l'export du 29/10/2026, typés seulement SaleEvent, SocialEvent ou Event.
        for (String title : List.of("Vide-greniers", "VIDE GRENIER DU COMITÉ DES FÊTES", "Grande brocante du centre ville",
                "Marché aux puces", "Bourse aux jouets - 1ère édition", "Vide dressing féminin",
                "Braderie de printemps", "Belle Brocante de Noël", "Troc et puces")) {
            assertTrue(SELECTION.matches(title, GENERIC), title);
        }
    }

    @Test
    void matches_ShouldIgnoreAccentsAndTypographicApostrophes() {
        assertTrue(SELECTION.matches("Vide‑grenier de l’été", GENERIC));
        assertTrue(SELECTION.matches("BROCANTE À L’ANCIENNE", GENERIC));
    }

    @Test
    void matches_ShouldRejectOtherEvents() {
        for (String title : List.of("Concert de jazz", "Marché de producteurs", "Visite guidée du grenier à sel",
                "Exposition", "Bourse d'études")) {
            assertFalse(SELECTION.matches(title, List.of("EntertainmentAndEvent", "Concert", "Market")), title);
        }
        assertFalse(SELECTION.matches(null, null));
    }

    @Test
    void fromStoredValue_ShouldReadKnownSelections_AndTreatEmptyAsAll() {
        assertEquals(SELECTION, DataTourismeEventSelection.fromStoredValue("VIDE_GRENIERS_BROCANTES"));
        assertEquals(SELECTION, DataTourismeEventSelection.fromStoredValue(" VIDE_GRENIERS_BROCANTES "));
        assertNull(DataTourismeEventSelection.fromStoredValue(null));
        assertNull(DataTourismeEventSelection.fromStoredValue(" "));
        assertThrows(IllegalArgumentException.class, () -> DataTourismeEventSelection.fromStoredValue("INCONNUE"));
    }
}
