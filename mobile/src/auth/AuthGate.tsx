import { useRootNavigationState, useRouter, useSegments } from 'expo-router';
import { useEffect, useRef } from 'react';

import { useAuth } from './AuthProvider';
import { isOpenWithoutAccount } from './signInRules';

/**
 * Compte obligatoire : sans session (jamais connecté, déconnexion, jeton expiré), tout écran autre que la connexion
 * renvoie vers l'onglet Compte (formulaire de connexion / inscription). Une fois connecté, l'utilisateur renvoyé
 * ainsi arrive sur la carte.
 */
export function AuthGate() {
  const { state } = useAuth();
  const router = useRouter();
  const segments = useSegments();
  const navigationReady = Boolean(useRootNavigationState()?.key);
  const sentToSignIn = useRef(false);

  useEffect(() => {
    if (!navigationReady) return;
    if (state.status === 'anonymous') {
      if (!isOpenWithoutAccount(segments)) {
        sentToSignIn.current = true;
        router.replace('/compte');
      }
      return;
    }
    if (state.status === 'authenticated' && sentToSignIn.current) {
      sentToSignIn.current = false;
      router.replace('/');
    }
  }, [state.status, segments, navigationReady, router]);

  return null;
}
