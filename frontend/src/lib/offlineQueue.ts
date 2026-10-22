import { ApiError, api, isNetworkError } from './api';
import type { PostTransactionPayload } from './types';

const DB_NAME = 'moziki-offline';
const DB_VERSION = 1;
const STORE = 'transactions';

export interface QueuedTransaction {
  id: string;
  payload: PostTransactionPayload;
  createdAt: string;
  summary: string;
  kindLabel: string;
  amountMinor: number;
}

function openDatabase(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    if (!('indexedDB' in window)) {
      reject(new Error('IndexedDB is unavailable in this browser'));
      return;
    }
    const request = indexedDB.open(DB_NAME, DB_VERSION);
    request.onupgradeneeded = () => {
      const db = request.result;
      if (!db.objectStoreNames.contains(STORE)) {
        const store = db.createObjectStore(STORE, { keyPath: 'id' });
        store.createIndex('createdAt', 'createdAt');
      }
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error ?? new Error('Could not open offline queue'));
  });
}

function transactionDone(tx: IDBTransaction): Promise<void> {
  return new Promise((resolve, reject) => {
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error ?? new Error('Offline queue write failed'));
    tx.onabort = () => reject(tx.error ?? new Error('Offline queue write aborted'));
  });
}

export async function enqueueTransaction(item: QueuedTransaction): Promise<void> {
  const db = await openDatabase();
  try {
    const tx = db.transaction(STORE, 'readwrite');
    tx.objectStore(STORE).put(item);
    await transactionDone(tx);
  } finally {
    db.close();
  }
  window.dispatchEvent(new Event('moziki:queue-changed'));
}

export async function getQueuedTransactions(): Promise<QueuedTransaction[]> {
  const db = await openDatabase();
  try {
    const tx = db.transaction(STORE, 'readonly');
    const store = tx.objectStore(STORE);
    const result = await new Promise<QueuedTransaction[]>((resolve, reject) => {
      const request = store.getAll();
      request.onsuccess = () => resolve(request.result as QueuedTransaction[]);
      request.onerror = () => reject(request.error);
    });
    await transactionDone(tx);
    return result.sort((a, b) => a.createdAt.localeCompare(b.createdAt));
  } finally {
    db.close();
  }
}

export async function removeQueuedTransaction(id: string): Promise<void> {
  const db = await openDatabase();
  try {
    const tx = db.transaction(STORE, 'readwrite');
    tx.objectStore(STORE).delete(id);
    await transactionDone(tx);
  } finally {
    db.close();
  }
  window.dispatchEvent(new Event('moziki:queue-changed'));
}

export async function queuedCount(): Promise<number> {
  const db = await openDatabase();
  try {
    const tx = db.transaction(STORE, 'readonly');
    return await new Promise<number>((resolve, reject) => {
      const request = tx.objectStore(STORE).count();
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error);
    });
  } finally {
    db.close();
  }
}

/**
 * Replays queued postings in order. Every queued payload already carries an
 * idempotency key, so a retry after an ambiguous network failure is safe.
 * 4xx responses are dropped (they can never succeed); network/5xx errors stop
 * the flush so the work is retried when connectivity returns.
 */
export async function flushQueue(): Promise<{ posted: number; failed: number }> {
  const items = await getQueuedTransactions();
  let posted = 0;
  let failed = 0;

  for (const item of items) {
    try {
      await api.createTransaction(item.payload);
      await removeQueuedTransaction(item.id);
      posted += 1;
    } catch (error) {
      if (error instanceof ApiError && error.status >= 400 && error.status < 500) {
        await removeQueuedTransaction(item.id);
        failed += 1;
        continue;
      }
      if (isNetworkError(error)) {
        break;
      }
      failed += 1;
      break;
    }
  }

  if (posted > 0) {
    window.dispatchEvent(new Event('moziki:synced'));
  }
  window.dispatchEvent(new Event('moziki:queue-changed'));
  return { posted, failed };
}
