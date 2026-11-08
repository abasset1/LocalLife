import type { Period } from '@/features/list/filters';

import type { MapLayer } from './placesPreference';

/**
 * Libellés des commandes de la carte (issue #226) : raccourcis d'univers, filtres qui affichent leur valeur, bouton
 * de liste. Chaque commande est écrite en clair, avec un pictogramme système à côté (voir `MapChip`).
 */

export interface LayerTab {
  value: MapLayer;
  label: string;
  /** Libellé accessible : ce que l'onglet affiche. */
  description: string;
}

const LAYER_TABS: readonly LayerTab[] = [
  { value: 'activities', label: 'Tous', description: 'Tout afficher : activités, lieux et food trucks' },
  { value: 'places', label: 'Lieux', description: 'Afficher les lieux : musées, monuments, équipements…' },
  { value: 'foodTrucks', label: 'Food trucks', description: 'Afficher les food trucks présents aujourd’hui' },
  { value: 'night', label: 'Night', description: 'Entrer dans Neiri Night : soirées, concerts, festivals, de 20 h à 6 h' },
];

/** Raccourcis proposés : Tous et Night toujours, Lieux et Food trucks quand ils sont ouverts au lancement. */
export function layerTabs(features: { places: boolean; foodTrucks: boolean }): LayerTab[] {
  return LAYER_TABS.filter(
    (tab) => (tab.value !== 'places' || features.places) && (tab.value !== 'foodTrucks' || features.foodTrucks),
  );
}

/**
 * Contenus montrés par un univers. « Tous » mélange activités, lieux et food trucks (ceux qui sont ouverts) ;
 * Night ne montre que les activités de nuit, avec son propre thème.
 */
export function layerContents(
  layer: MapLayer,
  features: { places: boolean; foodTrucks: boolean },
): { activities: boolean; places: boolean; foodTrucks: boolean; night: boolean } {
  const all = layer === 'activities';
  return {
    activities: all || layer === 'night',
    places: (all || layer === 'places') && features.places,
    foodTrucks: (all || layer === 'foodTrucks') && features.foodTrucks,
    night: layer === 'night',
  };
}

const PERIOD_LABELS: Record<Period, string> = {
  all: 'Toutes dates',
  today: 'Aujourd’hui',
  weekend: 'Ce week-end',
  week: '7 jours',
};

/** « Toutes dates », « Ce week-end »… */
export function periodChipLabel(period: Period): string {
  return PERIOD_LABELS[period];
}

/** « Catégories », « 1 catégorie », « 3 catégories ». */
export function categoriesChipLabel(count: number): string {
  if (count === 0) return 'Catégories';
  return `${count} catégorie${count > 1 ? 's' : ''}`;
}

/** « Rayon », « 20 km ». */
export function radiusChipLabel(radiusKm: number | null): string {
  return radiusKm === null ? 'Rayon' : `${radiusKm} km`;
}

/** Bouton du bas qui ouvre la liste dans le même univers : « Voir la liste (37) ». */
export function listButtonLabel(count: number): string {
  return `Voir la liste (${count})`;
}

/** Filtres des activités posés : la carte propose alors « Réinitialiser ». */
export function hasActiveFilters(filters: {
  period: Period;
  categoryCount: number;
  radiusKm: number | null;
  freeOnly: boolean;
}): boolean {
  return filters.period !== 'all' || filters.categoryCount > 0 || filters.radiusKm !== null || filters.freeOnly;
}
