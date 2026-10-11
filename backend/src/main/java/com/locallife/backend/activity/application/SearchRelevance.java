package com.locallife.backend.activity.application;

/**
 * Niveau de pertinence d'un résultat de la recherche globale (#91), du plus fort au plus faible. Les
 * résultats sont triés par niveau d'abord ; la distance ne départage qu'à niveau (et nombre de champs
 * correspondants) égal.
 *
 * <p>Chaque terme significatif de la requête doit se trouver dans au moins un champ, et les termes
 * peuvent se répartir entre champs. Hors titre exact ou préfixe, le niveau est le <b>moins bon</b>, parmi
 * les termes, du meilleur champ de chaque terme (titre, puis catégorie, puis lieu, puis description) :
 * « concert lyon » avec « concert » dans le titre et « Lyon » en ville vaut {@link #LOCATION_MATCH}.
 */
public enum SearchRelevance {
    /** Titre normalisé égal à la requête normalisée. */
    TITLE_EXACT,
    /** Titre normalisé commençant par la requête. */
    TITLE_PREFIX,
    /** Tous les termes dans le titre. */
    TITLE_CONTAINS,
    /**
     * Tous les termes dans le titre ou une catégorie (libre, code ou libellé du référentiel), au moins un
     * en catégorie.
     */
    CATEGORY_MATCH,
    /**
     * Tous les termes dans le titre, une catégorie ou le lieu (ville ou adresse de l'activité ou d'un lieu
     * effectif d'occurrence), au moins un dans le lieu.
     */
    LOCATION_MATCH,
    /** Au moins un terme trouvé uniquement dans la description. */
    DESCRIPTION_MATCH;

    /** Rang SQL (1 à 6) vers niveau. */
    public static SearchRelevance fromRank(int rank) {
        return values()[rank - 1];
    }
}
