import { decodeJwtPayload, isTokenExpired, shouldRenewSession } from '../jwt';

function tokenFor(payload: unknown): string {
  const encode = (value: unknown) => Buffer.from(JSON.stringify(value), 'utf8').toString('base64url');
  return `${encode({ alg: 'HS256' })}.${encode(payload)}.signature`;
}

describe('decodeJwtPayload', () => {
  it('lit les claims du backend', () => {
    const token = tokenFor({ userId: 7, email: 'zoé@exemple.fr', role: 'USER', exp: 1_800_000_000 });
    expect(decodeJwtPayload(token)).toEqual({
      userId: 7,
      email: 'zoé@exemple.fr',
      role: 'USER',
      exp: 1_800_000_000,
    });
  });

  it('lit la date de connexion (iat) quand le jeton la porte', () => {
    const token = tokenFor({ userId: 7, email: 'a@b.fr', role: 'USER', iat: 1_700_000_000, exp: 1_800_000_000 });
    expect(decodeJwtPayload(token)?.iat).toBe(1_700_000_000);
    expect(decodeJwtPayload(tokenFor({ userId: 7, email: 'a@b.fr', role: 'USER' }))?.iat).toBeUndefined();
  });

  it('garde la date de la connexion d’origine (auth_time) après un renouvellement', () => {
    const token = tokenFor({ userId: 7, email: 'a@b.fr', role: 'USER', iat: 1_750_000_000, auth_time: 1_700_000_000 });
    expect(decodeJwtPayload(token)?.iat).toBe(1_700_000_000);
  });

  it('rejette un token illisible ou incomplet', () => {
    expect(decodeJwtPayload('')).toBeNull();
    expect(decodeJwtPayload('pas-un-jwt')).toBeNull();
    expect(decodeJwtPayload('a.%%%.c')).toBeNull();
    expect(decodeJwtPayload(tokenFor({ email: 'a@b.fr', role: 'USER' }))).toBeNull();
    expect(decodeJwtPayload(tokenFor('texte'))).toBeNull();
  });
});

describe('isTokenExpired', () => {
  const base = { userId: 1, email: 'a@b.fr', role: 'USER' };

  it("compare l'expiration à l'instant courant", () => {
    expect(isTokenExpired({ ...base, exp: 1000 }, 999_999)).toBe(false);
    expect(isTokenExpired({ ...base, exp: 1000 }, 1_000_000)).toBe(true);
  });

  it("considère valide un token sans claim d'expiration", () => {
    expect(isTokenExpired(base)).toBe(false);
  });
});

describe('shouldRenewSession', () => {
  const now = 1_800_000_000_000;
  const day = 24 * 3600;
  const base = { userId: 7, email: 'a@b.fr', role: 'USER' };

  it('prolonge le jeton de 24 h reçu à la connexion', () => {
    expect(shouldRenewSession({ ...base, exp: now / 1000 + day }, now)).toBe(true);
  });

  it('prolonge une session de 30 jours renouvelée il y a plus d’un jour', () => {
    expect(shouldRenewSession({ ...base, exp: now / 1000 + 28 * day }, now)).toBe(true);
  });

  it('ne refait pas l’appel juste après un renouvellement', () => {
    expect(shouldRenewSession({ ...base, exp: now / 1000 + 30 * day - 60 }, now)).toBe(false);
  });

  it('ignore un jeton expiré, sans expiration, ou administrateur', () => {
    expect(shouldRenewSession({ ...base, exp: now / 1000 - 1 }, now)).toBe(false);
    expect(shouldRenewSession({ ...base }, now)).toBe(false);
    expect(shouldRenewSession({ ...base, role: 'ADMIN', exp: now / 1000 + 3600 }, now)).toBe(false);
  });
});
