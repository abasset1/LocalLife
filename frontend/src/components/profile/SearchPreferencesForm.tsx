import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import { apiFetch, describeErrorResponse } from "../../api/apiClient";
import { DEFAULT_SEARCH_RADIUS_KM } from "../explorer/constants";
import type { ApiErrorBody, Category, UserPreferences } from "../explorer/types";
import CityField, { RADIUS_OPTIONS_KM, useCityPicker } from "./CityField";
import { Chip, ChipGroup, Notice, ProfileCard } from "./ProfileUi";

/**
 * Paramètres utilisateur : catégories, ville et rayon appliqués par défaut
 * à l'ouverture de l'Explorer (voir App.tsx). La ville est choisie par
 * géolocalisation ou par saisie ; dans les deux cas le backend vérifie
 * qu'elle existe et renvoie son nom officiel. Laisser un champ vide
 * conserve le comportement standard de la carte (géolocalisation ou
 * Marseille, toutes catégories, 50 km).
 */
function SearchPreferencesForm() {
    const [categories, setCategories] = useState<Category[]>([]);
    const cityPicker = useCityPicker(() => setSaveSuccess(false));
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
                    cityPicker.setSavedCity(preferences.defaultCity);
                    setRadiusKm(preferences.defaultRadiusKm === null ? "" : String(preferences.defaultRadiusKm));
                    setCategoryCodes(preferences.defaultCategoryCodes);
                } else {
                    setLoadError(
                        await describeErrorResponse(preferencesResponse, "Impossible de charger tes paramètres"),
                    );
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
                    defaultCity: cityPicker.city.trim() || null,
                    defaultLatitude: cityPicker.position?.latitude ?? null,
                    defaultLongitude: cityPicker.position?.longitude ?? null,
                    defaultRadiusKm: radiusKm === "" ? null : Number(radiusKm),
                    defaultCategoryCodes: categoryCodes,
                }),
            });

            if (response.ok) {
                const preferences: UserPreferences = await response.json();
                cityPicker.setSavedCity(preferences.defaultCity);
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

    return (
        <ProfileCard
            icon="🧭"
            subtitle="Appliqués à l'ouverture de la carte. Laisse vide pour garder le comportement par défaut."
            title="Paramètres de recherche"
            titleId="profile-preferences-title"
        >
            {isLoading && <p className="pp-hint">Chargement de tes paramètres…</p>}
            {!isLoading && loadError && <Notice kind="error">{loadError}</Notice>}
            {!isLoading && !loadError && (
                <form className="pp-form" onSubmit={(event) => void handleSubmit(event)}>
                    <CityField id="preferences-city" label="Ville par défaut" picker={cityPicker} />

                    <ChipGroup legend="Rayon par défaut">
                        <Chip
                            checked={radiusKm === ""}
                            name="preferences-radius"
                            onChange={() => setRadiusKm("")}
                            type="radio"
                        >
                            Par défaut ({DEFAULT_SEARCH_RADIUS_KM} km)
                        </Chip>
                        {RADIUS_OPTIONS_KM.map((value) => (
                            <Chip
                                checked={radiusKm === String(value)}
                                key={value}
                                name="preferences-radius"
                                onChange={() => setRadiusKm(String(value))}
                                type="radio"
                            >
                                {value} km
                            </Chip>
                        ))}
                    </ChipGroup>

                    <ChipGroup hint="Aucune sélection = toutes les catégories." legend="Catégories par défaut">
                        {categories.length === 0 && <span className="pp-hint">Aucune catégorie disponible.</span>}
                        {categories.map((category) => (
                            <Chip
                                checked={categoryCodes.includes(category.code)}
                                key={category.code}
                                onChange={() => toggleCategory(category.code)}
                            >
                                {category.label}
                            </Chip>
                        ))}
                    </ChipGroup>

                    <div className="pp-actions">
                        <button
                            className="pp-button pp-button-primary"
                            disabled={isSaving || cityPicker.blocksSubmit}
                            type="submit"
                        >
                            {isSaving ? "Enregistrement…" : "Enregistrer"}
                        </button>
                        {saveSuccess && <Notice kind="success">Paramètres enregistrés.</Notice>}
                        {saveError && <Notice kind="error">{saveError}</Notice>}
                    </div>
                </form>
            )}
        </ProfileCard>
    );
}

export default SearchPreferencesForm;
