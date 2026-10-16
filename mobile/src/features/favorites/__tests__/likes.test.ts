import { withLike } from '../likes';

describe('withLike', () => {
  it('ajoute ou retire un identifiant sans modifier l’ensemble d’origine', () => {
    const ids = new Set([1, 2]);
    expect([...withLike(ids, 3, true)]).toEqual([1, 2, 3]);
    expect([...withLike(ids, 1, false)]).toEqual([2]);
    expect([...ids]).toEqual([1, 2]);
  });

  it('est idempotent', () => {
    const ids = new Set([1]);
    expect([...withLike(ids, 1, true)]).toEqual([1]);
    expect([...withLike(ids, 9, false)]).toEqual([1]);
  });
});
