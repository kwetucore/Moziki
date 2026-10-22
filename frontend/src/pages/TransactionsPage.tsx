import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { api } from '@/lib/api';
import { useAsync } from '@/lib/useAsync';
import { useAuth } from '@/lib/auth';
import { formatDateTime, formatMoney } from '@/lib/format';
import type { TransactionKind } from '@/lib/types';
import {
  EmptyState,
  ErrorState,
  KindBadge,
  LoadingBlock,
  PageHeader,
  StatusBadge,
} from '@/components/ui';
import {
  CheckIcon,
  ChevronLeftIcon,
  ChevronRightIcon,
  LedgerIcon,
  PlusIcon,
  SearchIcon,
} from '@/components/Icons';
import { TransactionModal } from '@/components/TransactionModal';
import { TransactionDetailModal } from '@/components/TransactionDetailModal';

const PAGE_SIZE = 20;

const FILTER_KINDS: TransactionKind[] = [
  'DEPOSIT',
  'WITHDRAWAL',
  'SHARE_PURCHASE',
  'LOAN_DISBURSEMENT',
  'LOAN_REPAYMENT',
  'FEE',
  'REVERSAL',
];

export function TransactionsPage() {
  const { t, i18n } = useTranslation();
  const { hasRole } = useAuth();
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const [kindFilter, setKindFilter] = useState<'ALL' | TransactionKind>('ALL');
  const [formOpen, setFormOpen] = useState(false);
  const [detailId, setDetailId] = useState<string | null>(null);
  const [flash, setFlash] = useState<string | null>(null);

  const { data, loading, error, reload } = useAsync(
    () => api.transactions(page, PAGE_SIZE),
    [page],
  );

  const filtered = useMemo(() => {
    const query = search.trim().toLowerCase();
    return (data?.content ?? []).filter((transaction) => {
      const matchesKind = kindFilter === 'ALL' || transaction.kind === kindFilter;
      const haystack = [
        transaction.reference,
        transaction.description,
        transaction.memberName ?? '',
      ]
        .join(' ')
        .toLowerCase();
      return matchesKind && (!query || haystack.includes(query));
    });
  }, [data, kindFilter, search]);

  const goToPage = (next: number) => {
    setPage(next);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  return (
    <div>
      <PageHeader
        eyebrow={t('nav.transactions')}
        title={t('transactions.title')}
        subtitle={t('transactions.subtitle')}
        actions={
          hasRole('LEADER') ? (
            <button type="button" className="btn-primary" onClick={() => setFormOpen(true)}>
              <PlusIcon size={17} />
              {t('transactions.new')}
            </button>
          ) : undefined
        }
      />

      {flash && (
        <div className="mb-4 flex items-start gap-2 rounded-2xl border border-forest-200 bg-forest-50 px-4 py-3 text-xs font-medium text-forest-800">
          <CheckIcon size={16} className="mt-0.5 shrink-0 text-forest-600" />
          <span className="flex-1">{flash}</span>
          <button type="button" onClick={() => setFlash(null)} className="text-forest-600/50">
            ×
          </button>
        </div>
      )}

      <div className="card mb-4 flex flex-col gap-3 p-3.5 sm:flex-row sm:items-center">
        <div className="relative flex-1">
          <SearchIcon
            size={16}
            className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-forest-800/35"
          />
          <input
            type="search"
            className="input border-transparent bg-parchment/60 pl-10 shadow-none focus:bg-white"
            placeholder={t('transactions.search')}
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            aria-label={t('transactions.search')}
          />
        </div>
        <div className="flex items-center gap-2">
          <select
            className="select w-full border-transparent bg-parchment/60 py-2 text-xs shadow-none focus:bg-white sm:w-auto"
            value={kindFilter}
            onChange={(event) => setKindFilter(event.target.value as 'ALL' | TransactionKind)}
            aria-label={t('transactions.filterKind')}
          >
            <option value="ALL">{t('transactions.allKinds')}</option>
            {FILTER_KINDS.map((kind) => (
              <option key={kind} value={kind}>
                {t(`kinds.${kind}`)}
              </option>
            ))}
          </select>
          <span className="whitespace-nowrap rounded-full bg-parchment px-3 py-1.5 text-[0.68rem] font-semibold text-forest-800/50">
            {data?.totalElements ?? 0}
          </span>
        </div>
      </div>

      <div className="card overflow-hidden">
        {loading ? (
          <LoadingBlock rows={5} />
        ) : error ? (
          <ErrorState message={error} onRetry={reload} />
        ) : (data?.content.length ?? 0) === 0 ? (
          <EmptyState
            icon={<LedgerIcon size={22} />}
            title={t('transactions.emptyAll')}
            body={hasRole('LEADER') ? t('transactions.doubleEntryNote') : undefined}
          />
        ) : filtered.length === 0 ? (
          <EmptyState icon={<SearchIcon size={22} />} title={t('transactions.empty')} />
        ) : (
          <>
            <div className="hidden grid-cols-[2fr_1fr_1.1fr_0.9fr_auto] gap-3 border-b border-stone-200/70 bg-parchment/40 px-5 py-2.5 sm:grid">
              <span className="table-head !px-0">{t('transactions.description')}</span>
              <span className="table-head !px-0">{t('transactions.type')}</span>
              <span className="table-head !px-0">{t('transactions.member')}</span>
              <span className="table-head !px-0">{t('transactions.channel')}</span>
              <span className="table-head !px-0 text-right">{t('transactions.amount')}</span>
            </div>
            <ul className="divide-y divide-stone-100">
              {filtered.map((transaction) => (
                <li key={transaction.id}>
                  <button
                    type="button"
                    onClick={() => setDetailId(transaction.id)}
                    className="grid w-full grid-cols-[1fr_auto] items-center gap-3 px-4 py-3.5 text-left transition hover:bg-parchment/40 sm:grid-cols-[2fr_1fr_1.1fr_0.9fr_auto] sm:gap-3 sm:px-5"
                  >
                    <div className="min-w-0">
                      <div className="flex items-center gap-2">
                        <span className="truncate text-sm font-medium text-forest-950">
                          {transaction.description}
                        </span>
                        {transaction.status === 'REVERSED' && (
                          <span className="sm:hidden">
                            <StatusBadge status={transaction.status} />
                          </span>
                        )}
                      </div>
                      <div className="mt-0.5 flex flex-wrap items-center gap-x-1.5 text-[0.65rem] text-forest-800/40">
                        <span className="font-mono">{transaction.reference}</span>
                        <span>· {formatDateTime(transaction.createdAt, i18n.language)}</span>
                        <span className="sm:hidden">· {transaction.memberName ?? '—'}</span>
                      </div>
                    </div>
                    <div className="hidden sm:block">
                      <KindBadge kind={transaction.kind} />
                    </div>
                    <div className="hidden min-w-0 sm:block">
                      <div className="truncate text-xs text-forest-900">
                        {transaction.memberName ?? t('common.notAvailable')}
                      </div>
                      <div className="truncate text-[0.62rem] text-forest-800/40">
                        {t(`channels.${transaction.channel}`)}
                      </div>
                    </div>
                    <div className="hidden sm:block">
                      {transaction.status === 'REVERSED' ? (
                        <StatusBadge status={transaction.status} />
                      ) : (
                        <span className="text-[0.68rem] text-forest-800/45">
                          {t(`kinds.${transaction.kind}`)}
                        </span>
                      )}
                    </div>
                    <div className="text-right">
                      <div className="text-sm font-semibold tabular-nums text-forest-950">
                        {formatMoney(transaction.amountMinor, i18n.language)}
                      </div>
                      <div className="mt-0.5 text-[0.62rem] text-forest-800/40 sm:hidden">
                        {t(`kinds.${transaction.kind}`)}
                      </div>
                    </div>
                  </button>
                </li>
              ))}
            </ul>
          </>
        )}
      </div>

      {data && data.totalPages > 1 && (
        <div className="mt-4 flex items-center justify-between text-xs text-forest-800/50">
          <span>
            {t('transactions.page', { page: data.page + 1, pages: data.totalPages })}
          </span>
          <div className="flex items-center gap-2">
            <button
              type="button"
              className="btn-secondary px-3 py-1.5 text-xs"
              onClick={() => goToPage(page - 1)}
              disabled={page === 0}
            >
              <ChevronLeftIcon size={14} /> {t('transactions.previous')}
            </button>
            <button
              type="button"
              className="btn-secondary px-3 py-1.5 text-xs"
              onClick={() => goToPage(page + 1)}
              disabled={page >= data.totalPages - 1}
            >
              {t('transactions.next')} <ChevronRightIcon size={14} />
            </button>
          </div>
        </div>
      )}

      <TransactionModal
        open={formOpen}
        onClose={() => setFormOpen(false)}
        onPosted={(message, queued) => {
          setFlash(message);
          if (!queued) {
            setPage(0);
            reload();
          }
        }}
      />
      <TransactionDetailModal
        open={Boolean(detailId)}
        transactionId={detailId}
        onClose={() => setDetailId(null)}
        onChanged={(message) => {
          setFlash(message);
          reload();
        }}
      />
    </div>
  );
}
