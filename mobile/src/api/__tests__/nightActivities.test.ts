import { configureApiClient } from '../http';
import { searchNearby, searchWithinBounds } from '../activities';

jest.mock('@/config/env', () => ({ apiUrl: 'https://api.test/' }));

const fetchMock = jest.fn();

beforeEach(() => {
  fetchMock.mockReset();
  fetchMock.mockResolvedValue({ ok: true, status: 200, text: () => Promise.resolve('[]') });
  global.fetch = fetchMock as unknown as typeof fetch;
  configureApiClient({ getToken: () => null });
});

const BOUNDS = { swLatitude: 43.2, swLongitude: 5.3, neLatitude: 43.4, neLongitude: 5.5 };

describe('carte « Night »', () => {
  it('demande les seules activités de nuit avec `night=true`', async () => {
    await searchWithinBounds({ ...BOUNDS, night: true });
    await searchNearby({ latitude: 43.3, longitude: 5.4, radiusKm: 5, night: true });

    expect(fetchMock.mock.calls[0][0]).toBe(
      'https://api.test/api/v1/activities/within-bounds?swLatitude=43.2&swLongitude=5.3&neLatitude=43.4&neLongitude=5.5&night=true',
    );
    expect(fetchMock.mock.calls[1][0]).toBe(
      'https://api.test/api/v1/activities/nearby?latitude=43.3&longitude=5.4&radius=5&night=true',
    );
  });

  it("n'envoie pas `night` pour la carte des activités", async () => {
    await searchWithinBounds({ ...BOUNDS, night: false });
    await searchWithinBounds(BOUNDS);

    expect(fetchMock.mock.calls[0][0]).not.toContain('night');
    expect(fetchMock.mock.calls[1][0]).not.toContain('night');
  });
});
