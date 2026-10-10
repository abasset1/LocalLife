/**
 * Vrai si l'utilisateur peut modifier ou supprimer l'activité depuis sa fiche : son créateur ou un
 * administrateur, jamais pour une activité importée (`createdBy` nul). Le backend vérifie de nouveau.
 */
export function canManageActivity(
  user: { userId: number; role: string } | null,
  createdBy: number | null | undefined,
): boolean {
  if (!user || createdBy === null || createdBy === undefined) return false;
  return user.role === 'ADMIN' || user.userId === createdBy;
}
