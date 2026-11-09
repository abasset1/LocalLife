import { createContext, useContext, useEffect, useState, type ReactNode } from "react";
import { useLocation } from "react-router-dom";
import { isAuthenticated, subscribeSession } from "../auth/authStorage";

/**
 * Fonctions ouvertes progressivement après le lancement (05/11/2026, PACA et Occitanie) : lieux, food trucks, offres
 * Premium et Pro. Le serveur décide (`GET /api/v1/features`, interrupteurs `FEATURE_*`) : ouvrir une fonction ne
 * demande pas de nouvelle version du site. L'équipe Neiri et les administrateurs voient tout.
 */
export interface LaunchFeatures {
    places: boolean;
    foodTrucks: boolean;
    premium: boolean;
    pro: boolean;
}

/** Tout ouvert : valeur hors fournisseur (tests, composants rendus seuls), comportement d'avant le lancement. */
export const ALL_OPEN: LaunchFeatures = { places: true, foodTrucks: true, premium: true, pro: true };

/** Tout fermé : tant que la réponse du serveur n'est pas arrivée, ou s'il ne répond pas. */
export const ALL_CLOSED: LaunchFeatures = { places: false, foodTrucks: false, premium: false, pro: false };

const LaunchFeaturesContext = createContext<LaunchFeatures>(ALL_OPEN);

/** Fonctions disponibles pour l'utilisateur affiché. */
export function useLaunchFeatures(): LaunchFeatures {
    return useContext(LaunchFeaturesContext);
}

/** Réponse du serveur ramenée à des booléens : un champ absent ou illisible compte comme fermé. */
export function parseLaunchFeatures(value: unknown): LaunchFeatures {
    const data = (value ?? {}) as Partial<Record<keyof LaunchFeatures, unknown>>;
    return {
        places: data.places === true,
        foodTrucks: data.foodTrucks === true,
        premium: data.premium === true,
        pro: data.pro === true,
    };
}

async function fetchLaunchFeatures(signal: AbortSignal): Promise<LaunchFeatures> {
    // Cookie de session envoyé si présent : un membre de l'équipe voit les fonctions encore fermées. Une session
    // absente ou invalide est traitée comme un visiteur par le serveur (route publique), sans 401 ni redirection.
    const response = await fetch("/api/v1/features", { credentials: "include", signal });
    return response.ok ? parseLaunchFeatures(await response.json()) : ALL_CLOSED;
}

/**
 * Lit les fonctions disponibles au démarrage, puis à chaque connexion ou déconnexion : un administrateur qui se
 * connecte voit aussitôt les fonctions encore fermées au public. Le changement de session est détecté par
 * {@link subscribeSession} (connexion/déconnexion) et par le changement de page.
 */
export function LaunchFeaturesProvider({ children }: { children: ReactNode }) {
    const { pathname } = useLocation();
    const [authed, setAuthed] = useState<boolean>(() => isAuthenticated());
    const [features, setFeatures] = useState<LaunchFeatures>(ALL_CLOSED);

    useEffect(() => {
        setAuthed(isAuthenticated());
    }, [pathname]);

    useEffect(() => subscribeSession(() => setAuthed(isAuthenticated())), []);

    useEffect(() => {
        const controller = new AbortController();
        fetchLaunchFeatures(controller.signal)
            .then((loaded) => {
                if (!controller.signal.aborted) {
                    setFeatures(loaded);
                }
            })
            .catch(() => {
                if (!controller.signal.aborted) {
                    setFeatures(ALL_CLOSED);
                }
            });
        return () => controller.abort();
    }, [authed]);

    return <LaunchFeaturesContext.Provider value={features}>{children}</LaunchFeaturesContext.Provider>;
}
