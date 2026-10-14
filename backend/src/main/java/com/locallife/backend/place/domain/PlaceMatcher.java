package com.locallife.backend.place.domain;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Rapprochement d'un lieu d'événement et d'un lieu permanent (ADR-0011, § 2.3) — <b>fonction pure, sans
 * écriture</b>. Elle sert d'abord à une mesure ; aucune liaison n'est faite tant que le mécanisme n'est pas
 * validé.
 *
 * <p>Jamais de rapprochement si l'une des deux positions est approximative (une zone n'est pas un lieu), si les
 * codes postaux diffèrent, ou au-delà de {@value #MAX_DISTANCE_METERS} m. Sinon :
 * <ul>
 *   <li>{@link Level#CERTAIN} : même nom normalisé, ou même adresse normalisée et nom proche, à
 *       {@value #CERTAIN_DISTANCE_METERS} m au plus ;</li>
 *   <li>{@link Level#PROBABLE} : nom proche (mots significatifs du plus court presque tous dans l'autre) ;</li>
 *   <li>lieu d'événement sans nom : nom du lieu permanent contenu dans son adresse — {@link Level#CERTAIN} à
 *       {@value #CERTAIN_DISTANCE_METERS} m au plus, {@link Level#PROBABLE} au-delà ;</li>
 *   <li>{@link Level#AMBIGUOUS} : même adresse mais noms différents, à relire (une salle dans un bâtiment, un
 *       événement nommé d'après le lieu…) ;</li>
 *   <li>{@link Level#NONE} sinon.</li>
 * </ul>
 */
public final class PlaceMatcher {

    public static final double MAX_DISTANCE_METERS = 100;
    public static final double CERTAIN_DISTANCE_METERS = 30;
    public static final double MIN_WORD_OVERLAP = 0.8;
    public static final int MIN_NAME_WORDS_IN_ADDRESS = 2;

    /** Mots sans valeur d'identification d'un lieu. */
    static final Set<String> STOP_WORDS = Set.of(
            "le", "la", "les", "l", "de", "du", "des", "d", "et", "a", "au", "aux", "en", "sur", "saint", "sainte",
            "st", "ste");

    /** Premier mot d'un nom de voie : un tel nom figure dans les adresses sans désigner le lieu. */
    static final Set<String> STREET_WORDS = Set.of(
            "place", "placette", "rue", "avenue", "av", "boulevard", "bd", "cours", "quai", "chemin", "route",
            "allee", "allees", "impasse", "square", "esplanade", "parvis", "promenade");

    private static final Set<String> ARTICLES = Set.of("le", "la", "les", "l");

    private PlaceMatcher() {
    }

    public enum Level {
        CERTAIN,
        PROBABLE,
        AMBIGUOUS,
        NONE
    }

    /** Ce qui est comparé d'un lieu. */
    public record Candidate(
            String name, String address, String postalCode, Double latitude, Double longitude,
            CoordinatePrecision precision) {
    }

    public static Level compare(Candidate eventLocation, Candidate place) {
        if (eventLocation.precision() == CoordinatePrecision.APPROXIMATE
                || place.precision() == CoordinatePrecision.APPROXIMATE) {
            return Level.NONE;
        }
        String postal1 = CoordinatePrecisionPolicy.normalize(eventLocation.postalCode());
        String postal2 = CoordinatePrecisionPolicy.normalize(place.postalCode());
        if (!postal1.isEmpty() && !postal2.isEmpty() && !postal1.equals(postal2)) {
            return Level.NONE;
        }
        double distance = distanceMeters(eventLocation, place);
        if (distance > MAX_DISTANCE_METERS) {
            return Level.NONE;
        }
        String name1 = CoordinatePrecisionPolicy.normalize(eventLocation.name());
        String name2 = CoordinatePrecisionPolicy.normalize(place.name());
        boolean sameName = !name1.isEmpty() && name1.equals(name2);
        boolean closeName = sameName || closeNames(name1, name2);
        String address1 = CoordinatePrecisionPolicy.normalize(eventLocation.address());
        String address2 = CoordinatePrecisionPolicy.normalize(place.address());
        boolean sameAddress = !address1.isEmpty() && address1.equals(address2);
        if (distance <= CERTAIN_DISTANCE_METERS && (sameName || (sameAddress && closeName))) {
            return Level.CERTAIN;
        }
        if (closeName) {
            return Level.PROBABLE;
        }
        if (name1.isEmpty() && nameInAddress(name2, address1)) {
            return distance <= CERTAIN_DISTANCE_METERS ? Level.CERTAIN : Level.PROBABLE;
        }
        return sameAddress ? Level.AMBIGUOUS : Level.NONE;
    }

    /** Nom de voie, article initial ignoré : « Place Saint-Pierre », « Le cours Mirabeau ». */
    static boolean isStreetName(String normalizedName) {
        String[] words = normalizedName.split(" ");
        int first = words.length > 1 && ARTICLES.contains(words[0]) ? 1 : 0;
        return STREET_WORDS.contains(words[first]);
    }

    /**
     * Lieu d'événement sans nom dont l'adresse contient le nom du lieu permanent (cas DATAtourisme : « 31 rue
     * Giraud Cinéma Le Luberon »). Au moins {@value #MIN_NAME_WORDS_IN_ADDRESS} mots significatifs, tous présents ;
     * jamais pour un lieu nommé comme une voie (« Place Saint-Pierre »), que les adresses citent sans le désigner.
     */
    static boolean nameInAddress(String placeName, String eventAddress) {
        if (isStreetName(placeName)) {
            return false;
        }
        Set<String> nameWords = significantWords(placeName);
        return nameWords.size() >= MIN_NAME_WORDS_IN_ADDRESS
                && significantWords(eventAddress).containsAll(nameWords);
    }

    static boolean closeNames(String first, String second) {
        Set<String> a = significantWords(first);
        Set<String> b = significantWords(second);
        if (a.isEmpty() || b.isEmpty()) {
            return false;
        }
        Set<String> shorter = a.size() <= b.size() ? a : b;
        Set<String> longer = shorter == a ? b : a;
        long shared = shorter.stream().filter(longer::contains).count();
        return shared >= MIN_WORD_OVERLAP * shorter.size();
    }

    private static Set<String> significantWords(String normalized) {
        return Arrays.stream(normalized.split(" "))
                .filter(word -> word.length() >= 2 && !STOP_WORDS.contains(word))
                .collect(Collectors.toSet());
    }

    /** Distance orthodromique ; infinie si une position manque. */
    public static double distanceMeters(Candidate first, Candidate second) {
        if (first.latitude() == null || first.longitude() == null
                || second.latitude() == null || second.longitude() == null) {
            return Double.POSITIVE_INFINITY;
        }
        double phi1 = Math.toRadians(first.latitude());
        double phi2 = Math.toRadians(second.latitude());
        double dPhi = phi2 - phi1;
        double dLambda = Math.toRadians(second.longitude() - first.longitude());
        double h = Math.sin(dPhi / 2) * Math.sin(dPhi / 2)
                + Math.cos(phi1) * Math.cos(phi2) * Math.sin(dLambda / 2) * Math.sin(dLambda / 2);
        return 2 * 6_371_000 * Math.asin(Math.sqrt(h));
    }
}
