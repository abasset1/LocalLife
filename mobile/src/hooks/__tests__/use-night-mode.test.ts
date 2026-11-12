import { Colors } from '@/constants/theme';

import { isNightMode, setNightMode, subscribeNightMode, suspendNightMode } from '../use-night-mode';
import { themeNameFor } from '../use-theme';

// `constants/theme` importe une feuille de style que Jest ne sait pas lire (remonté avant les imports par Jest).
jest.mock('@/global.css', () => ({}));

afterEach(() => setNightMode(false));

describe('thème « Night »', () => {
  it('peut être suspendu le temps d’un écran, puis revient si la carte Night est toujours choisie', () => {
    const listener = jest.fn();
    const unsubscribe = subscribeNightMode(listener);
    setNightMode(true);
    const release = suspendNightMode();
    expect(isNightMode()).toBe(false);
    release();
    release();
    expect(isNightMode()).toBe(true);
    expect(listener).toHaveBeenCalledTimes(3);
    const releaseAgain = suspendNightMode();
    setNightMode(false);
    releaseAgain();
    expect(isNightMode()).toBe(false);
    unsubscribe();
  });

  it('s’active et prévient les écrans une seule fois par changement', () => {
    const listener = jest.fn();
    const unsubscribe = subscribeNightMode(listener);
    expect(isNightMode()).toBe(false);
    setNightMode(true);
    setNightMode(true);
    expect(isNightMode()).toBe(true);
    expect(listener).toHaveBeenCalledTimes(1);
    setNightMode(false);
    expect(listener).toHaveBeenCalledTimes(2);
    unsubscribe();
    setNightMode(true);
    expect(listener).toHaveBeenCalledTimes(2);
  });

  it('remplace le thème clair ou sombre tant que la carte Night est choisie', () => {
    expect(themeNameFor('light', false)).toBe('light');
    expect(themeNameFor('dark', false)).toBe('dark');
    expect(themeNameFor(null, false)).toBe('light');
    expect(themeNameFor('light', true)).toBe('night');
    expect(themeNameFor('dark', true)).toBe('night');
  });

  it('reprend les couleurs du mode Night du site', () => {
    expect(Colors.night.background).toBe('#0E061D');
    expect(Colors.night.chrome).toBe('#120726');
    expect(Colors.night.primary).toBe('#FF3FD1');
    expect(Object.keys(Colors.night).sort()).toEqual(Object.keys(Colors.light).sort());
  });
});
