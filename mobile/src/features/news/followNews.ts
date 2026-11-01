import AsyncStorage from '@react-native-async-storage/async-storage';

/**
 * Bandeau de la carte qui annonce la cloche « Suivre » aux membres gratuits : fermeture mémorisée sur l'appareil,
 * par compte (même principe que `locallife.news.follow.dismissed.<id>` sur le site web).
 */
export function followNewsDismissKey(userId: number): string {
  return `locallife.news.follow.dismissed.${userId}`;
}

/** Affiché seulement à un membre connecté dont le serveur a confirmé qu'il n'a pas le suivi, s'il ne l'a pas fermé. */
export function shouldShowFollowNews(
  userId: number | null,
  canFollow: boolean | undefined,
  dismissed: boolean | null,
): boolean {
  return userId !== null && canFollow === false && dismissed === false;
}

/** Fermé auparavant ? Stockage illisible : considéré comme non fermé (le bandeau reste fermable). */
export async function loadFollowNewsDismissed(userId: number): Promise<boolean> {
  try {
    return (await AsyncStorage.getItem(followNewsDismissKey(userId))) === '1';
  } catch {
    return false;
  }
}

/** Mémorise la fermeture ; un échec d'écriture la limite à la session. */
export async function saveFollowNewsDismissed(userId: number): Promise<void> {
  try {
    await AsyncStorage.setItem(followNewsDismissKey(userId), '1');
  } catch {
    // Stockage indisponible : le bandeau reste fermé pour la session.
  }
}
