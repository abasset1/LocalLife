import type { FeedbackKind } from '@/api';

/** Bornes du message, identiques au backend (`FeedbackService`) et au site web. */
export const FEEDBACK_MIN_LENGTH = 5;
export const FEEDBACK_MAX_LENGTH = 2000;

/** Choix du type de message, comme le formulaire du site web. */
export const FEEDBACK_KINDS: readonly { value: FeedbackKind; label: string; placeholder: string }[] = [
  { value: 'SUGGESTION', label: '💡 Une idée', placeholder: 'Qu’est-ce qui rendrait LocalLife plus utile pour vous ?' },
  { value: 'BUG', label: '🐞 Un problème', placeholder: 'Que s’est-il passé ? Qu’attendiez-vous à la place ?' },
];

/** Message d'erreur avant envoi, ou `null` si le message peut partir (le backend revérifie). */
export function feedbackMessageError(message: string): string | null {
  const length = message.trim().length;
  if (length < FEEDBACK_MIN_LENGTH) return 'Écrivez quelques mots pour décrire votre idée ou le problème.';
  if (length > FEEDBACK_MAX_LENGTH) return `Le message ne doit pas dépasser ${FEEDBACK_MAX_LENGTH} caractères.`;
  return null;
}

/**
 * Origine envoyée avec le message (`pageUrl`) : l'application et son système, utiles pour reproduire une anomalie
 * (l'en-tête User-Agent d'une application ne le dit pas).
 */
export function feedbackOrigin(os: string, version: string | number): string {
  return `Application mobile (${os} ${version})`;
}
