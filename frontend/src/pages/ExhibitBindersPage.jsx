import React, { useState, useEffect, useCallback } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import {
  BookOpen,
  Plus,
  Lock,
  Unlock,
  Trash2,
  Stamp,
  Share2,
  Scale,
  AlertCircle,
  X,
} from 'lucide-react';
import { casesApi, evidenceApi, bindersApi } from '../api/vaultService';
import { extractErrorMessage } from '../api/client';
import { useAuth } from '../context/AuthContext';

export default function ExhibitBindersPage() {
  const { user, mode, pushNotification } = useAuth();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();

  const [cases, setCases] = useState([]);
  const [selectedCaseId, setSelectedCaseId] = useState(searchParams.get('caseId') || '');
  const [binders, setBinders] = useState([]);
  const [evidenceItems, setEvidenceItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  // Create Binder Modal
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [binderForm, setBinderForm] = useState({
    name: '',
    batesPrefix: 'PLTF-01',
  });

  // Add Exhibit Modal
  const [activeBinder, setActiveBinder] = useState(null);
  const [exhibitForm, setExhibitForm] = useState({
    evidenceId: '',
    exhibitLabel: 'Exhibit A',
    pageCount: 15,
  });

  useEffect(() => {
    (async () => {
      try {
        const res = await casesApi.getCases();
        const list = res.content || (Array.isArray(res) ? res : []);
        setCases(list);
        if (!selectedCaseId && list.length > 0) {
          setSelectedCaseId(String(list[0].id));
        }
      } catch (err) {
        setError(extractErrorMessage(err));
      }
    })();
  }, [mode]);

  const fetchBindersAndEvidence = useCallback(async () => {
    if (!selectedCaseId) {
      setLoading(false);
      return;
    }
    setLoading(true);
    setError('');
    try {
      const [bRes, eRes] = await Promise.all([
        bindersApi.getBindersByCase(selectedCaseId),
        evidenceApi.getEvidenceByCase(selectedCaseId),
      ]);
      setBinders(bRes.content || (Array.isArray(bRes) ? bRes : []));
      const evList = eRes.content || (Array.isArray(eRes) ? eRes : []);
      setEvidenceItems(evList);
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }, [selectedCaseId, mode]);

  useEffect(() => {
    fetchBindersAndEvidence();
  }, [fetchBindersAndEvidence]);

  const handleCreateBinder = async (e) => {
    e.preventDefault();
    setError('');
    try {
      const created = await bindersApi.createBinder({
        caseId: Number(selectedCaseId),
        ...binderForm,
      });
      pushNotification(
        'Trial Binder Created',
        `Created "${created.name}"`,
        'success'
      );
      setShowCreateModal(false);
      setBinderForm({
        name: '',
        batesPrefix: 'PLTF-01',
      });
      fetchBindersAndEvidence();
    } catch (err) {
      setError(extractErrorMessage(err));
    }
  };

  const handleAddExhibit = async (e) => {
    e.preventDefault();
    if (!activeBinder) return;
    setError('');
    try {
      await bindersApi.addExhibit(activeBinder.id, {
        evidenceId: Number(exhibitForm.evidenceId),
        exhibitLabel: exhibitForm.exhibitLabel,
        pageCount: Number(exhibitForm.pageCount),
      });
      pushNotification(
        'Exhibit Added',
        `Added ${exhibitForm.exhibitLabel} to ${activeBinder.name}`,
        'success'
      );
      setActiveBinder(null);
      fetchBindersAndEvidence();
    } catch (err) {
      setError(extractErrorMessage(err));
    }
  };

  const handleRemoveExhibit = async (binderId, exhibitId) => {
    setError('');
    try {
      await bindersApi.removeExhibit(binderId, exhibitId);
      pushNotification(
        'Exhibit Removed',
        'Exhibit removed and page numbers updated.',
        'info'
      );
      fetchBindersAndEvidence();
    } catch (err) {
      setError(extractErrorMessage(err));
    }
  };

  const handleStatusChange = async (binderId, newStatus) => {
    setError('');
    try {
      await bindersApi.updateStatus(binderId, newStatus);
      pushNotification(
        'Binder Finalized',
        'Trial binder has been finalized for court.',
        'info'
      );
      fetchBindersAndEvidence();
    } catch (err) {
      setError(extractErrorMessage(err));
    }
  };

  const canManageBinders =
    user?.role === 'ROLE_ATTORNEY' || user?.role === 'ROLE_ADMIN';

  return (
    <div className="space-y-6">
      {/* Header Banner */}
      <div className="espresso-gradient rounded-3xl p-7 text-white shadow-xl border border-[#4A3222] flex flex-col lg:flex-row lg:items-center justify-between gap-6">
        <div>
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-[#3E2A1E] border border-[#8B5230] text-[#DFC3AC] text-xs font-semibold mb-2">
            <Stamp className="w-3.5 h-3.5 text-[#CA9874]" />
            Courtroom Preparation
          </div>
          <h1 className="font-serif-legal text-3xl font-bold">
            Trial Exhibit Binders
          </h1>
          <p className="text-xs text-[#DFC3AC] mt-1 max-w-2xl">
            Organize case evidence into numbered trial binders for the courtroom and finalize them when ready for presentation or service.
          </p>
        </div>

        {canManageBinders && (
          <button
            onClick={() => setShowCreateModal(true)}
            className="shrink-0 inline-flex items-center gap-2 px-5 py-3 rounded-xl cognac-gradient hover:opacity-95 text-white font-semibold text-sm shadow-lg border border-[#CA9874]/40 transition cursor-pointer"
          >
            <Plus className="w-4.5 h-4.5" />
            New Trial Binder
          </button>
        )}
      </div>

      {/* Case Selector */}
      <div className="card-elevated rounded-2xl p-5 bg-white flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div className="flex-1">
          <label className="block text-[10px] font-bold uppercase tracking-wider text-[#8B5230] mb-1">
            Legal Case
          </label>
          <select
            value={selectedCaseId}
            onChange={(e) => setSelectedCaseId(e.target.value)}
            className="w-full px-3.5 py-2.5 rounded-xl bg-[#FAF6F0] border border-[#DFC3AC] text-xs font-bold text-[#1E130D]"
          >
            {cases.map((c) => (
              <option key={c.id} value={c.id}>
                [{c.caseNumber}] {c.caseTitle}
              </option>
            ))}
          </select>
        </div>
      </div>

      {error && (
        <div className="p-4 rounded-2xl bg-red-50 border border-red-200 text-red-900 text-xs flex items-center gap-2.5">
          <AlertCircle className="w-4.5 h-4.5 text-red-700 shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {/* Binders List */}
      {loading ? (
        <div className="card-elevated rounded-3xl p-8 h-64 animate-pulse bg-white" />
      ) : binders.length === 0 ? (
        <div className="card-elevated rounded-3xl p-12 text-center bg-white">
          <BookOpen className="w-12 h-12 text-[#8B5230] mx-auto mb-3 opacity-80" />
          <h3 className="font-serif-legal text-2xl font-bold text-[#1E130D]">
            No Trial Binders Yet
          </h3>
          <p className="text-xs text-[#6E3F23] mt-1">
            Click &ldquo;New Trial Binder&rdquo; above to start organizing exhibits for court.
          </p>
        </div>
      ) : (
        <div className="space-y-6">
          {binders.map((binder) => (
            <div
              key={binder.id}
              className="card-elevated rounded-3xl overflow-hidden bg-white"
            >
              {/* Binder Header */}
              <div className="p-6 bg-[#FAF6F0] border-b border-[#E6D7C3] flex flex-col lg:flex-row lg:items-center justify-between gap-4">
                <div>
                  <div className="flex flex-wrap items-center gap-2 mb-2">
                    <span className="font-mono-hash text-xs font-bold px-3 py-1 rounded-lg bg-[#1E130D] text-[#CA9874]">
                      CODE: {binder.batesPrefix}
                    </span>
                    <span
                      className={`inline-flex items-center gap-1.5 text-xs font-bold uppercase tracking-wider px-3 py-1 rounded-full border ${
                        binder.isLocked
                          ? 'bg-[#3E2A1E] text-white border-[#8B5230]'
                          : 'bg-amber-900/15 text-[#7C4A2D] border-[#B0754F]'
                      }`}
                    >
                      {binder.isLocked ? (
                        <>
                          <Lock className="w-3.5 h-3.5 text-[#CA9874]" /> Finalized
                        </>
                      ) : (
                        <>
                          <Unlock className="w-3.5 h-3.5" /> Draft
                        </>
                      )}
                    </span>
                    <span className="text-xs font-semibold text-[#6E3F23]">
                      {binder.totalExhibits || binder.exhibits?.length || 0} Exhibits • {binder.totalPages || 0} Pages
                    </span>
                  </div>

                  <h3 className="font-serif-legal text-2xl font-bold text-[#1E130D]">
                    {binder.name}
                  </h3>
                  <div className="flex flex-wrap items-center gap-4 mt-2 text-xs text-[#6E3F23]">
                    <span className="flex items-center gap-1">
                      <Scale className="w-3.5 h-3.5 text-[#8B5230]" />
                      Case {binder.caseNumber}
                    </span>
                    {binder.status && (
                      <span>Status: <strong>{binder.status}</strong></span>
                    )}
                  </div>
                </div>

                {/* Binder Controls */}
                {canManageBinders && (
                  <div className="flex flex-wrap items-center gap-2 shrink-0">
                    {!binder.isLocked && (
                      <>
                        <button
                          onClick={() => {
                            setActiveBinder(binder);
                            setExhibitForm({
                              evidenceId: evidenceItems[0]?.id || '',
                              exhibitLabel: `Exhibit ${String.fromCharCode(65 + (binder.exhibits?.length || 0))}`,
                              pageCount: 16,
                            });
                          }}
                          className="inline-flex items-center gap-1.5 px-4 py-2 rounded-xl cognac-gradient text-white text-xs font-semibold shadow-sm cursor-pointer"
                        >
                          <Plus className="w-3.5 h-3.5" />
                          Add Exhibit
                        </button>

                        <button
                          onClick={() => handleStatusChange(binder.id, 'FINALIZED')}
                          className="inline-flex items-center gap-1.5 px-4 py-2 rounded-xl bg-[#1E130D] hover:bg-[#3E2A1E] text-white text-xs font-semibold cursor-pointer"
                        >
                          <Lock className="w-3.5 h-3.5 text-[#CA9874]" />
                          Finalize Binder
                        </button>
                      </>
                    )}

                    <button
                      onClick={() => navigate(`/discovery?caseId=${binder.caseId}&binderId=${binder.id}`)}
                      className="inline-flex items-center gap-1.5 px-4 py-2 rounded-xl bg-white hover:bg-[#EDE0D1] text-[#2C1D14] border border-[#DFC3AC] text-xs font-semibold cursor-pointer"
                    >
                      <Share2 className="w-3.5 h-3.5 text-[#8B5230]" />
                      Share with Opposing Counsel
                    </button>
                  </div>
                )}
              </div>

              {/* Exhibits Cards */}
              <div className="p-6">
                {(!binder.exhibits || binder.exhibits.length === 0) ? (
                  <div className="text-center py-6 text-xs text-[#6E3F23]">
                    No exhibits added yet. Click &ldquo;Add Exhibit&rdquo; to include evidence in this binder.
                  </div>
                ) : (
                  <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                    {binder.exhibits.map((ex) => (
                      <div
                        key={ex.id}
                        className="p-4 rounded-2xl bg-[#FAF6F0] border border-[#DFC3AC] flex flex-col justify-between relative group"
                      >
                        <div>
                          <div className="flex items-center justify-between gap-2 mb-2">
                            <span className="text-xs font-bold uppercase tracking-wider px-2.5 py-0.5 rounded bg-[#8B5230] text-white">
                              {ex.exhibitLabel}
                            </span>
                            <span className="text-[11px] font-bold text-[#6E3F23]">
                              {ex.pageCount} Pages
                            </span>
                          </div>

                          <div className="my-2 p-2.5 rounded-xl bg-white border border-[#D2B89A] text-center shadow-2xs">
                            <div className="text-[10px] uppercase tracking-widest text-[#8B5230] font-bold">
                              Page Number Range
                            </div>
                            <div className="font-mono-hash text-xs font-bold text-[#1E130D] mt-0.5">
                              {ex.batesStart} &rarr; {ex.batesEnd}
                            </div>
                          </div>

                          <div className="text-xs font-bold text-[#1E130D] mt-2">
                            {ex.evidenceName}
                          </div>
                          <div className="text-[11px] text-[#8B5230]">
                            ID: {ex.evidenceTrackingCode}
                          </div>
                        </div>

                        {!binder.isLocked && canManageBinders && (
                          <button
                            onClick={() => handleRemoveExhibit(binder.id, ex.id)}
                            className="mt-3 pt-2.5 border-t border-[#E6D7C3] w-full inline-flex items-center justify-center gap-1.5 text-[11px] font-semibold text-red-800 hover:text-red-950 cursor-pointer"
                          >
                            <Trash2 className="w-3.5 h-3.5" />
                            Remove Exhibit
                          </button>
                        )}
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>
          ))}
        </div>
      )}

      {/* MODAL 1: CREATE BINDER */}
      {showCreateModal && (
        <div className="fixed inset-0 z-50 bg-black/60 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white rounded-3xl max-w-md w-full overflow-hidden shadow-2xl border border-[#DFC3AC]">
            <div className="espresso-gradient p-6 text-white flex items-center justify-between">
              <div>
                <h3 className="font-serif-legal text-2xl font-bold">
                  New Trial Binder
                </h3>
                <p className="text-xs text-[#DFC3AC]">Enter courtroom binder details</p>
              </div>
              <button
                onClick={() => setShowCreateModal(false)}
                className="p-1.5 rounded-lg bg-white/10 hover:bg-white/20 text-white"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleCreateBinder} className="p-6 space-y-4">
              <div>
                <label className="block text-xs font-semibold text-[#3E2A1E] mb-1">
                  Binder Name *
                </label>
                <input
                  type="text"
                  required
                  minLength={3}
                  value={binderForm.name}
                  onChange={(e) => setBinderForm({ ...binderForm, name: e.target.value })}
                  placeholder="Primary Trial Exhibits - Vol. I"
                  className="w-full px-3.5 py-2 rounded-xl bg-[#FAF6F0] border border-[#DFC3AC] text-xs"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-[#3E2A1E] mb-1">
                  Page Number Prefix *
                </label>
                <input
                  type="text"
                  required
                  pattern="^[A-Z0-9_-]{2,12}$"
                  value={binderForm.batesPrefix}
                  onChange={(e) =>
                    setBinderForm({ ...binderForm, batesPrefix: e.target.value.toUpperCase() })
                  }
                  placeholder="PLTF-01"
                  className="w-full px-3.5 py-2 rounded-xl bg-[#FAF6F0] border border-[#DFC3AC] text-xs font-mono-hash"
                />
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
                  Create Binder
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* MODAL 2: ADD EXHIBIT TO BINDER */}
      {activeBinder && (
        <div className="fixed inset-0 z-50 bg-black/60 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white rounded-3xl max-w-md w-full overflow-hidden shadow-2xl border border-[#DFC3AC]">
            <div className="espresso-gradient p-6 text-white flex items-center justify-between">
              <div>
                <h3 className="font-serif-legal text-2xl font-bold">
                  Add Exhibit to Binder
                </h3>
                <p className="text-xs text-[#DFC3AC]">
                  {activeBinder.name}
                </p>
              </div>
              <button
                onClick={() => setActiveBinder(null)}
                className="p-1.5 rounded-lg bg-white/10 hover:bg-white/20 text-white"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleAddExhibit} className="p-6 space-y-4">
              <div>
                <label className="block text-xs font-semibold text-[#3E2A1E] mb-1">
                  Select Evidence Item *
                </label>
                <select
                  required
                  value={exhibitForm.evidenceId}
                  onChange={(e) => setExhibitForm({ ...exhibitForm, evidenceId: e.target.value })}
                  className="w-full px-3.5 py-2 rounded-xl bg-[#FAF6F0] border border-[#DFC3AC] text-xs font-bold"
                >
                  <option value="">Select Evidence...</option>
                  {evidenceItems.map((ev) => (
                    <option key={ev.id} value={ev.id}>
                      [{ev.trackingCode}] {ev.name}
                    </option>
                  ))}
                </select>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-[#3E2A1E] mb-1">
                    Exhibit Label *
                  </label>
                  <input
                    type="text"
                    required
                    value={exhibitForm.exhibitLabel}
                    onChange={(e) =>
                      setExhibitForm({ ...exhibitForm, exhibitLabel: e.target.value })
                    }
                    placeholder="Exhibit A"
                    className="w-full px-3 py-2 rounded-xl bg-[#FAF6F0] border border-[#DFC3AC] text-xs"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-[#3E2A1E] mb-1">
                    Page Count *
                  </label>
                  <input
                    type="number"
                    min={1}
                    required
                    value={exhibitForm.pageCount}
                    onChange={(e) =>
                      setExhibitForm({ ...exhibitForm, pageCount: e.target.value })
                    }
                    className="w-full px-3 py-2 rounded-xl bg-[#FAF6F0] border border-[#DFC3AC] text-xs"
                  />
                </div>
              </div>

              <div className="flex justify-end gap-3 pt-3 border-t border-[#E6D7C3]">
                <button
                  type="button"
                  onClick={() => setActiveBinder(null)}
                  className="px-4 py-2 rounded-xl bg-[#F5EFE6] text-[#3E2A1E] text-xs font-semibold"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-5 py-2 rounded-xl cognac-gradient text-white text-xs font-semibold shadow-md cursor-pointer"
                >
                  Add Exhibit
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
