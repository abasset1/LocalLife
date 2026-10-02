package com.locallife.backend.common.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Couvre la chaîne de repli décrite par CAT-005 (SPRINT_CATEG), en
 * particulier le maillon « langue de base correspondante » que les
 * services CAT-001 à CAT-004 ne couvraient pas avant cette harmonisation
 * (ex. du sprint : {@code es-ES} absent → {@code es} absent → {@code fr-FR}).
 */
class LocaleResolverTest {

    private record Entry(String locale, String label) {
    }

    @Test
    void resolve_ShouldReturnExactMatch_WhenRequestedLocalePresent() {
        List<Entry> translations = List.of(
                new Entry("fr-FR", "Culture"),
                new Entry("en-US", "Culture"));

        Entry result = LocaleResolver.resolve(translations, "en-US", Entry::locale);

        assertEquals("en-US", result.locale());
    }

    @Test
    void resolve_ShouldMatchExactLocale_CaseInsensitively() {
        List<Entry> translations = List.of(new Entry("fr-FR", "Culture"));

        Entry result = LocaleResolver.resolve(translations, "FR-fr", Entry::locale);

        assertEquals("fr-FR", result.locale());
    }

    @Test
    void resolve_ShouldFallBackToBaseLanguage_WhenExactRegionMissing() {
        // Exemple du sprint : fr-BE absent, mais fr-FR présent -> même langue de base "fr"
        List<Entry> translations = List.of(new Entry("fr-FR", "Culture"));

        Entry result = LocaleResolver.resolve(translations, "fr-BE", Entry::locale);

        assertEquals("fr-FR", result.locale());
    }

    @Test
    void resolve_ShouldFallBackToDefaultLocale_WhenBaseLanguageAlsoMissing() {
        // Exemple du sprint : es-ES absent, es absent, fr-FR disponible
        List<Entry> translations = List.of(new Entry("fr-FR", "Culture"));

        Entry result = LocaleResolver.resolve(translations, "es-ES", Entry::locale);

        assertEquals("fr-FR", result.locale());
    }

    @Test
    void resolve_ShouldReturnNull_WhenNoTranslationMatchesAnyFallback() {
        List<Entry> translations = List.of(new Entry("de-DE", "Kultur"));

        Entry result = LocaleResolver.resolve(translations, "es-ES", Entry::locale);

        // Le dernier maillon (repli sur le CODE) revient à l'appelant, pas à LocaleResolver
        assertNull(result);
    }

    @Test
    void resolve_ShouldReturnNull_WhenNoTranslationsAtAll() {
        Entry result = LocaleResolver.resolve(List.of(), "fr-FR", Entry::locale);

        assertNull(result);
    }

    @Test
    void resolve_ShouldHandleNullRequestedLocale() {
        List<Entry> translations = List.of(new Entry("fr-FR", "Culture"));

        Entry result = LocaleResolver.resolve(translations, null, Entry::locale);

        // Pas de locale demandée, pas de langue de base -> repli direct sur fr-FR
        assertEquals("fr-FR", result.locale());
    }

}
