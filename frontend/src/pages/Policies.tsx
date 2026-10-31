import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiRequest, ApiError } from '../api/client';
import { PolicyDto, PolicyRequest } from '../api/types';
import { Card } from '../components/common/Card';
import { Button } from '../components/common/Button';
import { Dialog } from '../components/common/Dialog';
import { Skeleton } from '../components/common/Skeleton';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../components/common/Toast';
import {
  FileCode2,
  Edit,
  Plus,
  AlertTriangle,
  RotateCcw,
  CheckCircle2,
  Clock,
  User,
} from 'lucide-react';

export const Policies: React.FC = () => {
  const queryClient = useQueryClient();
  const { hasRole } = useAuth();
  const isAdmin = hasRole('ADMIN');
  const { showToast } = useToast();

  const [editingPolicy, setEditingPolicy] = useState<PolicyDto | null>(null);
  const [isCreateOpen, setIsCreateOpen] = useState(false);

  // Form state
  const [name, setName] = useState('');
  const [expression, setExpression] = useState('');
  const [expectedVersion, setExpectedVersion] = useState<number | undefined>(undefined);

  // 409 Conflict banner state
  const [conflictError, setConflictError] = useState<string | null>(null);
  const [generalError, setGeneralError] = useState<string | null>(null);

  // 1. Fetch policies
  const { data: policies, isLoading, refetch } = useQuery({
    queryKey: ['policies'],
    queryFn: () => apiRequest<PolicyDto[]>('/policies'),
  });

  // Save / Update Mutation
  const saveMutation = useMutation({
    mutationFn: async ({ isCreate }: { isCreate: boolean }) => {
      setConflictError(null);
      setGeneralError(null);

      const payload: PolicyRequest = {
        name: name.trim(),
        expression: expression.trim(),
        version: expectedVersion,
      };

      if (isCreate) {
        return apiRequest<PolicyDto>('/policies', {
          method: 'POST',
          body: JSON.stringify(payload),
        });
      } else {
        return apiRequest<PolicyDto>(`/policies/${editingPolicy!.id}`, {
          method: 'PUT',
          body: JSON.stringify(payload),
        });
      }
    },
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: ['policies'] });
      setEditingPolicy(null);
      setIsCreateOpen(false);
      resetForm();
      showToast({
        type: 'success',
        message: `Policy "${(data as PolicyDto).name || ''}" saved successfully.`,
      });
    },
    onError: (err: unknown) => {
      if (err instanceof ApiError) {
        if (err.status === 409) {
          // RFC 9457 409 Conflict banner as required by the specification
          setConflictError(
            err.problem.detail ||
            'Conflict (409): This policy was concurrently modified by another administrator. Please reload the latest version.'
          );
        } else {
          setGeneralError(err.problem.detail || err.problem.title || 'Failed to save policy');
        }
      } else if (err instanceof Error) {
        setGeneralError(err.message);
      } else {
        setGeneralError('Failed to save policy');
      }
    },
  });

  const resetForm = () => {
    setName('');
    setExpression('');
    setExpectedVersion(undefined);
    setConflictError(null);
    setGeneralError(null);
  };

  const handleOpenEdit = (p: PolicyDto) => {
    setEditingPolicy(p);
    setName(p.name);
    setExpression(p.expression);
    setExpectedVersion(p.version);
    setConflictError(null);
    setGeneralError(null);
  };

  const handleReloadLatest = async () => {
    const res = await refetch();
    if (editingPolicy && res.data) {
      const fresh = res.data.find(p => p.id === editingPolicy.id);
      if (fresh) {
        setEditingPolicy(fresh);
        setName(fresh.name);
        setExpression(fresh.expression);
        setExpectedVersion(fresh.version);
      }
    }
    setConflictError(null);
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-3xl font-extrabold font-fraunces text-brown tracking-tight">
            Tenant Access Policies
          </h1>
          <p className="text-sm text-brown/70 mt-1">
            Fine-grained ABAC expressions with optimistic locking & conflict detection.
          </p>
        </div>

        {isAdmin && (
          <Button
            onClick={() => {
              resetForm();
              setIsCreateOpen(true);
            }}
            variant="primary"
            size="md"
          >
            <Plus className="w-4 h-4 mr-1.5" />
            Create Policy
          </Button>
        )}
      </div>

      {/* Policies List */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
        {isLoading ? (
          <>
            <Skeleton className="h-44 w-full" />
            <Skeleton className="h-44 w-full" />
          </>
        ) : policies && policies.length > 0 ? (
          policies.map((policy) => (
            <Card key={policy.id} className="p-5 flex flex-col justify-between space-y-4">
              <div>
                <div className="flex items-start justify-between gap-2">
                  <div className="flex items-center gap-2">
                    <div className="w-8 h-8 rounded-lg bg-terracotta/10 text-terracotta flex items-center justify-center shrink-0">
                      <FileCode2 className="w-4 h-4" />
                    </div>
                    <div>
                      <h3 className="font-bold text-base font-fraunces text-brown">
                        {policy.name}
                      </h3>
                      <span className="text-[11px] text-brown/50 font-mono">
                        Policy ID: {policy.policyId || policy.id}
                      </span>
                    </div>
                  </div>

                  {/* Version Chip */}
                  <span className="px-2.5 py-0.5 rounded-full text-xs font-bold font-mono bg-amber/15 text-brown border border-amber/30">
                    version {policy.version}
                  </span>
                </div>

                {/* Expression snippet */}
                <div className="mt-3 p-3 rounded-lg bg-sand/20 border border-sand/60 font-mono text-xs text-brown overflow-x-auto">
                  <code>{policy.expression}</code>
                </div>
              </div>

              <div className="pt-3 border-t border-sand flex items-center justify-between text-xs text-brown/60">
                <div className="flex items-center gap-1.5">
                  <User className="w-3.5 h-3.5 text-brown/40" />
                  <span>Updated by: <strong>{policy.updatedBy}</strong></span>
                </div>

                {isAdmin && (
                  <Button
                    onClick={() => handleOpenEdit(policy)}
                    variant="ghost"
                    size="sm"
                    className="h-7 text-xs"
                  >
                    <Edit className="w-3.5 h-3.5 mr-1" />
                    Edit Expression
                  </Button>
                )}
              </div>
            </Card>
          ))
        ) : (
          <div className="col-span-2 p-8 text-center text-brown/60 text-sm">
            No policies defined for this tenant yet.
          </div>
        )}
      </div>

      {/* Edit / Create Dialog with 409 Conflict Banner */}
      <Dialog
        isOpen={isCreateOpen || !!editingPolicy}
        onClose={() => {
          setIsCreateOpen(false);
          setEditingPolicy(null);
        }}
        title={isCreateOpen ? 'Create Access Policy' : `Edit Policy: ${editingPolicy?.name}`}
        description="Policy version number is strictly verified before saving to prevent lost updates."
      >
        <form
          onSubmit={(e) => {
            e.preventDefault();
            saveMutation.mutate({ isCreate: isCreateOpen });
          }}
          className="space-y-4"
        >
          {/* Prominent 409 Conflict Banner */}
          {conflictError && (
            <div className="p-4 rounded-xl bg-brick/10 border-2 border-brick/40 text-brick text-xs space-y-2.5 animate-in shake">
              <div className="flex items-start gap-2">
                <AlertTriangle className="w-5 h-5 shrink-0 mt-0.5 text-brick" />
                <div>
                  <div className="font-bold text-sm">409 Version Conflict Detected</div>
                  <div className="mt-1 leading-relaxed opacity-90">{conflictError}</div>
                </div>
              </div>

              <div className="pt-2 border-t border-brick/20 flex justify-end">
                <Button
                  type="button"
                  onClick={handleReloadLatest}
                  variant="secondary"
                  size="sm"
                  className="text-brick border-brick/40 hover:bg-brick/10"
                >
                  <RotateCcw className="w-3.5 h-3.5 mr-1" />
                  Reload Latest Version
                </Button>
              </div>
            </div>
          )}

          {generalError && (
            <div className="p-3 rounded-lg bg-brick/10 border border-brick/30 text-brick text-xs flex items-center gap-2">
              <AlertTriangle className="w-4 h-4 shrink-0" />
              <span>{generalError}</span>
            </div>
          )}

          <div>
            <label className="block text-xs font-semibold uppercase tracking-wider text-brown/80 mb-1.5">
              Policy Name
            </label>
            <input
              type="text"
              required
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="e.g. internal-engineering-read"
              className="w-full px-3.5 py-2 rounded-xl border border-sand bg-white/90 text-sm focus:outline-none focus:ring-2 focus:ring-terracotta font-mono"
            />
          </div>

          <div>
            <div className="flex items-center justify-between mb-1.5">
              <label className="block text-xs font-semibold uppercase tracking-wider text-brown/80">
                Expression
              </label>
              {expectedVersion !== undefined && (
                <span className="text-[11px] font-mono text-brown/60">
                  Target Version: v{expectedVersion}
                </span>
              )}
            </div>
            <textarea
              rows={4}
              required
              value={expression}
              onChange={(e) => setExpression(e.target.value)}
              placeholder='e.g. classification == "INTERNAL" || roles.contains("ADMIN")'
              className="w-full px-3.5 py-2 rounded-xl border border-sand bg-white/90 text-sm focus:outline-none focus:ring-2 focus:ring-terracotta font-mono text-xs"
            />
          </div>

          <div className="flex justify-end gap-2 pt-4">
            <Button
              type="button"
              variant="ghost"
              onClick={() => {
                setIsCreateOpen(false);
                setEditingPolicy(null);
              }}
            >
              Cancel
            </Button>
            <Button
              type="submit"
              variant="primary"
              isLoading={saveMutation.isPending}
            >
              Save Policy
            </Button>
          </div>
        </form>
      </Dialog>
    </div>
  );
};

