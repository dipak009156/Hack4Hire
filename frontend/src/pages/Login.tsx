import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { apiRequest, ApiError } from '../api/client';
import { LoginResponse } from '../api/types';
import { Shield, Lock, User, Building, AlertCircle, ArrowRight } from 'lucide-react';
import { Button } from '../components/common/Button';

interface DemoUser {
  label: string;
  tenant: string;
  username: string;
  role: string;
  trust: string;
}

const DEMO_USERS: DemoUser[] = [
  { label: 'Admin (Alpha)', tenant: 'tenant-alpha', username: 'admin', role: 'ADMIN', trust: 'HIGH' },
  { label: 'Engineer (Alpha)', tenant: 'tenant-alpha', username: 'user1', role: 'USER', trust: 'HIGH' },
  { label: 'Finance (Alpha)', tenant: 'tenant-alpha', username: 'user2', role: 'USER', trust: 'STANDARD' },
  { label: 'Auditor (Alpha)', tenant: 'tenant-alpha', username: 'auditor', role: 'AUDITOR', trust: 'HIGH' },
  { label: 'Admin (Beta)', tenant: 'tenant-beta', username: 'admin', role: 'ADMIN', trust: 'HIGH' },
  { label: 'Admin (Gamma)', tenant: 'tenant-gamma', username: 'admin', role: 'ADMIN', trust: 'HIGH' },
];

export const Login: React.FC = () => {
  const navigate = useNavigate();
  const { login } = useAuth();

  const [tenant, setTenant] = useState('tenant-alpha');
  const [username, setUsername] = useState('admin');
  const [password, setPassword] = useState('password123');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);

    try {
      const res = await apiRequest<LoginResponse>('/auth/login', {
        method: 'POST',
        skipAuth: true,
        body: JSON.stringify({
          tenant: tenant.trim(),
          username: username.trim(),
          password,
        }),
      });

      login(res);
      // Redirect auditors directly to their only page
      const payload = res.roles ?? [];
      if (payload.includes('AUDITOR') && !payload.includes('ADMIN')) {
        navigate('/audit-log');
      } else {
        navigate('/');
      }
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        setError(err.problem.detail || err.problem.title || 'Authentication failed');
      } else if (err instanceof Error) {
        setError(err.message);
      } else {
        setError('An unexpected error occurred during login');
      }
    } finally {
      setLoading(false);
    }
  };

  const handleQuickFill = (user: DemoUser) => {
    setTenant(user.tenant);
    setUsername(user.username);
    setPassword('password123');
    setError(null);
  };

  return (
    <div className="min-h-screen flex flex-col md:flex-row bg-cream text-brown">
      {/* Warm Illustration Side */}
      <div className="md:w-1/2 bg-espresso text-cream p-8 sm:p-12 lg:p-16 flex flex-col justify-between relative overflow-hidden">
        {/* Soft decorative background circles */}
        <div className="absolute -top-24 -right-24 w-96 h-96 rounded-full bg-terracotta/20 blur-3xl pointer-events-none" />
        <div className="absolute -bottom-24 -left-24 w-96 h-96 rounded-full bg-amber/15 blur-3xl pointer-events-none" />

        {/* Brand */}
        <div className="relative z-10">
          <div className="flex items-center gap-3">
            <div className="w-11 h-11 rounded-2xl bg-terracotta flex items-center justify-center text-cream shadow-warm-lg">
              <Shield className="w-6 h-6 fill-current" />
            </div>
            <div>
              <h1 className="text-2xl sm:text-3xl font-extrabold font-fraunces tracking-wide text-cream">
                QUANTUM<span className="font-light text-sand/80 ml-1.5">LOCK</span>
              </h1>
              <p className="text-xs text-sand/60 font-mono tracking-wider uppercase">
                Zero-Trust Document Security
              </p>
            </div>
          </div>
        </div>

        {/* Hero Illustration / Pillars */}
        <div className="relative z-10 my-12 space-y-6 max-w-lg">
          <div className="space-y-3">
            <span className="inline-block px-3 py-1 rounded-full text-xs font-semibold bg-terracotta/20 text-sand border border-terracotta/30">
              High-Assurance Architecture
            </span>
            <h2 className="text-3xl sm:text-4xl font-fraunces font-bold text-cream leading-tight">
              Cryptographic safety at rest, in transit, and across tenants.
            </h2>
            <p className="text-sm text-sand/80 leading-relaxed">
              Envelope encryption with AES-256-GCM, tamper-evident hash chaining,
              pessimistic multi-tenant isolation, and resilient two-phase storage.
            </p>
          </div>

          <div className="grid grid-cols-2 gap-4 pt-4 border-t border-white/10">
            <div className="p-3.5 rounded-xl bg-white/5 border border-white/10">
              <div className="text-amber font-fraunces font-bold text-lg">AES-256-GCM</div>
              <div className="text-xs text-sand/70">Per-version envelope encryption</div>
            </div>
            <div className="p-3.5 rounded-xl bg-white/5 border border-white/10">
              <div className="text-olive font-fraunces font-bold text-lg">SHA-256 Chain</div>
              <div className="text-xs text-sand/70">Tamper-evident audit trail</div>
            </div>
            <div className="p-3.5 rounded-xl bg-white/5 border border-white/10">
              <div className="text-sand font-fraunces font-bold text-lg">In-Memory JWT</div>
              <div className="text-xs text-sand/70">Zero persistent token exposure</div>
            </div>
            <div className="p-3.5 rounded-xl bg-white/5 border border-white/10">
              <div className="text-terracotta font-fraunces font-bold text-lg">Skip-Locked</div>
              <div className="text-xs text-sand/70">Transactional outbox delivery</div>
            </div>
          </div>
        </div>

        {/* Footer note */}
        <div className="relative z-10 text-xs text-sand/50">
          Strict security model: Tenant is never transmitted post-authentication.
        </div>
      </div>

      {/* Form Side */}
      <div className="md:w-1/2 p-8 sm:p-12 lg:p-16 flex flex-col justify-center max-w-xl mx-auto w-full">
        <div className="space-y-2 mb-8">
          <h2 className="text-2xl sm:text-3xl font-bold font-fraunces text-brown">
            Sign In to Quantum-Lock
          </h2>
          <p className="text-sm text-brown/70">
            Select a seeded demo account below or enter your tenant credentials.
          </p>
        </div>

        {/* Demo User Quick-Fill Chips */}
        <div className="mb-6 space-y-2">
          <label className="text-xs font-semibold text-brown/70 uppercase tracking-wider font-mono">
            Demo User Quick Fill:
          </label>
          <div className="flex flex-wrap gap-2">
            {DEMO_USERS.map((user) => (
              <button
                key={`${user.tenant}-${user.username}`}
                type="button"
                onClick={() => handleQuickFill(user)}
                className={`px-2.5 py-1.5 rounded-lg text-xs font-medium border transition-all text-left flex items-center gap-1.5 ${
                  tenant === user.tenant && username === user.username
                    ? 'bg-terracotta text-cream border-terracotta shadow-sm'
                    : 'bg-white/80 text-brown border-sand hover:bg-sand/30 hover:border-sand/80'
                }`}
              >
                <span>{user.label}</span>
                <span className="text-[10px] opacity-75 font-mono">({user.role})</span>
              </button>
            ))}
          </div>
        </div>

        {error && (
          <div className="mb-6 p-4 rounded-xl bg-brick/10 border border-brick/30 text-brick text-sm flex items-start gap-2.5 animate-in fade-in">
            <AlertCircle className="w-5 h-5 shrink-0 mt-0.5" />
            <div>
              <div className="font-semibold">Authentication Failed</div>
              <div className="text-xs mt-0.5 opacity-90">{error}</div>
            </div>
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-xs font-semibold uppercase tracking-wider text-brown/80 mb-1.5">
              Tenant Identifier
            </label>
            <div className="relative">
              <Building className="w-4 h-4 text-brown/40 absolute left-3.5 top-3" />
              <input
                type="text"
                required
                value={tenant}
                onChange={(e) => setTenant(e.target.value)}
                placeholder="tenant-alpha"
                className="w-full pl-10 pr-4 py-2.5 rounded-xl border border-sand bg-white/90 text-sm focus:outline-none focus:ring-2 focus:ring-terracotta font-mono"
              />
            </div>
            <p className="text-[11px] text-brown/50 mt-1">
              Required for ADR-001 tenant-boundary isolation.
            </p>
          </div>

          <div>
            <label className="block text-xs font-semibold uppercase tracking-wider text-brown/80 mb-1.5">
              Username
            </label>
            <div className="relative">
              <User className="w-4 h-4 text-brown/40 absolute left-3.5 top-3" />
              <input
                type="text"
                required
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                placeholder="admin"
                className="w-full pl-10 pr-4 py-2.5 rounded-xl border border-sand bg-white/90 text-sm focus:outline-none focus:ring-2 focus:ring-terracotta"
              />
            </div>
          </div>

          <div>
            <label className="block text-xs font-semibold uppercase tracking-wider text-brown/80 mb-1.5">
              Password
            </label>
            <div className="relative">
              <Lock className="w-4 h-4 text-brown/40 absolute left-3.5 top-3" />
              <input
                type="password"
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="••••••••"
                className="w-full pl-10 pr-4 py-2.5 rounded-xl border border-sand bg-white/90 text-sm focus:outline-none focus:ring-2 focus:ring-terracotta font-mono"
              />
            </div>
          </div>

          <Button
            type="submit"
            isLoading={loading}
            variant="primary"
            size="lg"
            className="w-full mt-4 flex items-center justify-center gap-2"
          >
            <span>Authenticate Session</span>
            <ArrowRight className="w-4 h-4" />
          </Button>
        </form>

        <div className="mt-8 pt-6 border-t border-sand text-xs text-brown/60 text-center">
          In-memory security: Refreshing the browser terminates the session immediately.
        </div>
      </div>
    </div>
  );
};

