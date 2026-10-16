import Constants from 'expo-constants';
import * as Device from 'expo-device';
import { Platform } from 'react-native';

import { registerPushToken, unregisterPushToken } from '@/api';
import { isExpoGoAndroid } from '@/config/expoGo';

/**
 * Notifications push des alertes (service Expo Push).
 *
 * - La permission n'est demandée que lorsque l'utilisateur active la
 *   notification d'une alerte (`enablePushNotifications`), jamais au
 *   démarrage — même principe que la géolocalisation (LL-MOB-0006).
 * - Si elle a déjà été accordée, le jeton est simplement renvoyé au backend
 *   à chaque ouverture de session (`refreshPushRegistration`) : il peut
 *   changer, et le backend l'attache au compte connecté.
 * - À la déconnexion, le jeton est retiré du compte (`disablePushRegistration`) :
 *   le téléphone ne reçoit plus les alertes de ce compte.
 *
 * ⚠️ Aucun log du jeton.
 */

export type PushRegistration =
  | { status: 'registered' }
  | { status: 'denied' }
  | { status: 'unavailable'; reason: string };

/** Canal Android des alertes (obligatoire avant la demande de permission sur Android 13+). */
const ANDROID_CHANNEL_ID = 'alerts';

let currentToken: string | null = null;

type NotificationsModule = typeof import('expo-notifications');
let notificationsModule: NotificationsModule | null = null;

/**
 * Charge `expo-notifications` à la première utilisation, jamais dans Expo Go
 * sur Android (voir `@/config/expoGo`) : les appelants vérifient
 * `unavailableReason()` avant.
 */
export function notifications(): NotificationsModule {
  if (!notificationsModule) {
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    notificationsModule = require('expo-notifications') as NotificationsModule;
    // Notification reçue application ouverte : affichée quand même.
    notificationsModule.setNotificationHandler({
      handleNotification: async () => ({
        shouldShowBanner: true,
        shouldShowList: true,
        shouldPlaySound: true,
        shouldSetBadge: false,
      }),
    });
  }
  return notificationsModule;
}

function projectId(): string | undefined {
  return Constants.expoConfig?.extra?.eas?.projectId ?? Constants.easConfig?.projectId;
}

/** Raison pour laquelle les notifications ne peuvent pas fonctionner ici, ou `null`. */
function unavailableReason(): string | null {
  if (Platform.OS === 'web') return 'Les notifications sont disponibles uniquement sur Android et iOS.';
  if (!Device.isDevice) return 'Les notifications nécessitent un téléphone (pas un émulateur).';
  if (isExpoGoAndroid) {
    return "Les notifications ne fonctionnent pas dans Expo Go sur Android : utilisez une version installée de l'application.";
  }
  if (!projectId()) return "Les notifications ne sont pas configurées pour cette version de l'application.";
  return null;
}

async function ensureAndroidChannel(): Promise<void> {
  if (Platform.OS !== 'android') return;
  await notifications().setNotificationChannelAsync(ANDROID_CHANNEL_ID, {
    name: 'Alertes LocalLife',
    importance: notifications().AndroidImportance.DEFAULT,
  });
}

async function sendTokenToBackend(): Promise<void> {
  const { data: token } = await notifications().getExpoPushTokenAsync({ projectId: projectId() });
  currentToken = token;
  await registerPushToken(token, Platform.OS === 'ios' ? 'ios' : 'android');
}

/** Demande la permission si besoin, puis enregistre l'appareil pour le compte connecté. */
export async function enablePushNotifications(): Promise<PushRegistration> {
  const reason = unavailableReason();
  if (reason) return { status: 'unavailable', reason };
  try {
    await ensureAndroidChannel();
    let permission = await notifications().getPermissionsAsync();
    if (!permission.granted && permission.canAskAgain) {
      permission = await notifications().requestPermissionsAsync();
    }
    if (!permission.granted) return { status: 'denied' };
    await sendTokenToBackend();
    return { status: 'registered' };
  } catch {
    return { status: 'unavailable', reason: "L'appareil n'a pas pu être enregistré. Réessayez plus tard." };
  }
}

/**
 * Active les notifications sur cet appareil : `null` si l'appareil est bien
 * enregistré, sinon un message affichable expliquant pourquoi.
 */
export async function enablePushOrExplain(): Promise<string | null> {
  const result = await enablePushNotifications();
  if (result.status === 'registered') return null;
  if (result.status === 'denied') {
    return 'Les notifications sont refusées : autorisez-les dans les réglages du téléphone.';
  }
  return result.reason;
}

/** À l'ouverture de session : renvoie le jeton au backend si la permission est déjà accordée, sans rien demander. */
export async function refreshPushRegistration(): Promise<void> {
  if (unavailableReason()) return;
  try {
    const permission = await notifications().getPermissionsAsync();
    if (!permission.granted) return;
    await ensureAndroidChannel();
    await sendTokenToBackend();
  } catch {
    // Sans conséquence : nouvel essai à la prochaine ouverture de session.
  }
}

/** À la déconnexion (JWT encore valide) : retire l'appareil du compte. */
export async function disablePushRegistration(): Promise<void> {
  const token = currentToken;
  currentToken = null;
  if (!token) return;
  try {
    await unregisterPushToken(token);
  } catch {
    // Le backend supprimera le jeton lui-même s'il est réattribué ou refusé par Expo.
  }
}
