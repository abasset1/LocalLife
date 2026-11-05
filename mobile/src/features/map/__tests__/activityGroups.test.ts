import type { Activity } from '@/api';

import {
  CLUSTER_POPUP_MAX_ACTIVITIES,
  activitiesAtSamePlace,
  clusterTapAction,
  placeKey,
  positionInGroup,
  sortByStartDate,
  stepInGroup,
  zoneCountLabel,
} from '../activityGroups';

function activity(id: number, overrides: Partial<Activity> = {}): Activity {
  return {
    id,
    title: `Activité ${id}`,
    description: null,
    category: 'MARKETS',
    latitude: 43.9493,
    longitude: 4.8055,
    startDate: null,
    endDate: null,
    status: 'PUBLISHED',
    sourceName: null,
    url: null,
    address: null,
    city: 'Avignon',
    postalCode: null,
    createdBy: null,
    createdByUsername: null,
    likedByCurrentUser: false,
    mainImageUrl: null,
    publishedAt: null,
    ...overrides,
  };
}

describe('regroupements de la carte', () => {
  it('considère au même endroit deux activités à moins de 100 m', () => {
    expect(placeKey({ latitude: 43.94931, longitude: 4.80549 })).toBe(placeKey({ latitude: 43.9494, longitude: 4.8051 }));
    expect(placeKey({ latitude: 43.9493, longitude: 4.8055 })).not.toBe(placeKey({ latitude: 43.951, longitude: 4.8055 }));
  });

  it('trie de la plus proche dans le temps à la plus lointaine, sans date en dernier', () => {
    const sorted = sortByStartDate([
      activity(1),
      activity(2, { startDate: '2026-12-01T10:00:00Z' }),
      activity(3, { startDate: '2026-11-10T10:00:00Z' }),
    ]);
    expect(sorted.map((a) => a.id)).toEqual([3, 2, 1]);
  });

  it('marqueur seul : l’activité touchée d’abord, puis celles au même endroit', () => {
    const touched = activity(1, { startDate: '2026-12-24T10:00:00Z' });
    const all = [
      touched,
      activity(2, { startDate: '2026-12-01T10:00:00Z' }),
      activity(3, { latitude: 44.1 }),
      activity(4, { startDate: '2026-11-10T10:00:00Z' }),
    ];
    expect(activitiesAtSamePlace(touched, all).map((a) => a.id)).toEqual([1, 4, 2]);
    expect(activitiesAtSamePlace(all[2], all).map((a) => a.id)).toEqual([3]);
  });

  it('pastille : zoom seulement au-delà de 10 activités à des endroits différents', () => {
    const spread = (n: number) => Array.from({ length: n }, (_, i) => activity(i, { latitude: 43.9 + i * 0.01 }));
    const samePlace = (n: number) => Array.from({ length: n }, (_, i) => activity(i));
    expect(clusterTapAction(spread(CLUSTER_POPUP_MAX_ACTIVITIES), true)).toBe('browse');
    expect(clusterTapAction(spread(CLUSTER_POPUP_MAX_ACTIVITIES + 1), true)).toBe('zoom');
    expect(clusterTapAction(spread(CLUSTER_POPUP_MAX_ACTIVITIES + 1), false)).toBe('browse');
    expect(clusterTapAction(samePlace(30), true)).toBe('browse');
  });

  it('fait défiler le groupe en boucle et indique la position', () => {
    const group = [activity(1), activity(2), activity(3)];
    expect(stepInGroup(group, group[0], 1).id).toBe(2);
    expect(stepInGroup(group, group[2], 1).id).toBe(1);
    expect(stepInGroup(group, group[0], -1).id).toBe(3);
    expect(positionInGroup(group, group[1])).toBe('2 / 3');
  });

  it('annonce le nombre d’activités de la zone', () => {
    expect(zoneCountLabel(0)).toBe('Aucune activité dans cette zone');
    expect(zoneCountLabel(1)).toBe('1 activité dans cette zone');
    expect(zoneCountLabel(37)).toBe('37 activités dans cette zone');
  });
});
