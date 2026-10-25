import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import AppLayout from './components/AppLayout';
import LoginPage from './pages/LoginPage';
import DashboardPage from './pages/DashboardPage';
import EvidenceVaultPage from './pages/EvidenceVaultPage';
import ChainOfCustodyPage from './pages/ChainOfCustodyPage';
import LabReservationsPage from './pages/LabReservationsPage';
import ExhibitBindersPage from './pages/ExhibitBindersPage';
import DiscoveryPage from './pages/DiscoveryPage';
import ExternalDiscoveryPortal from './pages/ExternalDiscoveryPortal';
import AdminUsersPage from './pages/AdminUsersPage';
import ProfilePage from './pages/ProfilePage';

function ProtectedRoute({ children }) {
  const { user, token } = useAuth();
  if (!user || !token) {
    return <Navigate to="/login" replace />;
  }
  return children;
}

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          {/* Public Routes */}
          <Route path="/login" element={<LoginPage />} />
          <Route path="/external-discovery" element={<ExternalDiscoveryPortal />} />

          {/* Authenticated Custodial Chambers */}
          <Route
            path="/"
            element={
              <ProtectedRoute>
                <AppLayout />
              </ProtectedRoute>
            }
          >
            <Route index element={<Navigate to="/dashboard" replace />} />
            <Route path="dashboard" element={<DashboardPage />} />
            <Route path="evidence" element={<EvidenceVaultPage />} />
            <Route path="custody-ledger" element={<ChainOfCustodyPage />} />
            <Route path="lab-reservations" element={<LabReservationsPage />} />
            <Route path="binders" element={<ExhibitBindersPage />} />
            <Route path="discovery" element={<DiscoveryPage />} />
            <Route path="admin-users" element={<AdminUsersPage />} />
            <Route path="profile" element={<ProfilePage />} />
          </Route>

          {/* Fallback */}
          <Route path="*" element={<Navigate to="/dashboard" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}
