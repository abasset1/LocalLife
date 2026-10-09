import { HttpError, NetworkError, RequestAbortedError, ResponseFormatError, TimeoutError } from '../errors';
import { buildUrl, configureApiClient, request } from '../http';

jest.mock('@/config/env', () => ({ apiUrl: 'https://api.test/' }));

const fetchMock = jest.fn();

/** Réponse minimale : `text()` est la seule lecture de corps faite par le client. */
function response(status: number, text: string | Promise<string>) {
  return { ok: status >= 200 && status < 300, status, text: () => Promise.resolve(text) };
}

/** Promesse qui échoue quand le signal de la requête est annulé (comme `fetch`). */
function rejectOnAbort(signal: AbortSignal): Promise<never> {
  return new Promise((_, reject) => {
    signal.addEventListener('abort', () => reject(new Error('aborted')));
  });
}

beforeEach(() => {
  fetchMock.mockReset();
  global.fetch = fetchMock as unknown as typeof fetch;
  configureApiClient({ getToken: () => null });
});

describe('buildUrl', () => {
  it('assemble base, chemin et paramètres en ignorant les valeurs vides', () => {
    expect(buildUrl('/api/v1/activities', { city: 'Aix en Provence', date: undefined, sort: ['city', 'date'], q: '' }))
      .toBe('https://api.test/api/v1/activities?city=Aix%20en%20Provence&sort=city%2Cdate');
  });
});

describe('request', () => {
  it('retourne le JSON de la réponse', async () => {
    fetchMock.mockResolvedValue(response(200, '[{"id":1}]'));
    await expect(request('/x')).resolves.toEqual([{ id: 1 }]);
  });

  it("n'envoie le JWT que sur les appels authentifiés", async () => {
    configureApiClient({ getToken: () => 'jeton' });
    fetchMock.mockResolvedValue(response(200, ''));

    await request('/public');
    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBeUndefined();

    await request('/protege', { authenticated: true });
    expect(fetchMock.mock.calls[1][1].headers.Authorization).toBe('Bearer jeton');
  });

  it('lève une HttpError avec le message du backend', async () => {
    fetchMock.mockResolvedValue(response(400, '{"message":"Adresse introuvable"}'));
    const error = await request('/x').catch((e: unknown) => e);
    expect(error).toBeInstanceOf(HttpError);
    expect(error).toMatchObject({ status: 400, message: 'Adresse introuvable' });
  });

  it('signale un 401 uniquement sur un appel authentifié', async () => {
    const onUnauthorized = jest.fn();
    configureApiClient({ getToken: () => 'jeton', onUnauthorized });
    fetchMock.mockResolvedValue(response(401, ''));

    await expect(request('/login')).rejects.toBeInstanceOf(HttpError);
    expect(onUnauthorized).not.toHaveBeenCalled();

    await expect(request('/protege', { authenticated: true })).rejects.toBeInstanceOf(HttpError);
    expect(onUnauthorized).toHaveBeenCalledTimes(1);
  });

  it("rejoue sans JWT un appel à authentification optionnelle refusé", async () => {
    const onUnauthorized = jest.fn();
    configureApiClient({ getToken: () => 'jeton-perime', onUnauthorized });
    fetchMock.mockResolvedValueOnce(response(401, '')).mockResolvedValueOnce(response(200, '{"id":1}'));

    await expect(request('/activite', { authenticated: 'optional' })).resolves.toEqual({ id: 1 });
    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBe('Bearer jeton-perime');
    expect(fetchMock.mock.calls[1][1].headers.Authorization).toBeUndefined();
    expect(onUnauthorized).toHaveBeenCalledTimes(1);
  });

  it("n'envoie rien et ne rejoue rien en authentification optionnelle sans session", async () => {
    fetchMock.mockResolvedValue(response(404, ''));
    await expect(request('/activite', { authenticated: 'optional' })).rejects.toMatchObject({ status: 404 });
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBeUndefined();
  });

  it('distingue réseau, format et annulation', async () => {
    fetchMock.mockRejectedValueOnce(new TypeError('Network request failed'));
    await expect(request('/x')).rejects.toBeInstanceOf(NetworkError);

    fetchMock.mockResolvedValueOnce(response(200, '<html>'));
    await expect(request('/x')).rejects.toBeInstanceOf(ResponseFormatError);

    const controller = new AbortController();
    fetchMock.mockImplementationOnce((_url: string, init: { signal: AbortSignal }) => rejectOnAbort(init.signal));
    const pending = request('/x', { signal: controller.signal });
    controller.abort();
    await expect(pending).rejects.toBeInstanceOf(RequestAbortedError);
  });

  it("n'aboutit pas si l'annulation survient pendant la lecture du corps", async () => {
    const controller = new AbortController();
    let deliverBody: (text: string) => void = () => undefined;
    const body = new Promise<string>((resolve) => {
      deliverBody = resolve;
    });
    fetchMock.mockResolvedValue(response(200, body));

    const pending = request('/x', { signal: controller.signal });
    await Promise.resolve(); // les en-têtes sont arrivés, le corps est en cours de lecture
    controller.abort();
    deliverBody('[{"id":1}]');

    await expect(pending).rejects.toBeInstanceOf(RequestAbortedError);
  });

  it('applique le délai maximal à la lecture du corps', async () => {
    fetchMock.mockImplementation((_url: string, init: { signal: AbortSignal }) =>
      Promise.resolve({ ok: true, status: 200, text: () => rejectOnAbort(init.signal) }),
    );
    await expect(request('/x', { timeoutMs: 20 })).rejects.toBeInstanceOf(TimeoutError);
  });
});
