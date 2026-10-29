package com.locallife.backend.collector.infrastructure;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Sélection d'événements d'une source DATAtourisme ({@code source.event_selection}, V77) : quels événements du
 * fichier garder, en plus des règles communes (départements, dates, coordonnées). Sans sélection, tous les
 * événements sont gardés.
 *
 * <p>Mesure à l'origine de {@link #VIDE_GRENIERS_BROCANTES} (export France du 29/10/2026, 72 581 événements) :
 * 1 052 vide-greniers, brocantes, puces, braderies et bourses à venir dans 68 départements, contre 138 publiés sur
 * la bêta (32 départements). Le type ne suffit pas : 20 % de ces événements n'ont qu'un type générique
 * ({@code SaleEvent}, {@code SocialEvent}, {@code Event}…) et ne se reconnaissent qu'à leur titre (« Bourse aux
 * jouets », « Marché aux puces »).
 */
public enum DataTourismeEventSelection {

    /** Vide-greniers et brocantes : types {@code GarageSale} ou {@code BricABrac}, ou titre explicite. */
    VIDE_GRENIERS_BROCANTES(
            Set.of("GarageSale", "BricABrac"),
            Pattern.compile("\\b(vide ?greniers?|brocantes?|braderies?|puces|vide ?dressings?|"
                    + "bourses? aux|troc et puces)\\b"));

    private final Set<String> types;

    /** Comparé au titre en minuscules, sans accents, tirets remplacés par des espaces, apostrophes normalisées. */
    private final Pattern title;

    DataTourismeEventSelection(Set<String> types, Pattern title) {
        this.types = types;
        this.title = title;
    }

    /** Vrai si l'événement fait partie de la sélection, par l'un de ses types ou par son titre. */
    public boolean matches(String eventTitle, List<String> eventTypes) {
        if (eventTypes != null && eventTypes.stream().anyMatch(types::contains)) {
            return true;
        }
        return eventTitle != null && title.matcher(normalize(eventTitle)).find();
    }

    /**
     * Sélection enregistrée pour une source ({@code null} ou vide : aucune, tous les événements sont gardés).
     *
     * @throws IllegalArgumentException si la valeur n'est pas une sélection connue
     */
    public static DataTourismeEventSelection fromStoredValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return valueOf(value.trim());
    }

    private static String normalize(String value) {
        String withoutAccents = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return withoutAccents.replace('’', '\'').replaceAll("\\p{Pd}", " ").toLowerCase(Locale.ROOT);
    }
}
