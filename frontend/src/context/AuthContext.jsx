import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import { authApi, isDemoMode, setConnectionMode } from '../api/vaultService';
import { DEMO_USERS } from '../api/demoData';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => {
    try {
      return JSON.parse(localStorage.getItem('vv_user') || 'null');
    } catch {
      return null;
    }
  });
  const [token, setToken] = useState(() => localStorage.getItem('vv_token') || null);
  const [mode, setMode] = useState(() => localStorage.getItem('vv_mode') || 'live');
  const [notifications, setNotifications] = useState([]);
  const [sseConnected, setSseConnected] = useState(false);

  const pushNotification = useCallback((title, message, type = 'info') => {
    const id = Date.now() + Math.random();
    setNotifications((prev) => [{ id, title, message, type, time: new Date() }, ...prev.slice(0, 14)]);
  }, []);

  // Connect to Spring Boot SSE stream (/api/notifications/stream?token=...) when authenticated in live mode
  useEffect(() => {
    if (!token || !user || user.role === 'ROLE_CLIENT') {
      setSseConnected(false);
      return;
    }

    if (mode === 'demo') {
      setSseConnected(true);
      return;
    }

    let eventSource;
    try {
      eventSource = new EventSource(`/api/notifications/stream?token=${encodeURIComponent(token)}`);

      eventSource.addEventListener('CONNECTED', () => {
        setSseConnected(true);
        pushNotification('Notifications Connected', 'Real-time case updates are active.', 'success');
      });

      const handleEvent = (eventName) => (e) => {
        try {
          const parsed = JSON.parse(e.data);
          pushNotification(
            eventName.replace(/_/g, ' '),
            parsed.evidenceName
              ? `${parsed.evidenceTrackingCode || ''} - ${parsed.evidenceName}`
              : e.data,
            'info'
          );
        } catch {
          pushNotification(eventName.replace(/_/g, ' '), e.data, 'info');
        }
      };

      eventSource.addEventListener('CUSTODY_RESERVATION_CREATED', handleEvent('CUSTODY_RESERVATION_CREATED'));
      eventSource.addEventListener('CUSTODY_RESERVATION_CANCELLED', handleEvent('CUSTODY_RESERVATION_CANCELLED'));
      eventSource.addEventListener('EVIDENCE_CHECKED_OUT', handleEvent('EVIDENCE_CHECKED_OUT'));
      eventSource.addEventListener('EVIDENCE_CHECKED_IN', handleEvent('EVIDENCE_CHECKED_IN'));

      eventSource.onerror = () => {
        setSseConnected(false);
      };
    } catch {
      setSseConnected(false);
    }

    return () => {
      if (eventSource) eventSource.close();
    };
  }, [token, user, mode, pushNotification]);

  const login = async (email, password, targetMode = mode) => {
    setConnectionMode(targetMode);
    setMode(targetMode);
    const res = await authApi.login(email, password);
    const userObj = {
      userId: res.userId,
      email: res.email,
      fullName: res.fullName,
      role: res.role,
    };
    localStorage.setItem('vv_token', res.token);
    localStorage.setItem('vv_user', JSON.stringify(userObj));
    setToken(res.token);
    setUser(userObj);
    pushNotification('Vault Session Authenticated', `Signed in as ${res.fullName} (${res.role.replace('ROLE_', '')})`, 'success');
    return userObj;
  };

  const loginDemoPersona = (demoUser = DEMO_USERS[1]) => {
    setConnectionMode('demo');
    setMode('demo');
    const userObj = {
      userId: demoUser.id,
      email: demoUser.email,
      fullName: demoUser.fullName,
      role: demoUser.role,
    };
    const fakeToken = 'demo-jwt-token-' + demoUser.role;
    localStorage.setItem('vv_token', fakeToken);
    localStorage.setItem('vv_user', JSON.stringify(userObj));
    setToken(fakeToken);
    setUser(userObj);
    pushNotification(
      'Interactive Showcase Active',
      `Viewing vault as ${demoUser.fullName} (${demoUser.role.replace('ROLE_', '')})`,
      'info'
    );
  };

  const switchMode = (newMode) => {
    setConnectionMode(newMode);
    setMode(newMode);
    if (newMode === 'demo' && !user) {
      loginDemoPersona(DEMO_USERS[1]);
    }
  };

  const logout = () => {
    localStorage.removeItem('vv_token');
    localStorage.removeItem('vv_user');
    setToken(null);
    setUser(null);
    setSseConnected(false);
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        token,
        mode,
        isDemo: mode === 'demo',
        sseConnected,
        notifications,
        pushNotification,
        login,
        loginDemoPersona,
        switchMode,
        logout,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  return useContext(AuthContext);
}
