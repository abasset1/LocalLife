import { exportMyData } from '../account';
import { loginWithGoogle, register } from '../auth';
import { HttpError } from '../errors';
import { configureApiClient } from '../http';

jest.mock('@/config/env', () => ({ apiUrl: 'https://api.test/' }));

const fetchMock = jest.fn();

function respond(status: number, body: string) {
  fetchMock.mockResolvedValue({ ok: status < 400, status, text: () => Promise.resolve(body) });
}

beforeEach(() => {
  fetchMock.mockReset();
  global.fetch = fetchMock as unknown as typeof fetch;
  configureApiClient({ getToken: () => 'jeton' });
});

describe('inscription', () => {
  it('envoie acceptTerms: true, sans JWT', async () => {
    respond(201, '{"id":1}');
    await register({ username: 'marie', email: 'marie@exemple.fr', password: 'secret123', acceptTerms: true });
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('https://api.test/api/v1/auth/register');
    expect(JSON.parse(init.body)).toEqual({
      username: 'marie',
      email: 'marie@exemple.fr',
      password: 'secret123',
      acceptTerms: true,
    });
    expect(init.headers.Authorization).toBeUndefined();
  });
});

describe('connexion Google', () => {
  it("n'envoie acceptTerms que s'il est accepté", async () => {
    respond(200, '{"token":"t"}');
    await loginWithGoogle('id-token');
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ idToken: 'id-token' });
    await loginWithGoogle('id-token', true);
    expect(JSON.parse(fetchMock.mock.calls[1][1].body)).toEqual({ idToken: 'id-token', acceptTerms: true });
  });

  it('remonte le 428 avec le message du serveur', async () => {
    respond(428, '{"status":428,"message":"Coche la case."}');
    const error = await loginWithGoogle('id-token').catch((e: unknown) => e);
    expect(error).toBeInstanceOf(HttpError);
    expect(error).toMatchObject({ status: 428, message: 'Coche la case.' });
  });
});

describe('export des données', () => {
  it('appelle GET /users/me/export avec le JWT et renvoie le JSON', async () => {
    respond(200, '{"account":{"id":1},"favorites":[]}');
    await expect(exportMyData()).resolves.toEqual({ account: { id: 1 }, favorites: [] });
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('https://api.test/api/v1/users/me/export');
    expect(init.method).toBe('GET');
    expect(init.headers.Authorization).toBe('Bearer jeton');
  });
});
