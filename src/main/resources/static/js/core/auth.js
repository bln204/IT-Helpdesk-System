// ==================== Authentication Module ====================
import { tokenKey, DEMO_CREDENTIALS, API_BASE, NOTIFICATION_POLLING_MS } from '../config/constants.js';
import { request } from './api.js';

// We need to import views and other elements dynamically from constants
let currentUser = null;
let notifications = [];
let notificationPollingTimer = null;
let passwordMustChange = false;

// Import elements that need to be updated
let navCard, appShell, sidebarUsername, sidebarRole, sidebarDept, logoMarkFallback, logoMarkImg;
let userDisplay, tokenStatus;

export const setCurrentUser = (user) => {
  currentUser = user;
  // Department-based controls (IT operator/lead) depend on the profile, not just the JWT.
  updateNavVisibility();
};

export const getCurrentUser = () => currentUser;

export const setPasswordMustChange = (mustChange) => {
  passwordMustChange = mustChange;
};

export const getPasswordMustChange = () => passwordMustChange;

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

export { parseJwt };

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
// Mirror backend ActorTiers.isItOperator / isItLead / seesAll.
export const isItOperator = () => (isTruongPhong() || isNhanVien()) && isInITDepartment();
export const isItLead = () => isTruongPhongIT();
export const seesAll = () => isAdmin() || isGiamDoc() || isItOperator();

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
  
  const displayName = currentUser?.displayName || formatName(username);
  const avatarUrl = currentUser?.avatarUrl || null;
  if (logoMarkFallback) {
    logoMarkFallback.textContent = displayName.trim().charAt(0).toUpperCase() || 'T';
    logoMarkFallback.classList.toggle('hidden', !!avatarUrl);
  }
  if (logoMarkImg) {
    if (avatarUrl) {
      logoMarkImg.src = avatarUrl;
      logoMarkImg.classList.remove('hidden');
    } else {
      logoMarkImg.classList.add('hidden');
      logoMarkImg.removeAttribute('src');
    }
  }
  if (sidebarUsername) {
    sidebarUsername.textContent = displayName;
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

// data-role / data-requires tokens: ROLE_* from the JWT, plus IT (IT operator) and IT_LEAD.
const matchesRoleList = (required) => required.split(',').map((role) => role.trim()).filter(Boolean)
  .some((role) => role === 'IT' ? isItOperator() : role === 'IT_LEAD' ? isItLead() : hasRole(role));

export const updateNavVisibility = () => {
  document.querySelectorAll('.nav-card a[data-role], .tab-btn[data-role], [data-requires]').forEach((el) => {
    el.classList.toggle('hidden', !matchesRoleList(el.dataset.role || el.dataset.requires));
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
  // IT staff: "Giao cho tôi" only on unassigned tickets, "Chuyển phụ trách" only on own tickets
  if (isITStaffUser && window.selectedTicket) {
    const username = getCurrentUsername();
    const isOwner = !!username && window.selectedTicket.assigneeName === username;
    const isUnassigned = !window.selectedTicket.assigneeName;
    if (assignMeBtn) assignMeBtn.classList.toggle('hidden', !isUnassigned);
    if (updateAssigneeBtn) updateAssigneeBtn.classList.toggle('hidden', !isOwner);
    if (assigneeInput) assigneeInput.disabled = !isOwner;
    // Only the owner may update; a closed ticket may also be reopened by its requester.
    const isRequester = !!username && window.selectedTicket.requesterUsername === username;
    const blockStatus = window.selectedTicket.status === 'CLOSED' ? !(isOwner || isRequester) : !isOwner;
    if (updateStatusBtn) updateStatusBtn.classList.toggle('hidden', blockStatus);
    if (statusSelect) statusSelect.disabled = blockStatus;
    if (updatePriorityBtn) updatePriorityBtn.classList.toggle('hidden', !isOwner);
    if (prioritySelect) prioritySelect.disabled = !isOwner;
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
  
  // Store passwordMustChange flag
  passwordMustChange = data.passwordMustChange === true;
  localStorage.setItem('ticketing.passwordMustChange', passwordMustChange);
  
  // Check if user needs to change password
  if (passwordMustChange) {
    // Show password change modal and prevent navigation
    showForcePasswordChangeModal();
    return;
  }
  
  // Start notification polling after login
  startNotificationPolling();
  await loadNotifications();
  
  setRoute('#/tickets');
};

/**
 * Show the force password change modal
 */
export function showForcePasswordChangeModal() {
  // Check if modal already exists
  let modal = document.getElementById('force-password-modal');
  if (!modal) {
    createForcePasswordChangeModal();
    modal = document.getElementById('force-password-modal');
  }
  
  // Show modal
  modal.classList.remove('hidden');
  
  // Focus on new password input
  setTimeout(() => {
    const newPasswordInput = document.getElementById('force-new-password');
    if (newPasswordInput) newPasswordInput.focus();
  }, 100);
}

/**
 * Create the force password change modal if it doesn't exist
 */
function createForcePasswordChangeModal() {
  const modal = document.createElement('div');
  modal.id = 'force-password-modal';
  modal.className = 'modal hidden';
  modal.innerHTML = `
    <div class="modal-content" role="dialog" aria-modal="true" aria-labelledby="force-password-title" style="max-width: 440px;">
      <div class="modal-header">
        <h3 id="force-password-title">Đặt mật khẩu mới</h3>
        <p style="margin: 6px 0 0; color: var(--muted); font-size: 0.9rem;">
          Đây là lần đăng nhập đầu tiên. Bạn hãy đặt mật khẩu riêng để bắt đầu sử dụng hệ thống.
        </p>
      </div>
      <form id="force-password-form" novalidate>
        <div class="modal-body">
          <div class="form-group">
            <label for="force-new-password">Nhập mật khẩu mới</label>
            <input type="password" id="force-new-password" name="newPassword" required
                   minlength="7" autocomplete="new-password" placeholder="Tối thiểu 7 ký tự">
          </div>
          <div class="form-group">
            <label for="force-confirm-password">Xác nhận mật khẩu mới</label>
            <input type="password" id="force-confirm-password" name="confirmPassword" required
                   minlength="7" autocomplete="new-password" placeholder="Nhập lại mật khẩu mới">
          </div>
          <p id="force-password-error" class="field-error hidden" role="alert" style="margin: 0;"></p>
        </div>
        <div class="modal-footer">
          <button type="submit" id="force-password-submit">Đổi mật khẩu</button>
        </div>
      </form>
    </div>
  `;
  document.body.appendChild(modal);

  // Submit via button or Enter key
  document.getElementById('force-password-form').addEventListener('submit', (e) => {
    e.preventDefault();
    handleForcePasswordSubmit();
  });
}

/**
 * Handle force password change submission
 */
async function handleForcePasswordSubmit() {
  const newPassword = document.getElementById('force-new-password')?.value;
  const confirmPassword = document.getElementById('force-confirm-password')?.value;
  const errorDiv = document.getElementById('force-password-error');
  const submitBtn = document.getElementById('force-password-submit');
  
  // Validate
  if (!newPassword || newPassword.length < 7) {
    if (errorDiv) {
      errorDiv.textContent = 'Mật khẩu mới cần có ít nhất 7 ký tự.';
      errorDiv.classList.remove('hidden');
    }
    return;
  }
  
  if (newPassword !== confirmPassword) {
    if (errorDiv) {
      errorDiv.textContent = 'Hai mật khẩu chưa khớp nhau, bạn kiểm tra lại nhé.';
      errorDiv.classList.remove('hidden');
    }
    return;
  }
  
  // Disable button to prevent double submission
  if (submitBtn) {
    submitBtn.disabled = true;
    submitBtn.textContent = 'Đang xử lý...';
  }
  if (errorDiv) {
    errorDiv.classList.add('hidden');
  }
  
  try {
    const data = await request('/api/users/me/force-password-change', {
      method: 'POST',
      headers: Auth.getAuthHeaders(),
      body: JSON.stringify({
        newPassword: newPassword,
        confirmPassword: confirmPassword
      }),
    });
    
    if (data.success) {
      // Clear password must change flag
      passwordMustChange = false;
      localStorage.setItem('ticketing.passwordMustChange', 'false');
      
      // Hide modal
      const modal = document.getElementById('force-password-modal');
      if (modal) modal.classList.add('hidden');
      
      // Clear auth state and redirect to login (don't call logout API since token is invalid after password change)
      clearAuthState();
      clearToken();
      currentUser = null;
      passwordMustChange = false;
      localStorage.removeItem('ticketing.currentUser');
      localStorage.removeItem('ticketing.passwordMustChange');
      
      // Stop notification polling
      if (stopNotificationPolling) {
        stopNotificationPolling();
      }
      
      // Redirect to login
      setRoute('#/login');
      
      // Show success message
      Dialog.alert('Mật khẩu của bạn đã được đổi. Vui lòng đăng nhập lại bằng mật khẩu mới nhé.', { title: 'Đổi mật khẩu thành công' });
    }
  } catch (error) {
    console.error('Force password change failed:', error);
    if (errorDiv) {
      errorDiv.textContent = error.message || 'Không thể đổi mật khẩu. Vui lòng thử lại.';
      errorDiv.classList.remove('hidden');
    }
    if (submitBtn) {
      submitBtn.disabled = false;
      submitBtn.textContent = 'Đổi mật khẩu';
    }
  }
}

/**
 * Check if password must be changed and show modal if needed
 */
export const checkPasswordMustChange = () => {
  const stored = localStorage.getItem('ticketing.passwordMustChange');
  if (stored === 'true') {
    passwordMustChange = true;
    showForcePasswordChangeModal();
    return true;
  }
  return false;
};

/**
 * Logout function
 */
export const logout = async () => {
  try {
    await request('/api/auth/logout', {
      method: 'POST',
      headers: Auth.getAuthHeaders(),
    });
  } catch (e) {
    // Ignore logout errors
  }
  
  // Clear auth state
  clearAuthState();
  clearToken();
  currentUser = null;
  passwordMustChange = false;
  localStorage.removeItem('ticketing.currentUser');
  localStorage.removeItem('ticketing.passwordMustChange');
  
  // Stop notification polling
  if (stopNotificationPolling) {
    stopNotificationPolling();
  }
  
  // Redirect to login
  setRoute('#/login');
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

// ==================== Expose Auth to Global Scope ====================
// This allows other modules to check for Auth module availability
const Auth = {
  // Core auth functions
  isTokenValid,
  getToken,
  setToken,
  clearToken,
  login,
  logout,
  checkPasswordMustChange,
  
  // User info
  getCurrentUser,
  setCurrentUser,
  getCurrentUsername,
  getCurrentUserDepartment,
  getPasswordMustChange,
  setPasswordMustChange,
  
  // Notifications
  getNotifications,
  setNotifications,
  loadNotifications,
  
  // Role checks
  hasRole,
  isAdmin,
  isGiamDoc,
  isTruongPhong,
  isNhanVien,
  isTruongPhongIT,
  isInITDepartment,
  isITStaff,
  isItOperator,
  isItLead,
  seesAll,
  canManageUsers,
  
  // Navigation & UI
  updateTokenStatus,
  updateNavVisibility,
  
  // Routing
  setRoute,
  
  // Auth ready callback (for modules that need to wait for auth)
  onAuthReady: (callback) => {
    if (isTokenValid()) {
      callback();
    } else {
      // Wait for token to be valid
      const checkToken = setInterval(() => {
        if (isTokenValid()) {
          clearInterval(checkToken);
          callback();
        }
      }, 100);
    }
  },
  
  // Get auth headers for API calls
  getAuthHeaders: () => {
    const token = getToken();
    return {
      'Content-Type': 'application/json',
      ...(token && { 'Authorization': `Bearer ${token}` })
    };
  },
  
  // Parse JWT
  parseJwt
};

// Expose to global scope
window.Auth = Auth;
window.onAuthReady = Auth.onAuthReady; // Also expose onAuthReady globally

export { Auth };
export { DEMO_CREDENTIALS, currentUser, notifications, notificationPollingTimer };
