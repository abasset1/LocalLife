import { alertError, needsOnboarding, preferencesError } from '../rules';

describe('needsOnboarding', () => {
  it("ne s'affiche que si les préférences n'ont jamais été enregistrées", () => {
    expect(needsOnboarding({ configured: false })).toBe(true);
    expect(needsOnboarding({ configured: true })).toBe(false);
    // Backend antérieur au drapeau : on ne bloque pas l'utilisateur.
    expect(needsOnboarding({})).toBe(false);
  });
});

describe('preferencesError', () => {
  it('exige une ville puis au moins une catégorie', () => {
    expect(preferencesError('  ', ['SPORT'])).toMatch(/ville/);
    expect(preferencesError('Lyon', [])).toMatch(/catégorie/);
    expect(preferencesError('Lyon', ['SPORT'])).toBeNull();
  });
});

describe('alertError', () => {
  it('exige des critères et un canal', () => {
    expect(alertError([], false, true, false)).toMatch(/food trucks/);
    expect(alertError([], true, true, false)).toBeNull();
    expect(alertError(['SPORT'], false, false, false)).toMatch(/moyen/);
    expect(alertError(['SPORT'], false, false, true)).toBeNull();
  });
});
