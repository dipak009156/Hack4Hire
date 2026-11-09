import React, { useState, useEffect } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { apiRequest, ApiError } from '../api/client';
import { CreateCredentialRequestDto, CredentialRequestResponse, TenantCheckResponse } from '../api/types';
import { Card } from '../components/common/Card';
import { Button } from '../components/common/Button';
import { useToast } from '../components/common/Toast';
import { EmailInboxModal } from '../components/common/EmailInboxModal';
import {
  Shield,
  Building,
  User,
  Mail,
  FileText,
  CheckCircle2,
  AlertCircle,
  Clock,
  ArrowLeft,
  Send,
  Lock,
  Layers,
} from 'lucide-react';

export const RequestCredentials: React.FC = () => {
  const navigate = useNavigate();
  const { showToast } = useToast();

  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [username, setUsername] = useState('');
  const [tenantName, setTenantName] = useState('');
  const [department, setDepartment] = useState('Engineering');
  const [reason, setReason] = useState('');

  // Tenant availability check state
  const [tenantCheckStatus, setTenantCheckStatus] = useState<'IDLE' | 'CHECKING' | 'AVAILABLE' | 'TAKEN' | 'PENDING'>('IDLE');
  const [tenantCheckMessage, setTenantCheckMessage] = useState<string>('');

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [submittedRequest, setSubmittedRequest] = useState<CredentialRequestResponse | null>(null);
  const [isInboxOpen, setIsInboxOpen] = useState(false);

  // Debounced tenant uniqueness check
  useEffect(() => {
    const clean = tenantName.trim().toLowerCase();
    if (!clean || clean.length < 3) {
      setTenantCheckStatus('IDLE');
      setTenantCheckMessage(clean ? 'Tenant name must be at least 3 characters' : '');
      return;
    }

    setTenantCheckStatus('CHECKING');
    const timer = setTimeout(async () => {
      try {
        const res = await apiRequest<TenantCheckResponse>(`/auth/check-tenant?name=${encodeURIComponent(clean)}`, {
          skipAuth: true,
        });

        if (res.available) {
          setTenantCheckStatus('AVAILABLE');
          setTenantCheckMessage(`✓ "${clean}" is available! Redundancy check passed.`);
        } else if (res.pending) {
          setTenantCheckStatus('PENDING');
          setTenantCheckMessage(`⏳ A request for "${clean}" is already pending approval.`);
        } else {
          setTenantCheckStatus('TAKEN');
          setTenantCheckMessage(`✗ "${clean}" is already taken! Redundancy prevented. Please pick a unique name.`);
        }
      } catch (e) {
        setTenantCheckStatus('IDLE');
        setTenantCheckMessage('Could not verify tenant uniqueness right now.');
      }
    }, 400);

    return () => clearTimeout(timer);
  }, [tenantName]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (tenantCheckStatus === 'TAKEN' || tenantCheckStatus === 'PENDING') {
      setError('Please choose an available tenant name before submitting.');
      return;
    }

    setError(null);
    setLoading(true);

    try {
      const payload: CreateCredentialRequestDto = {
        fullName: fullName.trim(),
        email: email.trim(),
        username: username.trim(),
        tenantName: tenantName.trim().toLowerCase(),
        department: department.trim(),
        reason: reason.trim(),
      };

      const res = await apiRequest<CredentialRequestResponse>('/credential-requests', {
        method: 'POST',
        skipAuth: true,
        body: JSON.stringify(payload),
      });

      setSubmittedRequest(res);
      showToast({
        type: 'success',
        message: `Credential request for tenant "${res.tenantName}" submitted! Verification email dispatched.`,
      });
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        setError(err.problem.detail || err.problem.title || 'Failed to submit request');
      } else if (err instanceof Error) {
        setError(err.message);
      } else {
        setError('An unexpected error occurred submitting the credential request.');
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-cream text-brown flex flex-col justify-between">
      {/* Top Banner */}
      <header className="border-b border-sand bg-white/70 backdrop-blur-md px-6 py-4 flex items-center justify-between sticky top-0 z-20">
        <div className="flex items-center gap-3">
          <Link
            to="/login"
            className="flex items-center gap-1.5 text-xs font-semibold text-brown/70 hover:text-terracotta transition-colors px-2.5 py-1.5 rounded-lg hover:bg-sand/20"
          >
            <ArrowLeft className="w-4 h-4" />
            Back to Login
          </Link>
          <div className="h-4 w-[1px] bg-sand" />
          <div className="flex items-center gap-2">
            <div className="w-7 h-7 rounded-lg bg-terracotta flex items-center justify-center text-cream">
              <Shield className="w-4 h-4 fill-current" />
            </div>
            <span className="font-fraunces font-extrabold text-base tracking-wide text-brown">
              QUANTUM<span className="font-light text-terracotta ml-1">LOCK</span>
            </span>
          </div>
        </div>

        <Button
          variant="secondary"
          size="sm"
          onClick={() => setIsInboxOpen(true)}
          className="text-xs"
        >
          <Mail className="w-3.5 h-3.5 mr-1.5 text-terracotta" />
          Notification Outbox
        </Button>
      </header>

      {/* Main Form or Success Container */}
      <main className="flex-1 max-w-3xl w-full mx-auto px-4 py-8 sm:py-12">
        {submittedRequest ? (
          /* Success Screen */
          <Card className="p-8 sm:p-10 border-2 border-olive/30 shadow-warm-lg animate-in fade-in duration-300 space-y-6 text-center">
            <div className="w-16 h-16 rounded-full bg-olive/15 text-olive flex items-center justify-center mx-auto shadow-inner">
              <CheckCircle2 className="w-8 h-8" />
            </div>

            <div className="space-y-2">
              <h2 className="text-2xl sm:text-3xl font-extrabold font-fraunces text-brown">
                Access Request Queued for Review
              </h2>
              <p className="text-sm text-brown/70 max-w-lg mx-auto leading-relaxed">
                Your credential request for isolated tenant{' '}
                <span className="font-mono font-bold text-terracotta bg-sand/30 px-2 py-0.5 rounded">
                  {submittedRequest.tenantName}
                </span>{' '}
                has been submitted to the security operations team.
              </p>
            </div>

            <div className="bg-sand/15 border border-sand rounded-xl p-5 text-left text-xs font-mono space-y-2 max-w-md mx-auto">
              <div className="flex justify-between">
                <span className="text-brown/50">Request ID:</span>
                <span className="text-brown font-semibold">{submittedRequest.id}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-brown/50">Requester:</span>
                <span className="text-brown">{submittedRequest.fullName}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-brown/50">Email:</span>
                <span className="text-terracotta">{submittedRequest.email}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-brown/50">Status:</span>
                <span className="text-amber font-bold">PENDING APPROVAL</span>
              </div>
            </div>

            <div className="p-4 bg-amber/10 border border-amber/30 rounded-xl text-xs text-brown/80 max-w-lg mx-auto text-left flex items-start gap-3">
              <Clock className="w-4 h-4 text-amber shrink-0 mt-0.5" />
              <div>
                <strong>Next Step:</strong> An administrator will receive an email to review and verify your requested tenant. Once approved, you will receive an official approval email with your generated administrative password and direct tenant login link.
              </div>
            </div>

            <div className="flex flex-col sm:flex-row items-center justify-center gap-3 pt-2">
              <Button
                variant="primary"
                onClick={() => navigate('/login')}
                className="w-full sm:w-auto"
              >
                Return to Login
              </Button>
            </div>
          </Card>
        ) : (
          /* Request Form */
          <Card className="p-8 sm:p-10 shadow-warm-lg space-y-6">
            <div>
              <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-bold bg-terracotta/15 text-terracotta font-mono uppercase tracking-wider mb-2">
                <Layers className="w-3.5 h-3.5" />
                Isolated Workspace Onboarding
              </div>
              <h1 className="text-2xl sm:text-3xl font-extrabold font-fraunces text-brown tracking-tight">
                Request Isolated Tenant Credentials
              </h1>
              <p className="text-sm text-brown/70 mt-1">
                Establish an independent zero-trust cryptographic workspace with dedicated KMS keys, MinIO S3 partitions, and blind indexes.
              </p>
            </div>

            {error && (
              <div className="p-4 rounded-xl bg-brick/10 border border-brick/30 text-brick text-xs flex items-start gap-2.5">
                <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" />
                <span>{error}</span>
              </div>
            )}

            <form onSubmit={handleSubmit} className="space-y-5">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                {/* Full Name */}
                <div className="space-y-1.5">
                  <label className="block text-xs font-semibold text-brown uppercase font-mono tracking-wider">
                    Full Name <span className="text-terracotta">*</span>
                  </label>
                  <div className="relative">
                    <User className="w-4 h-4 text-brown/40 absolute left-3 top-3" />
                    <input
                      type="text"
                      required
                      placeholder="Jane Doe"
                      value={fullName}
                      onChange={(e) => setFullName(e.target.value)}
                      className="w-full pl-9 pr-3 py-2.5 rounded-xl border border-sand bg-cream/30 text-sm focus:outline-none focus:ring-2 focus:ring-terracotta text-brown"
                    />
                  </div>
                </div>

                {/* Email Address */}
                <div className="space-y-1.5">
                  <label className="block text-xs font-semibold text-brown uppercase font-mono tracking-wider">
                    Corporate Email <span className="text-terracotta">*</span>
                  </label>
                  <div className="relative">
                    <Mail className="w-4 h-4 text-brown/40 absolute left-3 top-3" />
                    <input
                      type="email"
                      required
                      placeholder="jane.doe@example.com"
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
                      className="w-full pl-9 pr-3 py-2.5 rounded-xl border border-sand bg-cream/30 text-sm focus:outline-none focus:ring-2 focus:ring-terracotta text-brown"
                    />
                  </div>
                </div>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                {/* Admin Username */}
                <div className="space-y-1.5">
                  <label className="block text-xs font-semibold text-brown uppercase font-mono tracking-wider">
                    Requested Admin Username <span className="text-terracotta">*</span>
                  </label>
                  <input
                    type="text"
                    required
                    placeholder="e.g. jdoe_admin"
                    value={username}
                    onChange={(e) => setUsername(e.target.value)}
                    className="w-full px-3 py-2.5 rounded-xl border border-sand bg-cream/30 text-sm font-mono focus:outline-none focus:ring-2 focus:ring-terracotta text-brown"
                  />
                  <p className="text-[11px] text-brown/50">This user will be assigned ADMIN role in the new tenant.</p>
                </div>

                {/* Department */}
                <div className="space-y-1.5">
                  <label className="block text-xs font-semibold text-brown uppercase font-mono tracking-wider">
                    Department
                  </label>
                  <select
                    value={department}
                    onChange={(e) => setDepartment(e.target.value)}
                    className="w-full px-3 py-2.5 rounded-xl border border-sand bg-cream/30 text-sm focus:outline-none focus:ring-2 focus:ring-terracotta text-brown"
                  >
                    <option value="Security Operations">Security Operations</option>
                    <option value="Engineering & Development">Engineering & Development</option>
                    <option value="Finance & Auditing">Finance & Auditing</option>
                    <option value="Legal & Compliance">Legal & Compliance</option>
                    <option value="Research & Analytics">Research & Analytics</option>
                  </select>
                </div>
              </div>

              {/* Desired Tenant Name with Live Redundancy / Duplicate Checker */}
              <div className="space-y-2 p-4 rounded-xl border-2 border-sand bg-white/50">
                <div className="flex items-center justify-between">
                  <label className="block text-xs font-bold text-brown uppercase font-mono tracking-wider">
                    Desired Tenant Name <span className="text-terracotta">*</span>
                  </label>
                  <span className="text-[11px] font-mono text-brown/50">Lower-case, numbers, hyphens</span>
                </div>

                <div className="relative">
                  <Building className="w-4 h-4 text-brown/40 absolute left-3 top-3" />
                  <input
                    type="text"
                    required
                    placeholder="e.g. acme-defense, quantum-research"
                    value={tenantName}
                    onChange={(e) => setTenantName(e.target.value.toLowerCase().replace(/[^a-z0-9-]/g, ''))}
                    className="w-full pl-9 pr-3 py-2.5 rounded-xl border border-sand bg-white text-sm font-mono font-bold focus:outline-none focus:ring-2 focus:ring-terracotta text-brown"
                  />
                </div>

                {/* Live Duplicate Feedback Banner */}
                {tenantCheckStatus !== 'IDLE' && (
                  <div
                    className={`text-xs p-2.5 rounded-lg font-mono flex items-center gap-2 ${
                      tenantCheckStatus === 'AVAILABLE'
                        ? 'bg-olive/15 text-olive border border-olive/30 font-semibold'
                        : tenantCheckStatus === 'TAKEN'
                        ? 'bg-brick/15 text-brick border border-brick/30 font-bold'
                        : tenantCheckStatus === 'PENDING'
                        ? 'bg-amber/15 text-amber border border-amber/30 font-semibold'
                        : 'bg-sand/20 text-brown/60'
                    }`}
                  >
                    {tenantCheckStatus === 'CHECKING' && (
                      <span className="w-3 h-3 rounded-full border-2 border-brown/40 border-t-transparent animate-spin" />
                    )}
                    {tenantCheckStatus === 'AVAILABLE' && <CheckCircle2 className="w-4 h-4 text-olive" />}
                    {tenantCheckStatus === 'TAKEN' && <AlertCircle className="w-4 h-4 text-brick" />}
                    {tenantCheckStatus === 'PENDING' && <Clock className="w-4 h-4 text-amber" />}
                    <span>{tenantCheckMessage}</span>
                  </div>
                )}

                <p className="text-[11px] text-brown/60 leading-relaxed">
                  Tenant names are globally unique in QUANTUM-LOCK. Redundant or duplicated names are prevented to ensure cryptographic isolation across S3 prefixes and AAD signatures.
                </p>
              </div>

              {/* Reason / Purpose */}
              <div className="space-y-1.5">
                <label className="block text-xs font-semibold text-brown uppercase font-mono tracking-wider">
                  Business Justification / Purpose <span className="text-terracotta">*</span>
                </label>
                <textarea
                  required
                  rows={3}
                  placeholder="Describe your security requirements and what documents will be envelope-encrypted in this tenant..."
                  value={reason}
                  onChange={(e) => setReason(e.target.value)}
                  className="w-full px-3 py-2.5 rounded-xl border border-sand bg-cream/30 text-sm focus:outline-none focus:ring-2 focus:ring-terracotta text-brown"
                />
              </div>

              {/* Submit Button */}
              <div className="pt-2 flex flex-col sm:flex-row items-center justify-between gap-4">
                <Link
                  to="/login"
                  className="text-xs text-brown/60 hover:text-brown transition-colors"
                >
                  Cancel and return to login
                </Link>

                <Button
                  type="submit"
                  variant="primary"
                  size="lg"
                  isLoading={loading}
                  disabled={tenantCheckStatus === 'TAKEN' || tenantCheckStatus === 'PENDING' || tenantCheckStatus === 'CHECKING'}
                  className="w-full sm:w-auto"
                >
                  <Send className="w-4 h-4 mr-2" />
                  Submit Credential Request
                </Button>
              </div>
            </form>
          </Card>
        )}
      </main>

      {/* Footer */}
      <footer className="border-t border-sand bg-white/40 py-4 px-6 text-center text-xs text-brown/50 font-mono">
        QUANTUM-LOCK Zero-Trust Verification Engine • Multi-Tenant Partitioning
      </footer>

      {/* Email Inbox Preview Modal */}
      <EmailInboxModal
        isOpen={isInboxOpen}
        onClose={() => setIsInboxOpen(false)}
        defaultEmailId={submittedRequest?.id}
      />
    </div>
  );
};

