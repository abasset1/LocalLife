package com.locallife.backend.common.i18n;

import java.util.Collection;
import java.util.function.Function;

/**
 * Résolution de traduction partagée par les 4 référentiels du sprint
 * CATEG ({@code Category}, {@code ActivityType}, {@code Audience},
 * {@code OrganizerType}) — CAT-005 (SPRINT_CATEG), « Harmoniser IDs,
 * codes, labels et traductions ».
 *
 * Avant CAT-005, chacun des 4 services (CAT-001 à CAT-004) dupliquait une
 * résolution à 2 maillons (locale demandée → {@code fr-FR}), sans
 * implémenter le troisième maillon de la chaîne de repli explicitement
 * décrite par le sprint : « langue de base correspondante ». Exemple du
 * sprint : locale demandée {@code es-ES}, absente → {@code es} (langue de
 * base) absent → {@code fr-FR} disponible → {@code "CULTURE" → "Culture"}.
 *
 * Cette classe centralise les 4 maillons (le 4ᵉ, repli sur le {@code CODE}
 * lui-même, reste à la charge de chaque service appelant, qui seul connaît
 * le code de l'entité) pour que les 4 référentiels aient un comportement
 * identique et déterministe, comme l'exige le ticket.
 */
public final class LocaleResolver {

    /** V1 : seule locale garantie présente pour toute entité (SPRINT_CATEG). */
    public static final String DEFAULT_LOCALE = "fr-FR";

    private LocaleResolver() {
    }

    /**
     * Résout la meilleure traduction disponible pour {@code requestedLocale}
     * parmi {@code translations}, selon la chaîne de repli du sprint :
     * <ol>
     *   <li>locale demandée (correspondance exacte, insensible à la casse) ;</li>
     *   <li>langue de base correspondante (ex. {@code es-ES} → {@code es},
     *       comparée à la langue de base de chaque traduction disponible) ;</li>
     *   <li>{@code fr-FR} ;</li>
     * </ol>
     * Retourne {@code null} si aucun des trois maillons ne trouve de
     * traduction — le 4ᵉ maillon (repli sur le {@code CODE}) revient alors
     * à l'appelant.
     *
     * @param translations  les traductions disponibles pour une entité
     * @param requestedLocale la locale demandée (peut être {@code null})
     * @param localeOf      extrait la locale d'une traduction (ex. {@code CategoryTranslation::locale})
     */
    public static <T> T resolve(Collection<T> translations, String requestedLocale, Function<T, String> localeOf) {
        if (translations == null || translations.isEmpty()) {
            return null;
        }

        T exact = findByLocale(translations, localeOf, requestedLocale);
        if (exact != null) {
            return exact;
        }

        String baseLanguage = baseLanguage(requestedLocale);
        if (baseLanguage != null) {
            T baseMatch = findByBaseLanguage(translations, localeOf, baseLanguage);
            if (baseMatch != null) {
                return baseMatch;
            }
        }

        return findByLocale(translations, localeOf, DEFAULT_LOCALE);
    }

    private static <T> T findByLocale(Collection<T> translations, Function<T, String> localeOf, String locale) {
        if (locale == null) {
            return null;
        }
        return translations.stream()
                .filter(t -> locale.equalsIgnoreCase(localeOf.apply(t)))
                .findFirst()
                .orElse(null);
    }

    private static <T> T findByBaseLanguage(
            Collection<T> translations, Function<T, String> localeOf, String baseLanguage) {
        return translations.stream()
                .filter(t -> baseLanguage.equalsIgnoreCase(baseLanguage(localeOf.apply(t))))
                .findFirst()
                .orElse(null);
    }

    /** {@code "es-ES"} → {@code "es"} ; {@code "fr-FR"} → {@code "fr"} ; {@code null} si vide. */
    private static String baseLanguage(String locale) {
        if (locale == null || locale.isBlank()) {
            return null;
        }
        int separator = locale.indexOf('-');
        return separator > 0 ? locale.substring(0, separator) : locale;
    }

}
