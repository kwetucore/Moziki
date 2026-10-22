import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import type { PaymentChannel, Role, TransactionKind, TransactionStatus } from '@/lib/types';
import { AlertIcon, RefreshIcon } from './Icons';

export function Badge({
  children,
  tone = 'neutral',
}: {
  children: ReactNode;
  tone?: 'neutral' | 'forest' | 'gold' | 'danger' | 'sky' | 'violet';
}) {
  const tones: Record<string, string> = {
    neutral: 'bg-stone-100 text-stone-700 ring-stone-200/70',
    forest: 'bg-forest-100 text-forest-800 ring-forest-200/60',
    gold: 'bg-gold-100 text-gold-700 ring-gold-200/60',
    danger: 'bg-red-50 text-red-700 ring-red-200/70',
    sky: 'bg-sky-50 text-sky-700 ring-sky-200/70',
    violet: 'bg-violet-50 text-violet-700 ring-violet-200/70',
  };
  return (
    <span
      className={`inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-[0.68rem] font-semibold ring-1 ring-inset ${tones[tone]}`}
    >
      {children}
    </span>
  );
}

export function KindBadge({ kind }: { kind: TransactionKind }) {
  const { t } = useTranslation();
  const tones: Record<TransactionKind, 'forest' | 'gold' | 'danger' | 'sky' | 'violet' | 'neutral'> = {
    DEPOSIT: 'forest',
    WITHDRAWAL: 'gold',
    SHARE_PURCHASE: 'violet',
    LOAN_DISBURSEMENT: 'sky',
    LOAN_REPAYMENT: 'forest',
    FEE: 'neutral',
    REVERSAL: 'danger',
    ADJUSTMENT: 'neutral',
  };
  return <Badge tone={tones[kind]}>{t(`kinds.${kind}`)}</Badge>;
}

export function StatusBadge({ status }: { status: TransactionStatus }) {
  const { t } = useTranslation();
  return (
    <Badge tone={status === 'POSTED' ? 'forest' : 'danger'}>
      {status === 'POSTED' ? t('transactions.posted') : t('transactions.reversed')}
    </Badge>
  );
}

export function RoleBadge({ role }: { role: Role }) {
  const { t } = useTranslation();
  const tones: Record<Role, 'forest' | 'gold' | 'sky'> = {
    LEADER: 'gold',
    AUDITOR: 'sky',
    MEMBER: 'forest',
  };
  return <Badge tone={tones[role]}>{t(`roles.${role}`)}</Badge>;
}

export function ChannelLabel({ channel }: { channel: PaymentChannel }) {
  const { t } = useTranslation();
  return <span>{t(`channels.${channel}`)}</span>;
}

export function Card({
  children,
  className = '',
  as: Tag = 'div',
}: {
  children: ReactNode;
  className?: string;
  as?: 'div' | 'section' | 'article';
}) {
  return (
    <Tag
      className={`rounded-3xl border border-stone-200/80 bg-white/85 shadow-card backdrop-blur-sm ${className}`}
    >
      {children}
    </Tag>
  );
}

export function PageHeader({
  eyebrow,
  title,
  subtitle,
  actions,
}: {
  eyebrow?: string;
  title: string;
  subtitle?: string;
  actions?: ReactNode;
}) {
  return (
    <header className="mb-7 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
      <div className="animate-fade-up">
        {eyebrow && (
          <div className="mb-1.5 text-[0.68rem] font-bold uppercase tracking-[0.2em] text-forest-600/70">
            {eyebrow}
          </div>
        )}
        <h1 className="font-display text-3xl font-semibold tracking-tight text-forest-950 sm:text-[2.1rem]">
          {title}
        </h1>
        {subtitle && <p className="mt-1.5 max-w-2xl text-sm text-forest-800/60">{subtitle}</p>}
      </div>
      {actions && <div className="flex shrink-0 flex-wrap items-center gap-2">{actions}</div>}
    </header>
  );
}

export function EmptyState({
  icon,
  title,
  body,
}: {
  icon?: ReactNode;
  title: string;
  body?: string;
}) {
  return (
    <div className="flex flex-col items-center justify-center gap-3 px-6 py-14 text-center">
      <div className="grid h-12 w-12 place-items-center rounded-2xl bg-parchment text-forest-700/60">
        {icon}
      </div>
      <p className="font-display text-lg text-forest-900">{title}</p>
      {body && <p className="max-w-sm text-sm text-forest-800/55">{body}</p>}
    </div>
  );
}

export function LoadingBlock({ rows = 3 }: { rows?: number }) {
  return (
    <div className="space-y-3 p-6" aria-busy="true">
      {Array.from({ length: rows }).map((_, index) => (
        <div
          key={index}
          className="h-12 animate-pulse rounded-2xl bg-stone-100"
          style={{ opacity: 1 - index * 0.12 }}
        />
      ))}
    </div>
  );
}

export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  const { t } = useTranslation();
  return (
    <div className="flex flex-col items-center gap-3 px-6 py-14 text-center">
      <div className="grid h-12 w-12 place-items-center rounded-2xl bg-red-50 text-red-600">
        <AlertIcon />
      </div>
      <p className="text-sm text-forest-900">{message}</p>
      {onRetry && (
        <button type="button" onClick={onRetry} className="btn-secondary">
          <RefreshIcon size={16} />
          {t('common.retry')}
        </button>
      )}
    </div>
  );
}

export function Avatar({ name, size = 'md' }: { name: string; size?: 'sm' | 'md' | 'lg' }) {
  const initials = name
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part.charAt(0).toUpperCase())
    .join('');
  const sizes = { sm: 'h-8 w-8 text-[0.68rem]', md: 'h-10 w-10 text-xs', lg: 'h-12 w-12 text-sm' };
  return (
    <span
      className={`grid shrink-0 place-items-center rounded-full bg-forest-100 font-bold tracking-wide text-forest-800 ring-1 ring-forest-200/60 ${sizes[size]}`}
      aria-hidden="true"
    >
      {initials}
    </span>
  );
}
