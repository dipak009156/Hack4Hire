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
  Building2,
  UserCircle2,
} from 'lucide-react';

interface SidebarProps {
  onCloseMobile?: () => void;
}

// Role colour map
const ROLE_COLOURS: Record<string, string> = {
  ADMIN:   'bg-terracotta text-cream',
  USER:    'bg-olive/80 text-cream',
  AUDITOR: 'bg-amber/80 text-espresso',
};

export const Sidebar: React.FC<SidebarProps> = ({ onCloseMobile }) => {
  const { hasRole, hasAnyRole, session } = useAuth();

  const isAdmin   = hasRole('ADMIN');
  const isUser    = hasRole('USER');
  const isAuditor = hasRole('AUDITOR');

  // Each nav item has a `show` predicate — derived strictly from role
  const navItems = [
    // ADMIN + USER pages
    { to: '/',               label: 'Dashboard',         icon: LayoutDashboard, show: isAdmin || isUser },
    { to: '/documents',      label: 'Documents',         icon: FileText,        show: isAdmin || isUser },
    { to: '/search',         label: 'Search',            icon: Search,          show: isAdmin || isUser },
    { to: '/sharing',        label: 'Sharing',           icon: Share2,          show: isAdmin || isUser },
    // ADMIN-only pages
    { to: '/key-management', label: 'Key Management',    icon: KeyRound,        show: isAdmin },
    { to: '/policies',       label: 'Policies',          icon: FileCode2,       show: isAdmin },
    // ADMIN + AUDITOR pages
    { to: '/audit-log',      label: 'Audit Log',         icon: ScrollText,      show: isAdmin || isAuditor },
    // ADMIN-only pages (continued)
    { to: '/recovery',       label: 'Recovery',          icon: RotateCcw,       show: isAdmin },
    { to: '/cipherstorm',    label: 'CIPHERSTORM',       icon: ShieldAlert,     show: isAdmin },
    // ADMIN + USER pages
    { to: '/security-proof', label: 'Security Proof',    icon: Award,           show: isAdmin || isUser },
  ];

  const primaryRole = session?.roles[0] ?? 'USER';
  const roleColour  = ROLE_COLOURS[primaryRole] ?? 'bg-espresso text-cream';

  // Section grouping labels for visual hierarchy
  const sections = [
    {
      label: 'Workspace',
      items: navItems.filter(n => ['/', '/documents', '/search', '/sharing'].includes(n.to)),
    },
    {
      label: 'Administration',
      items: navItems.filter(n => ['/key-management', '/policies', '/recovery', '/cipherstorm'].includes(n.to)),
    },
    {
      label: 'Compliance',
      items: navItems.filter(n => ['/audit-log', '/security-proof'].includes(n.to)),
    },
  ];

  return (
    <aside className="w-64 bg-espresso text-cream flex flex-col h-full border-r border-espresso/50 shadow-warm select-none">
      {/* Brand Header */}
      <div className="h-16 px-6 flex items-center justify-between border-b border-white/10 shrink-0">
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

      {/* Navigation Links — grouped by section */}
      <nav className="flex-1 px-3 py-5 space-y-5 overflow-y-auto">
        {sections.map(({ label, items }) => {
          const visible = items.filter(i => i.show);
          if (visible.length === 0) return null;
          return (
            <div key={label}>
              <div className="px-3 pb-1.5 text-[10px] font-bold tracking-widest text-sand/40 uppercase font-mono">
                {label}
              </div>
              <div className="space-y-0.5">
                {visible.map(item => {
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
              </div>
            </div>
          );
        })}
      </nav>

      {/* User Profile Card at the bottom */}
      <div className="p-3 border-t border-white/10 shrink-0">
        <div className="p-3 rounded-xl bg-white/5 border border-white/10 space-y-2">
          {/* Username + role */}
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-full bg-terracotta/20 text-terracotta flex items-center justify-center shrink-0">
              <UserCircle2 className="w-5 h-5" />
            </div>
            <div className="min-w-0 flex-1">
              <div className="text-sm font-semibold text-cream truncate">
                {session?.username ?? '—'}
              </div>
              <span className={`inline-block text-[10px] font-bold uppercase tracking-wider px-1.5 py-0.5 rounded mt-0.5 ${roleColour}`}>
                {primaryRole}
              </span>
            </div>
          </div>

          {/* Tenant */}
          <div className="flex items-center gap-1.5 text-[11px] text-sand/60 font-mono">
            <Building2 className="w-3 h-3 text-sand/40 shrink-0" />
            <span className="truncate" title={session?.tenantId}>
              {session?.tenantId ?? '—'}
            </span>
          </div>
        </div>
      </div>
    </aside>
  );
};
