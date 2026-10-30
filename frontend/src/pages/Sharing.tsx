import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiRequest, ApiError } from '../api/client';
import { AccessGrantResponse, DocumentResponse, ShareDocumentRequest, DownloadResponse } from '../api/types';
import { Card } from '../components/common/Card';
import { Button } from '../components/common/Button';
import { Dialog } from '../components/common/Dialog';
import { Skeleton } from '../components/common/Skeleton';
import { EmptyState } from '../components/common/EmptyState';
import { generateIdempotencyKey } from '../utils/idempotency';
import {
  Share2,
  UserCheck,
  UserX,
  Clock,
  Download,
  AlertCircle,
  Plus,
  ShieldCheck,
  ShieldAlert,
} from 'lucide-react';

export const Sharing: React.FC = () => {
  const queryClient = useQueryClient();

  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [activeTab, setActiveTab] = useState<'my-grants' | 'shared-with-me'>('my-grants');

  // Form state
  const [documentId, setDocumentId] = useState('');
  const [granteeId, setGranteeId] = useState('');
  const [permissions, setPermissions] = useState<string[]>(['READ']);
  const [purpose, setPurpose] = useState('');
  const [expiresAt, setExpiresAt] = useState('');
  const [maxDownloads, setMaxDownloads] = useState<number | undefined>(5);
  const [idempotencyKey, setIdempotencyKey] = useState(generateIdempotencyKey('create-grant'));
  const [grantError, setGrantError] = useState<string | null>(null);

  // Download denial message state
  const [downloadMessage, setDownloadMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  // 1. Fetch documents (to pick documentId in form)
  const { data: documents } = useQuery({
    queryKey: ['documents'],
    queryFn: () => apiRequest<DocumentResponse[]>('/documents'),
  });

  // 2. Fetch grants
  const { data: grantsData, isLoading } = useQuery({
    queryKey: ['my-grants'],
    queryFn: () => apiRequest<{ created: AccessGrantResponse[]; received: AccessGrantResponse[] }>('/shares/my-grants'),
  });

  // Create Grant Mutation
  const createMutation = useMutation({
    mutationFn: async () => {
      if (!documentId) throw new Error('Please select a document');
      if (!granteeId.trim()) throw new Error('Recipient User ID is required');

      const body: ShareDocumentRequest = {
        granteeId: granteeId.trim(),
        permissions,
        purpose: purpose.trim() || undefined,
        expiresAt: expiresAt ? new Date(expiresAt).toISOString() : undefined,
        maxDownloads: maxDownloads ? Number(maxDownloads) : undefined,
      };

      return apiRequest<AccessGrantResponse>(`/documents/${documentId}/shares`, {
        method: 'POST',
        idempotencyKey,
        body: JSON.stringify(body),
      });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['my-grants'] });
      setIsCreateOpen(false);
      resetForm();
    },
    onError: (err: unknown) => {
      if (err instanceof ApiError) {
        setGrantError(err.problem.detail || err.problem.title || 'Failed to create grant');
      } else if (err instanceof Error) {
        setGrantError(err.message);
      } else {
        setGrantError('An error occurred granting access');
      }
    },
  });

  // Revoke Mutation
  const revokeMutation = useMutation({
    mutationFn: async ({ docId, grantId }: { docId: string; grantId: string }) => {
      return apiRequest<void>(`/documents/${docId}/shares/${grantId}`, {
        method: 'DELETE',
      });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['my-grants'] });
    },
    onError: (err: unknown) => {
      if (err instanceof ApiError) {
        alert(err.problem.detail || 'Revocation failed');
      }
    },
  });

  // Download with quota / denial check
  const handleDownloadGrant = async (docId: string) => {
    setDownloadMessage(null);
    try {
      const res = await apiRequest<DownloadResponse>(`/documents/${docId}/download`);

      const blob = new Blob([Uint8Array.from(atob(res.contentBase64), c => c.charCodeAt(0))], { type: res.contentType });
      const link = document.createElement('a');
      link.href = URL.createObjectURL(blob);
      link.download = res.name;
      link.click();

      setDownloadMessage({
        type: 'success',
        text: `Successfully downloaded "${res.name}". Remaining download quota updated.`,
      });
      queryClient.invalidateQueries({ queryKey: ['my-grants'] });
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        setDownloadMessage({
          type: 'error',
          text: `Access Denied (${err.status}): ${err.problem.detail || 'Grant revoked, expired, or download quota exhausted.'}`,
        });
      } else {
        setDownloadMessage({
          type: 'error',
          text: 'Failed to download document.',
        });
      }
    }
  };

  const resetForm = () => {
    setDocumentId('');
    setGranteeId('');
    setPermissions(['READ']);
    setPurpose('');
    setExpiresAt('');
    setMaxDownloads(5);
    setGrantError(null);
    setIdempotencyKey(generateIdempotencyKey('create-grant'));
  };

  const displayedGrants = activeTab === 'my-grants'
    ? grantsData?.created ?? []
    : grantsData?.received ?? [];

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-3xl font-extrabold font-fraunces text-brown tracking-tight">
            Document Sharing
          </h1>
          <p className="text-sm text-brown/70 mt-1">
            Time-limited, download-quota-enforced access grants within your tenant.
          </p>
        </div>

        <Button
          onClick={() => {
            resetForm();
            if (documents && documents[0]) setDocumentId(documents[0].id);
            setIsCreateOpen(true);
          }}
          variant="primary"
          size="md"
        >
          <Plus className="w-4 h-4 mr-1.5" />
          Create Access Grant
        </Button>
      </div>

      {/* Denial / Success Banner */}
      {downloadMessage && (
        <div
          className={`p-4 rounded-xl border flex items-start justify-between gap-3 text-sm animate-in fade-in ${
            downloadMessage.type === 'success'
              ? 'bg-olive/10 border-olive/30 text-olive'
              : 'bg-brick/10 border-brick/30 text-brick'
          }`}
        >
          <div className="flex items-center gap-2">
            {downloadMessage.type === 'success' ? (
              <ShieldCheck className="w-5 h-5 shrink-0" />
            ) : (
              <ShieldAlert className="w-5 h-5 shrink-0" />
            )}
            <span>{downloadMessage.text}</span>
          </div>
          <button
            onClick={() => setDownloadMessage(null)}
            className="font-bold text-xs underline"
          >
            Dismiss
          </button>
        </div>
      )}

      {/* Tabs */}
      <div className="flex border-b border-sand gap-2">
        <button
          onClick={() => setActiveTab('my-grants')}
          className={`pb-3 px-4 text-sm font-semibold border-b-2 transition-all flex items-center gap-2 ${
            activeTab === 'my-grants'
              ? 'border-terracotta text-terracotta'
              : 'border-transparent text-brown/60 hover:text-brown'
          }`}
        >
          <UserCheck className="w-4 h-4" />
          <span>Grants I Created ({grantsData?.created?.length ?? 0})</span>
        </button>
        <button
          onClick={() => setActiveTab('shared-with-me')}
          className={`pb-3 px-4 text-sm font-semibold border-b-2 transition-all flex items-center gap-2 ${
            activeTab === 'shared-with-me'
              ? 'border-terracotta text-terracotta'
              : 'border-transparent text-brown/60 hover:text-brown'
          }`}
        >
          <Share2 className="w-4 h-4" />
          <span>Shared With Me ({grantsData?.received?.length ?? 0})</span>
        </button>
      </div>

      {/* Grants Table */}
      <Card className="p-0 overflow-hidden">
        {isLoading ? (
          <div className="p-6 space-y-4">
            <Skeleton className="h-8 w-full" />
            <Skeleton className="h-8 w-full" />
            <Skeleton className="h-8 w-full" />
          </div>
        ) : displayedGrants.length > 0 ? (
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse text-sm">
              <thead>
                <tr className="border-b border-sand bg-sand/20 text-xs font-semibold uppercase tracking-wider text-brown/70 font-mono">
                  <th className="py-3 px-4">Document ID</th>
                  <th className="py-3 px-4">{activeTab === 'my-grants' ? 'Grantee (User)' : 'Grantor (Owner)'}</th>
                  <th className="py-3 px-4">Remaining Quota</th>
                  <th className="py-3 px-4">Expires At</th>
                  <th className="py-3 px-4">Status</th>
                  <th className="py-3 px-4 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-sand">
                {displayedGrants.map((grant) => {
                  const isRevoked = !!grant.revokedAt;
                  const isExpired = grant.expiresAt && new Date(grant.expiresAt) < new Date();
                  const isQuotaExhausted = grant.remainingDownloads !== undefined && grant.remainingDownloads !== null && grant.remainingDownloads <= 0;

                  return (
                    <tr key={grant.id} className="hover:bg-sand/10 transition-colors">
                      <td className="py-3.5 px-4 font-mono text-xs text-brown font-semibold">
                        {grant.documentId.slice(0, 13)}...
                      </td>
                      <td className="py-3.5 px-4 font-mono text-xs text-brown/80">
                        {activeTab === 'my-grants' ? grant.granteeId : grant.grantorId}
                      </td>
                      <td className="py-3.5 px-4 text-xs font-medium">
                        {grant.remainingDownloads !== undefined && grant.remainingDownloads !== null ? (
                          <span
                            className={`inline-flex items-center px-2 py-0.5 rounded font-mono ${
                              grant.remainingDownloads > 0
                                ? 'bg-olive/10 text-olive'
                                : 'bg-brick/15 text-brick font-bold'
                            }`}
                          >
                            {grant.remainingDownloads} / {grant.maxDownloads ?? '∞'} left
                          </span>
                        ) : (
                          <span className="text-brown/50">Unlimited</span>
                        )}
                      </td>
                      <td className="py-3.5 px-4 text-xs text-brown/70 font-mono">
                        {grant.expiresAt ? (
                          <span className={isExpired ? 'text-brick font-bold' : ''}>
                            {new Date(grant.expiresAt).toLocaleString()}
                          </span>
                        ) : (
                          <span className="text-brown/50">Never</span>
                        )}
                      </td>
                      <td className="py-3.5 px-4">
                        {isRevoked ? (
                          <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold bg-brick/15 text-brick">
                            REVOKED
                          </span>
                        ) : isExpired ? (
                          <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold bg-amber/15 text-amber">
                            EXPIRED
                          </span>
                        ) : isQuotaExhausted ? (
                          <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold bg-brick/15 text-brick">
                            QUOTA EXHAUSTED
                          </span>
                        ) : (
                          <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold bg-olive/15 text-olive">
                            ACTIVE
                          </span>
                        )}
                      </td>
                      <td className="py-3.5 px-4 text-right">
                        <div className="flex items-center justify-end gap-2">
                          <Button
                            onClick={() => handleDownloadGrant(grant.documentId)}
                            variant="secondary"
                            size="sm"
                            title="Download document using this grant"
                          >
                            <Download className="w-3.5 h-3.5 mr-1" />
                            Download
                          </Button>

                          {activeTab === 'my-grants' && !isRevoked && (
                            <Button
                              onClick={() => revokeMutation.mutate({ docId: grant.documentId, grantId: grant.id })}
                              variant="danger"
                              size="sm"
                              isLoading={revokeMutation.isPending}
                              title="Revoke access"
                            >
                              <UserX className="w-3.5 h-3.5 mr-1" />
                              Revoke
                            </Button>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        ) : (
          <EmptyState
            icon={Share2}
            title={activeTab === 'my-grants' ? 'No Created Grants' : 'No Shared Documents'}
            description={
              activeTab === 'my-grants'
                ? 'You have not shared any documents with other users yet.'
                : 'No other users in this tenant have shared documents with you.'
            }
          />
        )}
      </Card>

      {/* Create Grant Dialog */}
      <Dialog
        isOpen={isCreateOpen}
        onClose={() => setIsCreateOpen(false)}
        title="Share Document with Tenant User"
        description="Grants are strictly confined within your tenant and audited cryptographically."
      >
        <form
          onSubmit={(e) => {
            e.preventDefault();
            createMutation.mutate();
          }}
          className="space-y-4"
        >
          {grantError && (
            <div className="p-3 rounded-lg bg-brick/10 border border-brick/30 text-brick text-xs flex items-center gap-2">
              <AlertCircle className="w-4 h-4 shrink-0" />
              <span>{grantError}</span>
            </div>
          )}

          <div>
            <label className="block text-xs font-semibold uppercase tracking-wider text-brown/80 mb-1.5">
              Select Document
            </label>
            <select
              required
              value={documentId}
              onChange={(e) => setDocumentId(e.target.value)}
              className="w-full px-3.5 py-2 rounded-xl border border-sand bg-white/90 text-sm focus:outline-none focus:ring-2 focus:ring-terracotta"
            >
              <option value="" disabled>Select a document...</option>
              {documents?.map((d) => (
                <option key={d.id} value={d.id}>
                  {d.name} (v{d.currentVersion})
                </option>
              ))}
            </select>
          </div>

          <div>
            <label className="block text-xs font-semibold uppercase tracking-wider text-brown/80 mb-1.5">
              Grantee User ID (UUID)
            </label>
            <input
              type="text"
              required
              value={granteeId}
              onChange={(e) => setGranteeId(e.target.value)}
              placeholder="e.g. 11111111-aaaa-aaaa-aaaa-aaaaaaaaaaa2"
              className="w-full px-3.5 py-2 rounded-xl border border-sand bg-white/90 text-sm focus:outline-none focus:ring-2 focus:ring-terracotta font-mono"
            />
            <p className="text-[11px] text-brown/50 mt-1">
              Example user1 ID: 11111111-aaaa-aaaa-aaaa-aaaaaaaaaaa2
            </p>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-semibold uppercase tracking-wider text-brown/80 mb-1.5">
                Max Downloads (Quota)
              </label>
              <input
                type="number"
                min="1"
                value={maxDownloads ?? ''}
                onChange={(e) => setMaxDownloads(e.target.value ? parseInt(e.target.value) : undefined)}
                placeholder="5 (or leave empty for unlimited)"
                className="w-full px-3.5 py-2 rounded-xl border border-sand bg-white/90 text-sm focus:outline-none focus:ring-2 focus:ring-terracotta"
              />
            </div>
            <div>
              <label className="block text-xs font-semibold uppercase tracking-wider text-brown/80 mb-1.5">
                Expires At (Optional)
              </label>
              <input
                type="datetime-local"
                value={expiresAt}
                onChange={(e) => setExpiresAt(e.target.value)}
                className="w-full px-3.5 py-2 rounded-xl border border-sand bg-white/90 text-sm focus:outline-none focus:ring-2 focus:ring-terracotta"
              />
            </div>
          </div>

          <div>
            <label className="block text-xs font-semibold uppercase tracking-wider text-brown/80 mb-1.5">
              Purpose / Business Justification
            </label>
            <input
              type="text"
              value={purpose}
              onChange={(e) => setPurpose(e.target.value)}
              placeholder="e.g. Compliance audit review Q4"
              className="w-full px-3.5 py-2 rounded-xl border border-sand bg-white/90 text-sm focus:outline-none focus:ring-2 focus:ring-terracotta"
            />
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
            <Button type="button" variant="ghost" onClick={() => setIsCreateOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" variant="primary" isLoading={createMutation.isPending}>
              Grant Access
            </Button>
          </div>
        </form>
      </Dialog>
    </div>
  );
};

