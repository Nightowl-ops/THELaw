import apiClient from './client';
import {
  DEMO_USERS,
  DEMO_CASES,
  DEMO_EVIDENCE,
  DEMO_CUSTODY_LOGS,
  DEMO_RESERVATIONS,
  DEMO_BINDERS,
  DEMO_DISCOVERIES,
  DEMO_RECEIPTS,
} from './demoData';

// Helper to check if we are currently in Interactive Demo Mode
export function isDemoMode() {
  return localStorage.getItem('vv_mode') === 'demo';
}

export function setConnectionMode(mode) {
  localStorage.setItem('vv_mode', mode); // 'live' or 'demo'
}

// Local in-memory mutable copies for Demo Mode so creating/updating items works live in the UI
let demoUsers = [...DEMO_USERS];
let demoCases = [...DEMO_CASES];
let demoEvidence = [...DEMO_EVIDENCE];
let demoCustodyLogs = JSON.parse(JSON.stringify(DEMO_CUSTODY_LOGS));
let demoReservations = [...DEMO_RESERVATIONS];
let demoBinders = JSON.parse(JSON.stringify(DEMO_BINDERS));
let demoDiscoveries = JSON.parse(JSON.stringify(DEMO_DISCOVERIES));
let demoReceipts = JSON.parse(JSON.stringify(DEMO_RECEIPTS));

// Real browser Web Crypto SHA-256 calculator (used to preview SHA-256 on file selection and in Demo Mode)
export async function computeBrowserSha256(file) {
  const arrayBuffer = await file.arrayBuffer();
  const hashBuffer = await crypto.subtle.digest('SHA-256', arrayBuffer);
  const hashArray = Array.from(new Uint8Array(hashBuffer));
  return hashArray.map((b) => b.toString(16).padStart(2, '0')).join('');
}

// ==========================================
// 1. AUTHENTICATION & PROFILE ENDPOINTS
// ==========================================
export const authApi = {
  async login(email, password) {
    if (isDemoMode()) {
      const found =
        demoUsers.find((u) => u.email.toLowerCase() === email.toLowerCase().trim()) ||
        demoUsers[1];
      return {
        token: 'demo-jwt-token-' + found.role,
        tokenType: 'Bearer',
        userId: found.id,
        email: found.email,
        fullName: found.fullName,
        role: found.role,
      };
    }
    const { data } = await apiClient.post('/auth/login', { email, password });
    return data;
  },

  async register(payload) {
    if (isDemoMode()) {
      const newUser = {
        id: demoUsers.length + 10,
        email: payload.email,
        fullName: payload.fullName,
        role: 'ROLE_CLIENT',
        status: 'ACTIVE',
        isEmailVerified: true,
        createdAt: new Date().toISOString(),
      };
      demoUsers.push(newUser);
      return {
        token: null,
        tokenType: null,
        userId: newUser.id,
        email: newUser.email,
        fullName: newUser.fullName,
        role: newUser.role,
      };
    }
    const { data } = await apiClient.post('/auth/register', payload);
    return data;
  },

  async verifyEmail(token) {
    if (isDemoMode()) {
      return 'Email verified successfully! You may now log in to VeritasVault.';
    }
    const { data } = await apiClient.get('/auth/verify', { params: { token } });
    return data;
  },

  async forgotPassword(email) {
    if (isDemoMode()) {
      return 'If an account with that email exists, password reset instructions have been dispatched.';
    }
    const { data } = await apiClient.post('/auth/forgot-password', { email });
    return data;
  },

  async resetPassword(token, newPassword) {
    if (isDemoMode()) {
      return 'Password has been successfully reset. You may now log in.';
    }
    const { data } = await apiClient.post('/auth/reset-password', { token, newPassword });
    return data;
  },

  async getMe() {
    if (isDemoMode()) {
      const savedUser = JSON.parse(localStorage.getItem('vv_user') || 'null');
      const match = demoUsers.find((u) => u.id === savedUser?.userId) || demoUsers[1];
      return match;
    }
    const { data } = await apiClient.get('/auth/me');
    return data;
  },

  async updateProfile(payload) {
    if (isDemoMode()) {
      const savedUser = JSON.parse(localStorage.getItem('vv_user') || 'null');
      const idx = demoUsers.findIndex((u) => u.id === savedUser?.userId);
      if (idx !== -1) {
        demoUsers[idx] = { ...demoUsers[idx], ...payload, updatedAt: new Date().toISOString() };
        return demoUsers[idx];
      }
      return { ...demoUsers[0], ...payload };
    }
    const { data } = await apiClient.put('/auth/profile', payload);
    return data;
  },

  async changePassword(currentPassword, newPassword) {
    if (isDemoMode()) {
      return 'Password changed successfully.';
    }
    const { data } = await apiClient.post('/auth/change-password', {
      currentPassword,
      newPassword,
    });
    return data;
  },

  async uploadProfilePicture(file) {
    if (isDemoMode()) {
      return new Promise((resolve) => {
        const reader = new FileReader();
        reader.onloadend = () => {
          const base64Url = reader.result;
          const savedUser = JSON.parse(localStorage.getItem('vv_user') || 'null');
          const idx = demoUsers.findIndex((u) => u.id === savedUser?.userId);
          if (idx !== -1) {
            demoUsers[idx] = { ...demoUsers[idx], profilePictureUrl: base64Url };
            resolve(demoUsers[idx]);
          } else {
            resolve({ profilePictureUrl: base64Url });
          }
        };
        reader.readAsDataURL(file);
      });
    }

    const formData = new FormData();
    formData.append('file', file);
    const { data } = await apiClient.post('/auth/profile/picture', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
    return data;
  },

  getProfilePictureUrl(userId) {
    if (!userId) return null;
    return `/api/auth/users/${userId}/picture`;
  },
};

// ==========================================
// 2. LEGAL CASES ENDPOINTS (/api/cases)
// ==========================================
export const casesApi = {
  async getCases({ status, type, search, page = 0, size = 20 } = {}) {
    if (isDemoMode()) {
      let filtered = [...demoCases];
      if (status) filtered = filtered.filter((c) => c.status === status);
      if (type) filtered = filtered.filter((c) => c.caseType === type);
      if (search) {
        const q = search.toLowerCase();
        filtered = filtered.filter(
          (c) =>
            c.caseNumber.toLowerCase().includes(q) ||
            c.caseTitle.toLowerCase().includes(q) ||
            (c.opposingPartyName || '').toLowerCase().includes(q)
        );
      }
      return { content: filtered, totalElements: filtered.length, totalPages: 1 };
    }
    const params = { page, size };
    if (status) params.status = status;
    if (type) params.type = type;
    params.search = search && search.trim() ? search.trim() : '%';
    const { data } = await apiClient.get('/cases', { params });
    return data;
  },

  async getCaseById(id) {
    if (isDemoMode()) {
      return demoCases.find((c) => c.id === Number(id)) || demoCases[0];
    }
    const { data } = await apiClient.get(`/cases/${id}`);
    return data;
  },

  async createCase(payload) {
    if (isDemoMode()) {
      const attorney = demoUsers.find((u) => u.id === Number(payload.leadAttorneyId)) || demoUsers[1];
      const client = demoUsers.find((u) => u.id === Number(payload.clientId));
      const created = {
        id: demoCases.length + 10,
        caseNumber: payload.caseNumber,
        caseTitle: payload.caseTitle,
        caseType: payload.caseType,
        status: 'ACTIVE',
        leadAttorneyId: attorney.id,
        leadAttorneyName: attorney.fullName,
        leadAttorneyEmail: attorney.email,
        clientId: client?.id || payload.clientId || null,
        clientName: client?.fullName || (payload.clientId ? `Client #${payload.clientId}` : null),
        clientEmail: client?.email || null,
        opposingPartyName: payload.opposingPartyName,
        opposingCounselName: payload.opposingCounselName,
        opposingCounselEmail: payload.opposingCounselEmail,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      };
      demoCases = [created, ...demoCases];
      return created;
    }
    const { data } = await apiClient.post('/cases', payload);
    return data;
  },

  async updateCaseStatus(id, status) {
    if (isDemoMode()) {
      const idx = demoCases.findIndex((c) => c.id === Number(id));
      if (idx !== -1) {
        demoCases[idx] = { ...demoCases[idx], status, updatedAt: new Date().toISOString() };
        return demoCases[idx];
      }
      return null;
    }
    const { data } = await apiClient.patch(`/cases/${id}/status`, null, { params: { status } });
    return data;
  },
};

// ==========================================
// 3. EVIDENCE VAULT ENDPOINTS (/api/evidence)
// ==========================================
export const evidenceApi = {
  async getEvidenceByCase(caseId, { status, type, page = 0, size = 50 } = {}) {
    if (isDemoMode()) {
      let items = demoEvidence.filter((e) => e.caseId === Number(caseId));
      if (status) items = items.filter((e) => e.status === status);
      if (type) items = items.filter((e) => e.evidenceType === type);
      return { content: items, totalElements: items.length, totalPages: 1 };
    }
    const params = { page, size };
    if (status) params.status = status;
    if (type) params.type = type;
    const { data } = await apiClient.get(`/evidence/case/${caseId}`, { params });
    return data;
  },

  async getEvidenceByTrackingCode(trackingCode) {
    if (isDemoMode()) {
      const item = demoEvidence.find(
        (e) => e.trackingCode.toLowerCase() === trackingCode.trim().toLowerCase()
      );
      if (!item) throw new Error(`No evidence found with tracking code ${trackingCode}`);
      return item;
    }
    const { data } = await apiClient.get(`/evidence/tracking/${encodeURIComponent(trackingCode.trim())}`);
    return data;
  },

  async uploadEvidence(file, metadata) {
    if (isDemoMode()) {
      const sha256Checksum = await computeBrowserSha256(file);
      const targetCase = demoCases.find((c) => c.id === Number(metadata.caseId)) || demoCases[0];
      const nextNum = String(demoEvidence.length + 1).padStart(4, '0');
      const newEvidence = {
        id: Date.now(),
        trackingCode: `EV-2026-${nextNum}`,
        caseId: Number(metadata.caseId),
        caseNumber: targetCase.caseNumber,
        name: metadata.name,
        description: metadata.description,
        evidenceType: metadata.evidenceType,
        status: 'INGESTED',
        fileStoragePath: `uploads/evidence/case-${metadata.caseId}/${file.name}`,
        originalFileName: file.name,
        mimeType: file.type || 'application/octet-stream',
        fileSizeBytes: file.size,
        sha256Checksum,
        sourceOrigin: metadata.sourceOrigin,
        seizingOfficerName: metadata.seizingOfficerName,
        officerBadgeNumber: metadata.officerBadgeNumber,
        lawEnforcementAgency: metadata.lawEnforcementAgency,
        policeIncidentReportNum: metadata.policeIncidentReportNum,
        uploadedById: 2,
        uploadedByName: 'Current Authenticated User',
        collectedAt: metadata.collectedAt || new Date().toISOString(),
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      };
      demoEvidence = [newEvidence, ...demoEvidence];
      demoCustodyLogs[newEvidence.id] = [
        {
          id: Date.now() + 1,
          evidenceId: newEvidence.id,
          evidenceTrackingCode: newEvidence.trackingCode,
          evidenceName: newEvidence.name,
          actorId: 2,
          actorName: 'Current Authenticated User',
          actorEmail: 'authenticated@veritasvault.law',
          actionType: 'INGEST',
          notes: 'Initial evidence intake and repository registration.',
          hashAtEvent: sha256Checksum,
          loggedAt: new Date().toISOString(),
        },
      ];
      return newEvidence;
    }

    // Spring Boot @RequestPart("file") MultipartFile + @RequestPart("data") EvidenceUploadRequest (application/json)
    const formData = new FormData();
    formData.append('file', file);
    formData.append(
      'data',
      new Blob([JSON.stringify(metadata)], { type: 'application/json' })
    );

    const { data } = await apiClient.post('/evidence/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return data;
  },

  async updateStatus(evidenceId, status, notes) {
    if (isDemoMode()) {
      const idx = demoEvidence.findIndex((e) => e.id === Number(evidenceId));
      if (idx === -1) throw new Error('Evidence not found');
      const prev = demoEvidence[idx];
      demoEvidence[idx] = { ...prev, status, updatedAt: new Date().toISOString() };

      const actionMap = {
        INGESTED: 'INGEST',
        AVAILABLE: 'CHECK_IN',
        RESERVED: 'CHECK_OUT',
        IN_LAB: 'CHECK_OUT',
        ADMITTED: 'COURT_SUBMISSION',
        SEQUESTERED: 'ANALYZE',
        STAMPED: 'REDACT',
      };
      const existingLogs = demoCustodyLogs[evidenceId] || [];
      existingLogs.push({
        id: Date.now(),
        evidenceId: prev.id,
        evidenceTrackingCode: prev.trackingCode,
        evidenceName: prev.name,
        actorId: 3,
        actorName: 'Dr. Marcus Chen, EnCE, CCE',
        actorEmail: 'dr.marcus.chen@forensics.org',
        actionType: actionMap[status] || 'ANALYZE',
        notes: notes || `Status transitioned from ${prev.status} to ${status}`,
        hashAtEvent: prev.sha256Checksum,
        loggedAt: new Date().toISOString(),
      });
      demoCustodyLogs[evidenceId] = existingLogs;
      return demoEvidence[idx];
    }

    const params = { status };
    if (notes) params.notes = notes;
    const { data } = await apiClient.patch(`/evidence/${evidenceId}/status`, null, { params });
    return data;
  },

  async downloadEvidence(evidenceId, fileName = 'evidence-item', storagePath = '') {
    if (isDemoMode()) {
      const item = demoEvidence.find((e) => e.id === Number(evidenceId)) || demoEvidence[0];
      const certContent = [
        '====================================================================',
        'VERITASVAULT CERTIFIED FORENSIC EVIDENCE MANIFEST',
        '====================================================================',
        `Tracking Code : ${item.trackingCode}`,
        `Evidence Name : ${item.name}`,
        `Case Docket   : ${item.caseNumber}`,
        `Integrity     : Verified`,
        `Status        : ${item.status}`,
        `Source Origin : ${item.sourceOrigin}`,
        `Timestamp     : ${new Date().toISOString()}`,
        '====================================================================',
      ].join('\n');
      const blob = new Blob([certContent], { type: 'text/plain' });
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `${item.trackingCode}_Manifest.txt`;
      a.click();
      window.URL.revokeObjectURL(url);
      return;
    }

    const response = await apiClient.get(`/evidence/${evidenceId}/download`, {
      responseType: 'blob',
    });

    // Dynamically preserve the real file extension from fileStoragePath (e.g. .pdf, .jpg, .png, .docx)
    const extMatch = String(storagePath || '').match(/(\.[a-zA-Z0-9]{1,8})$/);
    const realExt = extMatch ? extMatch[1] : '';
    let finalName = (fileName || `evidence-${evidenceId}`).trim();
    if (realExt && !finalName.toLowerCase().endsWith(realExt.toLowerCase())) {
      finalName = `${finalName}${realExt}`;
    }

    const url = window.URL.createObjectURL(new Blob([response.data]));
    const a = document.createElement('a');
    a.href = url;
    a.download = finalName;
    a.click();
    window.URL.revokeObjectURL(url);
  },

  async simulateTamper(evidenceId) {
    if (isDemoMode()) {
      const idx = demoEvidence.findIndex((e) => e.id === Number(evidenceId));
      if (idx !== -1) {
        demoEvidence[idx] = {
          ...demoEvidence[idx],
          sha256Checksum: 'ffffffff' + demoEvidence[idx].sha256Checksum.slice(8),
        };
      }
      return { message: 'Tampering simulated in demo mode' };
    }
    const { data } = await apiClient.post(`/evidence/${evidenceId}/simulate-tamper`);
    return data;
  },
};

// ==========================================
// 4. CHAIN OF CUSTODY ENDPOINTS (/api/custody/logs)
// ==========================================
export const custodyLogsApi = {
  async getAuditTrail(evidenceId) {
    if (isDemoMode()) {
      return demoCustodyLogs[Number(evidenceId)] || [];
    }
    const { data } = await apiClient.get(`/custody/logs/evidence/${evidenceId}`);
    return data;
  },
};

// ==========================================
// 5. LAB RESERVATIONS ENDPOINTS (/api/custody/reservations)
// ==========================================
export const reservationsApi = {
  async getByEvidence(evidenceId) {
    if (isDemoMode()) {
      const list = demoReservations.filter((r) => r.evidenceItemId === Number(evidenceId));
      return { content: list, totalElements: list.length };
    }
    const { data } = await apiClient.get(`/custody/reservations/evidence/${evidenceId}`);
    return data;
  },

  async getByExaminer(examinerId) {
    if (isDemoMode()) {
      return { content: demoReservations, totalElements: demoReservations.length };
    }
    const { data } = await apiClient.get(`/custody/reservations/examiner/${examinerId}`);
    return data;
  },

  async createReservation(payload) {
    const reqStart = new Date(payload.startTime);
    const reqEnd = new Date(payload.endTime);
    const now = new Date();

    if (reqStart < now) {
      throw new Error('Cannot schedule a lab session for a date or time that has already passed.');
    }
    if (reqStart >= reqEnd) {
      throw new Error('End time must be after the start time.');
    }

    if (isDemoMode()) {
      const ev = demoEvidence.find((e) => e.id === Number(payload.evidenceItemId)) || demoEvidence[0];
      const hasOverlap = demoReservations.some((r) => {
        if (r.evidenceItemId !== ev.id) return false;
        if (r.status !== 'CONFIRMED' && r.status !== 'ACTIVE') return false;
        const existingStart = new Date(r.startTime);
        const existingEnd = new Date(r.endTime);
        return existingStart < reqEnd && existingEnd > reqStart;
      });
      if (hasOverlap) {
        throw new Error('Time conflict: This evidence item is already booked during the requested time window.');
      }

      const created = {
        id: Date.now(),
        evidenceItemId: ev.id,
        evidenceTrackingCode: ev.trackingCode,
        evidenceName: ev.name,
        examinerId: 3,
        examinerName: 'Dr. Marcus Chen, EnCE, CCE',
        startTime: payload.startTime,
        endTime: payload.endTime,
        status: 'CONFIRMED',
        notes: payload.notes,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      };
      demoReservations = [created, ...demoReservations];
      return created;
    }
    const { data } = await apiClient.post('/custody/reservations', payload);
    return data;
  },

  async checkOut(reservationId) {
    if (isDemoMode()) {
      const idx = demoReservations.findIndex((r) => r.id === Number(reservationId));
      if (idx !== -1) {
        demoReservations[idx] = { ...demoReservations[idx], status: 'ACTIVE' };
        const evIdx = demoEvidence.findIndex((e) => e.id === demoReservations[idx].evidenceItemId);
        if (evIdx !== -1) {
          demoEvidence[evIdx] = { ...demoEvidence[evIdx], status: 'IN_LAB' };
          const ev = demoEvidence[evIdx];
          const existingLogs = demoCustodyLogs[ev.id] || [];
          existingLogs.push({
            id: Date.now(),
            evidenceId: ev.id,
            evidenceTrackingCode: ev.trackingCode,
            evidenceName: ev.name,
            actorId: 3,
            actorName: demoReservations[idx].examinerName || 'Dr. Marcus Chen',
            actorEmail: 'examiner@veritasvault.com',
            actionType: 'CHECK_OUT',
            notes: 'Checked out to Forensic Lab for scheduled examination.',
            hashAtEvent: ev.sha256Checksum,
            loggedAt: new Date().toISOString(),
          });
          demoCustodyLogs[ev.id] = existingLogs;
        }
        return demoReservations[idx];
      }
      return null;
    }
    const { data } = await apiClient.patch(`/custody/reservations/${reservationId}/checkout`);
    return data;
  },

  async checkIn(reservationId, notes, actionType = 'CHECK_IN') {
    if (isDemoMode()) {
      const idx = demoReservations.findIndex((r) => r.id === Number(reservationId));
      if (idx !== -1) {
        demoReservations[idx] = {
          ...demoReservations[idx],
          status: 'COMPLETED',
          notes: notes || demoReservations[idx].notes,
        };
        const evIdx = demoEvidence.findIndex((e) => e.id === demoReservations[idx].evidenceItemId);
        if (evIdx !== -1) {
          demoEvidence[evIdx] = { ...demoEvidence[evIdx], status: 'AVAILABLE' };
          const ev = demoEvidence[evIdx];
          const existingLogs = demoCustodyLogs[ev.id] || [];
          existingLogs.push({
            id: Date.now(),
            evidenceId: ev.id,
            evidenceTrackingCode: ev.trackingCode,
            evidenceName: ev.name,
            actorId: 3,
            actorName: demoReservations[idx].examinerName || 'Dr. Marcus Chen',
            actorEmail: 'examiner@veritasvault.com',
            actionType: actionType || 'CHECK_IN',
            notes: notes || 'Evidence returned to vault after forensic review.',
            hashAtEvent: ev.sha256Checksum,
            loggedAt: new Date().toISOString(),
          });
          demoCustodyLogs[ev.id] = existingLogs;
        }
        return demoReservations[idx];
      }
      return null;
    }
    const params = {};
    if (notes) params.notes = notes;
    if (actionType) params.actionType = actionType;
    const { data } = await apiClient.patch(`/custody/reservations/${reservationId}/checkin`, null, {
      params,
    });
    return data;
  },

  async checkInWithReport(reservationId, examinerFindings, notes) {
    if (isDemoMode()) {
      const idx = demoReservations.findIndex((r) => r.id === Number(reservationId));
      if (idx !== -1) {
        const evIdx = demoEvidence.findIndex((e) => e.id === demoReservations[idx].evidenceItemId);
        const ev = demoEvidence[evIdx];
        const isTampered = ev && ev.sha256Checksum && ev.sha256Checksum.startsWith('ffffffff');
        const integrityStatus = isTampered ? 'COMPROMISED' : 'VERIFIED';
        if (evIdx !== -1) {
          demoEvidence[evIdx] = { ...ev, status: isTampered ? 'SEQUESTERED' : 'AVAILABLE' };
        }
        demoReservations[idx] = { ...demoReservations[idx], status: 'COMPLETED' };
        return {
          id: Date.now(),
          custodyReservationId: Number(reservationId),
          evidenceItemId: ev ? ev.id : null,
          evidenceTrackingCode: ev ? ev.trackingCode : null,
          evidenceName: ev ? ev.name : null,
          integrityStatus,
          preExamHash: ev ? ev.sha256Checksum : 'demo-pre-hash',
          postExamHash: isTampered ? 'tampered-hash-mismatch-error' : (ev ? ev.sha256Checksum : 'demo-post-hash'),
          verifiedByThread: 'lab-verifier-pool-1',
          verificationDurationMs: 42,
          examinerFindings: examinerFindings || 'All sectors verified',
        };
      }
    }
    const { data } = await apiClient.post(`/custody/reservations/${reservationId}/checkin`, {
      examinerFindings,
      notes,
    });
    return data;
  },

  async cancel(reservationId) {
    if (isDemoMode()) {
      const idx = demoReservations.findIndex((r) => r.id === Number(reservationId));
      if (idx !== -1) {
        demoReservations[idx] = { ...demoReservations[idx], status: 'CANCELLED' };
        return demoReservations[idx];
      }
      return null;
    }
    const { data } = await apiClient.patch(`/custody/reservations/${reservationId}/cancel`);
    return data;
  },
};

// ==========================================
// 6. EXHIBIT BINDERS & BATES STAMPING (/api/binders)
// ==========================================
export const bindersApi = {
  async getBindersByCase(caseId) {
    if (isDemoMode()) {
      const list = demoBinders.filter((b) => b.caseId === Number(caseId));
      return { content: list, totalElements: list.length };
    }
    const { data } = await apiClient.get(`/binders/case/${caseId}`);
    return data;
  },

  async getBinderById(binderId) {
    if (isDemoMode()) {
      return demoBinders.find((b) => b.id === Number(binderId)) || demoBinders[0];
    }
    const { data } = await apiClient.get(`/binders/${binderId}`);
    return data;
  },

  async createBinder(payload) {
    if (isDemoMode()) {
      const targetCase = demoCases.find((c) => c.id === Number(payload.caseId)) || demoCases[0];
      const newBinder = {
        id: Date.now(),
        caseId: Number(payload.caseId),
        caseNumber: targetCase.caseNumber,
        name: payload.name,
        batesPrefix: payload.batesPrefix.toUpperCase(),
        status: 'DRAFT',
        totalExhibits: 0,
        totalPages: 0,
        isLocked: false,
        createdById: 2,
        createdByName: 'Julian Sterling, Lead Counsel',
        exhibits: [],
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      };
      demoBinders = [newBinder, ...demoBinders];
      return newBinder;
    }
    const { data } = await apiClient.post('/binders', payload);
    return data;
  },

  async addExhibit(binderId, payload) {
    if (isDemoMode()) {
      const bIdx = demoBinders.findIndex((b) => b.id === Number(binderId));
      if (bIdx === -1) throw new Error('Binder not found');
      const binder = demoBinders[bIdx];
      if (binder.isLocked) throw new Error('Binder is locked (FINALIZED or SERVED)');

      const ev = demoEvidence.find((e) => e.id === Number(payload.evidenceId)) || demoEvidence[0];
      const currentPages = binder.exhibits.reduce((sum, ex) => sum + ex.pageCount, 0);
      const startPage = currentPages + 1;
      const endPage = currentPages + Number(payload.pageCount);
      const pad = (n) => String(n).padStart(4, '0');

      const newEx = {
        id: Date.now(),
        evidenceId: ev.id,
        evidenceTrackingCode: ev.trackingCode,
        evidenceName: ev.name,
        exhibitLabel: payload.exhibitLabel,
        pageCount: Number(payload.pageCount),
        batesStart: `${binder.batesPrefix}-${pad(startPage)}`,
        batesEnd: `${binder.batesPrefix}-${pad(endPage)}`,
      };
      binder.exhibits.push(newEx);
      binder.totalExhibits = binder.exhibits.length;
      binder.totalPages = endPage;
      return { ...binder };
    }
    const { data } = await apiClient.post(`/binders/${binderId}/exhibits`, payload);
    return data;
  },

  async removeExhibit(binderId, exhibitId) {
    if (isDemoMode()) {
      const bIdx = demoBinders.findIndex((b) => b.id === Number(binderId));
      if (bIdx === -1) throw new Error('Binder not found');
      const binder = demoBinders[bIdx];
      if (binder.isLocked) throw new Error('Binder is locked (FINALIZED or SERVED)');

      binder.exhibits = binder.exhibits.filter((ex) => ex.id !== Number(exhibitId));
      // Re-index consecutive Bates numbers automatically without gaps
      let pageCursor = 0;
      const pad = (n) => String(n).padStart(4, '0');
      binder.exhibits = binder.exhibits.map((ex) => {
        const start = pageCursor + 1;
        const end = pageCursor + ex.pageCount;
        pageCursor = end;
        return {
          ...ex,
          batesStart: `${binder.batesPrefix}-${pad(start)}`,
          batesEnd: `${binder.batesPrefix}-${pad(end)}`,
        };
      });
      binder.totalExhibits = binder.exhibits.length;
      binder.totalPages = pageCursor;
      return { ...binder };
    }
    const { data } = await apiClient.delete(`/binders/${binderId}/exhibits/${exhibitId}`);
    return data;
  },

  async updateStatus(binderId, status) {
    if (isDemoMode()) {
      const bIdx = demoBinders.findIndex((b) => b.id === Number(binderId));
      if (bIdx !== -1) {
        demoBinders[bIdx] = {
          ...demoBinders[bIdx],
          status,
          isLocked: status === 'FINALIZED' || status === 'SERVED',
        };
        return demoBinders[bIdx];
      }
      return null;
    }
    const { data } = await apiClient.patch(`/binders/${binderId}/status`, null, {
      params: { status },
    });
    return data;
  },
};

// ==========================================
// 7. DISCOVERY PRODUCTIONS (/api/discovery)
// ==========================================
export const discoveryApi = {
  async getByCase(caseId) {
    if (isDemoMode()) {
      return demoDiscoveries.filter((d) => d.caseId === Number(caseId));
    }
    const { data } = await apiClient.get(`/discovery/case/${caseId}`);
    return data;
  },

  async createProduction(binderId, payload) {
    if (isDemoMode()) {
      const targetCase = demoCases.find((c) => c.id === Number(payload.caseId)) || demoCases[0];
      const targetBinder = demoBinders.find((b) => b.id === Number(binderId));
      const rawToken =
        'vv-disc-' +
        Math.random().toString(36).substring(2, 10) +
        '-' +
        Date.now().toString(36);
      const created = {
        id: Date.now(),
        caseId: Number(payload.caseId),
        caseNumber: targetCase.caseNumber,
        binderId: Number(binderId),
        title: targetBinder ? `${targetBinder.name} - Discovery Production` : 'Discovery Package',
        description: `Bates-stamped discovery bundle served to ${payload.recipientParty}`,
        accessToken: rawToken,
        downloadUrl: `http://localhost:8085/api/discovery/download/${rawToken}`,
        recipientParty: payload.recipientParty,
        expiresAt: payload.expiresAt,
        downloadCount: 0,
        isRevoked: false,
        createdById: 2,
        createdByName: 'Julian Sterling, Lead Counsel',
        evidenceItems: demoEvidence.filter((e) => e.caseId === Number(payload.caseId)),
        createdAt: new Date().toISOString(),
      };
      demoDiscoveries = [created, ...demoDiscoveries];
      demoReceipts[created.id] = [];

      // Lock the binder as SERVED
      const bIdx = demoBinders.findIndex((b) => b.id === Number(binderId));
      if (bIdx !== -1) {
        demoBinders[bIdx] = { ...demoBinders[bIdx], status: 'SERVED', isLocked: true };
      }
      return created;
    }
    const { data } = await apiClient.post(`/discovery/produce/${binderId}`, payload);
    return data;
  },

  async accessByToken(accessToken) {
    if (isDemoMode()) {
      const found =
        demoDiscoveries.find((d) => d.accessToken === accessToken.trim()) || demoDiscoveries[0];
      // Log receipt
      const receipts = demoReceipts[found.id] || [];
      receipts.unshift({
        id: Date.now(),
        productionId: found.id,
        ipAddress: '127.0.0.1 (Local Opposing Counsel Portal)',
        userAgent: navigator.userAgent,
        downloadedFileName: 'METADATA_VERIFICATION_INSPECTION',
        accessedAt: new Date().toISOString(),
      });
      demoReceipts[found.id] = receipts;
      return found;
    }
    const { data } = await apiClient.get(`/discovery/access/${encodeURIComponent(accessToken.trim())}`);
    return data;
  },

  async getReceipts(productionId) {
    if (isDemoMode()) {
      return demoReceipts[Number(productionId)] || [];
    }
    const { data } = await apiClient.get(`/discovery/${productionId}/receipts`);
    return data;
  },

  async downloadBundle(accessToken, packageData) {
    if (isDemoMode() && packageData) {
      const content = [
        '====================================================================',
        'VERITASVAULT OPPOSING COUNSEL DISCOVERY PACKAGE',
        '====================================================================',
        `Package Title    : ${packageData.title}`,
        `Case Number      : ${packageData.caseNumber}`,
        `Recipient        : ${packageData.recipientParty}`,
        `Date Retrieved   : ${new Date().toLocaleString()}`,
        '====================================================================',
      ].join('\n');
      const blob = new Blob([content], { type: 'text/plain' });
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `${packageData.caseNumber}_Discovery_Package.txt`;
      a.click();
      window.URL.revokeObjectURL(url);
      return;
    }

    // 1. Trigger the discovery download endpoint so Proof-of-Service receipt is recorded and download 1st file
    const response = await apiClient.get(
      `/discovery/download/${encodeURIComponent(accessToken.trim())}`,
      { responseType: 'blob' }
    );

    const items = packageData?.evidenceItems || [];
    const primaryEv = items[0];
    const extMatch = String(primaryEv?.fileStoragePath || '').match(/(\.[a-zA-Z0-9]{1,8})$/);
    const realExt = extMatch ? extMatch[1] : '';
    let baseName = primaryEv?.originalFileName || primaryEv?.name || packageData?.title || 'Discovery_Exhibit';
    if (realExt && !baseName.toLowerCase().endsWith(realExt.toLowerCase())) {
      baseName = `${baseName}${realExt}`;
    }

    const url = window.URL.createObjectURL(new Blob([response.data]));
    const a = document.createElement('a');
    a.href = url;
    a.download = baseName;
    a.click();
    window.URL.revokeObjectURL(url);

    // 2. If the binder has additional evidence items (2nd, 3rd, etc.), download them too
    for (let i = 1; i < items.length; i++) {
      const ev = items[i];
      await evidenceApi.downloadEvidence(
        ev.id,
        ev.originalFileName || ev.name,
        ev.fileStoragePath
      );
    }
  },
};

// ==========================================
// 8. ADMIN USER MANAGEMENT (/api/admin/users)
// ==========================================
export const adminUsersApi = {
  async getUsers({ role, status, search, page = 0, size = 20 } = {}) {
    if (isDemoMode()) {
      let list = [...demoUsers];
      if (role) list = list.filter((u) => u.role === role);
      if (status) list = list.filter((u) => u.status === status);
      if (search) {
        const q = search.toLowerCase();
        list = list.filter(
          (u) => u.fullName.toLowerCase().includes(q) || u.email.toLowerCase().includes(q)
        );
      }
      return { content: list, totalElements: list.length, totalPages: 1 };
    }
    const params = { page, size };
    if (role) params.role = role;
    if (status) params.status = status;
    params.search = search && search.trim() ? search.trim() : '%';
    const { data } = await apiClient.get('/admin/users', { params });
    return data;
  },

  async createUser(payload) {
    if (isDemoMode()) {
      const created = {
        id: demoUsers.length + 20,
        fullName: payload.fullName,
        email: payload.email,
        role: payload.role,
        status: 'ACTIVE',
        isEmailVerified: true,
        phoneNumber: payload.phoneNumber,
        organizationOrAgency: payload.organizationOrAgency,
        barOrBadgeNumber: payload.barOrBadgeNumber,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      };
      demoUsers = [created, ...demoUsers];
      return created;
    }
    const { data } = await apiClient.post('/admin/users', payload);
    return data;
  },

  async updateUserRole(userId, role) {
    if (isDemoMode()) {
      const idx = demoUsers.findIndex((u) => u.id === Number(userId));
      if (idx !== -1) {
        demoUsers[idx] = { ...demoUsers[idx], role };
        return demoUsers[idx];
      }
      return null;
    }
    const { data } = await apiClient.patch(`/admin/users/${userId}/role`, null, {
      params: { role },
    });
    return data;
  },

  async updateUserStatus(userId, status) {
    if (isDemoMode()) {
      const idx = demoUsers.findIndex((u) => u.id === Number(userId));
      if (idx !== -1) {
        demoUsers[idx] = { ...demoUsers[idx], status };
        return demoUsers[idx];
      }
      return null;
    }
    const { data } = await apiClient.patch(`/admin/users/${userId}/status`, null, {
      params: { status },
    });
    return data;
  },
};
