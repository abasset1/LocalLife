package com.locallife.backend.place.domain;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Règles de la précision géographique (ADR-0011, § 7.1, phase A), en fonctions pures.
 *
 * <p><b>Non branchée</b> : rien ne l'appelle encore en dehors du collecteur DATAtourisme, qui lui délègue la
 * reconnaissance des textes génériques (comportement inchangé). Le branchement suppose la migration du
 * § 7.1, non appliquée.
 *
 * <p>Règles :
 * <ul>
 *   <li>un import ne pose jamais {@link CoordinatePrecision#EXACT} : aucune source actuelle ne garantit la
 *       position ; {@code EXACT} est réservé à une validation humaine ;</li>
 *   <li>un texte de lieu générique (« Divers lieux », « Centre ville »…) donne
 *       {@link CoordinatePrecision#APPROXIMATE} ; tout le reste {@link CoordinatePrecision#UNKNOWN} ;</li>
 *   <li>la même règle classe les lieux existants (rattrapage) : elle ne déduit jamais {@code EXACT} ;</li>
 *   <li>un lieu approximatif n'est partagé qu'avec un lieu approximatif de même nom et de même commune.</li>
 * </ul>
 *
 * <p>Le rattrapage SQL du § 7.1 reproduit {@link #isGenericPlaceText} ; leur équivalence est vérifiée par
 * {@code GenericPlaceSqlParityIntegrationTest}.
 */
public final class CoordinatePrecisionPolicy {

    private static final Pattern MULTIPLE_PLACES = Pattern.compile("\\b(divers|differents|plusieurs) lieux\\b");

    /** Texte désignant une zone et non un lieu (texte normalisé, nom de commune retiré). */
    private static final Pattern GENERIC_AREA = Pattern.compile(
            "(en )?(centre ville|centre village|centre du village|le village|dans le village|dans la ville"
                    + "|dans tout le village|dans les rues( du village| de la ville)?)");

    private CoordinatePrecisionPolicy() {
    }

    /**
     * Vrai si le texte désigne plusieurs lieux ou une zone de la commune plutôt qu'un lieu : « Divers lieux à
     * Aix », « Centre ville Saint-Rémy-de-Provence » (avec {@code city} = Saint-Rémy-de-Provence), « Dans les
     * rues du village ». Faux pour « Place du village » ou « Centre ville Cours Hyacinthe Bellon ».
     */
    public static boolean isGenericPlaceText(String text, String city) {
        if (text == null) {
            return false;
        }
        String normalized = normalize(text);
        if (MULTIPLE_PLACES.matcher(normalized).find()) {
            return true;
        }
        if (city != null) {
            String normalizedCity = normalize(city);
            if (!normalizedCity.isEmpty()) {
                normalized = normalized.replace(normalizedCity, " ").replaceAll("\\s+", " ").trim();
            }
        }
        return GENERIC_AREA.matcher(normalized).matches();
    }

    /**
     * Précision d'un lieu d'après ses textes, pour un lieu importé comme pour un lieu existant (rattrapage).
     * Le texte examiné est l'adresse, ou le nom à défaut d'adresse : le collecteur DATAtourisme range une
     * adresse générique dans le nom et vide l'adresse. Ne renvoie jamais {@link CoordinatePrecision#EXACT}.
     */
    public static CoordinatePrecision inferFromText(String name, String address, String city) {
        String text = hasText(address) ? address : name;
        return isGenericPlaceText(text, city) ? CoordinatePrecision.APPROXIMATE : CoordinatePrecision.UNKNOWN;
    }

    /**
     * Un lieu existant peut-il être réutilisé pour un lieu candidat de même position ?
     *
     * <p>Entre lieux non approximatifs : oui (règle actuelle de {@code LocationService#findOrCreate}). Dès
     * qu'un des deux est approximatif : seulement si les deux le sont, avec le même nom et la même commune
     * (comparés normalisés). Sinon deux événements « Divers lieux » et « Centre ville » d'une même commune,
     * géocodés au même centre, partageraient un lieu ; et un lieu précis serait confondu avec une zone. La
     * réutilisation entre lieux approximatifs identiques évite d'en créer un nouveau à chaque réimport.
     */
    public static boolean mayReuse(
            CoordinatePrecision candidate, String candidateName, String candidateCity,
            CoordinatePrecision existing, String existingName, String existingCity) {
        boolean candidateApproximate = candidate == CoordinatePrecision.APPROXIMATE;
        boolean existingApproximate = existing == CoordinatePrecision.APPROXIMATE;
        if (!candidateApproximate && !existingApproximate) {
            return true;
        }
        return candidateApproximate && existingApproximate
                && Objects.equals(normalize(candidateName), normalize(existingName))
                && Objects.equals(normalize(candidateCity), normalize(existingCity));
    }

    /**
     * Précision d'un lieu existant réutilisé par un import : inchangée. Un import n'abaisse pas une
     * validation humaine ({@code EXACT}) et n'élève rien ; {@link #mayReuse} garantit déjà qu'un lieu
     * approximatif n'est réutilisé que pour un candidat approximatif.
     */
    public static CoordinatePrecision onReuse(CoordinatePrecision existing) {
        return existing == null ? CoordinatePrecision.UNKNOWN : existing;
    }

    /** Mention « Emplacement approximatif » et itinéraire vers la commune : seulement pour {@code APPROXIMATE}. */
    public static boolean isShownAsApproximate(CoordinatePrecision precision) {
        return precision == CoordinatePrecision.APPROXIMATE;
    }

    /** Minuscules, sans accents ni ponctuation, espaces simples ; chaîne vide pour {@code null}. */
    static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String withoutAccents = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return withoutAccents.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
