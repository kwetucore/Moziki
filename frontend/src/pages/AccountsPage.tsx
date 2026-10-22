import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { api } from '@/lib/api';
import { errorMessage, useAsync } from '@/lib/useAsync';
import { useAuth } from '@/lib/auth';
import { formatMoney } from '@/lib/format';
import { localizedAccountName } from '@/lib/accountNames';
import type { AccountCategory, ReconciliationRow } from '@/lib/types';
import {
  Badge,
  EmptyState,
  ErrorState,
  LoadingBlock,
  PageHeader,
} from '@/components/ui';
import {
  AlertIcon,
  CheckIcon,
  CoinsIcon,
  RefreshIcon,
  ShieldIcon,
  WalletIcon,
} from '@/components/Icons';

type Filter = 'all' | 'system' | 'member';

function categoryTone(category: AccountCategory): 'forest' | 'gold' | 'sky' | 'neutral' | 'violet' {
  switch (category) {
    case 'CASH':
    case 'SAVINGS':
      return 'forest';
    case 'MOBILE_MONEY':
    case 'SHARES':
      return 'gold';
    case 'LOANS':
      return 'sky';
    case 'FEES':
    case 'INTEREST':
      return 'violet';
    default:
      return 'neutral';
  }
}

export function AccountsPage() {
  const { t, i18n } = useTranslation();
  const { hasRole } = useAuth();
  const { data, loading, error, reload } = useAsync(() => api.accounts(), []);
  const [filter, setFilter] = useState<Filter>('all');
  const [reconciliation, setReconciliation] = useState<ReconciliationRow[] | null>(null);
  const [reconciliationError, setReconciliationError] = useState<string | null>(null);
  const [verifying, setVerifying] = useState(false);

  const accounts = useMemo(() => {
    return (data ?? []).filter((account) => {
      if (filter === 'member') return Boolean(account.memberId);
      if (filter === 'system') return !account.memberId;
      return true;
    });
  }, [data, filter]);

  const inconsistent = (reconciliation ?? []).filter((row) => !row.consistent);
  const canVerify = hasRole('LEADER', 'AUDITOR');

  const verify = async () => {
    setVerifying(true);
    setReconciliationError(null);
    try {
      setReconciliation(await api.reconciliation());
    } catch (verifyError) {
      setReconciliationError(errorMessage(verifyError));
    } finally {
      setVerifying(false);
    }
  };

  return (
    <div>
      <PageHeader
        eyebrow={t('nav.accounts')}
        title={t('accounts.title')}
        subtitle={t('accounts.subtitle')}
        actions={
          canVerify ? (
            <button
              type="button"
              className="btn-secondary"
              onClick={() => void verify()}
              disabled={verifying}
            >
              <RefreshIcon size={16} className={verifying ? 'animate-spin' : ''} />
              {verifying ? t('accounts.verifying') : t('accounts.verify')}
            </button>
          ) : undefined
        }
      />

      <div className="mb-4 flex flex-wrap gap-1.5 rounded-2xl border border-stone-200/80 bg-white/60 p-1.5">
        {([
          ['all', 'accounts.all'],
          ['system', 'accounts.systemAccounts'],
          ['member', 'accounts.memberAccounts'],
        ] as [Filter, string][]).map(([value, key]) => (
          <button
            key={value}
            type="button"
            onClick={() => setFilter(value)}
            className={`rounded-xl px-3.5 py-2 text-xs font-semibold transition ${
              filter === value
                ? 'bg-forest-900 text-cream shadow-sm'
                : 'text-forest-800/60 hover:bg-parchment/70 hover:text-forest-900'
            }`}
          >
            {t(key)}
          </button>
        ))}
      </div>

      {reconciliation && (
        <section
          className={`mb-4 rounded-3xl border p-5 ${
            inconsistent.length === 0
              ? 'border-forest-200 bg-forest-50/80'
              : 'border-red-200 bg-red-50/70'
          }`}
        >
          <div className="flex items-start gap-3">
            <span
              className={`grid h-9 w-9 shrink-0 place-items-center rounded-xl ${
                inconsistent.length === 0 ? 'bg-forest-100 text-forest-700' : 'bg-red-100 text-red-700'
              }`}
            >
              {inconsistent.length === 0 ? <CheckIcon size={18} /> : <AlertIcon size={18} />}
            </span>
            <div className="flex-1">
              <h2 className="text-sm font-semibold text-forest-950">
                {inconsistent.length === 0
                  ? t('accounts.reconciled')
                  : t('accounts.discrepancy', { count: inconsistent.length })}
              </h2>
              <p className="mt-0.5 text-[0.7rem] text-forest-800/55">{t('dashboard.integrityBody')}</p>
              {inconsistent.length > 0 && (
                <div className="mt-3 space-y-1.5">
                  {inconsistent.map((row) => (
                    <div
                      key={row.accountId}
                      className="flex flex-wrap items-center justify-between gap-2 rounded-xl bg-white/70 px-3 py-2 text-xs"
                    >
                      <span className="font-medium text-forest-900">
                        {row.code} · {localizedAccountName(t, row)}
                      </span>
                      <span className="text-red-700">
                        {t('accounts.difference')}: {formatMoney(row.differenceMinor, i18n.language)}
                      </span>
                    </div>
                  ))}
                </div>
              )}
            </div>
            <button type="button" className="text-forest-800/40 hover:text-forest-900" onClick={() => setReconciliation(null)}>
              ×
            </button>
          </div>
        </section>
      )}

      {reconciliationError && (
        <div className="mb-4 rounded-2xl bg-red-50 px-4 py-3 text-xs text-red-700">{reconciliationError}</div>
      )}

      {loading ? (
        <div className="card">
          <LoadingBlock rows={4} />
        </div>
      ) : error ? (
        <div className="card">
          <ErrorState message={error} onRetry={reload} />
        </div>
      ) : accounts.length === 0 ? (
        <div className="card">
          <EmptyState icon={<WalletIcon size={22} />} title={t('accounts.empty')} />
        </div>
      ) : (
        <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
          {accounts.map((account) => (
            <article key={account.id} className="card p-5">
              <div className="flex items-start justify-between gap-2">
                <div className="flex items-center gap-2.5">
                  <span className="grid h-9 w-9 place-items-center rounded-xl bg-forest-50 text-forest-600">
                    {account.category === 'CASH' || account.category === 'MOBILE_MONEY' ? (
                      <WalletIcon size={17} />
                    ) : (
                      <CoinsIcon size={17} />
                    )}
                  </span>
                  <div>
                    <h2 className="text-sm font-semibold text-forest-950">
                      {account.memberName ?? localizedAccountName(t, account)}
                    </h2>
                    <p className="mt-0.5 text-[0.62rem] text-forest-800/40">
                      {account.code} · {localizedAccountName(t, account)}
                    </p>
                  </div>
                </div>
                <Badge tone={categoryTone(account.category)}>{t(`categories.${account.category}`)}</Badge>
              </div>

              <div className="mt-4">
                <div className="text-[0.6rem] font-semibold uppercase tracking-[0.12em] text-forest-800/40">
                  {t('accounts.balance')}
                </div>
                <div className="mt-1 font-display text-2xl font-semibold tracking-tight text-forest-950">
                  {formatMoney(account.displayBalanceMinor, i18n.language)}
                </div>
              </div>

              <div className="mt-3 flex items-center justify-between border-t border-stone-100 pt-2.5 text-[0.65rem] text-forest-800/40">
                <span className="inline-flex items-center gap-1">
                  <ShieldIcon size={12} className="text-forest-500" />
                  {t('audit.immutable')}
                </span>
                {account.walletProvider && (
                  <span className="rounded-full bg-parchment px-2 py-0.5 font-medium uppercase tracking-wider">
                    {account.walletProvider}
                  </span>
                )}
              </div>
            </article>
          ))}
        </div>
      )}

      <p className="mt-4 text-[0.68rem] leading-relaxed text-forest-800/40">
        {t('accounts.subtitle')} · {t('dashboard.integrityBody')}
      </p>
    </div>
  );
}
