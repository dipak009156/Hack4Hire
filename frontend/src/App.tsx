import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider, useAuth } from './context/AuthContext';
import { Layout } from './components/layout/Layout';

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

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
});

interface ProtectedRouteProps {
  children: React.ReactNode;
  requiredRole?: string;
}

const ProtectedRoute: React.FC<ProtectedRouteProps> = ({ children, requiredRole }) => {
  const { isAuthenticated, hasRole } = useAuth();

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  if (requiredRole && !hasRole(requiredRole)) {
    return <Navigate to="/" replace />;
  }

  return <>{children}</>;
};

export const App: React.FC = () => {
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
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
              <Route index element={<Dashboard />} />
              <Route path="documents" element={<Documents />} />
              <Route path="search" element={<Search />} />
              <Route path="sharing" element={<Sharing />} />
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
              <Route path="audit-log" element={<AuditLog />} />
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
              <Route path="security-proof" element={<SecurityProof />} />
            </Route>

            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </BrowserRouter>
      </AuthProvider>
    </QueryClientProvider>
  );
};

export default App;
