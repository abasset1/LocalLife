import { useState } from "react";
import { ALL_CATEGORIES, NO_DATE_FILTER } from "./constants";

interface ExplorerFiltersProps {
    categories: string[];
    selectedCategory: string;
    onSelectCategory: (category: string) => void;
    selectedDate: string;
    selectedDateTo: string;
    onDateChange: (date: string) => void;
    onPeriodChange: (period: string) => void;
    onClearDate: () => void;
    onUseMyLocation: () => void;
    viewMode: "map" | "list";
    onViewModeChange: (mode: "map" | "list") => void;
}

/**
 * LL-EF-017, section 6 : les filtres restent directement accessibles (pas
 * relégués au menu). Catégories affichées en liste déroulante (demande
 * d'Alex — remplace les chips d'une catégorie par ligne, trop nombreuses
 * une fois toutes les catégories réelles chargées) ; le filtre de
 * date/période — plus dense — reste dans un panneau replié par défaut
 * (`.explorer-dates-panel`) pour ne pas surcharger le bandeau, sans rien
 * retirer de la fonctionnalité existante (LL-4005/LL-11003, critère de
 * régression CA-12).
 *
 * Le bouton Carte/Liste (LL-EF-008) est conservé pour la même raison, mais
 * masqué en desktop (voir styles.css) : la liste y est désormais un panneau
 * permanent à côté de la carte, plus besoin d'y basculer explicitement.
 */
function ExplorerFilters({
    categories,
    selectedCategory,
    onSelectCategory,
    selectedDate,
    selectedDateTo,
    onDateChange,
    onPeriodChange,
    onClearDate,
    onUseMyLocation,
    viewMode,
    onViewModeChange,
}: ExplorerFiltersProps) {
    const [isDatesPanelOpen, setIsDatesPanelOpen] = useState(false);
    const hasActiveDateFilter = selectedDate !== NO_DATE_FILTER;

    return (
        <div className="explorer-filters">
            <div className="explorer-category-row">
                <label className="explorer-category-select-wrapper">
                    <span className="explorer-sr-only">Filtrer par catégorie</span>
                    <select
                        aria-label="Filtrer par catégorie"
                        className="explorer-category-select"
                        onChange={(event) => onSelectCategory(event.target.value)}
                        value={selectedCategory}
                    >
                        <option value={ALL_CATEGORIES}>Toutes les catégories</option>
                        {categories.map((category) => (
                            <option key={category} value={category}>
                                {category}
                            </option>
                        ))}
                    </select>
                </label>
                <button className="explorer-chip explorer-chip-location" onClick={onUseMyLocation} type="button">
                    ⌖ Autour de moi
                </button>
            </div>

            <div className="explorer-filters-secondary">
                <div className="explorer-dates">
                    <button
                        aria-expanded={isDatesPanelOpen}
                        className={hasActiveDateFilter ? "explorer-chip explorer-chip-active" : "explorer-chip"}
                        onClick={() => setIsDatesPanelOpen((open) => !open)}
                        type="button"
                    >
                        📅 Dates
                    </button>
                    {isDatesPanelOpen && (
                        <div className="explorer-dates-panel" role="group" aria-label="Filtrer par date">
                            <label htmlFor="explorer-start-date">À partir de</label>
                            <input
                                id="explorer-start-date"
                                onChange={(event) => onDateChange(event.target.value)}
                                type="date"
                                value={selectedDate}
                            />
                            <label htmlFor="explorer-period">Période</label>
                            <select
                                id="explorer-period"
                                onChange={(event) => onPeriodChange(event.target.value)}
                                value=""
                            >
                                <option value="">Toutes les périodes</option>
                                <option value="this-weekend">Ce weekend</option>
                                <option value="this-week">Cette semaine</option>
                                <option value="this-month">Ce mois-ci</option>
                                <option value="next-month">Prochain mois</option>
                            </select>
                            {hasActiveDateFilter && (
                                <button
                                    className="explorer-dates-clear"
                                    onClick={onClearDate}
                                    type="button"
                                >
                                    Effacer le filtre de date
                                </button>
                            )}
                        </div>
                    )}
                </div>

                {/* LL-EF-008 : conservé pour la vue plein écran mobile, masqué en desktop (styles.css). */}
                <div className="explorer-view-toggle" role="group" aria-label="Choix de l'affichage">
                    <button
                        aria-pressed={viewMode === "map"}
                        className={viewMode === "map" ? "explorer-chip explorer-chip-active" : "explorer-chip"}
                        onClick={() => onViewModeChange("map")}
                        type="button"
                    >
                        Carte
                    </button>
                    <button
                        aria-pressed={viewMode === "list"}
                        className={viewMode === "list" ? "explorer-chip explorer-chip-active" : "explorer-chip"}
                        onClick={() => onViewModeChange("list")}
                        type="button"
                    >
                        Liste
                    </button>
                </div>
            </div>

            {selectedDateTo !== NO_DATE_FILTER && (
                <p className="explorer-active-period-hint">
                    Période : du {selectedDate} au {selectedDateTo}
                </p>
            )}
        </div>
    );
}

export default ExplorerFilters;
