import type { Period } from '@/features/list/filters';

import type { MapLayer } from './placesPreference';

/**
 * Libellés des commandes de la carte, écrits en clair comme sur le site (page Explorer) plutôt qu'en icônes seules :
 * onglets de contenu, filtres qui affichent leur valeur, boutons du bas.
 */

export interface LayerTab {
  value: MapLayer;
  label: string;
  /** Libellé accessible : ce que l'onglet affiche. */
  description: string;
}

const LAYER_TABS: readonly LayerTab[] = [
  { value: 'activities', label: '📅 Activités', description: 'Afficher les activités' },
  { value: 'places', label: '🏛 Lieux', description: 'Afficher les lieux : musées, monuments, équipements…' },
  { value: 'foodTrucks', label: '🚚 Food trucks', description: 'Afficher les food trucks présents aujourd’hui' },
  { value: 'night', label: '🍸 Night', description: 'Afficher la vie nocturne, de 20 h à 6 h : soirées, concerts, festivals…' },
];

/** Onglets proposés : activités et Night toujours, lieux et food trucks quand ils sont ouverts au lancement. */
export function layerTabs(features: { places: boolean; foodTrucks: boolean }): LayerTab[] {
  return LAYER_TABS.filter(
    (tab) => (tab.value !== 'places' || features.places) && (tab.value !== 'foodTrucks' || features.foodTrucks),
  );
}

const PERIOD_LABELS: Record<Period, string> = {
  all: 'Toutes dates',
  today: 'Aujourd’hui',
  weekend: 'Ce week-end',
  week: '7 jours',
};

/** « 🗓 Toutes dates », « 🗓 Ce week-end »… */
export function periodChipLabel(period: Period): string {
  return `🗓 ${PERIOD_LABELS[period]}`;
}

/** « 🏷 Catégories », « 🏷 1 catégorie », « 🏷 3 catégories ». */
export function categoriesChipLabel(count: number): string {
  if (count === 0) return '🏷 Catégories';
  return `🏷 ${count} catégorie${count > 1 ? 's' : ''}`;
}

/** « 📍 Rayon », « 📍 20 km ». */
export function radiusChipLabel(radiusKm: number | null): string {
  return radiusKm === null ? '📍 Rayon' : `📍 ${radiusKm} km`;
}

/** Bouton du bas qui ouvre la liste de la zone : « Liste (37) », « Liste des lieux (5) ». */
export function listButtonLabel(layer: MapLayer, count: number): string {
  return layer === 'places' ? `Liste des lieux (${count})` : `Liste (${count})`;
}
