package com.locallife.backend.place.domain;

import com.locallife.backend.place.domain.PlaceMatcher.Candidate;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Doublons de lieux d'événement (ADR-0011, § 7.2.13) — <b>fonction pure, sans écriture</b>. Un même lieu réel a
 * souvent plusieurs lignes, parce que {@code LocationService#findOrCreate} ne réutilise un lieu que pour une
 * adresse identique au caractère près (« 8 Place Aristide Briand » et « 8 place Aristide Briand » donnent deux
 * lignes).
 *
 * <p>Le texte comparé est le nom suivi de l'adresse, réduit à ses mots significatifs (sans accents, casse,
 * ponctuation ni mots vides ; ordre ignoré). Jamais de doublon si une position est approximative (une zone n'est
 * pas un lieu), si les codes postaux diffèrent, ou au-delà de {@value PlaceMatcher#MAX_DISTANCE_METERS} m.
 * Sinon :
 * <ul>
 *   <li>{@link PlaceMatcher.Level#CERTAIN} : mêmes mots, à {@value PlaceMatcher#CERTAIN_DISTANCE_METERS} m au
 *       plus ;</li>
 *   <li>{@link PlaceMatcher.Level#PROBABLE} : mêmes mots plus loin, ou tous les mots de l'un (au moins
 *       {@value #MIN_WORDS}) présents dans l'autre (« Palais des Papes place du Palais » et « Place du Palais
 *       Départ : Palais des Papes »), à relire : une salle précise dans un bâtiment en est un cas ;</li>
 *   <li>{@link PlaceMatcher.Level#NONE} sinon.</li>
 * </ul>
 */
public final class EventLocationDuplicateMatcher {

    public static final int MIN_WORDS = 2;

    private EventLocationDuplicateMatcher() {
    }

    public static PlaceMatcher.Level compare(Candidate first, Candidate second) {
        if (first.precision() == CoordinatePrecision.APPROXIMATE
                || second.precision() == CoordinatePrecision.APPROXIMATE) {
            return PlaceMatcher.Level.NONE;
        }
        String postal1 = CoordinatePrecisionPolicy.normalize(first.postalCode());
        String postal2 = CoordinatePrecisionPolicy.normalize(second.postalCode());
        if (!postal1.isEmpty() && !postal2.isEmpty() && !postal1.equals(postal2)) {
            return PlaceMatcher.Level.NONE;
        }
        double distance = PlaceMatcher.distanceMeters(first, second);
        if (distance > PlaceMatcher.MAX_DISTANCE_METERS) {
            return PlaceMatcher.Level.NONE;
        }
        Set<String> words1 = words(first);
        Set<String> words2 = words(second);
        if (words1.size() < MIN_WORDS || words2.size() < MIN_WORDS) {
            return PlaceMatcher.Level.NONE;
        }
        if (words1.equals(words2)) {
            return distance <= PlaceMatcher.CERTAIN_DISTANCE_METERS
                    ? PlaceMatcher.Level.CERTAIN : PlaceMatcher.Level.PROBABLE;
        }
        return words1.containsAll(words2) || words2.containsAll(words1)
                ? PlaceMatcher.Level.PROBABLE : PlaceMatcher.Level.NONE;
    }

    /** Mots significatifs du nom et de l'adresse ; les numéros sont gardés, même d'un chiffre (« 3 rue Carnot »). */
    static Set<String> words(Candidate location) {
        String name = location.name() == null ? "" : location.name();
        String address = location.address() == null ? "" : location.address();
        return Arrays.stream(CoordinatePrecisionPolicy.normalize(name + " " + address).split(" "))
                .filter(word -> !word.isEmpty())
                .filter(word -> word.chars().allMatch(Character::isDigit)
                        || (word.length() >= 2 && !PlaceMatcher.STOP_WORDS.contains(word)))
                .collect(Collectors.toSet());
    }
}
