import AsyncStorage from '@react-native-async-storage/async-storage';

/**
 * Didacticiel (guide de l'application) proposé une fois par compte et par appareil, à la première connexion :
 * en fin d'accueil (`/bienvenue`), ou juste après la connexion pour un compte dont les préférences sont déjà
 * configurées (sur le site par exemple).
 */
export function guideOfferedKey(userId: number): string {
  return `locallife.guideOffered.${userId}`;
}

/** Connexion faite à l'instant (jeton émis il y a moins de 10 minutes), et non session restaurée au lancement. */
const FRESH_LOGIN_SECONDS = 10 * 60;

export function shouldOfferGuide(alreadyOffered: boolean, loggedInAt: number | undefined, nowMs: number): boolean {
  if (alreadyOffered || loggedInAt === undefined) return false;
  return nowMs / 1000 - loggedInAt <= FRESH_LOGIN_SECONDS;
}

/** Déjà proposé ? Stockage illisible : considéré comme déjà proposé (pas de proposition répétée). */
export async function loadGuideOffered(userId: number): Promise<boolean> {
  try {
    return (await AsyncStorage.getItem(guideOfferedKey(userId))) === '1';
  } catch {
    return true;
  }
}

export async function saveGuideOffered(userId: number): Promise<void> {
  try {
    await AsyncStorage.setItem(guideOfferedKey(userId), '1');
  } catch {
    // Stockage indisponible : la proposition pourra revenir à une prochaine connexion.
  }
}
