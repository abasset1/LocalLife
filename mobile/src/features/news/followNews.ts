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

/** Popup de nouveauté déjà montrée à ce compte sur cet appareil (même clé que le site web). */
export function followNewsPopupKey(userId: number): string {
  return `locallife.news.follow.popupSeen.${userId}`;
}

export async function loadFollowNewsPopupSeen(userId: number): Promise<boolean> {
  try {
    return (await AsyncStorage.getItem(followNewsPopupKey(userId))) === '1';
  } catch {
    return false;
  }
}

export async function saveFollowNewsPopupSeen(userId: number): Promise<void> {
  try {
    await AsyncStorage.setItem(followNewsPopupKey(userId), '1');
  } catch {
    // Stockage indisponible : la popup n'est pas mémorisée.
  }
}

/**
 * L'accueil de première connexion s'est ouvert depuis le lancement de l'application : pas de popup de nouveauté en
 * plus pendant cette session (elle viendra au lancement suivant), comme sur le site web.
 */
let onboardingShownThisSession = false;

export function markOnboardingShown(): void {
  onboardingShownThisSession = true;
}

export function wasOnboardingShown(): boolean {
  return onboardingShownThisSession;
}

/** Popup à montrer : membre connu comme gratuit, jamais vue sur cet appareil, et pas d'accueil pendant la session. */
export function shouldShowFollowNewsPopup(
  userId: number | null,
  canFollow: boolean | undefined,
  seen: boolean | null,
  onboardingShown: boolean,
): boolean {
  return userId !== null && canFollow === false && seen === false && !onboardingShown;
}
