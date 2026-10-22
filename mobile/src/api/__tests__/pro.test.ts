import { configureApiClient } from '../http';
import { listPlaceChanges, proposePlaceChange, renouncePlace, withdrawPlaceChange } from '../pro';

jest.mock('@/config/env', () => ({ apiUrl: 'https://api.test/' }));

const fetchMock = jest.fn();

beforeEach(() => {
  fetchMock.mockReset();
  global.fetch = fetchMock as unknown as typeof fetch;
  configureApiClient({ getToken: () => 'jeton' });
});

const INPUT = {
  address: '1 place du Marché',
  postalCode: '13001',
  city: 'Marseille',
  description: 'Halle couverte',
  website: '',
  phone: '',
  email: '',
};

describe('lieux gérés : propositions de modification', () => {
  it('envoie la proposition à `/pro/places/{id}/changes`, authentifiée', async () => {
    fetchMock.mockResolvedValue({ ok: true, status: 201, text: () => Promise.resolve('{"id":3,"status":"PENDING"}') });

    await expect(proposePlaceChange(7, INPUT)).resolves.toMatchObject({ id: 3, status: 'PENDING' });

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('https://api.test/api/v1/pro/places/7/changes');
    expect(init.method).toBe('POST');
    expect(JSON.parse(init.body)).toEqual(INPUT);
    expect(init.headers.Authorization).toBe('Bearer jeton');
  });

  it('liste, retire une proposition et renonce au lieu', async () => {
    fetchMock.mockResolvedValue({ ok: true, status: 200, text: () => Promise.resolve('[]') });
    await expect(listPlaceChanges(7)).resolves.toEqual([]);
    expect(fetchMock.mock.calls[0][0]).toBe('https://api.test/api/v1/pro/places/7/changes');

    fetchMock.mockResolvedValue({ ok: true, status: 200, text: () => Promise.resolve('{"id":3,"status":"WITHDRAWN"}') });
    await withdrawPlaceChange(3);
    expect(fetchMock.mock.calls[1][0]).toBe('https://api.test/api/v1/pro/place-changes/3/withdraw');
    expect(fetchMock.mock.calls[1][1].method).toBe('POST');

    fetchMock.mockResolvedValue({ ok: true, status: 204, text: () => Promise.resolve('') });
    await renouncePlace(7);
    expect(fetchMock.mock.calls[2][0]).toBe('https://api.test/api/v1/pro/places/7/renounce');
    expect(fetchMock.mock.calls[2][1].method).toBe('POST');
  });
});
