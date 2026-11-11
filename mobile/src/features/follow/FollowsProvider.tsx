import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';

import { ApiError, HttpError, follow, listFollows, unfollow } from '@/api';
import type { FollowTargetType, FollowedItem } from '@/api';
import { useAuth } from '@/auth';

import { FOLLOW_PLAN_MESSAGE, followKey, withFollow } from './follows';

export type FollowsStatus = 'idle' | 'loading' | 'success' | 'error';

/** Résultat d'un appui sur la cloche : `planLimit` quand le compte gratuit n'y a pas droit (403). */
export type FollowProblem = { message: string; planLimit: boolean } | null;

interface FollowsContextValue {
  follows: FollowedItem[];
  status: FollowsStatus;
  error: string | null;
  isFollowing: (type: FollowTargetType, id: number) => boolean;
  /** Suit ou ne suit plus ; l'état change seulement quand le serveur a confirmé. */
  setFollowing: (
    target: Pick<FollowedItem, 'type' | 'id' | 'title' | 'city'>,
    following: boolean,
  ) => Promise<FollowProblem>;
  refresh: () => Promise<void>;
}

const FollowsContext = createContext<FollowsContextValue | null>(null);

interface FollowsData {
  userId: number | null;
  follows: FollowedItem[];
  status: FollowsStatus;
  error: string | null;
}

const EMPTY: FollowsData = { userId: null, follows: [], status: 'idle', error: null };

function messageFor(error: unknown): string {
  return error instanceof ApiError ? error.message : 'Une erreur inattendue est survenue. Réessayez.';
}

/**
 * Activités, lieux et food trucks suivis par l'utilisateur connecté (`GET /users/me/follows`), partagés par la fiche
 * d'une activité ou d'un food truck, l'aperçu d'un lieu et l'écran « Mes suivis ». Vidés à la déconnexion.
 */
export function FollowsProvider({ children }: { children: ReactNode }) {
  const { state } = useAuth();
  const userId = state.status === 'authenticated' ? state.user.userId : null;
  // État rattaché au compte qui l'a chargé : à la déconnexion ou au changement de compte, il n'est plus affiché.
  const [data, setData] = useState<FollowsData>(EMPTY);
  const current = userId !== null && data.userId === userId ? data : EMPTY;
  const { follows, status, error } = current;

  const refresh = useCallback(async () => {
    if (userId === null) return;
    setData((previous) => ({
      userId,
      follows: previous.userId === userId ? previous.follows : [],
      status: 'loading',
      error: null,
    }));
    try {
      const items = await listFollows();
      setData({ userId, follows: items, status: 'success', error: null });
    } catch (e) {
      setData((previous) => ({ ...previous, userId, status: 'error', error: messageFor(e) }));
    }
  }, [userId]);

  useEffect(() => {
    if (userId !== null) void refresh();
  }, [userId, refresh]);

  const keys = useMemo(() => new Set(follows.map((item) => followKey(item.type, item.id))), [follows]);
  const isFollowing = useCallback((type: FollowTargetType, id: number) => keys.has(followKey(type, id)), [keys]);

  const setFollowing = useCallback(
    async (target: Pick<FollowedItem, 'type' | 'id' | 'title' | 'city'>, following: boolean) => {
      try {
        await (following ? follow(target.type, target.id) : unfollow(target.type, target.id));
        setData((previous) =>
          previous.userId === userId ? { ...previous, follows: withFollow(previous.follows, target, following) } : previous,
        );
        return null;
      } catch (e) {
        const planLimit = e instanceof HttpError && e.isForbidden;
        return { message: planLimit ? e.message || FOLLOW_PLAN_MESSAGE : messageFor(e), planLimit };
      }
    },
    [userId],
  );

  const value = useMemo(
    () => ({ follows, status, error, isFollowing, setFollowing, refresh }),
    [follows, status, error, isFollowing, setFollowing, refresh],
  );
  return <FollowsContext.Provider value={value}>{children}</FollowsContext.Provider>;
}

export function useFollows(): FollowsContextValue {
  const context = useContext(FollowsContext);
  if (!context) throw new Error('useFollows doit être utilisé sous <FollowsProvider>.');
  return context;
}
