import { formatActivityLocation, formatCategoryLabels } from "./activityUtils";
import type { Activity, Category } from "./types";

interface ActivityPreviewProps {
    activity: Activity;
    categories: Category[];
    onClose: () => void;
    onOpenDetail: (activity: Activity) => void;
}

/**
 * LL-EF-017, section 4/9 : aperçu affiché à la sélection d'un marqueur ou
 * d'une ligne de liste — carte flottante au-dessus de la carte en desktop,
 * bottom sheet en mobile (même composant, positionnement purement CSS, voir
 * `.explorer-preview` dans styles.css), la carte restant visible derrière.
 * La fiche complète (`ActivityDetailModal`) ne s'ouvre qu'à la demande,
 * via "Voir l'activité".
 */
function ActivityPreview({ activity, categories, onClose, onOpenDetail }: ActivityPreviewProps) {
    const categoryLabels = formatCategoryLabels(activity, categories);

    return (
        <div className="explorer-preview" role="dialog" aria-labelledby="explorer-preview-title">
            <button aria-label="Fermer l'aperçu" className="explorer-preview-close" onClick={onClose} type="button">
                ✕
            </button>
            {activity.mainImageUrl && (
                <img alt="" className="explorer-preview-thumbnail" loading="lazy" src={activity.mainImageUrl} />
            )}
            <div className="explorer-preview-body">
                <span className="explorer-preview-date">
                    {activity.startDate
                        ? new Date(activity.startDate).toLocaleDateString("fr-FR", { dateStyle: "long" })
                        : "Date non précisée"}
                </span>
                <h3 id="explorer-preview-title">{activity.title}</h3>
                <p className="explorer-preview-meta">
                    📍 {formatActivityLocation(activity)}
                    {categoryLabels ? ` · ${categoryLabels}` : ""}
                </p>
                <button className="explorer-preview-cta" onClick={() => onOpenDetail(activity)} type="button">
                    Voir l'activité →
                </button>
            </div>
        </div>
    );
}

export default ActivityPreview;
