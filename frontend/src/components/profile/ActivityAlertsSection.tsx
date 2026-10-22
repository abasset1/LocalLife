import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import { apiFetch, describeErrorResponse } from "../../api/apiClient";
import type { ApiErrorBody, Category, UserPreferences } from "../explorer/types";
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
}

const DEFAULT_ALERT_RADIUS_KM = 10;

/**
 * Alertes email : l'utilisateur reçoit un récapitulatif des nouvelles
 * activités publiées dans une ou plusieurs catégories, dans un rayon autour
 * d'une ville (envoi quotidien côté backend, voir ActivityAlertNotifier).
 * Le formulaire de création est prérempli avec les paramètres de recherche. Le nombre d'alertes est limité par
 * l'offre (1 en Gratuit, 10 en Premium/Pro) : à la limite, la création est désactivée et renvoie vers les offres.
 */
function ActivityAlertsSection({ maxAlerts = null }: {
    /** Nombre maximal d'alertes de l'offre (`limits.maxAlerts`), `null` s'il est inconnu. */
    maxAlerts?: number | null;
} = {}) {
    const [categories, setCategories] = useState<Category[]>([]);
    const [alerts, setAlerts] = useState<ActivityAlert[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [loadError, setLoadError] = useState<string | null>(null);

    const [categoryCodes, setCategoryCodes] = useState<string[]>([]);
    const [includeFoodTrucks, setIncludeFoodTrucks] = useState(false);
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
        if (categoryCodes.length === 0 && !includeFoodTrucks) {
            setCreateError("Choisis au moins une catégorie, ou les food trucks.");
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
                }),
            });
            if (response.ok) {
                const created: ActivityAlert = await response.json();
                setAlerts((current) => [...current, created]);
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
                                        </div>
                                        <p className="pp-alert-place">
                                            📍 {alert.city} · {alert.radiusKm} km
                                        </p>
                                        <div className="pp-badges">
                                            {alert.notifyEmail && <span className="pp-badge">✉️ E-mail</span>}
                                            {alert.notifyPush && <span className="pp-badge">📱 Notification</span>}
                                        </div>
                                    </div>
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
                        <ChipGroup legend="Catégories et/ou food trucks">
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
