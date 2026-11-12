import { isOpenWithoutAccount } from '../signInRules';

describe('isOpenWithoutAccount', () => {
  it('laisse la connexion / inscription et « Mot de passe oublié » accessibles sans compte', () => {
    expect(isOpenWithoutAccount(['(tabs)', 'compte'])).toBe(true);
    expect(isOpenWithoutAccount(['mot-de-passe-oublie'])).toBe(true);
  });

  it('ferme la carte, la liste et tous les autres écrans sans compte', () => {
    expect(isOpenWithoutAccount([])).toBe(false);
    expect(isOpenWithoutAccount(['(tabs)'])).toBe(false);
    expect(isOpenWithoutAccount(['(tabs)', 'liste'])).toBe(false);
    expect(isOpenWithoutAccount(['(tabs)', 'contribution'])).toBe(false);
    expect(isOpenWithoutAccount(['activite', '[id]'])).toBe(false);
    expect(isOpenWithoutAccount(['foodtruck', '[id]'])).toBe(false);
    expect(isOpenWithoutAccount(['guide'])).toBe(false);
  });
});
