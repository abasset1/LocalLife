/** Recherche appliquée : une ville choisie dans les suggestions (la carte s'y est déplacée) ou un texte. */
export type AppliedSearch = { kind: "city"; city: string } | { kind: "text"; query: string };

/** État de la recherche texte globale (#92), affiché par le bandeau. */
export type TextSearchStatus =
    | { status: "loading" }
    | { status: "error"; message: string }
    /** Texte refusé avant tout appel (trop court) : la carte garde ses activités. */
    | { status: "invalid"; message: string }
    | {
          status: "success";
          total: number;
          offset: number;
          /** Résultats de la page affichée. */
          count: number;
          /** Résultats de la page sans lieu connu : dans la liste seulement. */
          withoutLocation: number;
      };

interface SearchResultBannerProps {
    search: AppliedSearch | null;
    /** Recherche ville : activités de la carte dans la zone. */
    resultCount: number;
    isLoadingActivities: boolean;
    /** Recherche texte : état de `GET /api/v1/activities/search`. */
    textStatus: TextSearchStatus | null;
    /** Au moins un filtre catégorie ou date est actif (aide en cas d'absence de résultat). */
    hasActiveFilters: boolean;
    onClear: () => void;
    onRetry: () => void;
    onPreviousPage: () => void;
    onNextPage: () => void;
}

function plural(count: number, word: string): string {
    return `${count} ${word}${count > 1 ? "s" : ""}`;
}

/**
 * Bandeau sous la barre de recherche. Recherche texte : chargement, nombre de résultats et pagination,
 * aucun résultat ou erreur (avec « Réessayer »). Ville choisie : activités de la zone. « Effacer »
 * revient à la carte sans recherche.
 */
function SearchResultBanner({
    search,
    resultCount,
    isLoadingActivities,
    textStatus,
    hasActiveFilters,
    onClear,
    onRetry,
    onPreviousPage,
    onNextPage,
}: SearchResultBannerProps) {
    if (!search) {
        return null;
    }

    const clearButton = (
        <button className="explorer-search-banner-clear" onClick={onClear} type="button">
            Effacer ✕
        </button>
    );

    if (search.kind === "city") {
        const isEmpty = !isLoadingActivities && resultCount === 0;
        let message: string;
        if (isLoadingActivities) {
            message = `📍 ${search.city} — chargement des activités…`;
        } else if (isEmpty) {
            message = `📍 ${search.city} — aucune activité pour le moment dans cette zone.`;
        } else {
            message = `📍 ${search.city} — ${plural(resultCount, "activité")} sur la carte`;
        }
        return (
            <div className={`explorer-search-banner${isEmpty ? " is-empty" : ""}`} role="status">
                <span>{message}</span>
                {clearButton}
            </div>
        );
    }

    const query = <strong>{search.query}</strong>;

    if (textStatus === null || textStatus.status === "loading") {
        return (
            <div className="explorer-search-banner is-pending" role="status">
                <span aria-hidden="true" className="explorer-search-spinner" />
                <span>Recherche de « {query} »…</span>
                {clearButton}
            </div>
        );
    }

    if (textStatus.status === "error") {
        return (
            <div className="explorer-search-banner is-error" role="alert">
                <span>
                    La recherche de « {query} » a échoué : {textStatus.message}
                </span>
                <div className="explorer-search-banner-actions">
                    <button className="explorer-search-banner-clear" onClick={onRetry} type="button">
                        Réessayer
                    </button>
                    {clearButton}
                </div>
            </div>
        );
    }

    if (textStatus.status === "invalid") {
        return (
            <div className="explorer-search-banner is-empty" role="status">
                <span>
                    « {query} » : {textStatus.message}
                </span>
                {clearButton}
            </div>
        );
    }

    const { total, offset, count, withoutLocation } = textStatus;

    if (total === 0) {
        return (
            <div className="explorer-search-banner is-empty" role="status">
                <span>
                    Aucun résultat pour « {query} ».{" "}
                    {hasActiveFilters ? "Essaie un autre mot ou retire des filtres." : "Essaie un autre mot."}
                </span>
                {clearButton}
            </div>
        );
    }

    const first = count > 0 ? offset + 1 : 0;
    const last = offset + count;
    const hasPrevious = offset > 0;
    const hasNext = last < total;

    return (
        <div className="explorer-search-banner" role="status">
            <span>
                🔎 {plural(total, "résultat")} pour « {query} »
                {(hasPrevious || hasNext) && count > 0 && (
                    <span className="explorer-search-note">
                        {" "}
                        · {first}–{last} sur {total}
                    </span>
                )}
                {count === 0 && <span className="explorer-search-note"> · page vide</span>}
                {withoutLocation > 0 && (
                    <span className="explorer-search-note">
                        {" "}
                        · {withoutLocation === 1 ? "1 sans lieu précis" : `${withoutLocation} sans lieu précis`}, dans
                        la liste seulement
                    </span>
                )}
            </span>
            <div className="explorer-search-banner-actions">
                {(hasPrevious || hasNext) && (
                    <nav aria-label="Pages de résultats" className="explorer-search-pagination">
                        <button disabled={!hasPrevious} onClick={onPreviousPage} type="button">
                            ‹ Précédents
                        </button>
                        <button disabled={!hasNext} onClick={onNextPage} type="button">
                            Suivants ›
                        </button>
                    </nav>
                )}
                {clearButton}
            </div>
        </div>
    );
}

export default SearchResultBanner;
