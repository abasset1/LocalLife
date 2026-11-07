import { keepFreeActivities } from '../priceFilter';

const free = { id: 1, priceType: 'FREE' as const };
const paid = { id: 2, priceType: 'PAID' as const };
const unknown = { id: 3, priceType: 'UNKNOWN' as const };
const legacy = { id: 4, priceType: undefined };

describe('keepFreeActivities', () => {
  it('ne garde que les activités gratuites quand le filtre est actif', () => {
    expect(keepFreeActivities([free, paid, unknown, legacy], true)).toEqual([free]);
  });

  it('rend la liste telle quelle sans filtre', () => {
    const all = [free, paid, unknown, legacy];
    expect(keepFreeActivities(all, false)).toBe(all);
  });
});
