import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import { apiFetch, describeErrorResponse } from "../../api/apiClient";
import type { ApiErrorBody, Category, UserPreferences } from "../explorer/types";
import AlertKeywordsInput from "./AlertKeywordsInput";
import CityField, { RADIUS_OPTIONS_KM, useCityPicker } from "./CityField";
import { Chip, ChipGroup, Notice, ProfileCard } from "./ProfileUi";

/** Miroir de `AlertResponse` (`/api/v1/users/me/alerts`). */
interface ActivityAlert {
    id: number;
    categoryCodes: string[];
    /** Nouveaux food trucks inclus (l'alerte peut n'avoir qu'eux). */
    includeFoodTrucks: boolean;
    city: string;
    latitude: number;
    longitude: number;
    radiusKm: number;
    notifyEmail: boolean;
    /** Notification push, activée depuis l'application mobile. */
    notifyPush: boolean;
    /** Mots-clés (Premium et Pro) : l'un d'eux doit apparaître dans le titre ou la description. */
    keywords?: string[];
}

const DEFAULT_ALERT_RADIUS_KM = 10;

/**
 * Alertes email : l'utilisateur reçoit un récapitulatif des nouvelles
 * activités publiées dans une ou plusieurs catégories, dans un rayon autour
 * d'une ville (envoi quotidien côté backend, voir ActivityAlertNotifier).
 * Le formulaire de création est prérempli avec les paramètres de recherche. Le nombre d'alertes est limité par
 * l'offre (1 en Gratuit, 10 en Premium/Pro) : à la limite, la création est désactivée et renvoie vers les offres.
 *
 * Mots-clés (Premium et Pro, `limits.alertKeywords`) : à la création ou plus tard (« Mots-clés » sur une alerte). Avec
 * des mots-clés, les catégories deviennent facultatives. Un compte gratuit voit l'offre Premium à la place du champ,
 * et peut toujours retirer les mots-clés d'une alerte qui en a.
 */
function ActivityAlertsSection({ maxAlerts = null, canUseKeywords = null }: {
    /** Nombre maximal d'alertes de l'offre (`limits.maxAlerts`), `null` s'il est inconnu. */
    maxAlerts?: number | null;
    /** Mots-clés autorisés (`limits.alertKeywords`), `null` s'il est inconnu. */
    canUseKeywords?: boolean | null;
} = {}) {
    const [categories, setCategories] = useState<Category[]>([]);
    const [alerts, setAlerts] = useState<ActivityAlert[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [loadError, setLoadError] = useState<string | null>(null);

    const [categoryCodes, setCategoryCodes] = useState<string[]>([]);
    const [includeFoodTrucks, setIncludeFoodTrucks] = useState(false);
    const [keywords, setKeywords] = useState<string[]>([]);
    /** Alerte dont les mots-clés sont en cours de modification, et leur brouillon. */
    const [editing, setEditing] = useState<{ id: number; keywords: string[] } | null>(null);
    const [isSavingKeywords, setIsSavingKeywords] = useState(false);
    const [keywordsError, setKeywordsError] = useState<string | null>(null);
    const [radiusKm, setRadiusKm] = useState(String(DEFAULT_ALERT_RADIUS_KM));
    const cityPicker = useCityPicker(() => setCreateSuccess(false));
    const [isCreating, setIsCreating] = useState(false);
    const [createError, setCreateError] = useState<string | null>(null);
    const [createSuccess, setCreateSuccess] = useState(false);
    const [deleteError, setDeleteError] = useState<string | null>(null);

    useEffect(() => {
        const abortController = new AbortController();

        async function load() {
            try {
                const [categoriesResponse, alertsResponse, preferencesResponse] = await Promise.all([
                    fetch("/api/v1/categories?locale=fr-FR", { signal: abortController.signal }),
                    apiFetch("/api/v1/users/me/alerts", { signal: abortController.signal }),
                    apiFetch("/api/v1/users/me/preferences", { signal: abortController.signal }),
                ]);
                if (categoriesResponse.ok) {
                    const data: Category[] = await categoriesResponse.json();
                    setCategories(
                        data
                            .filter((category) => category.active)
                            .sort((a, b) => a.label.localeCompare(b.label, "fr")),
                    );
                }
                if (!alertsResponse.ok) {
                    setLoadError(await describeErrorResponse(alertsResponse, "Impossible de charger tes alertes"));
                    return;
                }
                setAlerts((await alertsResponse.json()) as ActivityAlert[]);
                // Préremplissage avec les paramètres de recherche, s'il y en a.
                if (preferencesResponse.ok) {
                    const preferences: UserPreferences = await preferencesResponse.json();
                    setCategoryCodes(preferences.defaultCategoryCodes);
                    cityPicker.setSavedCity(preferences.defaultCity);
                    if (preferences.defaultRadiusKm !== null) {
                        setRadiusKm(String(preferences.defaultRadiusKm));
                    }
                }
            } catch {
                if (!abortController.signal.aborted) {
                    setLoadError("Impossible de contacter le serveur, réessaie plus tard.");
                }
            } finally {
                if (!abortController.signal.aborted) {
                    setIsLoading(false);
                }
            }
        }

        void load();

        return () => abortController.abort();
        // Chargement unique au montage (cityPicker.setSavedCity ne fait que poser l'état initial).
    }, []);

    function labelOf(code: string): string {
        return categories.find((category) => category.code === code)?.label ?? code;
    }

    function criteriaOf(alert: ActivityAlert): string[] {
        const labels = alert.categoryCodes.map(labelOf);
        if (alert.includeFoodTrucks) {
            labels.push("🚚 Food trucks");
        }
        return labels;
    }

    function toggleCategory(code: string) {
        setCreateSuccess(false);
        setCategoryCodes((selected) =>
            selected.includes(code) ? selected.filter((value) => value !== code) : [...selected, code],
        );
    }

    async function handleCreate(event: FormEvent) {
        event.preventDefault();
        setCreateError(null);
        setCreateSuccess(false);
        if (categoryCodes.length === 0 && !includeFoodTrucks && keywords.length === 0) {
            setCreateError("Choisis au moins une catégorie, un mot-clé ou les food trucks.");
            return;
        }
        setIsCreating(true);

        try {
            const response = await apiFetch("/api/v1/users/me/alerts", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    categoryCodes,
                    includeFoodTrucks,
                    city: cityPicker.city.trim() || null,
                    latitude: cityPicker.position?.latitude ?? null,
                    longitude: cityPicker.position?.longitude ?? null,
                    radiusKm: Number(radiusKm),
                    keywords,
                }),
            });
            if (response.ok) {
                const created: ActivityAlert = await response.json();
                setAlerts((current) => [...current, created]);
                setKeywords([]);
                setCreateSuccess(true);
            } else {
                const body = (await response.json().catch(() => null)) as ApiErrorBody | null;
                setCreateError(body?.message ?? "Impossible de créer cette alerte, réessaie.");
            }
        } catch {
            setCreateError("Impossible de contacter le serveur, réessaie plus tard.");
        } finally {
            setIsCreating(false);
        }
    }

    async function saveKeywords() {
        if (!editing) {
            return;
        }
        setIsSavingKeywords(true);
        setKeywordsError(null);
        try {
            const response = await apiFetch(`/api/v1/users/me/alerts/${editing.id}`, {
                method: "PATCH",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ keywords: editing.keywords }),
            });
            if (response.ok) {
                const updated: ActivityAlert = await response.json();
                setAlerts((current) => current.map((alert) => (alert.id === updated.id ? updated : alert)));
                setEditing(null);
            } else {
                const body = (await response.json().catch(() => null)) as ApiErrorBody | null;
                setKeywordsError(body?.message ?? "Impossible d'enregistrer les mots-clés, réessaie.");
            }
        } catch {
            setKeywordsError("Impossible de contacter le serveur, réessaie plus tard.");
        } finally {
            setIsSavingKeywords(false);
        }
    }

    async function handleDelete(id: number) {
        setDeleteError(null);
        try {
            const response = await apiFetch(`/api/v1/users/me/alerts/${id}`, { method: "DELETE" });
            if (response.ok || response.status === 404) {
                setAlerts((current) => current.filter((alert) => alert.id !== id));
            } else {
                setDeleteError("Impossible de supprimer cette alerte, réessaie.");
            }
        } catch {
            setDeleteError("Impossible de contacter le serveur, réessaie plus tard.");
        }
    }

    const isAtLimit = maxAlerts !== null && alerts.length >= maxAlerts;

    return (
        <ProfileCard
            icon="🔔"
            subtitle={
                <>
                    Chaque matin, les nouvelles activités et les nouveaux food trucks qui correspondent à tes critères.
                    Les notifications se règlent depuis l'application mobile.
                </>
            }
            title="Mes alertes"
            titleId="profile-alerts-title"
        >
            {isLoading && <p className="pp-hint">Chargement de tes alertes…</p>}
            {!isLoading && loadError && <Notice kind="error">{loadError}</Notice>}
            {!isLoading && !loadError && (
                <>
                    {maxAlerts !== null && (
                        <p className="pp-hint">{alerts.length} / {maxAlerts} {maxAlerts > 1 ? "alertes" : "alerte"}</p>
                    )}
                    {alerts.length === 0 ? (
                        <p className="pp-empty">Aucune alerte pour l'instant : crée ta première ci-dessous.</p>
                    ) : (
                        <ul className="pp-alerts">
                            {alerts.map((alert) => (
                                <li className="pp-alert" key={alert.id}>
                                    <div className="pp-alert-body">
                                        <div className="pp-tags">
                                            {criteriaOf(alert).map((criterion) => (
                                                <span className="pp-tag" key={criterion}>
                                                    {criterion}
                                                </span>
                                            ))}
                                            {alert.categoryCodes.length === 0 && !alert.includeFoodTrucks && (
                                                <span className="pp-tag">Toutes catégories</span>
                                            )}
                                            {(alert.keywords ?? []).map((keyword) => (
                                                <span className="pp-tag pp-tag-keyword" key={`kw-${keyword}`}>
                                                    « {keyword} »
                                                </span>
                                            ))}
                                        </div>
                                        <p className="pp-alert-place">
                                            📍 {alert.city} · {alert.radiusKm} km
                                        </p>
                                        <div className="pp-badges">
                                            {alert.notifyEmail && <span className="pp-badge">✉️ E-mail</span>}
                                            {alert.notifyPush && <span className="pp-badge">📱 Notification</span>}
                                        </div>
                                        {editing?.id === alert.id && (
                                            <div className="pp-keywords-edit">
                                                <AlertKeywordsInput
                                                    id={`alert-${alert.id}-keywords`}
                                                    label={`Ajouter un mot-clé à l'alerte ${alert.city}`}
                                                    keywords={editing.keywords}
                                                    onChange={(next) => setEditing({ id: alert.id, keywords: next })}
                                                    disabled={isSavingKeywords}
                                                />
                                                <div className="pp-actions">
                                                    <button
                                                        className="pp-button pp-button-primary"
                                                        disabled={isSavingKeywords}
                                                        onClick={() => void saveKeywords()}
                                                        type="button"
                                                    >
                                                        {isSavingKeywords ? "Enregistrement…" : "Enregistrer"}
                                                    </button>
                                                    <button
                                                        className="pp-button pp-button-secondary"
                                                        disabled={isSavingKeywords}
                                                        onClick={() => {
                                                            setEditing(null);
                                                            setKeywordsError(null);
                                                        }}
                                                        type="button"
                                                    >
                                                        Annuler
                                                    </button>
                                                </div>
                                                {keywordsError && <Notice kind="error">{keywordsError}</Notice>}
                                            </div>
                                        )}
                                    </div>
                                    {editing?.id !== alert.id
                                        && (canUseKeywords === true || (alert.keywords ?? []).length > 0) && (
                                        <button
                                            aria-label={`Modifier les mots-clés de l'alerte ${alert.city}`}
                                            className="pp-button pp-button-secondary"
                                            onClick={() => {
                                                setEditing({ id: alert.id, keywords: alert.keywords ?? [] });
                                                setKeywordsError(null);
                                            }}
                                            type="button"
                                        >
                                            Mots-clés
                                        </button>
                                    )}
                                    <button
                                        aria-label={`Supprimer l'alerte ${alert.city}`}
                                        className="pp-button pp-button-danger"
                                        onClick={() => void handleDelete(alert.id)}
                                        type="button"
                                    >
                                        Supprimer
                                    </button>
                                </li>
                            ))}
                        </ul>
                    )}
                    {deleteError && <Notice kind="error">{deleteError}</Notice>}

                    <form className="pp-form pp-subform" onSubmit={(event) => void handleCreate(event)}>
                        <h3 className="pp-subtitle">Nouvelle alerte</h3>
                        <ChipGroup legend="Catégories et/ou food trucks (facultatives avec des mots-clés)">
                            {categories.map((category) => (
                                <Chip
                                    checked={categoryCodes.includes(category.code)}
                                    key={category.code}
                                    onChange={() => toggleCategory(category.code)}
                                >
                                    {category.label}
                                </Chip>
                            ))}
                            <Chip
                                checked={includeFoodTrucks}
                                onChange={() => {
                                    setIncludeFoodTrucks((value) => !value);
                                    setCreateSuccess(false);
                                }}
                            >
                                🚚 Food trucks
                            </Chip>
                        </ChipGroup>

                        <div className="pp-field">
                            <label className="pp-label" htmlFor="alert-keywords">
                                Mots-clés <span className="pp-premium-tag">Premium</span>
                            </label>
                            {canUseKeywords === false ? (
                                <p className="pp-hint pp-keywords-locked">
                                    Ne reçois que ce qui t'intéresse vraiment : « jazz », « marché de Noël »,
                                    « randonnée »… Les mots-clés sont réservés à Premium,{" "}
                                    <strong>gratuit pour le moment</strong>.{" "}
                                    <a href="#profile-subscription-title">Passer à Premium</a>
                                </p>
                            ) : (
                                <AlertKeywordsInput
                                    id="alert-keywords"
                                    keywords={keywords}
                                    onChange={(next) => {
                                        setKeywords(next);
                                        setCreateSuccess(false);
                                    }}
                                />
                            )}
                        </div>

                        <CityField id="alert-city" label="Ville" picker={cityPicker} required />

                        <ChipGroup legend="Rayon">
                            {RADIUS_OPTIONS_KM.map((value) => (
                                <Chip
                                    checked={radiusKm === String(value)}
                                    key={value}
                                    name="alert-radius"
                                    onChange={() => setRadiusKm(String(value))}
                                    type="radio"
                                >
                                    {value} km
                                </Chip>
                            ))}
                        </ChipGroup>

                        <div className="pp-actions">
                            <button
                                className="pp-button pp-button-primary"
                                disabled={isCreating || cityPicker.blocksSubmit || isAtLimit}
                                type="submit"
                            >
                                {isCreating ? "Création…" : "Créer l'alerte"}
                            </button>
                            {isAtLimit && (
                                <p className="pp-hint">
                                    Tu as atteint la limite de {maxAlerts} {maxAlerts > 1 ? "alertes" : "alerte"} de ton
                                    offre : supprime une alerte ou{" "}
                                    <a href="#profile-subscription-title">passe à une offre supérieure</a>.
                                </p>
                            )}
                            {createSuccess && <Notice kind="success">Alerte créée.</Notice>}
                            {createError && <Notice kind="error">{createError}</Notice>}
                        </div>
                    </form>
                </>
            )}
        </ProfileCard>
    );
}

export default ActivityAlertsSection;
