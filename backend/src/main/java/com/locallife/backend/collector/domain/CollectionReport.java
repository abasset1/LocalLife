package com.locallife.backend.collector.domain;

import java.util.List;

/**
 * Résultat d'une collecte, avec son exhaustivité : un collecteur qui s'arrête avant la fin réelle des données
 * de la source (limite de sécurité atteinte, curseur répété, échec au milieu de la pagination, réponse
 * incohérente) le dit ici au lieu de renvoyer silencieusement une liste tronquée.
 *
 * <p>Un échec <b>avant</b> toute donnée reçue reste une exception ({@code CollectorException}) : rien n'est
 * alors importé. Une collecte {@link Status#PARTIAL} porte les éléments reçus jusqu'à l'arrêt : ils peuvent
 * être créés ou mis à jour, mais leur absence ne prouve rien, donc rien n'est archivé sur cette base.
 *
 * @param activities éléments collectés, après les filtres du collecteur (lieu, région, créneau à venir)
 * @param status     {@link Status#COMPLETE} seulement si la fin réelle des données a été atteinte
 * @param pagesRead  pages lues avec succès (1 pour une source non paginée)
 * @param received   éléments bruts reçus de la source, avant les filtres du collecteur
 * @param stopReason raison de l'arrêt, en clair et sans donnée sensible (ni URL, ni clé)
 */
public record CollectionReport(
        List<CollectedActivity> activities, Status status, int pagesRead, int received, String stopReason) {

    public enum Status { COMPLETE, PARTIAL }

    public CollectionReport {
        activities = List.copyOf(activities);
    }

    /** Collecte d'une source non paginée, lue en entier. */
    public static CollectionReport complete(List<CollectedActivity> activities) {
        return new CollectionReport(activities, Status.COMPLETE, 1, activities.size(), "Source lue en entier.");
    }

    public boolean isComplete() {
        return status == Status.COMPLETE;
    }
}
