import { useState } from "react";
import { formatActivityLocation, formatCategoryLabels } from "./activityUtils";
import type { Activity, Category } from "./types";

interface ClusterActivitiesPopupProps {
    /** Activités du groupe cliqué, déjà triées. */
    activities: Activity[];
    categories: Category[];
    onOpenDetail: (activity: Activity) => void;
}

function formatDate(startDate: string): string {
    return startDate
        ? new Date(startDate).toLocaleDateString("fr-FR", { dateStyle: "long" })
        : "Date non précisée";
}

/**
 * Contenu de la popup ouverte au clic sur une pastille de la carte (groupe de marqueurs), comme sur la
 * carte Finishers : une activité à la fois, et des flèches pour passer d'une activité à l'autre
 * (« 2 / 5 »), au lieu d'un zoom sur la zone du groupe.
 */
function ClusterActivitiesPopup({ activities, categories, onOpenDetail }: ClusterActivitiesPopupProps) {
    const [index, setIndex] = useState(0);
    const total = activities.length;
    const activity = activities[Math.min(index, total - 1)];
    if (!activity) {
        return null;
    }
    const categoryLabels = formatCategoryLabels(activity, categories);
    const move = (step: number) => setIndex((current) => (current + step + total) % total);

    return (
        <div aria-label="Activités de ce groupe" className="explorer-cluster-popup" role="group">
            {activity.mainImageUrl && (
                <img alt="" className="explorer-cluster-popup-thumbnail" loading="lazy" src={activity.mainImageUrl} />
            )}
            <span className="explorer-cluster-popup-date">{formatDate(activity.startDate)}</span>
            <strong className="explorer-cluster-popup-title">{activity.title}</strong>
            <span className="explorer-cluster-popup-meta">
                📍 {formatActivityLocation(activity)}
                {categoryLabels ? ` · ${categoryLabels}` : ""}
            </span>
            <button className="explorer-cluster-popup-cta" onClick={() => onOpenDetail(activity)} type="button">
                Voir l'activité →
            </button>
            {total > 1 && (
                <div className="explorer-cluster-popup-nav">
                    <button aria-label="Activité précédente" onClick={() => move(-1)} type="button">‹</button>
                    <span aria-live="polite">{index + 1} / {total}</span>
                    <button aria-label="Activité suivante" onClick={() => move(1)} type="button">›</button>
                </div>
            )}
        </div>
    );
}

export default ClusterActivitiesPopup;
