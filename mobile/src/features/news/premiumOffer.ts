import AsyncStorage from '@react-native-async-storage/async-storage';

import { loadFollowNewsPopupSeen } from './followNews';

/** Durée pendant laquelle la popup Premium est proposée après une connexion (comme sur le site web). */
export const PREMIUM_OFFER_DAYS_AFTER_LOGIN = 7;
const DAY_MS = 24 * 60 * 60 * 1000;

/** « Ne plus me proposer » : définitif, par compte et par appareil (même clé que le site web). */
export function premiumOfferOptOutKey(userId: number): string {
  return `locallife.premiumOffer.optOut.${userId}`;
}

export async function loadPremiumOfferOptOut(userId: number): Promise<boolean> {
  try {
    return (await AsyncStorage.getItem(premiumOfferOptOutKey(userId))) === '1';
  } catch {
    return false;
  }
}

export async function savePremiumOfferOptOut(userId: number): Promise<void> {
  try {
    await AsyncStorage.setItem(premiumOfferOptOutKey(userId), '1');
  } catch {
    // Stockage indisponible : le refus vaut pour la session.
  }
}

/** Comptes pour lesquels la popup a été fermée depuis le lancement de l'application : elle revient au suivant. */
const closedThisSession = new Set<number>();

export function closePremiumOfferForSession(userId: number): void {
  closedThisSession.add(userId);
}

export function wasPremiumOfferClosed(userId: number): boolean {
  return closedThisSession.has(userId);
}

/**
 * Popup de nouveauté « Suivre » déjà vue avant ce lancement, par compte : lue une seule fois par lancement, sans quoi
 * l'offre suivrait aussitôt la nouveauté, qui se marque vue dès son affichage (jamais deux popups à la suite).
 */
const newsSeenBeforeSession = new Map<number, Promise<boolean>>();

export function loadNewsSeenBeforeSession(userId: number): Promise<boolean> {
  let seen = newsSeenBeforeSession.get(userId);
  if (!seen) {
    seen = loadFollowNewsPopupSeen(userId);
    newsSeenBeforeSession.set(userId, seen);
  }
  return seen;
}

/** Remet à zéro la mémoire de session (tests). */
export function resetPremiumOfferSession(): void {
  closedThisSession.clear();
  newsSeenBeforeSession.clear();
}

/** Connexion (émission du jeton, en secondes) datant de moins de {@link PREMIUM_OFFER_DAYS_AFTER_LOGIN} jours. */
export function isRecentLogin(loggedInAt: number | undefined, nowMs: number): boolean {
  return loggedInAt !== undefined && nowMs - loggedInAt * 1000 < PREMIUM_OFFER_DAYS_AFTER_LOGIN * DAY_MS;
}

export interface PremiumOfferContext {
  userId: number | null;
  /** `limits.canFollow` du compte : la popup n'est proposée que s'il vaut `false` (compte gratuit). */
  canFollow: boolean | undefined;
  loggedInAt: number | undefined;
  nowMs: number;
  /** « Ne plus me proposer » ; `null` tant que ce n'est pas lu. */
  optedOut: boolean | null;
  closedThisSession: boolean;
  /** Popup de nouveauté vue avant ce lancement ; `null` tant que ce n'est pas lu. */
  newsSeenBefore: boolean | null;
  /** L'accueil de première connexion s'est ouvert pendant ce lancement. */
  onboardingShown: boolean;
}

/**
 * Popup Premium à proposer : compte connu comme gratuit, connecté depuis moins de 7 jours, qui ne l'a ni refusée ni
 * fermée pendant ce lancement, et qui a déjà vu la popup de nouveauté lors d'un lancement précédent.
 */
export function shouldShowPremiumOffer(context: PremiumOfferContext): boolean {
  return (
    context.userId !== null &&
    context.canFollow === false &&
    isRecentLogin(context.loggedInAt, context.nowMs) &&
    context.optedOut === false &&
    !context.closedThisSession &&
    context.newsSeenBefore === true &&
    !context.onboardingShown
  );
}
