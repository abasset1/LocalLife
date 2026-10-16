import { useRouter } from 'expo-router';
import { useEffect, useRef } from 'react';

import { isExpoGoAndroid } from '@/config/expoGo';

import { notifications } from './push';
import { routeForNotification } from './routing';

/**
 * Ouvre l'écran correspondant à la notification touchée, y compris quand
 * c'est elle qui a lancé l'application. Rien à router dans Expo Go sur
 * Android, où `expo-notifications` n'est pas chargé : le choix est fixé une
 * fois pour toutes au chargement, l'ordre des hooks ne varie donc pas.
 */
export const useNotificationRouting: () => void = isExpoGoAndroid ? () => {} : useRouteLastNotification;

function useRouteLastNotification(): void {
  const router = useRouter();
  const response = notifications().useLastNotificationResponse();
  const handled = useRef<string | null>(null);

  useEffect(() => {
    if (!response) return;
    const id = response.notification.request.identifier;
    if (handled.current === id) return;
    handled.current = id;
    const route = routeForNotification(response.notification.request.content.data);
    if (route) router.push(route as never);
  }, [response, router]);
}
