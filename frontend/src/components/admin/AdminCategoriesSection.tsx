import { FormEvent, useEffect, useState } from "react";
import { apiFetch, describeErrorResponse } from "../../api/apiClient";

/**
 * Référentiel des catégories et des mots-clés de classification (`/api/v1/admin/categories`). Ajout et
 * désactivation seulement, jamais de suppression : une catégorie désactivée n'est plus proposée ni attribuée par la
 * classification (les activités qui la portent la gardent) ; un mot-clé désactivé n'est plus lu par la
 * classification. Les changements s'appliquent aux prochains imports.
 */

export type KeywordScope = "ALL" | "SOURCE";

export interface AdminKeyword {
    id: number;
    keyword: string;
    scope: KeywordScope;
    active: boolean;
}

export interface AdminCategory {
    code: string;
    label: string;
    description: string | null;
    active: boolean;
    keywords: AdminKeyword[];
}

const SCOPE_LABELS: Record<KeywordScope, string> = {
    ALL: "Titres et sources",
    SOURCE: "Sources seulement",
};

interface KeywordDraft {
    keyword: string;
    scope: KeywordScope;
}

const EMPTY_DRAFT: KeywordDraft = { keyword: "", scope: "ALL" };

function normalize(value: string): string {
    return value.toLocaleLowerCase("fr").trim();
}

function AdminCategoriesSection() {
    const [categories, setCategories] = useState<AdminCategory[] | null>(null);
    const [loadError, setLoadError] = useState<string | null>(null);
    const [refreshKey, setRefreshKey] = useState(0);
    const [busy, setBusy] = useState(false);
    const [message, setMessage] = useState<{ text: string; error: boolean } | null>(null);
    const [newCategory, setNewCategory] = useState({ code: "", label: "", description: "" });
    const [drafts, setDrafts] = useState<Record<string, KeywordDraft>>({});
    const [search, setSearch] = useState("");

    useEffect(() => {
        const controller = new AbortController();
        apiFetch("/api/v1/admin/categories", { signal: controller.signal })
            .then(async (response) => {
                if (!response.ok) {
                    throw new Error(await describeErrorResponse(response, "Chargement des catégories impossible"));
                }
                setCategories((await response.json()) as AdminCategory[]);
                setLoadError(null);
            })
            .catch((error: unknown) => {
                if (!controller.signal.aborted) {
                    setLoadError(error instanceof Error ? error.message : "Erreur de chargement");
                }
            });
        return () => controller.abort();
    }, [refreshKey]);

    /** Envoie une écriture, affiche son résultat et recharge la liste en cas de succès. */
    async function send(url: string, method: string, body: unknown, context: string, success: string) {
        setBusy(true);
        try {
            const response = await apiFetch(url, {
                method,
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(body),
            });
            if (!response.ok) {
                setMessage({ text: await describeErrorResponse(response, context), error: true });
                return false;
            }
            setMessage({ text: success, error: false });
            setRefreshKey((key) => key + 1);
            return true;
        } catch {
            setMessage({ text: `${context} : le serveur ne répond pas.`, error: true });
            return false;
        } finally {
            setBusy(false);
        }
    }

    async function createCategory(event: FormEvent) {
        event.preventDefault();
        const created = await send(
            "/api/v1/admin/categories",
            "POST",
            newCategory,
            "Ajout de la catégorie impossible",
            `Catégorie « ${newCategory.label.trim()} » ajoutée.`,
        );
        if (created) {
            setNewCategory({ code: "", label: "", description: "" });
        }
    }

    function toggleCategory(category: AdminCategory) {
        if (category.active && !window.confirm(
            `Désactiver « ${category.label} » ? Elle ne sera plus proposée ni attribuée aux nouvelles activités.`,
        )) {
            return;
        }
        void send(
            `/api/v1/admin/categories/${encodeURIComponent(category.code)}`,
            "PATCH",
            { active: !category.active },
            "Modification de la catégorie impossible",
            `Catégorie « ${category.label} » ${category.active ? "désactivée" : "réactivée"}.`,
        );
    }

    async function addKeyword(event: FormEvent, category: AdminCategory) {
        event.preventDefault();
        const draft = drafts[category.code] ?? EMPTY_DRAFT;
        const added = await send(
            `/api/v1/admin/categories/${encodeURIComponent(category.code)}/keywords`,
            "POST",
            draft,
            "Ajout du mot-clé impossible",
            `Mot-clé « ${draft.keyword.trim()} » ajouté à ${category.label}.`,
        );
        if (added) {
            setDrafts((current) => ({ ...current, [category.code]: EMPTY_DRAFT }));
        }
    }

    function toggleKeyword(keyword: AdminKeyword) {
        void send(
            `/api/v1/admin/categories/keywords/${keyword.id}`,
            "PATCH",
            { active: !keyword.active },
            "Modification du mot-clé impossible",
            `Mot-clé « ${keyword.keyword} » ${keyword.active ? "désactivé" : "réactivé"}.`,
        );
    }

    function updateDraft(code: string, update: Partial<KeywordDraft>) {
        setDrafts((current) => ({ ...current, [code]: { ...(current[code] ?? EMPTY_DRAFT), ...update } }));
    }

    const query = normalize(search);
    const visibleCategories = (categories ?? [])
        .map((category) => ({
            category,
            keywords: query
                ? category.keywords.filter((keyword) => normalize(keyword.keyword).includes(query))
                : category.keywords,
        }))
        .filter(({ category, keywords }) =>
            !query || keywords.length > 0 || normalize(category.label).includes(query)
                || normalize(category.code).includes(query));

    return (
        <section aria-labelledby="admin-categories-title">
            <h2 id="admin-categories-title">Catégories et mots-clés</h2>
            <p>
                Les mots-clés servent à classer automatiquement les activités importées. Rien n'est supprimé : une
                catégorie ou un mot-clé désactivé peut être réactivé. Les changements s'appliquent aux prochains
                imports.
            </p>

            <form aria-label="Nouvelle catégorie" className="admin-places-filters" onSubmit={(event) => void createCategory(event)}>
                <label>
                    Code
                    <input
                        maxLength={50}
                        onChange={(event) => setNewCategory({ ...newCategory, code: event.target.value.toUpperCase() })}
                        placeholder="ex. BIEN_ETRE"
                        required
                        value={newCategory.code}
                    />
                </label>
                <label>
                    Libellé
                    <input
                        maxLength={255}
                        onChange={(event) => setNewCategory({ ...newCategory, label: event.target.value })}
                        placeholder="ex. Bien-être"
                        required
                        value={newCategory.label}
                    />
                </label>
                <label>
                    Description
                    <input
                        onChange={(event) => setNewCategory({ ...newCategory, description: event.target.value })}
                        value={newCategory.description}
                    />
                </label>
                <button className="admin-approve-button" disabled={busy} type="submit">
                    Ajouter la catégorie
                </button>
            </form>

            <div className="admin-places-filters">
                <label>
                    Rechercher un mot-clé
                    <input onChange={(event) => setSearch(event.target.value)} type="search" value={search} />
                </label>
            </div>

            {message && (
                <p className={message.error ? "activities-status activities-status-error" : "activities-status"} role="status">
                    {message.text}
                </p>
            )}
            {loadError && <p className="activities-status activities-status-error" role="alert">{loadError}</p>}
            {categories && visibleCategories.length === 0 && <p className="activities-status">Aucun résultat.</p>}

            <ul className="admin-activity-list">
                {visibleCategories.map(({ category, keywords }) => {
                    const activeCount = category.keywords.filter((keyword) => keyword.active).length;
                    const draft = drafts[category.code] ?? EMPTY_DRAFT;
                    return (
                        <li className="admin-activity-card" key={category.code}>
                            <div className="admin-activity-header">
                                <h3>
                                    {category.label} <code>{category.code}</code>
                                </h3>
                                <span
                                    className={category.active
                                        ? "admin-status-badge admin-status-published"
                                        : "admin-status-badge admin-status-rejected"}
                                >
                                    {category.active ? "Active" : "Désactivée"}
                                </span>
                            </div>
                            {category.description && <p className="admin-activity-description">{category.description}</p>}
                            <div className="admin-activity-actions">
                                <button
                                    aria-label={`${category.active ? "Désactiver" : "Réactiver"} la catégorie ${category.label}`}
                                    className={category.active ? "admin-reject-button" : "admin-approve-button"}
                                    disabled={busy}
                                    onClick={() => toggleCategory(category)}
                                    type="button"
                                >
                                    {category.active ? "Désactiver" : "Réactiver"}
                                </button>
                            </div>

                            <details className="admin-category-keywords" open={query !== ""}>
                                <summary>
                                    Mots-clés : {activeCount} actif(s) sur {category.keywords.length}
                                </summary>
                                <ul className="admin-keyword-list">
                                    {keywords.map((keyword) => (
                                        <li className={keyword.active ? "admin-keyword" : "admin-keyword admin-keyword-inactive"} key={keyword.id}>
                                            <span>{keyword.keyword}</span>
                                            {keyword.scope === "SOURCE" && <small>{SCOPE_LABELS.SOURCE}</small>}
                                            <button
                                                aria-label={`${keyword.active ? "Désactiver" : "Réactiver"} le mot-clé ${keyword.keyword}`}
                                                disabled={busy}
                                                onClick={() => toggleKeyword(keyword)}
                                                title={keyword.active ? "Désactiver" : "Réactiver"}
                                                type="button"
                                            >
                                                {keyword.active ? "×" : "↺"}
                                            </button>
                                        </li>
                                    ))}
                                </ul>
                                <form
                                    aria-label={`Nouveau mot-clé pour ${category.label}`}
                                    className="admin-places-bulk"
                                    onSubmit={(event) => void addKeyword(event, category)}
                                >
                                    <input
                                        aria-label={`Mot-clé à ajouter à ${category.label}`}
                                        maxLength={100}
                                        onChange={(event) => updateDraft(category.code, { keyword: event.target.value })}
                                        placeholder="Nouveau mot-clé"
                                        required
                                        value={draft.keyword}
                                    />
                                    <select
                                        aria-label={`Portée du mot-clé pour ${category.label}`}
                                        onChange={(event) => updateDraft(category.code, { scope: event.target.value as KeywordScope })}
                                        value={draft.scope}
                                    >
                                        {(Object.keys(SCOPE_LABELS) as KeywordScope[]).map((scope) => (
                                            <option key={scope} value={scope}>
                                                {SCOPE_LABELS[scope]}
                                            </option>
                                        ))}
                                    </select>
                                    <button className="admin-approve-button" disabled={busy} type="submit">
                                        Ajouter
                                    </button>
                                </form>
                            </details>
                        </li>
                    );
                })}
            </ul>
        </section>
    );
}

export default AdminCategoriesSection;
