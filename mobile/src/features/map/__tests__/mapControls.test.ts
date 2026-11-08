import {
  categoriesChipLabel,
  hasActiveFilters,
  layerContents,
  layerTabs,
  listButtonLabel,
  periodChipLabel,
  radiusChipLabel,
} from '../mapControls';

const ALL = { places: true, foodTrucks: true };
const NONE = { places: false, foodTrucks: false };

describe('commandes de la carte', () => {
  it('propose Tous, Lieux, Food trucks et Night, Lieux et Food trucks seulement s’ils sont ouverts', () => {
    expect(layerTabs(ALL).map((tab) => tab.label)).toEqual(['Tous', 'Lieux', 'Food trucks', 'Night']);
    expect(layerTabs(NONE).map((tab) => tab.value)).toEqual(['activities', 'night']);
  });

  it('« Tous » mélange activités, lieux et food trucks ouverts ; Night ne montre que ses activités', () => {
    expect(layerContents('activities', ALL)).toEqual({ activities: true, places: true, foodTrucks: true, night: false });
    expect(layerContents('activities', NONE)).toEqual({
      activities: true,
      places: false,
      foodTrucks: false,
      night: false,
    });
    expect(layerContents('places', ALL)).toEqual({ activities: false, places: true, foodTrucks: false, night: false });
    expect(layerContents('foodTrucks', ALL)).toEqual({
      activities: false,
      places: false,
      foodTrucks: true,
      night: false,
    });
    expect(layerContents('night', ALL)).toEqual({ activities: true, places: false, foodTrucks: false, night: true });
  });

  it('affiche la valeur choisie dans chaque filtre', () => {
    expect(periodChipLabel('all')).toBe('Toutes dates');
    expect(periodChipLabel('weekend')).toBe('Ce week-end');
    expect(categoriesChipLabel(0)).toBe('Catégories');
    expect(categoriesChipLabel(1)).toBe('1 catégorie');
    expect(categoriesChipLabel(3)).toBe('3 catégories');
    expect(radiusChipLabel(null)).toBe('Rayon');
    expect(radiusChipLabel(20)).toBe('20 km');
  });

  it('compte les résultats dans le bouton de liste', () => {
    expect(listButtonLabel(37)).toBe('Voir la liste (37)');
  });

  it('détecte un filtre posé, pour proposer « Réinitialiser »', () => {
    const none = { period: 'all' as const, categoryCount: 0, radiusKm: null, freeOnly: false };
    expect(hasActiveFilters(none)).toBe(false);
    expect(hasActiveFilters({ ...none, period: 'today' })).toBe(true);
    expect(hasActiveFilters({ ...none, categoryCount: 2 })).toBe(true);
    expect(hasActiveFilters({ ...none, radiusKm: 20 })).toBe(true);
    expect(hasActiveFilters({ ...none, freeOnly: true })).toBe(true);
  });
});
