import { buildUrl } from '@/api/http';

/** Adresse complète de la photo d'un food truck (le backend renvoie un chemin), `null` sans photo. */
export function photoUri(photoUrl: string | null | undefined): string | null {
  if (!photoUrl) return null;
  return /^https?:\/\//.test(photoUrl) ? photoUrl : buildUrl(photoUrl);
}
