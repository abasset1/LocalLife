package com.locallife.backend.collector.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.locallife.backend.collector.application.CrossSourceDuplicateMatcher.Candidate;
import com.locallife.backend.collector.application.CrossSourceDuplicateMatcher.Level;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Règle de doublons entre sources (ADR-0011, § 2.9). Les cas de faux positifs viennent de la mesure sur
 * DATAtourisme PACA du 13/10/2026 : mêmes titres, même jour, lieux distincts. Positions : centres des
 * communes concernées (les distances mesurées étaient de 18 à 50 km).
 */
class CrossSourceDuplicateMatcherTest {

    private static final LocalDate MUSIC_DAY = LocalDate.of(2026, 6, 21);
    private static final LocalDate SHOW_DAY = LocalDate.of(2026, 11, 6);

    private static Candidate precise(String title, LocalDate day, double latitude, double longitude, String city) {
        return new Candidate(title, Set.of(day), latitude, longitude, city, false);
    }

    @Test
    void sameTitleSameDay_InDifferentTowns_IsNeverADuplicate() {
        Candidate portDeBouc = precise("Fête de la musique", MUSIC_DAY, 43.4050, 4.9880, "Port-de-Bouc");
        Candidate marignane = precise("Fête de la musique", MUSIC_DAY, 43.4160, 5.2150, "Marignane");
        Candidate lambesc = precise("Octobre Rose", LocalDate.of(2026, 10, 11), 43.6540, 5.2620, "Lambesc");
        Candidate barbentane = precise("Octobre Rose", LocalDate.of(2026, 10, 11), 43.9000, 4.7480, "Barbentane");

        assertTrue(CrossSourceDuplicateMatcher.distanceMeters(portDeBouc, marignane) > 18_000);
        assertEquals(Level.NONE, CrossSourceDuplicateMatcher.compare(portDeBouc, marignane));
        assertEquals(Level.NONE, CrossSourceDuplicateMatcher.compare(lambesc, barbentane));
    }

    @Test
    void sameTitle_TwoKilometresApart_IsNotADuplicate() {
        // « Jouer la mouette », Marseille 3e et 2e arrondissements : 1,9 km.
        Candidate third = precise("Jouer la mouette", SHOW_DAY, 43.3110, 5.3800, "Marseille");
        Candidate second = precise("Jouer la mouette", SHOW_DAY, 43.3000, 5.3600, "Marseille");

        assertEquals(Level.NONE, CrossSourceDuplicateMatcher.compare(third, second));
    }

    @Test
    void sameTitleSamePlaceSameDay_IsProbable() {
        Candidate openAgenda = precise("Cendrillon", SHOW_DAY, 43.3560, 5.3600, "Marseille");
        Candidate dataTourisme = precise("CENDRILLON !", SHOW_DAY, 43.3561, 5.3601, "Marseille");

        assertEquals(Level.PROBABLE, CrossSourceDuplicateMatcher.compare(openAgenda, dataTourisme));
    }

    @Test
    void genericTitle_SamePlaceSameDay_IsOnlyPossible() {
        // Deux séances de « Cinéma » à 2 m : peut-être deux films, jamais masqué automatiquement.
        Candidate first = precise("Cinéma", SHOW_DAY, 43.4520, 4.4280, "Saintes-Maries-de-la-Mer");
        Candidate second = precise("Cinéma", SHOW_DAY, 43.45202, 4.42801, "Saintes-Maries-de-la-Mer");

        assertEquals(Level.POSSIBLE, CrossSourceDuplicateMatcher.compare(first, second));
    }

    @Test
    void closeTitle_SamePlaceSameDay_IsOnlyPossible() {
        Candidate openAgenda = precise("Les Hivernales festival de danse", SHOW_DAY, 43.9496, 4.8143, "Avignon");
        Candidate dataTourisme = precise(
                "Les Hivernales, festival de danse - 49e édition", SHOW_DAY, 43.9496, 4.8143, "Avignon");

        assertEquals(Level.POSSIBLE, CrossSourceDuplicateMatcher.compare(openAgenda, dataTourisme));
    }

    @Test
    void approximatePosition_SameTown_IsOnlyPossible_OtherTown_None() {
        Candidate precise = precise("Parcours de l'Art", SHOW_DAY, 43.9460, 4.8060, "Avignon");
        Candidate approximate = new Candidate(
                "Parcours de l'Art", Set.of(SHOW_DAY), 43.9440, 4.8050, "Avignon", true);
        Candidate approximateElsewhere = new Candidate(
                "Parcours de l'Art", Set.of(SHOW_DAY), 43.9440, 4.8050, "Villeneuve-lès-Avignon", true);

        assertEquals(Level.POSSIBLE, CrossSourceDuplicateMatcher.compare(precise, approximate));
        assertEquals(Level.NONE, CrossSourceDuplicateMatcher.compare(precise, approximateElsewhere));
    }

    @Test
    void noCommonDay_IsNeverADuplicate() {
        Candidate monday = precise("Cendrillon", SHOW_DAY, 43.3560, 5.3600, "Marseille");
        Candidate tuesday = precise("Cendrillon", SHOW_DAY.plusDays(1), 43.3560, 5.3600, "Marseille");

        assertEquals(Level.NONE, CrossSourceDuplicateMatcher.compare(monday, tuesday));
    }

    @Test
    void differentTitles_SamePlaceSameDay_AreNotDuplicates() {
        Candidate first = precise("Cendrillon", SHOW_DAY, 43.3560, 5.3600, "Marseille");
        Candidate second = precise("Artistes en scène", SHOW_DAY, 43.3560, 5.3600, "Marseille");

        assertEquals(Level.NONE, CrossSourceDuplicateMatcher.compare(first, second));
    }

    @Test
    void missingPosition_IsNeverProbable() {
        Candidate withPosition = precise("Cendrillon", SHOW_DAY, 43.3560, 5.3600, "Marseille");
        Candidate withoutPosition = new Candidate("Cendrillon", Set.of(SHOW_DAY), null, null, "Marseille", false);

        assertEquals(Level.NONE, CrossSourceDuplicateMatcher.compare(withPosition, withoutPosition));
    }
}
