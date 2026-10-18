import { configureApiClient } from '../http';
import { suggestCities } from '../geocoding';

jest.mock('@/config/env', () => ({ apiUrl: 'https://api.test/' }));

const fetchMock = jest.fn();

beforeEach(() => {
  fetchMock.mockReset();
  global.fetch = fetchMock as unknown as typeof fetch;
  configureApiClient({ getToken: () => 'jeton' });
});

describe('suggestCities', () => {
  it('interroge `/geocoding/city-suggestions` sans joindre le jeton (endpoint public)', async () => {
    const lyon = { name: 'Lyon', postalCodes: ['69001'], department: '69', latitude: 45.758, longitude: 4.835 };
    fetchMock.mockResolvedValue({ ok: true, status: 200, text: () => Promise.resolve(JSON.stringify([lyon])) });

    await expect(suggestCities('saint é')).resolves.toEqual([lyon]);

    expect(fetchMock.mock.calls[0][0]).toBe('https://api.test/api/v1/geocoding/city-suggestions?q=saint%20%C3%A9');
    const headers = new Headers(fetchMock.mock.calls[0][1]?.headers);
    expect(headers.has('Authorization')).toBe(false);
  });

  it('remonte une panne du service des communes comme une erreur', async () => {
    fetchMock.mockResolvedValue({
      ok: false,
      status: 503,
      text: () => Promise.resolve(JSON.stringify({ message: 'Service des communes indisponible.' })),
    });
    await expect(suggestCities('lyon')).rejects.toThrow('Service des communes indisponible.');
  });
});
