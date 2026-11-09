import React, { useState, useEffect } from 'react';
import { useParams, useNavigate, useSearchParams, Link } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiRequest, ApiError } from '../api/client';
import { CredentialRequestResponse, ReviewCredentialRequestDto } from '../api/types';
import { Card } from '../components/common/Card';
import { Button } from '../components/common/Button';
import { Dialog } from '../components/common/Dialog';
import { useToast } from '../components/common/Toast';
import { EmailInboxModal } from '../components/common/EmailInboxModal';
import {
  ShieldCheck,
  ShieldAlert,
  Building,
  User,
  Mail,
  CheckCircle2,
  XCircle,
  Clock,
  ArrowLeft,
  KeyRound,
  ExternalLink,
  Layers,
  FileText,
} from 'lucide-react';

export const ReviewRequest: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { showToast } = useToast();

  const [searchParams] = useSearchParams();

  const [isInboxOpen, setIsInboxOpen] = useState(false);
  const [isApproveOpen, setIsApproveOpen] = useState(false);
  const [isRejectOpen, setIsRejectOpen] = useState(false);
  const [tempPassword, setTempPassword] = useState('password123');
  const [rejectionReason, setRejectionReason] = useState('Does not meet zero-trust onboarding criteria.');
  const [reviewError, setReviewError] = useState<string | null>(null);

  useEffect(() => {
    const action = searchParams.get('action');
    if (action === 'approve') {
      setIsApproveOpen(true);
    } else if (action === 'reject') {
      setIsRejectOpen(true);
    }
  }, [searchParams, id]);

  // 1. Fetch single request if ID is present
  const { data: request, isLoading, refetch } = useQuery({
    queryKey: ['credential-request', id],
    queryFn: () => apiRequest<CredentialRequestResponse>(`/credential-requests/${id}`),
    enabled: !!id,
  });

  // 2. Fetch all requests for listing
  const { data: allRequests, isLoading: loadingAll } = useQuery({
    queryKey: ['all-credential-requests'],
    queryFn: () => apiRequest<CredentialRequestResponse[]>('/credential-requests'),
  });

  // Review Mutation
  const reviewMutation = useMutation({
    mutationFn: async ({ action, reason, password }: { action: 'APPROVE' | 'REJECT'; reason?: string; password?: string }) => {
      const targetId = id || request?.id;
      if (!targetId) throw new Error('No request selected');

      const body: ReviewCredentialRequestDto = {
        action,
        rejectionReason: reason,
        temporaryPassword: password,
      };

      return apiRequest<CredentialRequestResponse>(`/credential-requests/${targetId}/review`, {
        method: 'POST',
        body: JSON.stringify(body),
      });
    },
    onSuccess: (data, vars) => {
      queryClient.invalidateQueries({ queryKey: ['credential-request', id] });
      queryClient.invalidateQueries({ queryKey: ['all-credential-requests'] });
      queryClient.invalidateQueries({ queryKey: ['simulated-emails'] });
      setIsApproveOpen(false);
      setIsRejectOpen(false);

      if (vars.action === 'APPROVE') {
        showToast({
          type: 'success',
          message: `Tenant "${data.tenantName}" approved & provisioned! User credentials sent via email.`,
        });
      } else {
        showToast({
          type: 'info',
          message: `Request for "${data.tenantName}" has been rejected.`,
        });
      }
    },
    onError: (err: unknown) => {
      if (err instanceof ApiError) {
        setReviewError(err.problem.detail || err.problem.title || 'Review action failed');
      } else if (err instanceof Error) {
        setReviewError(err.message);
      } else {
        setReviewError('Failed to process review action');
      }
    },
  });

  const activeRequest = request || (id ? null : allRequests?.[0]);

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <div className="flex items-center gap-2 mb-1">
            <Link
              to="/"
              className="text-xs font-semibold text-brown/60 hover:text-brown flex items-center gap-1"
            >
              <ArrowLeft className="w-3.5 h-3.5" /> Back to Dashboard
            </Link>
          </div>
          <h1 className="text-3xl font-extrabold font-fraunces text-brown tracking-tight">
            Tenant Credential Authorizations
          </h1>
          <p className="text-sm text-brown/70 mt-1">
            Review onboarding submissions, prevent tenant name redundancy, and provision isolated workspaces.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <Button
            variant="secondary"
            size="md"
            onClick={() => setIsInboxOpen(true)}
          >
            <Mail className="w-4 h-4 mr-2 text-terracotta" />
            Notification Outbox
          </Button>
        </div>
      </div>

      {reviewError && (
        <div className="p-4 rounded-xl bg-brick/10 border border-brick/30 text-brick text-xs flex items-center justify-between">
          <span>{reviewError}</span>
          <button onClick={() => setReviewError(null)} className="font-bold underline ml-2">Dismiss</button>
        </div>
      )}

      {/* Main Grid: Request Detail + Request Queue */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Left: Queue of Requests */}
        <div className="lg:col-span-1 space-y-3">
          <h3 className="text-sm font-bold font-fraunces text-brown uppercase font-mono tracking-wider">
            Request Queue ({allRequests?.length ?? 0})
          </h3>

          <div className="space-y-2 max-h-[600px] overflow-y-auto pr-1">
            {loadingAll ? (
              <div className="p-4 text-center text-xs text-brown/50">Loading requests...</div>
            ) : !allRequests || allRequests.length === 0 ? (
              <Card className="p-6 text-center text-xs text-brown/50">
                No credential requests found.
              </Card>
            ) : (
              allRequests.map((r) => {
                const isSelected = activeRequest?.id === r.id;
                const isPending = r.status === 'PENDING';
                const isApproved = r.status === 'APPROVED';

                return (
                  <Card
                    key={r.id}
                    onClick={() => navigate(`/review-request/${r.id}`)}
                    className={`p-4 cursor-pointer transition-all ${
                      isSelected
                        ? 'border-2 border-terracotta bg-cream shadow-sm'
                        : 'hover:border-sand hover:bg-white/80'
                    }`}
                  >
                    <div className="flex items-center justify-between gap-2">
                      <span className="font-mono font-bold text-sm text-brown truncate">
                        {r.tenantName}
                      </span>
                      <span
                        className={`text-[10px] font-mono font-bold uppercase px-2 py-0.5 rounded ${
                          isApproved
                            ? 'bg-olive/15 text-olive border border-olive/30'
                            : isPending
                            ? 'bg-amber/15 text-amber border border-amber/30 animate-pulse'
                            : 'bg-brick/15 text-brick border border-brick/30'
                        }`}
                      >
                        {r.status}
                      </span>
                    </div>

                    <div className="text-xs text-brown/70 mt-1 truncate">
                      {r.fullName} • {r.username}
                    </div>

                    <div className="text-[11px] font-mono text-brown/40 mt-2 flex justify-between">
                      <span>{r.department || 'General'}</span>
                      <span>{new Date(r.createdAt).toLocaleDateString()}</span>
                    </div>
                  </Card>
                );
              })
            )}
          </div>
        </div>

        {/* Right: Detailed Inspection & Approval Actions */}
        <div className="lg:col-span-2">
          {activeRequest ? (
            <Card className="p-6 sm:p-8 space-y-6">
              {/* Header card info */}
              <div className="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-4 pb-6 border-b border-sand">
                <div>
                  <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-mono font-bold uppercase mb-2 bg-sand/30 text-brown">
                    <Building className="w-3.5 h-3.5 text-terracotta" />
                    Target Tenant: {activeRequest.tenantName}
                  </div>
                  <h2 className="text-2xl font-bold font-fraunces text-brown">
                    Request from {activeRequest.fullName}
                  </h2>
                  <p className="text-xs text-brown/60 font-mono mt-0.5">
                    ID: {activeRequest.id} • Submitted {new Date(activeRequest.createdAt).toLocaleString()}
                  </p>
                </div>

                <div className="shrink-0">
                  <span
                    className={`inline-block px-3 py-1 rounded-full text-xs font-bold font-mono uppercase ${
                      activeRequest.status === 'APPROVED'
                        ? 'bg-olive/15 text-olive border border-olive/30'
                        : activeRequest.status === 'PENDING'
                        ? 'bg-amber/15 text-amber border border-amber/30'
                        : 'bg-brick/15 text-brick border border-brick/30'
                    }`}
                  >
                    STATUS: {activeRequest.status}
                  </span>
                </div>
              </div>

              {/* Status Specific Banners */}
              {activeRequest.status === 'APPROVED' && (
                <div className="p-4 rounded-xl bg-olive/10 border border-olive/30 text-olive text-xs flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <CheckCircle2 className="w-4 h-4 shrink-0" />
                    <span>
                      This tenant has been provisioned. Administrator credentials have been emailed to <strong>{activeRequest.email}</strong>.
                    </span>
                  </div>
                  <Button
                    size="sm"
                    variant="secondary"
                    onClick={() => setIsInboxOpen(true)}
                    className="text-xs shrink-0"
                  >
                    View Approval Email
                  </Button>
                </div>
              )}

              {activeRequest.status === 'REJECTED' && (
                <div className="p-4 rounded-xl bg-brick/10 border border-brick/30 text-brick text-xs flex items-start gap-2">
                  <XCircle className="w-4 h-4 shrink-0 mt-0.5" />
                  <div>
                    <strong>Request Rejected:</strong> {activeRequest.rejectionReason} (Reviewed by {activeRequest.reviewedBy || 'Admin'})
                  </div>
                </div>
              )}

              {/* Details table */}
              <div className="bg-sand/10 rounded-xl p-5 border border-sand divide-y divide-sand/40 text-xs">
                <div className="py-2.5 grid grid-cols-3">
                  <span className="font-semibold text-brown/60 uppercase font-mono">Corporate Email</span>
                  <span className="col-span-2 font-mono text-terracotta font-bold">{activeRequest.email}</span>
                </div>
                <div className="py-2.5 grid grid-cols-3">
                  <span className="font-semibold text-brown/60 uppercase font-mono">Admin Username</span>
                  <span className="col-span-2 font-mono text-brown font-bold">{activeRequest.username}</span>
                </div>
                <div className="py-2.5 grid grid-cols-3">
                  <span className="font-semibold text-brown/60 uppercase font-mono">Desired Tenant Name</span>
                  <span className="col-span-2 font-mono text-olive font-bold">{activeRequest.tenantName}</span>
                </div>
                <div className="py-2.5 grid grid-cols-3">
                  <span className="font-semibold text-brown/60 uppercase font-mono">Department</span>
                  <span className="col-span-2 text-brown">{activeRequest.department || 'Not specified'}</span>
                </div>
                <div className="py-2.5 grid grid-cols-3">
                  <span className="font-semibold text-brown/60 uppercase font-mono">Business Need / Reason</span>
                  <span className="col-span-2 text-brown leading-relaxed">{activeRequest.reason}</span>
                </div>
              </div>

              {/* Action Buttons for Pending Request */}
              {activeRequest.status === 'PENDING' ? (
                <div className="pt-4 border-t border-sand flex flex-wrap items-center justify-between gap-3">
                  <div className="text-xs text-brown/60 font-mono">
                    Actions will trigger automated email dispatch to {activeRequest.email}
                  </div>

                  <div className="flex items-center gap-3">
                    <Button
                      variant="danger"
                      size="md"
                      onClick={() => setIsRejectOpen(true)}
                    >
                      <XCircle className="w-4 h-4 mr-2" />
                      Reject Request
                    </Button>

                    <Button
                      variant="primary"
                      size="md"
                      onClick={() => setIsApproveOpen(true)}
                    >
                      <CheckCircle2 className="w-4 h-4 mr-2" />
                      Approve & Provision Tenant
                    </Button>
                  </div>
                </div>
              ) : (
                <div className="pt-2 flex justify-end">
                  <Button
                    variant="secondary"
                    size="sm"
                    onClick={() => setIsInboxOpen(true)}
                  >
                    <Mail className="w-4 h-4 mr-1.5" />
                    Inspect Generated Emails in Outbox
                  </Button>
                </div>
              )}
            </Card>
          ) : (
            <Card className="p-12 text-center text-sm text-brown/50">
              Select a request from the queue to review.
            </Card>
          )}
        </div>
      </div>

      {/* Approve Confirmation Modal */}
      <Dialog
        isOpen={isApproveOpen}
        onClose={() => setIsApproveOpen(false)}
        title="Authorize Tenant Provisioning"
      >
        <div className="space-y-4">
          <p className="text-sm text-brown/80 leading-relaxed">
            Approving this request will automatically:
          </p>
          <ul className="list-disc pl-5 text-xs text-brown/80 space-y-1.5">
            <li>Register the new isolated tenant <strong>{activeRequest?.tenantName}</strong> in PostgreSQL.</li>
            <li>Create administrator user <strong>{activeRequest?.username}</strong> with <strong>HIGH</strong> device trust.</li>
            <li>Send an official credentials email to <strong>{activeRequest?.email}</strong> with their initial login link.</li>
            <li>Record an immutable audit event in the cryptographic chain.</li>
          </ul>

          <div className="space-y-1.5 pt-2">
            <label className="block text-xs font-semibold text-brown uppercase font-mono">
              Initial Password for {activeRequest?.username}
            </label>
            <input
              type="text"
              value={tempPassword}
              onChange={(e) => setTempPassword(e.target.value)}
              className="w-full px-3 py-2 rounded-xl border border-sand bg-white text-sm font-mono focus:outline-none focus:ring-2 focus:ring-terracotta"
            />
            <p className="text-[11px] text-brown/50">Default: password123 (matching seed user convention)</p>
          </div>

          <div className="flex justify-end gap-3 pt-4 border-t border-sand">
            <Button variant="ghost" onClick={() => setIsApproveOpen(false)}>
              Cancel
            </Button>
            <Button
              variant="primary"
              isLoading={reviewMutation.isPending}
              onClick={() => reviewMutation.mutate({ action: 'APPROVE', password: tempPassword })}
            >
              Confirm & Issue Credentials
            </Button>
          </div>
        </div>
      </Dialog>

      {/* Reject Confirmation Modal */}
      <Dialog
        isOpen={isRejectOpen}
        onClose={() => setIsRejectOpen(false)}
        title="Reject Credential Request"
      >
        <div className="space-y-4">
          <p className="text-sm text-brown/80">
            Please provide a reason for rejecting the credential request for tenant <strong>{activeRequest?.tenantName}</strong>. This explanation will be included in the notification email sent to the requester.
          </p>

          <div className="space-y-1.5">
            <label className="block text-xs font-semibold text-brown uppercase font-mono">
              Rejection Reason
            </label>
            <textarea
              rows={3}
              value={rejectionReason}
              onChange={(e) => setRejectionReason(e.target.value)}
              className="w-full px-3 py-2 rounded-xl border border-sand bg-white text-sm focus:outline-none focus:ring-2 focus:ring-brick"
            />
          </div>

          <div className="flex justify-end gap-3 pt-4 border-t border-sand">
            <Button variant="ghost" onClick={() => setIsRejectOpen(false)}>
              Cancel
            </Button>
            <Button
              variant="danger"
              isLoading={reviewMutation.isPending}
              onClick={() => reviewMutation.mutate({ action: 'REJECT', reason: rejectionReason })}
            >
              Reject & Send Email
            </Button>
          </div>
        </div>
      </Dialog>

      {/* Email Inbox Preview Modal */}
      <EmailInboxModal
        isOpen={isInboxOpen}
        onClose={() => setIsInboxOpen(false)}
        defaultEmailId={activeRequest?.id}
      />
    </div>
  );
};

