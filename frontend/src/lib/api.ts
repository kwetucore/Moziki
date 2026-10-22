import i18n from '@/i18n';
import type {
  Account,
  AuditEntry,
  AuthResponse,
  Cooperative,
  CreateMemberPayload,
  Dashboard,
  MeResponse,
  Member,
  PageResponse,
  PostTransactionPayload,
  ReconciliationRow,
  Role,
  Transaction,
  UserProfile,
} from './types';

const API_BASE = (import.meta.env.VITE_API_URL || '/api').replace(/\/$/, '');
const TOKEN_KEY = 'moziki.token';
const LANG_KEY = 'moziki.lang';

export class ApiError extends Error {
  status: number;
  code?: string;
  fieldErrors?: Record<string, string>;

  constructor(message: string, status: number, code?: string, fieldErrors?: Record<string, string>) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
  }
}

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token);
}

export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY);
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  const token = getToken();
  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }
  if (init.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }
  headers.set('Accept', 'application/json');
  headers.set('Accept-Language', localStorage.getItem(LANG_KEY) || 'fr');

  let response: Response;
  try {
    response = await fetch(`${API_BASE}${path}`, { ...init, headers });
  } catch {
    throw new ApiError(i18n.t('common.networkError'), 0, 'NETWORK_ERROR');
  }

  if (!response.ok) {
    let payload: {
      message?: string;
      code?: string;
      fieldErrors?: Record<string, string>;
    } = {};
    try {
      payload = await response.json();
    } catch {
      // Non-JSON error body (proxy, gateway); fall back to a status message.
    }

    if (response.status === 401 && path !== '/auth/login') {
      window.dispatchEvent(new Event('moziki:unauthorized'));
    }

    throw new ApiError(
      payload.message || `Request failed (${response.status})`,
      response.status,
      payload.code,
      payload.fieldErrors,
    );
  }

  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

function queryString(params: Record<string, string | number | undefined>): string {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== '') {
      search.set(key, String(value));
    }
  });
  const result = search.toString();
  return result ? `?${result}` : '';
}

export const api = {
  login(phone: string, password: string): Promise<AuthResponse> {
    return request<AuthResponse>('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ phone, password }),
    });
  },

  me(): Promise<MeResponse> {
    return request<MeResponse>('/auth/me');
  },

  dashboard(): Promise<Dashboard> {
    return request<Dashboard>('/dashboard');
  },

  accounts(): Promise<Account[]> {
    return request<Account[]>('/accounts');
  },

  reconciliation(): Promise<ReconciliationRow[]> {
    return request<ReconciliationRow[]>('/accounts/reconciliation');
  },

  transactions(page = 0, size = 20, memberId?: string): Promise<PageResponse<Transaction>> {
    return request<PageResponse<Transaction>>(
      `/transactions${queryString({ page, size, memberId })}`,
    );
  },

  transaction(id: string): Promise<Transaction> {
    return request<Transaction>(`/transactions/${encodeURIComponent(id)}`);
  },

  createTransaction(payload: PostTransactionPayload): Promise<Transaction> {
    return request<Transaction>('/transactions', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  },

  reverseTransaction(id: string, reason: string): Promise<Transaction> {
    return request<Transaction>(`/transactions/${encodeURIComponent(id)}/reverse`, {
      method: 'POST',
      body: JSON.stringify({ reason }),
    });
  },

  members(): Promise<Member[]> {
    return request<Member[]>('/members');
  },

  createMember(payload: CreateMemberPayload): Promise<Member> {
    return request<Member>('/members', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  },

  audit(page = 0, size = 30): Promise<PageResponse<AuditEntry>> {
    return request<PageResponse<AuditEntry>>(`/audit${queryString({ page, size })}`);
  },
};

/**
 * Errors that mean "the request never reached the ledger": the browser could
 * not connect, or a gateway in front of the API could not reach it. Those
 * postings are safe to queue and replay, because every payload carries an
 * idempotency key. A 500 from the application itself is not treated this way.
 */
const TRANSIENT_STATUSES = new Set([0, 502, 503, 504]);

export function isNetworkError(error: unknown): boolean {
  return (
    error instanceof ApiError &&
    (error.code === 'NETWORK_ERROR' || TRANSIENT_STATUSES.has(error.status))
  );
}

export type { Cooperative, UserProfile, Role };
