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
  const { state } = useAuth();
  const authenticated = state.status === 'authenticated';
  const [preferences, setPreferences] = useState<UserPreferences | null>(null);

  useFocusEffect(
    useCallback(() => {
      if (!authenticated) {
        setPreferences(null);
        return undefined;
      }
      const controller = new AbortController();
      getPreferences({ signal: controller.signal })
        .then(setPreferences)
        .catch(() => undefined);
      return () => controller.abort();
    }, [authenticated]),
  );

  return preferences;
}
