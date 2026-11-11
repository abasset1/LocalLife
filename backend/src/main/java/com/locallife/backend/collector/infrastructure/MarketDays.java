package com.locallife.backend.collector.infrastructure;

import java.time.DayOfWeek;
import java.util.EnumSet;
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
 * Aucun jour trouvé : ensemble vide, la période reste continue.
 */
final class MarketDays {

    /** Types DATAtourisme des marchés et ventes, dont la description peut donner les jours. */
    static final Set<String> SALE_TYPES = Set.of("Market", "BricABrac", "GarageSale", "SaleEvent");

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
        Matcher matcher = DAY.matcher(DataTourismeEventSelection.normalize(text));
        while (matcher.find()) {
            if (matcher.group(1) != null || matcher.group(3) != null) {
                continue;
            }
            String word = matcher.group(2);
            days.add(word.startsWith("dominica") ? DayOfWeek.SUNDAY : DAYS.get(word));
        }
        return days;
    }
}
