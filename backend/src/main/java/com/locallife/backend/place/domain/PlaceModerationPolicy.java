package com.locallife.backend.place.domain;

/**
 * Évolution du statut d'un lieu permanent à l'import (ADR-0011, § 7.2.2, règles conservées le 14/10/2026), en
 * fonctions pures. Rien n'est publié ni dépublié sans regard humain :
 * <ul>
 *   <li>tout nouveau lieu est {@link PlaceStatus#PENDING} ;</li>
 *   <li>refus durable : un lieu {@link PlaceStatus#REJECTED} le reste, même réimporté ;</li>
 *   <li>retour en attente : un lieu {@link PlaceStatus#PUBLISHED} repasse {@link PlaceStatus#PENDING} si sa
 *       position bouge de plus de {@value #MAX_MOVE_METERS} m, si son adresse disparaît ou si ses types sortent de
 *       la liste blanche ;</li>
 *   <li>disparition de la source : {@link PlaceStatus#ARCHIVED}, sauf disparition massive
 *       ({@link #isSuspiciousDisappearance}).</li>
 * </ul>
 */
public final class PlaceModerationPolicy {

    /** Déplacement au-delà duquel un lieu publié repasse en modération. */
    public static final double MAX_MOVE_METERS = 100;

    /** Garde-fou de disparition massive : mêmes valeurs que pour les activités ({@code ImportService}). */
    public static final int MASS_ARCHIVE_MIN_COUNT = 10;

    public static final double MASS_ARCHIVE_MAX_RATIO = 0.5;

    private PlaceModerationPolicy() {
    }

    /**
     * Statut d'un lieu déjà connu, retrouvé dans la collecte.
     *
     * @param stillWhitelisted ses types sont toujours dans la liste blanche
     * @param movedMeters      déplacement depuis le dernier import ({@code 0} si inconnu)
     * @param addressLost      il avait une adresse et n'en a plus
     */
    public static PlaceStatus onReimport(
            PlaceStatus current, boolean stillWhitelisted, double movedMeters, boolean addressLost) {
        switch (current) {
            case REJECTED:
                return PlaceStatus.REJECTED;
            case PUBLISHED:
                boolean importantChange = !stillWhitelisted || movedMeters > MAX_MOVE_METERS || addressLost;
                return importantChange ? PlaceStatus.PENDING : PlaceStatus.PUBLISHED;
            case ARCHIVED:
                // Réapparu dans la source : de nouveau soumis à la modération, s'il est toujours retenu.
                return stillWhitelisted ? PlaceStatus.PENDING : PlaceStatus.ARCHIVED;
            case PENDING:
            default:
                // Jamais validé : sorti de la liste blanche, il quitte simplement la file d'attente.
                return stillWhitelisted ? PlaceStatus.PENDING : PlaceStatus.ARCHIVED;
        }
    }

    /** Statut d'un lieu connu absent de la collecte : seuls les lieux en attente ou publiés sont archivés. */
    public static PlaceStatus onDisappearance(PlaceStatus current) {
        return current == PlaceStatus.PENDING || current == PlaceStatus.PUBLISHED ? PlaceStatus.ARCHIVED : current;
    }

    /**
     * Disparition jugée suspecte (fichier tronqué, filtre mal saisi) : au moins {@value #MASS_ARCHIVE_MIN_COUNT}
     * lieux et strictement plus de la moitié des lieux en attente ou publiés de la source. L'archivage est alors
     * suspendu pour cette exécution.
     */
    public static boolean isSuspiciousDisappearance(int disappeared, int active) {
        return disappeared >= MASS_ARCHIVE_MIN_COUNT && disappeared > active * MASS_ARCHIVE_MAX_RATIO;
    }
}
