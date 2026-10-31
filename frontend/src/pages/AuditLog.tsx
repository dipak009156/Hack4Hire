import React, { useState, useMemo } from 'react';
import { useQuery, useMutation } from '@tanstack/react-query';
import { apiRequest } from '../api/client';
import { AuditEvent, AuditVerificationResult } from '../api/types';
import { Card } from '../components/common/Card';
import { Button } from '../components/common/Button';
import { Skeleton } from '../components/common/Skeleton';
import { useToast } from '../components/common/Toast';
import {
  ScrollText,
  ShieldCheck,
  ShieldAlert,
  Link as LinkIcon,
  RefreshCw,
  Hash,
  Download,
  Filter,
  X,
} from 'lucide-react';

export const AuditLog: React.FC = () => {
  const { showToast } = useToast();
  const [verificationResult, setVerificationResult] = useState<AuditVerificationResult | null>(null);

  // Filter state
  const [actionFilter, setActionFilter] = useState('ALL');
  const [actorFilter, setActorFilter] = useState('');

  // 1. Fetch audit events
  const { data: events, isLoading, refetch } = useQuery({
    queryKey: ['audit-events'],
    queryFn: () => apiRequest<AuditEvent[]>('/audit/events'),
  });

  // Derive unique action types for filter dropdown
  const uniqueActions = useMemo(() => {
    if (!events) return [];
    const actions = Array.from(new Set(events.map((e) => e.action))).sort();
    return actions;
  }, [events]);

  // Filtered events
  const filteredEvents = useMemo(() => {
    if (!events) return [];
    return events.filter((e) => {
      const matchesAction = actionFilter === 'ALL' || e.action === actionFilter;
      const actor = (e.actor || e.actorName || 'SYSTEM').toLowerCase();
      const matchesActor = !actorFilter.trim() || actor.includes(actorFilter.trim().toLowerCase());
      return matchesAction && matchesActor;
    });
  }, [events, actionFilter, actorFilter]);

  const handleExportAuditTrail = () => {
    if (!events || events.length === 0) return;
    const blob = new Blob([JSON.stringify(events, null, 2)], { type: 'application/json' });
    const link = document.createElement('a');
    link.href = URL.createObjectURL(blob);
    link.download = `quantumlock-audit-chain-${Date.now()}.json`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    showToast({ type: 'success', message: `Exported ${events.length} audit events as JSON.` });
  };

  // Verify Chain Mutation
  const verifyMutation = useMutation({
    mutationFn: async () => {
      return apiRequest<AuditVerificationResult>('/audit/verify', { method: 'POST' });
    },
    onSuccess: (data) => {
      setVerificationResult(data);
      if (data.valid) {
        showToast({
          type: 'success',
          message: `Audit chain VERIFIED — ${data.totalEvents} events intact from GENESIS.`,
        });
      } else {
        showToast({
          type: 'error',
          message: `Chain TAMPERED at position #${data.firstInconsistentEvent?.chainPosition ?? '?'}`,
        });
      }
    },
    onError: () => {
      showToast({ type: 'error', message: 'Chain verification failed — check connection.' });
    },
  });

  const firstInconsistentPos = verificationResult?.firstInconsistentEvent?.chainPosition;

  const clearFilters = () => {
    setActionFilter('ALL');
    setActorFilter('');
  };

  const hasActiveFilters = actionFilter !== 'ALL' || actorFilter.trim() !== '';

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-3xl font-extrabold font-fraunces text-brown tracking-tight">
            Audit Trail &amp; Hash Chaining
          </h1>
          <p className="text-sm text-brown/70 mt-1">
            Append-only, serialized per-tenant cryptographic audit ledger (ADR-006 / Invariant I6).
          </p>
        </div>

        <div className="flex flex-wrap gap-2">
          <Button
            onClick={() => refetch()}
            variant="secondary"
            size="md"
          >
            <RefreshCw className="w-4 h-4 mr-1.5" />
            Refresh
          </Button>

          <Button
            onClick={handleExportAuditTrail}
            variant="secondary"
            size="md"
            disabled={!events || events.length === 0}
            title="Export full cryptographic ledger as JSON"
          >
            <Download className="w-4 h-4 mr-1.5" />
            Export JSON
          </Button>

          <Button
            onClick={() => verifyMutation.mutate()}
            variant="primary"
            size="md"
            isLoading={verifyMutation.isPending}
          >
            <ShieldCheck className="w-4 h-4 mr-1.5" />
            Verify Chain Integrity
          </Button>
        </div>
      </div>

      {/* Verification Result Banner */}
      {verificationResult && (
        <div
          className={`p-5 rounded-xl border flex flex-col sm:flex-row items-start justify-between gap-4 animate-in fade-in ${
            verificationResult.valid
              ? 'bg-olive/10 border-olive/30 text-olive'
              : 'bg-brick/10 border-2 border-brick text-brick'
          }`}
        >
          <div className="flex items-start gap-3">
            {verificationResult.valid ? (
              <ShieldCheck className="w-6 h-6 text-olive shrink-0 mt-0.5" />
            ) : (
              <ShieldAlert className="w-6 h-6 text-brick shrink-0 mt-0.5" />
            )}
            <div>
              <div className="font-bold text-base">
                {verificationResult.valid
                  ? 'Audit Chain Verified Successfully'
                  : 'Cryptographic Tampering Detected!'}
              </div>
              <div className="text-xs mt-1 text-brown/80 leading-relaxed">
                {verificationResult.message}
              </div>

              {!verificationResult.valid && verificationResult.firstInconsistentEvent && (
                <div className="mt-3 p-3 rounded-lg bg-brick/15 border border-brick/40 text-xs text-brick font-mono space-y-1">
                  <div><strong>First Inconsistent Position:</strong> #{verificationResult.firstInconsistentEvent.chainPosition}</div>
                  <div><strong>Action:</strong> {verificationResult.firstInconsistentEvent.action}</div>
                  <div><strong>Reason:</strong> {verificationResult.firstInconsistentEvent.reason}</div>
                  {verificationResult.firstInconsistentEvent.expectedHash && (
                    <div className="truncate"><strong>Expected:</strong> {verificationResult.firstInconsistentEvent.expectedHash}</div>
                  )}
                  {verificationResult.firstInconsistentEvent.actualHash && (
                    <div className="truncate"><strong>Stored:</strong> {verificationResult.firstInconsistentEvent.actualHash}</div>
                  )}
                </div>
              )}
            </div>
          </div>

          <button
            onClick={() => setVerificationResult(null)}
            className="text-xs font-semibold underline shrink-0"
          >
            Clear Banner
          </button>
        </div>
      )}

      {/* Filter Row */}
      <Card className="p-4">
        <div className="flex flex-wrap items-center gap-3">
          <div className="flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wider text-brown/60 font-mono">
            <Filter className="w-3.5 h-3.5" />
            Filters:
          </div>

          {/* Action Type Filter */}
          <div className="flex items-center gap-2">
            <label className="text-xs text-brown/70 font-semibold font-mono">Action:</label>
            <select
              value={actionFilter}
              onChange={(e) => setActionFilter(e.target.value)}
              className="px-2.5 py-1.5 rounded-lg border border-sand bg-white/90 text-xs text-brown focus:outline-none focus:ring-2 focus:ring-terracotta font-mono"
            >
              <option value="ALL">ALL ({events?.length ?? 0})</option>
              {uniqueActions.map((action) => (
                <option key={action} value={action}>{action}</option>
              ))}
            </select>
          </div>

          {/* Actor Filter */}
          <div className="flex items-center gap-2">
            <label className="text-xs text-brown/70 font-semibold font-mono">Actor:</label>
            <input
              type="text"
              value={actorFilter}
              onChange={(e) => setActorFilter(e.target.value)}
              placeholder="Filter by actor..."
              className="px-2.5 py-1.5 rounded-lg border border-sand bg-white/90 text-xs text-brown focus:outline-none focus:ring-2 focus:ring-terracotta font-mono w-40"
            />
          </div>

          {hasActiveFilters && (
            <button
              onClick={clearFilters}
              className="flex items-center gap-1 px-2.5 py-1.5 rounded-lg bg-brick/10 text-brick text-xs font-semibold hover:bg-brick/20 transition-colors border border-brick/20"
            >
              <X className="w-3 h-3" />
              Clear Filters
            </button>
          )}

          <span className="ml-auto text-xs text-brown/50 font-mono">
            Showing {filteredEvents.length} / {events?.length ?? 0} events
          </span>
        </div>
      </Card>

      {/* Audit Chain Table */}
      <Card className="p-0 overflow-hidden">
        <div className="px-5 py-3.5 border-b border-sand bg-sand/20 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <LinkIcon className="w-4 h-4 text-terracotta" />
            <span className="text-xs font-bold uppercase tracking-wider text-brown font-mono">
              Cryptographic Hash Chain ({filteredEvents.length} Events)
            </span>
          </div>
          <span className="text-xs text-brown/50 font-mono">
            Genesis: "GENESIS"
          </span>
        </div>

        {isLoading ? (
          <div className="p-6 space-y-4">
            <Skeleton className="h-8 w-full" />
            <Skeleton className="h-8 w-full" />
            <Skeleton className="h-8 w-full" />
          </div>
        ) : filteredEvents.length > 0 ? (
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse text-sm">
              <thead>
                <tr className="border-b border-sand text-xs font-semibold uppercase tracking-wider text-brown/70 font-mono">
                  <th className="py-3 px-4">Pos #</th>
                  <th className="py-3 px-4">Action</th>
                  <th className="py-3 px-4">Actor</th>
                  <th className="py-3 px-4">Outcome</th>
                  <th className="py-3 px-4">Previous Hash</th>
                  <th className="py-3 px-4">Event Hash</th>
                  <th className="py-3 px-4">Timestamp</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-sand font-mono text-xs">
                {filteredEvents.map((event) => {
                  const isTampered = firstInconsistentPos !== undefined && event.chainPosition === firstInconsistentPos;

                  return (
                    <tr
                      key={event.id}
                      className={`transition-colors ${
                        isTampered
                          ? 'bg-brick/20 border-l-4 border-brick font-bold'
                          : 'hover:bg-sand/10'
                      }`}
                    >
                      <td className="py-3.5 px-4 font-bold text-brown">
                        #{event.chainPosition}
                      </td>
                      <td className="py-3.5 px-4 font-sans font-semibold text-brown">
                        {event.action}
                      </td>
                      <td className="py-3.5 px-4 font-sans text-brown/80">
                        {event.actor || event.actorName || 'SYSTEM'}
                      </td>
                      <td className="py-3.5 px-4">
                        <span
                          className={`inline-block px-2 py-0.5 rounded text-[11px] font-bold ${
                            event.outcome === 'SUCCESS'
                              ? 'bg-olive/15 text-olive'
                              : 'bg-brick/15 text-brick'
                          }`}
                        >
                          {event.outcome}
                        </span>
                      </td>
                      <td className="py-3.5 px-4 text-brown/60" title={event.previousHash ?? undefined}>
                        {event.previousHash ? `${event.previousHash.slice(0, 10)}...` : 'GENESIS'}
                      </td>
                      <td className="py-3.5 px-4 text-terracotta font-semibold" title={event.eventHash}>
                        {event.eventHash.slice(0, 12)}...
                      </td>
                      <td className="py-3.5 px-4 text-brown/60 font-sans text-xs">
                        {new Date(event.createdAt).toLocaleTimeString()}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        ) : events && events.length > 0 ? (
          <div className="p-8 text-center text-sm text-brown/60">
            No events match the current filters. <button onClick={clearFilters} className="text-terracotta underline">Clear filters</button>
          </div>
        ) : (
          <div className="p-8 text-center text-sm text-brown/60">
            No audit events recorded yet for this tenant.
          </div>
        )}
      </Card>
    </div>
  );
};
