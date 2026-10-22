import { useCallback, useEffect, useRef, useState } from 'react';
import { ApiError } from './api';
import i18n from '@/i18n';

interface AsyncState<T> {
  data: T | null;
  loading: boolean;
  error: string | null;
}

export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return error.message;
  }
  if (error instanceof Error) {
    return error.message;
  }
  return i18n.t('common.error');
}

export function useAsync<T>(factory: () => Promise<T>, deps: unknown[] = []) {
  const factoryRef = useRef(factory);
  factoryRef.current = factory;
  const [version, setVersion] = useState(0);
  const [state, setState] = useState<AsyncState<T>>({ data: null, loading: true, error: null });

  const reload = useCallback(() => setVersion((value) => value + 1), []);

  useEffect(() => {
    let cancelled = false;
    setState((current) => ({ ...current, loading: true, error: null }));

    factoryRef
      .current()
      .then((data) => {
        if (!cancelled) {
          setState({ data, loading: false, error: null });
        }
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          setState({ data: null, loading: false, error: errorMessage(error) });
        }
      });

    return () => {
      cancelled = true;
    };
    // The caller owns the dependency list; reload() forces a refetch.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, version]);

  useEffect(() => {
    const handler = () => reload();
    window.addEventListener('moziki:synced', handler);
    return () => window.removeEventListener('moziki:synced', handler);
  }, [reload]);

  return { ...state, reload };
}
