package com.locallife.backend.activityclassification.domain;

/**
 * Statuts de classification d'une activité (CAT-008, SPRINT_CATEG).
 *
 * <p>À ne pas confondre avec {@code Activity.status} (PENDING / PUBLISHED /
 * REJECTED / ARCHIVED = modération et cycle de vie, CATEGORY_MIGRATION_MAP §6.6).
 *
 * <ul>
 *   <li>{@link #UNCLASSIFIED} : aucun signal exploitable (ni catégorie d'origine, ni tag) ;</li>
 *   <li>{@link #AUTO_CLASSIFIED} : classée automatiquement vers un code V1 existant, sans ambiguïté ;</li>
 *   <li>{@link #VALIDATED} : catégorie choisie ou confirmée par un humain (ex. contribution manuelle) ;</li>
 *   <li>{@link #NEEDS_REVIEW} : signaux présents mais ambigus, conflictuels ou hors référentiel
 *       — aucune classification inventée ;</li>
 *   <li>{@link #REJECTED} : relecture terminée, l'élément ne sera pas classé ni publié.</li>
 * </ul>
 *
 * Seuls {@link #AUTO_CLASSIFIED} et {@link #VALIDATED} correspondent à une
 * activité réellement créée : {@code activity.primary_category_id} est
 * {@code NOT NULL} (V37), une activité sans catégorie ne peut pas exister.
 */
public enum ClassificationStatus {
    UNCLASSIFIED,
    AUTO_CLASSIFIED,
    VALIDATED,
    NEEDS_REVIEW,
    REJECTED;

    /** Vrai si une activité existe (ou peut exister) pour ce statut. */
    public boolean hasActivity() {
        return this == AUTO_CLASSIFIED || this == VALIDATED;
    }
}
