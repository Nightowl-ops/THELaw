import React, { useState, useEffect, useCallback } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import {
  HardDrive,
  Upload,
  Search,
  ShieldCheck,
  Download,
  History,
  FlaskConical,
  ArrowRightLeft,
  FileText,
  Film,
  Mic,
  Cpu,
  Network,
  AlertCircle,
  X,
} from 'lucide-react';
import { casesApi, evidenceApi } from '../api/vaultService';
import { extractErrorMessage } from '../api/client';
import { useAuth } from '../context/AuthContext';

const VALID_TRANSITIONS = {
  INGESTED: ['AVAILABLE'],
  AVAILABLE: ['RESERVED', 'SEQUESTERED', 'STAMPED', 'ADMITTED'],
  RESERVED: ['IN_LAB', 'AVAILABLE'],
  IN_LAB: ['AVAILABLE', 'STAMPED'],
  STAMPED: ['AVAILABLE', 'ADMITTED'],
  SEQUESTERED: ['AVAILABLE'],
  ADMITTED: [],
};

const EVIDENCE_STATUS_BADGE = {
  INGESTED: 'bg-[#EDE0D1] text-[#3E2A1E] border-[#C9B29B]',
  AVAILABLE: 'bg-emerald-950/10 text-emerald-900 border-emerald-700/30',
  RESERVED: 'bg-amber-900/15 text-[#7C4A2D] border-[#B0754F]',
  IN_LAB: 'bg-[#8B5230] text-white border-[#6E3F23]',
  STAMPED: 'bg-[#3E2A1E] text-[#FAF6F0] border-[#8B5230]',
  ADMITTED: 'bg-[#1E130D] text-[#CA9874] border-[#CA9874]',
  SEQUESTERED: 'bg-red-950/10 text-red-900 border-red-800/30',
};

const TYPE_LABELS = {
  VIDEO: 'Video Recording',
  AUDIO: 'Audio Recording',
  DOCUMENT: 'Document',
  DEVICE_IMAGE: 'Device Extraction',
  NETWORK_LOG: 'Network Record',
};

const TYPE_ICONS = {
  VIDEO: Film,
  AUDIO: Mic,
  DOCUMENT: FileText,
  DEVICE_IMAGE: Cpu,
  NETWORK_LOG: Network,
};

function formatBytes(bytes) {
  if (!bytes) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return `${parseFloat((bytes / Math.pow(k, i)).toFixed(2))} ${sizes[i]}`;
}

export default function EvidenceVaultPage() {
  const { user, mode, pushNotification } = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();
  const navigate = useNavigate();

  const [cases, setCases] = useState([]);
  const [selectedCaseId, setSelectedCaseId] = useState(searchParams.get('caseId') || '');
  const [evidenceList, setEvidenceList] = useState([]);
  const [statusFilter, setStatusFilter] = useState('');
  const [typeFilter, setTypeFilter] = useState('');
  const [trackingQuery, setTrackingQuery] = useState('');

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  // Upload Evidence Modal State
  const [showUploadModal, setShowUploadModal] = useState(false);
  const [selectedFile, setSelectedFile] = useState(null);
  const [uploadForm, setUploadForm] = useState({
    name: '',
    description: '',
    evidenceType: 'DEVICE_IMAGE',
    sourceOrigin: '',
    seizingOfficerName: '',
    officerBadgeNumber: '',
    lawEnforcementAgency: '',
    policeIncidentReportNum: '',
  });
  const [uploading, setUploading] = useState(false);

  // Status Transition Modal State
  const [transitionTarget, setTransitionTarget] = useState(null);
  const [nextStatus, setNextStatus] = useState('');
  const [transitionNotes, setTransitionNotes] = useState('');

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

  const fetchEvidence = useCallback(async () => {
    if (!selectedCaseId) {
      setLoading(false);
      return;
    }
    setLoading(true);
    setError('');
    try {
      const res = await evidenceApi.getEvidenceByCase(selectedCaseId, {
        status: statusFilter || undefined,
        type: typeFilter || undefined,
      });
      setEvidenceList(res.content || (Array.isArray(res) ? res : []));
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }, [selectedCaseId, statusFilter, typeFilter, mode]);

  useEffect(() => {
    fetchEvidence();
  }, [fetchEvidence]);

  const handleTrackingLookup = async (e) => {
    e.preventDefault();
    if (!trackingQuery.trim()) {
      fetchEvidence();
      return;
    }
    setLoading(true);
    setError('');
    try {
      const item = await evidenceApi.getEvidenceByTrackingCode(trackingQuery);
      setEvidenceList([item]);
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setLoading(false);
    }
  };

  const handleFileChange = (e) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setSelectedFile(file);
    if (!uploadForm.name) {
      setUploadForm((prev) => ({ ...prev, name: file.name }));
    }
  };

  const handleUploadSubmit = async (e) => {
    e.preventDefault();
    if (!selectedFile) {
      setError('Please select a file to upload.');
      return;
    }
    setUploading(true);
    setError('');
    try {
      const created = await evidenceApi.uploadEvidence(selectedFile, {
        caseId: Number(selectedCaseId),
        ...uploadForm,
      });
      pushNotification(
        'Evidence Uploaded',
        `${created.trackingCode} - ${created.name} has been added to the vault.`,
        'success'
      );
      setShowUploadModal(false);
      setSelectedFile(null);
      setUploadForm({
        name: '',
        description: '',
        evidenceType: 'DEVICE_IMAGE',
        sourceOrigin: '',
        seizingOfficerName: '',
        officerBadgeNumber: '',
        lawEnforcementAgency: '',
        policeIncidentReportNum: '',
      });
      fetchEvidence();
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setUploading(false);
    }
  };

  const handleTransitionSubmit = async (e) => {
    e.preventDefault();
    if (!transitionTarget || !nextStatus) return;
    try {
      const updated = await evidenceApi.updateStatus(
        transitionTarget.id,
        nextStatus,
        transitionNotes
      );
      pushNotification(
        'Status Updated',
        `${updated.trackingCode} status changed to ${updated.status.replace('_', ' ')}`,
        'info'
      );
      setTransitionTarget(null);
      setTransitionNotes('');
      fetchEvidence();
    } catch (err) {
      setError(extractErrorMessage(err));
    }
  };

  const canTransition =
    user?.role === 'ROLE_FORENSIC_EXAMINER' || user?.role === 'ROLE_ADMIN';

  return (
    <div className="space-y-6">
      {/* Top Vault Control Bar */}
      <div className="espresso-gradient rounded-3xl p-7 text-white shadow-xl border border-[#4A3222] flex flex-col lg:flex-row lg:items-center justify-between gap-6">
        <div>
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-[#3E2A1E] border border-[#8B5230] text-[#DFC3AC] text-xs font-semibold mb-2">
            <ShieldCheck className="w-3.5 h-3.5 text-[#CA9874]" />
            Digital Evidence Repository
          </div>
          <h1 className="font-serif-legal text-3xl font-bold">
            Evidence Vault
          </h1>
          <p className="text-xs text-[#DFC3AC] mt-1 max-w-2xl">
            Upload, organize, and manage digital evidence items, collection details, and current custody status for each legal case.
          </p>
        </div>

        <button
          onClick={() => setShowUploadModal(true)}
          disabled={!selectedCaseId}
          className="shrink-0 inline-flex items-center gap-2 px-5 py-3 rounded-xl cognac-gradient hover:opacity-95 text-white font-semibold text-sm shadow-lg border border-[#CA9874]/40 transition cursor-pointer"
        >
          <Upload className="w-4.5 h-4.5" />
          Upload Evidence
        </button>
      </div>

      {/* Case Selector + Tracking Code Search + Filters */}
      <div className="card-elevated rounded-2xl p-5 bg-white grid grid-cols-1 lg:grid-cols-12 gap-4 items-center">
        <div className="lg:col-span-4">
          <label className="block text-[10px] font-bold uppercase tracking-wider text-[#8B5230] mb-1">
            Legal Case
          </label>
          <select
            value={selectedCaseId}
            onChange={(e) => {
              setSelectedCaseId(e.target.value);
              setSearchParams({ caseId: e.target.value });
            }}
            className="w-full px-3.5 py-2.5 rounded-xl bg-[#FAF6F0] border border-[#DFC3AC] text-xs font-bold text-[#1E130D] focus:outline-none focus:border-[#8B5230]"
          >
            {cases.map((c) => (
              <option key={c.id} value={c.id}>
                [{c.caseNumber}] {c.caseTitle}
              </option>
            ))}
          </select>
        </div>

        <form onSubmit={handleTrackingLookup} className="lg:col-span-4">
          <label className="block text-[10px] font-bold uppercase tracking-wider text-[#8B5230] mb-1">
            Search by Evidence ID
          </label>
          <div className="flex gap-2">
            <div className="relative flex-1">
              <Search className="w-3.5 h-3.5 text-[#8B5230] absolute left-3 top-3" />
              <input
                type="text"
                value={trackingQuery}
                onChange={(e) => setTrackingQuery(e.target.value)}
                placeholder="e.g. EV-2026-0001"
                className="w-full pl-8 pr-3 py-2 rounded-xl bg-[#FAF6F0] border border-[#DFC3AC] text-xs font-mono-hash focus:outline-none focus:border-[#8B5230]"
              />
            </div>
            <button
              type="submit"
              className="px-3.5 py-2 rounded-xl bg-[#1E130D] text-white text-xs font-semibold hover:bg-[#3E2A1E] transition cursor-pointer"
            >
              Find
            </button>
          </div>
        </form>

        <div className="lg:col-span-2">
          <label className="block text-[10px] font-bold uppercase tracking-wider text-[#8B5230] mb-1">
            Status
          </label>
          <select
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            className="w-full px-3 py-2 rounded-xl bg-[#FAF6F0] border border-[#DFC3AC] text-xs font-semibold"
          >
            <option value="">All Statuses</option>
            <option value="INGESTED">Ingested</option>
            <option value="AVAILABLE">Available</option>
            <option value="RESERVED">Reserved</option>
            <option value="IN_LAB">In Lab</option>
            <option value="STAMPED">Stamped</option>
            <option value="ADMITTED">Admitted</option>
            <option value="SEQUESTERED">Sequestered</option>
          </select>
        </div>

        <div className="lg:col-span-2">
          <label className="block text-[10px] font-bold uppercase tracking-wider text-[#8B5230] mb-1">
            File Type
          </label>
          <select
            value={typeFilter}
            onChange={(e) => setTypeFilter(e.target.value)}
            className="w-full px-3 py-2 rounded-xl bg-[#FAF6F0] border border-[#DFC3AC] text-xs font-semibold"
          >
            <option value="">All Types</option>
            <option value="DEVICE_IMAGE">Device Extraction</option>
            <option value="NETWORK_LOG">Network Record</option>
            <option value="VIDEO">Video Recording</option>
            <option value="AUDIO">Audio Recording</option>
            <option value="DOCUMENT">Document</option>
          </select>
        </div>
      </div>

      {error && (
        <div className="p-4 rounded-2xl bg-red-50 border border-red-200 text-red-900 text-xs flex items-center gap-2.5">
          <AlertCircle className="w-4.5 h-4.5 text-red-700 shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {/* Evidence Cards List */}
      {loading ? (
        <div className="space-y-4">
          {[1, 2].map((i) => (
            <div key={i} className="card-elevated rounded-3xl p-6 h-48 animate-pulse bg-white" />
          ))}
        </div>
      ) : evidenceList.length === 0 ? (
        <div className="card-elevated rounded-3xl p-12 text-center bg-white">
          <HardDrive className="w-12 h-12 text-[#8B5230] mx-auto mb-3 opacity-80" />
          <h3 className="font-serif-legal text-2xl font-bold text-[#1E130D]">
            No Evidence Items Found
          </h3>
          <p className="text-xs text-[#6E3F23] mt-1 max-w-md mx-auto">
            Click &ldquo;Upload Evidence&rdquo; above to add a digital file to this case.
          </p>
        </div>
      ) : (
        <div className="space-y-5">
          {evidenceList.map((ev) => {
            const Icon = TYPE_ICONS[ev.evidenceType] || FileText;
            const allowedNext = VALID_TRANSITIONS[ev.status] || [];
            return (
              <div
                key={ev.id}
                className="card-elevated rounded-3xl p-6 bg-white transition-all"
              >
                <div className="flex flex-col lg:flex-row lg:items-start justify-between gap-4">
                  <div className="flex items-start gap-4">
                    <div className="w-12 h-12 rounded-2xl espresso-gradient text-[#CA9874] flex items-center justify-center shrink-0 shadow-md">
                      <Icon className="w-6 h-6" />
                    </div>
                    <div>
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="font-mono-hash text-xs font-bold px-2.5 py-1 rounded-lg bg-[#1E130D] text-white">
                          {ev.trackingCode}
                        </span>
                        <span
                          className={`text-[11px] font-bold uppercase tracking-wider px-3 py-0.5 rounded-full border ${
                            EVIDENCE_STATUS_BADGE[ev.status] || EVIDENCE_STATUS_BADGE.INGESTED
                          }`}
                        >
                          {ev.status.replace('_', ' ')}
                        </span>
                        <span className="text-xs font-semibold text-[#8B5230] bg-[#FAF6F0] px-2.5 py-0.5 rounded-lg border border-[#DFC3AC]">
                          {TYPE_LABELS[ev.evidenceType] || ev.evidenceType} • {formatBytes(ev.fileSizeBytes)}
                        </span>
                      </div>

                      <h3 className="font-serif-legal text-2xl font-bold text-[#1E130D] mt-2">
                        {ev.name}
                      </h3>
                      <p className="text-xs text-[#5A3E2B] mt-1 max-w-3xl">
                        {ev.description || 'No description provided.'}
                      </p>
                    </div>
                  </div>

                  {/* Action Buttons */}
                  <div className="flex flex-wrap items-center gap-2 shrink-0">
                    <button
                      onClick={() => navigate(`/custody-ledger?evidenceId=${ev.id}`)}
                      className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl bg-[#1E130D] hover:bg-[#3E2A1E] text-white text-xs font-semibold transition cursor-pointer"
                    >
                      <History className="w-3.5 h-3.5 text-[#CA9874]" />
                      History
                    </button>

                    <button
                      onClick={() => navigate(`/lab-reservations?evidenceId=${ev.id}`)}
                      className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl bg-[#F5EFE6] hover:bg-[#EDE0D1] text-[#1E130D] border border-[#DFC3AC] text-xs font-semibold transition cursor-pointer"
                    >
                      <FlaskConical className="w-3.5 h-3.5 text-[#8B5230]" />
                      Lab Schedule
                    </button>

                    <button
                      onClick={() =>
                        evidenceApi.downloadEvidence(
                          ev.id,
                          ev.originalFileName || ev.name,
                          ev.fileStoragePath
                        )
                      }
                      className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl bg-[#F5EFE6] hover:bg-[#EDE0D1] text-[#1E130D] border border-[#DFC3AC] text-xs font-semibold transition cursor-pointer"
                    >
                      <Download className="w-3.5 h-3.5 text-[#8B5230]" />
                      Download
                    </button>

                    {canTransition && allowedNext.length > 0 && (
                      <button
                        onClick={() => {
                          setTransitionTarget(ev);
                          setNextStatus(allowedNext[0]);
                        }}
                        className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl cognac-gradient text-white text-xs font-semibold shadow-sm transition cursor-pointer"
                      >
                        <ArrowRightLeft className="w-3.5 h-3.5" />
                        Update Status
                      </button>
                    )}
                  </div>
                </div>

                {/* Collection & Law Enforcement Metadata */}
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mt-5 pt-4 border-t border-[#E6D7C3] text-xs">
                  <div className="p-2.5 rounded-xl bg-[#FAF6F0]/60 border border-[#EDE0D1]">
                    <span className="text-[10px] uppercase font-bold text-[#8B5230] block">Collection Source</span>
                    <span className="font-semibold text-[#1E130D]">{ev.sourceOrigin || 'N/A'}</span>
                  </div>
                  <div className="p-2.5 rounded-xl bg-[#FAF6F0]/60 border border-[#EDE0D1]">
                    <span className="text-[10px] uppercase font-bold text-[#8B5230] block">Collecting Officer</span>
                    <span className="font-semibold text-[#1E130D]">
                      {ev.seizingOfficerName || 'Internal'} {ev.officerBadgeNumber ? `(${ev.officerBadgeNumber})` : ''}
                    </span>
                  </div>
                  <div className="p-2.5 rounded-xl bg-[#FAF6F0]/60 border border-[#EDE0D1]">
                    <span className="text-[10px] uppercase font-bold text-[#8B5230] block">Agency & Report #</span>
                    <span className="font-semibold text-[#1E130D]">
                      {ev.lawEnforcementAgency || 'N/A'} {ev.policeIncidentReportNum ? `• ${ev.policeIncidentReportNum}` : ''}
                    </span>
                  </div>
                  <div className="p-2.5 rounded-xl bg-[#FAF6F0]/60 border border-[#EDE0D1]">
                    <span className="text-[10px] uppercase font-bold text-[#8B5230] block">Registered By</span>
                    <span className="font-semibold text-[#1E130D]">{ev.uploadedByName || `Staff #${ev.uploadedById}`}</span>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* MODAL 1: UPLOAD EVIDENCE */}
      {showUploadModal && (
        <div className="fixed inset-0 z-50 bg-black/60 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white rounded-3xl max-w-xl w-full overflow-hidden shadow-2xl border border-[#DFC3AC] max-h-[92vh] flex flex-col">
            <div className="espresso-gradient p-6 text-white flex items-center justify-between">
              <div>
                <h3 className="font-serif-legal text-2xl font-bold">
                  Upload Evidence Item
                </h3>
                <p className="text-xs text-[#DFC3AC]">
                  Attach a digital file and record its collection details
                </p>
              </div>
              <button
                onClick={() => setShowUploadModal(false)}
                className="p-1.5 rounded-lg bg-white/10 hover:bg-white/20 text-white"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleUploadSubmit} className="p-6 space-y-4 overflow-y-auto">
              {/* File Dropzone */}
              <div className="p-4 rounded-2xl bg-[#FAF6F0] border-2 border-dashed border-[#B0754F] text-center">
                <input
                  type="file"
                  id="evidence-file-input"
                  onChange={handleFileChange}
                  className="hidden"
                />
                <label
                  htmlFor="evidence-file-input"
                  className="cursor-pointer flex flex-col items-center"
                >
                  <Upload className="w-8 h-8 text-[#8B5230] mb-2" />
                  <span className="text-xs font-bold text-[#1E130D]">
                    {selectedFile
                      ? `${selectedFile.name} (${formatBytes(selectedFile.size)})`
                      : 'Click to Select Evidence File'}
                  </span>
                  <span className="text-[11px] text-[#6E3F23] mt-0.5">
                    Documents, recordings, device extractions, or system logs
                  </span>
                </label>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-semibold text-[#3E2A1E] mb-1">
                    Evidence Name *
                  </label>
                  <input
                    type="text"
                    required
                    minLength={2}
                    value={uploadForm.name}
                    onChange={(e) => setUploadForm({ ...uploadForm, name: e.target.value })}
                    placeholder="iPhone 16 Pro Extraction"
                    className="w-full px-3 py-2 rounded-xl bg-[#FAF6F0] border border-[#DFC3AC] text-xs"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-[#3E2A1E] mb-1">
                    Category *
                  </label>
                  <select
                    value={uploadForm.evidenceType}
                    onChange={(e) => setUploadForm({ ...uploadForm, evidenceType: e.target.value })}
                    className="w-full px-3 py-2 rounded-xl bg-[#FAF6F0] border border-[#DFC3AC] text-xs font-semibold"
                  >
                    <option value="DEVICE_IMAGE">Device Extraction</option>
                    <option value="NETWORK_LOG">Network Record</option>
                    <option value="VIDEO">Video Recording</option>
                    <option value="AUDIO">Audio Recording</option>
                    <option value="DOCUMENT">Document</option>
                  </select>
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-[#3E2A1E] mb-1">
                  Collection Source / Origin *
                </label>
                <input
                  type="text"
                  required
                  value={uploadForm.sourceOrigin}
                  onChange={(e) => setUploadForm({ ...uploadForm, sourceOrigin: e.target.value })}
                  placeholder="e.g., Officer Bodycam #4 / Suspect Residence"
                  className="w-full px-3 py-2 rounded-xl bg-[#FAF6F0] border border-[#DFC3AC] text-xs"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-[#3E2A1E] mb-1">
                  Description
                </label>
                <textarea
                  rows={2}
                  value={uploadForm.description}
                  onChange={(e) => setUploadForm({ ...uploadForm, description: e.target.value })}
                  placeholder="Summary of the evidence item..."
                  className="w-full px-3 py-2 rounded-xl bg-[#FAF6F0] border border-[#DFC3AC] text-xs"
                />
              </div>

              <div className="grid grid-cols-2 gap-3 pt-2 border-t border-[#E6D7C3]">
                <div>
                  <label className="block text-[11px] font-semibold text-[#3E2A1E] mb-1">
                    Collecting Officer Name
                  </label>
                  <input
                    type="text"
                    value={uploadForm.seizingOfficerName}
                    onChange={(e) =>
                      setUploadForm({ ...uploadForm, seizingOfficerName: e.target.value })
                    }
                    placeholder="Det. K. Miller"
                    className="w-full px-3 py-1.5 rounded-xl bg-[#FAF6F0] border border-[#DFC3AC] text-xs"
                  />
                </div>
                <div>
                  <label className="block text-[11px] font-semibold text-[#3E2A1E] mb-1">
                    Badge Number
                  </label>
                  <input
                    type="text"
                    value={uploadForm.officerBadgeNumber}
                    onChange={(e) =>
                      setUploadForm({ ...uploadForm, officerBadgeNumber: e.target.value })
                    }
                    placeholder="BADGE-4481"
                    className="w-full px-3 py-1.5 rounded-xl bg-[#FAF6F0] border border-[#DFC3AC] text-xs"
                  />
                </div>
                <div>
                  <label className="block text-[11px] font-semibold text-[#3E2A1E] mb-1">
                    Agency / Department
                  </label>
                  <input
                    type="text"
                    value={uploadForm.lawEnforcementAgency}
                    onChange={(e) =>
                      setUploadForm({ ...uploadForm, lawEnforcementAgency: e.target.value })
                    }
                    placeholder="Metro Police Dept"
                    className="w-full px-3 py-1.5 rounded-xl bg-[#FAF6F0] border border-[#DFC3AC] text-xs"
                  />
                </div>
                <div>
                  <label className="block text-[11px] font-semibold text-[#3E2A1E] mb-1">
                    Incident Report #
                  </label>
                  <input
                    type="text"
                    value={uploadForm.policeIncidentReportNum}
                    onChange={(e) =>
                      setUploadForm({ ...uploadForm, policeIncidentReportNum: e.target.value })
                    }
                    placeholder="IR-2026-88412"
                    className="w-full px-3 py-1.5 rounded-xl bg-[#FAF6F0] border border-[#DFC3AC] text-xs"
                  />
                </div>
              </div>

              <div className="flex justify-end gap-3 pt-4 border-t border-[#E6D7C3]">
                <button
                  type="button"
                  onClick={() => setShowUploadModal(false)}
                  className="px-4 py-2.5 rounded-xl bg-[#F5EFE6] text-[#3E2A1E] text-xs font-semibold"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={uploading}
                  className="px-5 py-2.5 rounded-xl cognac-gradient text-white text-xs font-semibold shadow-md cursor-pointer"
                >
                  {uploading ? 'Uploading...' : 'Save Evidence'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* MODAL 2: UPDATE EVIDENCE STATUS */}
      {transitionTarget && (
        <div className="fixed inset-0 z-50 bg-black/60 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white rounded-3xl max-w-md w-full overflow-hidden shadow-2xl border border-[#DFC3AC]">
            <div className="espresso-gradient p-6 text-white flex items-center justify-between">
              <div>
                <h3 className="font-serif-legal text-2xl font-bold">
                  Update Evidence Status
                </h3>
                <p className="text-xs text-[#DFC3AC]">
                  {transitionTarget.trackingCode} - Current Status: {transitionTarget.status.replace('_', ' ')}
                </p>
              </div>
              <button
                onClick={() => setTransitionTarget(null)}
                className="p-1.5 rounded-lg bg-white/10 hover:bg-white/20 text-white"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleTransitionSubmit} className="p-6 space-y-4">
              <div>
                <label className="block text-xs font-semibold text-[#3E2A1E] mb-1.5">
                  New Status
                </label>
                <select
                  value={nextStatus}
                  onChange={(e) => setNextStatus(e.target.value)}
                  className="w-full px-3.5 py-2.5 rounded-xl bg-[#FAF6F0] border border-[#DFC3AC] text-sm font-bold text-[#1E130D]"
                >
                  {(VALID_TRANSITIONS[transitionTarget.status] || []).map((st) => (
                    <option key={st} value={st}>
                      {transitionTarget.status.replace('_', ' ')} → {st.replace('_', ' ')}
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-xs font-semibold text-[#3E2A1E] mb-1.5">
                  Handling Notes
                </label>
                <textarea
                  rows={3}
                  value={transitionNotes}
                  onChange={(e) => setTransitionNotes(e.target.value)}
                  placeholder="Enter reason or notes for this status update..."
                  className="w-full px-3.5 py-2 rounded-xl bg-[#FAF6F0] border border-[#DFC3AC] text-xs"
                />
              </div>

              <div className="flex justify-end gap-3 pt-3 border-t border-[#E6D7C3]">
                <button
                  type="button"
                  onClick={() => setTransitionTarget(null)}
                  className="px-4 py-2 rounded-xl bg-[#F5EFE6] text-[#3E2A1E] text-xs font-semibold"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-5 py-2 rounded-xl cognac-gradient text-white text-xs font-semibold shadow-md cursor-pointer"
                >
                  Update Status
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
