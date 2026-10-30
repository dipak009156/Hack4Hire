import React, { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { apiRequest } from '../api/client';
import { ReconcileResult } from '../api/types';
import { Card } from '../components/common/Card';
import { Button } from '../components/common/Button';
import {
  RotateCcw,
  CheckCircle2,
  Trash2,
  AlertTriangle,
  Layers,
  ArrowRight,
  ShieldCheck,
  Server,
  Database,
} from 'lucide-react';

export const Recovery: React.FC = () => {
  const [lastResult, setLastResult] = useState<ReconcileResult | null>(null);

  const reconcileMutation = useMutation({
    mutationFn: async () => {
      return apiRequest<ReconcileResult>('/admin/reconcile', { method: 'POST' });
    },
    onSuccess: (data) => {
      setLastResult(data);
    },
  });

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-3xl font-extrabold font-fraunces text-brown tracking-tight">
            Recovery & Orphan Reconciliation
          </h1>
          <p className="text-sm text-brown/70 mt-1">
            Compare-and-set reconciliation for incomplete or abandoned uploads (ADR-004 / Invariant I1).
          </p>
        </div>

        <Button
          onClick={() => reconcileMutation.mutate()}
          variant="primary"
          size="md"
          isLoading={reconcileMutation.isPending}
        >
          <RotateCcw className="w-4 h-4 mr-2" />
          Reconcile Stale Uploads
        </Button>
      </div>

      {/* Result Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5">
        <Card className="p-5">
          <div className="flex items-center justify-between text-xs font-semibold text-brown/60 uppercase font-mono">
            <span>Stale Records Found</span>
            <Layers className="w-4 h-4 text-terracotta" />
          </div>
          <div className="text-3xl font-bold font-fraunces text-brown mt-3">
            {lastResult ? lastResult.staleRecordsFound : 0}
          </div>
          <div className="text-xs text-brown/60 mt-1">&gt; 5 min in PENDING / STORED</div>
        </Card>

        <Card className="p-5">
          <div className="flex items-center justify-between text-xs font-semibold text-brown/60 uppercase font-mono">
            <span>Recovered & Committed</span>
            <CheckCircle2 className="w-4 h-4 text-olive" />
          </div>
          <div className="text-3xl font-bold font-fraunces text-olive mt-3">
            {lastResult ? lastResult.recovered : 0}
          </div>
          <div className="text-xs text-brown/60 mt-1">Ciphertext digest verified</div>
        </Card>

        <Card className="p-5">
          <div className="flex items-center justify-between text-xs font-semibold text-brown/60 uppercase font-mono">
            <span>Marked FAILED</span>
            <AlertTriangle className="w-4 h-4 text-brick" />
          </div>
          <div className="text-3xl font-bold font-fraunces text-brick mt-3">
            {lastResult ? lastResult.markedFailed : 0}
          </div>
          <div className="text-xs text-brown/60 mt-1">Corrupted / missing payload</div>
        </Card>

        <Card className="p-5">
          <div className="flex items-center justify-between text-xs font-semibold text-brown/60 uppercase font-mono">
            <span>Orphans Deleted</span>
            <Trash2 className="w-4 h-4 text-amber" />
          </div>
          <div className="text-3xl font-bold font-fraunces text-amber mt-3">
            {lastResult ? lastResult.objectsDeleted : 0}
          </div>
          <div className="text-xs text-brown/60 mt-1">MinIO objects purged</div>
        </Card>
      </div>

      {/* Operation State Machine Timeline */}
      <Card className="p-6 space-y-6">
        <div>
          <h3 className="text-lg font-bold font-fraunces text-brown">
            Two-Phase Upload & Recovery State Machine
          </h3>
          <p className="text-xs text-brown/70 mt-1">
            Compare-and-set transitions ensure concurrent in-flight uploads are never disrupted.
          </p>
        </div>

        {/* State Flow Diagram */}
        <div className="grid grid-cols-1 md:grid-cols-4 gap-4 relative">
          {/* Step 1: PENDING */}
          <div className="p-4 rounded-xl border border-sand bg-white/60 space-y-2">
            <div className="flex items-center justify-between">
              <span className="font-mono font-bold text-xs text-amber">1. PENDING</span>
              <Database className="w-4 h-4 text-amber" />
            </div>
            <div className="text-xs font-semibold text-brown">Database Record Created</div>
            <p className="text-[11px] text-brown/70 leading-relaxed">
              Row inserted with wrapped DEK, nonce, and ciphertext digest before object store write.
            </p>
          </div>

          {/* Step 2: OBJECT_STORED */}
          <div className="p-4 rounded-xl border border-sand bg-white/60 space-y-2">
            <div className="flex items-center justify-between">
              <span className="font-mono font-bold text-xs text-brown">2. OBJECT_STORED</span>
              <Server className="w-4 h-4 text-sand" />
            </div>
            <div className="text-xs font-semibold text-brown">MinIO Put Succeeded</div>
            <p className="text-[11px] text-brown/70 leading-relaxed">
              Ciphertext payload securely written to tenant bucket. Marked immediately after put.
            </p>
          </div>

          {/* Step 3: RECOVERING */}
          <div className="p-4 rounded-xl border-2 border-amber/60 bg-amber/5 space-y-2">
            <div className="flex items-center justify-between">
              <span className="font-mono font-bold text-xs text-amber">3. RECOVERING</span>
              <RotateCcw className="w-4 h-4 text-amber" />
            </div>
            <div className="text-xs font-semibold text-brown">Compare-and-Set Claim</div>
            <p className="text-[11px] text-brown/70 leading-relaxed">
              Reconciler atomically claims stale row to avoid racing with active in-flight commits.
            </p>
          </div>

          {/* Step 4: COMMITTED / FAILED */}
          <div className="p-4 rounded-xl border border-sand bg-white/60 space-y-2">
            <div className="flex items-center justify-between">
              <span className="font-mono font-bold text-xs text-olive">4. COMMITTED</span>
              <ShieldCheck className="w-4 h-4 text-olive" />
            </div>
            <div className="text-xs font-semibold text-brown">Integrity Verified</div>
            <p className="text-[11px] text-brown/70 leading-relaxed">
              If SHA-256 digest matches MinIO bytes, version commits; otherwise marked FAILED & purged.
            </p>
          </div>
        </div>

        <div className="p-4 rounded-xl bg-sand/30 border border-sand text-xs text-brown/80 space-y-1 font-mono">
          <div>• Stale threshold: 5 minutes</div>
          <div>• Execution boundary: Strict per-tenant isolation (reconciler never crosses tenant boundaries)</div>
          <div>• Audit log: Every recovery and orphan deletion emits a cryptographically chained audit event</div>
        </div>
      </Card>
    </div>
  );
};

