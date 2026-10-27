import { useState, type ReactElement } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAuth } from '@/lib/auth';
import { api } from '@/lib/api';
import { useAsync } from '@/lib/useAsync';
import { formatDateTime, formatMoney, formatNumber, todayLabel } from '@/lib/format';
import type { TransactionKind } from '@/lib/types';
import { PageHeader, KindBadge, StatusBadge, EmptyState, ErrorState } from '@/components/ui';
import {
  ArrowRightIcon,
  CheckIcon,
  LedgerIcon,
  PlusIcon,
  ShieldIcon,
  UsersIcon,
  WalletIcon,
  CoinsIcon,
  AlertIcon,
  LockIcon,
} from '@/components/Icons';
import { TransactionModal } from '@/components/TransactionModal';
import { TransactionDetailModal } from '@/components/TransactionDetailModal';

type IconComponent = (props: { size?: number; className?: string }) => ReactElement;

function Metric({
  label,
  value,
  hint,
  icon: Icon,
}: {
  label: string;
  value: string;
  hint?: string;
  icon: IconComponent;
}) {
  return (
    <div className="card p-4">
      <div className="flex items-center justify-between">
        <span className="text-[0.68rem] font-semibold uppercase tracking-[0.1em] text-forest-800/45">
          {label}
        </span>
        <span className="grid h-8 w-8 place-items-center rounded-xl bg-forest-50 text-forest-600">
          <Icon size={16} />
        </span>
      </div>
      <div className="mt-2.5 font-display text-xl font-semibold tracking-tight text-forest-950">
        {value}
      </div>
      {hint && <div className="mt-1 text-[0.68rem] text-forest-800/40">{hint}</div>}
    </div>
  );
}

const KIND_ICON: Record<string, IconComponent> = {
  DEPOSIT: CoinsIcon,
  WITHDRAWAL: WalletIcon,
  SHARE_PURCHASE: CoinsIcon,
  LOAN_DISBURSEMENT: ArrowRightIcon,
  LOAN_REPAYMENT: CheckIcon,
  FEE: LedgerIcon,
  REVERSAL: AlertIcon,
  ADJUSTMENT: LedgerIcon,
};

export function DashboardPage() {
  const { t, i18n } = useTranslation();
  const { user, hasRole } = useAuth();
  const { data, loading, error, reload } = useAsync(() => api.dashboard(), []);
  const [formOpen, setFormOpen] = useState(false);
  const [detailId, setDetailId] = useState<string | null>(null);
  const [flash, setFlash] = useState<string | null>(null);

  if (loading) {
    return (
      <div>
        <div className="skeleton mb-6 h-16 w-72" />
        <div className="grid gap-4 lg:grid-cols-[1.55fr_1fr]">
          <div className="skeleton h-56 rounded-3xl" />
          <div className="skeleton h-56 rounded-3xl" />
        </div>
        <div className="mt-4 grid grid-cols-2 gap-3 lg:grid-cols-4">
          {Array.from({ length: 4 }).map((_, index) => (
            <div key={index} className="skeleton h-28 rounded-3xl" />
          ))}
        </div>
      </div>
    );
  }

  if (error || !data) {
    return <ErrorState message={error ?? t('common.error')} onRetry={reload} />;
  }

  const isMember = user?.role === 'MEMBER';
  const pool = data.pool;
  const personal = data.personal;
  const firstName = user?.fullName.split(' ')[0] ?? '';

  const metrics = isMember
    ? [
        { label: t('dashboard.myShares'), value: formatMoney(personal.sharesMinor, i18n.language), icon: CoinsIcon },
        { label: t('dashboard.cash'), value: formatMoney(pool.cashMinor, i18n.language), icon: WalletIcon },
        { label: t('dashboard.mobileMoney'), value: formatMoney(pool.mobileMoneyMinor, i18n.language), icon: WalletIcon },
        { label: t('dashboard.savings'), value: formatMoney(pool.savingsMinor, i18n.language), icon: UsersIcon },
      ]
    : [
        { label: t('dashboard.cash'), value: formatMoney(pool.cashMinor, i18n.language), icon: WalletIcon },
        { label: t('dashboard.mobileMoney'), value: formatMoney(pool.mobileMoneyMinor, i18n.language), icon: WalletIcon },
        { label: t('dashboard.loans'), value: formatMoney(pool.loansOutstandingMinor, i18n.language), icon: ArrowRightIcon },
        { label: t('dashboard.shares'), value: formatMoney(pool.sharesMinor, i18n.language), icon: CoinsIcon },
      ];

  return (
    <div>
      <PageHeader
        eyebrow={t('nav.dashboard')}
        title={t('dashboard.greeting', { name: firstName })}
        subtitle={t('dashboard.subtitle')}
        actions={
          hasRole('LEADER') ? (
            <button type="button" className="btn-primary" onClick={() => setFormOpen(true)}>
              <PlusIcon size={17} />
              {t('dashboard.newTransaction')}
            </button>
          ) : undefined
        }
      />

      {flash && (
        <div className="mb-5 flex items-start gap-2 rounded-2xl border border-forest-200 bg-forest-50 px-4 py-3 text-xs font-medium text-forest-800">
          <CheckIcon size={16} className="mt-0.5 shrink-0 text-forest-600" />
          <span className="flex-1">{flash}</span>
          <button type="button" onClick={() => setFlash(null)} className="text-forest-600/50 hover:text-forest-800">
            ×
          </button>
        </div>
      )}

      <div className="grid gap-4 lg:grid-cols-[1.55fr_1fr]">
        <section className="relative overflow-hidden rounded-3xl bg-forest-950 p-6 text-cream shadow-lift sm:p-8">
          <div
            className="pointer-events-none absolute -right-20 -top-28 h-72 w-72 rounded-full border border-white/[0.08]"
            aria-hidden="true"
          />
          <div
            className="pointer-events-none absolute -right-6 -top-16 h-48 w-48 rounded-full border border-gold-400/15"
            aria-hidden="true"
          />
          <div className="relative flex flex-wrap items-center justify-between gap-2">
            <div className="text-[0.64rem] font-bold uppercase tracking-[0.2em] text-forest-200/50">
              {isMember ? t('dashboard.myPosition') : t('dashboard.pool')}
            </div>
            <span className="inline-flex items-center gap-1.5 rounded-full border border-white/15 bg-white/[0.06] px-2.5 py-1 text-[0.62rem] font-medium text-forest-100/80">
              <LockIcon size={11} className="text-gold-300" />
              {t('dashboard.trusted')}
            </span>
          </div>
          <div className="relative mt-6 text-xs text-forest-200/55">
            {isMember ? t('dashboard.mySavings') : t('dashboard.savings')}
          </div>
          <div className="relative mt-1 font-display text-4xl tracking-tight sm:text-5xl">
            {formatMoney(isMember ? personal.savingsMinor : pool.savingsMinor, i18n.language)}
          </div>
          <div className="relative mt-1 text-[0.68rem] text-forest-200/40">{data.cooperative.name}</div>

          <div className="relative mt-7 grid grid-cols-2 gap-x-4 gap-y-3 border-t border-white/10 pt-4 text-xs sm:grid-cols-4">
            <div>
              <div className="text-[0.6rem] uppercase tracking-wider text-forest-200/40">
                {t('dashboard.myShares')}
              </div>
              <div className="mt-1 font-medium tabular-nums text-cream">
                {formatMoney(personal.sharesMinor, i18n.language)}
              </div>
            </div>
            <div>
              <div className="text-[0.6rem] uppercase tracking-wider text-forest-200/40">
                {t('dashboard.fees')}
              </div>
              <div className="mt-1 font-medium tabular-nums text-cream">
                {formatMoney(pool.feeIncomeMinor, i18n.language)}
              </div>
            </div>
            <div>
              <div className="text-[0.6rem] uppercase tracking-wider text-forest-200/40">
                {t('dashboard.members')}
              </div>
              <div className="mt-1 font-medium text-cream">{formatNumber(data.memberCount, i18n.language)}</div>
            </div>
            <div>
              <div className="text-[0.6rem] uppercase tracking-wider text-forest-200/40">
                {t('dashboard.lastActivity')}
              </div>
              <div className="mt-1 truncate font-medium text-cream">
                {data.lastTransactionAt ? formatDateTime(data.lastTransactionAt, i18n.language) : '—'}
              </div>
            </div>
          </div>
          <div className="relative mt-6 flex items-center gap-1.5 text-[0.65rem] text-forest-200/40">
            <span className="h-1.5 w-1.5 rounded-full bg-gold-400" />
            {todayLabel(i18n.language)}
          </div>
        </section>

        <section className="flex flex-col justify-between rounded-3xl border border-gold-200/70 bg-gradient-to-br from-gold-50 to-cream p-6 shadow-card">
          <div>
            <span className="grid h-11 w-11 place-items-center rounded-2xl bg-gold-100 text-gold-700 ring-1 ring-gold-200/70">
              <ShieldIcon size={21} />
            </span>
            <h2 className="mt-4 font-display text-xl font-semibold tracking-tight text-forest-950">
              {t('dashboard.integrityTitle')}
            </h2>
            <p className="mt-2 text-xs leading-relaxed text-forest-800/55">
              {t('dashboard.integrityBody')}
            </p>
            <div className="mt-4 space-y-1.5">
              {[t('auth.featureLedger'), t('audit.immutable'), t('audit.accountabilityTitle')].map((item) => (
                <div key={item} className="flex items-center gap-2 text-[0.68rem] text-forest-800/60">
                  <span className="grid h-4 w-4 place-items-center rounded-full bg-forest-100 text-forest-600">
                    <CheckIcon size={10} />
                  </span>
                  {item}
                </div>
              ))}
            </div>
          </div>
          {hasRole('LEADER', 'AUDITOR') ? (
            <Link
              to="/audit"
              className="mt-5 inline-flex items-center gap-1.5 text-xs font-semibold text-forest-800 hover:text-forest-950"
            >
              {t('dashboard.integrityLink')} <ArrowRightIcon size={14} />
            </Link>
          ) : (
            <Link
              to="/accountability"
              className="mt-5 inline-flex items-center gap-1.5 text-xs font-semibold text-forest-800 hover:text-forest-950"
            >
              {t('nav.accountability')} <ArrowRightIcon size={14} />
            </Link>
          )}
        </section>
      </div>

      {isMember && (
        <p className="mt-3 text-[0.68rem] text-forest-800/40">{t('dashboard.memberViewHint')}</p>
      )}

      <div className="mt-4 grid grid-cols-2 gap-3 lg:grid-cols-4">
        {metrics.map((metric) => (
          <Metric key={metric.label} label={metric.label} value={metric.value} icon={metric.icon} />
        ))}
      </div>

      <div className="mt-6 grid gap-6 lg:grid-cols-[1.55fr_1fr]">
        <section className="card overflow-hidden">
          <div className="flex items-center justify-between border-b border-stone-200/70 px-5 py-4">
            <h2 className="font-display text-lg font-semibold text-forest-950">
              {t('dashboard.recent')}
            </h2>
            <Link to="/transactions" className="inline-flex items-center gap-1 text-xs font-semibold text-forest-700">
              {t('dashboard.viewAll')} <ArrowRightIcon size={13} />
            </Link>
          </div>
          {data.recentTransactions.content.length === 0 ? (
            <EmptyState icon={<LedgerIcon size={22} />} title={t('dashboard.emptyRecent')} />
          ) : (
            <ul className="divide-y divide-stone-100">
              {data.recentTransactions.content.map((transaction) => {
                const Icon = KIND_ICON[transaction.kind] ?? LedgerIcon;
                return (
                  <li key={transaction.id}>
                    <button
                      type="button"
                      onClick={() => setDetailId(transaction.id)}
                      className="flex w-full items-center gap-3 px-4 py-3.5 text-left transition hover:bg-parchment/40 sm:px-5"
                    >
                      <span className="grid h-9 w-9 shrink-0 place-items-center rounded-xl bg-forest-50 text-forest-600">
                        <Icon size={16} />
                      </span>
                      <span className="min-w-0 flex-1">
                        <span className="block truncate text-sm font-medium text-forest-950">
                          {transaction.description}
                        </span>
                        <span className="mt-0.5 flex flex-wrap items-center gap-x-1.5 text-[0.65rem] text-forest-800/40">
                          <span className="font-mono">{transaction.reference}</span>
                          {transaction.memberName && <span>· {transaction.memberName}</span>}
                          <span>· {formatDateTime(transaction.createdAt, i18n.language)}</span>
                        </span>
                      </span>
                      <span className="shrink-0 text-right">
                        <span className="block text-sm font-semibold tabular-nums text-forest-950">
                          {formatMoney(transaction.amountMinor, i18n.language)}
                        </span>
                        <span className="mt-0.5 block">
                          {transaction.status === 'REVERSED' ? (
                            <StatusBadge status={transaction.status} />
                          ) : (
                            <KindBadge kind={transaction.kind as TransactionKind} />
                          )}
                        </span>
                      </span>
                    </button>
                  </li>
                );
              })}
            </ul>
          )}
        </section>

        <aside className="space-y-4">
          <div className="card p-5">
            <h2 className="font-display text-lg font-semibold text-forest-950">
              {t('dashboard.quickActions')}
            </h2>
            <div className="mt-3 space-y-2">
              {hasRole('LEADER') && (
                <button
                  type="button"
                  onClick={() => setFormOpen(true)}
                  className="flex w-full items-center gap-3 rounded-2xl bg-forest-900 px-3.5 py-3 text-left text-xs font-medium text-cream transition hover:bg-forest-800"
                >
                  <PlusIcon size={17} className="text-gold-300" />
                  {t('transactions.new')}
                </button>
              )}
              <Link
                to="/transactions"
                className="flex w-full items-center gap-3 rounded-2xl border border-stone-200 bg-white/60 px-3.5 py-3 text-xs font-medium text-forest-900 transition hover:border-forest-300 hover:bg-white"
              >
                <LedgerIcon size={17} className="text-forest-600" />
                {t('nav.transactions')}
              </Link>
              <Link
                to="/accounts"
                className="flex w-full items-center gap-3 rounded-2xl border border-stone-200 bg-white/60 px-3.5 py-3 text-xs font-medium text-forest-900 transition hover:border-forest-300 hover:bg-white"
              >
                <WalletIcon size={17} className="text-forest-600" />
                {t('nav.accounts')}
              </Link>
              {hasRole('LEADER', 'AUDITOR') && (
                <Link
                  to="/members"
                  className="flex w-full items-center gap-3 rounded-2xl border border-stone-200 bg-white/60 px-3.5 py-3 text-xs font-medium text-forest-900 transition hover:border-forest-300 hover:bg-white"
                >
                  <UsersIcon size={17} className="text-forest-600" />
                  {t('nav.members')}
                </Link>
              )}
            </div>
          </div>

          <div className="rounded-3xl border border-forest-200/60 bg-forest-50/70 p-5">
            <div className="flex items-center justify-between">
              <span className="text-[0.65rem] font-bold uppercase tracking-[0.16em] text-forest-700/50">
                {t('nav.members')}
              </span>
              <UsersIcon size={16} className="text-forest-600" />
            </div>
            <div className="mt-2 flex items-end gap-2">
              <span className="font-display text-3xl font-semibold text-forest-950">
                {formatNumber(data.memberCount, i18n.language)}
              </span>
              <span className="mb-1 text-[0.68rem] text-forest-800/45">
                {t('dashboard.transactions')}: {formatNumber(data.transactionCount, i18n.language)}
              </span>
            </div>
            <p className="mt-2 text-[0.68rem] leading-relaxed text-forest-800/45">
              {data.cooperative.name} · {data.cooperative.currency}
            </p>
            {hasRole('LEADER', 'AUDITOR') && (
              <Link
                to="/members"
                className="mt-3 inline-flex items-center gap-1 text-[0.68rem] font-semibold text-forest-700"
              >
                {t('nav.members')} <ArrowRightIcon size={12} />
              </Link>
            )}
          </div>
        </aside>
      </div>

      <TransactionModal
        open={formOpen}
        onClose={() => setFormOpen(false)}
        onPosted={(message, queued) => {
          setFlash(message);
          // Queued offline postings are not in the journal yet, so refetching
          // now would fail and hide the cached dashboard behind an error.
          if (!queued) {
            reload();
          }
        }}
      />
      <TransactionDetailModal
        open={Boolean(detailId)}
        transactionId={detailId}
        onClose={() => setDetailId(null)}
        onChanged={(message) => setFlash(message)}
      />

    </div>
  );
}
