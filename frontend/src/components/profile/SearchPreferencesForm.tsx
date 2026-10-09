import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import { apiFetch } from "../../api/apiClient";
import { DEFAULT_SEARCH_RADIUS_KM } from "../explorer/constants";
import type { ApiErrorBody, Category, UserPreferences } from "../explorer/types";

/** Rayons proposés (km) — bornés à 50 comme la recherche `/nearby`. */
const RADIUS_OPTIONS_KM = [5, 10, 20, 30, 50];

/**
 * Paramètres utilisateur : catégories, ville et rayon appliqués par défaut
 * à l'ouverture de l'Explorer (voir App.tsx). Laisser un champ vide
 * conserve le comportement standard de la carte (géolocalisation ou
 * Marseille, toutes catégories, 50 km).
 */
function SearchPreferencesForm() {
    const [categories, setCategories] = useState<Category[]>([]);
    const [city, setCity] = useState("");
    const [radiusKm, setRadiusKm] = useState("");
    const [categoryCodes, setCategoryCodes] = useState<string[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [loadError, setLoadError] = useState<string | null>(null);
    const [isSaving, setIsSaving] = useState(false);
    const [saveError, setSaveError] = useState<string | null>(null);
    const [saveSuccess, setSaveSuccess] = useState(false);

    useEffect(() => {
        const abortController = new AbortController();

        async function load() {
            try {
                // Référentiel public : `fetch` direct (voir apiClient.ts) ; préférences : JWT requis.
                const [categoriesResponse, preferencesResponse] = await Promise.all([
                    fetch("/api/v1/categories?locale=fr-FR", { signal: abortController.signal }),
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
                if (preferencesResponse.ok) {
                    const preferences: UserPreferences = await preferencesResponse.json();
                    setCity(preferences.defaultCity ?? "");
                    setRadiusKm(preferences.defaultRadiusKm === null ? "" : String(preferences.defaultRadiusKm));
                    setCategoryCodes(preferences.defaultCategoryCodes);
                } else {
                    setLoadError("Impossible de charger tes paramètres, réessaie.");
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
    }, []);

    function toggleCategory(code: string) {
        setCategoryCodes((selected) =>
            selected.includes(code) ? selected.filter((value) => value !== code) : [...selected, code],
        );
    }

    async function handleSubmit(event: FormEvent) {
        event.preventDefault();
        setIsSaving(true);
        setSaveError(null);
        setSaveSuccess(false);

        try {
            const response = await apiFetch("/api/v1/users/me/preferences", {
                method: "PUT",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    defaultCity: city.trim() || null,
                    defaultRadiusKm: radiusKm === "" ? null : Number(radiusKm),
                    defaultCategoryCodes: categoryCodes,
                }),
            });

            if (response.ok) {
                const preferences: UserPreferences = await response.json();
                setCity(preferences.defaultCity ?? "");
                setCategoryCodes(preferences.defaultCategoryCodes);
                setSaveSuccess(true);
            } else {
                const body = (await response.json().catch(() => null)) as ApiErrorBody | null;
                setSaveError(body?.message ?? "Impossible d'enregistrer ces paramètres, réessaie.");
            }
        } catch {
            setSaveError("Impossible de contacter le serveur, réessaie plus tard.");
        } finally {
            setIsSaving(false);
        }
    }

    if (isLoading) {
        return <p className="activities-status">Chargement de tes paramètres…</p>;
    }
    if (loadError) {
        return <p className="activities-status activities-status-error">{loadError}</p>;
    }

    return (
        <form className="profile-form profile-preferences" onSubmit={(event) => void handleSubmit(event)}>
            <h2>Paramètres de recherche</h2>
            <p className="profile-preferences-hint">
                Appliqués à l'ouverture de la carte. Laisse un champ vide pour garder le comportement par défaut.
            </p>

            <label htmlFor="preferences-city">Ville par défaut</label>
            <input
                id="preferences-city"
                onChange={(event) => setCity(event.target.value)}
                placeholder="Ma position (ou Marseille)"
                type="text"
                value={city}
            />

            <label htmlFor="preferences-radius">Rayon par défaut</label>
            <select id="preferences-radius" onChange={(event) => setRadiusKm(event.target.value)} value={radiusKm}>
                <option value="">Par défaut ({DEFAULT_SEARCH_RADIUS_KM} km)</option>
                {RADIUS_OPTIONS_KM.map((value) => (
                    <option key={value} value={String(value)}>
                        {value} km
                    </option>
                ))}
            </select>

            <fieldset className="profile-preferences-categories">
                <legend>Catégories par défaut</legend>
                {categories.length === 0 && <p>Aucune catégorie disponible.</p>}
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
                <small>Aucune case cochée = toutes les catégories.</small>
            </fieldset>

            <button disabled={isSaving} type="submit">
                {isSaving ? "Enregistrement…" : "Enregistrer mes paramètres"}
            </button>
            {saveSuccess && <span className="form-message form-message-success">Paramètres enregistrés.</span>}
            {saveError && <span className="form-message form-message-error">{saveError}</span>}
        </form>
    );
}

export default SearchPreferencesForm;
