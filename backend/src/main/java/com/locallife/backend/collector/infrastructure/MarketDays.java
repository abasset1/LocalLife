package com.locallife.backend.collector.infrastructure;

import com.locallife.backend.collector.domain.CollectedTiming;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Jours de la semaine d'un événement publié sur une longue période (marché du jeudi publié du 1er janvier au
 * 31 décembre) : l'export CSV DATAtourisme ne les donne pas, ils sont lus dans le texte.
 *
 * <ul>
 *   <li><b>titre</b> d'abord, pour tout type d'événement (« Marché du jeudi », « Samedi : marché
 *       hebdomadaire », « Marché dominical ») ; s'il en nomme, la description n'est pas lue ;</li>
 *   <li>sinon <b>description</b>, pour les seuls marchés et ventes ({@link #SALE_TYPES}) : « tous les jeudis
 *       matin », « le mardi matin sur la place ». Pas pour une exposition, où « fermé le lundi » serait
 *       pris pour un jour d'ouverture. Un jour précédé de « sauf » ou « fermé » est ignoré.</li>
 * </ul>
 * Plage « du lundi au samedi » : tous les jours compris ; « les week-ends » : samedi et dimanche. Aucun jour
 * trouvé : ensemble vide, la période reste continue.
 *
 * <p>Après le CSV, {@link DataTourismeApiEnricher} relit les jours d'un marché resté continu dans les horaires
 * et la description détaillée de l'API ({@link #splitWithApiText}), absents du CSV.
 */
final class MarketDays {

    /** Types DATAtourisme des marchés et ventes, dont la description peut donner les jours. */
    static final Set<String> SALE_TYPES = Set.of("Market", "BricABrac", "GarageSale", "SaleEvent");

    /** Libellés des types de {@link #SALE_TYPES} dans les tags d'un événement collecté (voir {@code TYPE_LABELS}). */
    private static final Set<String> SALE_TAGS = Set.of("marché", "brocante", "vide-greniers", "vente");

    /** Au-delà, une période dont les jours de la semaine sont annoncés est découpée. */
    static final int MAX_CONTINUOUS_DAYS = 7;

    /** Début de description lu : les jours y sont annoncés, la suite parle d'autre chose (autres marchés…). */
    private static final int DESCRIPTION_LENGTH = 400;

    private static final Map<String, DayOfWeek> DAYS = Map.of(
            "lundi", DayOfWeek.MONDAY,
            "mardi", DayOfWeek.TUESDAY,
            "mercredi", DayOfWeek.WEDNESDAY,
            "jeudi", DayOfWeek.THURSDAY,
            "vendredi", DayOfWeek.FRIDAY,
            "samedi", DayOfWeek.SATURDAY,
            "dimanche", DayOfWeek.SUNDAY);

    private static final String DAY_NAMES = "lundi|mardi|mercredi|jeudi|vendredi|samedi|dimanche";

    /** « du lundi au samedi », « mardi au dimanche », « de mercredi à vendredi » (accents retirés). */
    private static final Pattern RANGE = Pattern.compile(
            "\\b(" + DAY_NAMES + ")s?\\s+(?:au|a)\\s+(" + DAY_NAMES + ")s?\\b");

    /** « les week-ends », « le week-end » (tirets remplacés par des espaces) : samedi et dimanche. */
    private static final Pattern WEEKEND = Pattern.compile("\\bweek\\s?ends?\\b");

    private static final Pattern DAY = Pattern.compile(
            "\\b(?:(sauf|fermee?s?|fermeture)\\s+(?:les?\\s+)?)?"
                    + "(lundi|mardi|mercredi|jeudi|vendredi|samedi|dimanche|dominical|dominicaux)s?\\b"
                    + "(\\s+gras)?");

    private MarketDays() {
    }

    static Set<DayOfWeek> find(String title, String description, Iterable<String> types) {
        Set<DayOfWeek> fromTitle = days(title);
        if (!fromTitle.isEmpty()) {
            return fromTitle;
        }
        for (String type : types) {
            if (SALE_TYPES.contains(type)) {
                String text = description == null ? "" : description;
                return days(text.substring(0, Math.min(text.length(), DESCRIPTION_LENGTH)));
            }
        }
        return EnumSet.noneOf(DayOfWeek.class);
    }

    static Set<DayOfWeek> days(String text) {
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        String normalized = DataTourismeEventSelection.normalize(text);
        Matcher range = RANGE.matcher(normalized);
        StringBuilder rest = new StringBuilder();
        while (range.find()) {
            DayOfWeek day = DAYS.get(range.group(1));
            DayOfWeek last = DAYS.get(range.group(2));
            days.add(day);
            while (day != last) {
                day = day.plus(1);
                days.add(day);
            }
            range.appendReplacement(rest, " ");
        }
        range.appendTail(rest);
        if (WEEKEND.matcher(rest).find()) {
            days.add(DayOfWeek.SATURDAY);
            days.add(DayOfWeek.SUNDAY);
        }
        Matcher matcher = DAY.matcher(rest);
        while (matcher.find()) {
            if (matcher.group(1) != null || matcher.group(3) != null) {
                continue;
            }
            String word = matcher.group(2);
            days.add(word.startsWith("dominica") ? DayOfWeek.SUNDAY : DAYS.get(word));
        }
        return days;
    }

    /**
     * Une période de plus de {@value #MAX_CONTINUOUS_DAYS} jours devient un horaire par jour de {@code days}
     * (mêmes heures de début et de fin) ; sans jour, ou plus courte, elle reste telle quelle.
     */
    static List<CollectedTiming> split(List<CollectedTiming> timings, Set<DayOfWeek> days) {
        List<CollectedTiming> result = new ArrayList<>();
        for (CollectedTiming timing : timings) {
            if (days.isEmpty() || !isLong(timing)) {
                result.add(timing);
                continue;
            }
            LocalDate last = timing.end().toLocalDate();
            for (LocalDate day = timing.begin().toLocalDate(); !day.isAfter(last); day = day.plusDays(1)) {
                if (days.contains(day.getDayOfWeek())) {
                    result.add(new CollectedTiming(
                            day.atTime(timing.begin().toLocalTime()), day.atTime(timing.end().toLocalTime())));
                }
            }
        }
        return result;
    }

    /**
     * Marché ou vente resté continu après le CSV : jours lus dans les horaires de l'API, sinon dans sa description
     * détaillée. Autres événements, ou rien de trouvé : horaires inchangés.
     */
    static List<CollectedTiming> splitWithApiText(
            List<CollectedTiming> timings, List<String> tags, String openingHours, String longDescription) {
        if (timings == null || tags == null || tags.stream().noneMatch(SALE_TAGS::contains)
                || timings.stream().noneMatch(MarketDays::isLong)) {
            return timings;
        }
        Set<DayOfWeek> days = days(openingHours);
        if (days.isEmpty()) {
            days = days(longDescription);
        }
        return split(timings, days);
    }

    private static boolean isLong(CollectedTiming timing) {
        return timing.begin() != null && timing.end() != null
                && !timing.end().toLocalDate().isBefore(timing.begin().toLocalDate().plusDays(MAX_CONTINUOUS_DAYS));
    }
}
