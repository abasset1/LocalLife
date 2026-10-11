import type { FormEvent } from "react";

interface ExplorerSearchProps {
    value: string;
    onChange: (value: string) => void;
    /** « Rechercher » ou Entrée : lance la recherche (ville, sinon activités). */
    onSubmit: () => void;
    /** Efface la saisie et la recherche en cours. */
    onClear: () => void;
    /** Recherche en cours : bouton et barre animés. */
    isSearching: boolean;
    /** Une recherche est appliquée à la carte : la barre est mise en évidence. */
    isActive: boolean;
}

/**
 * LL-EF-017, section 7 : barre de recherche du bandeau. La recherche est
 * explicite (bouton « Rechercher » ou Entrée) : la saisie seule ne change
 * rien à la carte. Voir `handleSearchSubmit` (App.tsx) pour son déroulé.
 */
function ExplorerSearch({ value, onChange, onSubmit, onClear, isSearching, isActive }: ExplorerSearchProps) {
    function handleSubmit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        if (!isSearching) {
            onSubmit();
        }
    }

    const className = ["explorer-search", isSearching && "is-searching", isActive && "is-active"]
        .filter(Boolean)
        .join(" ");

    return (
        <form aria-busy={isSearching} className={className} onSubmit={handleSubmit} role="search">
            <span aria-hidden="true" className="explorer-search-icon">
                ⌕
            </span>
            <input
                aria-label="Rechercher une activité, un lieu ou une ville"
                enterKeyHint="search"
                onChange={(event) => onChange(event.target.value)}
                placeholder="Une activité, un lieu ou une ville…"
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
    );
}

export default ExplorerSearch;
