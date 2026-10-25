import React, { useState } from 'react';
import { NavLink, Outlet, useNavigate, useLocation } from 'react-router-dom';
import {
  ShieldCheck,
  Briefcase,
  HardDrive,
  History,
  FlaskConical,
  BookOpen,
  Share2,
  Users,
  UserCircle,
  LogOut,
  Bell,
  Sparkles,
  Server,
  ChevronRight,
  Scale,
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { DEMO_USERS } from '../api/demoData';

const ROLE_BADGES = {
  ROLE_ADMIN: { label: 'Administrator', bg: 'bg-[#8B5230] text-white border-[#B0754F]' },
  ROLE_ATTORNEY: { label: 'Lead Attorney', bg: 'bg-[#3E2A1E] text-[#F5EFE6] border-[#8B5230]' },
  ROLE_FORENSIC_EXAMINER: { label: 'Forensic Examiner', bg: 'bg-[#5A3825] text-[#FAF6F0] border-[#CA9874]' },
  ROLE_CLIENT: { label: 'Client', bg: 'bg-[#4A3528] text-[#EDE0D1] border-[#8B5230]' },
};

export default function AppLayout() {
  const { user, mode, notifications, loginDemoPersona, switchMode, logout } = useAuth();
  const [showNotifs, setShowNotifs] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();

  const navItems = [
    {
      to: '/dashboard',
      label: 'Cases & Dockets',
      sub: 'Overview & Active Cases',
      icon: Briefcase,
      roles: ['ROLE_ADMIN', 'ROLE_ATTORNEY', 'ROLE_FORENSIC_EXAMINER', 'ROLE_CLIENT'],
    },
    {
      to: '/evidence',
      label: 'Evidence Vault',
      sub: 'Digital Files & Records',
      icon: HardDrive,
      roles: ['ROLE_ADMIN', 'ROLE_ATTORNEY', 'ROLE_FORENSIC_EXAMINER'],
    },
    {
      to: '/custody-ledger',
      label: 'Chain of Custody',
      sub: 'Handling History',
      icon: History,
      roles: ['ROLE_ADMIN', 'ROLE_ATTORNEY', 'ROLE_FORENSIC_EXAMINER'],
    },
    {
      to: '/lab-reservations',
      label: 'Lab Schedule',
      sub: 'Appointments & Check-Out',
      icon: FlaskConical,
      roles: ['ROLE_ADMIN', 'ROLE_ATTORNEY', 'ROLE_FORENSIC_EXAMINER'],
    },
    {
      to: '/binders',
      label: 'Trial Binders',
      sub: 'Courtroom Exhibits',
      icon: BookOpen,
      roles: ['ROLE_ADMIN', 'ROLE_ATTORNEY', 'ROLE_FORENSIC_EXAMINER'],
    },
    {
      to: '/discovery',
      label: 'Discovery Sharing',
      sub: 'External Counsel Access',
      icon: Share2,
      roles: ['ROLE_ADMIN', 'ROLE_ATTORNEY'],
    },
    {
      to: '/admin-users',
      label: 'Team & Accounts',
      sub: 'Manage Staff & Permissions',
      icon: Users,
      roles: ['ROLE_ADMIN'],
    },
    {
      to: '/profile',
      label: 'My Profile',
      sub: 'Personal Info & Security',
      icon: UserCircle,
      roles: ['ROLE_ADMIN', 'ROLE_ATTORNEY', 'ROLE_FORENSIC_EXAMINER', 'ROLE_CLIENT'],
    },
  ];

  const visibleNav = navItems.filter((item) => !user?.role || item.roles.includes(user.role));
  const roleStyle = ROLE_BADGES[user?.role] || ROLE_BADGES.ROLE_ATTORNEY;

  return (
    <div className="min-h-screen flex bg-[#FAF6F0] text-[#1E130D]">
      {/* Deep Espresso Sidebar */}
      <aside className="w-76 shrink-0 espresso-gradient text-[#F5EFE6] flex flex-col justify-between border-r border-[#3E2A1E] shadow-2xl z-20">
        <div>
          {/* Brand Crest */}
          <div className="p-6 border-b border-[#3E2A1E]/80">
            <div className="flex items-center gap-3.5">
              <div className="w-11 h-11 rounded-xl cognac-gradient flex items-center justify-center shadow-lg border border-[#CA9874]/40">
                <Scale className="w-6 h-6 text-[#FAF6F0]" />
              </div>
              <div>
                <span className="font-serif-legal text-2xl font-bold tracking-wide text-white">
                  VeritasVault
                </span>
                <p className="text-xs text-[#CA9874] tracking-wider uppercase font-medium mt-0.5">
                  Legal & Evidence Management
                </p>
              </div>
            </div>
          </div>

          {/* Active User Card inside Sidebar */}
          <div className="mx-4 mt-5 p-4 rounded-xl bg-[#2A1B12]/90 border border-[#4A3222] shadow-inner">
            <div className="flex items-center justify-between mb-2">
              <span className={`text-[11px] font-semibold uppercase tracking-wider px-2.5 py-0.5 rounded-full border ${roleStyle.bg}`}>
                {roleStyle.label}
              </span>
            </div>
            <div className="flex items-center gap-3">
              <div className="w-9 h-9 rounded-xl bg-[#1E130D] border border-[#8B5230] overflow-hidden flex items-center justify-center shrink-0 relative">
                <UserCircle className="w-6 h-6 text-[#CA9874]" />
                {(user?.userId || user?.id) && (
                  <img
                    src={`/api/auth/users/${user?.userId || user?.id}/picture`}
                    alt=""
                    className="absolute inset-0 w-full h-full object-cover"
                    onError={(e) => {
                      e.currentTarget.style.display = 'none';
                    }}
                  />
                )}
              </div>
              <div className="min-w-0 flex-1">
                <p className="font-semibold text-sm text-white truncate">{user?.fullName}</p>
                <p className="text-xs text-[#C9B29B] truncate mt-0.5">{user?.email}</p>
              </div>
            </div>

            {mode === 'demo' && (
              <div className="mt-3 pt-3 border-t border-[#3E2A1E]">
                <p className="text-[10px] uppercase tracking-wider text-[#CA9874] mb-1.5 font-semibold">
                  Switch Role View:
                </p>
                <div className="grid grid-cols-2 gap-1.5">
                  {DEMO_USERS.map((du) => (
                    <button
                      key={du.role}
                      onClick={() => loginDemoPersona(du)}
                      className={`text-[11px] py-1 px-2 rounded-md text-left truncate transition font-medium ${
                        user?.role === du.role
                          ? 'bg-[#8B5230] text-white shadow-sm border border-[#CA9874]/50'
                          : 'bg-[#1E130D]/70 text-[#DFC3AC] hover:bg-[#3E2A1E]'
                      }`}
                    >
                      {ROLE_BADGES[du.role]?.label || du.role}
                    </button>
                  ))}
                </div>
              </div>
            )}
          </div>

          {/* Navigation Menu */}
          <nav className="px-3 mt-5 space-y-1">
            <p className="px-3 text-[10px] font-bold uppercase tracking-widest text-[#A68971] mb-2">
              Workspace
            </p>
            {visibleNav.map((item) => {
              const Icon = item.icon;
              return (
                <NavLink
                  key={item.to}
                  to={item.to}
                  className={({ isActive }) =>
                    `group flex items-center justify-between px-3.5 py-2.5 rounded-xl transition-all duration-150 ${
                      isActive
                        ? 'cognac-gradient text-white shadow-md border border-[#CA9874]/30'
                        : 'text-[#DFC3AC] hover:bg-[#2C1D14] hover:text-white'
                    }`
                  }
                >
                  <div className="flex items-center gap-3 min-w-0">
                    <Icon className="w-4.5 h-4.5 shrink-0 text-[#CA9874] group-hover:text-white" />
                    <div className="truncate">
                      <div className="text-sm font-medium leading-tight">{item.label}</div>
                      <div className="text-[11px] text-[#C9B29B]/80 truncate">{item.sub}</div>
                    </div>
                  </div>
                  <ChevronRight className="w-3.5 h-3.5 opacity-50 group-hover:opacity-100 shrink-0" />
                </NavLink>
              );
            })}
          </nav>
        </div>

        {/* Bottom Footer: Public Opposing Counsel Portal Shortcut + Logout */}
        <div className="p-4 border-t border-[#3E2A1E] space-y-2.5 bg-[#180F0A]/60">
          <button
            onClick={() => navigate('/external-discovery')}
            className="w-full flex items-center justify-center gap-2 py-2 px-3 rounded-lg bg-[#2C1D14] hover:bg-[#3E2A1E] text-[#EDE0D1] border border-[#5A3B28] text-xs font-medium transition"
          >
            <ShieldCheck className="w-4 h-4 text-[#CA9874]" />
            Opposing Counsel Portal
          </button>
          <button
            onClick={() => {
              logout();
              navigate('/login');
            }}
            className="w-full flex items-center justify-center gap-2 py-2 px-3 rounded-lg bg-[#3A1E1A]/80 hover:bg-[#522721] text-[#F5D0C5] text-xs font-medium transition"
          >
            <LogOut className="w-4 h-4" />
            Sign Out
          </button>
        </div>
      </aside>

      {/* Main Content Area */}
      <div className="flex-1 flex flex-col min-w-0 bg-vault-pattern">
        {/* Top Bar: White & Warm Brown Accent Header */}
        <header className="h-16 bg-white/95 backdrop-blur-md border-b border-[#E6D7C3] px-8 flex items-center justify-between sticky top-0 z-10 shadow-xs">
          <div className="flex items-center gap-4">
            <h2 className="font-serif-legal text-2xl font-bold text-[#1E130D]">
              {visibleNav.find((n) => location.pathname.startsWith(n.to))?.label || 'VeritasVault'}
            </h2>
          </div>

          {/* Right Controls: Mode Switcher + Notifications */}
          <div className="flex items-center gap-3">
            <div className="flex items-center bg-[#F5EFE6] p-1 rounded-xl border border-[#DFC3AC]">
              <button
                onClick={() => switchMode('live')}
                className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold transition ${
                  mode === 'live'
                    ? 'bg-[#1E130D] text-white shadow-xs'
                    : 'text-[#6E3F23] hover:text-[#1E130D]'
                }`}
              >
                <Server className="w-3.5 h-3.5" />
                Live System
              </button>
              <button
                onClick={() => switchMode('demo')}
                className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold transition ${
                  mode === 'demo'
                    ? 'bg-[#8B5230] text-white shadow-xs'
                    : 'text-[#6E3F23] hover:text-[#1E130D]'
                }`}
              >
                <Sparkles className="w-3.5 h-3.5" />
                Sample Preview
              </button>
            </div>

            {/* Notification Bell */}
            <div className="relative">
              <button
                onClick={() => setShowNotifs(!showNotifs)}
                className="relative p-2.5 rounded-xl bg-[#F5EFE6] hover:bg-[#EDE0D1] text-[#2C1D14] border border-[#DFC3AC] transition"
                title="Activity Notifications"
              >
                <Bell className="w-4.5 h-4.5" />
                {notifications.length > 0 && (
                  <span className="absolute -top-1 -right-1 w-4.5 h-4.5 rounded-full bg-[#8B5230] text-white text-[10px] font-bold flex items-center justify-center">
                    {notifications.length}
                  </span>
                )}
              </button>

              {showNotifs && (
                <div className="absolute right-0 mt-2 w-88 bg-white rounded-2xl shadow-2xl border border-[#DFC3AC] overflow-hidden z-50">
                  <div className="p-3.5 espresso-gradient text-white flex items-center justify-between">
                    <span className="font-semibold text-xs uppercase tracking-wider">
                      Recent Activity Alerts
                    </span>
                  </div>
                  <div className="max-h-80 overflow-y-auto divide-y divide-[#F5EFE6]">
                    {notifications.length === 0 ? (
                      <div className="p-6 text-center text-xs text-[#6E3F23]">
                        No new alerts right now.
                      </div>
                    ) : (
                      notifications.map((n) => (
                        <div key={n.id} className="p-3.5 hover:bg-[#FAF6F0] transition">
                          <div className="flex items-center justify-between">
                            <span className="text-xs font-bold text-[#1E130D]">{n.title}</span>
                            <span className="text-[10px] text-[#8B5230]">
                              {n.time.toLocaleTimeString()}
                            </span>
                          </div>
                          <p className="text-xs text-[#5A3E2B] mt-1">{n.message}</p>
                        </div>
                      ))
                    )}
                  </div>
                </div>
              )}
            </div>
          </div>
        </header>

        {/* Page Body */}
        <main className="flex-1 p-8 max-w-7xl w-full mx-auto">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
