const API_BASE = '';
const tokenKey = 'ticketing.jwt';
const UNASSIGNED_VALUE = 'UNASSIGNED';

const loginForm = document.getElementById('login-form');
const loginError = document.getElementById('login-error');
const loginPrefillNote = document.getElementById('login-prefill-note');
const logoutBtn = document.getElementById('logout-btn');
const tokenStatus = document.getElementById('token-status');
const sidebarUsername = document.getElementById('sidebar-username');
const sidebarRole = document.getElementById('sidebar-role');
const logoMark = document.getElementById('logo-mark');
const logoMarkImg = document.getElementById('logo-mark-img');
const logoMarkFallback = document.getElementById('logo-mark-fallback');
const userDisplay = document.getElementById('user-display');
const themeToggle = document.getElementById('theme-toggle');
const apiStatus = document.getElementById('api-status');
const navLinks = Array.from(document.querySelectorAll('.nav-card a'));
const views = Array.from(document.querySelectorAll('.view'));
const navCard = document.querySelector('.nav-card');
const appShell = document.getElementById('app-shell');
const navToggle = document.getElementById('nav-toggle');

const createForm = document.getElementById('create-form');
const assigneeCreate = document.getElementById('assignee-create');
const ticketsList = document.getElementById('tickets-list');
const refreshBtn = document.getElementById('refresh-btn');
const clearFiltersBtn = document.getElementById('clear-filters-btn');
const autoRefreshToggle = document.getElementById('auto-refresh-toggle');
const refreshStatus = document.getElementById('refresh-status');
const filterSearch = document.getElementById('filter-search');
const dashboardCards = document.getElementById('dashboard-cards');
const dashboardStatus = document.getElementById('dashboard-status');
const queuePanel = document.getElementById('queue-panel');
const queueList = document.getElementById('queue-list');
const filterAssignee = document.getElementById('filter-assignee');
const filterStatus = document.getElementById('filter-status');
const filterSort = document.getElementById('filter-sort');
const openCreateModal = document.getElementById('open-create-modal');
const closeCreateModal = document.getElementById('close-create-modal');
const createModal = document.getElementById('create-modal');
const ticketBoard = document.getElementById('ticket-board');
const ticketsSentinel = document.getElementById('tickets-sentinel');

const ticketDetails = document.getElementById('ticket-details');
const ticketActions = document.getElementById('ticket-actions');
const statusSelect = document.getElementById('status-select');
const prioritySelect = document.getElementById('priority-select');
const assigneeInput = document.getElementById('assignee-input');
const closeDetails = document.getElementById('close-details');
const ticketDetailsPanel = document.getElementById('ticket-details-panel');
const ticketTabButtons = Array.from(document.querySelectorAll('.tab-btn'));
const ticketTabPanels = Array.from(document.querySelectorAll('[data-tab-panel]'));
const updateStatusBtn = document.getElementById('update-status-btn');
const updatePriorityBtn = document.getElementById('update-priority-btn');
const updateAssigneeBtn = document.getElementById('update-assignee-btn');
const auditDownloadBtn = document.getElementById('audit-download-btn');
const assignMeBtn = document.getElementById('assign-me-btn');
const commentPanel = document.getElementById('comment-panel');
const commentVisibility = document.getElementById('comment-visibility');
const commentBody = document.getElementById('comment-body');
const addCommentBtn = document.getElementById('add-comment-btn');
const commentsList = document.getElementById('comments-list');
const auditList = document.getElementById('audit-list');
const assignmentsList = document.getElementById('assignments-list');

const reportFrom = document.getElementById('report-from');
const reportTo = document.getElementById('report-to');
const engineerReportBtn = document.getElementById('engineer-report-btn');
const requesterReportBtn = document.getElementById('requester-report-btn');
const backlogReportBtn = document.getElementById('backlog-report-btn');
const slaReportBtn = document.getElementById('sla-report-btn');
const reportOutput = document.getElementById('report-output');
const reportDownloadBtn = document.getElementById('report-download-btn');

const adminCreateForm = document.getElementById('admin-create-form');
const adminRoleSelect = document.getElementById('admin-role-select');
const adminUsers = document.getElementById('admin-users');
const userSearch = document.getElementById('user-search');
const userSearchBtn = document.getElementById('user-search-btn');
const usersPrev = document.getElementById('users-prev');
const usersNext = document.getElementById('users-next');
const usersPage = document.getElementById('users-page');
const userDetailModal = document.getElementById('user-detail-modal');
const userDetailHeader = document.getElementById('user-detail-header');
const userDisplayName = document.getElementById('user-display-name');
const userTitle = document.getElementById('user-title');
const userEmail = document.getElementById('user-email');
const userRoleSelect = document.getElementById('user-role-select');
const userEnabledSelect = document.getElementById('user-enabled-select');
const userPasswordInput = document.getElementById('user-password-input');
const userSaveRole = document.getElementById('user-save-role');
const userSaveEnabled = document.getElementById('user-save-enabled');
const userSavePassword = document.getElementById('user-save-password');
const userSaveProfile = document.getElementById('user-save-profile');
const userDelete = document.getElementById('user-delete');
const userAudit = document.getElementById('user-audit');
const userAuditPrev = document.getElementById('user-audit-prev');
const userAuditNext = document.getElementById('user-audit-next');
const userAuditPageLabel = document.getElementById('user-audit-page');
const openUserModal = document.getElementById('open-user-modal');
const closeUserModal = document.getElementById('close-user-modal');
const userCreateModal = document.getElementById('user-create-modal');
const closeUserDetail = document.getElementById('close-user-detail');

const profileDetails = document.getElementById('profile-details');
const profilePasswordForm = document.getElementById('profile-password-form');
const profileDisplayName = document.getElementById('profile-display-name');
const profileTitle = document.getElementById('profile-title');
const profileEmail = document.getElementById('profile-email');
const profileSaveBtn = document.getElementById('profile-save-btn');
const profileAvatar = document.getElementById('profile-avatar');
const profileAvatarPreview = document.getElementById('profile-avatar-preview');
const profileAvatarFallback = document.getElementById('profile-avatar-fallback');
const profileCard = document.getElementById('profile-card');

let selectedTicket = null;
let userPage = 0;
const userPageSize = 10;
let selectedUser = null;
let userAuditPage = 0;
const userAuditPageSize = 5;
let userAuditEntries = [];
const userAvatarMap = new Map();
let userAvatarReady = false;
const reportButtons = [engineerReportBtn, requesterReportBtn, backlogReportBtn, slaReportBtn].filter(Boolean);
let currentReport = null;
let autoRefreshTimer = null;
const autoRefreshKey = 'ticketing.autoRefresh';
const autoRefreshMs = 60000;
let ticketPage = 0;
const ticketPageSize = 20;
let ticketHasMore = true;
let ticketLoading = false;
let ticketsCache = [];

const LIVE_ENGINEER_CREDENTIALS = {
  username: 'engineer',
  password: 'engineer123',
};

const setApiStatus = (text) => {
  let viText = text;
  if (text === 'idle') viText = 'chờ';
  else if (text === 'working') viText = 'đang xử lý';
  else if (text === 'ok') viText = 'sẵn sàng';
  else if (typeof text === 'string' && text.startsWith('error')) viText = text.replace('error', 'lỗi');
  apiStatus.textContent = `Trạng thái API: ${viText}`;
};

const shouldPrefillEngineerLogin = () => window.location.protocol !== 'file:';

const applyLiveEngineerLoginPrefill = () => {
  if (!loginForm) return;
  const usernameInput = loginForm.elements.namedItem('username');
  const passwordInput = loginForm.elements.namedItem('password');
  const shouldPrefill = shouldPrefillEngineerLogin();
  if (loginPrefillNote) {
    loginPrefillNote.classList.toggle('hidden', !shouldPrefill);
  }
  if (!shouldPrefill || !usernameInput || !passwordInput) {
    return;
  }
  usernameInput.value = LIVE_ENGINEER_CREDENTIALS.username;
  passwordInput.value = LIVE_ENGINEER_CREDENTIALS.password;
};

const getToken = () => localStorage.getItem(tokenKey);
const clearToken = () => localStorage.removeItem(tokenKey);
const setToken = (token) => {
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

const isTokenValid = () => {
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

const hasRole = (role) => getRoles().includes(role);

const getCurrentUsername = () => {
  const token = isTokenValid() ? getToken() : null;
  if (!token) return null;
  const payload = parseJwt(token);
  return payload && payload.sub ? payload.sub : null;
};

const updateTokenStatus = () => {
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
  const payload = token ? parseJwt(token) : null;
  const username = payload && payload.sub ? payload.sub : 'Phiếu hỗ trợ';
  const roles = payload && payload.roles ? payload.roles : [];
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
  if (userDisplay) {
    userDisplay.textContent = token ? formatName(username) : '—';
  }
};

const formatName = (value) => {
  if (!value) return '';
  return value
    .split(/[\s_-]+/)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1).toLowerCase())
    .join(' ');
};

const STATUS_LABELS = {
  NEW: 'Mới',
  IN_PROGRESS: 'Đang xử lý',
  BLOCKED: 'Bị chặn',
  CLOSED: 'Đã đóng',
  LOW: 'Thấp',
  MEDIUM: 'Trung bình',
  HIGH: 'Cao',
  URGENT: 'Khẩn cấp',
  SOFTWARE: 'Phần mềm',
  ACCESS: 'Quyền truy cập',
  NETWORK: 'Mạng',
  HARDWARE: 'Phần cứng',
  OTHER: 'Khác',
};

const ROLE_LABELS = {
  REQUESTER: 'Người yêu cầu',
  ENGINEER: 'Kỹ sư',
  ADMIN: 'Quản trị viên',
};

const formatRole = (role) => {
  if (!role) return '';
  const clean = role.replace('ROLE_', '').toUpperCase();
  return ROLE_LABELS[clean] || formatName(clean);
};

const AUDIT_ACTION_LABELS = {
  CREATED: 'Đã tạo',
  STATUS_CHANGED: 'Thay đổi trạng thái',
  ASSIGNEE_CHANGED: 'Thay đổi người phân công',
  COMMENT_ADDED: 'Đã thêm bình luận',
  PRIORITY_CHANGED: 'Thay đổi độ ưu tiên',
  LOGIN: 'Đăng nhập',
  PASSWORD_CHANGED: 'Đổi mật khẩu',
  PASSWORD_RESET: 'Đặt lại mật khẩu',
  USER_CREATED: 'Tạo người dùng',
  USER_DELETED: 'Xóa người dùng',
  USER_DISABLED: 'Vô hiệu hóa người dùng',
  USER_ENABLED: 'Kích hoạt người dùng',
  ROLE_CHANGED: 'Thay đổi vai trò',
  PROFILE_UPDATED: 'Cập nhật hồ sơ',
};

const formatAuditAction = (action) => {
  if (!action) return '';
  const upper = String(action).toUpperCase();
  return AUDIT_ACTION_LABELS[upper] || formatName(action.replace(/_/g, ' '));
};

const formatFieldName = (field) => {
  if (!field) return '';
  const map = {
    status: 'trạng thái',
    priority: 'độ ưu tiên',
    assignee: 'người phân công',
    comment: 'bình luận',
  };
  return map[field.toLowerCase()] || field;
};

const formatAuditValue = (field, value) => {
  if (!value) return '';
  const upper = String(value).toUpperCase();
  if (STATUS_LABELS[upper]) {
    return STATUS_LABELS[upper];
  }
  if (ROLE_LABELS[upper]) {
    return ROLE_LABELS[upper];
  }
  if (upper === 'UNASSIGNED') {
    return 'Chưa phân công';
  }
  return value;
};

const formatSlaBucket = (bucket) => {
  if (!bucket) return '';
  return bucket.replace(/days/g, 'ngày');
};

const formatStatus = (value) => {
  if (!value) return '';
  const upper = String(value).toUpperCase();
  if (STATUS_LABELS[upper]) {
    return STATUS_LABELS[upper];
  }
  return formatName(value.replace(/_/g, ' '));
};

const canManageUsers = () => hasRole('ROLE_ADMIN') || hasRole('ROLE_ENGINEER');

const applyAdminRoleOptions = () => {
  if (!adminRoleSelect) return;
  adminRoleSelect.innerHTML = '';
  const roles = hasRole('ROLE_ADMIN') ? ['REQUESTER', 'ENGINEER', 'ADMIN'] : ['REQUESTER'];
  roles.forEach((role) => {
    const option = document.createElement('option');
    option.value = role;
    option.textContent = formatRole(role);
    adminRoleSelect.appendChild(option);
  });
};

const loadUserAvatarMap = async () => {
  userAvatarMap.clear();
  userAvatarReady = false;
  if (!canManageUsers()) return;
  try {
    const data = await request('/api/users?page=0&size=200');
    data.content.forEach((user) => {
      const entry = {
        avatarUrl: user.avatarUrl,
        displayName: user.displayName,
      };
      if (user.username) {
        userAvatarMap.set(user.username.toLowerCase(), entry);
      }
      if (user.displayName) {
        userAvatarMap.set(user.displayName.toLowerCase(), entry);
      }
    });
    populateAssigneeSelect(filterAssignee, {
      includeAll: true,
      includeUnassigned: true,
      useDisplayNameValues: true,
    });
    populateAssigneeSelect(assigneeInput, { includeUnassigned: true });
    populateAssigneeSelect(assigneeCreate, { includeUnassigned: true });
    userAvatarReady = true;
  } catch (error) {
    userAvatarMap.clear();
  }
};

const getDisplayName = (value) => {
  if (!value) return '';
  const entry = userAvatarMap.get(value.toLowerCase());
  if (entry && entry.displayName) {
    return entry.displayName;
  }
  return formatName(value);
};

const canManageTargetUser = (user) => {
  if (!user) return false;
  if (hasRole('ROLE_ADMIN')) return true;
  return user.role === 'REQUESTER';
};

const applyUserDetailControls = () => {
  if (!selectedUser) return;
  const canManage = canManageTargetUser(selectedUser);
  if (userRoleSelect) {
    userRoleSelect.innerHTML = '';
    const roles = hasRole('ROLE_ADMIN') ? ['REQUESTER', 'ENGINEER', 'ADMIN'] : ['REQUESTER'];
    roles.forEach((role) => {
      const option = document.createElement('option');
      option.value = role;
      option.textContent = formatRole(role);
      userRoleSelect.appendChild(option);
    });
    userRoleSelect.value = selectedUser.role;
    userRoleSelect.disabled = !canManage || !hasRole('ROLE_ADMIN');
  }
  if (userDisplayName) {
    userDisplayName.value = selectedUser.displayName || '';
    userDisplayName.disabled = !canManage;
  }
  if (userTitle) {
    userTitle.value = selectedUser.title || '';
    userTitle.disabled = !canManage;
  }
  if (userEmail) {
    userEmail.value = selectedUser.email || '';
    userEmail.disabled = !canManage;
  }
  if (userEnabledSelect) {
    userEnabledSelect.value = selectedUser.enabled ? 'true' : 'false';
    userEnabledSelect.disabled = !canManage;
  }
  if (userSaveRole) {
    userSaveRole.disabled = !canManage || !hasRole('ROLE_ADMIN');
  }
  if (userSaveEnabled) {
    userSaveEnabled.disabled = !canManage;
  }
  if (userSavePassword) {
    userSavePassword.disabled = !canManage;
  }
  if (userSaveProfile) {
    userSaveProfile.disabled = !canManage;
  }
  if (userDelete) {
    userDelete.disabled = !canManage;
  }
};

const validateProfileFields = (fields) => {
  let valid = true;
  fields.forEach(({ label, input }) => {
    if (!input) return;
    clearFieldError(input);
    if (!input.value || !input.value.trim()) {
      setFieldError(input, `${label} là bắt buộc.`);
      valid = false;
      return;
    }
    if (!input.checkValidity()) {
      setFieldError(input, input.validationMessage || `${label} không hợp lệ.`);
      valid = false;
    }
  });
  return valid;
};

const setFieldError = (input, message) => {
  if (!input) return;
  const label = input.closest('label');
  if (!label) return;
  label.classList.add('invalid');
  let error = label.querySelector('.field-error');
  if (!error) {
    error = document.createElement('span');
    error.className = 'field-error';
    label.appendChild(error);
  }
  error.textContent = message;
};

const clearFieldError = (input) => {
  if (!input) return;
  const label = input.closest('label');
  if (!label) return;
  label.classList.remove('invalid');
  const error = label.querySelector('.field-error');
  if (error) {
    error.remove();
  }
};

const clearFormErrors = (form) => {
  if (!form) return;
  form.querySelectorAll('.field-error').forEach((node) => node.remove());
  form.querySelectorAll('label.invalid').forEach((label) => label.classList.remove('invalid'));
};

const validateForm = (form) => {
  if (!form) return true;
  clearFormErrors(form);
  let valid = true;
  const inputs = Array.from(form.querySelectorAll('input, select, textarea'));
  inputs.forEach((input) => {
    if (input.disabled || input.type === 'button' || input.type === 'submit') return;
    if (!input.checkValidity()) {
      setFieldError(input, input.validationMessage || 'Bắt buộc');
      valid = false;
    }
  });
  return valid;
};

const attachValidationHandlers = (container) => {
  if (!container) return;
  const inputs = container.querySelectorAll('input, select, textarea');
  inputs.forEach((input) => {
    const handler = () => clearFieldError(input);
    input.addEventListener('input', handler);
    input.addEventListener('change', handler);
  });
};

const setSelectValue = (select, value) => {
  if (!select) return;
  const options = Array.from(select.options);
  const match = options.some((option) => option.value === value);
  select.value = match ? value : options[0]?.value || '';
};

const normalizeAssigneeInput = (value) => {
  if (!value || value === UNASSIGNED_VALUE) {
    return null;
  }
  return value;
};

const ensureAssigneeOption = (select, value) => {
  if (!select || !value) return;
  const exists = Array.from(select.options).some((option) => option.value === value);
  if (!exists) {
    const option = document.createElement('option');
    option.value = value;
    option.textContent = formatName(value);
    select.appendChild(option);
  }
};

let engineerOptions = [];

const populateAssigneeSelect = (
  select,
  { includeAll = false, includeUnassigned = false, useDisplayNameValues = false } = {}
) => {
  if (!select) return;
  const current = select.value;
  select.innerHTML = '';
  if (includeAll) {
    const option = document.createElement('option');
    option.value = '';
    option.textContent = 'Tất cả người được phân công';
    select.appendChild(option);
  }
  if (includeUnassigned) {
    const option = document.createElement('option');
    option.value = UNASSIGNED_VALUE;
    option.textContent = 'Chưa phân công';
    select.appendChild(option);
  }
  engineerOptions.forEach((username) => {
    const displayName = getDisplayName(username);
    const option = document.createElement('option');
    option.value = useDisplayNameValues && displayName ? displayName : username;
    option.textContent = displayName || formatName(username);
    option.dataset.username = username;
    if (displayName) {
      option.dataset.displayName = displayName;
    }
    select.appendChild(option);
  });
  if (current) {
    select.value = current;
  }
};

const loadEngineerOptions = async () => {
  if (!isTokenValid()) return;
  try {
    engineerOptions = await request('/api/users/engineers');
    populateAssigneeSelect(filterAssignee, {
      includeAll: true,
      includeUnassigned: true,
      useDisplayNameValues: true,
    });
    populateAssigneeSelect(assigneeInput, { includeUnassigned: true });
    populateAssigneeSelect(assigneeCreate, { includeUnassigned: true });
  } catch (error) {
    engineerOptions = [];
  }
};

const applyRoleControls = () => {
  const requesterOnly = hasRole('ROLE_REQUESTER') && !hasRole('ROLE_ENGINEER') && !hasRole('ROLE_ADMIN');
  if (statusSelect) {
    statusSelect.disabled = requesterOnly;
    Array.from(statusSelect.options).forEach((option) => {
      option.disabled = requesterOnly;
    });
  }
  if (prioritySelect) {
    prioritySelect.disabled = requesterOnly;
  }
  if (assigneeInput) {
    assigneeInput.disabled = requesterOnly;
  }
  if (updateStatusBtn) {
    updateStatusBtn.classList.toggle('hidden', requesterOnly);
  }
  if (updatePriorityBtn) {
    updatePriorityBtn.classList.toggle('hidden', requesterOnly);
  }
  if (updateAssigneeBtn) {
    updateAssigneeBtn.classList.toggle('hidden', requesterOnly);
  }
  if (assignMeBtn) {
    assignMeBtn.classList.toggle('hidden', requesterOnly);
  }
  updateActionButtons();
};

const updateActionButtons = () => {
  if (!selectedTicket) return;
  if (updateStatusBtn) {
    updateStatusBtn.disabled = statusSelect?.value === selectedTicket.status;
  }
  if (updatePriorityBtn) {
    updatePriorityBtn.disabled = prioritySelect?.value === selectedTicket.priority;
  }
  if (updateAssigneeBtn) {
    const currentAssignee = selectedTicket.assigneeName || UNASSIGNED_VALUE;
    updateAssigneeBtn.disabled = assigneeInput?.value === currentAssignee;
  }
  if (assignMeBtn) {
    const currentUser = getCurrentUsername();
    assignMeBtn.disabled = !currentUser || selectedTicket.assigneeName === currentUser;
  }
};

const authHeaders = () => {
  const token = getToken();
  return token ? { Authorization: `Bearer ${token}` } : {};
};

const request = async (path, options = {}) => {
  setApiStatus('working');
  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(options.headers || {}),
      ...authHeaders(),
    },
  });
  setApiStatus(response.ok ? 'ok' : `error ${response.status}`);
  if (!response.ok) {
    if ((response.status === 401 || response.status === 403) && getToken()) {
      setToken(null);
      showView('login');
    }
    const message = await response.text();
    throw new Error(message || `Yêu cầu thất bại: ${response.status}`);
  }
  if (response.status === 204) {
    return null;
  }
  return response.json();
};

const login = async (payload) => {
  const data = await request('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
  setToken(data.token);
  setRoute('#/tickets');
};

const setRoute = (hash) => {
  window.location.hash = hash;
};

const showView = (viewName) => {
  views.forEach((view) => {
    view.classList.toggle('active', view.dataset.view === viewName);
  });
  navLinks.forEach((link) => {
    link.classList.toggle('active', link.getAttribute('href') === `#/${viewName}`);
  });
  if (viewName !== 'tickets' && ticketDetailsPanel) {
    ticketDetailsPanel.classList.add('hidden');
    selectedTicket = null;
  }
  if (viewName === 'admin' && canManageUsers()) {
    loadAdminUsers().catch(() => {});
  }
  if (viewName === 'login') {
    applyLiveEngineerLoginPrefill();
  }
};

const routeGuard = () => {
  const token = isTokenValid() ? getToken() : null;
  if (!token && getToken()) {
    setToken(null);
  }
  const hash = window.location.hash || '#/login';
  const viewName = hash.replace('#/', '');
  if (!token && viewName !== 'login') {
    showView('login');
    return;
  }
  if (viewName === 'admin' && !(hasRole('ROLE_ADMIN') || hasRole('ROLE_ENGINEER'))) {
    showView('tickets');
    return;
  }
  if (viewName === 'reports' && !(hasRole('ROLE_ADMIN') || hasRole('ROLE_ENGINEER'))) {
    showView('tickets');
    return;
  }
  if (viewName === 'login' && token) {
    showView('tickets');
    return;
  }
  showView(viewName);
};

const updateNavVisibility = () => {
  navLinks.forEach((link) => {
    const required = link.dataset.role;
    if (!required) return;
    const roles = required.split(',').map((role) => role.trim()).filter(Boolean);
    const allowed = roles.some((role) => hasRole(role));
    link.classList.toggle('hidden', !allowed);
  });
  if (assigneeCreate) {
    const requesterOnly = hasRole('ROLE_REQUESTER') && !hasRole('ROLE_ENGINEER') && !hasRole('ROLE_ADMIN');
    assigneeCreate.closest('label').classList.toggle('hidden', requesterOnly);
  }
  applyRoleControls();
  applyAdminRoleOptions();
};

const getAssigneeLabel = (ticket) => {
  if (!ticket || !ticket.assigneeName) return '';
  const key = ticket.assigneeName.toLowerCase();
  const profile = userAvatarMap.get(key);
  if (profile && profile.displayName) {
    return profile.displayName;
  }
  return formatName(ticket.assigneeName);
};

const renderTickets = (rows, { append = false } = {}) => {
  if (!append) {
    ticketsList.innerHTML = '';
  }
  rows.forEach((ticket, index) => {
    const row = document.createElement('div');
    row.className = 'ticket-row';
    row.style.animationDelay = `${index * 40}ms`;
    const createdAt = ticket.createdAt ? new Date(ticket.createdAt).toLocaleString('vi-VN') : 'Không rõ thời gian';
    const priorityClass = ticket.priority ? ticket.priority.toLowerCase() : 'low';
    const statusClass = ticket.status ? ticket.status.toLowerCase().replace(/_/g, '-') : 'new';
    const assigneeKey = ticket.assigneeName ? ticket.assigneeName.toLowerCase() : '';
    const assigneeProfile = assigneeKey ? userAvatarMap.get(assigneeKey) : null;
    const assigneeLabel = assigneeProfile && assigneeProfile.displayName
      ? assigneeProfile.displayName
      : (ticket.assigneeName ? formatName(ticket.assigneeName) : 'Chưa phân công');
    const avatarLetter = assigneeLabel.charAt(0).toUpperCase();
    const avatarImage = assigneeProfile && assigneeProfile.avatarUrl
      ? `<img src="${assigneeProfile.avatarUrl}" alt="${assigneeLabel}" />`
      : '';
    row.innerHTML = `
      <div class="assignee-avatar" aria-hidden="true">
        ${avatarImage || avatarLetter}
      </div>
      <div class="ticket-row-main">
        <h4>${ticket.title}</h4>
        <div class="ticket-row-meta">${ticket.ticketNumber} • ${formatStatus(ticket.category)}</div>
      </div>
      <div class="ticket-row-tags">
        <span class="status ${statusClass}">${formatStatus(ticket.status)}</span>
        <span class="priority ${priorityClass}">${formatStatus(ticket.priority)}</span>
      </div>
      <div class="ticket-row-side">
        <strong>${assigneeLabel}</strong>
        <span>${createdAt}</span>
      </div>
    `;
    row.addEventListener('click', () => selectTicket(ticket));
    ticketsList.appendChild(row);
  });
};

const loadTickets = async ({ reset = false } = {}) => {
  if (ticketLoading) return;
  if (canManageUsers() && !userAvatarReady) {
    await loadUserAvatarMap();
  }
  if (reset) {
    ticketPage = 0;
    ticketHasMore = true;
    ticketsCache = [];
    ticketsList.innerHTML = '';
  }
  if (!ticketHasMore) return;
  ticketLoading = true;
  if (refreshStatus) {
    refreshStatus.textContent = 'Đang làm mới…';
  }
  const params = new URLSearchParams();
  let assigneeFallback = null;
  if (filterAssignee && filterAssignee.value) {
    const selectedOption = filterAssignee.selectedOptions[0];
    params.append('assignee', filterAssignee.value);
    if (selectedOption && selectedOption.dataset.username) {
      assigneeFallback = selectedOption.dataset.username;
    }
  }
  if (filterStatus.value) params.append('status', filterStatus.value);
  if (!filterStatus.value) {
    params.append('excludeClosed', 'true');
  }
  if (filterSearch && filterSearch.value.trim()) {
    params.append('search', filterSearch.value.trim());
  }
  params.append('page', String(ticketPage));
  params.append('size', String(ticketPageSize));
  const sortValue = filterSort && filterSort.value ? filterSort.value : 'createdAt,desc';
  if (!sortValue.startsWith('assigneeDisplay')) {
    params.append('sort', sortValue);
  } else {
    params.append('sort', 'createdAt,desc');
  }
  let data = await request(`/api/tickets?${params.toString()}`);
  if (assigneeFallback && data.content.length === 0 && assigneeFallback !== params.get('assignee')) {
    params.set('assignee', assigneeFallback);
    data = await request(`/api/tickets?${params.toString()}`);
  }
  const rows = data.content.slice();
  if (sortValue.startsWith('assigneeDisplay')) {
    ticketsCache = ticketsCache.concat(rows);
    const sorted = ticketsCache.slice().sort((a, b) => {
      const left = getAssigneeLabel(a).toLowerCase();
      const right = getAssigneeLabel(b).toLowerCase();
      if (!left && right) return 1;
      if (!right && left) return -1;
      return left.localeCompare(right);
    });
    renderTickets(sorted, { append: false });
  } else {
    renderTickets(rows, { append: !reset });
  }
  ticketHasMore = !data.last;
  ticketPage = data.number + 1;
  if (ticketsSentinel) {
    ticketsSentinel.textContent = ticketHasMore ? 'Đang tải thêm…' : 'Hết danh sách';
    ticketsSentinel.classList.toggle('hidden', !ticketHasMore);
  }
  if (refreshStatus) {
    refreshStatus.textContent = `Cập nhật lần cuối lúc ${new Date().toLocaleTimeString('vi-VN')}`;
  }
  ticketLoading = false;
};

const selectTicket = async (ticket) => {
  const full = await request(`/api/tickets/${ticket.id}`);
  selectedTicket = full;
  const createdAt = full.createdAt ? new Date(full.createdAt).toLocaleString('vi-VN') : 'Không rõ thời gian';
  const updatedAt = full.updatedAt ? new Date(full.updatedAt).toLocaleString('vi-VN') : 'Không rõ thời gian';
  const assignee = full.assigneeName ? formatName(full.assigneeName) : 'Chưa phân công';
  ticketDetails.innerHTML = `
    <div class="ticket-detail-card">
      <div class="ticket-detail-header">
        <h4>${full.title}</h4>
        <div class="ticket-detail-chips">
          <span class="status ${full.status ? full.status.toLowerCase().replace(/_/g, '-') : 'new'}">${formatStatus(full.status)}</span>
          <span class="priority ${full.priority ? full.priority.toLowerCase() : 'low'}">${formatStatus(full.priority)}</span>
          <span class="pill">${formatStatus(full.category)}</span>
        </div>
      </div>
      <p>${full.description}</p>
      <div class="ticket-detail-grid">
        <div class="ticket-detail-field">
          <span>Người được phân công</span>
          <strong>${assignee}</strong>
        </div>
        <div class="ticket-detail-field">
          <span>Người yêu cầu</span>
          <strong>${formatName(full.requesterName)}</strong>
        </div>
        <div class="ticket-detail-field">
          <span>Mã phiếu</span>
          <strong>${full.ticketNumber}</strong>
        </div>
        <div class="ticket-detail-field">
          <span>Ngày tạo</span>
          <strong>${createdAt}</strong>
        </div>
        <div class="ticket-detail-field">
          <span>Cập nhật</span>
          <strong>${updatedAt}</strong>
        </div>
      </div>
    </div>
  `;
  ticketActions.classList.remove('hidden');
  commentPanel.classList.remove('hidden');
  ticketDetailsPanel.classList.remove('hidden');
  if (ticketBoard) {
    ticketBoard.classList.add('has-detail');
  }
  ticketTabButtons.forEach((btn) => {
    btn.classList.toggle('active', btn.dataset.tab === 'comments');
  });
  ticketTabPanels.forEach((panel) => {
    panel.classList.toggle('active', panel.dataset.tabPanel === 'comments');
  });
  setSelectValue(statusSelect, full.status);
  setSelectValue(prioritySelect, full.priority);
  ensureAssigneeOption(assigneeInput, full.assigneeName);
  assigneeInput.value = full.assigneeName || UNASSIGNED_VALUE;
  applyRoleControls();
  await Promise.all([loadComments(full.id), loadAudit(full.id), loadAssignments(full.id)]);
};

const loadComments = async (ticketId) => {
  const data = await request(`/api/tickets/${ticketId}/comments`);
  commentsList.innerHTML = '';
  data.forEach((comment) => {
    const item = document.createElement('div');
    item.className = 'list-item';
    item.textContent = `${comment.actorName || 'Hệ thống'}: ${comment.body}`;
    commentsList.appendChild(item);
  });
};

const loadAudit = async (ticketId) => {
  const data = await request(`/api/tickets/${ticketId}/audit`);
  auditList.innerHTML = '';
  data.forEach((entry) => {
    const item = document.createElement('div');
    item.className = 'list-item';
    const when = entry.createdAt ? new Date(entry.createdAt).toLocaleString('vi-VN') : 'không rõ thời gian';
    const actor = entry.actorName ? `bởi ${entry.actorName}` : '';
    const detail = `${formatFieldName(entry.fieldName)} ${formatAuditValue(entry.fieldName, entry.oldValue)} → ${formatAuditValue(entry.fieldName, entry.newValue)}`.trim();
    item.textContent = `${when} • ${formatAuditAction(entry.action)} ${actor} ${detail}`.trim();
    auditList.appendChild(item);
  });
};

const downloadAuditCsv = async () => {
  if (!selectedTicket) return;
  const response = await fetch(`/api/tickets/${selectedTicket.id}/audit/export`, {
    headers: {
      ...authHeaders(),
    },
  });
  if (!response.ok) {
    throw new Error(`Xuất dữ liệu thất bại: ${response.status}`);
  }
  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = `phieu-${selectedTicket.id}-kiem-tra.csv`;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
};

const loadAssignments = async (ticketId) => {
  const data = await request(`/api/tickets/${ticketId}/assignments`);
  assignmentsList.innerHTML = '';
  data.forEach((assignment) => {
    const item = document.createElement('div');
    item.className = 'list-item';
    const prev = assignment.previousAssignee ? formatName(assignment.previousAssignee) : 'Chưa phân công';
    const next = assignment.newAssignee ? formatName(assignment.newAssignee) : 'Chưa phân công';
    item.textContent = `${prev} → ${next}`;
    assignmentsList.appendChild(item);
  });
};

const updateStatus = async () => {
  if (!selectedTicket) return;
  if (statusSelect.value === selectedTicket.status) return;
  await request(`/api/tickets/${selectedTicket.id}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ status: statusSelect.value }),
  });
  await loadTickets();
  await selectTicket({ id: selectedTicket.id });
};

const updatePriority = async () => {
  if (!selectedTicket) return;
  if (prioritySelect.value === selectedTicket.priority) return;
  await request(`/api/tickets/${selectedTicket.id}/priority`, {
    method: 'PATCH',
    body: JSON.stringify({ priority: prioritySelect.value }),
  });
  await loadTickets();
  await selectTicket({ id: selectedTicket.id });
};

const updateAssignee = async () => {
  if (!selectedTicket) return;
  const assigneeName = normalizeAssigneeInput(assigneeInput.value);
  if ((selectedTicket.assigneeName || null) === assigneeName) return;
  await request(`/api/tickets/${selectedTicket.id}/assignee`, {
    method: 'PATCH',
    body: JSON.stringify({ assigneeName }),
  });
  await loadTickets();
  await selectTicket({ id: selectedTicket.id });
};

const assignToMe = async () => {
  if (!selectedTicket) return;
  await request(`/api/tickets/${selectedTicket.id}/assign/me`, {
    method: 'POST',
  });
  await loadTickets();
  await loadQueue();
  await selectTicket({ id: selectedTicket.id });
};

const addComment = async () => {
  if (!selectedTicket) return;
  clearFieldError(commentBody);
  if (!commentBody.value || !commentBody.value.trim()) {
    setFieldError(commentBody, 'Bình luận là bắt buộc.');
    return;
  }
  if (!commentBody.checkValidity()) {
    setFieldError(commentBody, commentBody.validationMessage || 'Bình luận không hợp lệ.');
    return;
  }
  await request(`/api/tickets/${selectedTicket.id}/comments`, {
    method: 'POST',
    body: JSON.stringify({
      visibility: commentVisibility.value,
      body: commentBody.value,
    }),
  });
  commentBody.value = '';
  await loadComments(selectedTicket.id);
};

const renderTable = (columns, rows) => {
  const table = document.createElement('table');
  table.className = 'data-table';
  const thead = document.createElement('thead');
  const headerRow = document.createElement('tr');
  columns.forEach((col) => {
    const th = document.createElement('th');
    th.textContent = col;
    headerRow.appendChild(th);
  });
  thead.appendChild(headerRow);
  table.appendChild(thead);
  const tbody = document.createElement('tbody');
  rows.forEach((row) => {
    const tr = document.createElement('tr');
    columns.forEach((col) => {
      const td = document.createElement('td');
      td.textContent = row[col] ?? '';
      tr.appendChild(td);
    });
    tbody.appendChild(tr);
  });
  table.appendChild(tbody);
  return table;
};

const toIsoDateTime = (value, endOfDay = false) => {
  if (!value) return '';
  if (value.includes('T')) return value;
  return `${value}T${endOfDay ? '23:59:59' : '00:00:00'}`;
};

const buildReportParams = () => {
  const params = new URLSearchParams();
  const fromValue = toIsoDateTime(reportFrom.value, false);
  const toValue = toIsoDateTime(reportTo.value, true);
  if (fromValue) params.append('from', fromValue);
  if (toValue) params.append('to', toValue);
  return params.toString();
};

const filterKey = 'ticketing.ticketFilters';
const saveTicketFilters = () => {
  if (!filterAssignee || !filterStatus || !filterSort || !filterSearch) return;
  const payload = {
    assignee: filterAssignee.value,
    status: filterStatus.value,
    sort: filterSort.value,
    search: filterSearch.value,
  };
  localStorage.setItem(filterKey, JSON.stringify(payload));
};

const loadTicketFilters = () => {
  if (!filterAssignee || !filterStatus || !filterSort || !filterSearch) return;
  const raw = localStorage.getItem(filterKey);
  if (!raw) return;
  try {
    const payload = JSON.parse(raw);
    if (payload.assignee !== undefined) filterAssignee.value = payload.assignee;
    if (payload.status !== undefined) filterStatus.value = payload.status;
    if (payload.sort !== undefined) filterSort.value = payload.sort;
    if (payload.search !== undefined) filterSearch.value = payload.search;
    ticketPage = 0;
  } catch (error) {
    localStorage.removeItem(filterKey);
  }
};

const initReportDates = () => {
  if (!reportFrom || !reportTo) return;
  const today = new Date();
  const start = new Date();
  start.setDate(today.getDate() - 30);
  const formatDate = (value) => value.toISOString().slice(0, 10);
  if (!reportFrom.value) {
    reportFrom.value = formatDate(start);
  }
  if (!reportTo.value) {
    reportTo.value = formatDate(today);
  }
};

const renderReportTable = (columns, rows) => {
  reportOutput.innerHTML = '';
  if (!rows.length) {
    reportOutput.textContent = 'Không có kết quả.';
    return;
  }
  const wrap = document.createElement('div');
  wrap.className = 'table-wrap';
  wrap.appendChild(renderTable(columns, rows));
  reportOutput.appendChild(wrap);
};

const setActiveReportButton = (button) => {
  reportButtons.forEach((btn) => btn.classList.remove('active'));
  if (button) {
    button.classList.add('active');
  }
};

const setCurrentReport = (title, columns, rows) => {
  currentReport = {
    title,
    columns,
    rows,
  };
};

const downloadReportCsv = () => {
  if (!currentReport || !currentReport.rows.length) {
    alert('Hãy chạy báo cáo trước khi tải xuống.');
    return;
  }
  const escapeCsv = (value) => {
    if (value === null || value === undefined) return '';
    const str = String(value);
    if (/[",\n]/.test(str)) {
      return `"${str.replace(/"/g, '""')}"`;
    }
    return str;
  };
  const lines = [];
  lines.push(currentReport.columns.map(escapeCsv).join(','));
  currentReport.rows.forEach((row) => {
    const line = currentReport.columns.map((col) => escapeCsv(row[col]));
    lines.push(line.join(','));
  });
  const blob = new Blob([lines.join('\n')], { type: 'text/csv' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  const stamp = new Date().toISOString().slice(0, 10);
  link.href = url;
  link.download = `${currentReport.title || 'report'}-${stamp}.csv`;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
};

const formatHours = (value) => Number.isFinite(value) ? value.toFixed(2) : '0.00';

const buildDashboardParams = () => {
  const end = new Date();
  const start = new Date();
  start.setDate(end.getDate() - 30);
  const params = new URLSearchParams();
  params.append('from', start.toISOString());
  params.append('to', end.toISOString());
  return params.toString();
};

const loadDashboard = async () => {
  if (!dashboardCards) return;
  const data = await request(`/api/tickets/reports/dashboard?${buildDashboardParams()}`);
  dashboardCards.innerHTML = `
    <div class="dashboard-card">
      <h5>Đang mở</h5>
      <strong>${data.openCount}</strong>
    </div>
    <div class="dashboard-card">
      <h5>Đã đóng (30 ngày)</h5>
      <strong>${data.closedCount}</strong>
    </div>
    <div class="dashboard-card">
      <h5>Quá hạn</h5>
      <strong>${data.overdueCount}</strong>
    </div>
    <div class="dashboard-card">
      <h5>TG xử lý TB (giờ)</h5>
      <strong>${formatHours(data.avgCompletionHours)}</strong>
    </div>
  `;
  if (dashboardStatus) {
    dashboardStatus.innerHTML = '';
    if (data.openByStatus && data.openByStatus.length) {
      data.openByStatus.forEach((item) => {
        const line = document.createElement('div');
        line.className = 'list-item';
        line.textContent = `${formatStatus(item.status)}: ${item.count}`;
        dashboardStatus.appendChild(line);
      });
    }
  }
};

const loadQueue = async () => {
  if (!queueList) return;
  if (!(hasRole('ROLE_ENGINEER') || hasRole('ROLE_ADMIN'))) {
    queueList.innerHTML = '';
    if (queuePanel) {
      queuePanel.classList.add('hidden');
    }
    return;
  }
  if (queuePanel) {
    queuePanel.classList.remove('hidden');
  }
  const data = await request('/api/tickets/queue');
  queueList.innerHTML = '';
  if (!data.length) {
    const line = document.createElement('div');
    line.className = 'list-item';
    line.textContent = 'Hàng đợi chưa phân công đang trống.';
    queueList.appendChild(line);
    return;
  }
  data.forEach((ticket) => {
    const item = document.createElement('div');
    item.className = 'list-item';
    item.textContent = `${ticket.ticketNumber} • ${ticket.title}`;
    item.addEventListener('click', () => selectTicket(ticket));
    queueList.appendChild(item);
  });
};

const loadEngineerReport = async () => {
  const data = await request(`/api/tickets/reports/engineer-summary?${buildReportParams()}`);
  const rows = data.map((row) => ({
    'Kỹ sư': getDisplayName(row.engineer),
    'Phiếu được phân công': row.ticketsAssigned,
    'Phiếu đã hoàn thành': row.ticketsCompleted,
    'TB phân công/ngày': formatHours(row.avgAssignedPerDay),
    'TB hoàn thành/ngày': formatHours(row.avgCompletedPerDay),
    'TG hoàn thành TB (giờ)': formatHours(row.avgCompletionHours),
  }));
  const columns = [
    'Kỹ sư',
    'Phiếu được phân công',
    'Phiếu đã hoàn thành',
    'TB phân công/ngày',
    'TB hoàn thành/ngày',
    'TG hoàn thành TB (giờ)',
  ];
  setActiveReportButton(engineerReportBtn);
  setCurrentReport('bao-cao-ky-su', columns, rows);
  renderReportTable(
    columns,
    rows
  );
};

const loadRequesterReport = async () => {
  const data = await request(`/api/tickets/reports/requester-summary?${buildReportParams()}`);
  const rows = data.map((row) => ({
    'Người yêu cầu': getDisplayName(row.requester),
    'Phiếu đã gửi': row.ticketsSubmitted,
    'TG hoàn thành TB (giờ)': formatHours(row.avgCompletionHours),
  }));
  const columns = ['Người yêu cầu', 'Phiếu đã gửi', 'TG hoàn thành TB (giờ)'];
  setActiveReportButton(requesterReportBtn);
  setCurrentReport('bao-cao-nguoi-yeu-cau', columns, rows);
  renderReportTable(columns, rows);
};

const loadBacklogReport = async () => {
  const data = await request(`/api/tickets/reports/backlog-aging?${buildReportParams()}`);
  const rows = data.map((row) => ({
    'Trạng thái': formatStatus(row.status),
    'Số lượng mở': row.openCount,
    'Thời gian tồn đọng TB (giờ)': formatHours(row.avgAgeHours),
  }));
  const columns = ['Trạng thái', 'Số lượng mở', 'Thời gian tồn đọng TB (giờ)'];
  setActiveReportButton(backlogReportBtn);
  setCurrentReport('thoi-gian-ton-dong', columns, rows);
  renderReportTable(columns, rows);
};

const loadSlaReport = async () => {
  const data = await request(`/api/tickets/reports/sla-buckets?${buildReportParams()}`);
  const rows = data.map((row) => ({
    'Nhóm thời gian': formatSlaBucket(row.bucket),
    'Số lượng': row.count,
  }));
  const columns = ['Nhóm thời gian', 'Số lượng'];
  setActiveReportButton(slaReportBtn);
  setCurrentReport('nhom-sla', columns, rows);
  renderReportTable(columns, rows);
};

const loadAdminUsers = async () => {
  if (!canManageUsers()) return;
  const params = new URLSearchParams({
    page: String(userPage),
    size: String(userPageSize),
  });
  if (userSearch && userSearch.value.trim()) {
    params.append('search', userSearch.value.trim());
  }
  const data = await request(`/api/users?${params.toString()}`);
  adminUsers.innerHTML = '';
  data.content.forEach((user) => {
    const row = document.createElement('tr');
    row.innerHTML = `
      <td>${user.username}</td>
      <td>${formatRole(user.role)}</td>
      <td>${user.enabled ? 'Có' : 'Không'}</td>
    `;
    row.addEventListener('click', () => selectUser(user));

    adminUsers.appendChild(row);
  });

  if (usersPage) {
    usersPage.textContent = `Trang ${data.number + 1} / ${data.totalPages || 1}`;
  }
  if (usersPrev) {
    usersPrev.disabled = data.first;
  }
  if (usersNext) {
    usersNext.disabled = data.last;
  }
};

const selectUser = async (user) => {
  selectedUser = user;
  if (userDetailModal) {
    userDetailModal.classList.remove('hidden');
  }
  if (userDetailHeader) {
    userDetailHeader.textContent = `${formatName(user.username)} (${formatRole(user.role)})`;
  }
  applyUserDetailControls();
  if (userPasswordInput) {
    userPasswordInput.value = '';
  }
  if (userAudit) {
    userAudit.innerHTML = '';
  }
  try {
    userAuditEntries = await request(`/api/users/audit?targetUsername=${encodeURIComponent(user.username)}`);
    userAuditPage = 0;
    renderUserAuditPage();
  } catch (error) {
    if (userAudit) {
      const item = document.createElement('div');
      item.className = 'list-item';
      item.textContent = error.message;
      userAudit.appendChild(item);
    }
  }
};

const renderUserAuditPage = () => {
  if (!userAudit) return;
  userAudit.innerHTML = '';
  if (!userAuditEntries.length) {
    const item = document.createElement('div');
    item.className = 'list-item';
    item.textContent = 'Chưa có nhật ký kiểm tra.';
    userAudit.appendChild(item);
  } else {
    const start = userAuditPage * userAuditPageSize;
    const pageItems = userAuditEntries.slice(start, start + userAuditPageSize);
    pageItems.forEach((entry) => {
      const item = document.createElement('div');
      item.className = 'list-item';
      const when = entry.createdAt ? new Date(entry.createdAt).toLocaleString('vi-VN') : 'không rõ thời gian';
      item.textContent = `${when} • ${formatAuditAction(entry.action)} bởi ${entry.actorUsername} (${formatRole(entry.actorRole)})`;
      userAudit.appendChild(item);
    });
  }
  const totalPages = Math.max(1, Math.ceil(userAuditEntries.length / userAuditPageSize));
  if (userAuditPageLabel) {
    userAuditPageLabel.textContent = `Trang ${Math.min(userAuditPage + 1, totalPages)} / ${totalPages}`;
  }
  if (userAuditPrev) {
    userAuditPrev.disabled = userAuditPage <= 0;
  }
  if (userAuditNext) {
    userAuditNext.disabled = userAuditPage + 1 >= totalPages;
  }
};

const loadAdminAudit = async () => {
  const params = new URLSearchParams();
  if (auditFilter.value) params.append('targetUsername', auditFilter.value);
  const data = await request(`/api/users/audit?${params.toString()}`);
  adminAudit.innerHTML = '';
  data.forEach((entry) => {
    const item = document.createElement('div');
    item.className = 'list-item';
    const when = entry.createdAt ? new Date(entry.createdAt).toLocaleString('vi-VN') : 'không rõ thời gian';
    item.textContent = `${when} • ${formatAuditAction(entry.action)} bởi ${entry.actorUsername} (${formatRole(entry.actorRole)}) → ${entry.targetUsername}`;
    adminAudit.appendChild(item);
  });
};

const loadProfile = async () => {
  const profile = await request('/api/users/me');
  profileDetails.innerHTML = '';
  const item = document.createElement('div');
  item.className = 'list-item';
  const displayName = profile.displayName || profile.username;
  const nameLine = displayName ? `${displayName} (${profile.role})` : `${profile.username} (${profile.role})`;
  item.textContent = nameLine;
  profileDetails.appendChild(item);
  if (profile.email) {
    const emailItem = document.createElement('div');
    emailItem.className = 'list-item';
    emailItem.textContent = profile.email;
    profileDetails.appendChild(emailItem);
  }
  if (profile.title) {
    const titleItem = document.createElement('div');
    titleItem.className = 'list-item';
    titleItem.textContent = profile.title;
    profileDetails.appendChild(titleItem);
  }
  if (profileDisplayName) {
    profileDisplayName.value = profile.displayName || '';
  }
  if (profileTitle) {
    profileTitle.value = profile.title || '';
  }
  if (profileEmail) {
    profileEmail.value = profile.email || '';
  }
  if (profileAvatarPreview) {
    profileAvatarPreview.src = profile.avatarUrl || '';
    profileAvatarPreview.classList.toggle('hidden', !profile.avatarUrl);
  }
  if (profileAvatarFallback) {
    const initialSource = displayName || profile.username || 'U';
    profileAvatarFallback.textContent = initialSource.trim().charAt(0).toUpperCase();
    profileAvatarFallback.classList.toggle('hidden', !!profile.avatarUrl);
  }
  if (sidebarUsername) {
    sidebarUsername.textContent = formatName(displayName || profile.username);
  }
  if (logoMarkImg) {
    logoMarkImg.src = profile.avatarUrl || '';
    logoMarkImg.classList.toggle('hidden', !profile.avatarUrl);
  }
  if (logoMarkFallback) {
    const initialSource = displayName || profile.username || 'T';
    logoMarkFallback.textContent = initialSource.trim().charAt(0).toUpperCase();
    logoMarkFallback.classList.toggle('hidden', !!profile.avatarUrl);
  }
  if (profileAvatar) {
    profileAvatar.classList.remove('hidden');
  }
  if (profileCard) {
    profileCard.classList.remove('no-avatar');
  }
};

loginForm.addEventListener('submit', async (event) => {
  event.preventDefault();
  if (!validateForm(loginForm)) {
    return;
  }
  const formData = new FormData(loginForm);
  if (loginError) {
    loginError.classList.add('hidden');
    loginError.textContent = '';
  }
  try {
    await login({
      username: formData.get('username'),
      password: formData.get('password'),
    });
    routeGuard();
    await loadEngineerOptions();
    await loadUserAvatarMap();
    await loadTickets({ reset: true });
    await loadDashboard();
    await loadQueue();
    if (canManageUsers()) {
      await loadAdminUsers();
    }
    await loadProfile();
  } catch (error) {
    if (error.message.includes('403')) {
      if (loginError) {
        loginError.textContent = 'Tên đăng nhập hoặc mật khẩu không hợp lệ.';
        loginError.classList.remove('hidden');
      }
    } else {
      if (loginError) {
        loginError.textContent = error.message;
        loginError.classList.remove('hidden');
      } else {
        alert(error.message);
      }
    }
  }
});

logoutBtn.addEventListener('click', () => {
  setToken(null);
  showView('login');
  if (loginError) {
    loginError.classList.add('hidden');
    loginError.textContent = '';
  }
});

createForm.addEventListener('submit', async (event) => {
  event.preventDefault();
  if (!validateForm(createForm)) {
    return;
  }
  const formData = new FormData(createForm);
  const payload = Object.fromEntries(formData.entries());
  payload.assigneeName = normalizeAssigneeInput(payload.assigneeName);
  try {
    await request('/api/tickets', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
    createForm.reset();
    await loadTickets({ reset: true });
    if (createModal) {
      createModal.classList.add('hidden');
    }
  } catch (error) {
    alert(error.message);
  }
});

refreshBtn.addEventListener('click', () => {
  loadTickets({ reset: true }).catch((error) => alert(error.message));
  loadDashboard().catch((error) => alert(error.message));
  loadQueue().catch((error) => alert(error.message));
});

if (clearFiltersBtn) {
  clearFiltersBtn.addEventListener('click', () => {
    ticketPage = 0;
    if (filterAssignee) filterAssignee.value = '';
    if (filterStatus) filterStatus.value = '';
    if (filterSort) filterSort.value = 'createdAt,desc';
    if (filterSearch) filterSearch.value = '';
    saveTicketFilters();
    loadTickets({ reset: true }).catch((error) => alert(error.message));
  });
}

const stopAutoRefresh = () => {
  if (autoRefreshTimer) {
    clearInterval(autoRefreshTimer);
    autoRefreshTimer = null;
  }
};

const startAutoRefresh = () => {
  stopAutoRefresh();
  autoRefreshTimer = setInterval(() => {
    if (window.location.hash.includes('tickets')) {
      loadTickets({ reset: true }).catch(() => {});
    }
  }, autoRefreshMs);
};

const initAutoRefresh = () => {
  if (!autoRefreshToggle) return;
  const saved = localStorage.getItem(autoRefreshKey);
  const enabled = saved === 'true';
  autoRefreshToggle.checked = enabled;
  if (enabled) {
    startAutoRefresh();
  }
  autoRefreshToggle.addEventListener('change', () => {
    const isOn = autoRefreshToggle.checked;
    localStorage.setItem(autoRefreshKey, isOn ? 'true' : 'false');
    if (isOn) {
      startAutoRefresh();
    } else {
      stopAutoRefresh();
    }
  });
};

const initTicketInfiniteScroll = () => {
  if (!ticketsSentinel) return;
  const observer = new IntersectionObserver((entries) => {
    entries.forEach((entry) => {
      if (entry.isIntersecting) {
        loadTickets().catch(() => {});
      }
    });
  }, { root: document.querySelector('.app-main'), threshold: 0.1 });
  observer.observe(ticketsSentinel);
};

if (filterAssignee) {
  filterAssignee.addEventListener('change', () => {
    ticketPage = 0;
    saveTicketFilters();
    loadTickets({ reset: true }).catch((error) => alert(error.message));
  });
}

if (filterStatus) {
  filterStatus.addEventListener('change', () => {
    ticketPage = 0;
    saveTicketFilters();
    loadTickets({ reset: true }).catch((error) => alert(error.message));
  });
}

if (filterSort) {
  filterSort.addEventListener('change', () => {
    ticketPage = 0;
    saveTicketFilters();
    loadTickets({ reset: true }).catch((error) => alert(error.message));
  });
}

if (filterSearch) {
  let searchTimer = null;
  filterSearch.addEventListener('input', () => {
    if (searchTimer) {
      clearTimeout(searchTimer);
    }
    searchTimer = setTimeout(() => {
      ticketPage = 0;
      saveTicketFilters();
      loadTickets({ reset: true }).catch((error) => alert(error.message));
    }, 300);
  });
}

if (openCreateModal) {
  openCreateModal.addEventListener('click', () => {
    createModal.classList.remove('hidden');
  });
}

if (closeCreateModal) {
  closeCreateModal.addEventListener('click', () => {
    createModal.classList.add('hidden');
  });
}

if (closeDetails) {
  closeDetails.addEventListener('click', () => {
    ticketDetailsPanel.classList.add('hidden');
    if (ticketBoard) {
      ticketBoard.classList.remove('has-detail');
    }
  });
}

ticketTabButtons.forEach((btn) => {
  btn.addEventListener('click', () => {
    const target = btn.dataset.tab;
    ticketTabButtons.forEach((item) => item.classList.toggle('active', item === btn));
    ticketTabPanels.forEach((panel) => {
      panel.classList.toggle('active', panel.dataset.tabPanel === target);
    });
  });
});

updateStatusBtn.addEventListener('click', () => updateStatus().catch((err) => alert(err.message)));
updatePriorityBtn.addEventListener('click', () => updatePriority().catch((err) => alert(err.message)));
updateAssigneeBtn.addEventListener('click', () => updateAssignee().catch((err) => alert(err.message)));
if (assignMeBtn) {
  assignMeBtn.addEventListener('click', () => assignToMe().catch((err) => alert(err.message)));
}
addCommentBtn.addEventListener('click', () => addComment().catch((err) => alert(err.message)));
if (auditDownloadBtn) {
  auditDownloadBtn.addEventListener('click', () => downloadAuditCsv().catch((err) => alert(err.message)));
}

if (statusSelect) {
  statusSelect.addEventListener('change', updateActionButtons);
}
if (prioritySelect) {
  prioritySelect.addEventListener('change', updateActionButtons);
}
if (assigneeInput) {
  assigneeInput.addEventListener('change', updateActionButtons);
}

if (engineerReportBtn) {
  engineerReportBtn.addEventListener('click', () => loadEngineerReport().catch((err) => alert(err.message)));
}
if (requesterReportBtn) {
  requesterReportBtn.addEventListener('click', () => loadRequesterReport().catch((err) => alert(err.message)));
}
if (backlogReportBtn) {
  backlogReportBtn.addEventListener('click', () => loadBacklogReport().catch((err) => alert(err.message)));
}
if (slaReportBtn) {
  slaReportBtn.addEventListener('click', () => loadSlaReport().catch((err) => alert(err.message)));
}
if (reportDownloadBtn) {
  reportDownloadBtn.addEventListener('click', downloadReportCsv);
}

if (userSearchBtn) {
  userSearchBtn.addEventListener('click', () => {
    userPage = 0;
    loadAdminUsers().catch((err) => alert(err.message));
  });
}

if (userSearch) {
  userSearch.addEventListener('keydown', (event) => {
    if (event.key === 'Enter') {
      event.preventDefault();
      userPage = 0;
      loadAdminUsers().catch((err) => alert(err.message));
    }
  });
}

if (usersPrev) {
  usersPrev.addEventListener('click', () => {
    if (userPage > 0) {
      userPage -= 1;
      loadAdminUsers().catch((err) => alert(err.message));
    }
  });
}

if (usersNext) {
  usersNext.addEventListener('click', () => {
    userPage += 1;
    loadAdminUsers().catch((err) => alert(err.message));
  });
}

if (openUserModal) {
  openUserModal.addEventListener('click', () => {
    if (userCreateModal) {
      userCreateModal.classList.remove('hidden');
    }
  });
}

if (closeUserModal) {
  closeUserModal.addEventListener('click', () => {
    if (userCreateModal) {
      userCreateModal.classList.add('hidden');
    }
  });
}

if (closeUserDetail) {
  closeUserDetail.addEventListener('click', () => {
    if (userDetailModal) {
      userDetailModal.classList.add('hidden');
    }
    selectedUser = null;
    userAuditEntries = [];
    userAuditPage = 0;
  });
}

if (userAuditPrev) {
  userAuditPrev.addEventListener('click', () => {
    if (userAuditPage > 0) {
      userAuditPage -= 1;
      renderUserAuditPage();
    }
  });
}

if (userAuditNext) {
  userAuditNext.addEventListener('click', () => {
    const totalPages = Math.max(1, Math.ceil(userAuditEntries.length / userAuditPageSize));
    if (userAuditPage + 1 < totalPages) {
      userAuditPage += 1;
      renderUserAuditPage();
    }
  });
}

if (userSaveRole) {
  userSaveRole.addEventListener('click', async () => {
    if (!selectedUser) return;
    await request(`/api/users/${selectedUser.id}/role`, {
      method: 'PATCH',
      body: JSON.stringify({ role: userRoleSelect.value }),
    });
    await loadAdminUsers();
  });
}

if (userSaveEnabled) {
  userSaveEnabled.addEventListener('click', async () => {
    if (!selectedUser) return;
    await request(`/api/users/${selectedUser.id}/enabled`, {
      method: 'PATCH',
      body: JSON.stringify({ enabled: userEnabledSelect.value === 'true' }),
    });
    await loadAdminUsers();
  });
}

if (userSavePassword) {
  userSavePassword.addEventListener('click', async () => {
    if (!selectedUser) return;
    clearFieldError(userPasswordInput);
    if (!userPasswordInput.value || !userPasswordInput.value.trim()) {
      setFieldError(userPasswordInput, 'Mật khẩu là bắt buộc.');
      return;
    }
    if (!userPasswordInput.checkValidity()) {
      setFieldError(userPasswordInput, userPasswordInput.validationMessage || 'Mật khẩu không hợp lệ.');
      return;
    }
    await request(`/api/users/${selectedUser.id}/password`, {
      method: 'PATCH',
      body: JSON.stringify({ password: userPasswordInput.value }),
    });
    userPasswordInput.value = '';
  });
}

if (userSaveProfile) {
  userSaveProfile.addEventListener('click', async () => {
    if (!selectedUser) return;
    clearFieldError(userPasswordInput);
    if (!validateProfileFields([
      { label: 'Tên hiển thị', input: userDisplayName },
      { label: 'Chức danh', input: userTitle },
      { label: 'Email', input: userEmail },
    ])) {
      return;
    }
    if (userPasswordInput && userPasswordInput.value.trim()) {
      if (!userPasswordInput.checkValidity()) {
        setFieldError(userPasswordInput, userPasswordInput.validationMessage || 'Mật khẩu không hợp lệ.');
        return;
      }
    }
    const updates = [];
    if (userRoleSelect && !userRoleSelect.disabled && userRoleSelect.value !== selectedUser.role) {
      updates.push(
        request(`/api/users/${selectedUser.id}/role`, {
          method: 'PATCH',
          body: JSON.stringify({ role: userRoleSelect.value }),
        }).then((response) => {
          selectedUser = response;
        })
      );
    }
    if (userEnabledSelect && !userEnabledSelect.disabled) {
      const enabledValue = userEnabledSelect.value === 'true';
      if (enabledValue !== selectedUser.enabled) {
        updates.push(
          request(`/api/users/${selectedUser.id}/enabled`, {
            method: 'PATCH',
            body: JSON.stringify({ enabled: enabledValue }),
          }).then((response) => {
            selectedUser = response;
          })
        );
      }
    }
    if (userPasswordInput && userPasswordInput.value.trim()) {
      updates.push(
        request(`/api/users/${selectedUser.id}/password`, {
          method: 'PATCH',
          body: JSON.stringify({ password: userPasswordInput.value }),
        }).then((response) => {
          selectedUser = response;
        })
      );
    }
    const profilePayload = {
      displayName: userDisplayName.value,
      title: userTitle.value,
      email: userEmail.value,
    };
    if (
      profilePayload.displayName !== selectedUser.displayName ||
      profilePayload.title !== selectedUser.title ||
      profilePayload.email !== selectedUser.email
    ) {
      updates.push(
        request(`/api/users/${selectedUser.id}/profile`, {
          method: 'PATCH',
          body: JSON.stringify(profilePayload),
        }).then((response) => {
          selectedUser = response;
        })
      );
    }
    if (!updates.length) {
      return;
    }
    await Promise.all(updates);
    if (userPasswordInput) {
      userPasswordInput.value = '';
    }
    applyUserDetailControls();
    if (userDetailHeader && selectedUser) {
      userDetailHeader.textContent = `${formatName(selectedUser.username)} (${formatRole(selectedUser.role)})`;
    }
    await loadAdminUsers();
    await loadUserAvatarMap();
  });
}

if (userDelete) {
  userDelete.addEventListener('click', async () => {
    if (!selectedUser) return;
    if (!confirm(`Bạn có chắc muốn xóa người dùng ${selectedUser.username}?`)) return;
    await request(`/api/users/${selectedUser.id}`, { method: 'DELETE' });
    selectedUser = null;
    if (userDetailModal) {
      userDetailModal.classList.add('hidden');
    }
    await loadAdminUsers();
  });
}

adminCreateForm.addEventListener('submit', async (event) => {
  event.preventDefault();
  if (!validateForm(adminCreateForm)) {
    return;
  }
  const formData = new FormData(adminCreateForm);
  const payload = Object.fromEntries(formData.entries());
  if (!hasRole('ROLE_ADMIN')) {
    payload.role = 'REQUESTER';
  }
  try {
    await request('/api/users', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
    adminCreateForm.reset();
    if (userCreateModal) {
      userCreateModal.classList.add('hidden');
    }
    await loadAdminUsers();
    await loadUserAvatarMap();
    await loadEngineerOptions();
  } catch (error) {
    alert(error.message);
  }
});

profilePasswordForm.addEventListener('submit', async (event) => {
  event.preventDefault();
  if (!validateForm(profilePasswordForm)) {
    return;
  }
  const formData = new FormData(profilePasswordForm);
  const payload = Object.fromEntries(formData.entries());
  try {
    await request('/api/users/me/password', {
      method: 'PATCH',
      body: JSON.stringify(payload),
    });
    profilePasswordForm.reset();
  } catch (error) {
    alert(error.message);
  }
});

if (profileSaveBtn) {
  profileSaveBtn.addEventListener('click', async () => {
    if (!validateProfileFields([
      { label: 'Tên hiển thị', input: profileDisplayName },
      { label: 'Chức danh', input: profileTitle },
      { label: 'Email', input: profileEmail },
    ])) {
      return;
    }
    await request('/api/users/me/profile', {
      method: 'PATCH',
      body: JSON.stringify({
        displayName: profileDisplayName.value,
        title: profileTitle.value,
        email: profileEmail.value,
      }),
    });
    await loadProfile();
  });
}

attachValidationHandlers(loginForm);
attachValidationHandlers(createForm);
attachValidationHandlers(adminCreateForm);
attachValidationHandlers(profilePasswordForm);
attachValidationHandlers(userDetailModal);
attachValidationHandlers(commentPanel);
attachValidationHandlers(document.getElementById('profile-panel'));

window.addEventListener('hashchange', () => {
  routeGuard();
});

if (navToggle && navCard) {
  navToggle.addEventListener('click', () => {
    navCard.classList.toggle('open');
    const expanded = navCard.classList.contains('open');
    navToggle.setAttribute('aria-expanded', expanded ? 'true' : 'false');
  });
  navLinks.forEach((link) => {
    link.addEventListener('click', () => {
      if (window.innerWidth <= 1024) {
        navCard.classList.remove('open');
        navToggle.setAttribute('aria-expanded', 'false');
      }
    });
  });
}

updateTokenStatus();
updateNavVisibility();
routeGuard();
initReportDates();
initAutoRefresh();
loadTicketFilters();
initTicketInfiniteScroll();
if (isTokenValid()) {
  loadEngineerOptions().catch(() => {});
  loadUserAvatarMap()
    .then(() => loadTickets({ reset: true }))
    .catch(() => {});
  loadDashboard().catch(() => {});
  loadQueue().catch(() => {});
  if (canManageUsers()) {
    loadAdminUsers().catch(() => {});
  }
  loadProfile().catch(() => {});
}

const themeKey = 'ticketing.theme';
const applyTheme = (theme) => {
  document.body.classList.toggle('dark', theme === 'dark');
  if (themeToggle) {
    const label = theme === 'dark' ? 'Chế độ sáng' : 'Chế độ tối';
    const labelNode = themeToggle.querySelector('.theme-label');
    if (labelNode) {
      labelNode.textContent = label;
    } else {
      themeToggle.textContent = label;
    }
    themeToggle.setAttribute('aria-pressed', theme === 'dark' ? 'true' : 'false');
  }
};

const savedTheme = localStorage.getItem(themeKey);
applyTheme(savedTheme || 'light');

if (themeToggle) {
  themeToggle.addEventListener('click', () => {
    const next = document.body.classList.contains('dark') ? 'light' : 'dark';
    localStorage.setItem(themeKey, next);
    applyTheme(next);
  });
}
