import { Platform } from 'react-native';

import { googleIosClientId, googleWebClientId } from '@/config/env';
import { isExpoGo } from '@/config/expoGo';

type GoogleSignInModule = typeof import('@react-native-google-signin/google-signin');

/**
 * Connexion Google native (`@react-native-google-signin/google-signin`). Elle demande un ID token
 * destiné au client OAuth « web » (`webClientId`), le seul que le backend accepte : aucun changement
 * côté serveur. Indisponible dans Expo Go (module natif absent) et sur le web : il faut un build
 * (`npx expo run:android`), et les identifiants `EXPO_PUBLIC_GOOGLE_*` (voir `.env.example`).
 */
export const googleSignInAvailable =
  Boolean(googleWebClientId) && !isExpoGo && (Platform.OS === 'android' || (Platform.OS === 'ios' && Boolean(googleIosClientId)));

let module: GoogleSignInModule | null = null;

/** Charge et configure le module à la première utilisation (jamais au démarrage, jamais dans Expo Go). */
function google(): GoogleSignInModule {
  if (!module) {
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    module = require('@react-native-google-signin/google-signin') as GoogleSignInModule;
    module.GoogleSignin.configure({ webClientId: googleWebClientId, iosClientId: googleIosClientId });
  }
  return module;
}

/** Échec de la connexion Google, avec un message affichable. */
export class GoogleSignInError extends Error {}

/**
 * Ouvre le sélecteur de compte Google. Renvoie l'ID token, ou `null` si l'utilisateur annule.
 * Lève `GoogleSignInError` (services Google indisponibles, connexion déjà en cours, autre échec).
 */
export async function requestGoogleIdToken(): Promise<string | null> {
  const { GoogleSignin, isErrorWithCode, isSuccessResponse, statusCodes } = google();
  try {
    await GoogleSignin.hasPlayServices({ showPlayServicesUpdateDialog: true });
    const response = await GoogleSignin.signIn();
    if (!isSuccessResponse(response)) return null;
    if (!response.data.idToken) throw new GoogleSignInError('Google n’a pas renvoyé d’identifiant. Réessayez.');
    return response.data.idToken;
  } catch (error) {
    if (error instanceof GoogleSignInError) throw error;
    if (isErrorWithCode(error)) {
      if (error.code === statusCodes.SIGN_IN_CANCELLED) return null;
      if (error.code === statusCodes.IN_PROGRESS) throw new GoogleSignInError('Une connexion Google est déjà en cours.');
      if (error.code === statusCodes.PLAY_SERVICES_NOT_AVAILABLE) {
        throw new GoogleSignInError('Les services Google Play sont indisponibles ou à mettre à jour sur ce téléphone.');
      }
    }
    throw new GoogleSignInError('La connexion avec Google a échoué. Réessayez, ou connectez-vous avec votre e-mail.');
  }
}

/** Oublie le compte Google choisi (déconnexion) ; sans effet si la connexion Google n'a jamais servi. */
export async function signOutFromGoogle(): Promise<void> {
  if (!module) return;
  await module.GoogleSignin.signOut().catch(() => undefined);
}
