import type { Activity } from '@/api/types';

/**
 * Filtre « Gratuit » de la carte : seules les activités dont les tarifs disent qu'elles sont gratuites
 * (`priceType` = `FREE`, déduit par le backend). Une activité sans tarif connu n'est pas retenue.
 */
export function keepFreeActivities<T extends Pick<Activity, 'priceType'>>(activities: T[], freeOnly: boolean): T[] {
  return freeOnly ? activities.filter((activity) => activity.priceType === 'FREE') : activities;
}
