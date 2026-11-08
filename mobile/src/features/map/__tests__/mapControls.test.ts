import {
  categoriesChipLabel,
  layerTabs,
  listButtonLabel,
  periodChipLabel,
  radiusChipLabel,
} from '../mapControls';

describe('commandes de la carte en clair', () => {
  it('propose des onglets nommés, lieux et food trucks seulement s’ils sont ouverts', () => {
    expect(layerTabs({ places: false, foodTrucks: false }).map((tab) => tab.label)).toEqual([
      '📅 Activités',
      '🍸 Night',
    ]);
    expect(layerTabs({ places: true, foodTrucks: true }).map((tab) => tab.value)).toEqual([
      'activities',
      'places',
      'foodTrucks',
      'night',
    ]);
  });

  it('affiche la valeur choisie dans chaque filtre', () => {
    expect(periodChipLabel('all')).toBe('🗓 Toutes dates');
    expect(periodChipLabel('weekend')).toBe('🗓 Ce week-end');
    expect(categoriesChipLabel(0)).toBe('🏷 Catégories');
    expect(categoriesChipLabel(1)).toBe('🏷 1 catégorie');
    expect(categoriesChipLabel(3)).toBe('🏷 3 catégories');
    expect(radiusChipLabel(null)).toBe('📍 Rayon');
    expect(radiusChipLabel(20)).toBe('📍 20 km');
  });

  it('nomme le bouton de liste selon le contenu affiché', () => {
    expect(listButtonLabel('activities', 37)).toBe('Liste (37)');
    expect(listButtonLabel('night', 4)).toBe('Liste (4)');
    expect(listButtonLabel('places', 5)).toBe('Liste des lieux (5)');
  });
});
