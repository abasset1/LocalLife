package com.locallife.backend.collector.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.DayOfWeek;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Titres et descriptions réels de l'export DATAtourisme PACA (Vaucluse, 11/11/2026). */
class MarketDaysTest {

    private static final List<String> MARKET = List.of("EntertainmentAndEvent", "SaleEvent", "Market");
    private static final List<String> EXHIBITION = List.of("EntertainmentAndEvent", "Exhibition");

    @Test
    void find_ShouldReadTitle_ForAnyType() {
        assertEquals(Set.of(DayOfWeek.WEDNESDAY), MarketDays.find("Mercredi : Marché hebdomadaire de Valréas", "", EXHIBITION));
        assertEquals(Set.of(DayOfWeek.FRIDAY), MarketDays.find("Petit Marché du vendredi matin à Sablet", "", MARKET));
        assertEquals(Set.of(DayOfWeek.SUNDAY), MarketDays.find("Brocante du dimanche", "", MARKET));
    }

    @Test
    void find_ShouldPreferTitle_OverDescription() {
        // Description : « En plus du marché du jeudi, vous pourrez profiter d'un marché tous les dimanches matin. »
        assertEquals(Set.of(DayOfWeek.SUNDAY), MarketDays.find("Le petit marché du dimanche",
                "En plus du marché du jeudi, vous pourrez profiter d’un marché tous les dimanches matin.", MARKET));
        // Description recopiée d'un autre marché (« les mardis ») : le titre dit dimanche.
        assertEquals(Set.of(DayOfWeek.SUNDAY), MarketDays.find("Marché dominical",
                "Le marché hebdomadaire de Lapalud a lieu les mardis de 8h30 à 12h30.", MARKET));
    }

    @Test
    void find_ShouldReadDescription_OnlyForMarketsAndSales() {
        assertEquals(EnumSet.of(DayOfWeek.TUESDAY, DayOfWeek.SATURDAY), MarketDays.find(
                "Petit marché des producteurs locaux",
                "Tous les mardis et samedis matin de 8h à 12 h, toute l'année.", MARKET));
        assertEquals(Set.of(), MarketDays.find("Exposition", "Ouvert tous les jours, fermé le lundi.", EXHIBITION));
        assertEquals(Set.of(), MarketDays.find("Marché de Gordes", "Il se tient au cœur du village.", MARKET));
    }

    @Test
    void days_ShouldIgnoreClosedDays_AndMardiGras() {
        assertEquals(Set.of(DayOfWeek.SATURDAY), MarketDays.days("Tous les samedis, sauf le dimanche"));
        assertEquals(Set.of(DayOfWeek.THURSDAY), MarketDays.days("Le jeudi matin. Fermé le lundi."));
        assertEquals(Set.of(), MarketDays.days("Carnaval de Mardi Gras"));
    }
}
