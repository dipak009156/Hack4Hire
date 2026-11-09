import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiRequest } from '../../api/client';
import { EmailNotificationResponse } from '../../api/types';
import { Dialog } from './Dialog';
import { Button } from './Button';
import { Mail, RefreshCw, CheckCircle, Clock, ShieldAlert, ArrowRight, ExternalLink } from 'lucide-react';
import { useNavigate } from 'react-router-dom';

interface EmailInboxModalProps {
  isOpen: boolean;
  onClose: () => void;
  defaultEmailId?: string;
}

export const EmailInboxModal: React.FC<EmailInboxModalProps> = ({ isOpen, onClose, defaultEmailId }) => {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [selectedId, setSelectedId] = useState<string | null>(defaultEmailId || null);

  const { data: emails, isLoading, refetch, isFetching } = useQuery({
    queryKey: ['simulated-emails'],
    queryFn: () => apiRequest<EmailNotificationResponse[]>('/credential-requests/emails'),
    enabled: isOpen,
    refetchInterval: isOpen ? 4000 : false,
  });

  const markReadMutation = useMutation({
    mutationFn: (id: string) => apiRequest<void>(`/credential-requests/emails/${id}/read`, { method: 'POST' }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['simulated-emails'] });
    },
  });

  const activeEmail = emails?.find((e) => e.id === selectedId) || emails?.[0];

  const handleSelectEmail = (email: EmailNotificationResponse) => {
    setSelectedId(email.id);
    if (!email.isRead) {
      markReadMutation.mutate(email.id);
    }
  };

  return (
    <Dialog
      isOpen={isOpen}
      onClose={onClose}
      title="Secure Notification Center & Email Outbox"
      maxWidth="5xl"
    >
      <div className="flex flex-col h-[650px] -m-6">
        {/* Subheader */}
        <div className="px-6 py-3 bg-sand/20 border-b border-sand flex items-center justify-between text-xs text-brown/70">
          <div className="flex items-center gap-2">
            <Mail className="w-4 h-4 text-terracotta" />
            <span>Platform Mail Delivery &amp; Outbox Log (Real SMTP &amp; Dispatched Notifications)</span>
          </div>
          <Button
            size="sm"
            variant="ghost"
            onClick={() => refetch()}
            isLoading={isFetching}
            className="text-xs"
          >
            <RefreshCw className="w-3.5 h-3.5 mr-1" />
            Refresh Mailbox
          </Button>
        </div>

        {/* Content split */}
        <div className="flex flex-1 overflow-hidden">
          {/* Email List Column */}
          <div className="w-2/5 border-r border-sand overflow-y-auto bg-sand/5">
            {isLoading ? (
              <div className="p-8 text-center text-sm text-brown/50">Loading notifications...</div>
            ) : !emails || emails.length === 0 ? (
              <div className="p-8 text-center text-sm text-brown/50 flex flex-col items-center gap-2">
                <Mail className="w-8 h-8 opacity-40 text-brown" />
                <p>No notifications dispatched yet.</p>
                <p className="text-xs text-brown/40">Submit a credential request to see emails appear here.</p>
              </div>
            ) : (
              <div className="divide-y divide-sand/60">
                {emails.map((email) => {
                  const isSelected = activeEmail?.id === email.id;
                  const isAlert = email.emailType === 'ADMIN_ALERT';
                  const isApproved = email.emailType === 'APPROVAL_NOTICE';

                  return (
                    <button
                      key={email.id}
                      onClick={() => handleSelectEmail(email)}
                      className={`w-full text-left p-4 transition-colors flex flex-col gap-1.5 ${
                        isSelected
                          ? 'bg-cream border-l-4 border-terracotta'
                          : 'hover:bg-sand/20 bg-white/40'
                      }`}
                    >
                      <div className="flex items-center justify-between gap-2">
                        <span
                          className={`text-[10px] font-mono uppercase font-bold px-2 py-0.5 rounded ${
                            isApproved
                              ? 'bg-olive/15 text-olive border border-olive/30'
                              : isAlert
                              ? 'bg-terracotta/15 text-terracotta border border-terracotta/30'
                              : 'bg-amber/15 text-amber border border-amber/30'
                          }`}
                        >
                          {email.emailType.replace('_', ' ')}
                        </span>
                        <span className="text-[11px] font-mono text-brown/50">
                          {new Date(email.sentAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                        </span>
                      </div>

                      <div className="text-xs font-bold text-brown truncate">{email.subject}</div>

                      <div className="text-[11px] text-brown/70 flex items-center justify-between">
                        <span className="truncate">To: {email.recipientName} ({email.recipientEmail})</span>
                        {!email.isRead && (
                          <span className="w-2 h-2 rounded-full bg-terracotta shrink-0" title="Unread" />
                        )}
                      </div>
                    </button>
                  );
                })}
              </div>
            )}
          </div>

          {/* Email Preview Column */}
          <div className="w-3/5 flex flex-col bg-cream/30 overflow-hidden">
            {activeEmail ? (
              <div className="flex flex-col h-full overflow-hidden">
                {/* Email Header */}
                <div className="p-5 border-b border-sand bg-white/60 space-y-2">
                  <div className="flex items-start justify-between gap-4">
                    <h3 className="text-base font-bold font-fraunces text-brown leading-snug">
                      {activeEmail.subject}
                    </h3>
                    {activeEmail.requestId && (
                      <Button
                        size="sm"
                        variant="secondary"
                        onClick={() => {
                          onClose();
                          navigate(`/review-request/${activeEmail.requestId}`);
                        }}
                        className="text-xs shrink-0"
                      >
                        <ExternalLink className="w-3.5 h-3.5 mr-1" />
                        Open Review Page
                      </Button>
                    )}
                  </div>

                  <div className="grid grid-cols-2 gap-2 text-xs text-brown/70 font-mono">
                    <div>
                      <span className="text-brown/40">From: </span>
                      <span>security-ops@quantumlock.io</span>
                    </div>
                    <div>
                      <span className="text-brown/40">To: </span>
                      <span className="text-terracotta font-semibold">{activeEmail.recipientEmail}</span>
                    </div>
                    <div>
                      <span className="text-brown/40">Date: </span>
                      <span>{new Date(activeEmail.sentAt).toLocaleString()}</span>
                    </div>
                    <div>
                      <span className="text-brown/40">Status: </span>
                      <span className="text-olive font-semibold">Delivered (Simulated)</span>
                    </div>
                  </div>
                </div>

                {/* Email Body rendered */}
                <div className="flex-1 p-6 overflow-y-auto bg-sand/10">
                  <div
                    className="email-content-wrapper shadow-sm rounded-xl overflow-hidden bg-white"
                    dangerouslySetInnerHTML={{ __html: activeEmail.bodyHtml }}
                    onClick={(e) => {
                      const target = e.target as HTMLElement;
                      const anchor = target.closest('a');
                      if (anchor) {
                        const href = anchor.getAttribute('href');
                        if (href && href.startsWith('/')) {
                          e.preventDefault();
                          onClose();
                          navigate(href);
                        }
                      }
                    }}
                  />
                </div>
              </div>
            ) : (
              <div className="h-full flex items-center justify-center p-8 text-center text-sm text-brown/50">
                Select an email from the left pane to view its content.
              </div>
            )}
          </div>
        </div>
      </div>
    </Dialog>
  );
};
