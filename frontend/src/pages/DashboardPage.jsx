import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Briefcase,
  Plus,
  Search,
  Filter,
  HardDrive,
  BookOpen,
  Share2,
  Scale,
  UserCheck,
  Building2,
  CheckCircle2,
  AlertCircle,
  X,
} from 'lucide-react';
import { casesApi } from '../api/vaultService';
import { extractErrorMessage } from '../api/client';
import { useAuth } from '../context/AuthContext';

const STATUS_STYLES = {
  ACTIVE: 'bg-emerald-950/10 text-emerald-900 border-emerald-700/30',
  PENDING_TRIAL: 'bg-amber-900/15 text-[#7C4A2D] border-[#B0754F]/50',
  SETTLED: 'bg-[#EDE0D1] text-[#3E2A1E] border-[#C9B29B]',
  ARCHIVED: 'bg-stone-200 text-stone-700 border-stone-300',
};

const ROLE_NAMES = {
  ROLE_ADMIN: 'Administrator',
  ROLE_ATTORNEY: 'Lead Attorney',
  ROLE_FORENSIC_EXAMINER: 'Forensic Examiner',
  ROLE_CLIENT: 'Client',
};

export default function DashboardPage() {
  const { user, mode, pushNotification } = useAuth();
  const navigate = useNavigate();

  const [cases, setCases] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  // Filters
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [typeFilter, setTypeFilter] = useState('');

  // Create Case Modal
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [formData, setFormData] = useState({
    caseNumber: 'CR-2026-0142',
    caseTitle: '',
    caseType: 'PROSECUTION',
    leadAttorneyId: user?.userId || 2,
    clientId: '',
    opposingPartyName: '',
    opposingCounselName: '',
    opposingCounselEmail: '',
  });
  const [submitting, setSubmitting] = useState(false);

  const fetchCases = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const data = await casesApi.getCases({
        status: statusFilter || undefined,
        type: typeFilter || undefined,
        search: search || undefined,
      });
      setCases(data.content || (Array.isArray(data) ? data : []));
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }, [statusFilter, typeFilter, search, mode]);

  useEffect(() => {
    fetchCases();
  }, [fetchCases]);

  const handleCreateCase = async (e) => {
    e.preventDefault();
    setSubmitting(true);
    setError('');
    try {
      const payload = {
        ...formData,
        leadAttorneyId: Number(formData.leadAttorneyId),
        clientId: formData.clientId ? Number(formData.clientId) : null,
      };
      const created = await casesApi.createCase(payload);
      pushNotification('Case Created', `Added ${created.caseNumber}: ${created.caseTitle}`, 'success');
      setShowCreateModal(false);
      setFormData({
        caseNumber: `CR-2026-0${Math.floor(100 + Math.random() * 899)}`,
        caseTitle: '',
        caseType: 'PROSECUTION',
        leadAttorneyId: user?.userId || 2,
        clientId: '',
        opposingPartyName: '',
        opposingCounselName: '',
        opposingCounselEmail: '',
      });
      fetchCases();
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setSubmitting(false);
    }
  };

  const handleStatusChange = async (caseId, newStatus) => {
    try {
      await casesApi.updateCaseStatus(caseId, newStatus);
      pushNotification('Case Status Updated', `Case status changed to ${newStatus}`, 'info');
      fetchCases();
    } catch (err) {
      setError(extractErrorMessage(err));
    }
  };

  const canManageCases = user?.role === 'ROLE_ATTORNEY' || user?.role === 'ROLE_ADMIN';

  return (
    <div className="space-y-8">
      {/* Hero Banner in Deep Espresso & Warm Bronze */}
      <div className="espresso-gradient rounded-3xl p-8 text-[#F5EFE6] shadow-xl border border-[#4A3222] relative overflow-hidden">
        <div className="absolute -right-12 -bottom-12 w-72 h-72 rounded-full bg-[#B0754F]/15 blur-2xl pointer-events-none" />
        <div className="relative z-10 flex flex-col lg:flex-row lg:items-center justify-between gap-6">
          <div>
            <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-[#3E2A1E] border border-[#8B5230] text-[#DFC3AC] text-xs font-semibold mb-3">
              <Scale className="w-3.5 h-3.5 text-[#CA9874]" />
              Case & Docket Management
            </div>
            <h1 className="font-serif-legal text-3xl lg:text-4xl font-bold text-white">
              Welcome back, {user?.fullName || 'Counsel'}
            </h1>
            <p className="text-sm text-[#DFC3AC] mt-1.5 max-w-2xl">
              Select a legal case below to view its digital evidence, lab schedule, trial exhibit binders, and opposing counsel discovery packages.
            </p>
          </div>

          {canManageCases && (
            <button
              onClick={() => {
                const maxNum = cases.reduce((max, c) => {
                  const match = String(c.caseNumber || '').match(/(\d+)$/);
                  return match ? Math.max(max, parseInt(match[1], 10)) : max;
                }, 41);
                const nextCode = `CR-2026-${String(maxNum + 1).padStart(4, '0')}`;
                setFormData((prev) => ({
                  ...prev,
                  caseNumber: nextCode,
                  leadAttorneyId: user?.userId || 2,
                }));
                setShowCreateModal(true);
              }}
              className="shrink-0 inline-flex items-center gap-2 px-5 py-3 rounded-xl cognac-gradient hover:opacity-95 text-white font-semibold text-sm shadow-lg border border-[#CA9874]/40 transition cursor-pointer"
            >
              <Plus className="w-4.5 h-4.5" />
              New Legal Case
            </button>
          )}
        </div>

        {/* 4 Summary Metrics */}
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mt-8 pt-6 border-t border-[#3E2A1E]">
          <div className="bg-[#261810]/90 p-4 rounded-2xl border border-[#4A3222]">
            <div className="text-xs text-[#CA9874] uppercase font-semibold tracking-wider">Total Cases</div>
            <div className="font-serif-legal text-3xl font-bold text-white mt-1">{cases.length}</div>
            <div className="text-[11px] text-[#C9B29B] mt-0.5">Assigned to your workspace</div>
          </div>
          <div className="bg-[#261810]/90 p-4 rounded-2xl border border-[#4A3222]">
            <div className="text-xs text-[#CA9874] uppercase font-semibold tracking-wider">Open Proceedings</div>
            <div className="font-serif-legal text-3xl font-bold text-white mt-1">
              {cases.filter((c) => c.status === 'ACTIVE' || c.status === 'PENDING_TRIAL').length}
            </div>
            <div className="text-[11px] text-[#C9B29B] mt-0.5">Active & pending trial</div>
          </div>
          <div className="bg-[#261810]/90 p-4 rounded-2xl border border-[#4A3222]">
            <div className="text-xs text-[#CA9874] uppercase font-semibold tracking-wider">Closed / Settled</div>
            <div className="font-serif-legal text-3xl font-bold text-white mt-1">
              {cases.filter((c) => c.status === 'SETTLED' || c.status === 'ARCHIVED').length}
            </div>
            <div className="text-[11px] text-[#C9B29B] mt-0.5">Completed matters</div>
          </div>
          <div className="bg-[#261810]/90 p-4 rounded-2xl border border-[#4A3222]">
            <div className="text-xs text-[#CA9874] uppercase font-semibold tracking-wider">Your Role</div>
            <div className="text-lg font-bold text-white mt-1.5 truncate">
              {ROLE_NAMES[user?.role] || 'Attorney'}
            </div>
            <div className="text-[11px] text-emerald-400 mt-0.5 flex items-center gap-1">
              <CheckCircle2 className="w-3.5 h-3.5" /> Active Session
            </div>
          </div>
        </div>
      </div>

      {/* Filter & Search Bar */}
      <div className="card-elevated rounded-2xl p-5 bg-white flex flex-col md:flex-row items-center justify-between gap-4">
        <div className="relative flex-1 w-full">
          <Search className="w-4 h-4 text-[#8B5230] absolute left-3.5 top-3" />
          <input
            type="text"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search by case number, title, or opposing party..."
            className="w-full pl-10 pr-4 py-2.5 rounded-xl bg-[#FAF6F0] border border-[#DFC3AC] text-sm focus:outline-none focus:border-[#8B5230] focus:bg-white transition"
          />
        </div>

        <div className="flex items-center gap-3 w-full md:w-auto">
          <div className="flex items-center gap-2 bg-[#FAF6F0] px-3 py-2 rounded-xl border border-[#DFC3AC]">
            <Filter className="w-4 h-4 text-[#8B5230]" />
            <select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              className="bg-transparent text-xs font-semibold text-[#3E2A1E] focus:outline-none"
            >
              <option value="">All Statuses</option>
              <option value="ACTIVE">Active</option>
              <option value="PENDING_TRIAL">Pending Trial</option>
              <option value="SETTLED">Settled</option>
              <option value="ARCHIVED">Archived</option>
            </select>
          </div>

          <div className="flex items-center gap-2 bg-[#FAF6F0] px-3 py-2 rounded-xl border border-[#DFC3AC]">
            <select
              value={typeFilter}
              onChange={(e) => setTypeFilter(e.target.value)}
              className="bg-transparent text-xs font-semibold text-[#3E2A1E] focus:outline-none"
            >
              <option value="">All Case Types</option>
              <option value="PROSECUTION">Prosecution</option>
              <option value="DEFENSE">Defense</option>
            </select>
          </div>
        </div>
      </div>

      {error && (
        <div className="p-4 rounded-2xl bg-red-50 border border-red-200 text-red-900 text-sm flex items-center gap-3">
          <AlertCircle className="w-5 h-5 text-red-700 shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {/* Cases Grid */}
      {loading ? (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          {[1, 2].map((n) => (
            <div key={n} className="card-elevated rounded-3xl p-6 h-64 animate-pulse bg-white" />
          ))}
        </div>
      ) : cases.length === 0 ? (
        <div className="card-elevated rounded-3xl p-12 text-center bg-white">
          <Briefcase className="w-12 h-12 text-[#8B5230] mx-auto mb-3 opacity-80" />
          <h3 className="font-serif-legal text-2xl font-bold text-[#1E130D]">No Cases Found</h3>
          <p className="text-sm text-[#6E3F23] mt-1 max-w-md mx-auto">
            No legal cases match your current search filters.
          </p>
        </div>
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          {cases.map((c) => (
            <div
              key={c.id}
              className="card-elevated rounded-3xl p-6 bg-white flex flex-col justify-between transition-all"
            >
              <div>
                {/* Top Row: Docket Pill + Case Type + Status Dropdown */}
                <div className="flex flex-wrap items-center justify-between gap-2 mb-4">
                  <div className="flex items-center gap-2">
                    <span className="font-mono-hash text-xs font-bold px-3 py-1 rounded-lg bg-[#1E130D] text-[#FAF6F0]">
                      {c.caseNumber}
                    </span>
                    <span
                      className={`text-[11px] font-bold uppercase tracking-wider px-2.5 py-1 rounded-lg border ${
                        c.caseType === 'PROSECUTION'
                          ? 'bg-[#8B5230]/15 text-[#6E3F23] border-[#8B5230]/30'
                          : 'bg-[#3E2A1E]/10 text-[#3E2A1E] border-[#3E2A1E]/25'
                      }`}
                    >
                      {c.caseType}
                    </span>
                  </div>

                  {canManageCases ? (
                    <select
                      value={c.status}
                      onChange={(e) => handleStatusChange(c.id, e.target.value)}
                      className={`text-xs font-bold uppercase tracking-wider px-3 py-1 rounded-full border cursor-pointer focus:outline-none ${
                        STATUS_STYLES[c.status] || STATUS_STYLES.ACTIVE
                      }`}
                    >
                      <option value="ACTIVE">ACTIVE</option>
                      <option value="PENDING_TRIAL">PENDING TRIAL</option>
                      <option value="SETTLED">SETTLED</option>
                      <option value="ARCHIVED">ARCHIVED</option>
                    </select>
                  ) : (
                    <span
                      className={`text-xs font-bold uppercase tracking-wider px-3 py-1 rounded-full border ${
                        STATUS_STYLES[c.status] || STATUS_STYLES.ACTIVE
                      }`}
                    >
                      {c.status.replace('_', ' ')}
                    </span>
                  )}
                </div>

                {/* Case Title */}
                <h3 className="font-serif-legal text-2xl font-bold text-[#1E130D] leading-snug">
                  {c.caseTitle}
                </h3>

                {/* Parties & Counsel Metadata Grid */}
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 mt-4 p-4 rounded-2xl bg-[#FAF6F0] border border-[#EDE0D1]">
                  <div>
                    <div className="text-[10px] font-bold uppercase tracking-wider text-[#8B5230] flex items-center gap-1">
                      <UserCheck className="w-3.5 h-3.5" /> Lead Attorney
                    </div>
                    <div className="text-xs font-semibold text-[#1E130D] mt-0.5">
                      {c.leadAttorneyName || `Attorney #${c.leadAttorneyId}`}
                    </div>
                    <div className="text-[11px] text-[#6E3F23] truncate">{c.leadAttorneyEmail}</div>
                  </div>

                  <div>
                    <div className="text-[10px] font-bold uppercase tracking-wider text-[#8B5230] flex items-center gap-1">
                      <Building2 className="w-3.5 h-3.5" /> Opposing Party & Counsel
                    </div>
                    <div className="text-xs font-semibold text-[#1E130D] mt-0.5 truncate">
                      {c.opposingPartyName || 'Unlisted Party'}
                    </div>
                    <div className="text-[11px] text-[#6E3F23] truncate">
                      {c.opposingCounselName || 'No opposing counsel listed'}
                    </div>
                  </div>
                </div>
              </div>

              {/* Action Bar for This Case */}
              {user?.role !== 'ROLE_CLIENT' && (
                <div className="mt-5 pt-4 border-t border-[#E6D7C3] flex flex-wrap items-center gap-2">
                  <button
                    onClick={() => navigate(`/evidence?caseId=${c.id}`)}
                    className="flex-1 inline-flex items-center justify-center gap-1.5 py-2 px-3 rounded-xl bg-[#1E130D] hover:bg-[#3E2A1E] text-white text-xs font-semibold transition cursor-pointer"
                  >
                    <HardDrive className="w-3.5 h-3.5 text-[#CA9874]" />
                    Evidence Vault
                  </button>
                  <button
                    onClick={() => navigate(`/binders?caseId=${c.id}`)}
                    className="flex-1 inline-flex items-center justify-center gap-1.5 py-2 px-3 rounded-xl bg-[#F5EFE6] hover:bg-[#EDE0D1] text-[#2C1D14] border border-[#DFC3AC] text-xs font-semibold transition cursor-pointer"
                  >
                    <BookOpen className="w-3.5 h-3.5 text-[#8B5230]" />
                    Trial Binders
                  </button>
                  {canManageCases && (
                    <button
                      onClick={() => navigate(`/discovery?caseId=${c.id}`)}
                      className="flex-1 inline-flex items-center justify-center gap-1.5 py-2 px-3 rounded-xl bg-[#F5EFE6] hover:bg-[#EDE0D1] text-[#2C1D14] border border-[#DFC3AC] text-xs font-semibold transition cursor-pointer"
                    >
                      <Share2 className="w-3.5 h-3.5 text-[#8B5230]" />
                      Discovery
                    </button>
                  )}
                </div>
              )}
            </div>
          ))}
        </div>
      )}

      {/* Modal: Create New Legal Case */}
      {showCreateModal && (
        <div className="fixed inset-0 z-50 bg-black/60 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white rounded-3xl max-w-lg w-full overflow-hidden shadow-2xl border border-[#DFC3AC]">
            <div className="espresso-gradient p-6 text-white flex items-center justify-between">
              <div>
                <h3 className="font-serif-legal text-2xl font-bold">New Legal Case</h3>
                <p className="text-xs text-[#DFC3AC]">Enter the court docket and party details below</p>
              </div>
              <button
                onClick={() => setShowCreateModal(false)}
                className="p-1.5 rounded-lg bg-white/10 hover:bg-white/20 text-white"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleCreateCase} className="p-6 space-y-4">
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-semibold text-[#3E2A1E] mb-1">
                    Case Number *
                  </label>
                  <input
                    type="text"
                    required
                    value={formData.caseNumber}
                    onChange={(e) => setFormData({ ...formData, caseNumber: e.target.value })}
                    placeholder="CR-2026-0041"
                    className="w-full px-3.5 py-2 rounded-xl bg-[#FAF6F0] border border-[#DFC3AC] text-sm font-mono-hash"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-[#3E2A1E] mb-1">
                    Case Type *
                  </label>
                  <select
                    value={formData.caseType}
                    onChange={(e) => setFormData({ ...formData, caseType: e.target.value })}
                    className="w-full px-3.5 py-2 rounded-xl bg-[#FAF6F0] border border-[#DFC3AC] text-sm font-semibold"
                  >
                    <option value="PROSECUTION">Prosecution</option>
                    <option value="DEFENSE">Defense</option>
                  </select>
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-[#3E2A1E] mb-1">
                  Case Title *
                </label>
                <input
                  type="text"
                  required
                  value={formData.caseTitle}
                  onChange={(e) => setFormData({ ...formData, caseTitle: e.target.value })}
                  placeholder="e.g., State v. Sterling"
                  className="w-full px-3.5 py-2 rounded-xl bg-[#FAF6F0] border border-[#DFC3AC] text-sm"
                />
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-semibold text-[#3E2A1E] mb-1">
                    Lead Attorney ID *
                  </label>
                  <input
                    type="number"
                    required
                    value={formData.leadAttorneyId}
                    onChange={(e) => setFormData({ ...formData, leadAttorneyId: e.target.value })}
                    className="w-full px-3.5 py-2 rounded-xl bg-[#FAF6F0] border border-[#DFC3AC] text-sm"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-[#3E2A1E] mb-1">
                    Client ID (Optional)
                  </label>
                  <input
                    type="number"
                    value={formData.clientId}
                    onChange={(e) => setFormData({ ...formData, clientId: e.target.value })}
                    placeholder="e.g. 4"
                    className="w-full px-3.5 py-2 rounded-xl bg-[#FAF6F0] border border-[#DFC3AC] text-sm"
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-[#3E2A1E] mb-1">
                  Opposing Party Name
                </label>
                <input
                  type="text"
                  value={formData.opposingPartyName}
                  onChange={(e) => setFormData({ ...formData, opposingPartyName: e.target.value })}
                  placeholder="e.g., Nexus Wire Corp."
                  className="w-full px-3.5 py-2 rounded-xl bg-[#FAF6F0] border border-[#DFC3AC] text-sm"
                />
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-semibold text-[#3E2A1E] mb-1">
                    Opposing Counsel Name
                  </label>
                  <input
                    type="text"
                    value={formData.opposingCounselName}
                    onChange={(e) => setFormData({ ...formData, opposingCounselName: e.target.value })}
                    placeholder="Damien Cross, Esq."
                    className="w-full px-3.5 py-2 rounded-xl bg-[#FAF6F0] border border-[#DFC3AC] text-sm"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-[#3E2A1E] mb-1">
                    Opposing Counsel Email
                  </label>
                  <input
                    type="email"
                    value={formData.opposingCounselEmail}
                    onChange={(e) => setFormData({ ...formData, opposingCounselEmail: e.target.value })}
                    placeholder="d.cross@firm.law"
                    className="w-full px-3.5 py-2 rounded-xl bg-[#FAF6F0] border border-[#DFC3AC] text-sm"
                  />
                </div>
              </div>

              <div className="flex justify-end gap-3 pt-4 border-t border-[#E6D7C3]">
                <button
                  type="button"
                  onClick={() => setShowCreateModal(false)}
                  className="px-4 py-2.5 rounded-xl bg-[#F5EFE6] text-[#3E2A1E] text-xs font-semibold"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={submitting}
                  className="px-5 py-2.5 rounded-xl cognac-gradient text-white text-xs font-semibold shadow-md cursor-pointer"
                >
                  {submitting ? 'Saving...' : 'Create Case'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
