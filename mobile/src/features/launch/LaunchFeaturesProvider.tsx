import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import { AppState } from 'react-native';

import { getLaunchFeatures } from '@/api';
import { useAuth } from '@/auth';

import { ALL_CLOSED, ALL_OPEN, type LaunchFeatures, parseLaunchFeatures } from './launchFeatures';

const LaunchFeaturesContext = createContext<LaunchFeatures>(ALL_OPEN);

/** Fonctions disponibles pour l'utilisateur de l'application. */
export function useLaunchFeatures(): LaunchFeatures {
  return useContext(LaunchFeaturesContext);
}

/**
 * Lit les fonctions disponibles au démarrage, à chaque connexion ou déconnexion (un administrateur voit aussitôt
 * ce qui est encore fermé au public) et au retour au premier plan : une fonction ouverte entre-temps apparaît
 * sans relancer l'application. Tout reste fermé tant que le serveur n'a pas répondu.
 */
export function LaunchFeaturesProvider({ children }: { children: ReactNode }) {
  const { state } = useAuth();
  const sessionKey = state.status === 'authenticated' ? `user-${state.user.userId}` : state.status;
  const [features, setFeatures] = useState<LaunchFeatures>(ALL_CLOSED);
  const [foregroundCount, setForegroundCount] = useState(0);

  useEffect(() => {
    const subscription = AppState.addEventListener('change', (next) => {
      if (next === 'active') setForegroundCount((count) => count + 1);
    });
    return () => subscription.remove();
  }, []);

  useEffect(() => {
    // Session pas encore restaurée : on attend de savoir qui est connecté.
    if (sessionKey === 'loading') return;
    const controller = new AbortController();
    getLaunchFeatures({ signal: controller.signal })
      .then((loaded) => {
        if (!controller.signal.aborted) setFeatures(parseLaunchFeatures(loaded));
      })
      .catch(() => {
        // Serveur injoignable : on garde la dernière réponse connue (tout fermé au premier lancement).
      });
    return () => controller.abort();
  }, [sessionKey, foregroundCount]);

  return <LaunchFeaturesContext.Provider value={features}>{children}</LaunchFeaturesContext.Provider>;
}
