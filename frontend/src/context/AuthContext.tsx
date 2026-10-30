import React, { createContext, useContext, useState, useCallback, useMemo } from 'react';
import { setAuthToken } from '../api/client';
import { LoginResponse } from '../api/types';

export interface JwtPayload {
  sub: string;
  tenant: string;
  roles: string[];
  department?: string;
  device_trust?: string;
  exp?: number;
  iat?: number;
}

export interface UserSession {
  token: string;
  username: string;
  tenantId: string;
  roles: string[];
  department?: string;
  deviceTrust: string;
}

interface AuthContextType {
  session: UserSession | null;
  isAuthenticated: boolean;
  login: (data: LoginResponse) => void;
  logout: () => void;
  hasRole: (role: string) => boolean;
  hasAnyRole: (roles: string[]) => boolean;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

function parseJwt(token: string): JwtPayload | null {
  try {
    const base64Url = token.split('.')[1];
    if (!base64Url) return null;
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
    const jsonPayload = decodeURIComponent(
      window
        .atob(base64)
        .split('')
        .map(c => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
        .join('')
    );
    return JSON.parse(jsonPayload);
  } catch (err) {
    console.error('Failed to parse JWT payload', err);
    return null;
  }
}

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  // STRICT SECURITY RULE: in-memory state ONLY — NO localStorage or sessionStorage
  const [session, setSession] = useState<UserSession | null>(null);

  const login = useCallback((data: LoginResponse) => {
    const payload = parseJwt(data.accessToken);
    if (!payload) {
      throw new Error('Invalid JWT returned from server');
    }

    setAuthToken(data.accessToken);

    setSession({
      token: data.accessToken,
      username: payload.sub || 'User',
      tenantId: payload.tenant || data.tenantId,
      roles: payload.roles || data.roles || ['USER'],
      department: payload.department,
      deviceTrust: payload.device_trust || 'STANDARD',
    });
  }, []);

  const logout = useCallback(() => {
    setAuthToken(null);
    setSession(null);
  }, []);

  const hasRole = useCallback((role: string) => {
    if (!session) return false;
    return session.roles.includes(role);
  }, [session]);

  const hasAnyRole = useCallback((roles: string[]) => {
    if (!session) return false;
    return roles.some(r => session.roles.includes(r));
  }, [session]);

  const value = useMemo(() => ({
    session,
    isAuthenticated: session !== null,
    login,
    logout,
    hasRole,
    hasAnyRole,
  }), [session, login, logout, hasRole, hasAnyRole]);

  return (
    <AuthContext.Provider value={value}>
      {children}
    </AuthContext.Provider>
  );
};

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}

