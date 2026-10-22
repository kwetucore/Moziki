import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import { api, clearToken, getToken, isNetworkError, setToken } from './api';
import type { AuthResponse, Cooperative, Role, UserProfile } from './types';

interface Session {
  user: UserProfile;
  cooperative: Cooperative;
}

interface AuthContextValue {
  user: UserProfile | null;
  cooperative: Cooperative | null;
  loading: boolean;
  login: (phone: string, password: string) => Promise<AuthResponse>;
  logout: () => void;
  hasRole: (...roles: Role[]) => boolean;
}

const SESSION_KEY = 'moziki.session';
const CACHE_NAME = 'moziki-api';

const AuthContext = createContext<AuthContextValue | null>(null);

function readSession(): Session | null {
  try {
    const raw = localStorage.getItem(SESSION_KEY);
    if (!raw) {
      return null;
    }
    const parsed = JSON.parse(raw) as Session;
    return parsed?.user && parsed?.cooperative ? parsed : null;
  } catch {
    return null;
  }
}

function writeSession(session: Session | null): void {
  if (session) {
    localStorage.setItem(SESSION_KEY, JSON.stringify(session));
  } else {
    localStorage.removeItem(SESSION_KEY);
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [cached] = useState(readSession);
  const [hasToken] = useState(() => Boolean(getToken()));
  const [user, setUser] = useState<UserProfile | null>(hasToken ? cached?.user ?? null : null);
  const [cooperative, setCooperative] = useState<Cooperative | null>(
    hasToken ? cached?.cooperative ?? null : null,
  );
  const [loading, setLoading] = useState(hasToken && !cached);

  useEffect(() => {
    let cancelled = false;
    if (!getToken()) {
      setLoading(false);
      return;
    }

    api
      .me()
      .then((response) => {
        if (!cancelled) {
          setUser(response.user);
          setCooperative(response.cooperative);
          writeSession({ user: response.user, cooperative: response.cooperative });
        }
      })
      .catch((error: unknown) => {
        if (cancelled) {
          return;
        }
        // Starting the PWA without a network must not wipe the session: the
        // queued postings still belong to this user. Only a rejected token
        // (or another server answer) signs them out.
        if (isNetworkError(error)) {
          return;
        }
        clearToken();
        writeSession(null);
        setUser(null);
        setCooperative(null);
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    const handler = () => {
      clearToken();
      writeSession(null);
      setUser(null);
      setCooperative(null);
    };
    window.addEventListener('moziki:unauthorized', handler);
    return () => window.removeEventListener('moziki:unauthorized', handler);
  }, []);

  const login = useCallback(async (phone: string, password: string) => {
    const response = await api.login(phone, password);
    setToken(response.token);
    setUser(response.user);
    setCooperative(response.cooperative);
    writeSession({ user: response.user, cooperative: response.cooperative });
    return response;
  }, []);

  const logout = useCallback(() => {
    clearToken();
    writeSession(null);
    setUser(null);
    setCooperative(null);
    if ('caches' in window) {
      void caches.delete(CACHE_NAME);
    }
  }, []);

  const hasRole = useCallback(
    (...roles: Role[]) => {
      return Boolean(user && roles.includes(user.role));
    },
    [user],
  );

  const value = useMemo<AuthContextValue>(
    () => ({ user, cooperative, loading, login, logout, hasRole }),
    [user, cooperative, loading, login, logout, hasRole],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used inside AuthProvider');
  }
  return context;
}
