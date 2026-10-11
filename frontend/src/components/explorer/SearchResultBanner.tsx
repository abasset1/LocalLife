/** Recherche appliquée à la carte : une ville (la carte s'y est déplacée) ou un filtre texte. */
export type AppliedSearch =
    | { kind: "city"; city: string }
    | { kind: "text"; query: string; note?: string };

interface SearchResultBannerProps {
    /** Texte en cours de recherche, `null` hors recherche. */
    pendingQuery: string | null;
    search: AppliedSearch | null;
    /** Activités visibles sur la carte qui correspondent à la recherche. */
    resultCount: number;
    isLoadingActivities: boolean;
    onClear: () => void;
}

function plural(count: number, word: string): string {
    return `${count} ${word}${count > 1 ? "s" : ""}`;
}

/**
 * Bandeau sous la barre de recherche : recherche en cours, puis son résultat
 * (nombre d'activités sur la carte, ou message s'il n'y en a aucune), avec
 * « Effacer » pour revenir à toutes les activités.
 */
function SearchResultBanner({ pendingQuery, search, resultCount, isLoadingActivities, onClear }: SearchResultBannerProps) {
    if (pendingQuery !== null) {
        return (
            <div className="explorer-search-banner is-pending" role="status">
                <span aria-hidden="true" className="explorer-search-spinner" />
                <span>
                    Recherche de « <strong>{pendingQuery}</strong> »…
                </span>
            </div>
        );
    }
    if (!search) {
        return null;
    }

    let message: string;
    let isEmpty = false;
    if (isLoadingActivities) {
        message = search.kind === "city" ? `📍 ${search.city} — chargement des activités…` : "Chargement des activités…";
    } else if (search.kind === "city") {
        isEmpty = resultCount === 0;
        message = isEmpty
            ? `📍 ${search.city} — aucune activité pour le moment dans cette zone.`
            : `📍 ${search.city} — ${plural(resultCount, "activité")} sur la carte`;
    } else {
        isEmpty = resultCount === 0;
        message = isEmpty
            ? `Aucun résultat pour « ${search.query} » dans cette zone. Déplace la carte ou essaie un autre mot.`
            : `🔎 ${plural(resultCount, "résultat")} pour « ${search.query} »`;
    }

    return (
        <div className={`explorer-search-banner${isEmpty ? " is-empty" : ""}`} role="status">
            <span>
                {message}
                {search.kind === "text" && search.note && <span className="explorer-search-note"> {search.note}</span>}
            </span>
            <button className="explorer-search-banner-clear" onClick={onClear} type="button">
                Effacer ✕
            </button>
        </div>
    );
}

export default SearchResultBanner;
