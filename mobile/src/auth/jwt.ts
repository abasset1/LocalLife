/**
 * Lecture du payload d'un JWT, pour affichage et pour détecter
 * l'expiration côté client uniquement. Ce n'est PAS une vérification de
 * signature : la sécurité réelle reste côté backend, qui revalide chaque
 * token à chaque requête protégée (comme `frontend/src/auth/authStorage.ts`).
 */
export interface JwtPayload {
  userId: number;
  email: string;
  role: string;
  /** Expiration, en secondes depuis l'epoch (claim `exp`). */
  exp?: number;
}

export function decodeJwtPayload(token: string): JwtPayload | null {
  try {
    const segment = token.split('.')[1];
    if (!segment) return null;
    const base64 = segment.replace(/-/g, '+').replace(/_/g, '/');
    const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4);
    const binary = atob(padded);
    const json = decodeURIComponent(
      Array.from(binary, (char) => '%' + char.charCodeAt(0).toString(16).padStart(2, '0')).join(''),
    );
    const payload: unknown = JSON.parse(json);
    if (payload === null || typeof payload !== 'object') return null;
    const { userId, email, role, exp } = payload as Record<string, unknown>;
    if (typeof userId !== 'number' || typeof email !== 'string' || typeof role !== 'string') {
      return null;
    }
    return { userId, email, role, exp: typeof exp === 'number' ? exp : undefined };
  } catch {
    return null;
  }
}

/** `true` si le token porte une date d'expiration déjà dépassée. */
export function isTokenExpired(payload: JwtPayload, nowMs: number = Date.now()): boolean {
  return payload.exp !== undefined && payload.exp * 1000 <= nowMs;
}
