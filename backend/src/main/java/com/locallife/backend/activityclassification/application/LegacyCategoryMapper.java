package com.locallife.backend.activityclassification.application;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Correspondance « ancienne valeur de {@code activity.category} → catégorie
 * V1 » (CAT-007, {@code docs/05_Sprints/SPRINT_CATEG.md}).
 *
 * <p>Contexte : l'ancienne colonne {@code activity.category} est une chaîne
 * libre (premier mot-clé OpenAgenda ou saisie manuelle), voir
 * {@code docs/CATEGORY_MIGRATION_MAP.md} §3-§4. Cette classe est la seule
 * définition, exécutable et testée, de la correspondance documentée au §10
 * de ce document ; elle est destinée à être appelée par toute opération qui
 * doit traduire une ancienne valeur (reprise de données, et en CAT-008 la
 * classification des imports) plutôt que de dupliquer des règles.
 *
 * <p>Principe imposé par le ticket : <b>aucune classification inventée</b>.
 * Une valeur n'est classée que si elle figure, après normalisation
 * (minuscules, sans accents ni ponctuation), dans la liste fermée
 * ci-dessous — correspondance exacte, jamais par sous-chaîne (sinon
 * « marché du travail » serait classé {@code MARKETS}). Tout le reste est
 * {@link Outcome#NEEDS_REVIEW} avec la raison de ce statut, et les
 * catégories candidates quand l'ambiguïté en porte.
 *
 * <p>Ne crée jamais de catégorie : les seuls codes produits sont des codes
 * V1 existants (CAT-001).
 */
public final class LegacyCategoryMapper {

    public enum Outcome {
        /** Correspondance sûre vers une catégorie V1 : {@code categoryCode} renseigné. */
        MAPPED,
        /** Donnée ambiguë ou sans équivalent : à relire, aucune catégorie proposée. */
        NEEDS_REVIEW,
        /** Aucune valeur d'origine (null/blanc) : rien à migrer, pas une ambiguïté. */
        NO_VALUE
    }

    public enum ReviewReason {
        /** Plusieurs catégories V1 plausibles, rien ne permet de trancher (voir candidats). */
        AMBIGUOUS_CATEGORY,
        /** La valeur décrit un public (dimension AUDIENCE), pas une catégorie. */
        AUDIENCE_VALUE,
        /** La valeur décrit un type d'activité (dimension ACTIVITY_TYPE), pas une catégorie. */
        ACTIVITY_TYPE_VALUE,
        /** Domaine emploi/formation : aucune des 9 catégories V1 (CATEGORY_MIGRATION_MAP §6.1). */
        EMPLOYMENT_DOMAIN,
        /** Valeur inconnue : lieu, artiste, code interne, texte libre... */
        UNRECOGNIZED
    }

    /**
     * Résultat d'une correspondance.
     *
     * @param ruleId identifiant de la règle appliquée, pour la traçabilité
     *               (ex. {@code EXACT:CULTURE}, {@code REVIEW:AUDIENCE_VALUE})
     */
    public record Result(
            Outcome outcome,
            String categoryCode,
            ReviewReason reason,
            List<String> candidateCategoryCodes,
            String ruleId) {
    }

    private static final Map<String, String> EXACT = Map.ofEntries(
            // CULTURE
            Map.entry("theatre", "CULTURE"),
            Map.entry("theatre de papier", "CULTURE"),
            Map.entry("theatre d objet", "CULTURE"),
            Map.entry("spectacle", "CULTURE"),
            Map.entry("spectacle de rue", "CULTURE"),
            Map.entry("concert", "CULTURE"),
            Map.entry("musique", "CULTURE"),
            Map.entry("jazz", "CULTURE"),
            Map.entry("cirque", "CULTURE"),
            Map.entry("danse", "CULTURE"),
            Map.entry("danse verticale", "CULTURE"),
            Map.entry("exposition", "CULTURE"),
            Map.entry("lecture", "CULTURE"),
            Map.entry("club lecture", "CULTURE"),
            Map.entry("marionnettes", "CULTURE"),
            Map.entry("conte", "CULTURE"),
            Map.entry("contes", "CULTURE"),
            Map.entry("poesie", "CULTURE"),
            Map.entry("opera", "CULTURE"),
            Map.entry("clown", "CULTURE"),
            Map.entry("magie", "CULTURE"),
            Map.entry("cinema", "CULTURE"),
            Map.entry("projection", "CULTURE"),
            Map.entry("film", "CULTURE"),
            Map.entry("culture", "CULTURE"),
            // MARKETS
            Map.entry("marche", "MARKETS"),
            Map.entry("marches", "MARKETS"),
            Map.entry("brocante", "MARKETS"),
            Map.entry("vide grenier", "MARKETS"),
            Map.entry("vide greniers", "MARKETS"),
            // GASTRONOMY
            Map.entry("food truck", "GASTRONOMY"),
            Map.entry("food trucks", "GASTRONOMY"),
            Map.entry("degustation", "GASTRONOMY"),
            Map.entry("gastronomie", "GASTRONOMY"),
            // SPORT
            Map.entry("sport", "SPORT"),
            Map.entry("sports", "SPORT"),
            Map.entry("football", "SPORT"),
            Map.entry("basket", "SPORT"),
            // NATURE
            Map.entry("nature", "NATURE"),
            // LEISURE
            Map.entry("jeux", "LEISURE"),
            Map.entry("jeux de societe", "LEISURE"),
            Map.entry("jeux video", "LEISURE"),
            Map.entry("jeux videos", "LEISURE"));

    /**
     * Valeurs ambiguës entre deux catégories V1 : le patrimoine et les
     * visites relèvent de la culture ou du tourisme selon l'intention
     * (CATEGORY_MIGRATION_MAP §6.4 : aucun signal ne permet de trancher) ;
     * la randonnée est un sport ou une activité de nature.
     */
    private static final Map<String, List<String>> AMBIGUOUS = Map.ofEntries(
            Map.entry("patrimoine", List.of("CULTURE", "TOURISM")),
            Map.entry("journees du patrimoine", List.of("CULTURE", "TOURISM")),
            Map.entry("journees europeennes du patrimoine", List.of("CULTURE", "TOURISM")),
            Map.entry("jep", List.of("CULTURE", "TOURISM")),
            Map.entry("visite", List.of("CULTURE", "TOURISM")),
            Map.entry("visites", List.of("CULTURE", "TOURISM")),
            Map.entry("visite guidee", List.of("CULTURE", "TOURISM")),
            Map.entry("visites guidees", List.of("CULTURE", "TOURISM")),
            Map.entry("visite commentee", List.of("CULTURE", "TOURISM")),
            Map.entry("visite libre", List.of("CULTURE", "TOURISM")),
            Map.entry("architecture", List.of("CULTURE", "TOURISM")),
            Map.entry("archeologie", List.of("CULTURE", "TOURISM")),
            Map.entry("randonnee", List.of("SPORT", "NATURE")),
            Map.entry("randonnees", List.of("SPORT", "NATURE")),
            Map.entry("loisir", List.of("LEISURE")),
            Map.entry("loisirs", List.of("LEISURE")));

    /**
     * Publics (dimension AUDIENCE) : des attributs, pas des catégories métier
     * (arbitrage validé, CATEGORY_MIGRATION_MAP §11.5). Ne jamais en déduire
     * ni en créer une catégorie.
     */
    private static final Set<String> AUDIENCE_VALUES = Set.of(
            "jeune public", "tres jeune public", "tout public", "famille",
            "adulte", "adultes", "jeunesse", "enfants", "ados", "seniors");

    /** Types d'activité (dimension ACTIVITY_TYPE) : ne sont pas des catégories. */
    private static final Set<String> ACTIVITY_TYPE_VALUES = Set.of(
            "atelier", "atelier creatif", "bricolage", "conference", "rencontre",
            "festival", "animation", "stage");

    /**
     * Mots entiers signalant le domaine emploi/formation/insertion. Aucune
     * catégorie V1 n'existe pour ce domaine : décision de fond dédiée avant
     * CAT-008 (CATEGORY_MIGRATION_MAP §11.5), le référentiel n'est pas modifié ici.
     */
    private static final Set<String> EMPLOYMENT_TOKENS = Set.of(
            "emploi", "recrutement", "formation", "insertion", "alternance",
            "apprentissage", "metier", "metiers", "interim", "travail",
            "tousmobilises", "tousenimmersion", "qpv", "cv", "orientation");

    private LegacyCategoryMapper() {
    }

    public static Result map(String legacyValue) {
        if (legacyValue == null || legacyValue.isBlank()) {
            return new Result(Outcome.NO_VALUE, null, null, List.of(), "NO_VALUE");
        }
        String key = normalize(legacyValue);
        if (key.isEmpty()) {
            return review(ReviewReason.UNRECOGNIZED);
        }
        String code = EXACT.get(key);
        if (code != null) {
            return new Result(Outcome.MAPPED, code, null, List.of(), "EXACT:" + code);
        }
        List<String> candidates = AMBIGUOUS.get(key);
        if (candidates != null) {
            return new Result(Outcome.NEEDS_REVIEW, null, ReviewReason.AMBIGUOUS_CATEGORY,
                    candidates, "REVIEW:" + ReviewReason.AMBIGUOUS_CATEGORY);
        }
        if (AUDIENCE_VALUES.contains(key)) {
            return review(ReviewReason.AUDIENCE_VALUE);
        }
        if (ACTIVITY_TYPE_VALUES.contains(key)) {
            return review(ReviewReason.ACTIVITY_TYPE_VALUE);
        }
        for (String token : key.split(" ")) {
            if (EMPLOYMENT_TOKENS.contains(token)) {
                return review(ReviewReason.EMPLOYMENT_DOMAIN);
            }
        }
        return review(ReviewReason.UNRECOGNIZED);
    }

    /** Minuscules, sans accents, ponctuation remplacée par des espaces, espaces compactés. */
    public static String normalize(String value) {
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
        return decomposed.replaceAll("[^a-z0-9]+", " ").trim();
    }

    private static Result review(ReviewReason reason) {
        return new Result(Outcome.NEEDS_REVIEW, null, reason, List.of(), "REVIEW:" + reason);
    }
}
