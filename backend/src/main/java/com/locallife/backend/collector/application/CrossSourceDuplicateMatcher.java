package com.locallife.backend.collector.application;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Règle de rapprochement de deux événements venant de sources différentes (OpenAgenda, DATAtourisme) :
 * ADR-0011, § 2.9. <b>Fonction pure, non branchée</b> : elle ne lit ni n'écrit rien et ne fusionne rien.
 * Elle servira d'abord à une mesure à blanc sur les données réelles, puis, après validation, à un lien de
 * doublon réversible.
 *
 * <p>Mesure à l'origine des seuils (DATAtourisme PACA, 13/10/2026) : un même titre le même jour désigne
 * souvent des événements distincts (« Fête de la musique » à 18–27 km, « Octobre Rose » à 50 km) ; même à
 * moins de 300 m, un titre générique (« Cinéma ») peut être deux séances. D'où :
 * <ul>
 *   <li>jamais de rapprochement sans jour commun ;</li>
 *   <li>{@link Level#PROBABLE} seulement si le titre normalisé est identique et non générique, et les deux
 *       lieux, de position non approximative, à {@value #MAX_DISTANCE_METERS} m au plus ;</li>
 *   <li>{@link Level#POSSIBLE} (relecture humaine) si le titre est proche ou générique, ou si un lieu est
 *       approximatif mais dans la même commune ;</li>
 *   <li>{@link Level#NONE} sinon.</li>
 * </ul>
 */
public final class CrossSourceDuplicateMatcher {

    /** Distance maximale entre deux lieux précis pour un même événement. */
    public static final double MAX_DISTANCE_METERS = 300;

    /** Part minimale des mots du titre le plus court présents dans l'autre, pour un titre « proche ». */
    public static final double MIN_TITLE_WORD_OVERLAP = 0.8;

    /** Titres trop courants pour identifier un événement à eux seuls (forme normalisée). */
    static final Set<String> GENERIC_TITLES = Set.of(
            "marche", "marche provencal", "marche hebdomadaire", "cinema", "concert", "spectacle", "exposition",
            "visite guidee", "atelier", "brocante", "vide greniers", "conference", "projection", "animation");

    private static final double EARTH_RADIUS_METERS = 6_371_000;

    private CrossSourceDuplicateMatcher() {
    }

    /** Niveau de rapprochement. */
    public enum Level {
        PROBABLE,
        POSSIBLE,
        NONE
    }

    /**
     * Ce qui est comparé d'un événement : titre, jours d'occurrence (au jour près, DATAtourisme n'ayant pas
     * d'heure), position et commune du lieu effectif, et précision de cette position (ADR-0011, § 2.6).
     */
    public record Candidate(
            String title, Set<LocalDate> days, Double latitude, Double longitude, String city,
            boolean approximatePosition) {
    }

    public static Level compare(Candidate first, Candidate second) {
        if (Collections.disjoint(first.days(), second.days())) {
            return Level.NONE;
        }
        String firstTitle = normalize(first.title());
        String secondTitle = normalize(second.title());
        boolean sameTitle = !firstTitle.isEmpty() && firstTitle.equals(secondTitle);
        boolean closeTitle = sameTitle || closeTitles(firstTitle, secondTitle);
        if (!closeTitle) {
            return Level.NONE;
        }
        boolean preciseAndNear = !first.approximatePosition() && !second.approximatePosition()
                && distanceMeters(first, second) <= MAX_DISTANCE_METERS;
        boolean approximateSameCity = (first.approximatePosition() || second.approximatePosition())
                && first.city() != null && normalize(first.city()).equals(normalize(second.city()));
        if (!preciseAndNear && !approximateSameCity) {
            return Level.NONE;
        }
        boolean generic = GENERIC_TITLES.contains(firstTitle) || GENERIC_TITLES.contains(secondTitle);
        return sameTitle && preciseAndNear && !generic ? Level.PROBABLE : Level.POSSIBLE;
    }

    /** Minuscules, sans accents ni ponctuation, espaces simples. */
    static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String withoutAccents = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return withoutAccents.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    private static boolean closeTitles(String first, String second) {
        Set<String> firstWords = words(first);
        Set<String> secondWords = words(second);
        if (firstWords.isEmpty() || secondWords.isEmpty()) {
            return false;
        }
        Set<String> shorter = firstWords.size() <= secondWords.size() ? firstWords : secondWords;
        Set<String> longer = shorter == firstWords ? secondWords : firstWords;
        long shared = shorter.stream().filter(longer::contains).count();
        return shared >= MIN_TITLE_WORD_OVERLAP * shorter.size();
    }

    /** Mots significatifs : deux lettres ou plus. */
    private static Set<String> words(String normalized) {
        return Arrays.stream(normalized.split(" "))
                .filter(word -> word.length() >= 2)
                .collect(Collectors.toSet());
    }

    /** Distance orthodromique ; infinie si une position manque. */
    static double distanceMeters(Candidate first, Candidate second) {
        if (first.latitude() == null || first.longitude() == null
                || second.latitude() == null || second.longitude() == null) {
            return Double.POSITIVE_INFINITY;
        }
        double lat1 = Math.toRadians(first.latitude());
        double lat2 = Math.toRadians(second.latitude());
        double deltaLat = lat2 - lat1;
        double deltaLon = Math.toRadians(second.longitude() - first.longitude());
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.sqrt(a));
    }
}
