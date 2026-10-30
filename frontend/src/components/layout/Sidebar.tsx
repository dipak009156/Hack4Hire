import React from 'react';
import { NavLink } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import {
  LayoutDashboard,
  FileText,
  Search,
  Share2,
  KeyRound,
  FileCode2,
  ScrollText,
  RotateCcw,
  ShieldAlert,
  Award,
  Shield,
  X,
} from 'lucide-react';

interface SidebarProps {
  onCloseMobile?: () => void;
}

export const Sidebar: React.FC<SidebarProps> = ({ onCloseMobile }) => {
  const { hasRole, hasAnyRole } = useAuth();

  const isAdmin = hasRole('ADMIN');
  const isAuditorOrAdmin = hasAnyRole(['ADMIN', 'AUDITOR']);

  const navItems = [
    { to: '/', label: 'Dashboard', icon: LayoutDashboard, show: true },
    { to: '/documents', label: 'Documents', icon: FileText, show: true },
    { to: '/search', label: 'Search', icon: Search, show: true },
    { to: '/sharing', label: 'Sharing', icon: Share2, show: true },
    { to: '/key-management', label: 'Key Management', icon: KeyRound, show: isAdmin },
    { to: '/policies', label: 'Policies', icon: FileCode2, show: isAdmin },
    { to: '/audit-log', label: 'Audit Log', icon: ScrollText, show: isAuditorOrAdmin },
    { to: '/recovery', label: 'Recovery', icon: RotateCcw, show: isAdmin },
    { to: '/cipherstorm', label: 'CIPHERSTORM Report', icon: ShieldAlert, show: isAdmin },
    { to: '/security-proof', label: 'Security Proof', icon: Award, show: true },
  ];

  return (
    <aside className="w-64 bg-espresso text-cream flex flex-col h-full border-r border-espresso/50 shadow-warm select-none">
      {/* Brand Header */}
      <div className="h-16 px-6 flex items-center justify-between border-b border-white/10">
        <div className="flex items-center gap-2.5">
          <div className="w-9 h-9 rounded-xl bg-terracotta flex items-center justify-center text-cream shadow-md">
            <Shield className="w-5 h-5 fill-current" />
          </div>
          <div>
            <span className="font-fraunces font-extrabold text-base tracking-wide text-cream">
              QUANTUM
            </span>
            <span className="font-fraunces font-normal text-base text-sand/80 ml-1">
              LOCK
            </span>
            <div className="text-[10px] tracking-wider text-sand/60 uppercase font-mono leading-none">
              Zero-Trust
            </div>
          </div>
        </div>
        {onCloseMobile && (
          <button
            onClick={onCloseMobile}
            className="lg:hidden p-1.5 text-sand hover:text-cream rounded-lg hover:bg-white/10 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        )}
      </div>

      {/* Navigation Links */}
      <nav className="flex-1 px-3 py-6 space-y-1.5 overflow-y-auto">
        <div className="px-3 pb-2 text-[11px] font-bold tracking-wider text-sand/50 uppercase font-mono">
          Navigation
        </div>
        {navItems
          .filter(item => item.show)
          .map(item => {
            const Icon = item.icon;
            return (
              <NavLink
                key={item.to}
                to={item.to}
                onClick={onCloseMobile}
                end={item.to === '/'}
                className={({ isActive }) =>
                  `flex items-center gap-3 px-3.5 py-2.5 rounded-xl text-sm font-medium transition-all duration-150 ${
                    isActive
                      ? 'bg-terracotta text-cream font-semibold shadow-warm'
                      : 'text-sand/80 hover:bg-white/10 hover:text-cream'
                  }`
                }
              >
                <Icon className="w-4 h-4 shrink-0" />
                <span className="truncate">{item.label}</span>
              </NavLink>
            );
          })}
      </nav>

      {/* Security Assurance Footer Badge */}
      <div className="p-4 m-3 rounded-xl bg-white/5 border border-white/10 text-xs text-sand/70">
        <div className="flex items-center gap-1.5 font-semibold text-cream mb-1">
          <Shield className="w-3.5 h-3.5 text-olive" />
          <span>High Assurance</span>
        </div>
        <p className="text-[11px] leading-relaxed text-sand/60">
          AES-256-GCM envelope encryption & tamper-evident audit chaining.
        </p>
      </div>
    </aside>
  );
};

