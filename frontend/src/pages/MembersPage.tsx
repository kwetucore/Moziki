import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { api } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { errorMessage, useAsync } from '@/lib/useAsync';
import { formatDate, formatMoney, formatNumber, initials } from '@/lib/format';
import { LANGUAGES } from '@/i18n';
import type { CreateMemberPayload, Role } from '@/lib/types';
import {
  EmptyState,
  ErrorState,
  LoadingBlock,
  PageHeader,
  RoleBadge,
} from '@/components/ui';
import { AlertIcon, CheckIcon, PlusIcon, SearchIcon, UsersIcon } from '@/components/Icons';
import { Modal } from '@/components/Modal';

function MemberAvatar({ name }: { name: string }) {
  return (
    <span className="grid h-11 w-11 shrink-0 place-items-center rounded-2xl bg-forest-100 text-xs font-bold text-forest-800 ring-1 ring-forest-200/60">
      {initials(name)}
    </span>
  );
}

export function MembersPage() {
  const { t, i18n } = useTranslation();
  const { hasRole } = useAuth();
  const { data, loading, error, reload } = useAsync(() => api.members(), []);
  const [search, setSearch] = useState('');
  const [formOpen, setFormOpen] = useState(false);
  const [flash, setFlash] = useState<string | null>(null);

  const filtered = useMemo(() => {
    const query = search.trim().toLowerCase();
    return (data ?? []).filter(
      (member) =>
        !query ||
        member.fullName.toLowerCase().includes(query) ||
        member.memberNumber.toLowerCase().includes(query) ||
        member.phone.includes(query),
    );
  }, [data, search]);

  const totalSavings = (data ?? []).reduce((sum, member) => sum + member.savingsMinor, 0);

  return (
    <div>
      <PageHeader
        eyebrow={t('nav.members')}
        title={t('members.title')}
        subtitle={t('members.subtitle')}
        actions={
          hasRole('LEADER') ? (
            <button type="button" className="btn-primary" onClick={() => setFormOpen(true)}>
              <PlusIcon size={17} />
              {t('members.add')}
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

      <div className="mb-4 grid gap-3 sm:grid-cols-[1fr_auto]">
        <div className="relative">
          <SearchIcon
            size={16}
            className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-forest-800/35"
          />
          <input
            type="search"
            className="input pl-10"
            placeholder={t('members.search')}
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            aria-label={t('members.search')}
          />
        </div>
        <div className="flex items-center gap-4 rounded-2xl border border-stone-200/80 bg-white/70 px-4 py-2.5 text-xs text-forest-800/50">
          <span>
            <strong className="font-semibold text-forest-900">{formatNumber(data?.length ?? 0, i18n.language)}</strong>{' '}
            {t('nav.members').toLowerCase()}
          </span>
          <span className="h-4 w-px bg-stone-200" />
          <span>
            {t('members.savings')}{' '}
            <strong className="font-semibold text-forest-900">{formatMoney(totalSavings, i18n.language)}</strong>
          </span>
        </div>
      </div>

      {loading ? (
        <div className="card">
          <LoadingBlock rows={4} />
        </div>
      ) : error ? (
        <div className="card">
          <ErrorState message={error} onRetry={reload} />
        </div>
      ) : filtered.length === 0 ? (
        <div className="card">
          <EmptyState
            icon={<UsersIcon size={22} />}
            title={search ? t('transactions.empty') : t('members.empty')}
            body={hasRole('LEADER') ? t('members.subtitle') : undefined}
          />
        </div>
      ) : (
        <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
          {filtered.map((member) => (
            <article key={member.id} className="card p-5">
              <div className="flex items-start justify-between gap-2">
                <div className="flex min-w-0 items-center gap-3">
                  <MemberAvatar name={member.fullName} />
                  <div className="min-w-0">
                    <h2 className="truncate text-sm font-semibold text-forest-950">{member.fullName}</h2>
                    <p className="mt-0.5 text-[0.66rem] text-forest-800/45">
                      <span className="font-mono">{member.memberNumber}</span> · {member.phone}
                    </p>
                  </div>
                </div>
                <RoleBadge role={member.role} />
              </div>

              <div className="mt-4 grid grid-cols-2 gap-2">
                <div className="rounded-2xl bg-parchment/55 px-3 py-2.5">
                  <div className="text-[0.6rem] font-semibold uppercase tracking-wider text-forest-800/40">
                    {t('members.savings')}
                  </div>
                  <div className="mt-1 text-sm font-semibold tabular-nums text-forest-950">
                    {formatMoney(member.savingsMinor, i18n.language)}
                  </div>
                </div>
                <div className="rounded-2xl bg-parchment/55 px-3 py-2.5">
                  <div className="text-[0.6rem] font-semibold uppercase tracking-wider text-forest-800/40">
                    {t('members.shares')}
                  </div>
                  <div className="mt-1 text-sm font-semibold tabular-nums text-forest-950">
                    {formatMoney(member.sharesMinor, i18n.language)}
                  </div>
                </div>
              </div>

              <div className="mt-3 flex items-center justify-between text-[0.65rem] text-forest-800/40">
                <span>{member.email || '—'}</span>
                <span>
                  {t('members.since')} {formatDate(member.createdAt, i18n.language)}
                </span>
              </div>
            </article>
          ))}
        </div>
      )}

      <MemberFormModal
        open={formOpen}
        onClose={() => setFormOpen(false)}
        onCreated={() => {
          setFormOpen(false);
          setFlash(t('members.success'));
          reload();
        }}
      />
    </div>
  );
}

function MemberFormModal({
  open,
  onClose,
  onCreated,
}: {
  open: boolean;
  onClose: () => void;
  onCreated: () => void;
}) {
  const { t } = useTranslation();
  const [fullName, setFullName] = useState('');
  const [phone, setPhone] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [role, setRole] = useState<Role>('MEMBER');
  const [locale, setLocale] = useState('fr');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!open) {
      return;
    }
    setFullName('');
    setPhone('');
    setEmail('');
    setPassword('');
    setRole('MEMBER');
    setLocale('fr');
    setError(null);
    setSubmitting(false);
  }, [open]);

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError(null);
    if (fullName.trim().length < 3) {
      setError(t('common.required'));
      return;
    }
    if (!phone.trim()) {
      setError(t('common.required'));
      return;
    }
    if (password.length < 8) {
      setError(t('members.passwordHint'));
      return;
    }

    const payload: CreateMemberPayload = {
      fullName: fullName.trim(),
      phone: phone.trim(),
      email: email.trim() || null,
      password,
      role,
      locale,
    };

    setSubmitting(true);
    try {
      await api.createMember(payload);
      onCreated();
    } catch (submitError) {
      setError(errorMessage(submitError));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal open={open} onClose={onClose} title={t('members.addTitle')} subtitle={t('members.subtitle')}>
      <form className="space-y-4" onSubmit={handleSubmit} noValidate>
        <div className="field">
          <label className="label" htmlFor="member-name">
            {t('members.fullName')}
          </label>
          <input
            id="member-name"
            className="input"
            value={fullName}
            onChange={(event) => setFullName(event.target.value)}
            autoComplete="name"
            autoFocus
            required
          />
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <div className="field">
            <label className="label" htmlFor="member-phone">
              {t('members.phone')}
            </label>
            <input
              id="member-phone"
              className="input"
              type="tel"
              inputMode="tel"
              placeholder={t('auth.phonePlaceholder')}
              value={phone}
              onChange={(event) => setPhone(event.target.value)}
              autoComplete="tel"
              required
            />
          </div>
          <div className="field">
            <label className="label" htmlFor="member-email">
              {t('members.email')}
            </label>
            <input
              id="member-email"
              className="input"
              type="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              autoComplete="email"
            />
          </div>
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <div className="field">
            <label className="label" htmlFor="member-role">
              {t('members.role')}
            </label>
            <select
              id="member-role"
              className="select"
              value={role}
              onChange={(event) => setRole(event.target.value as Role)}
            >
              {(['MEMBER', 'LEADER', 'AUDITOR'] as Role[]).map((option) => (
                <option key={option} value={option}>
                  {t(`roles.${option}`)}
                </option>
              ))}
            </select>
          </div>
          <div className="field">
            <label className="label" htmlFor="member-locale">
              {t('members.locale')}
            </label>
            <select
              id="member-locale"
              className="select"
              value={locale}
              onChange={(event) => setLocale(event.target.value)}
            >
              {LANGUAGES.map((language) => (
                <option key={language.code} value={language.code}>
                  {language.name}
                </option>
              ))}
            </select>
          </div>
        </div>

        <div className="field">
          <label className="label" htmlFor="member-password">
            {t('members.temporaryPassword')}
          </label>
          <input
            id="member-password"
            className="input"
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            autoComplete="new-password"
            minLength={8}
            required
          />
          <p className="help">{t('members.passwordHint')}</p>
        </div>

        <div className="rounded-2xl bg-parchment/70 px-3.5 py-3 text-[0.7rem] leading-relaxed text-forest-800/60">
          {t('members.success')}
        </div>

        {error && (
          <div role="alert" className="flex items-start gap-2 rounded-2xl bg-red-50 px-3.5 py-3 text-xs text-red-700">
            <AlertIcon size={16} className="mt-0.5 shrink-0" />
            <span>{error}</span>
          </div>
        )}

        <div className="flex justify-end gap-2 border-t border-stone-200/80 pt-4">
          <button type="button" className="btn-secondary" onClick={onClose}>
            {t('common.cancel')}
          </button>
          <button type="submit" className="btn-primary" disabled={submitting}>
            {submitting ? t('members.creating') : t('members.create')}
          </button>
        </div>
      </form>
    </Modal>
  );
}
