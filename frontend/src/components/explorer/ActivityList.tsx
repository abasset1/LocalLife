import type { ReactNode } from "react";
import { APPROXIMATE_LOCATION_LABEL, formatCategoryLabels, groupActivitiesByCity, isApproximateLocation } from "./activityUtils";
import { UNKNOWN_ADDRESS_LABEL } from "./activityUtils";
import { formatDistance, hasCoordinates, UNKNOWN_PLACE_LABEL } from "./globalSearch";
import type { Activity, Category } from "./types";

interface ActivityListProps {
    activities: Activity[];
    categories: Category[];
    isLoading: boolean;
    error: string | null;
    onSelectActivity: (activity: Activity) => void;
    /** Message quand aucune activité ne s'affiche (par défaut : aucune dans la zone). */
    emptyMessage?: string;
    /**
     * Recherche globale (#92) : liste à plat dans l'ordre reçu (pertinence), avec la distance et la
     * ville de chaque résultat, au lieu du regroupement par ville trié par date.
     */
    ordered?: boolean;
    /** Libellé accessible de la liste (recherche : « Résultats de la recherche »). */
    label?: string;
    /** Erreur de chargement : relance la même recherche (« Réessayer »). */
    onRetry?: () => void;
    /** Aucune activité : aide affichée à la place de `emptyMessage` (actions utiles, spec P0-D). */
    emptyActions?: ReactNode;
}

function formatWhen(startDate: string): string {
    if (!startDate) {
        return "Date non précisée";
    }
    const date = new Date(startDate);
    const hasTime = date.getHours() !== 0 || date.getMinutes() !== 0;
    return date.toLocaleDateString("fr-FR")
        + (hasTime ? ` · ${date.toLocaleTimeString("fr-FR", { hour: "2-digit", minute: "2-digit" })}` : "");
}

function formatPlace(activity: Activity, ordered: boolean): string {
    if (!ordered) {
        return activity.address ?? UNKNOWN_ADDRESS_LABEL;
    }
    const place = [activity.address, activity.city].filter(Boolean).join(", ");
    if (place) {
        return place;
    }
    return hasCoordinates(activity) ? UNKNOWN_ADDRESS_LABEL : UNKNOWN_PLACE_LABEL;
}

/**
 * LL-EF-017 : panneau liste — colonne permanente à côté de la carte en
 * desktop, vue plein écran optionnelle en mobile (`viewMode === "list"`,
 * LL-EF-008, régression CA-12). Regroupement par ville puis tri par date,
 * inchangé depuis LL-EF-008/LL-10007. Une ligne sélectionnée ouvre
 * désormais l'aperçu (`ActivityPreview`) plutôt que la fiche complète
 * directement (section 8 du ticket : même comportement qu'un clic sur un
 * marqueur).
 */
function ActivityList({
    activities,
    categories,
    isLoading,
    error,
    onSelectActivity,
    emptyMessage = "Aucune activité trouvée dans cette zone.",
    ordered = false,
    label,
    onRetry,
    emptyActions,
}: ActivityListProps) {
    if (isLoading) {
        return <p className="explorer-status">Chargement des activités…</p>;
    }
    if (error) {
        return (
            <div className="explorer-status explorer-status-error explorer-status-card" role="alert">
                <span>{error}</span>
                {onRetry && (
                    <button className="explorer-chip" onClick={onRetry} type="button">
                        Réessayer
                    </button>
                )}
            </div>
        );
    }
    if (activities.length === 0) {
        return emptyActions ? (
            <div className="explorer-status" role="status">
                {emptyActions}
            </div>
        ) : (
            <p className="explorer-status">{emptyMessage}</p>
        );
    }

    function renderItem(activity: Activity) {
        const distance = ordered ? formatDistance(activity.distanceMeters) : null;
        const categoryLabels = formatCategoryLabels(activity, categories);
        return (
            <li key={activity.id}>
                <button className="explorer-list-item" onClick={() => onSelectActivity(activity)} type="button">
                    {activity.mainImageUrl && (
                        <img alt="" className="explorer-list-item-thumbnail" loading="lazy" src={activity.mainImageUrl} />
                    )}
                    <span className="explorer-list-item-title">{activity.title}</span>
                    <span className="explorer-list-item-address">
                        {formatPlace(activity, ordered)}
                        {isApproximateLocation(activity) ? ` · ${APPROXIMATE_LOCATION_LABEL}` : ""}
                        {distance ? ` · ${distance}` : ""}
                    </span>
                    <span className="explorer-list-item-meta">
                        {formatWhen(activity.startDate)}
                        {categoryLabels ? ` · ${categoryLabels}` : ""}
                    </span>
                </button>
            </li>
        );
    }

    if (ordered) {
        return (
            <div className="explorer-list">
                <ol aria-label={label} className="explorer-list-items explorer-list-ordered">
                    {activities.map(renderItem)}
                </ol>
            </div>
        );
    }

    return (
        <div className="explorer-list">
            {groupActivitiesByCity(activities).map(([city, cityActivities]) => (
                <section className="explorer-list-group" key={city}>
                    <h2 className="explorer-list-city">{city}</h2>
                    <ul className="explorer-list-items">
                        {cityActivities.map(renderItem)}
                    </ul>
                </section>
            ))}
        </div>
    );
}

export default ActivityList;
