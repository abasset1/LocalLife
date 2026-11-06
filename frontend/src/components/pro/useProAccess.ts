import { useEffect, useState } from "react";

/** Droits du compte connecté utiles à l'affichage, relus sur le serveur (`GET /api/v1/users/me`). */
export interface AccountAccess {
    /** Accès à l'espace professionnel : seul indicateur des entrées pro. */
    hasProAccess: boolean;
    /** Suivre des activités et des lieux (Premium et Pro) ; `null` tant qu'il n'est pas connu. */
    canFollow: boolean | null;
}

const UNKNOWN: AccountAccess = { hasProAccess: false, canFollow: null };

/**
 * Droits du compte connecté (`GET /api/v1/users/me`), en une requête : `hasProAccess` pour les entrées pro (lien
 * « Demander la gestion de ce lieu », lieu hôte d'une activité…), jamais le rôle du JWT ; `limits.canFollow` pour le
 * bandeau de nouveauté. Simple raccourci d'affichage : le backend vérifie le droit à chaque appel. `fetch` direct
 * (cookie de session) plutôt qu'`apiFetch` : une session expirée ne doit pas rediriger vers /login depuis la carte
 * publique.
 */
export function useAccountAccess(isLoggedIn: boolean): AccountAccess {
    const [access, setAccess] = useState<AccountAccess>(UNKNOWN);

    useEffect(() => {
        if (!isLoggedIn) {
            setAccess(UNKNOWN);
            return;
        }
        const abortController = new AbortController();
        void fetch("/api/v1/users/me", {
            credentials: "include",
            signal: abortController.signal,
        })
            .then(async (response) => {
                const me = response.ok
                    ? ((await response.json()) as { hasProAccess?: boolean; limits?: { canFollow?: boolean } } | null)
                    : null;
                if (!abortController.signal.aborted) {
                    setAccess({
                        hasProAccess: me?.hasProAccess === true,
                        canFollow: typeof me?.limits?.canFollow === "boolean" ? me.limits.canFollow : null,
                    });
                }
            })
            .catch(() => undefined);
        return () => abortController.abort();
    }, [isLoggedIn]);

    return access;
}

/** Accès à l'espace professionnel (`hasProAccess`), voir {@link useAccountAccess}. */
export function useProAccess(isLoggedIn: boolean): boolean {
    return useAccountAccess(isLoggedIn).hasProAccess;
}
