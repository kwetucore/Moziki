import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { api } from '@/lib/api';
import { useAsync } from '@/lib/useAsync';
import { formatDateTime, formatMoney } from '@/lib/format';
import {
  Badge,
  EmptyState,
  ErrorState,
  LoadingBlock,
  PageHeader,
} from '@/components/ui';
import {
  CheckIcon,
  ChevronLeftIcon,
  ChevronRightIcon,
  LockIcon,
  RefreshIcon,
  ShieldIcon,
  UsersIcon,
  LedgerIcon,
  AlertIcon,
} from '@/components/Icons';

const PAGE_SIZE = 20;

const ACTION_STYLES: Record<string, { tone: 'forest' | 'gold' | 'sky' | 'danger'; icon: typeof ShieldIcon }> = {
  TRANSACTION_POSTED: { tone: 'forest', icon: LedgerIcon },
  TRANSACTION_REVERSED: { tone: 'danger', icon: AlertIcon },
  MEMBER_CREATED: { tone: 'gold', icon: UsersIcon },
  USER_LOGIN: { tone: 'sky', icon: ShieldIcon },
  CASH_RECONCILIATION_SUBMITTED: { tone: 'gold', icon: LockIcon },
  CASH_RECONCILIATION_CORRECTED: { tone: 'danger', icon: RefreshIcon },
};

export function AuditPage() {
  const { t, i18n } = useTranslation();
  const [page, setPage] = useState(0);
  const { data, loading, error, reload } = useAsync(() => api.audit(page, PAGE_SIZE), [page]);

  const knownActions = new Set(Object.keys(ACTION_STYLES));

  return (
    <div>
      <PageHeader
        eyebrow={t('nav.audit')}
        title={t('audit.title')}
        subtitle={t('audit.subtitle')}
        actions={
          <button type="button" className="btn-secondary" onClick={reload}>
            <RefreshIcon size={16} />
            {t('common.refresh')}
          </button>
        }
      />

      <section className="relative mb-5 overflow-hidden rounded-3xl bg-forest-950 px-6 py-6 text-cream shadow-lift sm:px-8">
        <div
          className="pointer-events-none absolute -right-16 -top-20 h-56 w-56 rounded-full border border-white/[0.08]"
          aria-hidden="true"
        />
        <div className="relative flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div className="max-w-xl">
            <div className="flex items-center gap-2 text-[0.62rem] font-bold uppercase tracking-[0.2em] text-gold-300/80">
              <LockIcon size={13} /> {t('audit.immutable')}
            </div>
            <h2 className="mt-2 font-display text-xl font-semibold">{t('audit.accountabilityTitle')}</h2>
            <p className="mt-1.5 text-xs leading-relaxed text-forest-200/55">
              {t('audit.accountabilityBody')}
            </p>
          </div>
          <div className="flex shrink-0 items-center gap-3 rounded-2xl border border-white/10 bg-white/[0.04] px-4 py-3">
            <ShieldIcon size={22} className="text-gold-300" />
            <div>
              <div className="text-[0.6rem] uppercase tracking-[0.16em] text-forest-200/40">
                {t('audit.details')}
              </div>
              <div className="mt-0.5 text-sm font-semibold">{data?.totalElements ?? 0}</div>
            </div>
          </div>
        </div>
      </section>

      {loading ? (
        <div className="card">
          <LoadingBlock rows={5} />
        </div>
      ) : error ? (
        <div className="card">
          <ErrorState message={error} onRetry={reload} />
        </div>
      ) : (data?.content.length ?? 0) === 0 ? (
        <div className="card">
          <EmptyState icon={<ShieldIcon size={22} />} title={t('audit.empty')} />
        </div>
      ) : (
        <div className="card overflow-hidden">
          <ul className="divide-y divide-stone-100">
            {data!.content.map((entry) => {
              const style = ACTION_STYLES[entry.action];
              const Icon = style?.icon ?? ShieldIcon;
              const tone = style?.tone ?? 'sky';
              const label = knownActions.has(entry.action)
                ? t(`audit.actions.${entry.action}`)
                : entry.action.replace(/_/g, ' ').toLowerCase();
              const amountMinor =
                typeof entry.metadata.amountMinor === 'number'
                  ? (entry.metadata.amountMinor as number)
                  : null;
              const reference =
                typeof entry.metadata.reference === 'string'
                  ? (entry.metadata.reference as string)
                  : null;
              const metadataEntries = Object.keys(entry.metadata ?? {}).length > 0;

              return (
                <li key={entry.id} className="px-4 py-4 sm:px-5">
                  <div className="flex items-start gap-3">
                    <span
                      className={`grid h-9 w-9 shrink-0 place-items-center rounded-xl ${
                        tone === 'forest'
                          ? 'bg-forest-50 text-forest-600'
                          : tone === 'danger'
                            ? 'bg-red-50 text-red-600'
                            : tone === 'gold'
                              ? 'bg-gold-50 text-gold-700'
                              : 'bg-sky-50 text-sky-600'
                      }`}
                    >
                      <Icon size={17} />
                    </span>
                    <div className="min-w-0 flex-1">
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="text-sm font-medium text-forest-950">{label}</span>
                        {amountMinor !== null && (
                          <Badge tone="forest">{formatMoney(amountMinor, i18n.language)}</Badge>
                        )}
                        {entry.actorName ? (
                          <span className="text-[0.68rem] text-forest-800/45">· {entry.actorName}</span>
                        ) : (
                          <span className="text-[0.68rem] text-forest-800/40">· {t('common.notAvailable')}</span>
                        )}
                      </div>
                      <p className="mt-1 text-xs leading-relaxed text-forest-800/60">{entry.summary}</p>
                      <div className="mt-1 flex flex-wrap items-center gap-x-2 text-[0.62rem] text-forest-800/35">
                        <span>{formatDateTime(entry.createdAt, i18n.language)}</span>
                        {reference && <span>· {reference}</span>}
                        {entry.ipAddress && <span className="hidden sm:inline">· {t('audit.ip')}: {entry.ipAddress}</span>}
                      </div>
                      {metadataEntries && (
                        <details className="mt-2 group">
                          <summary className="cursor-pointer list-none text-[0.65rem] font-semibold text-forest-600 hover:text-forest-900">
                            {t('audit.details')}
                          </summary>
                          <pre className="mt-2 max-h-52 overflow-auto rounded-xl bg-parchment/60 p-3 text-[0.62rem] leading-relaxed text-forest-800/70">
                            {JSON.stringify(entry.metadata, null, 2)}
                          </pre>
                        </details>
                      )}
                    </div>
                    <div className="hidden shrink-0 items-center gap-1 text-[0.6rem] text-forest-800/30 sm:flex">
                      <CheckIcon size={12} className="text-forest-500" />
                      {t('audit.immutable')}
                    </div>
                  </div>
                </li>
              );
            })}
          </ul>
        </div>
      )}

      {data && data.totalPages > 1 && (
        <div className="mt-4 flex items-center justify-between text-xs text-forest-800/50">
          <span>
            {t('transactions.page', { page: data.page + 1, pages: data.totalPages })}
          </span>
          <div className="flex gap-2">
            <button
              type="button"
              className="btn-secondary px-3 py-1.5 text-xs"
              onClick={() => setPage((current) => Math.max(0, current - 1))}
              disabled={page === 0}
            >
              <ChevronLeftIcon size={14} /> {t('transactions.previous')}
            </button>
            <button
              type="button"
              className="btn-secondary px-3 py-1.5 text-xs"
              onClick={() => setPage((current) => current + 1)}
              disabled={page >= data.totalPages - 1}
            >
              {t('transactions.next')} <ChevronRightIcon size={14} />
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
