package com.locallife.backend.activity.domain;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Indicateur gratuit / payant d'une activité, déduit de ses tarifs en clair ({@code Activity#conditions}) :
 * renseignés par l'API DATAtourisme (« Plein tarif : 22 €. », « Accès libre. »), par OpenAgenda (« Gratuit,
 * réservation conseillée. ») ou par un contributeur. Calculé à la lecture plutôt que stocké : il suit toute
 * modification des tarifs, quelle qu'en soit l'origine, sans migration.
 *
 * <p>Règles, dans l'ordre (texte comparé sans casse ni accents) :
 * <ol>
 *   <li>au moins un montant en euros supérieur à zéro (« 5 € », « 12,50 euros », « € 8 ») : {@link #PAID},
 *       même si une gratuité partielle est citée (« Gratuit pour les moins de 6 ans. Adulte : 40 € ») ;</li>
 *   <li>« payant », « payante » : {@link #PAID} ;</li>
 *   <li>gratuité annoncée en tête et sans réserve (« Gratuit. », « Accès libre, sur réservation. ») :
 *       {@link #FREE}, même si un public est cité ensuite (« Gratuit. Ateliers adultes et enfants. ») ;</li>
 *   <li>ailleurs dans le texte, gratuité (« gratuit », « entrée libre », « accès libre », « libre
 *       participation », ou des montants tous nuls) sans restriction de public (« moins de », « enfants »,
 *       « adhérents »…) : {@link #FREE} ;</li>
 *   <li>sinon, et notamment sans tarif ou avec « Tarifs non communiqués » : {@link #UNKNOWN}.</li>
 * </ol>
 */
public enum PriceIndicator {
    FREE,
    PAID,
    UNKNOWN;

    private static final Pattern AMOUNT = Pattern.compile(
            "(\\d+(?:[.,]\\d{1,2})?)\\s*(?:€|eur\\b|euros?\\b)|€\\s*(\\d+(?:[.,]\\d{1,2})?)");
    /** Gratuité annoncée d'emblée et sans réserve : « Gratuit. », « Accès libre, réservation conseillée ». */
    private static final Pattern FREE_HEADLINE = Pattern.compile(
            "^(gratuit|gratuite|entree libre|acces libre|entree gratuite|acces gratuit|libre participation"
                    + "|participation libre)\\s*([.,;!]|$)");
    private static final Pattern PAID_WORDS = Pattern.compile("\\bpayante?s?\\b");
    private static final Pattern FREE_WORDS = Pattern.compile(
            "\\bgratuite?s?\\b|\\bgratuitement\\b|\\b(entree|acces) libre\\b|\\blibre participation\\b"
                    + "|\\bparticipation libre\\b|\\bsans frais\\b");
    private static final Pattern RESTRICTION = Pattern.compile(
            "moins de|\\benfants?\\b|\\bjusqu|\\badherents?\\b|\\betudiants?\\b|\\bsauf\\b|\\bmineurs?\\b"
                    + "|\\bseniors?\\b|\\bchomeurs?\\b|demandeurs d'emploi|\\breduit|\\b\\d+ ans\\b");

    public static PriceIndicator of(String conditions) {
        if (conditions == null || conditions.isBlank()) {
            return UNKNOWN;
        }
        String text = normalize(conditions);
        Matcher amounts = AMOUNT.matcher(text);
        boolean zeroAmount = false;
        while (amounts.find()) {
            String value = amounts.group(1) != null ? amounts.group(1) : amounts.group(2);
            if (new BigDecimal(value.replace(',', '.')).signum() > 0) {
                return PAID;
            }
            zeroAmount = true;
        }
        if (PAID_WORDS.matcher(text).find()) {
            return PAID;
        }
        if (FREE_HEADLINE.matcher(text).find()) {
            return FREE;
        }
        if ((zeroAmount || FREE_WORDS.matcher(text).find()) && !RESTRICTION.matcher(text).find()) {
            return FREE;
        }
        return UNKNOWN;
    }

    private static String normalize(String value) {
        String decomposed = Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "")
                .replace('’', '\'')
                .replaceAll("\\p{Zs}", " ")
                .strip();
    }
}
