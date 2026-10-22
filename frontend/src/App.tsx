import { BrowserRouter, Navigate, Outlet, Route, Routes, useLocation } from 'react-router-dom';
import { AuthProvider, useAuth } from '@/lib/auth';
import type { Role } from '@/lib/types';
import { Layout } from '@/components/Layout';
import { Brand } from '@/components/Brand';
import { LoginPage } from '@/pages/LoginPage';
import { DashboardPage } from '@/pages/DashboardPage';
import { TransactionsPage } from '@/pages/TransactionsPage';
import { MembersPage } from '@/pages/MembersPage';
import { AccountsPage } from '@/pages/AccountsPage';
import { AuditPage } from '@/pages/AuditPage';

function LoadingScreen() {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-4 bg-cream">
      <Brand />
      <div className="flex items-center gap-2 text-xs text-forest-800/45">
        <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-gold-500" />
        <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-gold-500 [animation-delay:120ms]" />
        <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-gold-500 [animation-delay:240ms]" />
      </div>
    </div>
  );
}

function RequireAuth() {
  const { user, loading } = useAuth();
  const location = useLocation();

  if (loading) {
    return <LoadingScreen />;
  }
  if (!user) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }
  return <Outlet />;
}

function RequireRole({ roles }: { roles: Role[] }) {
  const { user, loading } = useAuth();
  if (loading) {
    return <LoadingScreen />;
  }
  if (!user || !roles.includes(user.role)) {
    return <Navigate to="/" replace />;
  }
  return <Outlet />;
}

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route element={<RequireAuth />}>
            <Route element={<Layout />}>
              <Route index element={<DashboardPage />} />
              <Route path="transactions" element={<TransactionsPage />} />
              <Route path="accounts" element={<AccountsPage />} />
              <Route element={<RequireRole roles={['LEADER', 'AUDITOR']} />}>
                <Route path="members" element={<MembersPage />} />
                <Route path="audit" element={<AuditPage />} />
              </Route>
            </Route>
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}
