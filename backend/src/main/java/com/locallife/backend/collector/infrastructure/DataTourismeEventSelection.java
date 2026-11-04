package com.locallife.backend.collector.infrastructure;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Sélection d'événements d'une source DATAtourisme ({@code source.event_selection}, V77) : quels événements du
 * fichier garder, en plus des règles communes (départements, dates, coordonnées). Sans sélection, tous les
 * événements sont gardés.
 *
 * <p>Mesure à l'origine de {@link #VIDE_GRENIERS_BROCANTES} (export France du 29/10/2026, 72 581 événements) :
 * 1 052 vide-greniers, brocantes, puces, braderies et bourses à venir dans 68 départements, contre 138 publiés sur
 * la bêta (32 départements). Le type ne suffit pas : 20 % de ces événements n'ont qu'un type générique
 * ({@code SaleEvent}, {@code SocialEvent}, {@code Event}…) et ne se reconnaissent qu'à leur titre (« Bourse aux
 * jouets », « Marché aux puces »).
 *
 * <p>Faux positifs mesurés sur l'export France du 04/11/2026 (1 074 retenus, relus un à un) :
 * <ul>
 *   <li>par le type : des producteurs typent {@code GarageSale} ou {@code BricABrac} des événements d'un autre
 *       genre (« Concert de Naudau », « Fête de la châtaigne », « Marché de Noël », « Repair Café », « Collecte de
 *       textile ») — 39 cas. Un tel titre n'est gardé que s'il nomme aussi une vente d'occasion (« Vide jouets –
 *       Téléthon », « Fête de la Récup' ») ou si la description en annonce une (« Marché de Noël » avec brocante,
 *       « Téléthon » avec vide-grenier) ;</li>
 *   <li>par un mot-clé ambigu du titre (« puces », « bourse aux ») : spectacles et réunions (« Spectacle musical
 *       "Aux puces superphoniques" », « Réunion de préparation de la bourse aux jouets ») — 4 cas.</li>
 * </ul>
 * Faux négatifs du même export, ajoutés aux mots-clés du titre : « Foire à tout », « Bric à brac », « Vide ta
 * chambre », « Vide-jardin », « Bourse puériculture » (16 événements, tous pertinents).
 */
public enum DataTourismeEventSelection {

    /** Vide-greniers et brocantes : types {@code GarageSale} ou {@code BricABrac}, ou titre explicite. */
    VIDE_GRENIERS_BROCANTES(
            Set.of("GarageSale", "BricABrac"),
            // Mots-clés du titre, suffisants à eux seuls.
            Pattern.compile("\\b(vide ?greniers?|brocantes?|braderies?|puces|vide ?dressings?|bourses? aux|"
                    + "troc et puces|foires? a tout|bric a brac|vide (jardins?|maisons?|poussettes?|ta chambre|"
                    + "chambres?)|bourses? (de |a la )?puericulture)\\b"),
            // Mots-clés sans ambiguïté : seuls « puces » et « bourse… » peuvent nommer autre chose qu'une vente.
            Pattern.compile("\\b(vide ?greniers?|brocantes?|braderies?|vide ?dressings?|marches? aux puces|"
                    + "troc et puces|foires? a tout|bric a brac|vide (jardins?|maisons?|poussettes?|ta chambre|"
                    + "chambres?))\\b"),
            // Spectacle ou réunion dont le titre contient un mot-clé ambigu.
            Pattern.compile("\\b(spectacles?|diner|concerts?|reunion)\\b"),
            // Titre d'un autre genre d'événement (fête, marché artisanal ou de Noël, atelier, collecte…).
            Pattern.compile("\\b(concerts?|spectacles?|theatre|diner|reunion|conferences?|telethon|kermesse|"
                    + "repair cafe|ateliers?|fetes? (de|du|des|locales?)|en fete|marches? (de noel|de l'avent|"
                    + "artisanal|d'automne|du lien|des createurs)|village de noel|salon de noel|vente de noel|"
                    + "expo(sition)? ?(et )?vente|portes ouvertes|collecte|anniversaire|manoeuvre|jeu de noel|"
                    + "impromptu|foire (sainte|saint|a l'artisanat|d'ete))\\b"),
            // Mots d'une vente d'occasion, dans un titre d'un autre genre (« Vide jouets – Téléthon »).
            Pattern.compile("\\b(vide|troc|trocs|bric a brac|foires? a tout|braderies?|brocantes?|puces?|bourses?|"
                    + "gratiferias?|gratuiterie|friperies?|fripes?|deballage|chineurs?|occasion|seconde main|"
                    + "antiquites?|antiquaires?|recup\\w*|broc\\w*)\\b"),
            // Vente d'occasion annoncée par la description d'un titre d'un autre genre.
            Pattern.compile("\\b(vide ?greniers?|brocantes?|braderies?|vide ?dressings?|marches? aux puces|"
                    + "troc et puces|chiner|seconde main|d'occasion|antiquites?)\\b"));

    private final Set<String> types;

    /**
     * Tous les motifs sont comparés au texte en minuscules, sans accents, tirets remplacés par des espaces,
     * apostrophes normalisées ({@link #normalize}).
     */
    private final Pattern title;
    private final Pattern unambiguousTitle;
    private final Pattern show;
    private final Pattern otherEvent;
    private final Pattern saleWord;
    private final Pattern saleDescription;

    DataTourismeEventSelection(Set<String> types, Pattern title, Pattern unambiguousTitle, Pattern show,
            Pattern otherEvent, Pattern saleWord, Pattern saleDescription) {
        this.types = types;
        this.title = title;
        this.unambiguousTitle = unambiguousTitle;
        this.show = show;
        this.otherEvent = otherEvent;
        this.saleWord = saleWord;
        this.saleDescription = saleDescription;
    }

    /**
     * Vrai si l'événement fait partie de la sélection :
     * <ol>
     *   <li>titre avec un mot-clé : gardé, sauf mot-clé ambigu (« puces », « bourse aux ») dans le titre d'un
     *       spectacle ou d'une réunion ;</li>
     *   <li>sinon, type de la sélection : gardé, sauf titre d'un autre genre d'événement sans mot de vente
     *       d'occasion ; ce titre-là n'est gardé que si la description annonce une vente d'occasion ;</li>
     *   <li>sinon : écarté.</li>
     * </ol>
     */
    public boolean matches(String eventTitle, String eventDescription, List<String> eventTypes) {
        String normalizedTitle = normalize(eventTitle);
        if (title.matcher(normalizedTitle).find()) {
            return unambiguousTitle.matcher(normalizedTitle).find() || !show.matcher(normalizedTitle).find();
        }
        if (eventTypes == null || eventTypes.stream().noneMatch(types::contains)) {
            return false;
        }
        if (!otherEvent.matcher(normalizedTitle).find() || saleWord.matcher(normalizedTitle).find()) {
            return true;
        }
        return saleDescription.matcher(normalize(eventDescription)).find();
    }

    /**
     * Sélection enregistrée pour une source ({@code null} ou vide : aucune, tous les événements sont gardés).
     *
     * @throws IllegalArgumentException si la valeur n'est pas une sélection connue
     */
    public static DataTourismeEventSelection fromStoredValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return valueOf(value.trim());
    }

    /** Minuscules, sans accents, tirets remplacés par des espaces, apostrophes normalisées ; {@code null} → vide. */
    static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String withoutAccents = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return withoutAccents.replace('’', '\'').replaceAll("\\p{Pd}", " ").toLowerCase(Locale.ROOT);
    }
}
