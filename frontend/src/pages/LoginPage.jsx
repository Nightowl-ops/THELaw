import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Scale,
  ShieldCheck,
  Lock,
  Mail,
  User,
  KeyRound,
  Sparkles,
  ArrowRight,
  CheckCircle2,
  AlertCircle,
  Server,
  FileCheck2,
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { authApi } from '../api/vaultService';
import { extractErrorMessage } from '../api/client';
import { DEMO_USERS } from '../api/demoData';

const ROLE_LABELS = {
  ROLE_ADMIN: 'Administrator',
  ROLE_ATTORNEY: 'Lead Attorney',
  ROLE_FORENSIC_EXAMINER: 'Forensic Examiner',
  ROLE_CLIENT: 'Client',
};

export default function LoginPage() {
  const { login, loginDemoPersona, mode, switchMode } = useAuth();
  const navigate = useNavigate();

  const [tab, setTab] = useState('login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [fullName, setFullName] = useState('');
  const [verifyToken, setVerifyToken] = useState('');
  const [resetToken, setResetToken] = useState('');
  const [newPassword, setNewPassword] = useState('');

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [successMsg, setSuccessMsg] = useState('');

  const handleLogin = async (e) => {
    e.preventDefault();
    setError('');
    setSuccessMsg('');
    setLoading(true);
    try {
      await login(email, password, mode);
      navigate('/dashboard');
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setLoading(false);
    }
  };

  const handleRegister = async (e) => {
    e.preventDefault();
    setError('');
    setSuccessMsg('');
    setLoading(true);
    try {
      await authApi.register({ fullName, email, password });
      setSuccessMsg(
        'Account created! Please check your email for your verification code and enter it below.'
      );
      setTab('verify');
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setLoading(false);
    }
  };

  const handleVerify = async (e) => {
    e.preventDefault();
    setError('');
    setSuccessMsg('');
    setLoading(true);
    try {
      const msg = await authApi.verifyEmail(verifyToken.trim());
      setSuccessMsg(msg || 'Email verified! You can now sign in.');
      setTab('login');
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setLoading(false);
    }
  };

  const handleForgot = async (e) => {
    e.preventDefault();
    setError('');
    setSuccessMsg('');
    setLoading(true);
    try {
      if (resetToken) {
        const msg = await authApi.resetPassword(resetToken.trim(), newPassword);
        setSuccessMsg(msg || 'Password reset complete! Please sign in.');
        setTab('login');
      } else {
        const msg = await authApi.forgotPassword(email);
        setSuccessMsg(msg || 'Recovery instructions sent! Enter your recovery code below.');
      }
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setLoading(false);
    }
  };

  const handleQuickDemo = (demoUser) => {
    loginDemoPersona(demoUser);
    navigate('/dashboard');
  };

  return (
    <div className="min-h-screen flex flex-col lg:flex-row bg-[#FAF6F0]">
      {/* Left Column: Deep Espresso Legal Showcase */}
      <div className="lg:w-7/12 espresso-gradient text-[#F5EFE6] p-10 lg:p-16 flex flex-col justify-between relative overflow-hidden">
        <div className="absolute -top-24 -left-24 w-96 h-96 rounded-full bg-[#8B5230]/20 blur-3xl pointer-events-none" />
        <div className="absolute bottom-0 right-0 w-112 h-112 rounded-full bg-[#B0754F]/15 blur-3xl pointer-events-none" />

        <div className="relative z-10">
          <div className="flex items-center gap-3.5">
            <div className="w-12 h-12 rounded-2xl cognac-gradient flex items-center justify-center shadow-xl border border-[#CA9874]/40">
              <Scale className="w-6.5 h-6.5 text-white" />
            </div>
            <div>
              <span className="font-serif-legal text-3xl font-bold tracking-wide text-white">
                VeritasVault
              </span>
              <p className="text-xs uppercase tracking-widest text-[#CA9874] font-semibold">
                Legal Case & Digital Evidence Platform
              </p>
            </div>
          </div>

          <div className="mt-12 max-w-xl">
            <div className="inline-flex items-center gap-2 px-3.5 py-1.5 rounded-full bg-[#2C1D14] border border-[#5A3B28] text-[#DFC3AC] text-xs font-medium mb-6">
              <ShieldCheck className="w-4 h-4 text-[#CA9874]" />
              Courtroom-Ready Digital Custody & Case Management
            </div>
            <h1 className="font-serif-legal text-4xl lg:text-5xl font-bold text-white leading-tight">
              Trusted Custody for High-Stakes Legal Teams.
            </h1>
            <p className="mt-5 text-[#DFC3AC] text-base leading-relaxed">
              Manage court dockets, organize digital evidence, track every custodial handoff,
              prepare trial exhibit binders, and share discovery packages with opposing counsel
              in one unified workspace.
            </p>
          </div>

          {/* 3 Clean Legal Pillars */}
          <div className="mt-10 grid grid-cols-1 sm:grid-cols-3 gap-4 max-w-2xl">
            <div className="p-4 rounded-2xl bg-[#261810]/90 border border-[#4A3222]">
              <div className="text-xs text-[#CA9874] font-semibold uppercase tracking-wider">Evidence Vault</div>
              <div className="text-sm font-bold text-white mt-1">Protected Intake</div>
              <p className="text-xs text-[#C9B29B] mt-1">
                Organize case files, recordings, and digital extractions with complete origin records.
              </p>
            </div>
            <div className="p-4 rounded-2xl bg-[#261810]/90 border border-[#4A3222]">
              <div className="text-xs text-[#CA9874] font-semibold uppercase tracking-wider">Chain of Custody</div>
              <div className="text-sm font-bold text-white mt-1">Complete Audit Trail</div>
              <p className="text-xs text-[#C9B29B] mt-1">
                Track every check-out, lab review, and status update in a clear timeline.
              </p>
            </div>
            <div className="p-4 rounded-2xl bg-[#261810]/90 border border-[#4A3222]">
              <div className="text-xs text-[#CA9874] font-semibold uppercase tracking-wider">Trial & Discovery</div>
              <div className="text-sm font-bold text-white mt-1">Binders & Sharing</div>
              <p className="text-xs text-[#C9B29B] mt-1">
                Assemble numbered trial exhibits and deliver discovery packages with access receipts.
              </p>
            </div>
          </div>
        </div>

        {/* Bottom Instant Preview Bar */}
        <div className="relative z-10 mt-12 pt-8 border-t border-[#3E2A1E]">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
            <div>
              <span className="text-xs uppercase tracking-wider text-[#CA9874] font-bold flex items-center gap-1.5">
                <Sparkles className="w-4 h-4" />
                Quick Role Preview
              </span>
              <p className="text-xs text-[#C9B29B] mt-0.5">
                Preview the workspace with sample cases and exhibits under any role:
              </p>
            </div>
            <button
              onClick={() => navigate('/external-discovery')}
              className="shrink-0 inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl bg-[#2C1D14] hover:bg-[#3E2A1E] text-[#EDE0D1] border border-[#5A3B28] text-xs font-semibold transition"
            >
              <FileCheck2 className="w-4 h-4 text-[#CA9874]" />
              Opposing Counsel Portal
            </button>
          </div>

          <div className="grid grid-cols-2 sm:grid-cols-4 gap-2.5 mt-4">
            {DEMO_USERS.map((du) => (
              <button
                key={du.id}
                onClick={() => handleQuickDemo(du)}
                className="group p-3 rounded-xl bg-[#2A1B12] hover:bg-[#8B5230] border border-[#4A3222] hover:border-[#CA9874] text-left transition-all shadow-md"
              >
                <div className="text-[10px] font-bold uppercase tracking-wider text-[#CA9874] group-hover:text-[#FAF6F0]">
                  {ROLE_LABELS[du.role] || du.role}
                </div>
                <div className="text-xs font-semibold text-white truncate mt-0.5">
                  {du.fullName}
                </div>
                <div className="text-[11px] text-[#C9B29B] group-hover:text-white/90 flex items-center gap-1 mt-1.5">
                  Open Workspace <ArrowRight className="w-3 h-3" />
                </div>
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* Right Column: Crisp White & Warm Brown Authentication Card */}
      <div className="lg:w-5/12 flex items-center justify-center p-8 lg:p-12 bg-vault-pattern">
        <div className="w-full max-w-md card-elevated rounded-3xl p-8 bg-white">
          {/* Mode Pill */}
          <div className="flex items-center justify-between bg-[#F5EFE6] p-1.5 rounded-2xl border border-[#DFC3AC] mb-6">
            <button
              type="button"
              onClick={() => switchMode('live')}
              className={`flex-1 flex items-center justify-center gap-1.5 py-2 rounded-xl text-xs font-semibold transition ${
                mode === 'live'
                  ? 'bg-[#1E130D] text-white shadow-sm'
                  : 'text-[#6E3F23] hover:text-[#1E130D]'
              }`}
            >
              <Server className="w-3.5 h-3.5" />
              Live System
            </button>
            <button
              type="button"
              onClick={() => switchMode('demo')}
              className={`flex-1 flex items-center justify-center gap-1.5 py-2 rounded-xl text-xs font-semibold transition ${
                mode === 'demo'
                  ? 'bg-[#8B5230] text-white shadow-sm'
                  : 'text-[#6E3F23] hover:text-[#1E130D]'
              }`}
            >
              <Sparkles className="w-3.5 h-3.5" />
              Sample Preview
            </button>
          </div>

          {/* Auth Navigation Tabs */}
          <div className="flex border-b border-[#E6D7C3] mb-6">
            {[
              { id: 'login', label: 'Sign In' },
              { id: 'register', label: 'Register' },
              { id: 'verify', label: 'Verify Email' },
              { id: 'forgot', label: 'Recovery' },
            ].map((t) => (
              <button
                key={t.id}
                type="button"
                onClick={() => {
                  setTab(t.id);
                  setError('');
                  setSuccessMsg('');
                }}
                className={`flex-1 pb-3 text-xs font-bold uppercase tracking-wider border-b-2 transition ${
                  tab === t.id
                    ? 'border-[#8B5230] text-[#1E130D]'
                    : 'border-transparent text-[#8C735B] hover:text-[#3E2A1E]'
                }`}
              >
                {t.label}
              </button>
            ))}
          </div>

          {error && (
            <div className="mb-5 p-3.5 rounded-xl bg-red-50 border border-red-200 text-red-900 text-xs flex items-start gap-2.5">
              <AlertCircle className="w-4 h-4 text-red-700 shrink-0 mt-0.5" />
              <div>{error}</div>
            </div>
          )}

          {successMsg && (
            <div className="mb-5 p-3.5 rounded-xl bg-emerald-50 border border-emerald-200 text-emerald-900 text-xs flex items-start gap-2.5">
              <CheckCircle2 className="w-4 h-4 text-emerald-700 shrink-0 mt-0.5" />
              <div>{successMsg}</div>
            </div>
          )}

          {/* TAB 1: SIGN IN */}
          {tab === 'login' && (
            <form onSubmit={handleLogin} className="space-y-4">
              <div>
                <h2 className="font-serif-legal text-3xl font-bold text-[#1E130D]">
                  Sign In to Your Account
                </h2>
                <p className="text-xs text-[#6E3F23] mt-1">
                  Enter your email and password to access your cases and evidence.
                </p>
              </div>

              <div>
                <label className="block text-xs font-semibold text-[#3E2A1E] mb-1.5">
                  Email Address
                </label>
                <div className="relative">
                  <Mail className="w-4 h-4 text-[#8B5230] absolute left-3.5 top-3" />
                  <input
                    type="email"
                    required
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    placeholder="attorney@veritasvault.com"
                    className="w-full pl-10 pr-4 py-2.5 rounded-xl bg-[#FAF6F0] border border-[#DFC3AC] text-sm focus:outline-none focus:border-[#8B5230] focus:bg-white transition"
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-[#3E2A1E] mb-1.5">
                  Password
                </label>
                <div className="relative">
                  <Lock className="w-4 h-4 text-[#8B5230] absolute left-3.5 top-3" />
                  <input
                    type="password"
                    required
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    placeholder="••••••••••••"
                    className="w-full pl-10 pr-4 py-2.5 rounded-xl bg-[#FAF6F0] border border-[#DFC3AC] text-sm focus:outline-none focus:border-[#8B5230] focus:bg-white transition"
                  />
                </div>
              </div>

              <button
                type="submit"
                disabled={loading}
                className="w-full py-3 px-4 rounded-xl cognac-gradient hover:opacity-95 text-white font-semibold text-sm shadow-lg transition flex items-center justify-center gap-2 cursor-pointer"
              >
                {loading ? 'Signing In...' : 'Sign In'}
                <ArrowRight className="w-4 h-4" />
              </button>
            </form>
          )}

          {/* TAB 2: REGISTER */}
          {tab === 'register' && (
            <form onSubmit={handleRegister} className="space-y-4">
              <div>
                <h2 className="font-serif-legal text-3xl font-bold text-[#1E130D]">
                  Create Client Account
                </h2>
                <p className="text-xs text-[#6E3F23] mt-1">
                  Register as a client to view your assigned legal cases. Firm staff accounts are created by an Administrator.
                </p>
              </div>

              <div>
                <label className="block text-xs font-semibold text-[#3E2A1E] mb-1.5">
                  Full Name
                </label>
                <div className="relative">
                  <User className="w-4 h-4 text-[#8B5230] absolute left-3.5 top-3" />
                  <input
                    type="text"
                    required
                    minLength={2}
                    value={fullName}
                    onChange={(e) => setFullName(e.target.value)}
                    placeholder="Arthur Pendleton"
                    className="w-full pl-10 pr-4 py-2.5 rounded-xl bg-[#FAF6F0] border border-[#DFC3AC] text-sm focus:outline-none focus:border-[#8B5230] focus:bg-white"
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-[#3E2A1E] mb-1.5">
                  Email Address
                </label>
                <div className="relative">
                  <Mail className="w-4 h-4 text-[#8B5230] absolute left-3.5 top-3" />
                  <input
                    type="email"
                    required
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    placeholder="client@organization.com"
                    className="w-full pl-10 pr-4 py-2.5 rounded-xl bg-[#FAF6F0] border border-[#DFC3AC] text-sm focus:outline-none focus:border-[#8B5230] focus:bg-white"
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-[#3E2A1E] mb-1.5">
                  Password
                </label>
                <div className="relative">
                  <Lock className="w-4 h-4 text-[#8B5230] absolute left-3.5 top-3" />
                  <input
                    type="password"
                    required
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    placeholder="At least 8 characters"
                    className="w-full pl-10 pr-4 py-2.5 rounded-xl bg-[#FAF6F0] border border-[#DFC3AC] text-sm focus:outline-none focus:border-[#8B5230] focus:bg-white"
                  />
                </div>
                <p className="text-[11px] text-[#8C735B] mt-1">
                  Include an uppercase letter, lowercase letter, number, and symbol.
                </p>
              </div>

              <button
                type="submit"
                disabled={loading}
                className="w-full py-3 px-4 rounded-xl cognac-gradient text-white font-semibold text-sm shadow-lg transition cursor-pointer"
              >
                {loading ? 'Creating Account...' : 'Create Account'}
              </button>
            </form>
          )}

          {/* TAB 3: VERIFY EMAIL */}
          {tab === 'verify' && (
            <form onSubmit={handleVerify} className="space-y-4">
              <div>
                <h2 className="font-serif-legal text-3xl font-bold text-[#1E130D]">
                  Verify Your Email
                </h2>
                <p className="text-xs text-[#6E3F23] mt-1">
                  Enter the verification code sent to your email address to activate your account.
                </p>
              </div>

              <div>
                <label className="block text-xs font-semibold text-[#3E2A1E] mb-1.5">
                  Verification Code
                </label>
                <div className="relative">
                  <KeyRound className="w-4 h-4 text-[#8B5230] absolute left-3.5 top-3" />
                  <input
                    type="text"
                    required
                    value={verifyToken}
                    onChange={(e) => setVerifyToken(e.target.value)}
                    placeholder="Enter verification code..."
                    className="w-full pl-10 pr-4 py-2.5 rounded-xl bg-[#FAF6F0] border border-[#DFC3AC] text-sm focus:outline-none focus:border-[#8B5230]"
                  />
                </div>
              </div>

              <button
                type="submit"
                disabled={loading}
                className="w-full py-3 px-4 rounded-xl cognac-gradient text-white font-semibold text-sm shadow-lg transition cursor-pointer"
              >
                {loading ? 'Verifying...' : 'Verify Account'}
              </button>
            </form>
          )}

          {/* TAB 4: PASSWORD RECOVERY */}
          {tab === 'forgot' && (
            <form onSubmit={handleForgot} className="space-y-4">
              <div>
                <h2 className="font-serif-legal text-3xl font-bold text-[#1E130D]">
                  Reset Password
                </h2>
                <p className="text-xs text-[#6E3F23] mt-1">
                  Request a password recovery code or enter your code to choose a new password.
                </p>
              </div>

              <div>
                <label className="block text-xs font-semibold text-[#3E2A1E] mb-1.5">
                  Email Address
                </label>
                <input
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="attorney@veritasvault.com"
                  className="w-full px-4 py-2.5 rounded-xl bg-[#FAF6F0] border border-[#DFC3AC] text-sm focus:outline-none focus:border-[#8B5230]"
                />
              </div>

              <div className="pt-2 border-t border-[#E6D7C3]">
                <label className="block text-xs font-semibold text-[#3E2A1E] mb-1.5">
                  Have a Recovery Code? (Optional)
                </label>
                <input
                  type="text"
                  value={resetToken}
                  onChange={(e) => setResetToken(e.target.value)}
                  placeholder="Enter recovery code..."
                  className="w-full px-4 py-2 rounded-xl bg-[#FAF6F0] border border-[#DFC3AC] text-xs mb-2"
                />
                {resetToken && (
                  <input
                    type="password"
                    required
                    value={newPassword}
                    onChange={(e) => setNewPassword(e.target.value)}
                    placeholder="Enter new password"
                    className="w-full px-4 py-2 rounded-xl bg-[#FAF6F0] border border-[#DFC3AC] text-xs"
                  />
                )}
              </div>

              <button
                type="submit"
                disabled={loading}
                className="w-full py-3 px-4 rounded-xl cognac-gradient text-white font-semibold text-sm shadow-lg transition cursor-pointer"
              >
                {resetToken ? 'Update Password' : 'Send Recovery Code'}
              </button>
            </form>
          )}
        </div>
      </div>
    </div>
  );
}
