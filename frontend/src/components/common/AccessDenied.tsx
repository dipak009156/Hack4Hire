import React from 'react';
import { useNavigate } from 'react-router-dom';
import { ShieldOff } from 'lucide-react';
import { Button } from './Button';
import { useAuth } from '../../context/AuthContext';

interface AccessDeniedProps {
  requiredRole?: string;
  message?: string;
}

export const AccessDenied: React.FC<AccessDeniedProps> = ({
  requiredRole,
  message,
}) => {
  const navigate = useNavigate();
  const { session } = useAuth();

  return (
    <div className="min-h-[60vh] flex items-center justify-center p-8">
      <div className="max-w-md w-full text-center space-y-6">
        {/* Icon */}
        <div className="w-20 h-20 rounded-2xl bg-brick/10 border border-brick/20 flex items-center justify-center mx-auto">
          <ShieldOff className="w-10 h-10 text-brick" />
        </div>

        {/* Heading */}
        <div className="space-y-2">
          <h1 className="text-2xl font-bold font-fraunces text-brown">
            Access Denied
          </h1>
          <p className="text-sm text-brown/70 leading-relaxed">
            {message ||
              `You don't have permission to view this page.`}
          </p>
        </div>

        {/* Role info */}
        <div className="p-4 rounded-xl bg-sand/30 border border-sand text-left space-y-2">
          <div className="flex items-center justify-between text-xs font-mono">
            <span className="text-brown/60 uppercase tracking-wider font-semibold">Your role</span>
            <span className="bg-espresso text-cream px-2 py-0.5 rounded font-bold text-[11px] uppercase">
              {session?.roles.join(', ') || 'UNKNOWN'}
            </span>
          </div>
          {requiredRole && (
            <div className="flex items-center justify-between text-xs font-mono">
              <span className="text-brown/60 uppercase tracking-wider font-semibold">Required role</span>
              <span className="bg-brick/15 text-brick px-2 py-0.5 rounded font-bold text-[11px] uppercase">
                {requiredRole}
              </span>
            </div>
          )}
          <div className="flex items-center justify-between text-xs font-mono">
            <span className="text-brown/60 uppercase tracking-wider font-semibold">Tenant</span>
            <span className="text-brown font-medium">{session?.tenantId}</span>
          </div>
        </div>

        {/* Action */}
        <div className="flex gap-3 justify-center">
          <Button variant="ghost" onClick={() => navigate(-1)}>
            Go Back
          </Button>
          <Button variant="primary" onClick={() => navigate('/')}>
            Dashboard
          </Button>
        </div>

        <p className="text-[11px] text-brown/40 font-mono">
          Contact your tenant administrator if you believe this is an error.
        </p>
      </div>
    </div>
  );
};

