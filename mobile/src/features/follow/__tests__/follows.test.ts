import type { FollowedItem } from '@/api';

import { followKey, followStatusLabel, withFollow } from '../follows';

const item = (type: FollowedItem['type'], id: number, status: string | null = 'PUBLISHED'): FollowedItem => ({
  type,
  id,
  title: `${type} ${id}`,
  city: 'Lyon',
  status,
  followedAt: '2026-11-01T10:00:00Z',
});

describe('suivis (cloche « Suivre »)', () => {
  it('distingue une activité et un lieu de même identifiant', () => {
    expect(followKey('activity', 5)).not.toBe(followKey('place', 5));
  });

  it('ajoute en tête sans doublon, et retire', () => {
    const items = [item('activity', 1), item('place', 1)];
    const added = withFollow(items, { type: 'activity', id: 2, title: 'Concert', city: null }, true);
    expect(added.map((i) => followKey(i.type, i.id))).toEqual(['activity-2', 'activity-1', 'place-1']);
    expect(withFollow(added, { type: 'activity', id: 2, title: 'Concert', city: null }, true)).toHaveLength(3);
    expect(withFollow(added, { type: 'place', id: 1, title: null, city: null }, false).map((i) => i.type)).toEqual([
      'activity',
      'activity',
    ]);
  });

  it('signale une activité annulée ou un lieu retiré de la carte', () => {
    expect(followStatusLabel(item('activity', 1, 'CANCELLED'))).toBe('annulée');
    expect(followStatusLabel(item('activity', 1))).toBeNull();
    expect(followStatusLabel(item('place', 1, 'ARCHIVED'))).toBe('retiré de la carte');
    expect(followStatusLabel(item('place', 1))).toBeNull();
  });
});
