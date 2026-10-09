import { decodeJwtPayload, isTokenExpired } from '../jwt';

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
