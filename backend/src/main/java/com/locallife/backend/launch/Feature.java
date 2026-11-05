package com.locallife.backend.launch;

/**
 * Fonctions ouvertes progressivement après le lancement (décision du 05/11/2026 : lancement PACA et Occitanie avec
 * les activités et les comptes gratuits seulement). Chacune s'ouvre par un interrupteur serveur, sans nouvelle
 * version du site ni de l'application ({@link LaunchFeatures}).
 */
public enum Feature {

    /** Lieux permanents : couche « Lieux », fiches, revendication et gestion dans l'espace pro. */
    PLACES,

    /** Food trucks : couche, liste, fiches, gestion et planning dans l'espace pro. */
    FOOD_TRUCKS,

    /** Offre Premium : souscription et suivi d'activités ou de lieux (cloche « Suivre »). */
    PREMIUM,

    /** Offre Pro : souscription et espace professionnel ({@code /api/v1/pro/**}). */
    PRO
}
