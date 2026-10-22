import { useEffect, useState } from 'react';

import { getCurrentAccount, listMyFoodTrucks, listMyPlaces } from '@/api';
import type { CurrentAccount, ManagedPlace } from '@/api';
import { useAuth } from '@/auth';

import { hostablePlaces } from './format';

/**
 * Compte connecté relu par le serveur (`GET /users/me`), `null` tant qu'il n'est pas connu ou hors connexion.
 * `enabled` à faux : pas d'appel (ex. menu fermé). La réponse ne vaut que pour le compte qui l'a demandée.
 */
export function useCurrentAccount(enabled = true): CurrentAccount | null {
  const { state } = useAuth();
  const userId = state.status === 'authenticated' ? state.user.userId : null;
  const [loaded, setLoaded] = useState<{ userId: number; account: CurrentAccount } | null>(null);

  useEffect(() => {
    if (userId === null || !enabled) return;
    const controller = new AbortController();
    getCurrentAccount({ signal: controller.signal })
      .then((account) => setLoaded({ userId, account }))
      .catch(() => undefined);
    return () => controller.abort();
  }, [userId, enabled]);

  return loaded && loaded.userId === userId ? loaded.account : null;
}

/**
 * Accès à l'espace professionnel (`hasProAccess`), seul indicateur pour afficher l'espace pro et ses liens ;
 * faux tant qu'il n'est pas connu. Le backend vérifie toujours le droit.
 */
export function useProAccess(enabled = true): boolean {
  return useCurrentAccount(enabled)?.hasProAccess === true;
}

/** Charge les lieux publiés gérés quand `enabled` (compte avec accès pro) ; `[]` sinon ou en cas d'erreur (403). */
export function useHostablePlaces(enabled: boolean): ManagedPlace[] {
  const { state } = useAuth();
  const userId = state.status === 'authenticated' ? state.user.userId : null;
  const [loaded, setLoaded] = useState<{ userId: number; places: ManagedPlace[] } | null>(null);

  useEffect(() => {
    if (userId === null || !enabled) return;
    const controller = new AbortController();
    listMyPlaces({ signal: controller.signal })
      .then((places) => setLoaded({ userId, places: hostablePlaces(places) }))
      .catch(() => undefined);
    return () => controller.abort();
  }, [userId, enabled]);

  return enabled && loaded && loaded.userId === userId ? loaded.places : NO_PLACES;
}

const NO_PLACES: ManagedPlace[] = [];

/**
 * Le compte possède des food trucks (`GET /pro/foodtrucks` : 200 non vide pour un propriétaire, même sans accès pro ;
 * 403 sinon). Sert aux anciens comptes Pro, qui peuvent encore consulter et archiver leurs food trucks.
 */
export function useOwnsFoodTrucks(enabled: boolean): boolean {
  const { state } = useAuth();
  const userId = state.status === 'authenticated' ? state.user.userId : null;
  const [loaded, setLoaded] = useState<{ userId: number; owns: boolean } | null>(null);

  useEffect(() => {
    if (userId === null || !enabled) return;
    const controller = new AbortController();
    listMyFoodTrucks({ signal: controller.signal })
      .then((trucks) => setLoaded({ userId, owns: trucks.length > 0 }))
      .catch(() => {
        if (!controller.signal.aborted) setLoaded({ userId, owns: false });
      });
    return () => controller.abort();
  }, [userId, enabled]);

  return enabled && loaded !== null && loaded.userId === userId && loaded.owns;
}

/** Accès pro relu et absent (ancien compte Pro) ; faux tant que le compte n'est pas connu. */
export function useProAccessEnded(): boolean {
  const account = useCurrentAccount();
  return account !== null && account.hasProAccess !== true;
}
