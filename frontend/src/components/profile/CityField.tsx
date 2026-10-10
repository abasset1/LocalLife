import { useState } from "react";
import { apiFetch } from "../../api/apiClient";
import type { ApiErrorBody } from "../explorer/types";

/** Rayons proposés (km) — bornés à 50 comme la recherche `/nearby`. */
export const RADIUS_OPTIONS_KM = [5, 10, 20, 30, 50];

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
export type CityStatus =
    | { kind: "idle" }
    | { kind: "checking" }
    | { kind: "valid"; message: string | null }
    | { kind: "invalid"; message: string };

export interface CityPicker {
    city: string;
    /** Position du navigateur ayant servi à choisir la ville (« Utiliser ma position ») ; null = ville saisie. */
    position: { latitude: number; longitude: number } | null;
    status: CityStatus;
    /** Ville déjà connue (enregistrée) : affichée comme valide, sans nouvelle vérification. */
    setSavedCity: (city: string | null) => void;
    handleChange: (value: string) => void;
    handleBlur: () => Promise<void>;
    handleUseMyPosition: () => void;
    /** Vrai tant qu'une vérification est en cours ou que la ville est introuvable. */
    blocksSubmit: boolean;
}

/**
 * Choix d'une ville par saisie ou par géolocalisation, partagé par les
 * paramètres de recherche et les alertes email. Dans les deux cas le backend
 * vérifie que la ville existe et renvoie son nom officiel.
 */
export function useCityPicker(onEdit?: () => void): CityPicker {
    const [city, setCity] = useState("");
    const [position, setPosition] = useState<{ latitude: number; longitude: number } | null>(null);
    const [status, setStatus] = useState<CityStatus>({ kind: "idle" });

    async function lookup(params: URLSearchParams): Promise<CityLookup | null> {
        setStatus({ kind: "checking" });
        try {
            const response = await apiFetch(`/api/v1/users/me/preferences/city?${params.toString()}`);
            if (response.ok) {
                return (await response.json()) as CityLookup;
            }
            const body = (await response.json().catch(() => null)) as ApiErrorBody | null;
            setStatus({ kind: "invalid", message: body?.message ?? "Impossible de vérifier cette ville." });
        } catch {
            setStatus({ kind: "invalid", message: "Impossible de contacter le serveur, réessaie plus tard." });
        }
        return null;
    }

    function setSavedCity(saved: string | null) {
        setCity(saved ?? "");
        setPosition(null);
        setStatus(saved ? { kind: "valid", message: null } : { kind: "idle" });
    }

    function handleChange(value: string) {
        setCity(value);
        setPosition(null);
        setStatus({ kind: "idle" });
        onEdit?.();
    }

    /** Ville saisie : vérifiée dès que le champ perd le focus, remplacée par son nom officiel. */
    async function handleBlur() {
        if (status.kind !== "idle" || city.trim() === "") {
            return;
        }
        const found = await lookup(new URLSearchParams({ name: city.trim() }));
        if (found) {
            setCity(found.city);
            setStatus({ kind: "valid", message: `Ville trouvée : ${found.city}` });
        }
    }

    /** Ville choisie par géolocalisation : seule la ville est retenue, jamais la position exacte. */
    function handleUseMyPosition() {
        if (!("geolocation" in navigator)) {
            setStatus({ kind: "invalid", message: "La géolocalisation n'est pas disponible sur cet appareil." });
            return;
        }
        setStatus({ kind: "checking" });
        navigator.geolocation.getCurrentPosition(
            (browserPosition) => {
                const coordinates = {
                    latitude: browserPosition.coords.latitude,
                    longitude: browserPosition.coords.longitude,
                };
                void (async () => {
                    const found = await lookup(new URLSearchParams({
                        latitude: String(coordinates.latitude),
                        longitude: String(coordinates.longitude),
                    }));
                    if (found) {
                        setCity(found.city);
                        setPosition(coordinates);
                        setStatus({ kind: "valid", message: `Ville détectée : ${found.city}` });
                        onEdit?.();
                    }
                })();
            },
            () => {
                setStatus({
                    kind: "invalid",
                    message: "Position indisponible : autorise la géolocalisation ou saisis ta ville.",
                });
            },
        );
    }

    return {
        city,
        position,
        status,
        setSavedCity,
        handleChange,
        handleBlur,
        handleUseMyPosition,
        blocksSubmit: status.kind === "checking" || status.kind === "invalid",
    };
}

interface CityFieldProps {
    id: string;
    label: string;
    picker: CityPicker;
    required?: boolean;
}

function CityField({ id, label, picker, required = false }: CityFieldProps) {
    const { status } = picker;
    return (
        <div className="pp-field">
            <label className="pp-label" htmlFor={id}>
                {label}
            </label>
            <div className="pp-city">
                <input
                    aria-describedby={`${id}-status`}
                    aria-invalid={status.kind === "invalid"}
                    className="pp-input"
                    id={id}
                    onBlur={() => void picker.handleBlur()}
                    onChange={(event) => picker.handleChange(event.target.value)}
                    placeholder="Ex. : Lyon"
                    required={required}
                    type="text"
                    value={picker.city}
                />
                <button
                    className="pp-button pp-button-secondary"
                    disabled={status.kind === "checking"}
                    onClick={picker.handleUseMyPosition}
                    type="button"
                >
                    📍 Ma position
                </button>
            </div>
            <span className="pp-city-status" id={`${id}-status`} role="status">
                {status.kind === "checking" && <span className="pp-hint">Vérification de la ville…</span>}
                {status.kind === "valid" && status.message && (
                    <span className="pp-status-ok">✓ {status.message}</span>
                )}
                {status.kind === "invalid" && <span className="pp-status-error">{status.message}</span>}
            </span>
        </div>
    );
}

export default CityField;
