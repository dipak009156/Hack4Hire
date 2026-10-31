import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { QueryClient, QueryClientProvider, QueryCache, MutationCache } from '@tanstack/react-query';
import { AuthProvider, useAuth } from './context/AuthContext';
import { Layout } from './components/layout/Layout';
import { ToastProvider } from './components/common/Toast';
import { AccessDenied } from './components/common/AccessDenied';
import { ApiError } from './api/client';

// 11 Pages
import { Login } from './pages/Login';
import { Dashboard } from './pages/Dashboard';
import { Documents } from './pages/Documents';
import { Search } from './pages/Search';
import { Sharing } from './pages/Sharing';
import { KeyManagement } from './pages/KeyManagement';
import { Policies } from './pages/Policies';
import { AuditLog } from './pages/AuditLog';
import { Recovery } from './pages/Recovery';
import { Cipherstorm } from './pages/Cipherstorm';
import { SecurityProof } from './pages/SecurityProof';
import { UserManagement } from './pages/UserManagement';

// ─── Error Boundary ────────────────────────────────────────────────────────────

interface ErrorBoundaryState {
  hasError: boolean;
  errorMessage: string;
}

class AppErrorBoundary extends React.Component<
  { children: React.ReactNode },
  ErrorBoundaryState
> {
  constructor(props: { children: React.ReactNode }) {
    super(props);
    this.state = { hasError: false, errorMessage: '' };
  }

  static getDerivedStateFromError(error: unknown): ErrorBoundaryState {
    const msg = error instanceof Error ? error.message : String(error);
    return { hasError: true, errorMessage: msg };
  }

  componentDidCatch(error: unknown, info: React.ErrorInfo) {
    console.error('[AppErrorBoundary] Uncaught render error:', error, info.componentStack);
  }

  render() {
    if (this.state.hasError) {
      return (
        <div className="min-h-screen bg-cream flex items-center justify-center p-6">
          <div className="max-w-md w-full bg-white border border-sand rounded-2xl p-8 shadow-sm text-center space-y-4">
            <div className="w-14 h-14 rounded-full bg-brick/10 text-brick flex items-center justify-center mx-auto">
              <svg className="w-7 h-7" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v2m0 4h.01M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z" />
              </svg>
            </div>
            <h1 className="text-xl font-bold font-fraunces text-brown">
              Something went wrong
            </h1>
            <p className="text-sm text-brown/70 leading-relaxed">
              An unexpected rendering error occurred. Your session and keys are safe.
            </p>
            {this.state.errorMessage && (
              <pre className="text-left text-xs bg-sand/20 rounded-lg px-3 py-2 text-brick/80 font-mono overflow-auto max-h-28">
                {this.state.errorMessage}
              </pre>
            )}
            <button
              onClick={() => {
                this.setState({ hasError: false, errorMessage: '' });
                window.location.href = '/';
              }}
              className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl bg-terracotta text-cream text-sm font-semibold hover:bg-terracotta/90 transition-colors"
            >
              Return to Dashboard
            </button>
          </div>
        </div>
      );
    }
    return this.props.children;
  }
}

// ─── QueryClient with 403/404 suppression ─────────────────────────────────────

function isExpectedError(error: unknown): boolean {
  if (error instanceof ApiError) {
    return error.status === 403 || error.status === 404;
  }
  return false;
}

const queryClient = new QueryClient({
  queryCache: new QueryCache({
    onError: (error) => {
      if (!isExpectedError(error)) {
        console.error('[QueryCache] Unhandled query error:', error);
      }
    },
  }),
  mutationCache: new MutationCache({
    onError: (error) => {
      if (!isExpectedError(error)) {
        console.error('[MutationCache] Unhandled mutation error:', error);
      }
    },
  }),
  defaultOptions: {
    queries: {
      retry: false,
      throwOnError: false,
      refetchOnWindowFocus: false,
    },
    mutations: {
      throwOnError: false,
    },
  },
});

// ─── Protected Route ───────────────────────────────────────────────────────────

interface ProtectedRouteProps {
  children: React.ReactNode;
  requiredRole?: string;
  requiredAnyRole?: string[];
}

const ProtectedRoute: React.FC<ProtectedRouteProps> = ({ children, requiredRole, requiredAnyRole }) => {
  const { isAuthenticated, hasRole, hasAnyRole } = useAuth();

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  if (requiredRole && !hasRole(requiredRole)) {
    return <AccessDenied requiredRole={requiredRole} />;
  }

  if (requiredAnyRole && !hasAnyRole(requiredAnyRole)) {
    return <AccessDenied requiredRole={requiredAnyRole.join(' or ')} />;
  }

  return <>{children}</>;
};

// ─── App ──────────────────────────────────────────────────────────────────────

export const App: React.FC = () => {
  return (
    <AppErrorBoundary>
      <QueryClientProvider client={queryClient}>
        <AuthProvider>
          <ToastProvider>
            <BrowserRouter>
              <Routes>
                <Route path="/login" element={<Login />} />

                <Route
                  path="/"
                  element={
                    <ProtectedRoute>
                      <Layout />
                    </ProtectedRoute>
                  }
                >
                  <Route index element={
                    <ProtectedRoute requiredAnyRole={['ADMIN', 'USER']}>
                      <Dashboard />
                    </ProtectedRoute>
                  } />
                  <Route path="documents" element={
                    <ProtectedRoute requiredAnyRole={['ADMIN', 'USER']}>
                      <Documents />
                    </ProtectedRoute>
                  } />
                  <Route path="search" element={
                    <ProtectedRoute requiredAnyRole={['ADMIN', 'USER']}>
                      <Search />
                    </ProtectedRoute>
                  } />
                  <Route path="sharing" element={
                    <ProtectedRoute requiredAnyRole={['ADMIN', 'USER']}>
                      <Sharing />
                    </ProtectedRoute>
                  } />
                  <Route
                    path="key-management"
                    element={
                      <ProtectedRoute requiredRole="ADMIN">
                        <KeyManagement />
                      </ProtectedRoute>
                    }
                  />
                  <Route
                    path="policies"
                    element={
                      <ProtectedRoute requiredRole="ADMIN">
                        <Policies />
                      </ProtectedRoute>
                    }
                  />
                  <Route
                    path="users"
                    element={
                      <ProtectedRoute requiredRole="ADMIN">
                        <UserManagement />
                      </ProtectedRoute>
                    }
                  />
                  <Route path="audit-log" element={
                    <ProtectedRoute requiredAnyRole={['ADMIN', 'AUDITOR']}>
                      <AuditLog />
                    </ProtectedRoute>
                  } />
                  <Route
                    path="recovery"
                    element={
                      <ProtectedRoute requiredRole="ADMIN">
                        <Recovery />
                      </ProtectedRoute>
                    }
                  />
                  <Route
                    path="cipherstorm"
                    element={
                      <ProtectedRoute requiredRole="ADMIN">
                        <Cipherstorm />
                      </ProtectedRoute>
                    }
                  />
                  <Route path="security-proof" element={
                    <ProtectedRoute requiredAnyRole={['ADMIN', 'USER']}>
                      <SecurityProof />
                    </ProtectedRoute>
                  } />
                </Route>

                <Route path="*" element={<Navigate to="/" replace />} />
              </Routes>
            </BrowserRouter>
          </ToastProvider>
        </AuthProvider>
      </QueryClientProvider>
    </AppErrorBoundary>
  );
};

export default App;
