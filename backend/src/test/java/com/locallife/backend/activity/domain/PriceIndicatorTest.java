package com.locallife.backend.activity.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Textes de tarifs réels (API DATAtourisme PACA du 07/11/2026, OpenAgenda) et cas limites. */
class PriceIndicatorTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "Plein tarif : 5€ / Tarif adhérent : 3€.",
        "Plein tarif : 22 €.",
        "5 €.",
        "De 10 à 35 €.",
        "Adulte : 40 € (pour une personne).\n\n Gratuit pour les moins de 6 ans.",
        "Super réduit : 4 € /Enfant : 4 € /Solidaire : 3 € /Plein : 8 €",
        "Tarif unique 12,50 euros",
        "€ 8 sur place",
        "Entrée payante, billetterie sur place.",
        "• Adulte : entrée gratuite + 3\u00A0€ la visite commentée",
    })
    void of_ShouldBePaid_WithAPositiveAmountOrThePaidWord(String conditions) {
        assertEquals(PriceIndicator.PAID, PriceIndicator.of(conditions));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Gratuit.",
        "Accès libre.",
        "Entrée libre",
        "GRATUIT",
        "Gratuit, réservation conseillée.",
        "Libre participation.",
        "Gratuit. Tout public, ateliers adultes et enfants à partir de 10 ans.",
        "Accès libre, réservation conseillée au 04 91 14 59 55.",
        "Tarif unique : 0 €.\n\nConcert gratuit (dans la limite des places disponibles)",
    })
    void of_ShouldBeFree_WithoutAmountNorRestriction(String conditions) {
        assertEquals(PriceIndicator.FREE, PriceIndicator.of(conditions));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
        "Tarifs non communiqués.",
        "Gratuit pour les moins de 12 ans.",
        "Gratuit pour les enfants",
        "Gratuit pour les adhérents",
        "Sur réservation.",
        "   ",
    })
    void of_ShouldBeUnknown_WithoutPriceOrWithAPartialGratuity(String conditions) {
        assertEquals(PriceIndicator.UNKNOWN, PriceIndicator.of(conditions));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "Gratuit pour tous | FREE",
        "Visite gratuite. | FREE",
        "2 € la part, gratuit pour les enfants | PAID",
    })
    void of_ShouldHandleMixedWording(String conditions, PriceIndicator expected) {
        assertEquals(expected, PriceIndicator.of(conditions));
    }
}
