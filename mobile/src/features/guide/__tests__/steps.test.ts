import { GUIDE_STEPS, clampStep, nextAutoplayStep } from '../steps';

describe('guide de l’application', () => {
  it('présente les possibilités principales, de la bienvenue à la fin', () => {
    const ids = GUIDE_STEPS.map((step) => step.id);
    expect(ids[0]).toBe('bienvenue');
    expect(ids[ids.length - 1]).toBe('fin');
    expect(ids).toEqual(
      expect.arrayContaining(['carte', 'recherche', 'filtres', 'couches', 'favoris', 'alertes', 'contribuer', 'compte', 'offres']),
    );
    expect(new Set(ids).size).toBe(ids.length);
  });

  it('donne à chaque étape un titre, un texte et une scène non vide', () => {
    for (const step of GUIDE_STEPS) {
      expect(step.title.trim()).not.toBe('');
      expect(step.text.trim()).not.toBe('');
      switch (step.scene.kind) {
        case 'hero':
          expect(step.scene.orbit.length).toBeGreaterThan(0);
          break;
        case 'map':
          expect(step.scene.layers.every((layer) => layer.pins.length > 0)).toBe(true);
          break;
        case 'search':
        case 'rows':
          expect(step.scene.rows.length).toBeGreaterThan(0);
          break;
        case 'chips':
          for (const group of step.scene.groups) {
            expect(group.selected.every((index) => index >= 0 && index < group.chips.length)).toBe(true);
          }
          break;
        case 'flow':
          expect(step.scene.stages.length).toBeGreaterThan(1);
          break;
      }
    }
  });

  it('borne la navigation et arrête la lecture automatique à la dernière étape', () => {
    const last = GUIDE_STEPS.length - 1;
    expect(clampStep(-1)).toBe(0);
    expect(clampStep(last + 3)).toBe(last);
    expect(nextAutoplayStep(0)).toBe(1);
    expect(nextAutoplayStep(last)).toBeNull();
  });
});
