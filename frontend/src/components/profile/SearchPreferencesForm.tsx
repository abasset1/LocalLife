import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import { apiFetch } from "../../api/apiClient";
import { DEFAULT_SEARCH_RADIUS_KM } from "../explorer/constants";
import type { ApiErrorBody, Category, UserPreferences } from "../explorer/types";

/** Rayons proposés (km) — bornés à 50 comme la recherche `/nearby`. */
const RADIUS_OPTIONS_KM = [5, 10, 20, 30, 50];

/** Réponse de `GET /api/v1/users/me/preferences/city` : ville existante, nom officiel. */
interface CityLookup {
    city: string;
    latitude: number;
    longitude: number;
}

/**
 * État de la ville saisie : `valid` = ville existante confirmée par le backend
 * (ou déjà enregistrée), `invalid` = introuvable — l'enregistrement est alors
 * bloqué tant que la saisie n'est pas corrigée.
 */
type CityStatus =
    | { kind: "idle" }
    | { kind: "checking" }
    | { kind: "valid"; message: string | null }
    | { kind: "invalid"; message: string };

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
    const [city, setCity] = useState("");
    /** Position du navigateur ayant servi à choisir la ville (« Utiliser ma position ») ; null = ville saisie. */
    const [cityPosition, setCityPosition] = useState<{ latitude: number; longitude: number } | null>(null);
    const [cityStatus, setCityStatus] = useState<CityStatus>({ kind: "idle" });
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
                    setCityStatus(preferences.defaultCity ? { kind: "valid", message: null } : { kind: "idle" });
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

    async function lookupCity(params: URLSearchParams): Promise<CityLookup | null> {
        setCityStatus({ kind: "checking" });
        try {
            const response = await apiFetch(`/api/v1/users/me/preferences/city?${params.toString()}`);
            if (response.ok) {
                return (await response.json()) as CityLookup;
            }
            const body = (await response.json().catch(() => null)) as ApiErrorBody | null;
            setCityStatus({ kind: "invalid", message: body?.message ?? "Impossible de vérifier cette ville." });
        } catch {
            setCityStatus({ kind: "invalid", message: "Impossible de contacter le serveur, réessaie plus tard." });
        }
        return null;
    }

    function handleCityChange(value: string) {
        setCity(value);
        setCityPosition(null);
        setCityStatus({ kind: "idle" });
        setSaveSuccess(false);
    }

    /** Ville saisie : vérifiée dès que le champ perd le focus, remplacée par son nom officiel. */
    async function handleCityBlur() {
        if (cityStatus.kind !== "idle" || city.trim() === "") {
            return;
        }
        const found = await lookupCity(new URLSearchParams({ name: city.trim() }));
        if (found) {
            setCity(found.city);
            setCityStatus({ kind: "valid", message: `Ville trouvée : ${found.city}` });
        }
    }

    /** Ville choisie par géolocalisation : seule la ville est retenue, jamais la position exacte. */
    function handleUseMyPosition() {
        if (!("geolocation" in navigator)) {
            setCityStatus({ kind: "invalid", message: "La géolocalisation n'est pas disponible sur cet appareil." });
            return;
        }
        setCityStatus({ kind: "checking" });
        navigator.geolocation.getCurrentPosition(
            (position) => {
                const coordinates = { latitude: position.coords.latitude, longitude: position.coords.longitude };
                void (async () => {
                    const found = await lookupCity(new URLSearchParams({
                        latitude: String(coordinates.latitude),
                        longitude: String(coordinates.longitude),
                    }));
                    if (found) {
                        setCity(found.city);
                        setCityPosition(coordinates);
                        setCityStatus({ kind: "valid", message: `Ville détectée : ${found.city}` });
                        setSaveSuccess(false);
                    }
                })();
            },
            () => {
                setCityStatus({
                    kind: "invalid",
                    message: "Position indisponible : autorise la géolocalisation ou saisis ta ville.",
                });
            },
        );
    }

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
                    defaultLatitude: cityPosition?.latitude ?? null,
                    defaultLongitude: cityPosition?.longitude ?? null,
                    defaultRadiusKm: radiusKm === "" ? null : Number(radiusKm),
                    defaultCategoryCodes: categoryCodes,
                }),
            });

            if (response.ok) {
                const preferences: UserPreferences = await response.json();
                setCity(preferences.defaultCity ?? "");
                setCityPosition(null);
                setCityStatus(preferences.defaultCity ? { kind: "valid", message: null } : { kind: "idle" });
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
            <div className="profile-preferences-city">
                <input
                    aria-describedby="preferences-city-status"
                    aria-invalid={cityStatus.kind === "invalid"}
                    id="preferences-city"
                    onBlur={() => void handleCityBlur()}
                    onChange={(event) => handleCityChange(event.target.value)}
                    placeholder="Ex. : Lyon"
                    type="text"
                    value={city}
                />
                <button
                    className="profile-preferences-locate"
                    disabled={cityStatus.kind === "checking"}
                    onClick={handleUseMyPosition}
                    type="button"
                >
                    📍 Utiliser ma position
                </button>
            </div>
            <span id="preferences-city-status" role="status">
                {cityStatus.kind === "checking" && (
                    <span className="profile-preferences-hint">Vérification de la ville…</span>
                )}
                {cityStatus.kind === "valid" && cityStatus.message && (
                    <span className="form-message form-message-success">✓ {cityStatus.message}</span>
                )}
                {cityStatus.kind === "invalid" && (
                    <span className="form-message form-message-error">{cityStatus.message}</span>
                )}
            </span>

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

            <button
                disabled={isSaving || cityStatus.kind === "checking" || cityStatus.kind === "invalid"}
                type="submit"
            >
                {isSaving ? "Enregistrement…" : "Enregistrer mes paramètres"}
            </button>
            {saveSuccess && <span className="form-message form-message-success">Paramètres enregistrés.</span>}
            {saveError && <span className="form-message form-message-error">{saveError}</span>}
        </form>
    );
}

export default SearchPreferencesForm;
