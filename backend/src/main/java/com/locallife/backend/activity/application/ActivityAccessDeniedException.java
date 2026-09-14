package com.locallife.backend.activity.application;

/**
 * Le demandeur n'est ni le créateur de l'activité, ni un administrateur
 * (LL-EF-010, voir {@link ActivityService#cancel}). Distincte d'{@link
 * IllegalArgumentException} : il ne s'agit pas d'une donnée invalide mais
 * d'un refus d'autorisation — traitée comme un {@code 403} par le
 * contrôleur, même sémantique que le {@code 403} renvoyé par
 * {@code SecurityConfig} pour les règles de rôle statiques, mais ici pour
 * une règle qui dépend des données (le créateur de <em>cette</em> activité
 * précisément) et ne peut donc pas être exprimée par une règle statique.
 */
public class ActivityAccessDeniedException extends RuntimeException {

    public ActivityAccessDeniedException(String message) {
        super(message);
    }
}
