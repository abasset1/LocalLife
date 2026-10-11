package com.locallife.backend.activity.application;

/**
 * Niveau de pertinence d'un résultat de la recherche globale (#91), du plus fort au plus faible. Les
 * résultats sont triés par niveau d'abord ; la distance ne départage qu'à niveau (et nombre de champs
 * correspondants) égal. Un champ correspond s'il contient la requête normalisée, ou tous ses termes
 * significatifs.
 */
public enum SearchRelevance {
    /** Titre normalisé égal à la requête normalisée. */
    TITLE_EXACT,
    /** Titre normalisé commençant par la requête. */
    TITLE_PREFIX,
    /** Titre contenant la requête ou tous ses termes significatifs. */
    TITLE_CONTAINS,
    /** Correspondance dans une catégorie (catégorie libre, code ou libellé du référentiel). */
    CATEGORY_MATCH,
    /** Correspondance dans la ville ou l'adresse (de l'activité ou d'un lieu effectif d'occurrence). */
    LOCATION_MATCH,
    /** Correspondance uniquement dans la description. */
    DESCRIPTION_MATCH;

    /** Rang SQL (1 à 6) vers niveau. */
    public static SearchRelevance fromRank(int rank) {
        return values()[rank - 1];
    }
}
