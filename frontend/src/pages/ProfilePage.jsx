import React, { useState, useEffect } from 'react';
import {
  UserCircle,
  ShieldCheck,
  KeyRound,
  Save,
  CheckCircle2,
  AlertCircle,
  Camera,
  Loader2,
} from 'lucide-react';
import { authApi } from '../api/vaultService';
import { extractErrorMessage } from '../api/client';
import { useAuth } from '../context/AuthContext';

const ROLE_LABELS = {
  ROLE_ADMIN: 'Administrator',
  ROLE_ATTORNEY: 'Lead Attorney',
  ROLE_FORENSIC_EXAMINER: 'Forensic Examiner',
  ROLE_CLIENT: 'Client',
};

export default function ProfilePage() {
  const { mode, pushNotification } = useAuth();

  const [profile, setProfile] = useState({
    id: null,
    fullName: '',
    email: '',
    phoneNumber: '',
    role: '',
    organizationOrAgency: '',
    barOrBadgeNumber: '',
    profilePictureUrl: '',
  });
  const [passwords, setPasswords] = useState({
    currentPassword: '',
    newPassword: '',
  });
  const [msg, setMsg] = useState('');
  const [error, setError] = useState('');
  const [uploadingPic, setUploadingPic] = useState(false);
  const [avatarKey, setAvatarKey] = useState(Date.now());

  useEffect(() => {
    (async () => {
      try {
        const data = await authApi.getMe();
        setProfile({
          id: data.id || null,
          fullName: data.fullName || '',
          email: data.email || '',
          phoneNumber: data.phoneNumber || '',
          role: data.role || '',
          organizationOrAgency: data.organizationOrAgency || '',
          barOrBadgeNumber: data.barOrBadgeNumber || '',
          profilePictureUrl: data.profilePictureUrl || '',
        });
      } catch (err) {
        setError(extractErrorMessage(err));
      }
    })();
  }, [mode]);

  const handlePictureUpload = async (e) => {
    const file = e.target.files?.[0];
    if (!file) return;

    if (!file.type.startsWith('image/')) {
      setError('Please select a valid image file (PNG, JPG, WebP)');
      return;
    }

    setUploadingPic(true);
    setError('');
    setMsg('');

    try {
      const res = await authApi.uploadProfilePicture(file);
      setProfile((prev) => ({
        ...prev,
        profilePictureUrl: res.profilePictureUrl || prev.profilePictureUrl,
      }));
      setAvatarKey(Date.now());
      setMsg('Profile picture updated successfully.');
      pushNotification('Avatar Updated', 'Your profile picture has been updated.', 'success');
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setUploadingPic(false);
    }
  };

  const handleUpdateProfile = async (e) => {
    e.preventDefault();
    setError('');
    setMsg('');
    try {
      const updated = await authApi.updateProfile({
        fullName: profile.fullName,
        phoneNumber: profile.phoneNumber,
        organizationOrAgency: profile.organizationOrAgency,
        barOrBadgeNumber: profile.barOrBadgeNumber,
      });
      setProfile((prev) => ({ ...prev, ...updated }));
      setMsg('Profile updated successfully.');
      pushNotification('Profile Saved', 'Your profile details have been updated.', 'success');
    } catch (err) {
      setError(extractErrorMessage(err));
    }
  };

  const handleChangePassword = async (e) => {
    e.preventDefault();
    setError('');
    setMsg('');
    try {
      const res = await authApi.changePassword(
        passwords.currentPassword,
        passwords.newPassword
      );
      setMsg(res || 'Password updated successfully.');
      setPasswords({ currentPassword: '', newPassword: '' });
    } catch (err) {
      setError(extractErrorMessage(err));
    }
  };

  return (
    <div className="max-w-4xl space-y-6">
      <div className="espresso-gradient rounded-3xl p-7 text-white shadow-xl border border-[#4A3222] flex flex-col sm:flex-row sm:items-center justify-between gap-6">
        <div className="flex items-center gap-5">
          {/* Avatar with Camera Button */}
          <div className="relative group shrink-0">
            <div className="w-20 h-20 rounded-2xl bg-[#2A1B12] border-2 border-[#8B5230] overflow-hidden flex items-center justify-center shadow-lg">
              {profile.profilePictureUrl ? (
                <img
                  src={
                    profile.profilePictureUrl.startsWith('data:')
                      ? profile.profilePictureUrl
                      : `${authApi.getProfilePictureUrl(profile.id)}?t=${avatarKey}`
                  }
                  alt={profile.fullName}
                  className="w-full h-full object-cover"
                  onError={(e) => {
                    e.currentTarget.style.display = 'none';
                  }}
                />
              ) : (
                <UserCircle className="w-12 h-12 text-[#CA9874]" />
              )}
            </div>
            <label
              htmlFor="avatar-upload"
              className="absolute -bottom-1.5 -right-1.5 p-2 rounded-xl cognac-gradient text-white shadow-md hover:scale-105 transition cursor-pointer flex items-center justify-center"
              title="Upload profile photo"
            >
              {uploadingPic ? (
                <Loader2 className="w-4 h-4 animate-spin" />
              ) : (
                <Camera className="w-4 h-4" />
              )}
              <input
                id="avatar-upload"
                type="file"
                accept="image/png,image/jpeg,image/webp"
                className="hidden"
                disabled={uploadingPic}
                onChange={handlePictureUpload}
              />
            </label>
          </div>

          <div>
            <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-[#3E2A1E] border border-[#8B5230] text-[#DFC3AC] text-xs font-semibold mb-2">
              <ShieldCheck className="w-3.5 h-3.5 text-[#CA9874]" />
              Account Settings
            </div>
            <h1 className="font-serif-legal text-3xl font-bold">{profile.fullName}</h1>
            <p className="text-xs text-[#DFC3AC] mt-1">
              {profile.email} • Role: <strong className="text-[#CA9874]">{ROLE_LABELS[profile.role] || profile.role}</strong>
            </p>
          </div>
        </div>

        <label
          htmlFor="avatar-upload"
          className="self-start sm:self-center px-4 py-2 rounded-xl bg-[#3E2A1E] hover:bg-[#5A3825] border border-[#8B5230] text-xs font-semibold text-[#DFC3AC] hover:text-white transition cursor-pointer flex items-center gap-2"
        >
          <Camera className="w-4 h-4 text-[#CA9874]" />
          <span>{profile.profilePictureUrl ? 'Change Photo' : 'Upload Photo'}</span>
        </label>
      </div>

      {error && (
        <div className="p-4 rounded-2xl bg-red-50 border border-red-200 text-red-900 text-xs flex items-center gap-2.5">
          <AlertCircle className="w-4.5 h-4.5 text-red-700 shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {msg && (
        <div className="p-4 rounded-2xl bg-emerald-50 border border-emerald-200 text-emerald-900 text-xs flex items-center gap-2.5">
          <CheckCircle2 className="w-4.5 h-4.5 text-emerald-700 shrink-0" />
          <span>{msg}</span>
        </div>
      )}

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {/* Profile Form */}
        <form
          onSubmit={handleUpdateProfile}
          className="card-elevated rounded-3xl p-6 bg-white space-y-4"
        >
          <div className="flex items-center gap-2 border-b border-[#E6D7C3] pb-3">
            <UserCircle className="w-5 h-5 text-[#8B5230]" />
            <h3 className="font-serif-legal text-2xl font-bold text-[#1E130D]">
              Personal & Professional Details
            </h3>
          </div>

          <div>
            <label className="block text-xs font-semibold text-[#3E2A1E] mb-1">
              Full Name *
            </label>
            <input
              type="text"
              required
              value={profile.fullName}
              onChange={(e) => setProfile({ ...profile, fullName: e.target.value })}
              className="w-full px-3.5 py-2 rounded-xl bg-[#FAF6F0] border border-[#DFC3AC] text-xs"
            />
          </div>

          <div>
            <label className="block text-xs font-semibold text-[#3E2A1E] mb-1">
              Phone Number
            </label>
            <input
              type="text"
              value={profile.phoneNumber}
              onChange={(e) => setProfile({ ...profile, phoneNumber: e.target.value })}
              className="w-full px-3.5 py-2 rounded-xl bg-[#FAF6F0] border border-[#DFC3AC] text-xs"
            />
          </div>

          <div>
            <label className="block text-xs font-semibold text-[#3E2A1E] mb-1">
              Law Firm / Organization
            </label>
            <input
              type="text"
              value={profile.organizationOrAgency}
              onChange={(e) =>
                setProfile({ ...profile, organizationOrAgency: e.target.value })
              }
              className="w-full px-3.5 py-2 rounded-xl bg-[#FAF6F0] border border-[#DFC3AC] text-xs"
            />
          </div>

          <div>
            <label className="block text-xs font-semibold text-[#3E2A1E] mb-1">
              Bar Number or Badge ID
            </label>
            <input
              type="text"
              value={profile.barOrBadgeNumber}
              onChange={(e) => setProfile({ ...profile, barOrBadgeNumber: e.target.value })}
              className="w-full px-3.5 py-2 rounded-xl bg-[#FAF6F0] border border-[#DFC3AC] text-xs"
            />
          </div>

          <button
            type="submit"
            className="w-full py-2.5 px-4 rounded-xl cognac-gradient text-white text-xs font-semibold shadow-md flex items-center justify-center gap-2 cursor-pointer"
          >
            <Save className="w-4 h-4" />
            Save Changes
          </button>
        </form>

        {/* Change Password Form */}
        <form
          onSubmit={handleChangePassword}
          className="card-elevated rounded-3xl p-6 bg-white space-y-4"
        >
          <div className="flex items-center gap-2 border-b border-[#E6D7C3] pb-3">
            <KeyRound className="w-5 h-5 text-[#8B5230]" />
            <h3 className="font-serif-legal text-2xl font-bold text-[#1E130D]">
              Change Password
            </h3>
          </div>

          <div>
            <label className="block text-xs font-semibold text-[#3E2A1E] mb-1">
              Current Password *
            </label>
            <input
              type="password"
              required
              value={passwords.currentPassword}
              onChange={(e) =>
                setPasswords({ ...passwords, currentPassword: e.target.value })
              }
              className="w-full px-3.5 py-2 rounded-xl bg-[#FAF6F0] border border-[#DFC3AC] text-xs"
            />
          </div>

          <div>
            <label className="block text-xs font-semibold text-[#3E2A1E] mb-1">
              New Password *
            </label>
            <input
              type="password"
              required
              value={passwords.newPassword}
              onChange={(e) => setPasswords({ ...passwords, newPassword: e.target.value })}
              placeholder="At least 8 characters"
              className="w-full px-3.5 py-2 rounded-xl bg-[#FAF6F0] border border-[#DFC3AC] text-xs"
            />
          </div>

          <button
            type="submit"
            className="w-full py-2.5 px-4 rounded-xl bg-[#1E130D] hover:bg-[#3E2A1E] text-white text-xs font-semibold shadow-md cursor-pointer"
          >
            Update Password
          </button>
        </form>
      </div>
    </div>
  );
}
