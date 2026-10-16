import Constants, { ExecutionEnvironment } from 'expo-constants';
import { Platform } from 'react-native';

/**
 * Expo Go sur Android ne contient plus les notifications push depuis le
 * SDK 53 : le simple chargement de `expo-notifications` y affiche une
 * erreur. Le module n'est donc chargé (à la demande) que hors de ce cas.
 */
export const isExpoGoAndroid =
  Platform.OS === 'android' && Constants.executionEnvironment === ExecutionEnvironment.StoreClient;
