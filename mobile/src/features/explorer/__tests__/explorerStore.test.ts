import type { UserPreferences } from '@/api';

import {
  applyPreferences,
  clearCategories,
  getExplorerState,
  resetExplorer,
  setPlace,
  setRadius,
  subscribe,
  toggleCategory,
} from '../explorerStore';

const PREFERENCES: UserPreferences = {
  defaultCity: 'Avignon',
  defaultLatitude: 43.95,
  defaultLongitude: 4.81,
  defaultRadiusKm: 10,
  defaultCategoryCodes: ['CONCERT'],
  configured: true,
};

const LYON = { label: 'Lyon (69)', latitude: 45.758, longitude: 4.835, source: 'city' as const };

beforeEach(() => {
  resetExplorer();
});

describe('explorerStore', () => {
  it('démarre sans lieu, sans catégorie et sans rayon : aucune ville supposée', () => {
    expect(getExplorerState()).toEqual({ place: null, categoryCodes: [], radiusKm: null });
  });

  it('coche puis décoche une ou plusieurs catégories, et les retire toutes', () => {
    toggleCategory('CONCERT');
    toggleCategory('MARCHE');
    expect(getExplorerState().categoryCodes).toEqual(['CONCERT', 'MARCHE']);
    toggleCategory('CONCERT');
    expect(getExplorerState().categoryCodes).toEqual(['MARCHE']);
    clearCategories();
    expect(getExplorerState().categoryCodes).toEqual([]);
  });

  it('prévient les vues abonnées (carte et liste) de chaque changement', () => {
    const listener = jest.fn();
    const unsubscribe = subscribe(listener);
    setPlace(LYON);
    setRadius(20);
    unsubscribe();
    toggleCategory('CONCERT');
    expect(listener).toHaveBeenCalledTimes(2);
  });

  it('applique les paramètres de recherche une seule fois par version', () => {
    expect(applyPreferences(PREFERENCES)).toBe(true);
    expect(getExplorerState()).toEqual({
      place: { label: 'Avignon', latitude: 43.95, longitude: 4.81, source: 'preferences' },
      categoryCodes: ['CONCERT'],
      radiusKm: 10,
    });

    // L'utilisateur retire le filtre : relire les mêmes paramètres (retour sur l'écran) ne l'écrase pas.
    clearCategories();
    expect(applyPreferences({ ...PREFERENCES })).toBe(false);
    expect(getExplorerState().categoryCodes).toEqual([]);

    // Paramètres modifiés : ils s'appliquent de nouveau.
    expect(applyPreferences({ ...PREFERENCES, defaultCategoryCodes: ['MARCHE'] })).toBe(true);
    expect(getExplorerState().categoryCodes).toEqual(['MARCHE']);
  });

  it('garde la ville choisie par l’utilisateur quand les paramètres changent', () => {
    setPlace(LYON);
    applyPreferences(PREFERENCES);
    expect(getExplorerState().place).toEqual(LYON);
  });

  it('ignore une ville enregistrée sans coordonnées valides', () => {
    applyPreferences({ ...PREFERENCES, defaultLatitude: null });
    expect(getExplorerState().place).toBeNull();
    applyPreferences({ ...PREFERENCES, defaultLatitude: 123 });
    expect(getExplorerState().place).toBeNull();
  });

  it('à la déconnexion, retire les critères venus des paramètres mais pas une ville choisie', () => {
    applyPreferences(PREFERENCES);
    applyPreferences(null);
    expect(getExplorerState()).toEqual({ place: null, categoryCodes: [], radiusKm: null });

    setPlace(LYON);
    applyPreferences(PREFERENCES);
    applyPreferences(null);
    expect(getExplorerState().place).toEqual(LYON);
  });
});
