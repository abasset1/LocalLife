import { useRootNavigationState, useRouter } from 'expo-router';
import { useEffect, useRef } from 'react';

import { getPreferences } from '@/api';
import { useAuth } from '@/auth';

import { needsOnboarding } from './rules';

/**
 * Ouvre l'accueil de première connexion (`/bienvenue`) dès qu'une session
 * s'ouvre (connexion ou restauration) pour un compte qui n'a jamais
 * enregistré ses préférences. Vérifié une seule fois par utilisateur et par
 * lancement de l'application ; une erreur réseau laisse simplement
 * l'application utilisable.
 */
export function OnboardingGate() {
  const { state } = useAuth();
  const router = useRouter();
  const navigationReady = Boolean(useRootNavigationState()?.key);
  const userId = state.status === 'authenticated' ? state.user.userId : null;
  const checkedUserId = useRef<number | null>(null);

  useEffect(() => {
    if (userId === null) {
      checkedUserId.current = null;
      return;
    }
    if (!navigationReady || checkedUserId.current === userId) return;
    const controller = new AbortController();
    getPreferences({ signal: controller.signal })
      .then((preferences) => {
        checkedUserId.current = userId;
        if (needsOnboarding(preferences)) router.push('/bienvenue');
      })
      .catch(() => undefined);
    return () => controller.abort();
  }, [userId, navigationReady, router]);

  return null;
}
