interface ExplorerSearchProps {
    value: string;
    onChange: (value: string) => void;
}

/**
 * LL-EF-017, section 7 : recherche accessible sans jamais prendre le dessus
 * visuellement sur la carte — voir `.explorer-search` (styles.css) pour son
 * placement compact dans le bandeau du haut. Filtre client-side sur les
 * activités déjà chargées (voir `matchesSearchQuery`, activityUtils.ts).
 */
function ExplorerSearch({ value, onChange }: ExplorerSearchProps) {
    return (
        <div className="explorer-search">
            <span aria-hidden="true" className="explorer-search-icon">
                ⌕
            </span>
            <input
                aria-label="Rechercher une activité, un lieu ou une ville"
                onChange={(event) => onChange(event.target.value)}
                placeholder="Rechercher une activité, un lieu…"
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
        </div>
    );
}

export default ExplorerSearch;
