import Constants, { ExecutionEnvironment } from 'expo-constants';
import { Platform } from 'react-native';

/**
 * L'application tourne dans Expo Go sur Android, qui a ses limites propres :
 * - plus de notifications push depuis le SDK 53 (le simple chargement de
 *   `expo-notifications` y affiche une erreur, voir `src/notifications`) ;
 * - fond de carte Google Maps parfois vide (clé fournie par Expo Go), d'où
 *   des tuiles OpenStreetMap à la place (voir `src/features/map`).
 */
export const isExpoGoAndroid =
  Platform.OS === 'android' && Constants.executionEnvironment === ExecutionEnvironment.StoreClient;
