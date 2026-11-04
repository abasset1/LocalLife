package com.locallife.backend.collector.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Titres réels des exports France du 29/10/2026 et du 04/11/2026, sauf mention contraire. */
class DataTourismeEventSelectionTest {

    private static final DataTourismeEventSelection SELECTION = DataTourismeEventSelection.VIDE_GRENIERS_BROCANTES;

    private static final List<String> GENERIC = List.of("EntertainmentAndEvent", "SaleEvent");

    private static final List<String> GARAGE_SALE = List.of("EntertainmentAndEvent", "GarageSale");

    private static boolean matches(String title, List<String> types) {
        return SELECTION.matches(title, null, types);
    }

    @Test
    void matches_ShouldKeepGarageSalesAndBricABrac_ByType() {
        for (String title : List.of("Belle journée du Ménil", "Vide ta chambre", "Gratiferia", "Bourse petite enfance",
                "Salon des Antiquaires", "Farfouille Vêtements Enfants")) {
            assertTrue(matches(title, GARAGE_SALE), title);
        }
        assertTrue(matches("Belle journée du Ménil", List.of("BricABrac")));
    }

    @Test
    void matches_ShouldKeepGenericEvents_WhoseTitleIsExplicit() {
        // Typés seulement SaleEvent, SocialEvent ou Event.
        for (String title : List.of("Vide-greniers", "VIDE GRENIER DU COMITÉ DES FÊTES", "Grande brocante du centre ville",
                "Marché aux puces", "Bourse aux jouets - 1ère édition", "Vide dressing féminin",
                "Braderie de printemps", "Belle Brocante de Noël", "Troc et puces")) {
            assertTrue(matches(title, GENERIC), title);
        }
    }

    /** Faux négatifs de l'export du 04/11/2026 : typés sans GarageSale ni BricABrac. */
    @Test
    void matches_ShouldKeepOtherNamesOfGarageSales_ByTitle() {
        for (String title : List.of("Foire à tout", "[Foire à tout] En intérieur", "Bric à brac de Noël", "Vide-jardin",
                "VIDE TA CHAMBRE POUR NOEL", "VIDE-TA-CHAMBRE", "Bourse Puériculture & Vêtements", "Bourse puériculture")) {
            assertTrue(matches(title, GENERIC), title);
        }
    }

    @Test
    void matches_ShouldIgnoreAccentsAndTypographicApostrophes() {
        assertTrue(matches("Vide‑grenier de l’été", GENERIC));
        assertTrue(matches("BROCANTE À L’ANCIENNE", GENERIC));
    }

    @Test
    void matches_ShouldRejectOtherEvents() {
        for (String title : List.of("Concert de jazz", "Marché de producteurs", "Visite guidée du grenier à sel",
                "Exposition", "Bourse d'études", "Troc de plantes")) {
            assertFalse(matches(title, List.of("EntertainmentAndEvent", "Concert", "Market")), title);
        }
        assertFalse(matches(null, null));
    }

    /** Faux positifs du type : événements d'un autre genre typés GarageSale ou BricABrac par leur producteur. */
    @Test
    void matches_ShouldRejectOtherKindsOfEvents_TypedAsGarageSales() {
        for (String title : List.of("Concert de Naudau à Saint-Loubouer", "Fête de la châtaigne", "Fête de la pomme",
                "Fêtes locales de Gousse", "Braud en fête : Fête locale de Braud-et-Saint-Louis", "Kermesse",
                "Marché de Noël", "Marché de l'avent", "Marché artisanal", "Village de Noël", "Repair Café",
                "Atelier création jeu vidéo narratif", "Collecte de textile", "Téléthon",
                "Expo-vente des Petites Mains Fourasines", "Foire Sainte Catherine")) {
            assertFalse(matches(title, GARAGE_SALE), title);
        }
    }

    /** Un titre d'un autre genre qui nomme aussi une vente d'occasion reste dans la sélection. */
    @Test
    void matches_ShouldKeepOtherKindsOfEvents_ThatNameASecondHandSale() {
        for (String title : List.of("Vide jouets - Pirates de Cour - Téléthon 2026", "Fête de la Récup' 2026",
                "Gratiferia enfants + ateliers + spectacle Cheap Tchip !", "Troc en fête !", "28ème Vide-Atelier du Grand Bassin",
                "Marché de Noël des émaux et faïences de Longwy et antiquités", "Anniversaire : les 10 ans de la Brocboutik")) {
            assertTrue(matches(title, GARAGE_SALE), title);
        }
    }

    /** Un titre d'un autre genre reste dans la sélection si la description annonce une vente d'occasion. */
    @Test
    void matches_ShouldKeepOtherKindsOfEvents_WhoseDescriptionAnnouncesASale() {
        assertTrue(SELECTION.matches("Marché de Noël",
                "Sur place retrouvez artisanat local, créateurs, brocante, producteurs, manèges.", GARAGE_SALE));
        assertTrue(SELECTION.matches("Téléthon", "Au programme : pot-au-feu, randonnée, vide-grenier.", GARAGE_SALE));
        assertTrue(SELECTION.matches("Marché artisanal et salon du livre",
                "Venez chiner au marché du livre de Vailhourles !", GARAGE_SALE));
        assertFalse(SELECTION.matches("Marché de Noël", "Artisanat local et producteurs.", GARAGE_SALE));
        // La description ne repêche pas un événement hors du type de la sélection.
        assertFalse(SELECTION.matches("Fête du village", "Tombola, vide grenier, pétanque.", GENERIC));
    }

    /** Faux positifs du titre : « puces » ou « bourse aux » dans le titre d'un spectacle ou d'une réunion. */
    @Test
    void matches_ShouldRejectShowsAndMeetings_WithAnAmbiguousKeyword() {
        for (String title : List.of("Spectacle musical \"Aux puces superphoniques\"",
                "Spectacle famille musical \"Aux puces superphoniques\"",
                "Dîner Concert avec la troupe de théâtre \"Le Sac à Puces\"",
                "Réunion de préparation de la bourse aux jouets")) {
            assertFalse(matches(title, GENERIC), title);
            assertFalse(matches(title, GARAGE_SALE), title);
        }
        // Mot-clé sans ambiguïté, ou bourse sans spectacle : gardés.
        assertTrue(matches("Vide-grenier & concert", GENERIC));
        assertTrue(matches("Marché de Noël et bourse aux jouets", GENERIC));
        assertTrue(matches("Bourse aux skis & atelier environnement enfants", GENERIC));
        assertTrue(matches("Puces Couturières et Loisirs Créatifs", GENERIC));
    }

    @Test
    void normalize_ShouldLowerCaseAndDropAccentsAndDashes() {
        assertEquals("vide grenier de l'ete", DataTourismeEventSelection.normalize("Vide‑Grenier de l’Été"));
        assertEquals("", DataTourismeEventSelection.normalize(null));
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
