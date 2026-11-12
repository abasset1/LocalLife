import { useRootNavigationState, useRouter } from 'expo-router';
import { useEffect, useRef } from 'react';
import { Alert } from 'react-native';

import { getPreferences } from '@/api';
import { useAuth } from '@/auth';

import { loadGuideOffered, saveGuideOffered, shouldOfferGuide } from './guideOffer';
import { needsOnboarding } from './rules';

/**
 * Ouvre l'accueil de première connexion (`/bienvenue`) dès qu'une session
 * s'ouvre (connexion ou restauration) pour un compte qui n'a jamais
 * enregistré ses préférences. Vérifié une seule fois par utilisateur et par
 * lancement de l'application ; une erreur réseau laisse simplement
 * l'application utilisable.
 *
 * Compte déjà configuré (sur le site par exemple) qui se connecte pour la première fois sur cet appareil : le
 * didacticiel (guide de l'application) est proposé une fois. Sinon, l'accueil le propose à sa dernière étape.
 */
export function OnboardingGate() {
  const { state } = useAuth();
  const router = useRouter();
  const navigationReady = Boolean(useRootNavigationState()?.key);
  const userId = state.status === 'authenticated' ? state.user.userId : null;
  const loggedInAt = state.status === 'authenticated' ? state.user.loggedInAt : undefined;
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
        if (needsOnboarding(preferences)) {
          router.push('/bienvenue');
          return;
        }
        void loadGuideOffered(userId).then((offered) => {
          if (controller.signal.aborted || !shouldOfferGuide(offered, loggedInAt, Date.now())) return;
          void saveGuideOffered(userId);
          Alert.alert('Découvrir Neiri', 'Voulez-vous suivre le didacticiel de l’application en quelques étapes ?', [
            { text: 'Plus tard', style: 'cancel' },
            { text: 'Suivre le didacticiel', onPress: () => router.push('/guide') },
          ]);
        });
      })
      .catch(() => undefined);
    return () => controller.abort();
  }, [userId, loggedInAt, navigationReady, router]);

  return null;
}
