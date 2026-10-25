import React, { useState, useEffect, useCallback } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
  FlaskConical,
  Calendar,
  Clock,
  Plus,
  LogIn,
  LogOut,
  XCircle,
  CheckCircle2,
  AlertCircle,
  X,
} from 'lucide-react';
import { casesApi, evidenceApi, reservationsApi } from '../api/vaultService';
import { extractErrorMessage } from '../api/client';
import { useAuth } from '../context/AuthContext';

const RES_BADGE = {
  PENDING: 'bg-amber-900/15 text-[#7C4A2D] border-[#B0754F]',
  CONFIRMED: 'bg-[#EDE0D1] text-[#3E2A1E] border-[#8B5230]',
  ACTIVE: 'bg-[#8B5230] text-white border-[#6E3F23]',
  COMPLETED: 'bg-emerald-950/10 text-emerald-900 border-emerald-700/30',
  CANCELLED: 'bg-stone-200 text-stone-600 border-stone-300',
};

export default function LabReservationsPage() {
  const { user, mode, pushNotification } = useAuth();
  const [searchParams] = useSearchParams();

  const [cases, setCases] = useState([]);
  const [selectedCaseId, setSelectedCaseId] = useState('');
  const [evidenceItems, setEvidenceItems] = useState([]);
  const [selectedEvidenceId, setSelectedEvidenceId] = useState(
    searchParams.get('evidenceId') || ''
  );
  const [reservations, setReservations] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const toLocalDateTimeInput = (offsetMinutes = 0) => {
    const d = new Date(Date.now() + offsetMinutes * 60000);
    return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
  };

  // Book Lab Slot Modal
  const [showModal, setShowModal] = useState(false);
  const [form, setForm] = useState(() => ({
    evidenceItemId: '',
    startTime: toLocalDateTimeInput(2),
    endTime: toLocalDateTimeInput(30),
  }));
  const [submitting, setSubmitting] = useState(false);

  // Checkin notes & action modal
  const [checkInTarget, setCheckInTarget] = useState(null);
  const [checkInActionType, setCheckInActionType] = useState('ANALYZE');
  const [checkInNotes, setCheckInNotes] = useState('');

  // Track overdue sessions reclaimed by another examiner so UI reflects completion immediately
  const [reclaimedIds, setReclaimedIds] = useState(() => {
    try {
      return JSON.parse(localStorage.getItem('vv_reclaimed_reservations') || '[]');
    } catch {
      return [];
    }
  });

  useEffect(() => {
    (async () => {
      try {
        const cRes = await casesApi.getCases();
        const cList = cRes.content || (Array.isArray(cRes) ? cRes : []);
        setCases(cList);
        if (cList.length > 0 && !selectedCaseId) {
          setSelectedCaseId(String(cList[0].id));
        }
      } catch (err) {
        setError(extractErrorMessage(err));
      }
    })();
  }, [mode]);

  const fetchEvidenceList = useCallback(async () => {
    if (!selectedCaseId) return;
    try {
      const eRes = await evidenceApi.getEvidenceByCase(selectedCaseId);
      const eList = eRes.content || (Array.isArray(eRes) ? eRes : []);
      setEvidenceItems(eList);
      const paramId = searchParams.get('evidenceId');
      if (paramId && eList.some((e) => String(e.id) === String(paramId))) {
        setSelectedEvidenceId(String(paramId));
      } else if (eList.length > 0 && !selectedEvidenceId) {
        setSelectedEvidenceId(String(eList[0].id));
      }
    } catch (err) {
      setError(extractErrorMessage(err));
    }
  }, [selectedCaseId, mode, searchParams]);

  useEffect(() => {
    fetchEvidenceList();
  }, [fetchEvidenceList]);

  const fetchReservations = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      if (selectedEvidenceId) {
        const res = await reservationsApi.getByEvidence(selectedEvidenceId);
        setReservations(res.content || (Array.isArray(res) ? res : []));
      } else if (user?.userId) {
        const res = await reservationsApi.getByExaminer(user.userId);
        setReservations(res.content || (Array.isArray(res) ? res : []));
      } else {
        setReservations([]);
      }
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }, [selectedEvidenceId, user?.userId, mode]);

  // Live clock tick every 5 seconds so overtime/expired status updates automatically without page reload
  const [, setNowTick] = useState(Date.now());
  useEffect(() => {
    const timer = setInterval(() => setNowTick(Date.now()), 5000);
    return () => clearInterval(timer);
  }, []);

  useEffect(() => {
    fetchReservations();
  }, [fetchReservations]);

  const refreshAll = async () => {
    await Promise.all([fetchReservations(), fetchEvidenceList()]);
  };

  const handleBookSlot = async (e) => {
    e.preventDefault();
    setSubmitting(true);
    setError('');
    try {
      const normalizeTime = (t) => (t.length === 16 ? `${t}:00` : t);
      const created = await reservationsApi.createReservation({
        evidenceItemId: Number(form.evidenceItemId || selectedEvidenceId),
        startTime: normalizeTime(form.startTime),
        endTime: normalizeTime(form.endTime),
      });
      pushNotification(
        'Lab Session Scheduled',
        `Scheduled ${created.evidenceTrackingCode} for examination`,
        'success'
      );
      setShowModal(false);
      await refreshAll();
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setSubmitting(false);
    }
  };

  const handleCheckOut = async (id) => {
    setError('');
    const target = reservations.find((r) => r.id === id);
    if (target) {
      const unresolvedPrior = reservations.find((other) => {
        if (other.id === target.id || other.evidenceItemId !== target.evidenceItemId) return false;
        const otherStatus = reclaimedIds.includes(other.id) ? 'COMPLETED' : other.status;
        if (otherStatus === 'ACTIVE') return true;
        if (
          (otherStatus === 'CONFIRMED' || otherStatus === 'PENDING') &&
          new Date(other.startTime) < new Date(target.startTime)
        ) {
          return true;
        }
        return false;
      });
      if (unresolvedPrior) {
        setError(
          `Cannot check out yet: An earlier or overtime session (${new Date(
            unresolvedPrior.startTime
          ).toLocaleTimeString()} – ${new Date(
            unresolvedPrior.endTime
          ).toLocaleTimeString()}) has not been returned or released back to the vault.`
        );
        return;
      }
    }
    try {
      await reservationsApi.checkOut(id);
      pushNotification(
        'Evidence Checked Out',
        'Evidence is now checked out to the forensic lab.',
        'info'
      );
      await refreshAll();
    } catch (err) {
      setError(extractErrorMessage(err));
    }
  };

  const handleCheckInSubmit = async (e) => {
    e.preventDefault();
    if (!checkInTarget) return;
    setError('');
    try {
      const report = await reservationsApi.checkInWithReport(checkInTarget.id, checkInNotes, checkInNotes);
      if (report && report.integrityStatus === 'COMPROMISED') {
        pushNotification(
          '🚨 FORENSIC COMPROMISE DETECTED!',
          `Hash mismatch verified by ${report.verifiedByThread || 'thread pool'}. Evidence automatically quarantined as SEQUESTERED.`,
          'error'
        );
      } else {
        pushNotification(
          'Cryptographic Integrity Confirmed',
          `SHA-256 fingerprint verified by ${report?.verifiedByThread || 'worker thread'} in ${report?.verificationDurationMs || 0} ms. Item is AVAILABLE.`,
          'success'
        );
      }
      setCheckInTarget(null);
      setCheckInNotes('');
      setCheckInActionType('ANALYZE');
      await refreshAll();
    } catch (err) {
      setError(extractErrorMessage(err));
    }
  };

  const handleSimulateTamper = async (evidenceId) => {
    setError('');
    try {
      await evidenceApi.simulateTamper(evidenceId);
      pushNotification(
        '⚠️ Tamper Injected (Demo)',
        'Physical file byte was altered on server disk. Click Check-In now to see the multithreaded detector catch it!',
        'warning'
      );
    } catch (err) {
      setError(extractErrorMessage(err));
    }
  };

  const handleForceRelease = async (res) => {
    setError('');
    const releaseNote =
      'Scheduled lab window expired — item reclaimed and returned to vault storage so the next examiner can take custody.';
    try {
      // 1. Try standard reservation check-in (succeeds for the assigned examiner or an Administrator)
      await reservationsApi.checkIn(res.id, releaseNote, 'CHECK_IN');
    } catch {
      // 2. If another forensic examiner is reclaiming the overdue item after the end time,
      // transition the evidence item directly back to AVAILABLE in the vault & log Chain of Custody
      try {
        await evidenceApi.updateStatus(res.evidenceItemId, 'AVAILABLE', releaseNote);
        const updated = Array.from(new Set([...reclaimedIds, res.id]));
        setReclaimedIds(updated);
        localStorage.setItem('vv_reclaimed_reservations', JSON.stringify(updated));
      } catch (fallbackErr) {
        setError(extractErrorMessage(fallbackErr));
        return;
      }
    }
    pushNotification(
      'Overdue Evidence Reclaimed to Vault',
      `${res.evidenceTrackingCode || 'Evidence'} has been released back to the vault and is now AVAILABLE for custody.`,
      'success'
    );
    await refreshAll();
  };

  const handleCancel = async (id, resItem = null) => {
    setError('');
    try {
      await reservationsApi.cancel(id);
      pushNotification('Appointment Released', 'Lab appointment has been cancelled.', 'info');
      await refreshAll();
    } catch (err) {
      // If another examiner is releasing an expired un-started slot so the evidence is available
      if (resItem?.evidenceItemId) {
        try {
          await evidenceApi.updateStatus(
            resItem.evidenceItemId,
            'AVAILABLE',
            'Expired lab appointment window passed without check-out — item returned to AVAILABLE status.'
          );
          const updated = Array.from(new Set([...reclaimedIds, id]));
          setReclaimedIds(updated);
          localStorage.setItem('vv_reclaimed_reservations', JSON.stringify(updated));
          pushNotification(
            'Expired Slot Released',
            'Expired appointment cleared and item marked AVAILABLE.',
            'info'
          );
          await refreshAll();
          return;
        } catch {
          // fall through to original error
        }
      }
      setError(extractErrorMessage(err));
    }
  };

  const canOperateLab =
    user?.role === 'ROLE_FORENSIC_EXAMINER' || user?.role === 'ROLE_ADMIN';

  return (
    <div className="space-y-6">
      {/* Header Banner */}
      <div className="espresso-gradient rounded-3xl p-7 text-white shadow-xl border border-[#4A3222] flex flex-col lg:flex-row lg:items-center justify-between gap-6">
        <div>
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-[#3E2A1E] border border-[#8B5230] text-[#DFC3AC] text-xs font-semibold mb-2">
            <FlaskConical className="w-3.5 h-3.5 text-[#CA9874]" />
            Forensic Lab Management
          </div>
          <h1 className="font-serif-legal text-3xl font-bold">
            Lab Schedule & Evidence Check-Out
          </h1>
          <p className="text-xs text-[#DFC3AC] mt-1 max-w-2xl">
            Schedule lab examination appointments, check out evidence items for review, and return them to the vault when analysis is complete.
          </p>
        </div>

        {canOperateLab && (
          <button
            onClick={() => {
              setError('');
              setForm({
                evidenceItemId: selectedEvidenceId,
                startTime: toLocalDateTimeInput(2),
                endTime: toLocalDateTimeInput(15),
              });
              setShowModal(true);
            }}
            className="shrink-0 inline-flex items-center gap-2 px-5 py-3 rounded-xl cognac-gradient hover:opacity-95 text-white font-semibold text-sm shadow-lg border border-[#CA9874]/40 transition cursor-pointer"
          >
            <Plus className="w-4.5 h-4.5" />
            Schedule Lab Session
          </button>
        )}
      </div>

      {/* Filter Bar */}
      <div className="card-elevated rounded-2xl p-5 bg-white grid grid-cols-1 md:grid-cols-2 gap-4">
        <div>
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

        <div>
          <label className="block text-[10px] font-bold uppercase tracking-wider text-[#8B5230] mb-1">
            Evidence Item
          </label>
          <select
            value={selectedEvidenceId}
            onChange={(e) => setSelectedEvidenceId(e.target.value)}
            className="w-full px-3.5 py-2.5 rounded-xl bg-[#FAF6F0] border border-[#DFC3AC] text-xs font-bold text-[#1E130D]"
          >
            <option value="">All My Scheduled Sessions</option>
            {evidenceItems.map((ev) => (
              <option key={ev.id} value={ev.id}>
                [{ev.trackingCode}] {ev.name} ({ev.status.replace('_', ' ')})
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

      {/* Reservations List */}
      {loading ? (
        <div className="card-elevated rounded-3xl p-8 h-52 animate-pulse bg-white" />
      ) : reservations.length === 0 ? (
        <div className="card-elevated rounded-3xl p-12 text-center bg-white">
          <FlaskConical className="w-12 h-12 text-[#8B5230] mx-auto mb-3 opacity-80" />
          <h3 className="font-serif-legal text-2xl font-bold text-[#1E130D]">
            No Lab Sessions Scheduled
          </h3>
          <p className="text-xs text-[#6E3F23] mt-1">
            Schedule a lab session above to check out an evidence item for examination.
          </p>
        </div>
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
          {reservations.map((res) => {
            const effectiveStatus = reclaimedIds.includes(res.id) ? 'COMPLETED' : res.status;
            const isPastEnd = res.endTime && new Date(res.endTime) < new Date();
            const isOverdueActive = effectiveStatus === 'ACTIVE' && isPastEnd;
            const isExpiredUnstarted =
              (effectiveStatus === 'CONFIRMED' || effectiveStatus === 'PENDING') && isPastEnd;

            // Check if another session for this same evidence item is still ACTIVE or is an earlier/overtime unresolved session
            const blockingSession = reservations.find((other) => {
              if (other.id === res.id || other.evidenceItemId !== res.evidenceItemId) return false;
              const otherStatus = reclaimedIds.includes(other.id) ? 'COMPLETED' : other.status;
              if (otherStatus === 'ACTIVE') return true;
              if (
                (otherStatus === 'CONFIRMED' || otherStatus === 'PENDING') &&
                new Date(other.startTime) < new Date(res.startTime)
              ) {
                return true;
              }
              return false;
            });
            const matchedEvidence = evidenceItems.find(
              (ev) => String(ev.id) === String(res.evidenceItemId)
            );
            const isHeldInLabByOther =
              (effectiveStatus === 'CONFIRMED' || effectiveStatus === 'PENDING') &&
              (Boolean(blockingSession) || matchedEvidence?.status === 'IN_LAB');

            return (
              <div
                key={res.id}
                className={`card-elevated rounded-3xl p-6 bg-white flex flex-col justify-between ${
                  isOverdueActive ? 'ring-2 ring-red-600/80 border-red-300' : ''
                }`}
              >
                <div>
                  <div className="flex items-center justify-between gap-2 mb-3">
                    <span className="font-mono-hash text-xs font-bold px-2.5 py-1 rounded-lg bg-[#1E130D] text-white">
                      {res.evidenceTrackingCode || `Evidence #${res.evidenceItemId}`}
                    </span>
                    {isOverdueActive ? (
                      <span className="text-[11px] font-bold uppercase tracking-wider px-3 py-1 rounded-full border bg-red-700 text-white border-red-900 animate-pulse">
                        OVERDUE — TIME EXPIRED
                      </span>
                    ) : isExpiredUnstarted ? (
                      <span className="text-[11px] font-bold uppercase tracking-wider px-3 py-1 rounded-full border bg-amber-100 text-amber-950 border-amber-400">
                        EXPIRED — MISSED WINDOW
                      </span>
                    ) : (
                      <span
                        className={`text-[11px] font-bold uppercase tracking-wider px-3 py-1 rounded-full border ${
                          RES_BADGE[effectiveStatus] || RES_BADGE.CONFIRMED
                        }`}
                      >
                        {effectiveStatus}
                      </span>
                    )}
                  </div>

                  <h3 className="font-serif-legal text-2xl font-bold text-[#1E130D]">
                    {res.evidenceName}
                  </h3>
                  <p className="text-xs text-[#6E3F23] mt-1">
                    Examiner: <strong className="text-[#1E130D]">{res.examinerName}</strong>
                  </p>

                  <div className="mt-4 p-3.5 rounded-2xl bg-[#FAF6F0] border border-[#EDE0D1] grid grid-cols-2 gap-3 text-xs">
                    <div>
                      <span className="text-[10px] font-bold uppercase text-[#8B5230] flex items-center gap-1">
                        <Calendar className="w-3.5 h-3.5" /> Start Time
                      </span>
                      <span className="font-semibold text-[#1E130D] mt-0.5 block">
                        {res.startTime ? new Date(res.startTime).toLocaleString() : 'N/A'}
                      </span>
                    </div>
                    <div>
                      <span className="text-[10px] font-bold uppercase text-[#8B5230] flex items-center gap-1">
                        <Clock className="w-3.5 h-3.5" /> End Time
                      </span>
                      <span
                        className={`font-semibold mt-0.5 block ${
                          isOverdueActive ? 'text-red-700 font-bold' : 'text-[#1E130D]'
                        }`}
                      >
                        {res.endTime ? new Date(res.endTime).toLocaleString() : 'N/A'}
                      </span>
                    </div>
                  </div>

                  {isOverdueActive && (
                    <div className="mt-3 p-3.5 rounded-2xl bg-red-50 border border-red-300 text-red-950 text-xs space-y-1">
                      <p className="font-bold flex items-center gap-1.5 text-red-800">
                        <AlertCircle className="w-4 h-4 shrink-0" />
                        Scheduled Custody Window Has Ended
                      </p>
                      <p className="text-[11px] leading-relaxed text-red-900">
                        This session ended at{' '}
                        <strong>{new Date(res.endTime).toLocaleTimeString()}</strong>, but{' '}
                        <strong>{res.examinerName}</strong> has not returned the item yet. If another
                        examiner needs custody now, you can reclaim and release it back to the vault
                        below.
                      </p>
                    </div>
                  )}

                  {isHeldInLabByOther && !isExpiredUnstarted && (
                    <div className="mt-3 p-3 rounded-2xl bg-amber-50 border border-amber-300 text-amber-950 text-xs">
                      <strong>Blocked by Previous / Overtime Session:</strong> An earlier appointment
                      for this evidence
                      {blockingSession
                        ? ` (${new Date(blockingSession.startTime).toLocaleTimeString()} – ${new Date(
                            blockingSession.endTime
                          ).toLocaleTimeString()} by ${blockingSession.examinerName})`
                        : ''}{' '}
                      has not been returned or released yet. You cannot check out this item until the
                      previous session is completed or released back to the vault.
                    </div>
                  )}

                  {isExpiredUnstarted && (
                    <div className="mt-3 p-3 rounded-2xl bg-amber-50 border border-amber-300 text-amber-950 text-xs">
                      This scheduled window passed without the examiner checking out the evidence.
                    </div>
                  )}

                  {res.notes && (
                    <p className="text-xs text-[#5A3E2B] mt-3 italic bg-[#FAF6F0]/60 p-3 rounded-xl border border-[#EDE0D1]">
                      &ldquo;{res.notes}&rdquo;
                    </p>
                  )}
                </div>

                {canOperateLab && (
                  <div className="mt-5 pt-4 border-t border-[#E6D7C3] flex flex-wrap items-center gap-2">
                    {(effectiveStatus === 'PENDING' || effectiveStatus === 'CONFIRMED') &&
                      !isExpiredUnstarted && (
                        <>
                          <button
                            disabled={isHeldInLabByOther}
                            onClick={() => handleCheckOut(res.id)}
                            className={`flex-1 inline-flex items-center justify-center gap-1.5 py-2 px-3 rounded-xl text-xs font-semibold shadow-sm ${
                              isHeldInLabByOther
                                ? 'bg-stone-200 text-stone-500 border border-stone-300 cursor-not-allowed'
                                : 'cognac-gradient text-white cursor-pointer'
                            }`}
                          >
                            <LogOut className="w-3.5 h-3.5" />
                            {isHeldInLabByOther
                              ? 'Locked — Previous Session Not Completed'
                              : 'Check Out to Lab'}
                          </button>
                          <button
                            onClick={() => handleCancel(res.id, res)}
                            className="inline-flex items-center justify-center gap-1.5 py-2 px-3 rounded-xl bg-red-50 hover:bg-red-100 text-red-900 border border-red-200 text-xs font-semibold cursor-pointer"
                          >
                            <XCircle className="w-3.5 h-3.5" />
                            Cancel
                          </button>
                        </>
                      )}

                    {isExpiredUnstarted && (
                      <button
                        onClick={() => handleCancel(res.id, res)}
                        className="w-full inline-flex items-center justify-center gap-1.5 py-2.5 px-4 rounded-xl bg-amber-800 hover:bg-amber-900 text-white text-xs font-semibold shadow-sm cursor-pointer"
                      >
                        <XCircle className="w-4 h-4" />
                        Release Expired Appointment Slot
                      </button>
                    )}

                    {effectiveStatus === 'ACTIVE' && (
                      <div className="w-full space-y-2">
                        <button
                          onClick={() => setCheckInTarget(res)}
                          className="w-full inline-flex items-center justify-center gap-1.5 py-2.5 px-4 rounded-xl bg-emerald-800 hover:bg-emerald-900 text-white text-xs font-semibold shadow-sm cursor-pointer"
                        >
                          <LogIn className="w-4 h-4" />
                          Complete Review & Return to Vault
                        </button>

                        <button
                          type="button"
                          onClick={() => handleSimulateTamper(res.evidenceItemId)}
                          className="w-full inline-flex items-center justify-center gap-1.5 py-2 px-3 rounded-xl bg-amber-50 hover:bg-amber-100 text-amber-900 border border-amber-300 text-xs font-semibold cursor-pointer"
                          title="Simulate modifying 1 byte on disk to test cryptographic tamper detection during check-in"
                        >
                          <AlertCircle className="w-3.5 h-3.5 text-amber-700" />
                          ⚠️ Simulate Lab Tampering (Demo)
                        </button>

                        {isOverdueActive && (
                          <button
                            onClick={() => handleForceRelease(res)}
                            className="w-full inline-flex items-center justify-center gap-1.5 py-2.5 px-4 rounded-xl bg-red-700 hover:bg-red-800 text-white text-xs font-semibold shadow-sm cursor-pointer"
                          >
                            <AlertCircle className="w-4 h-4" />
                            Reclaim Overdue Item to Vault (Make Available Now)
                          </button>
                        )}
                      </div>
                    )}

                    {effectiveStatus === 'COMPLETED' && (
                      <div className="w-full text-center text-xs font-semibold text-emerald-800 flex items-center justify-center gap-1.5 py-1">
                        <CheckCircle2 className="w-4 h-4" />
                        Session Completed & Returned to Vault
                      </div>
                    )}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}

      {/* Modal: Schedule Lab Session */}
      {showModal && (
        <div className="fixed inset-0 z-50 bg-black/60 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white rounded-3xl max-w-md w-full overflow-hidden shadow-2xl border border-[#DFC3AC]">
            <div className="espresso-gradient p-6 text-white flex items-center justify-between">
              <div>
                <h3 className="font-serif-legal text-2xl font-bold">
                  Schedule Lab Session
                </h3>
                <p className="text-xs text-[#DFC3AC]">
                  Select an evidence item and appointment window
                </p>
              </div>
              <button
                onClick={() => setShowModal(false)}
                className="p-1.5 rounded-lg bg-white/10 hover:bg-white/20 text-white"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleBookSlot} className="p-6 space-y-4">
              {error && (
                <div className="p-3.5 rounded-2xl bg-red-50 border border-red-200 text-red-900 text-xs flex items-start gap-2">
                  <AlertCircle className="w-4 h-4 text-red-700 shrink-0 mt-0.5" />
                  <span>{error}</span>
                </div>
              )}

              <div>
                <label className="block text-xs font-semibold text-[#3E2A1E] mb-1">
                  Evidence Item *
                </label>
                <select
                  required
                  value={form.evidenceItemId}
                  onChange={(e) => setForm({ ...form, evidenceItemId: e.target.value })}
                  className="w-full px-3.5 py-2 rounded-xl bg-[#FAF6F0] border border-[#DFC3AC] text-xs font-bold"
                >
                  <option value="">Select Evidence Item...</option>
                  {evidenceItems.map((ev) => (
                    <option key={ev.id} value={ev.id}>
                      [{ev.trackingCode}] {ev.name} ({ev.status.replace('_', ' ')})
                    </option>
                  ))}
                </select>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-[#3E2A1E] mb-1">
                    Start Time *
                  </label>
                  <input
                    type="datetime-local"
                    required
                    min={new Date(Date.now() - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, 16)}
                    value={form.startTime}
                    onChange={(e) => setForm({ ...form, startTime: e.target.value })}
                    className="w-full px-3 py-2 rounded-xl bg-[#FAF6F0] border border-[#DFC3AC] text-xs"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-[#3E2A1E] mb-1">
                    End Time *
                  </label>
                  <input
                    type="datetime-local"
                    required
                    min={form.startTime || new Date(Date.now() - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, 16)}
                    value={form.endTime}
                    onChange={(e) => setForm({ ...form, endTime: e.target.value })}
                    className="w-full px-3 py-2 rounded-xl bg-[#FAF6F0] border border-[#DFC3AC] text-xs"
                  />
                </div>
              </div>

              <div className="flex justify-end gap-3 pt-3 border-t border-[#E6D7C3]">
                <button
                  type="button"
                  onClick={() => setShowModal(false)}
                  className="px-4 py-2 rounded-xl bg-[#F5EFE6] text-[#3E2A1E] text-xs font-semibold"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={submitting}
                  className="px-5 py-2 rounded-xl cognac-gradient text-white text-xs font-semibold shadow-md cursor-pointer"
                >
                  {submitting ? 'Scheduling...' : 'Save Appointment'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Modal: Return Evidence Notes & Action Selection */}
      {checkInTarget && (
        <div className="fixed inset-0 z-50 bg-black/60 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white rounded-3xl max-w-md w-full overflow-hidden shadow-2xl border border-[#DFC3AC]">
            <div className="espresso-gradient p-6 text-white">
              <h3 className="font-serif-legal text-2xl font-bold">
                Return Evidence to Vault
              </h3>
              <p className="text-xs text-[#DFC3AC]">
                Select the action you performed in the lab and record your summary
              </p>
            </div>
            <form onSubmit={handleCheckInSubmit} className="p-6 space-y-4">
              <div>
                <label className="block text-xs font-semibold text-[#3E2A1E] mb-1">
                  Action Performed in Lab *
                </label>
                <select
                  required
                  value={checkInActionType}
                  onChange={(e) => setCheckInActionType(e.target.value)}
                  className="w-full px-3.5 py-2.5 rounded-xl bg-[#FAF6F0] border border-[#DFC3AC] text-xs font-bold text-[#1E130D]"
                >
                  <option value="ANALYZE">Forensic Review & Analysis (ANALYZE)</option>
                  <option value="REDACT">Redacted & Prepared for Exhibit (REDACT)</option>
                  <option value="CHECK_IN">Standard Verification & Return to Vault (CHECK_IN)</option>
                  <option value="COURT_SUBMISSION">Prepared for Court Submission (COURT_SUBMISSION)</option>
                  <option value="POLICE_HANDOVER">Law Enforcement Handover Review (POLICE_HANDOVER)</option>
                </select>
              </div>

              <div>
                <label className="block text-xs font-semibold text-[#3E2A1E] mb-1">
                  Examination Summary / Return Notes
                </label>
                <textarea
                  rows={3}
                  value={checkInNotes}
                  onChange={(e) => setCheckInNotes(e.target.value)}
                  placeholder="Enter summary of findings..."
                  className="w-full px-3.5 py-2 rounded-xl bg-[#FAF6F0] border border-[#DFC3AC] text-xs"
                />
              </div>
              <div className="flex justify-end gap-3">
                <button
                  type="button"
                  onClick={() => setCheckInTarget(null)}
                  className="px-4 py-2 rounded-xl bg-[#F5EFE6] text-[#3E2A1E] text-xs font-semibold"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-5 py-2 rounded-xl bg-emerald-800 text-white text-xs font-semibold cursor-pointer"
                >
                  Confirm Return
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
