import { configureApiClient } from '../http';
import { cancelActivity, deleteActivity, listActivitiesAtPlace } from '../activities';
import { login } from '../auth';
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

describe('listActivitiesAtPlace', () => {
  it('appelle `/activities/at-place/{id}`', async () => {
    fetchMock.mockResolvedValue({ ok: true, status: 200, text: () => Promise.resolve('[]') });
    await expect(listActivitiesAtPlace(7)).resolves.toEqual([]);
    expect(fetchMock.mock.calls[0][0]).toBe('https://api.test/api/v1/activities/at-place/7');
  });
});

describe('deleteActivity', () => {
  it('ajoute le motif d’un administrateur en paramètre `reason`, rien sinon', async () => {
    fetchMock.mockResolvedValue({ ok: true, status: 204, text: () => Promise.resolve('') });
    await deleteActivity(7);
    await deleteActivity(7, { reason: 'Doublon' });
    expect(fetchMock.mock.calls[0][0]).toBe('https://api.test/api/v1/activities/7');
    expect(fetchMock.mock.calls[1][0]).toBe('https://api.test/api/v1/activities/7?reason=Doublon');
    expect(fetchMock.mock.calls[1][1].method).toBe('DELETE');
  });
});

describe('cancelActivity', () => {
  it('envoie le motif d’un administrateur dans le corps, aucun corps sinon', async () => {
    fetchMock.mockResolvedValue({ ok: true, status: 200, text: () => Promise.resolve('{"id":7,"status":"CANCELLED"}') });
    await cancelActivity(7);
    await cancelActivity(7, { reason: 'Événement reporté' });
    expect(fetchMock.mock.calls[0][0]).toBe('https://api.test/api/v1/activities/7/cancel');
    expect(fetchMock.mock.calls[0][1].method).toBe('PATCH');
    expect(fetchMock.mock.calls[0][1].body).toBeUndefined();
    expect(JSON.parse(fetchMock.mock.calls[1][1].body)).toEqual({ reason: 'Événement reporté' });
  });
});

describe('login', () => {
  it('remonte le message du serveur après trop de tentatives (429)', async () => {
    const message = 'Trop de tentatives de connexion. Réessaie dans 15 minutes.';
    fetchMock.mockResolvedValue({ ok: false, status: 429, text: () => Promise.resolve(JSON.stringify({ status: 429, message })) });
    await expect(login({ email: 'a@b.fr', password: 'x' })).rejects.toMatchObject({ status: 429, message });
  });
});
