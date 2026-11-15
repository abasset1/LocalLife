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
  /**
   * Date de la connexion, en secondes depuis l'epoch : claim `auth_time` d'une session prolongée (conservé d'un
   * renouvellement à l'autre), sinon émission du jeton (`iat`, jeton reçu à la connexion).
   */
  iat?: number;
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
    const { userId, email, role, iat: issuedAt, auth_time: authTime, exp } = payload as Record<string, unknown>;
    const iat = typeof authTime === 'number' ? authTime : issuedAt;
    if (typeof userId !== 'number' || typeof email !== 'string' || typeof role !== 'string') {
      return null;
    }
    return {
      userId,
      email,
      role,
      iat: typeof iat === 'number' ? iat : undefined,
      exp: typeof exp === 'number' ? exp : undefined,
    };
  } catch {
    return null;
  }
}

/** `true` si le token porte une date d'expiration déjà dépassée. */
export function isTokenExpired(payload: JwtPayload, nowMs: number = Date.now()): boolean {
  return payload.exp !== undefined && payload.exp * 1000 <= nowMs;
}

/** Durée d'une session de l'application renouvelée par `POST /auth/refresh` (30 jours, voir `JwtService`). */
export const DEVICE_SESSION_MS = 30 * 24 * 3600 * 1000;

/** Marge : on renouvelle au plus une fois par jour, quand il reste moins de 29 jours. */
const RENEW_MARGIN_MS = 24 * 3600 * 1000;

/**
 * `true` si la session doit être prolongée : jeton encore valable, mais émis par la connexion (24 h) ou renouvelé
 * il y a plus d'un jour. Une session administrateur n'est jamais prolongée (limitée à 8 h côté serveur).
 */
export function shouldRenewSession(payload: JwtPayload, nowMs: number = Date.now()): boolean {
  if (payload.role === 'ADMIN' || payload.exp === undefined || isTokenExpired(payload, nowMs)) return false;
  return payload.exp * 1000 - nowMs < DEVICE_SESSION_MS - RENEW_MARGIN_MS;
}
