import type { FormEvent } from "react";

interface ExplorerSearchProps {
    value: string;
    onChange: (value: string) => void;
    /** Entrée : recherche le texte comme une ville et y déplace la carte. */
    onSubmit: () => void;
}

/**
 * LL-EF-017, section 7 : recherche accessible sans jamais prendre le dessus
 * visuellement sur la carte — voir `.explorer-search` (styles.css) pour son
 * placement compact dans le bandeau du haut. Pendant la saisie, filtre les
 * activités déjà chargées (voir `matchesSearchQuery`, activityUtils.ts) ;
 * Entrée cherche une ville et y déplace la carte (voir `handleSearchSubmit`, App.tsx).
 */
function ExplorerSearch({ value, onChange, onSubmit }: ExplorerSearchProps) {
    function handleSubmit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        onSubmit();
    }

    return (
        <form className="explorer-search" onSubmit={handleSubmit} role="search">
            <span aria-hidden="true" className="explorer-search-icon">
                ⌕
            </span>
            <input
                aria-label="Rechercher une activité, un lieu ou une ville"
                enterKeyHint="search"
                onChange={(event) => onChange(event.target.value)}
                placeholder="Rechercher une activité, un lieu ou une ville…"
                type="search"
                value={value}
            />
            {value && (
                <button
                    aria-label="Effacer la recherche"
                    className="explorer-search-clear"
                    onClick={() => onChange("")}
                    type="button"
                >
                    ×
                </button>
            )}
        </form>
    );
}

export default ExplorerSearch;
