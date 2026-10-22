import { useEffect, useState, type FormEvent } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAuth } from '@/lib/auth';
import { errorMessage } from '@/lib/useAsync';
import { formatMoney } from '@/lib/format';
import { Brand } from '@/components/Brand';
import { LanguageSwitcher } from '@/components/LanguageSwitcher';
import {
  AlertIcon,
  CheckIcon,
  OfflineIcon,
  ShieldIcon,
  WalletIcon,
} from '@/components/Icons';

const DEMO_ACCOUNTS = [
  { phone: '+243990000001', role: 'LEADER' as const },
  { phone: '+243990000002', role: 'AUDITOR' as const },
  { phone: '+243990000003', role: 'MEMBER' as const },
];

export function LoginPage() {
  const { t, i18n } = useTranslation();
  const { user, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [phone, setPhone] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const demoPassword = import.meta.env.VITE_DEMO_PASSWORD || 'Moziki2026!';
  const isDev = import.meta.env.DEV;

  // A shared device can switch accounts without a clean sign-out. Drop cached
  // API responses as soon as the login screen is shown so no member ever sees
  // another member's ledger from the service-worker cache.
  useEffect(() => {
    if ('caches' in window) {
      void caches.delete('moziki-api');
    }
  }, []);

  if (user) {
    return <Navigate to="/" replace />;
  }

  const from = (location.state as { from?: { pathname?: string } } | null)?.from?.pathname || '/';

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await login(phone.trim(), password);
      navigate(from, { replace: true });
    } catch (loginError) {
      setError(errorMessage(loginError));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="min-h-screen lg:grid lg:grid-cols-[1.08fr_1fr]">
      <section className="relative hidden overflow-hidden bg-forest-950 p-10 lg:flex lg:flex-col lg:justify-between xl:p-14">
        <div
          className="pointer-events-none absolute -right-40 -top-40 h-[30rem] w-[30rem] rounded-full border border-white/[0.07]"
          aria-hidden="true"
        />
        <div
          className="pointer-events-none absolute -right-16 -top-16 h-[20rem] w-[20rem] rounded-full border border-gold-400/10"
          aria-hidden="true"
        />
        <div
          className="pointer-events-none absolute -bottom-48 -left-32 h-[28rem] w-[28rem] rounded-full bg-forest-800/30 blur-3xl"
          aria-hidden="true"
        />

        <div className="relative">
          <Brand inverted />
        </div>

        <div className="relative max-w-xl py-12">
          <p className="mb-3 text-[0.68rem] font-bold uppercase tracking-[0.28em] text-gold-300/80">
            {t('app.tagline')}
          </p>
          <h1 className="font-display text-4xl leading-[1.08] tracking-tight text-cream xl:text-[3.4rem]">
            {t('auth.heroTitle')}
          </h1>
          <p className="mt-5 max-w-md text-sm leading-relaxed text-forest-100/55">
            {t('auth.heroBody')}
          </p>

          <div className="mt-10 space-y-3">
            {[
              { icon: ShieldIcon, title: t('auth.featureLedger'), body: t('auth.featureLedgerBody') },
              { icon: WalletIcon, title: t('auth.featureAudit'), body: t('auth.featureAuditBody') },
              { icon: OfflineIcon, title: t('auth.featureOffline'), body: t('auth.featureOfflineBody') },
            ].map((feature) => (
              <div
                key={feature.title}
                className="flex items-start gap-3 rounded-2xl border border-white/[0.07] bg-white/[0.035] px-4 py-3 backdrop-blur-sm"
              >
                <feature.icon size={19} className="mt-0.5 shrink-0 text-gold-300" />
                <div>
                  <div className="text-xs font-semibold text-cream">{feature.title}</div>
                  <div className="mt-0.5 text-[0.7rem] leading-relaxed text-forest-200/50">
                    {feature.body}
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>

        <div className="relative rounded-3xl border border-white/10 bg-white/[0.04] p-5 backdrop-blur-sm">
          <div className="mb-4 flex items-center justify-between">
            <span className="text-[0.62rem] font-bold uppercase tracking-[0.2em] text-forest-200/50">
              {t('dashboard.trusted')}
            </span>
            <span className="inline-flex items-center gap-1 rounded-full bg-forest-500/20 px-2 py-0.5 text-[0.62rem] font-semibold text-forest-100">
              <CheckIcon size={11} /> CDF
            </span>
          </div>
          <div className="space-y-2.5">
            <div className="flex items-center justify-between text-xs">
              <span className="text-forest-200/60">{t('kinds.DEPOSIT')}</span>
              <span className="font-medium tabular-nums text-cream">
                {formatMoney(5_000_000, i18n.language)}
              </span>
            </div>
            <div className="flex items-center justify-between text-xs">
              <span className="text-forest-200/60">{t('categories.SAVINGS')}</span>
              <span className="font-medium tabular-nums text-gold-200">
                {formatMoney(5_000_000, i18n.language)}
              </span>
            </div>
            <div className="flex items-center justify-between border-t border-white/10 pt-2.5 text-[0.68rem]">
              <span className="text-gold-300/90">{t('transactions.balanced')}</span>
              <CheckIcon size={14} className="text-gold-300" />
            </div>
          </div>
        </div>
      </section>

      <section className="flex min-h-screen flex-col px-5 py-6 sm:px-10 lg:px-14">
        <header className="flex items-center justify-between">
          <div className="lg:hidden">
            <Brand />
          </div>
          <div className="ml-auto">
            <LanguageSwitcher />
          </div>
        </header>

        <div className="mx-auto flex w-full max-w-sm flex-1 flex-col justify-center py-10">
          <div className="animate-fade-up">
            <h2 className="font-display text-[2.15rem] font-semibold tracking-tight text-forest-950">
              {t('auth.welcome')}
            </h2>
            <p className="mt-1.5 text-sm text-forest-800/55">{t('auth.subtitle')}</p>
          </div>

          <form className="mt-8 space-y-4" onSubmit={handleSubmit} noValidate>
            <div className="field">
              <label className="label" htmlFor="login-phone">
                {t('auth.phone')}
              </label>
              <input
                id="login-phone"
                name="phone"
                type="tel"
                autoComplete="tel"
                inputMode="tel"
                className="input"
                placeholder={t('auth.phonePlaceholder')}
                value={phone}
                onChange={(event) => setPhone(event.target.value)}
                required
              />
            </div>

            <div className="field">
              <label className="label" htmlFor="login-password">
                {t('auth.password')}
              </label>
              <input
                id="login-password"
                name="password"
                type="password"
                autoComplete="current-password"
                className="input"
                placeholder={t('auth.passwordPlaceholder')}
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                required
              />
            </div>

            {error && (
              <div
                role="alert"
                className="flex items-start gap-2 rounded-2xl bg-red-50 px-3.5 py-3 text-xs text-red-700"
              >
                <AlertIcon size={16} className="mt-0.5 shrink-0" />
                <span>{error}</span>
              </div>
            )}

            <button type="submit" className="btn-primary w-full py-3" disabled={submitting}>
              {submitting ? t('auth.signingIn') : t('auth.signIn')}
            </button>
          </form>

          {isDev && (
            <div className="mt-8 rounded-3xl border border-stone-200/90 bg-white/60 p-4">
              <div className="flex items-center justify-between">
                <h3 className="text-[0.68rem] font-bold uppercase tracking-[0.16em] text-forest-800/45">
                  {t('auth.demoTitle')}
                </h3>
                <span className="rounded-full bg-parchment px-2 py-0.5 text-[0.58rem] font-semibold uppercase tracking-wider text-forest-800/50">
                  dev
                </span>
              </div>
              <p className="mt-1 text-[0.68rem] text-forest-800/45">{t('auth.demoHint')}</p>
              <div className="mt-3 grid gap-1.5">
                {DEMO_ACCOUNTS.map((account) => (
                  <button
                    key={account.phone}
                    type="button"
                    onClick={() => {
                      setPhone(account.phone);
                      setPassword(demoPassword);
                      setError(null);
                    }}
                    className="flex items-center justify-between rounded-xl px-2.5 py-2 text-left text-xs transition hover:bg-parchment/70"
                  >
                    <span className="font-medium text-forest-900">
                      {t(
                        account.role === 'LEADER'
                          ? 'auth.demoLeader'
                          : account.role === 'AUDITOR'
                            ? 'auth.demoAuditor'
                            : 'auth.demoMember',
                      )}
                    </span>
                    <span className="font-mono text-[0.65rem] text-forest-800/45">{account.phone}</span>
                  </button>
                ))}
              </div>
              <p className="mt-2 border-t border-stone-200/70 pt-2 text-[0.62rem] text-forest-800/40">
                {t('demo.password')} : <span className="font-mono">{demoPassword}</span>
              </p>
            </div>
          )}
        </div>

        <footer className="flex items-center justify-between text-[0.62rem] text-forest-800/35">
          <span>© {new Date().getFullYear()} Moziki</span>
          <span>{t('auth.languages')}</span>
        </footer>
      </section>
    </div>
  );
}
