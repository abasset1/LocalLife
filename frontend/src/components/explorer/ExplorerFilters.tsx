import { useState } from "react";
import { toLocalDateString } from "./activityUtils";
import { NO_DATE_FILTER } from "./constants";
import type { Category } from "./types";

interface ExplorerFiltersProps {
    /** Catégories actives du référentiel : seules valeurs proposées. */
    categories: Category[];
    /** Codes des catégories cochées ; vide = toutes les catégories. */
    selectedCategoryCodes: string[];
    onSelectedCategoryCodesChange: (codes: string[]) => void;
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
 * relégués au menu). Catégories : uniquement celles du référentiel
 * (`GET /api/v1/categories`), en cases à cocher pour en choisir plusieurs
 * (demande d'Alex — remplace la liste déroulante à choix unique, alimentée
 * par la chaîne libre des activités chargées). Elles sont dans un panneau
 * replié par défaut, comme le filtre de date/période
 * (`.explorer-dates-panel`), pour ne pas surcharger le bandeau, sans rien
 * retirer de la fonctionnalité existante (LL-4005/LL-11003, critère de
 * régression CA-12). Un seul des deux panneaux est ouvert à la fois.
 *
 * Le bouton Carte/Liste (LL-EF-008) est conservé pour la même raison, mais
 * masqué en desktop (voir styles.css) : la liste y est désormais un panneau
 * permanent à côté de la carte, plus besoin d'y basculer explicitement.
 */
function ExplorerFilters({
    categories,
    selectedCategoryCodes,
    onSelectedCategoryCodesChange,
    selectedDate,
    selectedDateTo,
    onDateChange,
    onPeriodChange,
    onClearDate,
    onUseMyLocation,
    viewMode,
    onViewModeChange,
}: ExplorerFiltersProps) {
    const [openPanel, setOpenPanel] = useState<"categories" | "dates" | null>(null);
    const isCategoriesPanelOpen = openPanel === "categories";
    const isDatesPanelOpen = openPanel === "dates";
    const hasActiveDateFilter = selectedDate !== NO_DATE_FILTER;
    const todayString = toLocalDateString(new Date());

    // La date "À partir de" ne peut pas être antérieure à aujourd'hui.
    // `min` empêche déjà le sélecteur natif de proposer une date passée,
    // mais reste contournable (saisie manuelle sur certains navigateurs) —
    // on retombe donc sur aujourd'hui si une valeur antérieure arrive
    // malgré tout jusqu'ici.
    function handleDateChange(value: string) {
        onDateChange(value && value < todayString ? todayString : value);
    }

    function toggleCategory(code: string) {
        onSelectedCategoryCodesChange(
            selectedCategoryCodes.includes(code)
                ? selectedCategoryCodes.filter((selected) => selected !== code)
                : [...selectedCategoryCodes, code],
        );
    }

    return (
        <div className="explorer-filters">
            <div className="explorer-category-row">
                <div className="explorer-categories">
                    <button
                        aria-expanded={isCategoriesPanelOpen}
                        className={
                            selectedCategoryCodes.length > 0 ? "explorer-chip explorer-chip-active" : "explorer-chip"
                        }
                        onClick={() => setOpenPanel(isCategoriesPanelOpen ? null : "categories")}
                        type="button"
                    >
                        Catégories{selectedCategoryCodes.length > 0 ? ` (${selectedCategoryCodes.length})` : ""}
                    </button>
                    {isCategoriesPanelOpen && (
                        <div className="explorer-categories-panel" role="group" aria-label="Filtrer par catégorie">
                            {categories.length === 0 && (
                                <p className="explorer-categories-empty">Aucune catégorie disponible.</p>
                            )}
                            {categories.map((category) => (
                                <label className="explorer-category-option" key={category.code}>
                                    <input
                                        checked={selectedCategoryCodes.includes(category.code)}
                                        onChange={() => toggleCategory(category.code)}
                                        type="checkbox"
                                    />
                                    {category.label}
                                </label>
                            ))}
                            {selectedCategoryCodes.length > 0 && (
                                <button
                                    className="explorer-dates-clear"
                                    onClick={() => onSelectedCategoryCodesChange([])}
                                    type="button"
                                >
                                    Toutes les catégories
                                </button>
                            )}
                        </div>
                    )}
                </div>
                <button className="explorer-chip explorer-chip-location" onClick={onUseMyLocation} type="button">
                    ⌖ Autour de moi
                </button>
            </div>

            <div className="explorer-filters-secondary">
                <div className="explorer-dates">
                    <button
                        aria-expanded={isDatesPanelOpen}
                        className={hasActiveDateFilter ? "explorer-chip explorer-chip-active" : "explorer-chip"}
                        onClick={() => setOpenPanel(isDatesPanelOpen ? null : "dates")}
                        type="button"
                    >
                        📅 Dates
                    </button>
                    {isDatesPanelOpen && (
                        <div className="explorer-dates-panel" role="group" aria-label="Filtrer par date">
                            <label htmlFor="explorer-start-date">À partir de</label>
                            <input
                                id="explorer-start-date"
                                min={todayString}
                                onChange={(event) => handleDateChange(event.target.value)}
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
