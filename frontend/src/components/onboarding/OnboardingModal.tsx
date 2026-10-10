import { useEffect, useRef, useState } from "react";
import type { FormEvent } from "react";
import { apiFetch, describeErrorResponse } from "../../api/apiClient";
import type { Category, UserPreferences } from "../explorer/types";
import CityField, { RADIUS_OPTIONS_KM, useCityPicker } from "../profile/CityField";
import { Chip, ChipGroup, Notice } from "../profile/ProfileUi";

const DEFAULT_RADIUS_KM = 10;

interface OnboardingModalProps {
    /** Catégories actives du référentiel, déjà chargées par l'Explorer. */
    categories: Category[];
    /** Préférences enregistrées : l'Explorer les applique aussitôt à la carte. */
    onPreferencesSaved: (preferences: UserPreferences) => void;
    /** Fin de l'accueil (alerte créée ou « Plus tard »). */
    onFinish: () => void;
    /** Seule autre sortie possible de l'étape obligatoire. */
    onLogout: () => void;
}

/**
 * Accueil de première connexion (l'utilisateur n'a encore jamais enregistré
 * de préférences, `configured: false`) :
 * 1. choix obligatoire de la ville, du rayon et d'au moins une catégorie —
 *    pas de bouton de fermeture ni de touche Échap, seule la déconnexion
 *    permet de sortir sans les enregistrer ;
 * 2. proposition facultative de créer une alerte e-mail préremplie avec ces
 *    préférences.
 */
function OnboardingModal({ categories, onPreferencesSaved, onFinish, onLogout }: OnboardingModalProps) {
    const [step, setStep] = useState<"preferences" | "alert">("preferences");
    const [saved, setSaved] = useState<UserPreferences | null>(null);

    // Étape 1
    const cityPicker = useCityPicker();
    const [radiusKm, setRadiusKm] = useState(DEFAULT_RADIUS_KM);
    const [categoryCodes, setCategoryCodes] = useState<string[]>([]);
    const [isSaving, setIsSaving] = useState(false);
    const [saveError, setSaveError] = useState<string | null>(null);

    // Étape 2
    const [alertCategoryCodes, setAlertCategoryCodes] = useState<string[]>([]);
    const [includeFoodTrucks, setIncludeFoodTrucks] = useState(false);
    const [isCreating, setIsCreating] = useState(false);
    const [alertError, setAlertError] = useState<string | null>(null);
    const [alertCreated, setAlertCreated] = useState(false);

    const dialogRef = useRef<HTMLDivElement>(null);
    useEffect(() => {
        // Focus sur le premier champ de l'étape affichée (lecteurs d'écran, clavier).
        dialogRef.current?.querySelector<HTMLElement>("input, button")?.focus();
    }, [step]);

    function toggle(codes: string[], code: string): string[] {
        return codes.includes(code) ? codes.filter((value) => value !== code) : [...codes, code];
    }

    async function savePreferences(event: FormEvent) {
        event.preventDefault();
        setSaveError(null);
        if (cityPicker.city.trim() === "") {
            setSaveError("Indique ta ville, ou utilise ta position.");
            return;
        }
        if (categoryCodes.length === 0) {
            setSaveError("Choisis au moins une catégorie.");
            return;
        }
        setIsSaving(true);
        try {
            const response = await apiFetch("/api/v1/users/me/preferences", {
                method: "PUT",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    defaultCity: cityPicker.city.trim(),
                    defaultLatitude: cityPicker.position?.latitude ?? null,
                    defaultLongitude: cityPicker.position?.longitude ?? null,
                    defaultRadiusKm: radiusKm,
                    defaultCategoryCodes: categoryCodes,
                }),
            });
            if (!response.ok) {
                setSaveError(await describeErrorResponse(response, "Impossible d'enregistrer tes préférences"));
                return;
            }
            const preferences: UserPreferences = await response.json();
            setSaved(preferences);
            setAlertCategoryCodes(preferences.defaultCategoryCodes);
            onPreferencesSaved(preferences);
            setStep("alert");
        } catch {
            setSaveError("Impossible de contacter le serveur, réessaie plus tard.");
        } finally {
            setIsSaving(false);
        }
    }

    async function createAlert() {
        if (!saved) {
            return;
        }
        setAlertError(null);
        if (alertCategoryCodes.length === 0 && !includeFoodTrucks) {
            setAlertError("Choisis au moins une catégorie, ou les food trucks.");
            return;
        }
        setIsCreating(true);
        try {
            const response = await apiFetch("/api/v1/users/me/alerts", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    categoryCodes: alertCategoryCodes,
                    includeFoodTrucks,
                    city: saved.defaultCity,
                    latitude: null,
                    longitude: null,
                    radiusKm: saved.defaultRadiusKm,
                }),
            });
            if (!response.ok) {
                setAlertError(await describeErrorResponse(response, "Impossible de créer l'alerte"));
                return;
            }
            setAlertCreated(true);
        } catch {
            setAlertError("Impossible de contacter le serveur, réessaie plus tard.");
        } finally {
            setIsCreating(false);
        }
    }

    const titleId = "onboarding-title";

    return (
        <div className="explorer-modal-overlay onboarding-overlay">
            <div
                aria-labelledby={titleId}
                aria-modal="true"
                className="explorer-modal-dialog onboarding-dialog"
                ref={dialogRef}
                role="dialog"
            >
                <header className="explorer-modal-header onboarding-header">
                    <div>
                        <p className="onboarding-step">Étape {step === "preferences" ? 1 : 2} sur 2</p>
                        <h2 id={titleId}>
                            {step === "preferences" ? "Bienvenue sur LocalLife 👋" : "Créer une alerte ?"}
                        </h2>
                    </div>
                </header>

                {step === "preferences" && (
                    <form className="pp-form onboarding-body" onSubmit={(event) => void savePreferences(event)}>
                        <p className="pp-hint onboarding-intro">
                            Pour commencer, dis-nous où et quoi chercher : la carte s'ouvrira toujours sur ces
                            préférences. Tu pourras les modifier à tout moment depuis ton profil.
                        </p>

                        <CityField id="onboarding-city" label="Ta ville" picker={cityPicker} required />

                        <ChipGroup legend="Rayon de recherche">
                            {RADIUS_OPTIONS_KM.map((value) => (
                                <Chip
                                    checked={radiusKm === value}
                                    key={value}
                                    name="onboarding-radius"
                                    onChange={() => setRadiusKm(value)}
                                    type="radio"
                                >
                                    {value} km
                                </Chip>
                            ))}
                        </ChipGroup>

                        <ChipGroup legend="Ce qui t'intéresse (au moins une catégorie)">
                            {categories.length === 0 && <span className="pp-hint">Chargement des catégories…</span>}
                            {categories.map((category) => (
                                <Chip
                                    checked={categoryCodes.includes(category.code)}
                                    key={category.code}
                                    onChange={() => setCategoryCodes((codes) => toggle(codes, category.code))}
                                >
                                    {category.label}
                                </Chip>
                            ))}
                        </ChipGroup>

                        {saveError && <Notice kind="error">{saveError}</Notice>}

                        <div className="onboarding-footer">
                            <button className="onboarding-link" onClick={onLogout} type="button">
                                Se déconnecter
                            </button>
                            <button
                                className="pp-button pp-button-primary"
                                disabled={isSaving || cityPicker.blocksSubmit}
                                type="submit"
                            >
                                {isSaving ? "Enregistrement…" : "Continuer"}
                            </button>
                        </div>
                    </form>
                )}

                {step === "alert" && saved && (
                    <div className="pp-form onboarding-body">
                        {alertCreated ? (
                            <Notice kind="success">
                                C'est prêt ! Tu recevras chaque matin par e-mail les nouveautés à moins de{" "}
                                {saved.defaultRadiusKm} km de {saved.defaultCity}.
                            </Notice>
                        ) : (
                            <>
                                <p className="pp-hint onboarding-intro">
                                    Reçois chaque matin par e-mail les nouvelles activités à moins de{" "}
                                    <strong>
                                        {saved.defaultRadiusKm} km de {saved.defaultCity}
                                    </strong>
                                    . C'est facultatif, et tu pourras gérer tes alertes depuis ton profil.
                                </p>
                                <ChipGroup legend="M'alerter pour">
                                    {categories.map((category) => (
                                        <Chip
                                            checked={alertCategoryCodes.includes(category.code)}
                                            key={category.code}
                                            onChange={() =>
                                                setAlertCategoryCodes((codes) => toggle(codes, category.code))
                                            }
                                        >
                                            {category.label}
                                        </Chip>
                                    ))}
                                    <Chip checked={includeFoodTrucks} onChange={() => setIncludeFoodTrucks((v) => !v)}>
                                        🚚 Food trucks
                                    </Chip>
                                </ChipGroup>
                                {alertError && <Notice kind="error">{alertError}</Notice>}
                            </>
                        )}

                        <div className="onboarding-footer">
                            {alertCreated ? (
                                <button className="pp-button pp-button-primary" onClick={onFinish} type="button">
                                    Découvrir la carte
                                </button>
                            ) : (
                                <>
                                    <button className="pp-button pp-button-secondary" onClick={onFinish} type="button">
                                        Plus tard
                                    </button>
                                    <button
                                        className="pp-button pp-button-primary"
                                        disabled={isCreating}
                                        onClick={() => void createAlert()}
                                        type="button"
                                    >
                                        {isCreating ? "Création…" : "Créer l'alerte"}
                                    </button>
                                </>
                            )}
                        </div>
                    </div>
                )}
            </div>
        </div>
    );
}

export default OnboardingModal;
