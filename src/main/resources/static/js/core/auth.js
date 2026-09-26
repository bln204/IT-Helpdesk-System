// ==================== Authentication Module ====================
import { tokenKey, DEMO_CREDENTIALS, API_BASE, NOTIFICATION_POLLING_MS } from '../config/constants.js';
import { request } from './api.js';

// We need to import views and other elements dynamically from constants
let currentUser = null;
let notifications = [];
let notificationPollingTimer = null;

// Import elements that need to be updated
let navCard, appShell, sidebarUsername, sidebarRole, sidebarDept, logoMarkFallback, logoMarkImg;
let userDisplay, tokenStatus;

export const setCurrentUser = (user) => {
  currentUser = user;
};

export const getCurrentUser = () => currentUser;

export const setNotifications = (notifs) => {
  notifications = notifs;
};

export const getNotifications = () => notifications;

export const setNotificationPollingTimer = (timer) => {
  notificationPollingTimer = timer;
};

export const getNotificationPollingTimer = () => notificationPollingTimer;

export const getToken = () => localStorage.getItem(tokenKey);
export const clearToken = () => localStorage.removeItem(tokenKey);

export const setToken = (token) => {
  if (token) {
    localStorage.setItem(tokenKey, token);
  } else {
    clearToken();
  }
  updateTokenStatus();
  updateNavVisibility();
};

const parseJwt = (token) => {
  try {
    const payload = token.split('.')[1];
    const normalized = payload.replace(/-/g, '+').replace(/_/g, '/');
    const decoded = atob(normalized);
    return JSON.parse(decoded);
  } catch (err) {
    return null;
  }
};

export const isTokenValid = () => {
  const token = getToken();
  if (!token) return false;
  const payload = parseJwt(token);
  if (!payload || !payload.exp) return false;
  const nowSeconds = Math.floor(Date.now() / 1000);
  return payload.exp > nowSeconds;
};

const getRoles = () => {
  const token = isTokenValid() ? getToken() : null;
  if (!token) return [];
  const payload = parseJwt(token);
  if (!payload || !payload.roles) return [];
  return Array.isArray(payload.roles) ? payload.roles : [];
};

export const hasRole = (role) => {
  const roles = getRoles();
  // Check with ROLE_ prefix if not already present
  if (role.startsWith('ROLE_')) {
    return roles.includes(role);
  }
  return roles.includes(role) || roles.includes('ROLE_' + role);
};

// Role check functions
export const isAdmin = () => hasRole('ROLE_ADMIN');
export const isGiamDoc = () => hasRole('ROLE_GIAM_DOC');
export const isTruongPhong = () => hasRole('ROLE_TRUONG_PHONG');
export const isNhanVien = () => hasRole('ROLE_NHAN_VIEN');
export const isTruongPhongIT = () => hasRole('ROLE_TRUONG_PHONG') && currentUser?.departmentCode === 'IT';
export const isInITDepartment = () => currentUser?.departmentCode === 'IT';
export const isITStaff = () => isNhanVien() && currentUser?.departmentCode === 'IT';

export const getCurrentUsername = () => {
  return currentUser?.username || null;
};

export const getCurrentUserDepartment = () => {
  return currentUser?.departmentCode || null;
};

export const updateTokenStatus = () => {
  // Initialize elements if not already done
  initElements();
  
  const token = isTokenValid() ? getToken() : null;
  if (!token && getToken()) {
    clearToken();
  }
  if (tokenStatus) {
    tokenStatus.textContent = token ? 'Đã đăng nhập' : 'Đã đăng xuất';
  }
  if (navCard) {
    navCard.classList.toggle('hidden', !token);
  }
  if (appShell) {
    appShell.classList.toggle('logged-out', !token);
  }
  
  const username = currentUser?.username || 'Phiếu hỗ trợ';
  const roles = getRoles();
  const dept = currentUser?.departmentName || null;
  
  // Format name helper
  const formatName = (value) => {
    if (!value) return '';
    return value
      .split(/[\s_-]+/)
      .map((part) => part.charAt(0).toUpperCase() + part.slice(1).toLowerCase())
      .join(' ');
  };
  
  if (logoMarkFallback) {
    const initial = username ? username.trim().charAt(0).toUpperCase() : 'T';
    logoMarkFallback.textContent = initial;
    logoMarkFallback.classList.remove('hidden');
  }
  if (logoMarkImg) {
    logoMarkImg.classList.add('hidden');
    logoMarkImg.removeAttribute('src');
  }
  if (sidebarUsername) {
    sidebarUsername.textContent = formatName(username);
  }
  if (sidebarRole) {
    sidebarRole.textContent = roles.length
      ? roles.map((role) => formatRole(role)).join(', ')
      : 'Bảng điều khiển';
  }
  if (sidebarDept) {
    sidebarDept.textContent = dept || '';
    sidebarDept.classList.toggle('hidden', !dept);
  }
  if (userDisplay) {
    userDisplay.textContent = token ? formatName(username) : '—';
  }
};

const ROLE_LABELS = {
  ADMIN: 'Quản trị viên',
  GIAM_DOC: 'Giám đốc',
  TRUONG_PHONG: 'Trưởng phòng',
  NHAN_VIEN: 'Nhân viên',
};

export const formatRole = (role) => {
  if (!role) return '';
  const clean = role.replace('ROLE_', '').toUpperCase();
  return ROLE_LABELS[clean] || formatName(clean);
};

const formatName = (value) => {
  if (!value) return '';
  return value
    .split(/[\s_-]+/)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1).toLowerCase())
    .join(' ');
};

export const updateNavVisibility = () => {
  const navLinks = Array.from(document.querySelectorAll('.nav-card a'));
  navLinks.forEach((link) => {
    const required = link.dataset.role;
    if (!required) return;
    const roles = required.split(',').map((role) => role.trim()).filter(Boolean);
    const allowed = roles.some((role) => hasRole(role));
    link.classList.toggle('hidden', !allowed);
  });
  
  // Show/hide tab buttons based on role
  document.querySelectorAll('.tab-btn').forEach(btn => {
    const required = btn.dataset.role;
    if (!required) return;
    const roles = required.split(',').map((role) => role.trim()).filter(Boolean);
    const allowed = roles.some((role) => hasRole(role));
    btn.classList.toggle('hidden', !allowed);
  });
  
  // Show/hide assignee field based on role
  const assigneeCreate = document.getElementById('assignee-create');
  if (assigneeCreate) {
    assigneeCreate.closest('label').classList.toggle('hidden', !canAssignTickets());
  }
  
  applyRoleControls();
  applyAdminRoleOptions();
};

const canProcessTickets = () => hasRole('ROLE_ADMIN') || hasRole('ROLE_GIAM_DOC') || hasRole('ROLE_TRUONG_PHONG') || isITStaff();
const canAssignTickets = () => hasRole('ROLE_ADMIN') || hasRole('ROLE_GIAM_DOC') || hasRole('ROLE_TRUONG_PHONG') || isITStaff();

const applyRoleControls = () => {
  const statusSelect = document.getElementById('status-select');
  const prioritySelect = document.getElementById('priority-select');
  const assigneeInput = document.getElementById('assignee-input');
  const updateStatusBtn = document.getElementById('update-status-btn');
  const updatePriorityBtn = document.getElementById('update-priority-btn');
  const updateAssigneeBtn = document.getElementById('update-assignee-btn');
  const assignMeBtn = document.getElementById('assign-me-btn');
  const commentVisibility = document.getElementById('comment-visibility');
  const updateActionButtons = () => {
    const selectedTicket = window.selectedTicket;
    if (!selectedTicket) return;
    if (updateStatusBtn) {
      updateStatusBtn.disabled = statusSelect?.value === selectedTicket.status;
    }
    if (updatePriorityBtn) {
      updatePriorityBtn.disabled = prioritySelect?.value === selectedTicket.priority;
    }
    if (updateAssigneeBtn) {
      const UNASSIGNED_VALUE = 'UNASSIGNED';
      const currentAssignee = selectedTicket.assigneeName || UNASSIGNED_VALUE;
      updateAssigneeBtn.disabled = assigneeInput?.value === currentAssignee || !canAssignTickets();
    }
    if (assignMeBtn) {
      const currentUsername = getCurrentUsername();
      assignMeBtn.disabled = !currentUsername || selectedTicket.assigneeName === currentUsername || !canAssignTickets();
    }
  };
  
  // IT Staff (NHAN_VIEN in IT) can process tickets like ADMIN
  const isITStaffUser = isITStaff();
  const isNhanVienUser = isNhanVien() && !isITStaffUser;
  
  if (statusSelect) {
    statusSelect.disabled = !canProcessTickets() && isNhanVienUser;
  }
  if (prioritySelect) {
    prioritySelect.disabled = !canProcessTickets();
  }
  if (assigneeInput) {
    assigneeInput.disabled = !canAssignTickets();
  }
  
  // Show/hide buttons based on permissions
  if (updateStatusBtn) {
    updateStatusBtn.classList.toggle('hidden', !canProcessTickets() && isNhanVienUser);
  }
  if (updatePriorityBtn) {
    updatePriorityBtn.classList.toggle('hidden', !canProcessTickets());
  }
  if (updateAssigneeBtn) {
    updateAssigneeBtn.classList.toggle('hidden', !canAssignTickets());
  }
  if (assignMeBtn) {
    assignMeBtn.classList.toggle('hidden', !canAssignTickets());
  }
  
  // Comment visibility
  if (commentVisibility) {
    const canViewInternalComments = () => !isNhanVien() || isITStaff();
    if (!canViewInternalComments()) {
      commentVisibility.value = 'PUBLIC';
      commentVisibility.disabled = true;
    } else {
      commentVisibility.disabled = false;
    }
  }
  
  updateActionButtons();
};

const applyAdminRoleOptions = () => {
  const adminRoleSelect = document.getElementById('admin-role-select');
  if (!adminRoleSelect) return;
  adminRoleSelect.innerHTML = '';
  
  let roles = [];
  if (isAdmin()) {
    roles = ['ADMIN', 'GIAM_DOC', 'TRUONG_PHONG', 'NHAN_VIEN'];
  } else if (isGiamDoc()) {
    roles = ['GIAM_DOC', 'TRUONG_PHONG', 'NHAN_VIEN'];
  } else {
    roles = ['NHAN_VIEN'];
  }
  
  roles.forEach((role) => {
    const option = document.createElement('option');
    option.value = role;
    option.textContent = formatRole(role);
    adminRoleSelect.appendChild(option);
  });
};

const canManageUsers = () => hasRole('ROLE_ADMIN') || hasRole('ROLE_GIAM_DOC') || hasRole('ROLE_TRUONG_PHONG');

const shouldPrefillLogin = () => window.location.protocol !== 'file:';

const applyLoginPrefill = () => {
  const loginForm = document.getElementById('login-form');
  if (!loginForm) return;
  const usernameInput = loginForm.elements.namedItem('username');
  const passwordInput = loginForm.elements.namedItem('password');
  const loginPrefillNote = document.getElementById('login-prefill-note');
  const shouldPrefill = shouldPrefillLogin();
  if (loginPrefillNote) {
    loginPrefillNote.classList.toggle('hidden', !shouldPrefill);
  }
  if (!shouldPrefill || !usernameInput || !passwordInput) return;
  
  usernameInput.value = DEMO_CREDENTIALS.nhanVien.username;
  passwordInput.value = DEMO_CREDENTIALS.nhanVien.password;
};

export const login = async (payload) => {
  const data = await request('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
  setToken(data.token);
  
  // Store current user info from login response
  if (data.user) {
    currentUser = data.user;
    localStorage.setItem('ticketing.currentUser', JSON.stringify(data.user));
  }
  
  // Start notification polling after login
  startNotificationPolling();
  await loadNotifications();
  
  setRoute('#/tickets');
};

// Notification functions need to be imported
let loadNotifications, startNotificationPolling, stopNotificationPolling;

export const setLoadNotifications = (fn) => {
  loadNotifications = fn;
};

export const setStartNotificationPolling = (fn) => {
  startNotificationPolling = fn;
};

export const setStopNotificationPolling = (fn) => {
  stopNotificationPolling = fn;
};

export const setRoute = (hash) => {
  window.location.hash = hash;
};

export const initElements = () => {
  navCard = document.querySelector('.nav-card');
  appShell = document.getElementById('app-shell');
  sidebarUsername = document.getElementById('sidebar-username');
  sidebarRole = document.getElementById('sidebar-role');
  sidebarDept = document.getElementById('sidebar-dept');
  logoMarkFallback = document.getElementById('logo-mark-fallback');
  logoMarkImg = document.getElementById('logo-mark-img');
  userDisplay = document.getElementById('user-display');
  tokenStatus = document.getElementById('token-status');
};

/**
 * Clear auth state for logout
 */
export const clearAuthState = () => {
  currentUser = null;
  notifications = [];
  if (notificationPollingTimer) {
    clearInterval(notificationPollingTimer);
    notificationPollingTimer = null;
  }
};

export { DEMO_CREDENTIALS, currentUser, notifications, notificationPollingTimer };
