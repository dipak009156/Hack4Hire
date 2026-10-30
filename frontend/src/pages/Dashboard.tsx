import React from 'react';
import { useQuery } from '@tanstack/react-query';
import { apiRequest } from '../api/client';
import { DocumentResponse, AccessGrantResponse, AuditVerificationResult } from '../api/types';
import { Card } from '../components/common/Card';
import { Button } from '../components/common/Button';
import { Skeleton } from '../components/common/Skeleton';
import {
  FileText,
  Share2,
  KeyRound,
  ShieldCheck,
  ShieldAlert,
  Activity,
  Server,
  Database,
  Radio,
  Lock,
  ArrowUpRight,
} from 'lucide-react';
import { Link } from 'react-router-dom';

export const Dashboard: React.FC = () => {
  // 1. Fetch documents count
  const { data: documents, isLoading: loadingDocs } = useQuery({
    queryKey: ['documents'],
    queryFn: () => apiRequest<DocumentResponse[]>('/documents'),
  });

  // 2. Fetch grants
  const { data: shares, isLoading: loadingShares } = useQuery({
    queryKey: ['shares'],
    queryFn: () => apiRequest<AccessGrantResponse[]>('/shares'),
  });

  // 3. Fetch KEK version
  const { data: kekData, isLoading: loadingKek } = useQuery({
    queryKey: ['kek-version'],
    queryFn: () => apiRequest<{ currentKekVersion: number }>('/rotation/current-kek'),
  });

  // 4. Check audit verification
  const { data: auditResult, isLoading: loadingAudit } = useQuery({
    queryKey: ['audit-verify'],
    queryFn: () => apiRequest<AuditVerificationResult>('/audit/verify', { method: 'POST' }),
  });

  const activeGrantsCount = shares?.filter((s) => s.active).length ?? 0;

  return (
    <div className="space-y-8 animate-in fade-in duration-200">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-3xl font-extrabold font-fraunces text-brown tracking-tight">
            Security Overview
          </h1>
          <p className="text-sm text-brown/70 mt-1">
            Real-time status of cryptographic primitives, tenant storage, and audit integrity.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <Link to="/documents">
            <Button variant="primary" size="sm">
              <FileText className="w-4 h-4 mr-1.5" />
              Manage Documents
            </Button>
          </Link>
          <Link to="/security-proof">
            <Button variant="secondary" size="sm">
              <ShieldCheck className="w-4 h-4 mr-1.5" />
              Security Proof
            </Button>
          </Link>
        </div>
      </div>

      {/* System Health Pills */}
      <div className="flex flex-wrap items-center gap-3">
        <span className="text-xs font-semibold uppercase tracking-wider text-brown/50 font-mono">
          Services:
        </span>
        <div className="flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-medium bg-olive/10 text-olive border border-olive/30 shadow-sm">
          <Database className="w-3.5 h-3.5" />
          <span>PostgreSQL (Enforced Multi-tenant)</span>
        </div>
        <div className="flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-medium bg-olive/10 text-olive border border-olive/30 shadow-sm">
          <Server className="w-3.5 h-3.5" />
          <span>MinIO (Tenant Partitioned S3)</span>
        </div>
        <div className="flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-medium bg-olive/10 text-olive border border-olive/30 shadow-sm">
          <Lock className="w-3.5 h-3.5" />
          <span>KMS (AESWrap RFC 3394)</span>
        </div>
        <div className="flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-medium bg-olive/10 text-olive border border-olive/30 shadow-sm">
          <Radio className="w-3.5 h-3.5" />
          <span>RabbitMQ (Transactional Outbox)</span>
        </div>
      </div>

      {/* 4 Primary Stat Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5">
        {/* Stat 1: Documents */}
        <Card hoverLift className="relative overflow-hidden">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-brown/60 uppercase tracking-wider font-mono">
              Encrypted Documents
            </span>
            <div className="w-9 h-9 rounded-xl bg-terracotta/10 text-terracotta flex items-center justify-center">
              <FileText className="w-5 h-5" />
            </div>
          </div>
          <div className="mt-4">
            {loadingDocs ? (
              <Skeleton className="h-8 w-16" />
            ) : (
              <div className="text-3xl font-bold font-fraunces text-brown">
                {documents?.length ?? 0}
              </div>
            )}
            <p className="text-xs text-brown/60 mt-1 flex items-center gap-1">
              <span>Tenant scoped</span>
              <Link to="/documents" className="text-terracotta hover:underline inline-flex items-center">
                <span>View</span>
                <ArrowUpRight className="w-3 h-3" />
              </Link>
            </p>
          </div>
        </Card>

        {/* Stat 2: Active Grants */}
        <Card hoverLift className="relative overflow-hidden">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-brown/60 uppercase tracking-wider font-mono">
              Active Access Grants
            </span>
            <div className="w-9 h-9 rounded-xl bg-amber/15 text-amber flex items-center justify-center">
              <Share2 className="w-5 h-5" />
            </div>
          </div>
          <div className="mt-4">
            {loadingShares ? (
              <Skeleton className="h-8 w-16" />
            ) : (
              <div className="text-3xl font-bold font-fraunces text-brown">
                {activeGrantsCount}
              </div>
            )}
            <p className="text-xs text-brown/60 mt-1 flex items-center gap-1">
              <span>Time & quota limited</span>
              <Link to="/sharing" className="text-terracotta hover:underline inline-flex items-center">
                <span>Grants</span>
                <ArrowUpRight className="w-3 h-3" />
              </Link>
            </p>
          </div>
        </Card>

        {/* Stat 3: Current KEK Version */}
        <Card hoverLift className="relative overflow-hidden">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-brown/60 uppercase tracking-wider font-mono">
              Active KEK Version
            </span>
            <div className="w-9 h-9 rounded-xl bg-sand/50 text-brown flex items-center justify-center">
              <KeyRound className="w-5 h-5" />
            </div>
          </div>
          <div className="mt-4">
            {loadingKek ? (
              <Skeleton className="h-8 w-16" />
            ) : (
              <div className="text-3xl font-bold font-fraunces text-brown">
                v{kekData?.currentKekVersion ?? 1}
              </div>
            )}
            <p className="text-xs text-brown/60 mt-1 flex items-center gap-1">
              <span>AESWrap (256-bit)</span>
              <Link to="/key-management" className="text-terracotta hover:underline inline-flex items-center">
                <span>Rotate</span>
                <ArrowUpRight className="w-3 h-3" />
              </Link>
            </p>
          </div>
        </Card>

        {/* Stat 4: Audit Verification */}
        <Card hoverLift className="relative overflow-hidden">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-brown/60 uppercase tracking-wider font-mono">
              Audit Chain Integrity
            </span>
            <div
              className={`w-9 h-9 rounded-xl flex items-center justify-center ${
                auditResult?.valid
                  ? 'bg-olive/15 text-olive'
                  : 'bg-brick/15 text-brick'
              }`}
            >
              {auditResult?.valid ? (
                <ShieldCheck className="w-5 h-5" />
              ) : (
                <ShieldAlert className="w-5 h-5" />
              )}
            </div>
          </div>
          <div className="mt-4">
            {loadingAudit ? (
              <Skeleton className="h-8 w-24" />
            ) : (
              <div
                className={`text-2xl font-bold font-fraunces ${
                  auditResult?.valid ? 'text-olive' : 'text-brick'
                }`}
              >
                {auditResult?.valid ? 'VERIFIED' : 'TAMPERED'}
              </div>
            )}
            <p className="text-xs text-brown/60 mt-1 flex items-center gap-1">
              <span>{auditResult?.totalEvents ?? 0} events verified</span>
              <Link to="/audit-log" className="text-terracotta hover:underline inline-flex items-center">
                <span>Logs</span>
                <ArrowUpRight className="w-3 h-3" />
              </Link>
            </p>
          </div>
        </Card>
      </div>

      {/* Recent Activity & Core Assurances */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Recent Documents Table */}
        <div className="lg:col-span-2 space-y-4">
          <div className="flex items-center justify-between">
            <h3 className="text-lg font-bold font-fraunces text-brown">
              Recent Documents
            </h3>
            <Link to="/documents" className="text-xs text-terracotta hover:underline font-medium">
              View all
            </Link>
          </div>

          <Card className="p-0 overflow-hidden">
            {loadingDocs ? (
              <div className="p-6 space-y-3">
                <Skeleton className="h-6 w-full" />
                <Skeleton className="h-6 w-full" />
                <Skeleton className="h-6 w-full" />
              </div>
            ) : documents && documents.length > 0 ? (
              <div className="divide-y divide-sand">
                {documents.slice(0, 5).map((doc) => (
                  <div
                    key={doc.id}
                    className="p-4 flex items-center justify-between hover:bg-sand/10 transition-colors"
                  >
                    <div className="flex items-center gap-3">
                      <div className="w-9 h-9 rounded-lg bg-sand/30 flex items-center justify-center text-brown">
                        <FileText className="w-4 h-4" />
                      </div>
                      <div>
                        <div className="text-sm font-semibold text-brown">{doc.name}</div>
                        <div className="text-xs text-brown/50 font-mono">
                          v{doc.currentVersion} • {new Date(doc.createdAt).toLocaleDateString()}
                        </div>
                      </div>
                    </div>

                    <span className="px-2 py-0.5 rounded text-xs font-medium bg-sand/30 text-brown border border-sand">
                      {doc.classification}
                    </span>
                  </div>
                ))}
              </div>
            ) : (
              <div className="p-8 text-center text-brown/60 text-sm">
                No documents uploaded in this tenant yet.
              </div>
            )}
          </Card>
        </div>

        {/* Security Invariants Feed */}
        <div className="space-y-4">
          <h3 className="text-lg font-bold font-fraunces text-brown flex items-center gap-2">
            <Activity className="w-4 h-4 text-terracotta" />
            <span>Guarantees Enforced</span>
          </h3>

          <Card className="space-y-4">
            <div className="space-y-1.5 pb-3 border-b border-sand">
              <div className="flex items-center justify-between text-xs font-semibold text-brown">
                <span>I1: Envelope Encryption</span>
                <span className="text-olive">ENFORCED</span>
              </div>
              <p className="text-xs text-brown/70 leading-relaxed">
                Ciphertext bound to AAD (tenant|docId|version). DEKs wrapped via AESWrap.
              </p>
            </div>

            <div className="space-y-1.5 pb-3 border-b border-sand">
              <div className="flex items-center justify-between text-xs font-semibold text-brown">
                <span>I2: In-Memory Isolation</span>
                <span className="text-olive">ENFORCED</span>
              </div>
              <p className="text-xs text-brown/70 leading-relaxed">
                Zero plaintext keys escape RAM. Plaintext DEK bytes are zeroized immediately.
              </p>
            </div>

            <div className="space-y-1.5 pb-3 border-b border-sand">
              <div className="flex items-center justify-between text-xs font-semibold text-brown">
                <span>I4: Idempotent Execution</span>
                <span className="text-olive">ENFORCED</span>
              </div>
              <p className="text-xs text-brown/70 leading-relaxed">
                Mandatory Idempotency-Key header on upload, share, and rotate operations.
              </p>
            </div>

            <div className="space-y-1.5">
              <div className="flex items-center justify-between text-xs font-semibold text-brown">
                <span>I6: Tamper-Evident Audit</span>
                <span className="text-olive">ENFORCED</span>
              </div>
              <p className="text-xs text-brown/70 leading-relaxed">
                Pessimistic serial lock on tenant ensures collision-free SHA-256 chain verification.
              </p>
            </div>
          </Card>
        </div>
      </div>
    </div>
  );
};

