import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react';
import { AppState } from 'react-native';

import {
  ResponseFormatError,
  configureApiClient,
  login as apiLogin,
  loginWithGoogle as apiLoginWithGoogle,
  register as apiRegister,
} from '@/api';
import { disablePushRegistration, refreshPushRegistration } from '@/notifications';

import { signOutFromGoogle } from './googleSignIn';
import { decodeJwtPayload, isTokenExpired } from './jwt';
import { clearToken, loadToken, saveToken } from './tokenStorage';

export interface SessionUser {
  userId: number;
  email: string;
  role: string;
}

export type AuthState =
  | { status: 'loading' }
  | { status: 'anonymous' }
  | { status: 'authenticated'; user: SessionUser };

export interface RegisterInput {
  username: string;
  email: string;
  password: string;
}

interface AuthContextValue {
  state: AuthState;
  /** Connecte l'utilisateur. Lève une `ApiError` (401 identifiants, 403 e-mail non vérifié…). */
  login: (email: string, password: string) => Promise<void>;
  /** Connecte avec l'ID token Google (compte créé au besoin). Lève une `ApiError` (401 token refusé…). */
  loginWithGoogle: (idToken: string) => Promise<void>;
  /**
   * Crée le compte. Ne connecte PAS l'utilisateur : le backend exige la
   * confirmation de l'adresse e-mail avant la première connexion.
   */
  register: (input: RegisterInput) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

/**
 * Session d'authentification (LL-MOB-0004) : restaure le JWT stocké au
 * démarrage, l'expose à l'application et l'injecte dans le client API
 * (`configureApiClient`). Le token est invalidé dès qu'il expire ou que le
 * backend répond 401 à un appel authentifié.
 *
 * ⚠️ Aucun log ici : ni token, ni mot de passe, ni e-mail ne doivent être
 * écrits dans la console.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({ status: 'loading' });
  const tokenRef = useRef<string | null>(null);

  const endSession = useCallback(async () => {
    tokenRef.current = null;
    setState({ status: 'anonymous' });
    await clearToken();
  }, []);

  const startSession = useCallback((token: string): boolean => {
    const payload = decodeJwtPayload(token);
    if (!payload || isTokenExpired(payload)) return false;
    tokenRef.current = token;
    setState({
      status: 'authenticated',
      user: { userId: payload.userId, email: payload.email, role: payload.role },
    });
    return true;
  }, []);

  // Restauration de la session au démarrage.
  useEffect(() => {
    configureApiClient({
      getToken: () => {
        const token = tokenRef.current;
        if (!token) return null;
        const payload = decodeJwtPayload(token);
        if (!payload || isTokenExpired(payload)) {
          void endSession();
          return null;
        }
        return token;
      },
      onUnauthorized: () => {
        void endSession();
      },
    });

    let cancelled = false;
    (async () => {
      const stored = await loadToken();
      if (cancelled) return;
      if (stored && startSession(stored)) return;
      // Absent, illisible ou expiré : on nettoie et on repart déconnecté.
      if (stored) await clearToken();
      if (!cancelled) setState({ status: 'anonymous' });
    })();

    return () => {
      cancelled = true;
    };
  }, [endSession, startSession]);

  // Un token peut expirer pendant que l'app est en arrière-plan.
  useEffect(() => {
    const subscription = AppState.addEventListener('change', (next) => {
      if (next !== 'active' || !tokenRef.current) return;
      const payload = decodeJwtPayload(tokenRef.current);
      if (!payload || isTokenExpired(payload)) void endSession();
    });
    return () => subscription.remove();
  }, [endSession]);

  /** Ouvre la session avec le JWT LocalLife reçu (connexion classique ou Google). */
  const openSession = useCallback(
    async (token: string) => {
      const payload = decodeJwtPayload(token);
      if (!payload || isTokenExpired(payload)) throw new ResponseFormatError();
      // Stockage d'abord : on ne passe « connecté » que si la session peut survivre au redémarrage.
      await saveToken(token);
      startSession(token);
    },
    [startSession],
  );

  const login = useCallback(
    async (email: string, password: string) => {
      const { token } = await apiLogin({ email: email.trim(), password });
      await openSession(token);
    },
    [openSession],
  );

  const loginWithGoogle = useCallback(
    async (idToken: string) => {
      const { token } = await apiLoginWithGoogle(idToken);
      await openSession(token);
    },
    [openSession],
  );

  // Session ouverte (connexion ou restauration) : l'appareil est rattaché au
  // compte pour les notifications, si l'utilisateur les a déjà autorisées.
  const isAuthenticated = state.status === 'authenticated';
  useEffect(() => {
    if (isAuthenticated) void refreshPushRegistration();
  }, [isAuthenticated]);

  // Déconnexion volontaire : l'appareil ne doit plus recevoir les alertes du
  // compte. Retiré avant d'effacer le JWT, nécessaire à l'appel.
  const logout = useCallback(async () => {
    await disablePushRegistration();
    // Sans effet hors connexion Google ; sinon, le prochain « Continuer avec Google » propose de choisir le compte.
    await signOutFromGoogle();
    await endSession();
  }, [endSession]);

  const register = useCallback(async ({ username, email, password }: RegisterInput) => {
    await apiRegister({ username: username.trim(), email: email.trim(), password });
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({ state, login, loginWithGoogle, register, logout }),
    [state, login, loginWithGoogle, register, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth doit être utilisé dans un <AuthProvider>.');
  return context;
}
