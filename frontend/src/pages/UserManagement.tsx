import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiRequest, ApiError } from '../api/client';
import { Card } from '../components/common/Card';
import { Button } from '../components/common/Button';
import { Dialog } from '../components/common/Dialog';
import { Skeleton } from '../components/common/Skeleton';
import { useToast } from '../components/common/Toast';
import { useAuth } from '../context/AuthContext';
import {
  Users,
  UserPlus,
  ShieldCheck,
  ShieldAlert,
  User,
  Pencil,
  UserX,
  UserCheck,
  Building2,
  Lock,
  AlertCircle,
  CheckCircle2,
} from 'lucide-react';

// ─── Types ────────────────────────────────────────────────────────────────────

interface UserRecord {
  id: string;
  tenantId: string;
  username: string;
  roles: string[];
  department: string | null;
  deviceTrustLevel: string;
  active: boolean;
  createdAt: string;
}

const ALL_ROLES = ['ADMIN', 'USER', 'AUDITOR'] as const;
type Role = typeof ALL_ROLES[number];

const ROLE_COLOURS: Record<string, string> = {
  ADMIN:   'bg-terracotta/15 text-terracotta border-terracotta/30',
  USER:    'bg-olive/15 text-olive border-olive/30',
  AUDITOR: 'bg-amber/15 text-amber border-amber/30',
};

// ─── Helpers ─────────────────────────────────────────────────────────────────

function RoleBadge({ role }: { role: string }) {
  return (
    <span className={`inline-block px-2 py-0.5 rounded text-[11px] font-bold uppercase tracking-wider border ${ROLE_COLOURS[role] ?? 'bg-sand/30 text-brown'}`}>
      {role}
    </span>
  );
}

// ─── Main Component ───────────────────────────────────────────────────────────

export const UserManagement: React.FC = () => {
  const queryClient = useQueryClient();
  const { showToast } = useToast();
  const { session } = useAuth();

  // Dialog state
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [editingUser, setEditingUser] = useState<UserRecord | null>(null);

  // Create form
  const [newUsername, setNewUsername]   = useState('');
  const [newPassword, setNewPassword]   = useState('');
  const [newRoles, setNewRoles]         = useState<Role[]>(['USER']);
  const [newDept, setNewDept]           = useState('');
  const [newTrust, setNewTrust]         = useState<'HIGH' | 'STANDARD'>('STANDARD');
  const [createError, setCreateError]   = useState<string | null>(null);

  // Edit form
  const [editRoles, setEditRoles]       = useState<Role[]>([]);
  const [editDept, setEditDept]         = useState('');
  const [editTrust, setEditTrust]       = useState<'HIGH' | 'STANDARD'>('STANDARD');
  const [editPassword, setEditPassword] = useState('');
  const [editError, setEditError]       = useState<string | null>(null);

  // ── Queries ────────────────────────────────────────────────────────────────

  const { data: users, isLoading } = useQuery({
    queryKey: ['admin-users'],
    queryFn: () => apiRequest<UserRecord[]>('/admin/users'),
  });

  // ── Mutations ─────────────────────────────────────────────────────────────

  const createMutation = useMutation({
    mutationFn: () => apiRequest<UserRecord>('/admin/users', {
      method: 'POST',
      body: JSON.stringify({
        username: newUsername.trim(),
        password: newPassword,
        roles: newRoles,
        department: newDept.trim() || null,
        deviceTrustLevel: newTrust,
      }),
    }),
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: ['admin-users'] });
      showToast({ type: 'success', message: `User "${data.username}" created with role ${data.roles.join(', ')}.` });
      setIsCreateOpen(false);
      resetCreateForm();
    },
    onError: (err) => {
      if (err instanceof ApiError) {
        setCreateError(err.problem.detail || err.problem.title || 'Failed to create user');
      } else {
        setCreateError('An unexpected error occurred');
      }
    },
  });

  const updateMutation = useMutation({
    mutationFn: (userId: string) => apiRequest<UserRecord>(`/admin/users/${userId}`, {
      method: 'PATCH',
      body: JSON.stringify({
        roles: editRoles,
        department: editDept.trim() || null,
        deviceTrustLevel: editTrust,
        newPassword: editPassword.length >= 8 ? editPassword : undefined,
      }),
    }),
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: ['admin-users'] });
      showToast({ type: 'success', message: `User "${data.username}" updated.` });
      setEditingUser(null);
    },
    onError: (err) => {
      if (err instanceof ApiError) {
        setEditError(err.problem.detail || err.problem.title || 'Failed to update user');
      } else {
        setEditError('An unexpected error occurred');
      }
    },
  });

  const toggleActiveMutation = useMutation({
    mutationFn: async ({ userId, active }: { userId: string; active: boolean }): Promise<void> => {
      if (active) {
        await apiRequest<void>(`/admin/users/${userId}`, { method: 'DELETE' });
      } else {
        await apiRequest<UserRecord>(`/admin/users/${userId}`, {
          method: 'PATCH',
          body: JSON.stringify({ active: true }),
        });
      }
    },
    onSuccess: (_data, vars) => {
      queryClient.invalidateQueries({ queryKey: ['admin-users'] });
      showToast({
        type: vars.active ? 'info' : 'success',
        message: vars.active ? 'User deactivated.' : 'User reactivated.',
      });
    },
    onError: (err) => {
      if (err instanceof ApiError) {
        showToast({ type: 'error', message: err.problem.detail || 'Operation failed' });
      }
    },
  });

  // ── Helpers ───────────────────────────────────────────────────────────────

  const resetCreateForm = () => {
    setNewUsername(''); setNewPassword(''); setNewRoles(['USER']);
    setNewDept(''); setNewTrust('STANDARD'); setCreateError(null);
  };

  const openEdit = (user: UserRecord) => {
    setEditingUser(user);
    setEditRoles(user.roles as Role[]);
    setEditDept(user.department ?? '');
    setEditTrust(user.deviceTrustLevel as 'HIGH' | 'STANDARD');
    setEditPassword('');
    setEditError(null);
  };

  const toggleRole = (role: Role, current: Role[], setter: (r: Role[]) => void) => {
    setter(current.includes(role) ? current.filter(r => r !== role) : [...current, role]);
  };

  // ── Counts ────────────────────────────────────────────────────────────────

  const activeCount  = users?.filter(u => u.active).length  ?? 0;
  const adminCount   = users?.filter(u => u.roles.includes('ADMIN')).length ?? 0;
  const auditorCount = users?.filter(u => u.roles.includes('AUDITOR')).length ?? 0;

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-3xl font-extrabold font-fraunces text-brown tracking-tight">
            User Management
          </h1>
          <p className="text-sm text-brown/70 mt-1">
            Provision users within <span className="font-mono font-semibold">{session?.tenantId}</span>. Tenant boundary is enforced — you cannot affect other tenants.
          </p>
        </div>
        <Button variant="primary" size="md" onClick={() => { resetCreateForm(); setIsCreateOpen(true); }}>
          <UserPlus className="w-4 h-4 mr-2" />
          Add User
        </Button>
      </div>

      {/* Stat Cards */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        {[
          { label: 'Total Users',  value: users?.length ?? 0, icon: Users,       colour: 'text-brown     bg-sand/30' },
          { label: 'Active',       value: activeCount,         icon: CheckCircle2, colour: 'text-olive     bg-olive/10' },
          { label: 'Admins',       value: adminCount,          icon: ShieldCheck,  colour: 'text-terracotta bg-terracotta/10' },
          { label: 'Auditors',     value: auditorCount,        icon: ShieldAlert,  colour: 'text-amber     bg-amber/10' },
        ].map(({ label, value, icon: Icon, colour }) => (
          <Card key={label} className="flex items-center gap-3 p-4">
            <div className={`w-10 h-10 rounded-xl flex items-center justify-center shrink-0 ${colour}`}>
              <Icon className="w-5 h-5" />
            </div>
            <div>
              <div className="text-xs font-semibold text-brown/60 uppercase font-mono">{label}</div>
              <div className="text-2xl font-bold font-fraunces text-brown">{value}</div>
            </div>
          </Card>
        ))}
      </div>

      {/* Users Table */}
      <Card className="p-0 overflow-hidden">
        <div className="px-5 py-3.5 border-b border-sand bg-sand/20 flex items-center justify-between">
          <span className="text-xs font-bold uppercase tracking-wider text-brown font-mono">
            Tenant Users
          </span>
          <span className="text-xs text-brown/50 font-mono">
            Tenant-scoped · Password hashes never exposed
          </span>
        </div>

        {isLoading ? (
          <div className="p-6 space-y-3">
            {[1,2,3,4].map(i => <Skeleton key={i} className="h-12 w-full" />)}
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse text-sm">
              <thead>
                <tr className="border-b border-sand text-xs font-semibold uppercase tracking-wider text-brown/70 font-mono">
                  <th className="py-3 px-4">User</th>
                  <th className="py-3 px-4">Roles</th>
                  <th className="py-3 px-4">Department</th>
                  <th className="py-3 px-4">Trust</th>
                  <th className="py-3 px-4">Status</th>
                  <th className="py-3 px-4">Created</th>
                  <th className="py-3 px-4 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-sand">
                {users?.map(user => (
                  <tr key={user.id} className={`transition-colors hover:bg-sand/10 ${!user.active ? 'opacity-50' : ''}`}>
                    <td className="py-3 px-4">
                      <div className="flex items-center gap-2">
                        <div className="w-7 h-7 rounded-full bg-terracotta/10 text-terracotta flex items-center justify-center shrink-0">
                          <User className="w-3.5 h-3.5" />
                        </div>
                        <div>
                          <div className="font-semibold text-brown text-sm">{user.username}</div>
                          <div className="text-[10px] text-brown/40 font-mono">{user.id.slice(0, 8)}...</div>
                        </div>
                      </div>
                    </td>
                    <td className="py-3 px-4">
                      <div className="flex flex-wrap gap-1">
                        {user.roles.map(r => <RoleBadge key={r} role={r} />)}
                      </div>
                    </td>
                    <td className="py-3 px-4 text-xs text-brown/70 font-mono">
                      {user.department ?? '—'}
                    </td>
                    <td className="py-3 px-4">
                      <div className={`flex items-center gap-1 text-xs font-semibold ${user.deviceTrustLevel === 'HIGH' ? 'text-olive' : 'text-amber'}`}>
                        {user.deviceTrustLevel === 'HIGH'
                          ? <ShieldCheck className="w-3.5 h-3.5" />
                          : <ShieldAlert className="w-3.5 h-3.5" />}
                        {user.deviceTrustLevel}
                      </div>
                    </td>
                    <td className="py-3 px-4">
                      <span className={`inline-block px-2 py-0.5 rounded-full text-[11px] font-bold uppercase border ${
                        user.active
                          ? 'bg-olive/10 text-olive border-olive/30'
                          : 'bg-brick/10 text-brick border-brick/30'
                      }`}>
                        {user.active ? 'Active' : 'Inactive'}
                      </span>
                    </td>
                    <td className="py-3 px-4 text-xs text-brown/50 font-mono">
                      {new Date(user.createdAt).toLocaleDateString()}
                    </td>
                    <td className="py-3 px-4">
                      <div className="flex items-center gap-1 justify-end">
                        <Button
                          variant="ghost" size="sm"
                          onClick={() => openEdit(user)}
                          title="Edit user"
                        >
                          <Pencil className="w-3.5 h-3.5" />
                        </Button>
                        <Button
                          variant="ghost" size="sm"
                          onClick={() => toggleActiveMutation.mutate({ userId: user.id, active: user.active })}
                          disabled={user.id === session?.roles[0]} // can't deactivate self
                          title={user.active ? 'Deactivate' : 'Reactivate'}
                          className={user.active ? 'text-brick hover:bg-brick/10' : 'text-olive hover:bg-olive/10'}
                        >
                          {user.active ? <UserX className="w-3.5 h-3.5" /> : <UserCheck className="w-3.5 h-3.5" />}
                        </Button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      {/* ── Create User Dialog ─────────────────────────────────────────────── */}
      <Dialog
        isOpen={isCreateOpen}
        onClose={() => setIsCreateOpen(false)}
        title="Add New User"
        description="Create a new user in your tenant. They can log in immediately with these credentials."
      >
        <div className="space-y-4">
          {createError && (
            <div className="p-3 rounded-lg bg-brick/10 border border-brick/30 text-brick text-xs flex items-start gap-2">
              <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" />
              <span>{createError}</span>
            </div>
          )}

          {/* Username */}
          <div>
            <label className="block text-xs font-semibold uppercase tracking-wider text-brown/80 mb-1.5 font-mono">Username</label>
            <div className="relative">
              <User className="w-4 h-4 text-brown/40 absolute left-3 top-2.5" />
              <input
                type="text" required value={newUsername}
                onChange={e => { setNewUsername(e.target.value); setCreateError(null); }}
                placeholder="e.g. john.doe"
                className="w-full pl-9 pr-4 py-2 rounded-xl border border-sand bg-white/90 text-sm focus:outline-none focus:ring-2 focus:ring-terracotta"
              />
            </div>
          </div>

          {/* Password */}
          <div>
            <label className="block text-xs font-semibold uppercase tracking-wider text-brown/80 mb-1.5 font-mono">Password</label>
            <div className="relative">
              <Lock className="w-4 h-4 text-brown/40 absolute left-3 top-2.5" />
              <input
                type="password" required value={newPassword}
                onChange={e => setNewPassword(e.target.value)}
                placeholder="Min 8 characters"
                className="w-full pl-9 pr-4 py-2 rounded-xl border border-sand bg-white/90 text-sm focus:outline-none focus:ring-2 focus:ring-terracotta font-mono"
              />
            </div>
          </div>

          {/* Roles */}
          <div>
            <label className="block text-xs font-semibold uppercase tracking-wider text-brown/80 mb-2 font-mono">Role(s)</label>
            <div className="flex gap-2 flex-wrap">
              {ALL_ROLES.map(role => {
                const active = newRoles.includes(role);
                return (
                  <button
                    key={role} type="button"
                    onClick={() => toggleRole(role, newRoles, setNewRoles)}
                    className={`px-3 py-1.5 rounded-lg text-xs font-bold uppercase border transition-all ${
                      active ? ROLE_COLOURS[role] + ' shadow-sm' : 'bg-white text-brown/60 border-sand hover:border-sand/80'
                    }`}
                  >
                    {role}
                  </button>
                );
              })}
            </div>
            <p className="text-[11px] text-brown/50 mt-1">Select one or more roles.</p>
          </div>

          {/* Department + Trust */}
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-semibold uppercase tracking-wider text-brown/80 mb-1.5 font-mono">Department</label>
              <div className="relative">
                <Building2 className="w-4 h-4 text-brown/40 absolute left-3 top-2.5" />
                <input
                  type="text" value={newDept}
                  onChange={e => setNewDept(e.target.value)}
                  placeholder="e.g. engineering"
                  className="w-full pl-9 pr-3 py-2 rounded-xl border border-sand bg-white/90 text-sm focus:outline-none focus:ring-2 focus:ring-terracotta"
                />
              </div>
            </div>
            <div>
              <label className="block text-xs font-semibold uppercase tracking-wider text-brown/80 mb-1.5 font-mono">Device Trust</label>
              <select
                value={newTrust}
                onChange={e => setNewTrust(e.target.value as 'HIGH' | 'STANDARD')}
                className="w-full px-3 py-2 rounded-xl border border-sand bg-white/90 text-sm focus:outline-none focus:ring-2 focus:ring-terracotta"
              >
                <option value="STANDARD">STANDARD</option>
                <option value="HIGH">HIGH</option>
              </select>
            </div>
          </div>

          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="ghost" onClick={() => setIsCreateOpen(false)}>Cancel</Button>
            <Button
              variant="primary"
              isLoading={createMutation.isPending}
              onClick={() => createMutation.mutate()}
              disabled={!newUsername.trim() || newPassword.length < 8 || newRoles.length === 0}
            >
              <UserPlus className="w-4 h-4 mr-1.5" />
              Create User
            </Button>
          </div>
        </div>
      </Dialog>

      {/* ── Edit User Dialog ───────────────────────────────────────────────── */}
      <Dialog
        isOpen={!!editingUser}
        onClose={() => setEditingUser(null)}
        title={`Edit — ${editingUser?.username}`}
        description="Update roles, department, trust level, or set a new password."
      >
        {editingUser && (
          <div className="space-y-4">
            {editError && (
              <div className="p-3 rounded-lg bg-brick/10 border border-brick/30 text-brick text-xs flex items-start gap-2">
                <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" />
                <span>{editError}</span>
              </div>
            )}

            {/* Roles */}
            <div>
              <label className="block text-xs font-semibold uppercase tracking-wider text-brown/80 mb-2 font-mono">Roles</label>
              <div className="flex gap-2 flex-wrap">
                {ALL_ROLES.map(role => {
                  const active = editRoles.includes(role);
                  return (
                    <button
                      key={role} type="button"
                      onClick={() => toggleRole(role, editRoles, setEditRoles)}
                      className={`px-3 py-1.5 rounded-lg text-xs font-bold uppercase border transition-all ${
                        active ? ROLE_COLOURS[role] + ' shadow-sm' : 'bg-white text-brown/60 border-sand hover:border-sand/80'
                      }`}
                    >
                      {role}
                    </button>
                  );
                })}
              </div>
            </div>

            {/* Department + Trust */}
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-brown/80 mb-1.5 font-mono">Department</label>
                <input
                  type="text" value={editDept}
                  onChange={e => setEditDept(e.target.value)}
                  className="w-full px-3 py-2 rounded-xl border border-sand bg-white/90 text-sm focus:outline-none focus:ring-2 focus:ring-terracotta"
                />
              </div>
              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-brown/80 mb-1.5 font-mono">Device Trust</label>
                <select
                  value={editTrust}
                  onChange={e => setEditTrust(e.target.value as 'HIGH' | 'STANDARD')}
                  className="w-full px-3 py-2 rounded-xl border border-sand bg-white/90 text-sm focus:outline-none focus:ring-2 focus:ring-terracotta"
                >
                  <option value="STANDARD">STANDARD</option>
                  <option value="HIGH">HIGH</option>
                </select>
              </div>
            </div>

            {/* New Password (optional) */}
            <div>
              <label className="block text-xs font-semibold uppercase tracking-wider text-brown/80 mb-1.5 font-mono">
                New Password <span className="text-brown/40 normal-case font-normal">(leave blank to keep current)</span>
              </label>
              <div className="relative">
                <Lock className="w-4 h-4 text-brown/40 absolute left-3 top-2.5" />
                <input
                  type="password" value={editPassword}
                  onChange={e => setEditPassword(e.target.value)}
                  placeholder="Min 8 characters"
                  className="w-full pl-9 pr-4 py-2 rounded-xl border border-sand bg-white/90 text-sm focus:outline-none focus:ring-2 focus:ring-terracotta font-mono"
                />
              </div>
            </div>

            <div className="flex justify-end gap-2 pt-2">
              <Button type="button" variant="ghost" onClick={() => setEditingUser(null)}>Cancel</Button>
              <Button
                variant="primary"
                isLoading={updateMutation.isPending}
                onClick={() => updateMutation.mutate(editingUser.id)}
                disabled={editRoles.length === 0}
              >
                Save Changes
              </Button>
            </div>
          </div>
        )}
      </Dialog>
    </div>
  );
};
