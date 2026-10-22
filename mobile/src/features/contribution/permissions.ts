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

/**
 * Vrai si supprimer (ou annuler) l'activité exige un motif : un administrateur — rôle relu par le serveur, sinon
 * celui du jeton — qui agit sur la contribution d'un autre (décision du 22/10/2026 : corbeille de 30 jours).
 */
export function requiresModerationReason(
  user: { userId: number; role: string } | null,
  createdBy: number | null | undefined,
  serverRole?: string | null,
): boolean {
  if (!user || createdBy === null || createdBy === undefined) return false;
  return (serverRole ?? user.role) === 'ADMIN' && user.userId !== createdBy;
}

/** Statuts dont le motif de modération est montré au contributeur. */
export function showsModerationReason(status: string): boolean {
  return status === 'REJECTED' || status === 'CANCELLED';
}
