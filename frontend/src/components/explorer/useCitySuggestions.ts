import { useEffect, useState } from "react";

/** Commune proposée (`GET /api/v1/geocoding/city-suggestions`). */
export interface CitySuggestion {
    name: string;
    postalCodes: string[];
    department: string | null;
    latitude: number;
    longitude: number;
}

const MIN_QUERY_LENGTH = 2;
const DEBOUNCE_MS = 250;

/**
 * Codes postaux lisibles : « 69003 », ou « 69001 – 69009 » pour une ville qui en a plusieurs. Si la
 * saisie est l'un de ces codes postaux, c'est lui qui est affiché.
 */
export function formatPostalCodes(postalCodes: string[], query = ""): string {
    const typed = query.trim();
    if (postalCodes.includes(typed)) {
        return typed;
    }
    if (postalCodes.length === 0) {
        return "";
    }
    if (postalCodes.length === 1) {
        return postalCodes[0];
    }
    return `${postalCodes[0]} – ${postalCodes[postalCodes.length - 1]}`;
}

/** « Lyon (69001 – 69009) » : libellé d'une commune choisie. */
export function formatCityLabel(city: CitySuggestion, query = ""): string {
    const postal = formatPostalCodes(city.postalCodes, query);
    return postal ? `${city.name} (${postal})` : city.name;
}

/**
 * Communes proposées pour la saisie en cours (2 caractères minimum), après une courte pause de
 * frappe. Une réponse arrivée pour une saisie déjà modifiée est ignorée ; en cas d'erreur, la liste
 * est simplement vide (la recherche par le bouton reste possible).
 */
export function useCitySuggestions(query: string, enabled: boolean): CitySuggestion[] {
    const value = query.trim();
    const active = enabled && value.length >= MIN_QUERY_LENGTH;
    const [result, setResult] = useState<{ query: string; suggestions: CitySuggestion[] } | null>(null);

    useEffect(() => {
        if (!active) {
            return;
        }
        const abortController = new AbortController();
        const timer = window.setTimeout(() => {
            fetch(`/api/v1/geocoding/city-suggestions?q=${encodeURIComponent(value)}`, { signal: abortController.signal })
                .then((response) => (response.ok ? (response.json() as Promise<CitySuggestion[]>) : []))
                .then((suggestions) => setResult({ query: value, suggestions }))
                .catch(() => {
                    if (!abortController.signal.aborted) {
                        setResult({ query: value, suggestions: [] });
                    }
                });
        }, DEBOUNCE_MS);
        return () => {
            window.clearTimeout(timer);
            abortController.abort();
        };
    }, [value, active]);

    // Les suggestions précédentes restent affichées le temps que la nouvelle réponse arrive (pas de clignotement).
    return active && result ? result.suggestions : [];
}
