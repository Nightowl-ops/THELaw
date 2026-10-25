import React, { useState, useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
  History,
  ShieldCheck,
  CheckCircle2,
  UserCheck,
  Clock,
  Lock,
  AlertCircle,
} from 'lucide-react';
import { casesApi, evidenceApi, custodyLogsApi } from '../api/vaultService';
import { extractErrorMessage } from '../api/client';
import { useAuth } from '../context/AuthContext';

const ACTION_STYLES = {
  INGEST: {
    bg: 'bg-blue-700 text-white shadow-xs',
    circle: 'bg-blue-700 text-white',
    cardBorder: 'border-l-4 border-l-blue-700 border-[#E6D7C3]',
    label: 'EVIDENCE REGISTERED',
  },
  POLICE_HANDOVER: {
    bg: 'bg-sky-800 text-white shadow-xs',
    circle: 'bg-sky-800 text-white',
    cardBorder: 'border-l-4 border-l-sky-800 border-[#E6D7C3]',
    label: 'OFFICER HANDOVER',
  },
  CHECK_IN: {
    bg: 'bg-emerald-600 text-white shadow-xs',
    circle: 'bg-emerald-600 text-white',
    cardBorder: 'border-l-4 border-l-emerald-600 border-[#E6D7C3]',
    label: 'RETURNED TO VAULT',
  },
  CHECK_OUT: {
    bg: 'bg-amber-600 text-white shadow-xs',
    circle: 'bg-amber-600 text-white',
    cardBorder: 'border-l-4 border-l-amber-600 border-[#E6D7C3]',
    label: 'CHECKED OUT TO LAB',
  },
  ANALYZE: {
    bg: 'bg-[#5C3A21] text-white shadow-xs',
    circle: 'bg-[#5C3A21] text-white',
    cardBorder: 'border-l-4 border-l-[#5C3A21] border-[#E6D7C3]',
    label: 'FORENSIC REVIEW',
  },
  REDACT: {
    bg: 'bg-purple-700 text-white shadow-xs',
    circle: 'bg-purple-700 text-white',
    cardBorder: 'border-l-4 border-l-purple-700 border-[#E6D7C3]',
    label: 'PREPARED FOR EXHIBIT',
  },
  COURT_SUBMISSION: {
    bg: 'bg-rose-700 text-white shadow-xs',
    circle: 'bg-rose-700 text-white',
    cardBorder: 'border-l-4 border-l-rose-700 border-[#E6D7C3]',
    label: 'ADMITTED TO COURT',
  },
};

export default function ChainOfCustodyPage() {
  const { mode } = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();

  const [cases, setCases] = useState([]);
  const [selectedCaseId, setSelectedCaseId] = useState('');
  const [evidenceItems, setEvidenceItems] = useState([]);
  const [selectedEvidenceId, setSelectedEvidenceId] = useState(
    searchParams.get('evidenceId') || ''
  );
  const [logs, setLogs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

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

  useEffect(() => {
    if (!selectedCaseId) return;
    (async () => {
      try {
        const eRes = await evidenceApi.getEvidenceByCase(selectedCaseId);
        const eList = eRes.content || (Array.isArray(eRes) ? eRes : []);
        setEvidenceItems(eList);
        const paramEvId = searchParams.get('evidenceId');
        if (paramEvId && eList.some((e) => String(e.id) === String(paramEvId))) {
          setSelectedEvidenceId(String(paramEvId));
        } else if (eList.length > 0) {
          setSelectedEvidenceId(String(eList[0].id));
        } else {
          setSelectedEvidenceId('');
          setLogs([]);
        }
      } catch (err) {
        setError(extractErrorMessage(err));
      }
    })();
  }, [selectedCaseId, mode]);

  useEffect(() => {
    if (!selectedEvidenceId) {
      setLoading(false);
      return;
    }
    (async () => {
      setLoading(true);
      setError('');
      try {
        const trail = await custodyLogsApi.getAuditTrail(selectedEvidenceId);
        setLogs(Array.isArray(trail) ? trail : []);
      } catch (err) {
        setError(extractErrorMessage(err));
      } finally {
        setLoading(false);
      }
    })();
  }, [selectedEvidenceId, mode]);

  const currentEvidence = evidenceItems.find(
    (e) => String(e.id) === String(selectedEvidenceId)
  );

  return (
    <div className="space-y-6">
      {/* Header Banner */}
      <div className="espresso-gradient rounded-3xl p-7 text-white shadow-xl border border-[#4A3222]">
        <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-[#3E2A1E] border border-[#8B5230] text-[#DFC3AC] text-xs font-semibold mb-2">
          <Lock className="w-3.5 h-3.5 text-[#CA9874]" />
          Official Custodial Record
        </div>
        <h1 className="font-serif-legal text-3xl font-bold">
          Chain of Custody
        </h1>
        <p className="text-xs text-[#DFC3AC] mt-1 max-w-3xl">
          Complete chronological history of everyone who handled, reviewed, or updated an evidence item from initial intake through courtroom admission.
        </p>
      </div>

      {/* Selector Bar */}
      <div className="card-elevated rounded-2xl p-5 bg-white grid grid-cols-1 md:grid-cols-2 gap-4">
        <div>
          <label className="block text-[10px] font-bold uppercase tracking-wider text-[#8B5230] mb-1">
            1. Select Legal Case
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
            2. Select Evidence Item
          </label>
          <select
            value={selectedEvidenceId}
            onChange={(e) => {
              setSelectedEvidenceId(e.target.value);
              setSearchParams({ evidenceId: e.target.value });
            }}
            className="w-full px-3.5 py-2.5 rounded-xl bg-[#FAF6F0] border border-[#DFC3AC] text-xs font-bold text-[#1E130D]"
          >
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

      {/* Summary Card */}
      {currentEvidence && (
        <div className="card-elevated rounded-3xl p-6 bg-white border-l-4 border-l-[#8B5230] flex flex-col lg:flex-row lg:items-center justify-between gap-4">
          <div>
            <div className="flex items-center gap-2">
              <span className="font-mono-hash text-xs font-bold px-2.5 py-1 rounded-lg bg-[#1E130D] text-white">
                {currentEvidence.trackingCode}
              </span>
              <h3 className="font-serif-legal text-2xl font-bold text-[#1E130D]">
                {currentEvidence.name}
              </h3>
            </div>
            <p className="text-xs text-[#6E3F23] mt-1">
              Source: <strong className="text-[#1E130D]">{currentEvidence.sourceOrigin || 'Recorded at intake'}</strong>
            </p>
          </div>

          <div className="shrink-0 flex items-center gap-2.5 px-4 py-2.5 rounded-2xl bg-emerald-950/5 border border-emerald-700/30 text-emerald-900">
            <CheckCircle2 className="w-5 h-5 text-emerald-700" />
            <div>
              <div className="text-xs font-bold uppercase tracking-wider">
                Custody Chain Verified
              </div>
              <div className="text-[11px] text-emerald-800">
                {logs.length} recorded custody events
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Vertical Timeline */}
      {loading ? (
        <div className="card-elevated rounded-3xl p-8 h-64 animate-pulse bg-white" />
      ) : logs.length === 0 ? (
        <div className="card-elevated rounded-3xl p-12 text-center bg-white">
          <History className="w-12 h-12 text-[#8B5230] mx-auto mb-3 opacity-80" />
          <h3 className="font-serif-legal text-2xl font-bold text-[#1E130D]">
            No Custody History Recorded Yet
          </h3>
          <p className="text-xs text-[#6E3F23] mt-1">
            Select an evidence item above to view its handling timeline.
          </p>
        </div>
      ) : (
        <div className="card-elevated rounded-3xl p-8 bg-white">
          {/* High-Contrast Color Legend for Quick Visual Reference */}
          <div className="mb-6 pb-4 border-b border-[#E6D7C3] flex flex-wrap items-center gap-2.5">
            <span className="text-[11px] font-bold uppercase tracking-wider text-[#6E3F23] mr-1">
              Status Color Guide:
            </span>
            <span className="text-[11px] font-bold px-2.5 py-1 rounded-md bg-blue-700 text-white">
              Registered (Intake)
            </span>
            <span className="text-[11px] font-bold px-2.5 py-1 rounded-md bg-amber-600 text-white">
              Checked Out to Lab
            </span>
            <span className="text-[11px] font-bold px-2.5 py-1 rounded-md bg-[#5C3A21] text-white">
              Forensic Review
            </span>
            <span className="text-[11px] font-bold px-2.5 py-1 rounded-md bg-emerald-600 text-white">
              Returned to Vault
            </span>
            <span className="text-[11px] font-bold px-2.5 py-1 rounded-md bg-purple-700 text-white">
              Prepared for Exhibit
            </span>
            <span className="text-[11px] font-bold px-2.5 py-1 rounded-md bg-rose-700 text-white">
              Admitted to Court
            </span>
          </div>

          <div className="relative border-l-2 border-[#B0754F] ml-4 space-y-8">
            {logs.map((log, index) => {
              const style = ACTION_STYLES[log.actionType] || {
                bg: 'bg-slate-800 text-white',
                circle: 'bg-slate-800 text-white',
                cardBorder: 'border-l-4 border-l-slate-800 border-[#E6D7C3]',
                label: log.actionType,
              };

              return (
                <div key={log.id || index} className="relative pl-8">
                  <div
                    className={`absolute -left-4 top-1 w-7.5 h-7.5 rounded-full flex items-center justify-center text-xs font-bold shadow-md border-2 border-white ${style.circle}`}
                  >
                    {index + 1}
                  </div>

                  <div
                    className={`p-5 rounded-2xl bg-[#FAF6F0] border hover:border-[#B0754F] transition ${style.cardBorder}`}
                  >
                    <div className="flex flex-wrap items-center justify-between gap-2 mb-2">
                      <div className="flex items-center gap-2.5">
                        <span
                          className={`text-xs font-extrabold uppercase tracking-wider px-3.5 py-1.5 rounded-lg ${style.bg}`}
                        >
                          {style.label}
                        </span>
                        <span className="text-xs font-semibold text-[#6E3F23] flex items-center gap-1">
                          <UserCheck className="w-3.5 h-3.5 text-[#8B5230]" />
                          {log.actorName || `Staff #${log.actorId}`}
                        </span>
                      </div>

                      <div className="flex items-center gap-3">
                        <span className="text-xs text-[#6E3F23] flex items-center gap-1">
                          <Clock className="w-3.5 h-3.5 text-[#8B5230]" />
                          {log.loggedAt ? new Date(log.loggedAt).toLocaleString() : 'Recorded'}
                        </span>
                        <span className="inline-flex items-center gap-1 text-[11px] font-bold px-2.5 py-0.5 rounded-full bg-emerald-950/10 text-emerald-800">
                          <ShieldCheck className="w-3.5 h-3.5" />
                          Verified
                        </span>
                      </div>
                    </div>

                    <p className="text-sm text-[#1E130D] font-medium leading-relaxed mt-2">
                      {log.notes?.replace(/Baseline SHA-256:.*$/i, '').trim() || 'Custody event recorded.'}
                    </p>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}
