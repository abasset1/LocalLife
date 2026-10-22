import { HttpError } from '@/api/errors';

import { buildRegisterRequest, isTermsConsentRequired, retryGoogleWithConsent } from '../consent';

const FORM = { username: ' marie ', email: ' marie@exemple.fr ', password: 'secret123', acceptTerms: true };

describe("consentement à l'inscription", () => {
  it("n'envoie rien tant que la case n'est pas cochée", () => {
    expect(buildRegisterRequest({ ...FORM, acceptTerms: false })).toBeNull();
  });

  it('envoie acceptTerms: true avec les champs nettoyés', () => {
    expect(buildRegisterRequest(FORM)).toEqual({
      username: 'marie',
      email: 'marie@exemple.fr',
      password: 'secret123',
      acceptTerms: true,
    });
  });
});

describe('connexion Google : 428', () => {
  const consentRequired = new HttpError(428, 'Coche la case : tu dois avoir au moins 15 ans…');

  it('reconnaît la réponse 428 et seulement elle', () => {
    expect(isTermsConsentRequired(consentRequired)).toBe(true);
    expect(isTermsConsentRequired(new HttpError(401, 'Refusé'))).toBe(false);
    expect(isTermsConsentRequired(new Error('x'))).toBe(false);
  });

  it("réutilise l'ID token déjà obtenu, avec acceptTerms", async () => {
    const login = jest.fn().mockResolvedValue(undefined);
    const requestNew = jest.fn();
    await expect(retryGoogleWithConsent('token-1', login, requestNew)).resolves.toBe(true);
    expect(login).toHaveBeenCalledWith('token-1', true);
    expect(requestNew).not.toHaveBeenCalled();
  });

  it('relance Google si le token a expiré (401)', async () => {
    const login = jest.fn().mockRejectedValueOnce(new HttpError(401, 'Expiré')).mockResolvedValueOnce(undefined);
    const requestNew = jest.fn().mockResolvedValue('token-2');
    await expect(retryGoogleWithConsent('token-1', login, requestNew)).resolves.toBe(true);
    expect(login).toHaveBeenNthCalledWith(2, 'token-2', true);
  });

  it("s'arrête si l'utilisateur annule le second passage par Google", async () => {
    const login = jest.fn().mockRejectedValueOnce(new HttpError(401, 'Expiré'));
    await expect(retryGoogleWithConsent('token-1', login, () => Promise.resolve(null))).resolves.toBe(false);
    expect(login).toHaveBeenCalledTimes(1);
  });

  it('propage les autres erreurs sans relancer Google', async () => {
    const login = jest.fn().mockRejectedValue(new HttpError(423, 'Ce compte est suspendu.'));
    const requestNew = jest.fn();
    await expect(retryGoogleWithConsent('token-1', login, requestNew)).rejects.toThrow('Ce compte est suspendu.');
    expect(requestNew).not.toHaveBeenCalled();
  });
});
