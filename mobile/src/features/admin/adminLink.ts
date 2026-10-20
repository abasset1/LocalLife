/**
 * Administration depuis l'application mobile (sprint « Rôles et comptes », MOB-01).
 *
 * Décision : pas d'administration native. Un administrateur ouvre la console du site (`/admin`), servie sous le même
 * domaine que l'API (Caddy, `BETA_DEPLOYMENT.md`). Aucune logique de permission n'est dupliquée ici : le lien n'est
 * proposé que si le serveur (`GET /users/me`) dit que le compte est administrateur, et le site comme l'API refusent
 * de toute façon un compte qui ne l'est pas.
 */
export function adminWebUrl(apiUrl: string): string | null {
  try {
    const url = new URL(apiUrl);
    if (url.protocol !== 'https:' && url.protocol !== 'http:') return null;
    return `${url.origin}/admin`;
  } catch {
    return null;
  }
}

/** Le lien n'apparaît que pour un rôle `ADMIN` renvoyé par le serveur. */
export function shouldOfferAdminLink(serverRole: string | null | undefined): boolean {
  return serverRole === 'ADMIN';
}
