import { configureApiClient } from '../http';
import { searchPlacesWithinBounds } from '../places';

jest.mock('@/config/env', () => ({ apiUrl: 'https://api.test/' }));

const fetchMock = jest.fn();

beforeEach(() => {
  fetchMock.mockReset();
  global.fetch = fetchMock as unknown as typeof fetch;
  configureApiClient({ getToken: () => null });
});

describe('searchPlacesWithinBounds', () => {
  it('traduit la zone visible dans le contrat de `/places/within-bounds`', async () => {
    fetchMock.mockResolvedValue({ ok: true, status: 200, text: () => Promise.resolve('[]') });

    await expect(
      searchPlacesWithinBounds({ swLatitude: 43.9, swLongitude: 4.7, neLatitude: 44, neLongitude: 4.9 }),
    ).resolves.toEqual([]);

    expect(fetchMock.mock.calls[0][0]).toBe(
      'https://api.test/api/v1/places/within-bounds?minLat=43.9&maxLat=44&minLng=4.7&maxLng=4.9',
    );
  });
});
