import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';

import { ApiError, likeActivity, listLikedActivities, unlikeActivity } from '@/api';
import type { Activity } from '@/api';
import { useAuth } from '@/auth';

import { withLike } from './likes';

export type FavoritesStatus = 'idle' | 'loading' | 'success' | 'error';

interface FavoritesContextValue {
  /** Activités aimées, dans l'ordre renvoyé par le backend (dernier chargement). */
  favorites: Activity[];
  status: FavoritesStatus;
  error: string | null;
  isLiked: (activityId: number) => boolean;
  /**
   * Aime ou n'aime plus une activité. L'état change tout de suite et revient en arrière si l'appel
   * échoue ; la promesse renvoie alors le message d'erreur (sinon `null`).
   */
  toggle: (activityId: number) => Promise<string | null>;
  /** Recharge la liste des favoris (écran « Mes favoris », tirer pour actualiser). */
  refresh: () => Promise<void>;
}

const FavoritesContext = createContext<FavoritesContextValue | null>(null);

function messageFor(error: unknown): string {
  return error instanceof ApiError ? error.message : 'Une erreur inattendue est survenue. Réessayez.';
}

/**
 * Favoris de l'utilisateur connecté (`GET /users/me/likes`), partagés par la carte, la liste, la fiche
 * et l'écran « Mes favoris » : un cœur touché sur un écran est à jour sur les autres. Vidés à la
 * déconnexion.
 */
export function FavoritesProvider({ children }: { children: ReactNode }) {
  const { state } = useAuth();
  const userId = state.status === 'authenticated' ? state.user.userId : null;

  const [favorites, setFavorites] = useState<Activity[]>([]);
  const [ids, setIds] = useState<ReadonlySet<number>>(new Set());
  const [status, setStatus] = useState<FavoritesStatus>('idle');
  const [error, setError] = useState<string | null>(null);
  /** Dernier état demandé par activité : une réponse tardive d'un appui précédent ne l'écrase pas. */
  const pending = useRef(new Map<number, boolean>());

  const refresh = useCallback(async () => {
    if (userId === null) return;
    setStatus('loading');
    setError(null);
    try {
      const liked = await listLikedActivities();
      setFavorites(liked);
      setIds(new Set(liked.map((activity) => activity.id)));
      setStatus('success');
    } catch (e) {
      setError(messageFor(e));
      setStatus('error');
    }
  }, [userId]);

  useEffect(() => {
    if (userId === null) {
      setFavorites([]);
      setIds(new Set());
      setStatus('idle');
      setError(null);
      return;
    }
    void refresh();
  }, [userId, refresh]);

  const isLiked = useCallback((activityId: number) => ids.has(activityId), [ids]);

  const toggle = useCallback(
    async (activityId: number) => {
      const liked = !ids.has(activityId);
      pending.current.set(activityId, liked);
      setIds((current) => withLike(current, activityId, liked));
      if (!liked) setFavorites((current) => current.filter((activity) => activity.id !== activityId));
      try {
        await (liked ? likeActivity(activityId) : unlikeActivity(activityId));
        return null;
      } catch (e) {
        if (pending.current.get(activityId) === liked) {
          setIds((current) => withLike(current, activityId, !liked));
          // Un favori retiré à tort réapparaît au prochain chargement de la liste.
          if (!liked) void refresh();
        }
        return messageFor(e);
      } finally {
        if (pending.current.get(activityId) === liked) pending.current.delete(activityId);
      }
    },
    [ids, refresh],
  );

  const value = useMemo(
    () => ({ favorites, status, error, isLiked, toggle, refresh }),
    [favorites, status, error, isLiked, toggle, refresh],
  );
  return <FavoritesContext.Provider value={value}>{children}</FavoritesContext.Provider>;
}

export function useFavorites(): FavoritesContextValue {
  const context = useContext(FavoritesContext);
  if (!context) throw new Error('useFavorites doit être utilisé sous <FavoritesProvider>.');
  return context;
}
