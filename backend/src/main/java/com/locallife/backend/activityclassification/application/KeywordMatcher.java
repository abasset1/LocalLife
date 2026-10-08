package com.locallife.backend.activityclassification.application;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Comparaison d'un mot-clé de référentiel avec un texte, sans altération sémantique
 * (docs/TICKET_CLASSIFICATION_KEYWORD_MATCHING.md, PR #38). Point unique de cette logique : ni les
 * collecteurs ni les services ne comparent eux-mêmes des mots-clés.
 *
 * <p>Principe : le texte et le mot-clé sont découpés en <b>mots</b> ; le mot-clé matche si ses mots
 * apparaissent <b>consécutivement et entiers</b> dans le texte (jamais un simple {@code contains}).
 *
 * <p>Autorisé (présentation uniquement) :
 * <ul>
 *   <li>casse : {@code Marché} = {@code MARCHÉ} = {@code marché} ;</li>
 *   <li>espaces de début/fin et espaces multiples ignorés ;</li>
 *   <li>tout caractère qui n'est ni lettre ni chiffre est un séparateur : tiret, apostrophe
 *       ({@code '}, {@code ’}, {@code ʼ}...), ponctuation. Donc {@code vide-grenier} =
 *       {@code vide grenier}, {@code d'art} = {@code d’art}, mais {@code d'art} ≠ {@code dart} ;</li>
 *   <li>forme Unicode composée (NFC) : {@code é} saisi en deux points de code = {@code é}.</li>
 * </ul>
 *
 * <p>Interdit, volontairement absent : suppression ou translittération des accents ({@code marché}
 * ≠ {@code marche}, {@code théâtre} ≠ {@code theatre}), stemming, lemmatisation, synonymes,
 * correction orthographique, distance d'édition, NLP/IA.
 *
 * Classe pure, sans état, déterministe.
 */
public final class KeywordMatcher {

    /** U+02BC (ʼ) : classée « lettre » par Unicode, mais traitée comme une apostrophe (PR #38). */
    private static final int MODIFIER_LETTER_APOSTROPHE = 0x02BC;

    private KeywordMatcher() {
    }

    /** Vrai si les mots de {@code keyword} apparaissent consécutivement et entiers dans {@code text}. */
    public static boolean matches(String keyword, String text) {
        List<String> needle = words(keyword);
        List<String> haystack = words(text);
        if (needle.isEmpty() || needle.size() > haystack.size()) {
            return false;
        }
        for (int start = 0; start <= haystack.size() - needle.size(); start++) {
            if (haystack.subList(start, start + needle.size()).equals(needle)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Vrai si {@code text} est exactement {@code keyword} (mêmes mots, dans le même ordre, rien d'autre) —
     * même normalisation que {@link #matches} : casse, espaces et séparateurs ignorés, accents conservés.
     */
    public static boolean matchesWhole(String keyword, String text) {
        List<String> needle = words(keyword);
        return !needle.isEmpty() && needle.equals(words(text));
    }

    /** Découpe en mots en minuscules, accents conservés. */
    static List<String> words(String value) {
        List<String> words = new ArrayList<>();
        if (value == null) {
            return words;
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
        StringBuilder current = new StringBuilder();
        normalized.codePoints().forEach(codePoint -> {
            if (isWordCharacter(codePoint)) {
                current.appendCodePoint(codePoint);
            } else if (current.length() > 0) {
                words.add(current.toString());
                current.setLength(0);
            }
        });
        if (current.length() > 0) {
            words.add(current.toString());
        }
        return words;
    }

    private static boolean isWordCharacter(int codePoint) {
        if (codePoint == MODIFIER_LETTER_APOSTROPHE) {
            return false;
        }
        if (Character.isLetterOrDigit(codePoint)) {
            return true;
        }
        // Un accent resté séparé de sa lettre (forme décomposée non recomposable) fait partie du mot.
        int type = Character.getType(codePoint);
        return type == Character.NON_SPACING_MARK
                || type == Character.COMBINING_SPACING_MARK
                || type == Character.ENCLOSING_MARK;
    }
}
