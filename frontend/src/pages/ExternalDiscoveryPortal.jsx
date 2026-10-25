import React, { useState, useEffect } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import {
  Scale,
  ShieldCheck,
  KeyRound,
  Download,
  ArrowLeft,
  CheckCircle2,
  AlertCircle,
  FileText,
} from 'lucide-react';
import { discoveryApi, evidenceApi } from '../api/vaultService';
import { extractErrorMessage } from '../api/client';

export default function ExternalDiscoveryPortal() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();

  const [token, setToken] = useState(
    searchParams.get('token') || 'vv-disc-9f8a7b6c-2026-sdny-token'
  );
  const [packageData, setPackageData] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const handleInspect = async (e) => {
    if (e) e.preventDefault();
    if (!token.trim()) return;
    setLoading(true);
    setError('');
    try {
      const data = await discoveryApi.accessByToken(token.trim());
      setPackageData(data);
    } catch (err) {
      setError(extractErrorMessage(err));
      setPackageData(null);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (searchParams.get('token')) {
      handleInspect();
    }
  }, []);

  const handleDownloadBundle = async () => {
    setError('');
    try {
      await discoveryApi.downloadBundle(token, packageData);
    } catch (err) {
      setError(extractErrorMessage(err));
    }
  };

  const handleDownloadSingleItem = async (ev, index) => {
    setError('');
    try {
      if (index === 0) {
        await discoveryApi.downloadBundle(token, { ...packageData, evidenceItems: [ev] });
      } else {
        await evidenceApi.downloadEvidence(
          ev.id,
          ev.originalFileName || ev.name,
          ev.fileStoragePath
        );
      }
    } catch (err) {
      setError(extractErrorMessage(err));
    }
  };

  return (
    <div className="min-h-screen bg-vault-pattern flex flex-col">
      {/* Top Bar */}
      <header className="espresso-gradient text-white px-8 py-5 flex items-center justify-between border-b border-[#4A3222]">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl cognac-gradient flex items-center justify-center">
            <Scale className="w-5 h-5 text-white" />
          </div>
          <div>
            <span className="font-serif-legal text-2xl font-bold">
              VeritasVault - Opposing Counsel Portal
            </span>
            <p className="text-[11px] text-[#CA9874] uppercase tracking-wider">
              External Legal Discovery Access
            </p>
          </div>
        </div>

        <button
          onClick={() => navigate('/dashboard')}
          className="inline-flex items-center gap-2 px-4 py-2 rounded-xl bg-[#2C1D14] hover:bg-[#3E2A1E] text-[#EDE0D1] border border-[#5A3B28] text-xs font-semibold cursor-pointer"
        >
          <ArrowLeft className="w-4 h-4" />
          Back to Workspace
        </button>
      </header>

      <main className="flex-1 max-w-4xl w-full mx-auto p-8 space-y-6">
        <div className="card-elevated rounded-3xl p-8 bg-white">
          <h1 className="font-serif-legal text-3xl font-bold text-[#1E130D]">
            Access Shared Discovery Files
          </h1>
          <p className="text-xs text-[#6E3F23] mt-1">
            Enter the access code provided to your firm to view and download the shared legal exhibits.
          </p>

          <form onSubmit={handleInspect} className="mt-5 flex flex-col sm:flex-row gap-3">
            <div className="relative flex-1">
              <KeyRound className="w-4 h-4 text-[#8B5230] absolute left-3.5 top-3.5" />
              <input
                type="text"
                required
                value={token}
                onChange={(e) => setToken(e.target.value)}
                placeholder="Enter discovery access code..."
                className="w-full pl-10 pr-4 py-3 rounded-xl bg-[#FAF6F0] border border-[#DFC3AC] text-sm font-mono-hash focus:outline-none focus:border-[#8B5230]"
              />
            </div>
            <button
              type="submit"
              disabled={loading}
              className="px-6 py-3 rounded-xl cognac-gradient text-white font-semibold text-sm shadow-md cursor-pointer"
            >
              {loading ? 'Opening...' : 'View Package'}
            </button>
          </form>

          {error && (
            <div className="mt-5 p-4 rounded-2xl bg-red-50 border border-red-200 text-red-900 text-xs flex items-center gap-2.5">
              <AlertCircle className="w-4.5 h-4.5 text-red-700 shrink-0" />
              <span>{error}</span>
            </div>
          )}
        </div>

        {packageData && (
          <div className="card-elevated rounded-3xl overflow-hidden bg-white">
            <div className="espresso-gradient p-6 text-white flex flex-col sm:flex-row sm:items-center justify-between gap-4">
              <div>
                <div className="inline-flex items-center gap-1.5 text-xs font-bold text-emerald-300 mb-1">
                  <CheckCircle2 className="w-4 h-4" />
                  Access Confirmed
                </div>
                <h2 className="font-serif-legal text-2xl font-bold">{packageData.title}</h2>
                <p className="text-xs text-[#DFC3AC]">
                  Case: {packageData.caseNumber} • Prepared for: {packageData.recipientParty}
                </p>
              </div>

              <button
                onClick={handleDownloadBundle}
                className="shrink-0 inline-flex items-center gap-2 px-5 py-3 rounded-xl cognac-gradient text-white text-xs font-bold shadow-lg border border-[#CA9874]/40 cursor-pointer"
              >
                <Download className="w-4 h-4" />
                Download All Files
              </button>
            </div>

            <div className="p-6 space-y-4">
              <p className="text-xs text-[#5A3E2B]">{packageData.description}</p>

              <h3 className="text-xs font-bold uppercase tracking-wider text-[#8B5230] pt-2">
                Included Evidence Files
              </h3>

              <div className="space-y-3">
                {(packageData.evidenceItems || []).map((ev, index) => (
                  <div
                    key={ev.id}
                    className="p-4 rounded-2xl bg-[#FAF6F0] border border-[#DFC3AC] flex flex-col sm:flex-row sm:items-center justify-between gap-3"
                  >
                    <div className="flex items-center gap-3">
                      <FileText className="w-5 h-5 text-[#8B5230] shrink-0" />
                      <div>
                        <div className="text-xs font-bold text-[#1E130D]">
                          [{ev.trackingCode}] {ev.name}
                        </div>
                        <div className="text-[11px] text-[#6E3F23]">
                          {ev.description || ev.sourceOrigin}
                        </div>
                      </div>
                    </div>

                    <div className="flex items-center gap-2.5 shrink-0">
                      <span className="inline-flex items-center gap-1 text-[11px] font-bold text-emerald-800 bg-emerald-950/10 px-2.5 py-1 rounded-full">
                        <ShieldCheck className="w-3.5 h-3.5" /> Official Copy
                      </span>
                      <button
                        type="button"
                        onClick={() => handleDownloadSingleItem(ev, index)}
                        className="inline-flex items-center gap-1.5 px-3.5 py-1.5 rounded-xl bg-white hover:bg-[#EDE0D1] text-[#1E130D] border border-[#DFC3AC] text-xs font-semibold shadow-xs transition cursor-pointer"
                      >
                        <Download className="w-3.5 h-3.5 text-[#8B5230]" />
                        Download
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        )}
      </main>
    </div>
  );
}
