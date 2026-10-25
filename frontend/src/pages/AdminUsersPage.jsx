import React, { useState, useEffect, useCallback } from 'react';
import {
  UserPlus,
  ShieldCheck,
  Search,
  AlertCircle,
  X,
} from 'lucide-react';
import { adminUsersApi } from '../api/vaultService';
import { extractErrorMessage } from '../api/client';
import { useAuth } from '../context/AuthContext';

export default function AdminUsersPage() {
  const { mode, pushNotification } = useAuth();

  const [users, setUsers] = useState([]);
  const [roleFilter, setRoleFilter] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  // Add Staff Modal
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [form, setForm] = useState({
    fullName: '',
    email: '',
    password: 'Vault@2026!Password',
    role: 'ROLE_ATTORNEY',
    phoneNumber: '',
    organizationOrAgency: 'Sterling & Hawthorne Litigation LLP',
    barOrBadgeNumber: '',
  });

  const fetchUsers = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const res = await adminUsersApi.getUsers({
        role: roleFilter || undefined,
        status: statusFilter || undefined,
        search: search || undefined,
      });
      setUsers(res.content || (Array.isArray(res) ? res : []));
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }, [roleFilter, statusFilter, search, mode]);

  useEffect(() => {
    fetchUsers();
  }, [fetchUsers]);

  const handleCreateUser = async (e) => {
    e.preventDefault();
    setError('');
    try {
      const created = await adminUsersApi.createUser(form);
      pushNotification(
        'Team Member Added',
        `Created account for ${created.fullName}`,
        'success'
      );
      setShowCreateModal(false);
      setForm({
        fullName: '',
        email: '',
        password: 'Vault@2026!Password',
        role: 'ROLE_ATTORNEY',
        phoneNumber: '',
        organizationOrAgency: '',
        barOrBadgeNumber: '',
      });
      fetchUsers();
    } catch (err) {
      setError(extractErrorMessage(err));
    }
  };

  const handleRoleUpdate = async (userId, newRole) => {
    try {
      await adminUsersApi.updateUserRole(userId, newRole);
      pushNotification('Role Updated', 'Team member role has been updated.', 'info');
      fetchUsers();
    } catch (err) {
      setError(extractErrorMessage(err));
    }
  };

  const handleStatusToggle = async (u) => {
    const nextStatus = u.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
    try {
      await adminUsersApi.updateUserStatus(u.id, nextStatus);
      pushNotification(
        'Account Status Updated',
        `${u.fullName} is now ${nextStatus.toLowerCase()}`,
        'info'
      );
      fetchUsers();
    } catch (err) {
      setError(extractErrorMessage(err));
    }
  };

  return (
    <div className="space-y-6">
      <div className="espresso-gradient rounded-3xl p-7 text-white shadow-xl border border-[#4A3222] flex flex-col lg:flex-row lg:items-center justify-between gap-6">
        <div>
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-[#3E2A1E] border border-[#8B5230] text-[#DFC3AC] text-xs font-semibold mb-2">
            <ShieldCheck className="w-3.5 h-3.5 text-[#CA9874]" />
            Administration
          </div>
          <h1 className="font-serif-legal text-3xl font-bold">
            Team & Account Management
          </h1>
          <p className="text-xs text-[#DFC3AC] mt-1 max-w-2xl">
            Add attorneys, forensic examiners, and clients, assign their workspace roles, and manage active or inactive accounts.
          </p>
        </div>

        <button
          onClick={() => setShowCreateModal(true)}
          className="shrink-0 inline-flex items-center gap-2 px-5 py-3 rounded-xl cognac-gradient text-white font-semibold text-sm shadow-lg border border-[#CA9874]/40 cursor-pointer"
        >
          <UserPlus className="w-4.5 h-4.5" />
          Add Team Member
        </button>
      </div>

      {/* Filters */}
      <div className="card-elevated rounded-2xl p-5 bg-white flex flex-col md:flex-row gap-4">
        <div className="relative flex-1">
          <Search className="w-4 h-4 text-[#8B5230] absolute left-3.5 top-3" />
          <input
            type="text"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search by name or email..."
            className="w-full pl-10 pr-4 py-2 rounded-xl bg-[#FAF6F0] border border-[#DFC3AC] text-xs"
          />
        </div>
        <select
          value={roleFilter}
          onChange={(e) => setRoleFilter(e.target.value)}
          className="px-3.5 py-2 rounded-xl bg-[#FAF6F0] border border-[#DFC3AC] text-xs font-semibold"
        >
          <option value="">All Roles</option>
          <option value="ROLE_ADMIN">Administrator</option>
          <option value="ROLE_ATTORNEY">Lead Attorney</option>
          <option value="ROLE_FORENSIC_EXAMINER">Forensic Examiner</option>
          <option value="ROLE_CLIENT">Client</option>
        </select>
        <select
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value)}
          className="px-3.5 py-2 rounded-xl bg-[#FAF6F0] border border-[#DFC3AC] text-xs font-semibold"
        >
          <option value="">All Statuses</option>
          <option value="ACTIVE">Active</option>
          <option value="INACTIVE">Inactive</option>
        </select>
      </div>

      {error && (
        <div className="p-4 rounded-2xl bg-red-50 border border-red-200 text-red-900 text-xs flex items-center gap-2.5">
          <AlertCircle className="w-4.5 h-4.5 text-red-700 shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {/* Users Table */}
      <div className="card-elevated rounded-3xl overflow-hidden bg-white">
        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse">
            <thead>
              <tr className="bg-[#FAF6F0] border-b border-[#E6D7C3] text-[11px] font-bold uppercase tracking-wider text-[#6E3F23]">
                <th className="py-3.5 px-5">Name & Email</th>
                <th className="py-3.5 px-5">Organization & License / Badge</th>
                <th className="py-3.5 px-5">Role</th>
                <th className="py-3.5 px-5">Account Status</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[#F5EFE6] text-xs">
              {loading ? (
                <tr>
                  <td colSpan={4} className="p-8 text-center text-[#6E3F23]">
                    Loading accounts...
                  </td>
                </tr>
              ) : (
                users.map((u) => (
                  <tr key={u.id} className="hover:bg-[#FAF6F0]/60 transition">
                    <td className="py-4 px-5">
                      <div className="font-bold text-[#1E130D]">{u.fullName}</div>
                      <div className="text-[#6E3F23]">{u.email}</div>
                    </td>
                    <td className="py-4 px-5">
                      <div className="font-medium text-[#1E130D]">
                        {u.organizationOrAgency || '-'}
                      </div>
                      <div className="text-[11px] text-[#8B5230]">
                        {u.barOrBadgeNumber || 'Not listed'}
                      </div>
                    </td>
                    <td className="py-4 px-5">
                      <select
                        value={u.role}
                        onChange={(e) => handleRoleUpdate(u.id, e.target.value)}
                        className="px-3 py-1.5 rounded-xl bg-[#FAF6F0] border border-[#DFC3AC] font-bold text-[#1E130D] text-xs"
                      >
                        <option value="ROLE_ADMIN">Administrator</option>
                        <option value="ROLE_ATTORNEY">Lead Attorney</option>
                        <option value="ROLE_FORENSIC_EXAMINER">Forensic Examiner</option>
                        <option value="ROLE_CLIENT">Client</option>
                      </select>
                    </td>
                    <td className="py-4 px-5">
                      <button
                        onClick={() => handleStatusToggle(u)}
                        className={`px-3 py-1 rounded-full text-[11px] font-bold uppercase tracking-wider border cursor-pointer ${
                          u.status === 'ACTIVE'
                            ? 'bg-emerald-950/10 text-emerald-900 border-emerald-700/30'
                            : 'bg-red-100 text-red-900 border-red-300'
                        }`}
                      >
                        {u.status === 'ACTIVE' ? 'Active' : 'Inactive'}
                      </button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Modal: Add Team Member */}
      {showCreateModal && (
        <div className="fixed inset-0 z-50 bg-black/60 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white rounded-3xl max-w-md w-full overflow-hidden shadow-2xl border border-[#DFC3AC]">
            <div className="espresso-gradient p-6 text-white flex items-center justify-between">
              <div>
                <h3 className="font-serif-legal text-2xl font-bold">
                  Add Team Member
                </h3>
                <p className="text-xs text-[#DFC3AC]">Create a new staff or client account</p>
              </div>
              <button
                onClick={() => setShowCreateModal(false)}
                className="p-1.5 rounded-lg bg-white/10 hover:bg-white/20 text-white"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleCreateUser} className="p-6 space-y-3.5">
              <div>
                <label className="block text-xs font-semibold text-[#3E2A1E] mb-1">
                  Full Name *
                </label>
                <input
                  type="text"
                  required
                  value={form.fullName}
                  onChange={(e) => setForm({ ...form, fullName: e.target.value })}
                  className="w-full px-3.5 py-2 rounded-xl bg-[#FAF6F0] border border-[#DFC3AC] text-xs"
                />
              </div>
              <div>
                <label className="block text-xs font-semibold text-[#3E2A1E] mb-1">
                  Email Address *
                </label>
                <input
                  type="email"
                  required
                  value={form.email}
                  onChange={(e) => setForm({ ...form, email: e.target.value })}
                  className="w-full px-3.5 py-2 rounded-xl bg-[#FAF6F0] border border-[#DFC3AC] text-xs"
                />
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-[#3E2A1E] mb-1">
                    Role *
                  </label>
                  <select
                    value={form.role}
                    onChange={(e) => setForm({ ...form, role: e.target.value })}
                    className="w-full px-3 py-2 rounded-xl bg-[#FAF6F0] border border-[#DFC3AC] text-xs font-bold"
                  >
                    <option value="ROLE_ATTORNEY">Lead Attorney</option>
                    <option value="ROLE_FORENSIC_EXAMINER">Forensic Examiner</option>
                    <option value="ROLE_ADMIN">Administrator</option>
                    <option value="ROLE_CLIENT">Client</option>
                  </select>
                </div>
                <div>
                  <label className="block text-xs font-semibold text-[#3E2A1E] mb-1">
                    Temporary Password *
                  </label>
                  <input
                    type="text"
                    required
                    value={form.password}
                    onChange={(e) => setForm({ ...form, password: e.target.value })}
                    className="w-full px-3 py-2 rounded-xl bg-[#FAF6F0] border border-[#DFC3AC] text-xs"
                  />
                </div>
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-[#3E2A1E] mb-1">
                    Organization / Firm
                  </label>
                  <input
                    type="text"
                    value={form.organizationOrAgency}
                    onChange={(e) =>
                      setForm({ ...form, organizationOrAgency: e.target.value })
                    }
                    className="w-full px-3 py-2 rounded-xl bg-[#FAF6F0] border border-[#DFC3AC] text-xs"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-[#3E2A1E] mb-1">
                    Bar or Badge Number
                  </label>
                  <input
                    type="text"
                    value={form.barOrBadgeNumber}
                    onChange={(e) => setForm({ ...form, barOrBadgeNumber: e.target.value })}
                    placeholder="NY-BAR-99120"
                    className="w-full px-3 py-2 rounded-xl bg-[#FAF6F0] border border-[#DFC3AC] text-xs"
                  />
                </div>
              </div>

              <div className="flex justify-end gap-3 pt-3 border-t border-[#E6D7C3]">
                <button
                  type="button"
                  onClick={() => setShowCreateModal(false)}
                  className="px-4 py-2 rounded-xl bg-[#F5EFE6] text-[#3E2A1E] text-xs font-semibold"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-5 py-2 rounded-xl cognac-gradient text-white text-xs font-semibold shadow-md cursor-pointer"
                >
                  Create Account
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
