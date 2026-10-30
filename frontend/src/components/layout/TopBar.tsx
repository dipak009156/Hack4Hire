import React from 'react';
import { useAuth } from '../../context/AuthContext';
import { ShieldCheck, ShieldAlert, LogOut, Menu, Building2, UserCircle2 } from 'lucide-react';
import { Button } from '../common/Button';

interface TopBarProps {
  onOpenMobileMenu: () => void;
}

export const TopBar: React.FC<TopBarProps> = ({ onOpenMobileMenu }) => {
  const { session, logout } = useAuth();

  if (!session) return null;

  const isHighTrust = session.deviceTrust === 'HIGH';

  return (
    <header className="h-16 bg-cream/90 backdrop-blur-md border-b border-sand px-6 flex items-center justify-between sticky top-0 z-30">
      <div className="flex items-center gap-3">
        <button
          onClick={onOpenMobileMenu}
          className="lg:hidden p-2 text-brown hover:bg-sand/30 rounded-lg transition-colors"
          aria-label="Toggle navigation"
        >
          <Menu className="w-5 h-5" />
        </button>

        {/* Tenant Badge — Read ONLY from decoded JWT claim, NEVER editable */}
        <div className="flex items-center gap-2 bg-sand/30 border border-sand px-3 py-1.5 rounded-lg text-xs font-medium text-brown">
          <Building2 className="w-3.5 h-3.5 text-terracotta" />
          <span className="text-brown/60">Tenant:</span>
          <span className="font-semibold text-brown font-mono tracking-tight" title={session.tenantId}>
            {session.tenantId.length > 18 ? `${session.tenantId.slice(0, 8)}...` : session.tenantId}
          </span>
        </div>
      </div>

      <div className="flex items-center gap-3">
        {/* Device Trust Indicator */}
        <div 
          className={`flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold border ${
            isHighTrust 
              ? 'bg-olive/10 text-olive border-olive/30' 
              : 'bg-amber/10 text-amber border-amber/30'
          }`}
          title={`Device Trust Level: ${session.deviceTrust}`}
        >
          {isHighTrust ? (
            <ShieldCheck className="w-3.5 h-3.5 text-olive" />
          ) : (
            <ShieldAlert className="w-3.5 h-3.5 text-amber" />
          )}
          <span className="hidden sm:inline">Trust: {session.deviceTrust}</span>
        </div>

        {/* User Info & Role Chips */}
        <div className="flex items-center gap-2 pl-2 border-l border-sand">
          <div className="w-8 h-8 rounded-full bg-terracotta/10 text-terracotta flex items-center justify-center font-bold text-xs">
            <UserCircle2 className="w-5 h-5" />
          </div>
          <div className="hidden sm:block text-left">
            <div className="text-sm font-semibold text-brown leading-tight">{session.username}</div>
            <div className="flex gap-1 mt-0.5">
              {session.roles.map(role => (
                <span 
                  key={role} 
                  className="inline-block text-[10px] font-bold uppercase tracking-wider bg-espresso text-cream px-1.5 py-0.2 rounded"
                >
                  {role}
                </span>
              ))}
            </div>
          </div>
        </div>

        {/* Logout Button */}
        <Button
          onClick={logout}
          variant="ghost"
          size="sm"
          className="ml-2 text-brown/70 hover:text-brick hover:bg-brick/10"
          title="Sign out (clears in-memory token)"
        >
          <LogOut className="w-4 h-4 sm:mr-1.5" />
          <span className="hidden sm:inline">Logout</span>
        </Button>
      </div>
    </header>
  );
};

