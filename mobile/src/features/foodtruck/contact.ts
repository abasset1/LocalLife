import { isSafeWebUrl } from '@/features/activity/format';

/**
 * `FoodTruck.contact` est un texte libre (URL, téléphone, réseau social…,
 * voir `FOOD_TRUCK_CONTRACT.md`). Cette fonction ne fait que déterminer
 * comment l'ouvrir ; aucune règle métier n'est dupliquée.
 */
export type ContactAction =
  | { kind: 'web'; url: string; label: string }
  | { kind: 'phone'; url: string; label: string }
  | { kind: 'email'; url: string; label: string }
  | { kind: 'text' };

const PHONE = /^\+?[\d\s().-]{6,}$/;
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function contactAction(contact: string | null | undefined): ContactAction | null {
  const value = contact?.trim();
  if (!value) return null;
  const isWeb: boolean = isSafeWebUrl(value); // évite le rétrécissement de type vers `never`
  if (isWeb) return { kind: 'web', url: value, label: 'Ouvrir le lien' };
  if (PHONE.test(value)) {
    return { kind: 'phone', url: `tel:${value.replace(/[^\d+]/g, '')}`, label: 'Appeler' };
  }
  if (EMAIL.test(value)) return { kind: 'email', url: `mailto:${value}`, label: 'Envoyer un e-mail' };
  return { kind: 'text' };
}
