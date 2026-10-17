import { RADIUS_OPTIONS_KM } from "../profile/CityField";

interface EmptyResultsHelpProps {
    /** Au moins une catégorie cochée. */
    hasCategoryFilter: boolean;
    /** Un filtre de date ou de période est actif. */
    hasDateFilter: boolean;
    /** Rayon actif (km), `null` = recherche sur la zone visible. */
    radiusKm: number | null;
    onClearCategories: () => void;
    onClearDate: () => void;
    onRadiusChange: (radiusKm: number) => void;
    onChooseCity: () => void;
}

/**
 * Spec P0-D : aucune activité pour les critères actuels. Seules les actions utiles à l'état courant
 * sont proposées (retirer un filtre actif, élargir un rayon actif, choisir une autre ville) ; rien
 * n'est changé sans un appui de l'utilisateur.
 */
function EmptyResultsHelp({
    hasCategoryFilter,
    hasDateFilter,
    radiusKm,
    onClearCategories,
    onClearDate,
    onRadiusChange,
    onChooseCity,
}: EmptyResultsHelpProps) {
    const widerRadiusKm = radiusKm === null ? undefined : RADIUS_OPTIONS_KM.find((value) => value > radiusKm);
    const hasCriteria = hasCategoryFilter || hasDateFilter || radiusKm !== null;

    return (
        <div className="explorer-empty-help">
            <p className="explorer-empty-help-text">
                {hasCriteria
                    ? "Aucune activité ne correspond à tes critères dans cette zone."
                    : "Aucune activité trouvée dans cette zone."}
            </p>
            <div className="explorer-empty-help-actions">
                {hasCategoryFilter && (
                    <button className="explorer-chip" onClick={onClearCategories} type="button">
                        Toutes les catégories
                    </button>
                )}
                {hasDateFilter && (
                    <button className="explorer-chip" onClick={onClearDate} type="button">
                        Toutes les dates
                    </button>
                )}
                {widerRadiusKm !== undefined && (
                    <button className="explorer-chip" onClick={() => onRadiusChange(widerRadiusKm)} type="button">
                        Élargir à {widerRadiusKm} km
                    </button>
                )}
                <button className="explorer-chip" onClick={onChooseCity} type="button">
                    📍 Autre ville
                </button>
            </div>
        </div>
    );
}

export default EmptyResultsHelp;
