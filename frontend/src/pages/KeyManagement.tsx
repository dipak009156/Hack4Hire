import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiRequest, ApiError } from '../api/client';
import { KeyRotationResponse } from '../api/types';
import { Card } from '../components/common/Card';
import { Button } from '../components/common/Button';
import { Dialog } from '../components/common/Dialog';
import { Skeleton } from '../components/common/Skeleton';
import { useAuth } from '../context/AuthContext';
import { generateIdempotencyKey } from '../utils/idempotency';
import {
  KeyRound,
  RotateCw,
  ShieldCheck,
  Clock,
  AlertCircle,
  CheckCircle2,
  Lock,
} from 'lucide-react';

export const KeyManagement: React.FC = () => {
  const queryClient = useQueryClient();
  const { hasRole } = useAuth();
  const isAdmin = hasRole('ADMIN');

  const [isRotateModalOpen, setIsRotateModalOpen] = useState(false);
  const [targetKekVersion, setTargetKekVersion] = useState(2);
  const [activeJobId, setActiveJobId] = useState<string | null>(null);
  const [rotateError, setRotateError] = useState<string | null>(null);
  const [idempotencyKey, setIdempotencyKey] = useState(generateIdempotencyKey('rotate-kek'));

  // 1. Fetch current KEK version
  const { data: currentKek } = useQuery({
    queryKey: ['current-kek'],
    queryFn: () => apiRequest<{ currentKekVersion: number }>('/rotation/current-kek'),
  });

  // 2. Fetch rotation history
  const { data: history, isLoading } = useQuery({
    queryKey: ['rotation-history'],
    queryFn: () => apiRequest<KeyRotationResponse[]>('/rotation/history'),
    enabled: isAdmin,
  });

  // 3. Poll active job status if running
  const { data: activeJob } = useQuery({
    queryKey: ['rotation-status', activeJobId],
    queryFn: () => apiRequest<KeyRotationResponse>(`/rotation/${activeJobId}/status`),
    enabled: !!activeJobId,
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      return status === 'IN_PROGRESS' || status === 'PENDING' ? 1000 : false;
    },
  });

  // Start Rotation Mutation
  const rotateMutation = useMutation({
    mutationFn: async () => {
      const source = currentKek?.currentKekVersion || 1;
      return apiRequest<KeyRotationResponse>('/rotation/start', {
        method: 'POST',
        idempotencyKey,
        body: JSON.stringify({
          sourceKekVersion: source,
          targetKekVersion: Number(targetKekVersion),
        }),
      });
    },
    onSuccess: (data) => {
      setActiveJobId(data.id);
      setIsRotateModalOpen(false);
      queryClient.invalidateQueries({ queryKey: ['rotation-history'] });
      queryClient.invalidateQueries({ queryKey: ['current-kek'] });
      setIdempotencyKey(generateIdempotencyKey('rotate-kek'));
    },
    onError: (err: unknown) => {
      if (err instanceof ApiError) {
        setRotateError(err.problem.detail || err.problem.title || 'Rotation failed');
      } else if (err instanceof Error) {
        setRotateError(err.message);
      } else {
        setRotateError('Failed to trigger key rotation');
      }
    },
  });

  const activeOrLatestJob = activeJob || history?.[0];
  const progressPercent = activeOrLatestJob && activeOrLatestJob.totalDocuments > 0
    ? Math.round((activeOrLatestJob.processedDocuments / activeOrLatestJob.totalDocuments) * 100)
    : activeOrLatestJob?.status === 'COMPLETED' ? 100 : 0;

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-3xl font-extrabold font-fraunces text-brown tracking-tight">
            Key Management & Rotation
          </h1>
          <p className="text-sm text-brown/70 mt-1">
            AESWrap (RFC 3394) envelope key re-wrapping without decrypting document ciphertext.
          </p>
        </div>

        {isAdmin ? (
          <Button
            onClick={() => {
              setRotateError(null);
              setTargetKekVersion((currentKek?.currentKekVersion || 1) + 1);
              setIdempotencyKey(generateIdempotencyKey('rotate-kek'));
              setIsRotateModalOpen(true);
            }}
            variant="primary"
            size="md"
          >
            <RotateCw className="w-4 h-4 mr-2" />
            Trigger KEK Rotation
          </Button>
        ) : (
          <div className="text-xs text-brown/60 px-3 py-1.5 rounded-lg bg-sand/30 font-mono">
            Role: Non-Admin (View Only)
          </div>
        )}
      </div>

      {/* Top Status Cards */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-5">
        <Card className="flex items-center gap-4">
          <div className="w-12 h-12 rounded-xl bg-terracotta/10 text-terracotta flex items-center justify-center shrink-0">
            <KeyRound className="w-6 h-6" />
          </div>
          <div>
            <div className="text-xs font-semibold text-brown/60 uppercase font-mono">Current KEK Version</div>
            <div className="text-3xl font-bold font-fraunces text-brown mt-0.5">
              v{currentKek?.currentKekVersion ?? 1}
            </div>
            <div className="text-xs text-olive font-medium mt-1">Active for new uploads</div>
          </div>
        </Card>

        <Card className="flex items-center gap-4">
          <div className="w-12 h-12 rounded-xl bg-olive/10 text-olive flex items-center justify-center shrink-0">
            <Lock className="w-6 h-6" />
          </div>
          <div>
            <div className="text-xs font-semibold text-brown/60 uppercase font-mono">Algorithm Spec</div>
            <div className="text-lg font-bold font-fraunces text-brown mt-0.5">AESWrap / 256-bit</div>
            <div className="text-xs text-brown/60 mt-1">JCA Standard • RFC 3394</div>
          </div>
        </Card>

        <Card className="flex items-center gap-4">
          <div className="w-12 h-12 rounded-xl bg-amber/15 text-amber flex items-center justify-center shrink-0">
            <ShieldCheck className="w-6 h-6" />
          </div>
          <div>
            <div className="text-xs font-semibold text-brown/60 uppercase font-mono">Zero-Knowledge Re-wrap</div>
            <div className="text-sm font-semibold text-brown mt-1">Ciphertext Untouched</div>
            <div className="text-xs text-brown/60 mt-0.5">Plaintext DEKs zeroed in memory</div>
          </div>
        </Card>
      </div>

      {/* Active / Recent Job Progress Bar */}
      {activeOrLatestJob && (
        <Card className="p-6 space-y-4 bg-white/95">
          <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2 pb-3 border-b border-sand">
            <div className="flex items-center gap-2.5">
              <RotateCw className={`w-5 h-5 text-terracotta ${activeOrLatestJob.status === 'IN_PROGRESS' ? 'animate-spin' : ''}`} />
              <div>
                <h3 className="text-base font-bold font-fraunces text-brown">
                  Rotation Job: v{activeOrLatestJob.sourceKekVersion} → v{activeOrLatestJob.targetKekVersion}
                </h3>
                <span className="text-xs text-brown/60 font-mono">Job ID: {activeOrLatestJob.id}</span>
              </div>
            </div>

            <span
              className={`px-3 py-1 rounded-full text-xs font-bold uppercase tracking-wider ${
                activeOrLatestJob.status === 'COMPLETED'
                  ? 'bg-olive/15 text-olive border border-olive/30'
                  : activeOrLatestJob.status === 'IN_PROGRESS'
                  ? 'bg-amber/15 text-amber border border-amber/30 animate-pulse'
                  : activeOrLatestJob.status === 'FAILED'
                  ? 'bg-brick/15 text-brick border border-brick/30'
                  : 'bg-sand/30 text-brown'
              }`}
            >
              {activeOrLatestJob.status}
            </span>
          </div>

          {/* Progress Bar */}
          <div className="space-y-2">
            <div className="flex justify-between text-xs font-semibold font-mono text-brown">
              <span>Progress</span>
              <span>{activeOrLatestJob.processedDocuments} / {activeOrLatestJob.totalDocuments} documents ({progressPercent}%)</span>
            </div>
            <div className="w-full h-3 rounded-full bg-sand/30 overflow-hidden">
              <div
                className="h-full bg-terracotta rounded-full transition-all duration-300"
                style={{ width: `${progressPercent}%` }}
              />
            </div>
          </div>

          {activeOrLatestJob.errorMessage && (
            <div className="p-3 rounded-lg bg-brick/10 border border-brick/30 text-brick text-xs flex items-center gap-2">
              <AlertCircle className="w-4 h-4 shrink-0" />
              <span>{activeOrLatestJob.errorMessage}</span>
            </div>
          )}
        </Card>
      )}

      {/* Rotation History Table */}
      <Card className="p-0 overflow-hidden">
        <div className="px-5 py-3.5 border-b border-sand bg-sand/20 flex items-center justify-between">
          <span className="text-xs font-bold uppercase tracking-wider text-brown font-mono">
            Rotation Job History
          </span>
          <span className="text-xs text-brown/50 font-mono">
            Audit Tracked
          </span>
        </div>

        {isLoading ? (
          <div className="p-6 space-y-4">
            <Skeleton className="h-8 w-full" />
            <Skeleton className="h-8 w-full" />
          </div>
        ) : history && history.length > 0 ? (
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse text-sm">
              <thead>
                <tr className="border-b border-sand text-xs font-semibold uppercase tracking-wider text-brown/70 font-mono">
                  <th className="py-3 px-4">Job ID</th>
                  <th className="py-3 px-4">Transition</th>
                  <th className="py-3 px-4">Processed</th>
                  <th className="py-3 px-4">Status</th>
                  <th className="py-3 px-4 text-right">Details</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-sand">
                {history.map((job) => (
                  <tr key={job.id} className="hover:bg-sand/10 transition-colors">
                    <td className="py-3.5 px-4 font-mono text-xs text-brown font-medium">
                      {job.id.slice(0, 13)}...
                    </td>
                    <td className="py-3.5 px-4 font-mono text-xs text-brown">
                      v{job.sourceKekVersion} → v{job.targetKekVersion}
                    </td>
                    <td className="py-3.5 px-4 font-mono text-xs text-brown">
                      {job.processedDocuments} / {job.totalDocuments}
                    </td>
                    <td className="py-3.5 px-4">
                      <span
                        className={`inline-block px-2 py-0.5 rounded text-xs font-semibold ${
                          job.status === 'COMPLETED'
                            ? 'bg-olive/15 text-olive'
                            : job.status === 'IN_PROGRESS'
                            ? 'bg-amber/15 text-amber'
                            : 'bg-brick/15 text-brick'
                        }`}
                      >
                        {job.status}
                      </span>
                    </td>
                    <td className="py-3.5 px-4 text-right">
                      <Button
                        onClick={() => setActiveJobId(job.id)}
                        variant="ghost"
                        size="sm"
                      >
                        Inspect
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <div className="p-8 text-center text-sm text-brown/60">
            No key rotation jobs executed yet for this tenant.
          </div>
        )}
      </Card>

      {/* Start Rotation Modal */}
      <Dialog
        isOpen={isRotateModalOpen}
        onClose={() => setIsRotateModalOpen(false)}
        title="Execute Key Encryption Key Rotation"
        description="Re-wraps all document DEKs from the current version to the target KEK version in batches."
      >
        <form
          onSubmit={(e) => {
            e.preventDefault();
            rotateMutation.mutate();
          }}
          className="space-y-4"
        >
          {rotateError && (
            <div className="p-3 rounded-lg bg-brick/10 border border-brick/30 text-brick text-xs flex items-center gap-2">
              <AlertCircle className="w-4 h-4 shrink-0" />
              <span>{rotateError}</span>
            </div>
          )}

          <div className="p-3 rounded-xl bg-sand/30 border border-sand text-xs text-brown/80 space-y-1">
            <div className="font-semibold text-brown">Resumable Cursor Guarantee (ADR-002):</div>
            <div>
              Document ciphertext stored in MinIO remains completely unchanged. Only the 256-bit encrypted DEKs stored in the database are re-wrapped.
            </div>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-semibold uppercase tracking-wider text-brown/80 mb-1.5 font-mono">
                Source KEK Version
              </label>
              <input
                type="text"
                readOnly
                value={`v${currentKek?.currentKekVersion ?? 1}`}
                className="w-full px-3.5 py-2 rounded-xl border border-sand bg-sand/20 font-mono text-sm text-brown/70"
              />
            </div>
            <div>
              <label className="block text-xs font-semibold uppercase tracking-wider text-brown/80 mb-1.5 font-mono">
                Target KEK Version
              </label>
              <input
                type="number"
                min="1"
                required
                value={targetKekVersion}
                onChange={(e) => setTargetKekVersion(parseInt(e.target.value))}
                className="w-full px-3.5 py-2 rounded-xl border border-sand bg-white/90 text-sm focus:outline-none focus:ring-2 focus:ring-terracotta font-mono"
              />
            </div>
          </div>

          <div>
            <label className="block text-xs font-semibold uppercase tracking-wider text-brown/80 mb-1.5 font-mono">
              Idempotency-Key
            </label>
            <input
              type="text"
              readOnly
              value={idempotencyKey}
              className="w-full px-3.5 py-2 rounded-xl border border-sand bg-sand/20 font-mono text-xs text-brown/70 select-all"
            />
          </div>

          <div className="flex justify-end gap-2 pt-4">
            <Button type="button" variant="ghost" onClick={() => setIsRotateModalOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" variant="primary" isLoading={rotateMutation.isPending}>
              Start Rotation
            </Button>
          </div>
        </form>
      </Dialog>
    </div>
  );
};

