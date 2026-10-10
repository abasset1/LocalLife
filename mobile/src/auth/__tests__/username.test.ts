import { usernameFormatError } from '../username';

describe('usernameFormatError (LS-000)', () => {
  it.each(['abc', 'Alice', 'jean.dupont', 'jean_dupont', 'jean-dupont', '42fan', '  alice  ', 'a'.repeat(30)])(
    'accepte « %s »',
    (username) => {
      expect(usernameFormatError(username)).toBeNull();
    },
  );

  it.each(['ab', 'a'.repeat(31)])('refuse une longueur hors 3–30 : « %s »', (username) => {
    expect(usernameFormatError(username)).toContain('entre 3 et 30 caractères');
  });

  it.each(['jean dupont', 'élodie', '-alice', '.alice', '_alice', 'alice@home'])(
    'refuse les caractères interdits : « %s »',
    (username) => {
      expect(usernameFormatError(username)).toContain('Lettres sans accent');
    },
  );
});
