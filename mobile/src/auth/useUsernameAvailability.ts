import { useEffect, useState } from 'react';

import { checkUsernameAvailability } from '@/api';

import { usernameFormatError } from './username';

export type UsernameStatus =
  | { kind: 'idle' }
  | { kind: 'checking' }
  | { kind: 'available' }
  | { kind: 'unavailable'; message: string };

const CHECK_DELAY_MS = 400;

/**
 * Vérifie pendant la saisie qu'un nom d'utilisateur est valide et libre (LS-000).
 * Le format se juge localement ; seule la réponse du serveur est gardée en état,
 * associée au nom vérifié pour ignorer une réponse devenue obsolète.
 */
export function useUsernameAvailability(username: string): UsernameStatus {
  const value = username.trim();
  const [checked, setChecked] = useState<{ value: string; status: UsernameStatus } | null>(null);
  const formatError = value === '' ? null : usernameFormatError(value);
  const needsServer = value !== '' && formatError === null;

  useEffect(() => {
    if (!needsServer) return;
    const controller = new AbortController();
    const timer = setTimeout(() => {
      checkUsernameAvailability(value, { signal: controller.signal })
        .then((result) =>
          setChecked({
            value,
            status: result.available
              ? { kind: 'available' }
              : { kind: 'unavailable', message: result.message ?? "Ce nom d'utilisateur est indisponible." },
          }),
        )
        .catch(() => {
          // Vérification indisponible : le serveur tranchera à l'inscription.
          if (!controller.signal.aborted) setChecked({ value, status: { kind: 'idle' } });
        });
    }, CHECK_DELAY_MS);

    return () => {
      clearTimeout(timer);
      controller.abort();
    };
  }, [value, needsServer]);

  if (value === '') return { kind: 'idle' };
  if (formatError) return { kind: 'unavailable', message: formatError };
  return checked?.value === value ? checked.status : { kind: 'checking' };
}
