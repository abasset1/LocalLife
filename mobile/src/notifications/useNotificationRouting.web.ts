/**
 * Pas de notifications push sur le web (`expo-notifications` n'y expose pas
 * `getLastNotificationResponse`, dont l'appel ferait planter l'application) :
 * rien à router.
 */
export function useNotificationRouting(): void {}
