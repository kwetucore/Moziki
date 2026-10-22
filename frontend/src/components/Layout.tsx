import { useCallback, useEffect, useRef, useState, type ReactElement } from 'react';
import { NavLink, Outlet } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAuth } from '@/lib/auth';
import { queuedCount, flushQueue } from '@/lib/offlineQueue';
import { formatDate } from '@/lib/format';
import type { Role } from '@/lib/types';
import { Brand } from './Brand';
import { LanguageSwitcher } from './LanguageSwitcher';
import {
  CheckIcon,
  HomeIcon,
  LedgerIcon,
  LockIcon,
  LogoutIcon,
  OfflineIcon,
  RefreshIcon,
  ShieldIcon,
  UsersIcon,
  WalletIcon,
} from './Icons';
import { Avatar } from './ui';

interface NavItem {
  to: string;
  labelKey: string;
  icon: (props: { size?: number }) => ReactElement;
  roles?: Role[];
  end?: boolean;
}

const NAV_ITEMS: NavItem[] = [
  { to: '/', labelKey: 'nav.dashboard', icon: HomeIcon, end: true },
  { to: '/transactions', labelKey: 'nav.transactions', icon: LedgerIcon },
  { to: '/members', labelKey: 'nav.members', icon: UsersIcon, roles: ['LEADER', 'AUDITOR'] },
  { to: '/accounts', labelKey: 'nav.accounts', icon: WalletIcon },
  { to: '/audit', labelKey: 'nav.audit', icon: ShieldIcon, roles: ['LEADER', 'AUDITOR'] },
];

export function Layout() {
  const { t, i18n } = useTranslation();
  const { user, cooperative, hasRole, logout } = useAuth();
  const [online, setOnline] = useState(() => navigator.onLine);
  const [pending, setPending] = useState(0);
  const [syncing, setSyncing] = useState(false);
  const syncingRef = useRef(false);
  const [notice, setNotice] = useState<string | null>(null);

  const refreshPending = useCallback(async () => {
    try {
      setPending(await queuedCount());
    } catch {
      // IndexedDB may be unavailable in private modes; the app still works online.
    }
  }, []);

  const sync = useCallback(async () => {
    if (syncingRef.current || !navigator.onLine) {
      return;
    }
    const queued = await queuedCount().catch(() => 0);
    if (queued === 0) {
      return;
    }
    syncingRef.current = true;
    setSyncing(true);
    try {
      const result = await flushQueue();
      if (result.posted > 0) {
        setNotice(t('app.syncDone'));
      }
    } finally {
      syncingRef.current = false;
      setSyncing(false);
      await refreshPending();
    }
  }, [refreshPending, t]);

  useEffect(() => {
    void refreshPending();
    // A previous session may have ended offline. Flush what is still queued as
    // soon as the app opens with a connection, instead of waiting for the
    // browser to emit another `online` event.
    if (navigator.onLine) {
      void sync();
    }

    const handleOnline = () => {
      setOnline(true);
      void sync();
    };
    const handleOffline = () => setOnline(false);
    const handleQueueChange = () => {
      void refreshPending();
      if (navigator.onLine) {
        void sync();
      }
    };

    window.addEventListener('online', handleOnline);
    window.addEventListener('offline', handleOffline);
    window.addEventListener('moziki:queue-changed', handleQueueChange);
    return () => {
      window.removeEventListener('online', handleOnline);
      window.removeEventListener('offline', handleOffline);
      window.removeEventListener('moziki:queue-changed', handleQueueChange);
    };
  }, [refreshPending, sync]);

  useEffect(() => {
    if (!notice) {
      return;
    }
    const timeout = window.setTimeout(() => setNotice(null), 4500);
    return () => window.clearTimeout(timeout);
  }, [notice]);

  const items = NAV_ITEMS.filter((item) => !item.roles || hasRole(...item.roles));
  const showBanner = !online || pending > 0 || syncing;

  return (
    <div className="min-h-screen lg:pl-[17rem]">
      <aside className="fixed inset-y-0 left-0 z-30 hidden w-[17rem] flex-col overflow-y-auto bg-forest-950 px-5 py-6 lg:flex">
        <Brand inverted />
        <div className="mt-8 rounded-2xl border border-white/10 bg-white/[0.04] px-4 py-3">
          <div className="text-[0.6rem] font-semibold uppercase tracking-[0.18em] text-forest-300/70">
            {cooperative?.code ?? '—'}
          </div>
          <div className="mt-1 text-sm font-medium leading-snug text-forest-50">
            {cooperative?.name}
          </div>
          <div className="mt-2 flex items-center gap-1.5 text-[0.68rem] text-forest-300/60">
            <LockIcon size={12} />
            {t('audit.immutable')}
          </div>
        </div>

        <nav className="mt-7 flex flex-1 flex-col gap-1" aria-label={t('nav.menu')}>
          {items.map((item) => {
            const Icon = item.icon;
            return (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.end}
                className={({ isActive }) =>
                  `group flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium transition ${
                    isActive
                      ? 'bg-white/[0.09] text-cream shadow-sm ring-1 ring-white/10'
                      : 'text-forest-200/65 hover:bg-white/[0.05] hover:text-cream'
                  }`
                }
              >
                {({ isActive }) => (
                  <>
                    <Icon size={19} />
                    <span>{t(item.labelKey)}</span>
                    {isActive && <span className="ml-auto h-1.5 w-1.5 rounded-full bg-gold-400" />}
                  </>
                )}
              </NavLink>
            );
          })}
        </nav>

        <div className="mt-6 border-t border-white/10 pt-4">
          <div className="flex items-center gap-3 px-1">
            <Avatar name={user?.fullName ?? 'Moziki'} size="sm" />
            <div className="min-w-0 flex-1">
              <div className="truncate text-sm font-medium text-cream">{user?.fullName}</div>
              <div className="text-[0.68rem] text-forest-300/60">
                {user ? t(`roles.${user.role}`) : ''}
              </div>
            </div>
            <button
              type="button"
              onClick={logout}
              className="rounded-full p-2 text-forest-300/60 transition hover:bg-white/10 hover:text-cream"
              aria-label={t('app.signOut')}
              title={t('app.signOut')}
            >
              <LogoutIcon size={17} />
            </button>
          </div>
          <p className="mt-4 px-1 text-[0.62rem] leading-relaxed text-forest-300/40">
            {t('app.footer')}
          </p>
        </div>
      </aside>

      <header className="sticky top-0 z-20 border-b border-stone-200/70 bg-cream/85 backdrop-blur-md">
        <div className="flex h-16 items-center justify-between gap-3 px-4 sm:px-6 lg:px-10">
          <div className="lg:hidden">
            <Brand compact />
          </div>
          <div className="hidden items-center gap-2 text-xs text-forest-800/50 lg:flex">
            <span className={`h-2 w-2 rounded-full ${online ? 'bg-forest-500' : 'bg-gold-500'}`} />
            {formatDate(new Date().toISOString(), i18n.language)}
            <span className="mx-1 text-stone-300">·</span>
            {t('auth.languages')}
          </div>

          <div className="flex items-center gap-2 sm:gap-3">
            {pending > 0 && (
              <span className="hidden items-center gap-1.5 rounded-full bg-gold-100 px-3 py-1 text-[0.68rem] font-semibold text-gold-700 sm:inline-flex">
                <RefreshIcon size={13} className={syncing ? 'animate-spin' : ''} />
                {syncing ? t('app.syncing', { count: pending }) : t('app.pendingTransactions', { count: pending })}
              </span>
            )}
            <LanguageSwitcher />
            <div className="hidden h-8 w-px bg-stone-300/60 sm:block" />
            <div className="hidden items-center gap-2 sm:flex">
              <Avatar name={user?.fullName ?? 'Moziki'} size="sm" />
              <div className="hidden md:block">
                <div className="text-xs font-semibold leading-tight text-forest-900">
                  {user?.fullName.split(' ')[0]}
                </div>
                <div className="text-[0.62rem] uppercase tracking-wider text-forest-800/45">
                  {user ? t(`roles.${user.role}`) : ''}
                </div>
              </div>
            </div>
            <button
              type="button"
              onClick={logout}
              className="btn-icon sm:hidden"
              aria-label={t('app.signOut')}
            >
              <LogoutIcon size={18} />
            </button>
          </div>
        </div>

        {showBanner && (
          <div
            className={`flex items-center gap-3 border-t px-4 py-2 text-xs sm:px-6 lg:px-10 ${
              !online
                ? 'border-gold-200/70 bg-gold-50 text-gold-800'
                : 'border-forest-100 bg-forest-50 text-forest-800'
            }`}
          >
            {!online ? <OfflineIcon size={16} /> : <RefreshIcon size={16} className={syncing ? 'animate-spin' : ''} />}
            <span className="flex-1">
              {!online
                ? `${t('app.offline')} — ${t('app.offlineHint')}`
                : syncing
                  ? t('app.syncing', { count: pending })
                  : t('app.pendingTransactions', { count: pending })}
            </span>
            {online && pending > 0 && !syncing && (
              <button type="button" onClick={() => void sync()} className="font-semibold underline underline-offset-2">
                {t('common.retry')}
              </button>
            )}
          </div>
        )}

        {notice && (
          <div className="flex items-center gap-2 border-t border-forest-200/60 bg-forest-800 px-4 py-2 text-xs font-medium text-cream sm:px-6 lg:px-10">
            <CheckIcon size={15} className="text-gold-300" />
            {notice}
          </div>
        )}
      </header>

      <main className="px-4 pb-28 pt-6 sm:px-6 lg:px-10 lg:pb-12 lg:pt-9">
        <div className="mx-auto max-w-6xl">
          <Outlet />
          <footer className="mt-14 flex flex-col items-center justify-between gap-2 border-t border-stone-200/70 pt-6 text-[0.68rem] text-forest-800/40 sm:flex-row">
            <span>{t('app.footer')}</span>
            <span className="flex items-center gap-1.5">
              <ShieldIcon size={13} /> {t('app.tagline')}
            </span>
          </footer>
        </div>
      </main>

      <nav
        className="fixed inset-x-0 bottom-0 z-30 border-t border-stone-200/80 bg-cream/95 pb-[env(safe-area-inset-bottom)] backdrop-blur-md lg:hidden"
        aria-label={t('nav.menu')}
      >
        <div className="mx-auto flex max-w-lg items-stretch justify-around">
          {items.map((item) => {
            const Icon = item.icon;
            return (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.end}
                className={({ isActive }) =>
                  `relative flex flex-1 flex-col items-center gap-1 px-1 py-3 text-[0.6rem] font-medium ${
                    isActive ? 'text-forest-900' : 'text-forest-800/45'
                  }`
                }
              >
                {({ isActive }) => (
                  <>
                    {isActive && (
                      <span className="absolute top-0 h-0.5 w-8 rounded-full bg-gold-400" />
                    )}
                    <Icon size={20} />
                    <span className="max-w-full truncate">{t(item.labelKey)}</span>
                  </>
                )}
              </NavLink>
            );
          })}
        </div>
      </nav>
    </div>
  );
}
