import { useEffect, useState } from "react";
import { getToken } from "../../auth/authStorage";

/**
 * Accès à l'espace professionnel (`hasProAccess` de `GET /api/v1/users/me`) : seul indicateur pour afficher les
 * entrées pro (lien « Demander la gestion de ce lieu », lieu hôte d'une activité…), jamais le rôle du JWT. Simple
 * raccourci d'affichage : le backend vérifie le droit à chaque appel. `fetch` direct avec le JWT plutôt
 * qu'`apiFetch` : un token expiré ne doit pas rediriger vers /login depuis la carte publique.
 */
export function useProAccess(isLoggedIn: boolean): boolean {
    const [hasProAccess, setHasProAccess] = useState(false);

    useEffect(() => {
        const token = getToken();
        if (!isLoggedIn || !token) {
            setHasProAccess(false);
            return;
        }
        const abortController = new AbortController();
        void fetch("/api/v1/users/me", {
            headers: { Authorization: `Bearer ${token}` },
            signal: abortController.signal,
        })
            .then(async (response) => {
                const me = response.ok ? ((await response.json()) as { hasProAccess?: boolean } | null) : null;
                if (!abortController.signal.aborted) {
                    setHasProAccess(me?.hasProAccess === true);
                }
            })
            .catch(() => undefined);
        return () => abortController.abort();
    }, [isLoggedIn]);

    return hasProAccess;
}
