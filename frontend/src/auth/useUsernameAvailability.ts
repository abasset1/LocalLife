import { useEffect, useState } from "react";

/**
 * Règles du nom d'utilisateur (LS-000), identiques à `UsernamePolicy` côté
 * backend : elles donnent un retour immédiat pendant la saisie. Le backend
 * reste seul juge, à l'inscription comme dans le profil.
 */
export const USERNAME_MIN_LENGTH = 3;
export const USERNAME_MAX_LENGTH = 30;
export const USERNAME_RULES =
    "3 à 30 caractères : lettres sans accent, chiffres, « . », « _ » ou « - », en commençant par une lettre ou un chiffre.";

const USERNAME_PATTERN = /^[A-Za-z0-9][A-Za-z0-9._-]*$/;
const CHECK_DELAY_MS = 400;

export type UsernameStatus =
    | { kind: "idle" }
    | { kind: "checking" }
    | { kind: "available" }
    | { kind: "unavailable"; message: string };

/** Problème de format détectable sans appeler le serveur, ou `null`. */
export function usernameFormatError(username: string): string | null {
    const value = username.trim();
    if (value.length < USERNAME_MIN_LENGTH || value.length > USERNAME_MAX_LENGTH) {
        return `Le nom d'utilisateur doit contenir entre ${USERNAME_MIN_LENGTH} et ${USERNAME_MAX_LENGTH} caractères.`;
    }
    if (!USERNAME_PATTERN.test(value)) {
        return "Lettres sans accent, chiffres, « . », « _ » ou « - » uniquement, en commençant par une lettre ou un chiffre.";
    }
    return null;
}

/**
 * Vérifie pendant la saisie qu'un nom d'utilisateur est valide et libre.
 * `currentUsername` (profil) : le nom actuel du compte, ou une simple
 * variante de casse, n'est pas signalé comme pris. Le format se juge
 * localement ; seule la réponse du serveur est gardée en état, associée au
 * nom vérifié pour ignorer une réponse devenue obsolète.
 */
export function useUsernameAvailability(username: string, currentUsername?: string): UsernameStatus {
    const value = username.trim();
    const [checked, setChecked] = useState<{ value: string; status: UsernameStatus } | null>(null);
    const unchanged = value === "" || (currentUsername !== undefined && value.toLowerCase() === currentUsername.toLowerCase());
    const formatError = unchanged ? null : usernameFormatError(value);
    const needsServer = !unchanged && formatError === null;

    useEffect(() => {
        if (!needsServer) {
            return;
        }
        const abortController = new AbortController();
        const timer = window.setTimeout(() => {
            fetch(`/api/v1/auth/username-availability?username=${encodeURIComponent(value)}`, {
                signal: abortController.signal,
            })
                .then((response) => (response.ok ? response.json() : null))
                .then((body: { available: boolean; message: string | null } | null) => {
                    let status: UsernameStatus = { kind: "idle" };
                    if (body?.available) {
                        status = { kind: "available" };
                    } else if (body) {
                        status = { kind: "unavailable", message: body.message ?? "Ce nom d'utilisateur est indisponible." };
                    }
                    setChecked({ value, status });
                })
                .catch(() => {
                    if (!abortController.signal.aborted) {
                        // Vérification indisponible : le serveur tranchera à l'envoi.
                        setChecked({ value, status: { kind: "idle" } });
                    }
                });
        }, CHECK_DELAY_MS);

        return () => {
            window.clearTimeout(timer);
            abortController.abort();
        };
    }, [value, needsServer]);

    if (unchanged) {
        return { kind: "idle" };
    }
    if (formatError) {
        return { kind: "unavailable", message: formatError };
    }
    return checked?.value === value ? checked.status : { kind: "checking" };
}
