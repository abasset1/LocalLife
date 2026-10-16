/** Ensemble d'identifiants aimés, recopié (jamais modifié en place) pour que React voie le changement. */
export function withLike(ids: ReadonlySet<number>, id: number, liked: boolean): Set<number> {
  const next = new Set(ids);
  if (liked) next.add(id);
  else next.delete(id);
  return next;
}
