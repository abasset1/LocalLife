import * as SecureStore from 'expo-secure-store';
import { Platform } from 'react-native';

/**
 * Stockage du JWT (LL-MOB-0004).
 *
 * Sur Android/iOS, le token est écrit dans le stockage sécurisé de l'OS
 * (Keystore / Keychain) via `expo-secure-store` — jamais dans
 * AsyncStorage ni `localStorage`. Le mot de passe n'est jamais stocké.
 *
 * Le web n'est pas une cible du sprint mobile : il n'existe pas de
 * stockage sécurisé équivalent, le token y reste donc en mémoire
 * uniquement (la session ne survit pas à un rechargement).
 */
const TOKEN_KEY = 'locallife.jwt';

let memoryToken: string | null = null;

export async function loadToken(): Promise<string | null> {
  if (Platform.OS === 'web') return memoryToken;
  try {
    return await SecureStore.getItemAsync(TOKEN_KEY);
  } catch {
    // Stockage illisible (ex. clé invalidée) : on repart déconnecté.
    return null;
  }
}

export async function saveToken(token: string): Promise<void> {
  if (Platform.OS === 'web') {
    memoryToken = token;
    return;
  }
  await SecureStore.setItemAsync(TOKEN_KEY, token, {
    keychainAccessible: SecureStore.WHEN_UNLOCKED_THIS_DEVICE_ONLY,
  });
}

export async function clearToken(): Promise<void> {
  memoryToken = null;
  if (Platform.OS === 'web') return;
  try {
    await SecureStore.deleteItemAsync(TOKEN_KEY);
  } catch {
    // Rien de plus à faire : le token en mémoire est déjà oublié.
  }
}
