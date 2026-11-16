import { useFocusEffect } from 'expo-router';
import { useCallback, useState } from 'react';

import { getPreferences } from '@/api';
import type { UserPreferences } from '@/api';
import { useAuth } from '@/auth';

/**
 * Paramètres de recherche de l'utilisateur connecté, relus chaque fois que
 * la carte reprend le focus (retour depuis l'écran « Paramètres de
 * recherche »). `null` pour un visiteur ou si le chargement échoue : la carte
 * garde alors son comportement standard.
 */
export function useSearchPreferences(): UserPreferences | null {
  return useSearchPreferencesState().preferences;
}

/**
 * Comme {@link useSearchPreferences}, avec `settled` : `true` une fois la première réponse reçue (ou en
 * échec), ou tout de suite pour un visiteur. La carte attend ce moment pour s'ouvrir sur la ville des paramètres.
 */
export function useSearchPreferencesState(): { preferences: UserPreferences | null; settled: boolean } {
  const { state } = useAuth();
  const authenticated = state.status === 'authenticated';
  const [preferences, setPreferences] = useState<UserPreferences | null>(null);
  const [settled, setSettled] = useState(false);

  useFocusEffect(
    useCallback(() => {
      if (!authenticated) {
        setPreferences(null);
        setSettled(true);
        return undefined;
      }
      const controller = new AbortController();
      getPreferences({ signal: controller.signal })
        .then(setPreferences)
        .catch(() => undefined)
        .finally(() => {
          if (!controller.signal.aborted) setSettled(true);
        });
      return () => controller.abort();
    }, [authenticated]),
  );

  return { preferences, settled };
}
