import { formatCategoryLabels, groupActivitiesByCity } from "./activityUtils";
import { UNKNOWN_ADDRESS_LABEL } from "./activityUtils";
import type { Activity, Category } from "./types";

interface ActivityListProps {
    activities: Activity[];
    categories: Category[];
    isLoading: boolean;
    error: string | null;
    onSelectActivity: (activity: Activity) => void;
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
function ActivityList({ activities, categories, isLoading, error, onSelectActivity }: ActivityListProps) {
    if (isLoading) {
        return <p className="explorer-status">Chargement des activités…</p>;
    }
    if (error) {
        return (
            <p className="explorer-status explorer-status-error" role="alert">
                {error}
            </p>
        );
    }
    if (activities.length === 0) {
        return <p className="explorer-status">Aucune activité trouvée dans cette zone.</p>;
    }

    return (
        <div className="explorer-list">
            {groupActivitiesByCity(activities).map(([city, cityActivities]) => (
                <section className="explorer-list-group" key={city}>
                    <h2 className="explorer-list-city">{city}</h2>
                    <ul className="explorer-list-items">
                        {cityActivities.map((activity) => (
                            <li key={activity.id}>
                                <button
                                    className="explorer-list-item"
                                    onClick={() => onSelectActivity(activity)}
                                    type="button"
                                >
                                    {activity.mainImageUrl && (
                                        <img
                                            alt=""
                                            className="explorer-list-item-thumbnail"
                                            loading="lazy"
                                            src={activity.mainImageUrl}
                                        />
                                    )}
                                    <span className="explorer-list-item-title">{activity.title}</span>
                                    <span className="explorer-list-item-address">
                                        {activity.address ?? UNKNOWN_ADDRESS_LABEL}
                                    </span>
                                    <span className="explorer-list-item-meta">
                                        {new Date(activity.startDate).toLocaleDateString("fr-FR")}
                                        {new Date(activity.startDate).getHours() !== 0
                                            || new Date(activity.startDate).getMinutes() !== 0
                                            ? ` · ${new Date(activity.startDate).toLocaleTimeString("fr-FR", {
                                                hour: "2-digit",
                                                minute: "2-digit",
                                            })}`
                                            : ""}
                                        {formatCategoryLabels(activity, categories)
                                            ? ` · ${formatCategoryLabels(activity, categories)}`
                                            : ""}
                                    </span>
                                </button>
                            </li>
                        ))}
                    </ul>
                </section>
            ))}
        </div>
    );
}

export default ActivityList;
