import * as Notifications from 'expo-notifications';
import { useRouter } from 'expo-router';
import { useEffect, useRef } from 'react';

import { routeForNotification } from './routing';

/**
 * Ouvre l'écran correspondant à la notification touchée, y compris quand
 * c'est elle qui a lancé l'application.
 */
export function useNotificationRouting(): void {
  const router = useRouter();
  const response = Notifications.useLastNotificationResponse();
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
