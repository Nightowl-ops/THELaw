import React, { useState, useEffect, useCallback } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import {
  Share2,
  Plus,
  Copy,
  Check,
  Eye,
  ShieldCheck,
  ExternalLink,
  AlertCircle,
  X,
  Clock,
} from 'lucide-react';
import { casesApi, bindersApi, discoveryApi } from '../api/vaultService';
import { extractErrorMessage } from '../api/client';
import { useAuth } from '../context/AuthContext';

export default function DiscoveryPage() {
  const { mode, pushNotification } = useAuth();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();

  const [cases, setCases] = useState([]);
  const [selectedCaseId, setSelectedCaseId] = useState(searchParams.get('caseId') || '');
  const [binders, setBinders] = useState([]);
  const [productions, setProductions] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [copiedId, setCopiedId] = useState(null);

  // Access Receipts Modal
  const [activeReceiptsProd, setActiveReceiptsProd] = useState(null);
  const [receipts, setReceipts] = useState([]);
  const [loadingReceipts, setLoadingReceipts] = useState(false);

  // Create Production Modal
  const [showProduceModal, setShowProduceModal] = useState(false);
  const [selectedBinderId, setSelectedBinderId] = useState(
    searchParams.get('binderId') || ''
  );
  const [form, setForm] = useState({
    recipientParty: 'Opposing Counsel - Cross & Associates LLP',
    expiresAt: '2026-11-30T23:59',
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

  const fetchData = useCallback(async () => {
    if (!selectedCaseId) {
      setLoading(false);
      return;
    }
    setLoading(true);
    setError('');
    try {
      const [pRes, bRes] = await Promise.all([
        discoveryApi.getByCase(selectedCaseId),
        bindersApi.getBindersByCase(selectedCaseId),
      ]);
      setProductions(Array.isArray(pRes) ? pRes : pRes.content || []);
      const bList = bRes.content || (Array.isArray(bRes) ? bRes : []);
      setBinders(bList);
      setSelectedBinderId(bList.length > 0 ? String(bList[0].id) : '');
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }, [selectedCaseId, mode]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  const handleProduce = async (e) => {
    e.preventDefault();
    if (!selectedBinderId) {
      setError('Please select a Trial Binder to share.');
      return;
    }
    setError('');
    try {
      const normalizeTime = (t) => (t.length === 16 ? `${t}:00` : t);
      const created = await discoveryApi.createProduction(selectedBinderId, {
        caseId: Number(selectedCaseId),
        recipientParty: form.recipientParty,
        expiresAt: normalizeTime(form.expiresAt),
      });
      pushNotification(
        'Discovery Package Shared',
        `Created access code for ${created.recipientParty}`,
        'success'
      );
      setShowProduceModal(false);
      fetchData();
    } catch (err) {
      setError(extractErrorMessage(err));
    }
  };

  const inspectReceipts = async (prod) => {
    setActiveReceiptsProd(prod);
    setLoadingReceipts(true);
    try {
      const list = await discoveryApi.getReceipts(prod.id);
      setReceipts(Array.isArray(list) ? list : []);
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setLoadingReceipts(false);
    }
  };

  const copyToken = (id, token) => {
    navigator.clipboard.writeText(token);
    setCopiedId(id);
    setTimeout(() => setCopiedId(null), 2000);
  };

  return (
    <div className="space-y-6">
      {/* Header Banner */}
      <div className="espresso-gradient rounded-3xl p-7 text-white shadow-xl border border-[#4A3222] flex flex-col lg:flex-row lg:items-center justify-between gap-6">
        <div>
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-[#3E2A1E] border border-[#8B5230] text-[#DFC3AC] text-xs font-semibold mb-2">
            <ShieldCheck className="w-3.5 h-3.5 text-[#CA9874]" />
            Opposing Counsel Disclosures
          </div>
          <h1 className="font-serif-legal text-3xl font-bold">
            Discovery Sharing & Delivery Receipts
          </h1>
          <p className="text-xs text-[#DFC3AC] mt-1 max-w-2xl">
            Share trial exhibit bundles with opposing counsel using time-limited access codes and view delivery receipts confirming when files were opened or downloaded.
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-3 shrink-0">
          <button
            onClick={() => navigate('/external-discovery')}
            className="inline-flex items-center gap-2 px-4 py-3 rounded-xl bg-[#2C1D14] hover:bg-[#3E2A1E] text-[#EDE0D1] border border-[#5A3B28] text-xs font-semibold transition cursor-pointer"
          >
            <ExternalLink className="w-4 h-4 text-[#CA9874]" />
            Opposing Counsel Portal
          </button>
          <button
            onClick={() => setShowProduceModal(true)}
            className="inline-flex items-center gap-2 px-5 py-3 rounded-xl cognac-gradient hover:opacity-95 text-white font-semibold text-sm shadow-lg border border-[#CA9874]/40 transition cursor-pointer"
          >
            <Plus className="w-4.5 h-4.5" />
            Share Discovery Package
          </button>
        </div>
      </div>

      {/* Case Selector */}
      <div className="card-elevated rounded-2xl p-5 bg-white">
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

      {error && (
        <div className="p-4 rounded-2xl bg-red-50 border border-red-200 text-red-900 text-xs flex items-center gap-2.5">
          <AlertCircle className="w-4.5 h-4.5 text-red-700 shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {/* Productions List */}
      {loading ? (
        <div className="card-elevated rounded-3xl p-8 h-52 animate-pulse bg-white" />
      ) : productions.length === 0 ? (
        <div className="card-elevated rounded-3xl p-12 text-center bg-white">
          <Share2 className="w-12 h-12 text-[#8B5230] mx-auto mb-3 opacity-80" />
          <h3 className="font-serif-legal text-2xl font-bold text-[#1E130D]">
            No Discovery Packages Shared Yet
          </h3>
          <p className="text-xs text-[#6E3F23] mt-1">
            Click &ldquo;Share Discovery Package&rdquo; to create an access code for opposing counsel.
          </p>
        </div>
      ) : (
        <div className="space-y-5">
          {productions.map((prod) => (
            <div key={prod.id} className="card-elevated rounded-3xl p-6 bg-white">
              <div className="flex flex-col lg:flex-row lg:items-start justify-between gap-4">
                <div>
                  <div className="flex flex-wrap items-center gap-2 mb-2">
                    <span className="font-mono-hash text-xs font-bold px-2.5 py-1 rounded-lg bg-[#1E130D] text-white">
                      {prod.caseNumber}
                    </span>
                    <span className="text-xs font-bold px-3 py-1 rounded-full bg-emerald-950/10 text-emerald-900 border border-emerald-700/30">
                      Downloads Used: {prod.downloadCount || 0}
                    </span>
                    <span className="text-xs font-semibold text-[#6E3F23]">
                      Valid Until: {prod.expiresAt ? new Date(prod.expiresAt).toLocaleString() : 'No Expiration'}
                    </span>
                  </div>

                  <h3 className="font-serif-legal text-2xl font-bold text-[#1E130D]">
                    {prod.title}
                  </h3>
                  <p className="text-xs text-[#5A3E2B] mt-1">{prod.description}</p>
                  <p className="text-xs text-[#6E3F23] mt-2">
                    Recipient: <strong className="text-[#1E130D]">{prod.recipientParty}</strong>
                  </p>
                </div>

                <div className="flex flex-wrap items-center gap-2 shrink-0">
                  <button
                    onClick={() => inspectReceipts(prod)}
                    className="inline-flex items-center gap-1.5 px-4 py-2 rounded-xl bg-[#1E130D] hover:bg-[#3E2A1E] text-white text-xs font-semibold cursor-pointer"
                  >
                    <Eye className="w-3.5 h-3.5 text-[#CA9874]" />
                    Delivery Receipts
                  </button>

                  {prod.accessToken && (
                    <button
                      onClick={() =>
                        navigate(`/external-discovery?token=${encodeURIComponent(prod.accessToken)}`)
                      }
                      className="inline-flex items-center gap-1.5 px-4 py-2 rounded-xl cognac-gradient text-white text-xs font-semibold shadow-sm cursor-pointer"
                    >
                      <ExternalLink className="w-3.5 h-3.5" />
                      Open in Counsel Portal
                    </button>
                  )}
                </div>
              </div>

              {/* Access Code Bar */}
              {prod.accessToken && (
                <div className="mt-4 p-3.5 rounded-2xl bg-[#FAF6F0] border border-[#DFC3AC] flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                  <div className="flex items-center gap-2 min-w-0">
                    <span className="text-[11px] font-bold uppercase tracking-wider text-[#8B5230] shrink-0">
                      Access Code:
                    </span>
                    <code className="font-mono-hash text-xs text-[#1E130D] truncate bg-white px-2.5 py-1 rounded border border-[#E6D7C3]">
                      {prod.accessToken}
                    </code>
                  </div>

                  <button
                    onClick={() => copyToken(prod.id, prod.accessToken)}
                    className="shrink-0 inline-flex items-center gap-1.5 px-3 py-1 rounded-lg bg-white hover:bg-[#EDE0D1] text-[#3E2A1E] border border-[#DFC3AC] text-xs font-semibold cursor-pointer"
                  >
                    {copiedId === prod.id ? (
                      <>
                        <Check className="w-3.5 h-3.5 text-emerald-700" /> Copied Code
                      </>
                    ) : (
                      <>
                        <Copy className="w-3.5 h-3.5 text-[#8B5230]" /> Copy Code
                      </>
                    )}
                  </button>
                </div>
              )}
            </div>
          ))}
        </div>
      )}

      {/* MODAL 1: SHARE DISCOVERY PACKAGE */}
      {showProduceModal && (
        <div className="fixed inset-0 z-50 bg-black/60 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white rounded-3xl max-w-lg w-full overflow-hidden shadow-2xl border border-[#DFC3AC]">
            <div className="espresso-gradient p-6 text-white flex items-center justify-between">
              <div>
                <h3 className="font-serif-legal text-2xl font-bold">
                  Share Discovery Package
                </h3>
                <p className="text-xs text-[#DFC3AC]">
                  Select a trial binder and recipient details
                </p>
              </div>
              <button
                onClick={() => setShowProduceModal(false)}
                className="p-1.5 rounded-lg bg-white/10 hover:bg-white/20 text-white"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleProduce} className="p-6 space-y-4">
              <div>
                <label className="block text-xs font-semibold text-[#3E2A1E] mb-1">
                  Trial Exhibit Binder *
                </label>
                <select
                  required
                  value={selectedBinderId}
                  onChange={(e) => setSelectedBinderId(e.target.value)}
                  className="w-full px-3.5 py-2 rounded-xl bg-[#FAF6F0] border border-[#DFC3AC] text-xs font-bold"
                >
                  <option value="">Select Binder...</option>
                  {binders.map((b) => (
                    <option key={b.id} value={b.id}>
                      [{b.batesPrefix}] {b.name}
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-xs font-semibold text-[#3E2A1E] mb-1">
                  Recipient Firm / Party *
                </label>
                <input
                  type="text"
                  required
                  value={form.recipientParty}
                  onChange={(e) => setForm({ ...form, recipientParty: e.target.value })}
                  className="w-full px-3.5 py-2 rounded-xl bg-[#FAF6F0] border border-[#DFC3AC] text-xs"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-[#3E2A1E] mb-1">
                  Expiration Date *
                </label>
                <input
                  type="datetime-local"
                  required
                  value={form.expiresAt}
                  onChange={(e) => setForm({ ...form, expiresAt: e.target.value })}
                  className="w-full px-3.5 py-2 rounded-xl bg-[#FAF6F0] border border-[#DFC3AC] text-xs"
                />
              </div>

              <div className="flex justify-end gap-3 pt-3 border-t border-[#E6D7C3]">
                <button
                  type="button"
                  onClick={() => setShowProduceModal(false)}
                  className="px-4 py-2 rounded-xl bg-[#F5EFE6] text-[#3E2A1E] text-xs font-semibold"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-5 py-2 rounded-xl cognac-gradient text-white text-xs font-semibold shadow-md cursor-pointer"
                >
                  Create Access Code
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* MODAL 2: DELIVERY RECEIPTS */}
      {activeReceiptsProd && (
        <div className="fixed inset-0 z-50 bg-black/60 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white rounded-3xl max-w-2xl w-full overflow-hidden shadow-2xl border border-[#DFC3AC]">
            <div className="espresso-gradient p-6 text-white flex items-center justify-between">
              <div>
                <h3 className="font-serif-legal text-2xl font-bold">
                  Delivery & Access Receipts
                </h3>
                <p className="text-xs text-[#DFC3AC]">
                  {activeReceiptsProd.title}
                </p>
              </div>
              <button
                onClick={() => setActiveReceiptsProd(null)}
                className="p-1.5 rounded-lg bg-white/10 hover:bg-white/20 text-white"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="p-6 max-h-96 overflow-y-auto space-y-3">
              {loadingReceipts ? (
                <div className="text-center py-8 text-xs text-[#6E3F23]">
                  Loading delivery records...
                </div>
              ) : receipts.length === 0 ? (
                <div className="text-center py-8 text-xs text-[#6E3F23]">
                  Opposing counsel has not opened or downloaded this package yet.
                </div>
              ) : (
                receipts.map((r) => (
                  <div
                    key={r.id}
                    className="p-4 rounded-2xl bg-[#FAF6F0] border border-[#DFC3AC] flex flex-col sm:flex-row sm:items-center justify-between gap-2"
                  >
                    <div>
                      <div className="text-xs font-bold text-[#1E130D]">
                        {r.downloadedFileName === 'METADATA_VERIFICATION_INSPECTION'
                          ? 'Package Viewed in Portal'
                          : `Downloaded: ${r.downloadedFileName}`}
                      </div>
                      <p className="text-[11px] text-[#5A3E2B] mt-0.5">
                        Recorded from {r.ipAddress}
                      </p>
                    </div>
                    <div className="text-xs text-[#8B5230] shrink-0 flex items-center gap-1">
                      <Clock className="w-3.5 h-3.5" />
                      {r.accessedAt ? new Date(r.accessedAt).toLocaleString() : ''}
                    </div>
                  </div>
                ))
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
