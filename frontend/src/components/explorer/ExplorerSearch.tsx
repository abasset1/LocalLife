import { useState } from "react";
import type { FormEvent, KeyboardEvent } from "react";
import { formatPostalCodes, useCitySuggestions } from "./useCitySuggestions";
import type { CitySuggestion } from "./useCitySuggestions";

interface ExplorerSearchProps {
    value: string;
    onChange: (value: string) => void;
    /** « Rechercher » ou Entrée sans suggestion choisie : lance la recherche (ville, sinon activités). */
    onSubmit: () => void;
    /** Une commune proposée est choisie (avec la saisie, pour son code postal) : la carte s'y déplace. */
    onSelectCity: (city: CitySuggestion, query: string) => void;
    /** Efface la saisie et la recherche en cours. */
    onClear: () => void;
    /** Recherche en cours : bouton et barre animés. */
    isSearching: boolean;
    /** Une recherche est appliquée à la carte : la barre est mise en évidence. */
    isActive: boolean;
}

const LISTBOX_ID = "explorer-city-suggestions";

/**
 * LL-EF-017, section 7 : barre de recherche du bandeau. Pendant la saisie, des communes sont proposées
 * (nom, codes postaux, département) : flèches pour parcourir, Entrée ou clic pour choisir, Échap pour
 * fermer. La recherche libre reste explicite (bouton « Rechercher » ou Entrée) — voir
 * `handleSearchSubmit` (App.tsx).
 */
function ExplorerSearch({ value, onChange, onSubmit, onSelectCity, onClear, isSearching, isActive }: ExplorerSearchProps) {
    const [isFocused, setIsFocused] = useState(false);
    // Fermée après un choix, Échap ou un envoi ; rouverte dès que l'on tape.
    const [isDismissed, setIsDismissed] = useState(false);
    const [highlighted, setHighlighted] = useState(-1);
    const suggestions = useCitySuggestions(value, isFocused && !isDismissed && !isSearching);
    const isOpen = suggestions.length > 0;
    const activeIndex = highlighted < suggestions.length ? highlighted : -1;

    function handleChange(next: string) {
        onChange(next);
        setIsDismissed(false);
        setHighlighted(-1);
    }

    function choose(city: CitySuggestion) {
        setIsDismissed(true);
        setHighlighted(-1);
        onSelectCity(city, value);
    }

    function handleSubmit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        if (isOpen && activeIndex >= 0) {
            choose(suggestions[activeIndex]);
            return;
        }
        setIsDismissed(true);
        if (!isSearching) {
            onSubmit();
        }
    }

    function handleKeyDown(event: KeyboardEvent<HTMLInputElement>) {
        if (!isOpen) {
            return;
        }
        if (event.key === "ArrowDown") {
            event.preventDefault();
            setHighlighted((activeIndex + 1) % suggestions.length);
        } else if (event.key === "ArrowUp") {
            event.preventDefault();
            setHighlighted(activeIndex <= 0 ? suggestions.length - 1 : activeIndex - 1);
        } else if (event.key === "Escape") {
            event.preventDefault();
            setIsDismissed(true);
            setHighlighted(-1);
        }
    }

    const className = ["explorer-search", isSearching && "is-searching", isActive && "is-active", isOpen && "is-open"]
        .filter(Boolean)
        .join(" ");

    return (
        <div className="explorer-search-wrapper">
            <form aria-busy={isSearching} className={className} onSubmit={handleSubmit} role="search">
                <span aria-hidden="true" className="explorer-search-icon">
                    ⌕
                </span>
                <input
                    aria-activedescendant={activeIndex >= 0 ? `${LISTBOX_ID}-${activeIndex}` : undefined}
                    aria-autocomplete="list"
                    aria-controls={LISTBOX_ID}
                    aria-expanded={isOpen}
                    aria-label="Rechercher une activité, un lieu ou une ville"
                    autoComplete="off"
                    enterKeyHint="search"
                    onBlur={() => setIsFocused(false)}
                    onChange={(event) => handleChange(event.target.value)}
                    onFocus={() => setIsFocused(true)}
                    onKeyDown={handleKeyDown}
                    placeholder="Une activité, un lieu, une ville ou un code postal…"
                    role="combobox"
                    type="search"
                    value={value}
                />
                {value && !isSearching && (
                    <button aria-label="Effacer la recherche" className="explorer-search-clear" onClick={onClear} type="button">
                        ×
                    </button>
                )}
                <button className="explorer-search-submit" disabled={isSearching || !value.trim()} type="submit">
                    {isSearching ? (
                        <>
                            <span aria-hidden="true" className="explorer-search-spinner" />
                            Recherche…
                        </>
                    ) : (
                        "Rechercher"
                    )}
                </button>
                {isSearching && <span aria-hidden="true" className="explorer-search-progress" />}
            </form>
            {isOpen && (
                <ul aria-label="Villes proposées" className="explorer-search-suggestions" id={LISTBOX_ID} role="listbox">
                    {suggestions.map((city, index) => (
                        <li
                            aria-selected={index === activeIndex}
                            className={index === activeIndex ? "is-highlighted" : undefined}
                            id={`${LISTBOX_ID}-${index}`}
                            key={`${city.name}-${city.postalCodes[0] ?? index}`}
                            // Le choix se fait au clic, que déclenchent aussi le toucher et les technologies
                            // d'assistance (souvent sans mousedown) ; mousedown est seulement neutralisé
                            // pour que le champ garde le focus et que la liste reste ouverte jusqu'au clic.
                            onClick={() => choose(city)}
                            onMouseDown={(event) => event.preventDefault()}
                            onMouseEnter={() => setHighlighted(index)}
                            role="option"
                        >
                            <span aria-hidden="true">📍</span>
                            <span className="explorer-suggestion-name">{city.name}</span>
                            <span className="explorer-suggestion-meta">
                                {formatPostalCodes(city.postalCodes, value)}
                                {city.department ? ` · ${city.department}` : ""}
                            </span>
                        </li>
                    ))}
                </ul>
            )}
        </div>
    );
}

export default ExplorerSearch;
