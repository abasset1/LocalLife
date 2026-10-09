import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import { apiFetch } from "../../api/apiClient";
import type { ApiErrorBody, Category, UserPreferences } from "../explorer/types";
import CityField, { RADIUS_OPTIONS_KM, useCityPicker } from "./CityField";

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

function channelsLabel(alert: ActivityAlert): string {
    const channels = [alert.notifyEmail && "e-mail", alert.notifyPush && "notification mobile"].filter(Boolean);
    return `par ${channels.join(" et ")}`;
}

const DEFAULT_ALERT_RADIUS_KM = 10;

/**
 * Alertes email : l'utilisateur reçoit un récapitulatif des nouvelles
 * activités publiées dans une ou plusieurs catégories, dans un rayon autour
 * d'une ville (envoi quotidien côté backend, voir ActivityAlertNotifier).
 * Le formulaire de création est prérempli avec les paramètres de recherche.
 */
function ActivityAlertsSection() {
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
                    setLoadError("Impossible de charger tes alertes, réessaie.");
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

    function criteriaOf(alert: ActivityAlert): string {
        const labels = alert.categoryCodes.map(labelOf);
        if (alert.includeFoodTrucks) {
            labels.push("Food trucks");
        }
        return labels.join(", ");
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

    if (isLoading) {
        return <p className="activities-status">Chargement de tes alertes…</p>;
    }
    if (loadError) {
        return <p className="activities-status activities-status-error">{loadError}</p>;
    }

    return (
        <section className="profile-form profile-preferences profile-alerts">
            <h2>Mes alertes e-mail</h2>
            <p className="profile-preferences-hint">
                Reçois chaque matin par e-mail les nouvelles activités et les nouveaux food trucks qui correspondent à
                tes critères.
                Les notifications se règlent depuis l'application mobile.
            </p>

            {alerts.length === 0 ? (
                <p className="profile-preferences-hint">Aucune alerte pour l'instant.</p>
            ) : (
                <ul className="profile-alerts-list">
                    {alerts.map((alert) => (
                        <li key={alert.id}>
                            <span>
                                <strong>{criteriaOf(alert)}</strong>
                                <br />
                                à moins de {alert.radiusKm} km de {alert.city}
                                <br />
                                <small>{channelsLabel(alert)}</small>
                            </span>
                            <button
                                aria-label={`Supprimer l'alerte ${alert.city}`}
                                className="profile-alerts-delete"
                                onClick={() => void handleDelete(alert.id)}
                                type="button"
                            >
                                Supprimer
                            </button>
                        </li>
                    ))}
                </ul>
            )}
            {deleteError && <span className="form-message form-message-error">{deleteError}</span>}

            <form className="profile-alerts-form" onSubmit={(event) => void handleCreate(event)}>
                <h3>Nouvelle alerte</h3>
                <fieldset className="profile-preferences-categories">
                    <legend>Catégories (une ou plusieurs) et/ou food trucks</legend>
                    {categories.map((category) => (
                        <label className="explorer-category-option" key={category.code}>
                            <input
                                checked={categoryCodes.includes(category.code)}
                                onChange={() => toggleCategory(category.code)}
                                type="checkbox"
                            />
                            {category.label}
                        </label>
                    ))}
                    <label className="explorer-category-option">
                        <input
                            checked={includeFoodTrucks}
                            onChange={() => {
                                setIncludeFoodTrucks((value) => !value);
                                setCreateSuccess(false);
                            }}
                            type="checkbox"
                        />
                        🚚 Food trucks
                    </label>
                </fieldset>

                <CityField id="alert-city" label="Ville" picker={cityPicker} required />

                <label htmlFor="alert-radius">Rayon</label>
                <select id="alert-radius" onChange={(event) => setRadiusKm(event.target.value)} value={radiusKm}>
                    {RADIUS_OPTIONS_KM.map((value) => (
                        <option key={value} value={String(value)}>
                            {value} km
                        </option>
                    ))}
                </select>

                <button disabled={isCreating || cityPicker.blocksSubmit} type="submit">
                    {isCreating ? "Création…" : "Créer l'alerte"}
                </button>
                {createSuccess && <span className="form-message form-message-success">Alerte créée.</span>}
                {createError && <span className="form-message form-message-error">{createError}</span>}
            </form>
        </section>
    );
}

export default ActivityAlertsSection;
