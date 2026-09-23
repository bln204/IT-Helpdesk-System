const API_BASE = '';
const tokenKey = 'ticketing.jwt';
const UNASSIGNED_VALUE = 'UNASSIGNED';

// Current user info (from login response)
let currentUser = null;
let notifications = [];
let pendingUsersCache = []; // Cache for pending users count
let notificationPollingTimer = null;
const NOTIFICATION_POLLING_MS = 10000; // Poll every 10 seconds

// ==================== Notification System ====================

// Fetch notifications from backend API
const fetchNotifications = async () => {
  try {
    const token = localStorage.getItem(tokenKey);
    if (!token) return { notifications: [], unreadCount: 0 };

    const response = await fetch(`${API_BASE}/api/notifications`, {
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
      }
    });
    
    if (!response.ok) {
      console.error('Failed to fetch notifications:', response.status);
      return { notifications: [], unreadCount: 0 };
    }
    
    const data = await response.json();
    return { 
      notifications: data || [], 
      unreadCount: data.filter(n => !n.read).length 
    };
  } catch (error) {
    console.error('Error fetching notifications:', error);
    return { notifications: [], unreadCount: 0 };
  }
};

// Fetch unread count from backend
const fetchUnreadCount = async () => {
  try {
    const token = localStorage.getItem(tokenKey);
    if (!token) return 0;

    const response = await fetch(`${API_BASE}/api/notifications/unread-count`, {
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
      }
    });
    
    if (!response.ok) return 0;
    
    const data = await response.json();
    return data.count || 0;
  } catch (error) {
    console.error('Error fetching unread count:', error);
    return 0;
  }
};

// Mark notification as read via API
const markNotificationReadAPI = async (id) => {
  try {
    const token = localStorage.getItem(tokenKey);
    if (!token) return;

    await fetch(`${API_BASE}/api/notifications/${id}/read`, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
      }
    });
  } catch (error) {
    console.error('Error marking notification as read:', error);
  }
};

// Mark all notifications as read via API
const markAllNotificationsReadAPI = async () => {
  try {
    const token = localStorage.getItem(tokenKey);
    if (!token) return;

    await fetch(`${API_BASE}/api/notifications/mark-all-read`, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
      }
    });
  } catch (error) {
    console.error('Error marking all notifications as read:', error);
  }
};

// Delete all notifications via API
const deleteAllNotificationsAPI = async () => {
  try {
    const token = localStorage.getItem(tokenKey);
    if (!token) return;

    await fetch(`${API_BASE}/api/notifications`, {
      method: 'DELETE',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
      }
    });
  } catch (error) {
    console.error('Error deleting all notifications:', error);
  }
};

// Start notification polling
const startNotificationPolling = () => {
  stopNotificationPolling();
  notificationPollingTimer = setInterval(async () => {
    await loadNotifications();
  }, NOTIFICATION_POLLING_MS);
};

// Stop notification polling
const stopNotificationPolling = () => {
  if (notificationPollingTimer) {
    clearInterval(notificationPollingTimer);
    notificationPollingTimer = null;
  }
};

// Convert backend notification to frontend format
const convertBackendNotification = (notif) => {
  // Map notification type to icon and class
  const typeMap = {
    'ACCOUNT_APPROVED': { type: 'approve', icon: '✓' },
    'ACCOUNT_REJECTED': { type: 'reject', icon: '✗' },
    'ACCOUNT_CREATED_PENDING': { type: 'pending', icon: '⏳' },
    'INFO': { type: 'info', icon: 'ℹ' },
    'TICKET_CREATED': { type: 'info', icon: '📋' },
    'TICKET_UPDATED': { type: 'info', icon: '📝' },
    'TICKET_ASSIGNED': { type: 'info', icon: '👤' },
    'TICKET_CLOSED': { type: 'info', icon: '✅' },
  };
  
  const mapping = typeMap[notif.type] || { type: 'info', icon: 'ℹ' };
  
  return {
    id: notif.id,
    type: mapping.type,
    icon: mapping.icon,
    title: notif.title || notif.message,
    message: notif.message || notif.title,
    userId: notif.relatedUserId,
    timestamp: notif.createdAt,
    read: notif.read || false,
  };
};

const addNotification = async (type, title, message, userId = null) => {
  const notification = {
    id: Date.now(),
    type, // 'approve' or 'reject'
    title,
    message,
    userId,
    timestamp: new Date().toISOString(),
    read: false,
  };

  notifications.unshift(notification);
  const unreadCount = notifications.filter(n => !n.read).length;
  updateNotificationBadge(unreadCount);
  renderNotifications();

  // Save to localStorage
  localStorage.setItem('ticketing.notifications', JSON.stringify(notifications.slice(0, 50)));

  // Also save to backend via API
  try {
    await request('/api/notifications', {
      method: 'POST',
      body: JSON.stringify({ type, title, message, userId })
    });
  } catch (error) {
    console.error('Failed to save notification to backend:', error);
  }
};

const loadNotifications = async () => {
  // If not logged in, clear notifications
  const token = localStorage.getItem(tokenKey);
  if (!token) {
    notifications = [];
    updateNotificationBadge(0);
    return;
  }

  try {
    const { notifications: fetchedNotifications, unreadCount } = await fetchNotifications();
    
    // Convert backend notifications to frontend format
    notifications = fetchedNotifications.map(convertBackendNotification);
    
    updateNotificationBadge(unreadCount);
    renderNotifications();
  } catch (error) {
    console.error('Error loading notifications:', error);
    // Fallback to localStorage if API fails
    const stored = localStorage.getItem('ticketing.notifications');
    if (stored) {
      try {
        notifications = JSON.parse(stored);
        const unread = notifications.filter(n => !n.read).length;
        updateNotificationBadge(unread);
        renderNotifications();
      } catch (e) {
        notifications = [];
      }
    }
  }
};

const updateNotificationBadge = (count) => {
  const badge = document.getElementById('notification-badge');
  if (!badge) return;
  if (count > 0) {
    badge.textContent = count > 99 ? '99+' : count;
    badge.style.display = 'flex';
  } else {
    badge.style.display = 'none';
  }
};

const renderNotifications = () => {
  if (!notificationList) return;

  if (notifications.length === 0) {
    notificationList.innerHTML = '<div class="notification-empty">Không có thông báo</div>';
    return;
  }

  notificationList.innerHTML = '';
  notifications.forEach((notif) => {
    const item = document.createElement('div');
    item.className = `notification-item${notif.read ? '' : ' unread'}`;

    const timeAgo = getTimeAgo(notif.timestamp);
    const icon = notif.icon || (notif.type === 'approve' ? '✓' : notif.type === 'reject' ? '✗' : 'ℹ');

    item.innerHTML = `
      <div class="notification-item-content">
        <span class="notification-icon">${icon}</span>
        <div class="notification-text">
          <div class="notification-item-title">${notif.title}</div>
          <div class="notification-item-message">${notif.message}</div>
        </div>
      </div>
      <div class="notification-item-footer">
        <span class="notification-item-time">${timeAgo}</span>
        ${!notif.read ? '<span class="notification-unread-dot"></span>' : ''}
      </div>
    `;

    item.addEventListener('click', () => {
      markNotificationRead(notif.id);
      setRoute('#/admin');
      closeNotificationDropdown();
    });

    notificationList.appendChild(item);
  });
};

const markNotificationRead = async (id) => {
  const notif = notifications.find(n => n.id === id);
  if (notif && !notif.read) {
    notif.read = true;
    const unreadCount = notifications.filter(n => !n.read).length;
    updateNotificationBadge(unreadCount);
    renderNotifications();
    // Call API to mark as read on backend
    await markNotificationReadAPI(id);
    // Also save to localStorage as fallback
    localStorage.setItem('ticketing.notifications', JSON.stringify(notifications.slice(0, 50)));
  }
};

const markAllNotificationsRead = async () => {
  notifications.forEach(n => n.read = true);
  updateNotificationBadge(0);
  renderNotifications();
  // Call API to mark all as read on backend
  await markAllNotificationsReadAPI();
  // Also save to localStorage as fallback
  localStorage.setItem('ticketing.notifications', JSON.stringify(notifications.slice(0, 50)));
};

const clearAllNotifications = async () => {
  // Call API to delete all notifications on backend
  await deleteAllNotificationsAPI();
  // Clear local state
  notifications = [];
  updateNotificationBadge(0);
  renderNotifications();
  // Also clear localStorage
  localStorage.setItem('ticketing.notifications', JSON.stringify(notifications));
};

const getTimeAgo = (isoString) => {
  const date = new Date(isoString);
  const now = new Date();
  const diffMs = now - date;
  const diffMins = Math.floor(diffMs / 60000);
  const diffHours = Math.floor(diffMs / 3600000);
  const diffDays = Math.floor(diffMs / 86400000);

  if (diffMins < 1) return 'Vừa xong';
  if (diffMins < 60) return `${diffMins} phút trước`;
  if (diffHours < 24) return `${diffHours} giờ trước`;
  if (diffDays < 7) return `${diffDays} ngày trước`;
  return date.toLocaleDateString('vi-VN');
};

const toggleNotificationDropdown = () => {
  if (notificationDropdown) {
    notificationDropdown.classList.toggle('hidden');
    if (!notificationDropdown.classList.contains('hidden')) {
      // Mark all as read when opening dropdown
      markAllNotificationsRead();
    }
  }
};

const closeNotificationDropdown = () => {
  if (notificationDropdown) {
    notificationDropdown.classList.add('hidden');
  }
};

const loginForm = document.getElementById('login-form');
const loginError = document.getElementById('login-error');
const notificationBtn = document.getElementById('notification-btn');
const notificationBadge = document.getElementById('notification-badge');
const notificationDropdown = document.getElementById('notification-dropdown');
const notificationList = document.getElementById('notification-list');
const clearNotificationsBtn = document.getElementById('clear-notifications');
const loginPrefillNote = document.getElementById('login-prefill-note');
const logoutBtn = document.getElementById('logout-btn');
const tokenStatus = document.getElementById('token-status');
const sidebarUsername = document.getElementById('sidebar-username');
const sidebarRole = document.getElementById('sidebar-role');
const sidebarDept = document.getElementById('sidebar-dept');
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
const attachmentsList = document.getElementById('ticket-attachments-list');
const attachmentInput = document.getElementById('attachment-input');
const selectedFilesList = document.getElementById('selected-files');
const uploadAttachmentBtn = document.getElementById('upload-attachment-btn');
const ticketAttachmentInput = document.getElementById('ticket-attachment-input');
const attachmentUploadSection = document.getElementById('attachment-upload-section');
const ticketFileUploadArea = document.getElementById('ticket-file-upload-area');
const ticketSelectedFiles = document.getElementById('ticket-selected-files');

// Track selected files for create form
let selectedFiles = [];
// Track selected files for ticket detail
let ticketSelectedFilesList = [];

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
const adminDeptSelect = document.getElementById('admin-dept-select');
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
const userDeptSelect = document.getElementById('user-dept-select');
const userEnabledSelect = document.getElementById('user-enabled-select');
const userPasswordInput = document.getElementById('user-password-input');
const userSaveRole = document.getElementById('user-save-role');
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
const rejectModal = document.getElementById('reject-modal');
const rejectModalClose = document.getElementById('reject-modal-close');
const rejectModalConfirm = document.getElementById('reject-modal-confirm');
const rejectModalCancel = document.getElementById('reject-modal-cancel');
const rejectReasonInput = document.getElementById('reject-reason-input');

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

// Department Management Elements
const departmentsSection = document.getElementById('departments-section');
const departmentsTbody = document.getElementById('departments-tbody');
const openDeptModal = document.getElementById('open-dept-modal');
const deptModal = document.getElementById('dept-modal');
const closeDeptModal = document.getElementById('close-dept-modal');
const deptForm = document.getElementById('dept-form');
const deptModalTitle = document.getElementById('dept-modal-title');
const deptModalDesc = document.getElementById('dept-modal-desc');
const deptId = document.getElementById('dept-id');
const deptCode = document.getElementById('dept-code');
const deptName = document.getElementById('dept-name');
const deptDescription = document.getElementById('dept-description');
const deptEnabled = document.getElementById('dept-enabled');
const deptManager = document.getElementById('dept-manager');
const deptSubmitBtn = document.getElementById('dept-submit-btn');
const deptCancelBtn = document.getElementById('dept-cancel-btn');
const deptDeleteModal = document.getElementById('dept-delete-modal');
const closeDeptDeleteModal = document.getElementById('close-dept-delete-modal');
const deptDeleteWarning = document.getElementById('dept-delete-warning');
const deptDeleteAffected = document.getElementById('dept-delete-affected');
const deptConfirmDelete = document.getElementById('dept-confirm-delete');
const deptCancelDelete = document.getElementById('dept-cancel-delete');

let selectedTicket = null;
let userPage = 0;
const userPageSize = 10;
let selectedUser = null;
let userAuditPage = 0;
const userAuditPageSize = 5;
let userAuditEntries = [];
let selectedDepartment = null;
let selectedDepartmentForDelete = null;

// Tab state
let currentAdminTab = 'users';

// Tab click handler
const initTabs = () => {
  document.querySelectorAll('.tab-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      const tab = btn.dataset.tab;
      if (!tab) return;
      
      // Check role restrictions for departments tab
      if (tab === 'departments' && !isAdminOrGiamDoc()) {
        return;
      }
      
      // Update active tab
      document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      
      // Show/hide content
      document.querySelectorAll('.tab-content').forEach(c => c.classList.add('hidden'));
      const tabContent = document.getElementById(`tab-${tab}`);
      if (tabContent) tabContent.classList.remove('hidden');
      
      currentAdminTab = tab;
      
      // Load data for tab
      if (tab === 'departments') {
        loadDepartmentsTable();
      }
    });
  });
};

// Role check helper
const isAdminOrGiamDoc = () => {
  return currentUser && (currentUser.role === 'ADMIN' || currentUser.role === 'GIAM_DOC');
};

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

// Demo credentials - different for new role system
const DEMO_CREDENTIALS = {
  admin: { username: 'admin', password: '12345678' },
  giamDoc: { username: 'mayagiamdoc', password: '12345678' },
  truongPhongIT: { username: 'tp.it', password: '12345678' },
  nhanVien: { username: 'tech.smith', password: '12345678' }
};

const setApiStatus = (text) => {
  let viText = text;
  if (text === 'idle') viText = 'chờ';
  else if (text === 'working') viText = 'đang xử lý';
  else if (text === 'ok') viText = 'sẵn sàng';
  else if (typeof text === 'string' && text.startsWith('error')) viText = text.replace('error', 'lỗi');
  apiStatus.textContent = `Trạng thái API: ${viText}`;
};

const shouldPrefillLogin = () => window.location.protocol !== 'file:';

const applyLoginPrefill = () => {
  if (!loginForm) return;
  const usernameInput = loginForm.elements.namedItem('username');
  const passwordInput = loginForm.elements.namedItem('password');
  const shouldPrefill = shouldPrefillLogin();
  if (loginPrefillNote) {
    loginPrefillNote.classList.toggle('hidden', !shouldPrefill);
  }
  if (!shouldPrefill || !usernameInput || !passwordInput) return;
  
  // Default to nhanvien account
  usernameInput.value = DEMO_CREDENTIALS.nhanVien.username;
  passwordInput.value = DEMO_CREDENTIALS.nhanVien.password;
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

const isAdmin = () => hasRole('ROLE_ADMIN');
const isGiamDoc = () => hasRole('ROLE_GIAM_DOC');
const isTruongPhong = () => hasRole('ROLE_TRUONG_PHONG');
const isNhanVien = () => hasRole('ROLE_NHAN_VIEN');
const isTruongPhongIT = () => hasRole('ROLE_TRUONG_PHONG') && currentUser?.departmentCode === 'IT';
const isInITDepartment = () => currentUser?.departmentCode === 'IT';
// IT Staff: NHAN_VIEN in IT department - can process tickets but not full admin
const isITStaff = () => isNhanVien() && currentUser?.departmentCode === 'IT';

const getCurrentUsername = () => {
  return currentUser?.username || null;
};

const getCurrentUserDepartment = () => {
  return currentUser?.departmentCode || null;
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
  
  const username = currentUser?.username || 'Phiếu hỗ trợ';
  const roles = getRoles();
  const dept = currentUser?.departmentName || null;
  
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

// Role labels - updated for new role system
const ROLE_LABELS = {
  ADMIN: 'Quản trị viên',
  GIAM_DOC: 'Giám đốc',
  TRUONG_PHONG: 'Trưởng phòng',
  NHAN_VIEN: 'Nhân viên',
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

// Can manage users: ADMIN, GIAM_DOC, TRUONG_PHONG (not IT Staff)
const canManageUsers = () => hasRole('ROLE_ADMIN') || hasRole('ROLE_GIAM_DOC') || hasRole('ROLE_TRUONG_PHONG');

// Can process tickets: ADMIN, GIAM_DOC, TRUONG_PHONG, IT Staff
const canProcessTickets = () => hasRole('ROLE_ADMIN') || hasRole('ROLE_GIAM_DOC') || hasRole('ROLE_TRUONG_PHONG') || isITStaff();

// Can assign tickets: ADMIN, GIAM_DOC, TRUONG_PHONG (chỉ IT)
const canAssignTickets = () => hasRole('ROLE_ADMIN') || hasRole('ROLE_GIAM_DOC') || isTruongPhongIT();

// Can view internal comments: ADMIN, GIAM_DOC, TRUONG_PHONG, IT Staff
const canViewInternalComments = () => !isNhanVien() || isITStaff();

// Can add internal comments: IT staff, ADMIN, GIAM_DOC
const canAddInternalComments = () => isInITDepartment() || isAdmin() || isGiamDoc();

// Staff check (not just NHAN_VIEN, but IT Staff is considered staff)
const isStaff = () => !isNhanVien() || isITStaff();

const applyAdminRoleOptions = () => {
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

const loadDepartments = async () => {
  try {
    const depts = await request('/api/departments');
    populateDepartmentSelect(adminDeptSelect, depts);
    return depts;
  } catch (error) {
    return [];
  }
};

const populateDepartmentSelect = (select, departments) => {
  if (!select) return;
  select.innerHTML = '';
  
  const option = document.createElement('option');
  option.value = '';
  option.textContent = '— Chọn phòng ban —';
  select.appendChild(option);
  
  departments.forEach((dept) => {
    const option = document.createElement('option');
    option.value = dept.id;
    option.textContent = `${dept.code} - ${dept.name}`;
    option.dataset.code = dept.code;
    select.appendChild(option);
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
        departmentCode: user.departmentCode,
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

// Check if current user can manage target user
const canManageTargetUser = (user) => {
  if (!user) return false;
  if (isAdmin()) return true;
  if (isGiamDoc()) return user.role !== 'ADMIN';
  if (isTruongPhong()) {
    // TRUONG_PHONG can only manage NHAN_VIEN in their department
    if (user.role !== 'NHAN_VIEN') return false;
    return user.departmentId === currentUser?.departmentId;
  }
  return false;
};

const applyUserDetailControls = () => {
  if (!selectedUser) return;
  const canManage = canManageTargetUser(selectedUser);
  const isPending = !selectedUser.approved && !selectedUser.rejectionReason;
  const isRejectedUser = !selectedUser.approved && selectedUser.rejectionReason;
  const isApprovedUser = selectedUser.approved;
  
  // Determine edit permissions based on role and status
  // IMPORTANT: Rejected users (approved=false, rejectionReason!=null) CANNOT be edited
  // - They can ONLY be deleted by ADMIN
  // - No one can edit their profile, change enabled status, or reset password
  let canEdit = false;
  let canResetPassword = false;
  let canDelete = false;
  
  if (isRejectedUser) {
    // Rejected accounts: only ADMIN can delete, NO ONE can edit
    if (isAdmin()) {
      // Admin can only DELETE rejected users, cannot edit
      canDelete = selectedUser.role !== 'ADMIN';
    }
    // No canEdit or canResetPassword for rejected users
  } else if (isAdmin()) {
    // Admin: can delete all users (except other admins)
    // Admin cannot edit user details from detail modal (only from pending table)
    if (selectedUser.role !== 'ADMIN') {
      canDelete = true;
    }
    // Admin can edit all users
    canEdit = true;
    // Admin can reset password for all users (except self)
    if (selectedUser.username !== currentUser?.username) {
      canResetPassword = true;
    }
  } else if (isGiamDoc()) {
    // GIAM_DOC: can edit and reset password for all users (except ADMIN)
    if (selectedUser.role !== 'ADMIN') {
      canEdit = true;
      canResetPassword = true;
      canDelete = true;
    }
  } else if (isTruongPhong()) {
    // TruongPhong: can edit only approved users in their department (except ADMIN/GIAM_DOC/other TRUONG_PHONG)
    if (isApprovedUser && canManage) {
      canEdit = true;
    }
    // TruongPhong can reset password for NHAN_VIEN in their department
    if (selectedUser.role === 'NHAN_VIEN' && canManage) {
      canResetPassword = true;
    }
    // TruongPhong can request delete for NHAN_VIEN in their department (any status)
    if (selectedUser.role === 'NHAN_VIEN' && canManage) {
      canDelete = true;
    }
  }
  
  // Helper to check if user is in TRUONG_PHONG's department
  const isUserInMyDept = selectedUser.departmentId === currentUser?.departmentId;
  
  if (userRoleSelect) {
    userRoleSelect.innerHTML = '';
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
      userRoleSelect.appendChild(option);
    });
    userRoleSelect.value = selectedUser.role;
    userRoleSelect.disabled = true; // Always disabled - cannot change role from detail modal
  }

  if (userDeptSelect) {
    userDeptSelect.disabled = true; // Always disabled - cannot change department from detail modal
  }

  if (userDisplayName) {
    userDisplayName.value = selectedUser.displayName || '';
    userDisplayName.disabled = !canEdit;
  }
  if (userTitle) {
    userTitle.value = selectedUser.title || '';
    userTitle.disabled = !canEdit;
  }
  if (userEmail) {
    userEmail.value = selectedUser.email || '';
    userEmail.disabled = !canEdit;
  }
  if (userEnabledSelect) {
    userEnabledSelect.checked = selectedUser.enabled;
    userEnabledSelect.disabled = !canEdit;
  }
  if (userPasswordInput) {
    userPasswordInput.disabled = !canResetPassword;
  }
  if (userSaveRole) {
    userSaveRole.disabled = true; // Always disabled
  }
  if (userSavePassword) {
    userSavePassword.disabled = !canResetPassword;
  }
  if (userSaveProfile) {
    userSaveProfile.disabled = !canEdit;
  }
  
  // Show/hide rejected user warning
  const userRejectedWarning = document.getElementById('user-rejected-warning');
  if (userRejectedWarning) {
    if (isRejectedUser) {
      userRejectedWarning.classList.remove('hidden');
    } else {
      userRejectedWarning.classList.add('hidden');
    }
  }
  
  if (userDelete) {
    userDelete.disabled = !canDelete;
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
  { includeAll = false, includeUnassigned = false } = {}
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
    // Display username (Tên đăng nhập) for consistency across all dropdowns
    option.value = username;
    option.textContent = formatName(username);
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
  // Load engineers for users who can process tickets (ADMIN, GIAM_DOC, TRUONG_PHONG, NHAN_VIEN in IT)
  // Check role from localStorage or token if currentUser not yet loaded
  const role = currentUser?.role || localStorage.getItem('ticketing.currentRole');
  const deptCode = currentUser?.departmentCode || localStorage.getItem('ticketing.currentDept');
  const isEligibleRole = role === 'ADMIN' || role === 'GIAM_DOC' || role === 'TRUONG_PHONG' || role === 'NHAN_VIEN';
  if (!isEligibleRole) {
    console.log('loadEngineerOptions: Not eligible role', role);
    return;
  }
  
  // For NHAN_VIEN, must be in IT department
  if (role === 'NHAN_VIEN' && deptCode !== 'IT') {
    console.log('loadEngineerOptions: NHAN_VIEN not in IT department', deptCode);
    return;
  }
  
  try {
    // Load IT staff - handle both array of usernames and array of user objects
    const response = await request('/api/users/engineers');
    console.log('loadEngineerOptions: Raw response', response);
    
    // API returns array of UserResponse objects, extract usernames
    engineerOptions = response.map(u => typeof u === 'string' ? u : u.username);
    console.log('loadEngineerOptions: Parsed engineers', engineerOptions);
    
    populateAssigneeSelect(filterAssignee, {
      includeAll: true,
      includeUnassigned: true,
    });
    populateAssigneeSelect(assigneeInput, { includeUnassigned: true });
    populateAssigneeSelect(assigneeCreate, { includeUnassigned: true });
  } catch (error) {
    console.error('loadEngineerOptions: Error loading engineers', error);
    engineerOptions = [];
  }
};

const applyRoleControls = () => {
  // IT Staff (NHAN_VIEN in IT) can process tickets like ADMIN
  const isITStaffUser = isITStaff();
  const isNhanVienUser = isNhanVien() && !isITStaffUser;
  
  if (statusSelect) {
    // IT Staff and above can update status; NHAN_VIEN can only close their own tickets
    statusSelect.disabled = !canProcessTickets() && isNhanVienUser;
  }
  if (prioritySelect) {
    // IT Staff can update priority
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
    // IT Staff can see internal comments
    if (!canViewInternalComments()) {
      commentVisibility.value = 'PUBLIC';
      commentVisibility.disabled = true;
    } else {
      commentVisibility.disabled = false;
    }
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
    updateAssigneeBtn.disabled = assigneeInput?.value === currentAssignee || !canAssignTickets();
  }
  if (assignMeBtn) {
    const currentUser = getCurrentUsername();
    assignMeBtn.disabled = !currentUser || selectedTicket.assigneeName === currentUser || !canAssignTickets();
  }
};

const authHeaders = () => {
  const token = getToken();
  return token ? { Authorization: `Bearer ${token}` } : {};
};

const request = async (path, options = {}) => {
  setApiStatus('working');
  console.log('[DEBUG] Request:', options.method || 'GET', path);
  console.log('[DEBUG] Token available:', !!getToken());
  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(options.headers || {}),
      ...authHeaders(),
    },
  });
  console.log('[DEBUG] Response status:', response.status);
  setApiStatus(response.ok ? 'ok' : `error ${response.status}`);
  if (!response.ok) {
    // Parse error response in our format
    let errorData = null;
    try {
      const contentType = response.headers.get('content-type');
      if (contentType && contentType.includes('application/json')) {
        errorData = await response.json();
      } else {
        const text = await response.text();
        errorData = { message: text };
      }
    } catch (e) {
      errorData = { message: await response.text() };
    }
    
    // Build error message from our format
    let errorMessage = errorData.message || `Yêu cầu thất bại: ${response.status}`;
    
    // Add suggestion if available
    if (errorData.details && errorData.details.suggestion) {
      errorMessage += '\n' + errorData.details.suggestion;
    }
    
    // Add rejection reason if available
    if (errorData.details && errorData.details.rejectionReason) {
      errorMessage += '\nLý do: ' + errorData.details.rejectionReason;
    }
    
    // Only logout on 401 (token invalid/expired), not on 403 (permission denied)
    // But still logout on our specific auth errors
    if (response.status === 401 && getToken()) {
      // Don't logout on approval-related errors, just throw the error
      const errorCode = errorData.errorCode;
      if (errorCode && (errorCode.startsWith('AUTH_USER') || errorCode === 'AUTH_INVALID_CREDENTIALS')) {
        // These are authentication errors - throw without logout
        const error = new Error(errorMessage);
        error.errorCode = errorCode;
        error.status = response.status;
        error.details = errorData.details;
        throw error;
      }
      // Token expired or invalid - logout
      setToken(null);
      currentUser = null;
      showView('login');
    }
    
    const error = new Error(errorMessage);
    error.errorCode = errorData.errorCode;
    error.status = response.status;
    error.details = errorData.details;
    throw error;
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
    // Update panel description based on role
    const adminDesc = document.getElementById('admin-panel-description');
    if (adminDesc) {
      if (isAdmin()) {
        adminDesc.textContent = 'Quản lý người dùng, duyệt hoặc từ chối tài khoản mới.';
      } else if (isTruongPhong()) {
        adminDesc.textContent = 'Quản lý nhân viên trong phòng và xem tài khoản chờ duyệt.';
      } else {
        adminDesc.textContent = 'Quản lý người dùng trong hệ thống.';
      }
    }
    loadAdminUsers().catch(() => {});
    loadDepartments().catch(() => {});
  }
  if (viewName === 'login') {
    applyLoginPrefill();
  }
};

const routeGuard = () => {
  const token = isTokenValid() ? getToken() : null;
  if (!token && getToken()) {
    setToken(null);
    currentUser = null;
  }
  
  // Try to restore current user from storage
  if (token && !currentUser) {
    const stored = localStorage.getItem('ticketing.currentUser');
    if (stored) {
      try {
        currentUser = JSON.parse(stored);
      } catch (e) {}
    }
  }
  
  const hash = window.location.hash || '#/login';
  const hashPath = hash.replace('#/', '');
  const viewName = hashPath.split('/')[0];
  const subRoute = hashPath.split('/')[1];
  
  if (!token && viewName !== 'login') {
    showView('login');
    return;
  }
  
  // Handle admin sub-routes
  if (viewName === 'admin') {
    if (!canManageUsers()) {
      showView('tickets');
      return;
    }
    showView('admin');
    // Switch to appropriate tab
    if (subRoute === 'departments' && isAdminOrGiamDoc()) {
      const deptTab = document.querySelector('.tab-btn[data-tab="departments"]');
      if (deptTab) deptTab.click();
    } else {
      const userTab = document.querySelector('.tab-btn[data-tab="users"]');
      if (userTab) userTab.click();
    }
    return;
  }
  
  // Role-based route guards
  if (viewName === 'reports' && !isStaff()) {
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
  
  // Show/hide tab buttons based on role
  document.querySelectorAll('.tab-btn').forEach(btn => {
    const required = btn.dataset.role;
    if (!required) return;
    const roles = required.split(',').map((role) => role.trim()).filter(Boolean);
    const allowed = roles.some((role) => hasRole(role));
    btn.classList.toggle('hidden', !allowed);
  });
  
  // Show/hide assignee field based on role
  if (assigneeCreate) {
    assigneeCreate.closest('label').classList.toggle('hidden', !canAssignTickets());
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
    const deptLabel = ticket.departmentName || ticket.departmentCode || '';
    const deptBadge = deptLabel ? `<span class="dept-badge">${deptLabel}</span>` : '';
    
    row.innerHTML = `
      <div class="assignee-avatar" aria-hidden="true">
        ${avatarImage || avatarLetter}
      </div>
      <div class="ticket-row-main">
        <h4>${ticket.title}</h4>
        <div class="ticket-row-meta">${ticket.ticketNumber} • ${formatStatus(ticket.category)} ${deptBadge}</div>
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
  if (ticketLoading) {
    console.log('loadTickets: BLOCKED by ticketLoading flag');
    return;
  }
  console.log('loadTickets: Starting', { reset, ticketLoading });
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
    console.log('loadTickets: Calling renderTickets with', rows.length, 'rows');
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
  const deptInfo = full.departmentName ? `${full.departmentName} (${full.departmentCode})` : '';
  
  ticketDetails.innerHTML = `
    <div class="ticket-detail-card">
      <div class="ticket-detail-header">
        <h4>${full.title}</h4>
        <div class="ticket-detail-chips">
          <span class="status ${full.status ? full.status.toLowerCase().replace(/_/g, '-') : 'new'}">${formatStatus(full.status)}</span>
          <span class="priority ${full.priority ? full.priority.toLowerCase() : 'low'}">${formatStatus(full.priority)}</span>
          <span class="pill">${formatStatus(full.category)}</span>
          ${deptInfo ? `<span class="dept-badge">${deptInfo}</span>` : ''}
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
  // Ensure engineer options are loaded, then populate assignee dropdown
  console.log('selectTicket: current engineerOptions length', engineerOptions.length);
  if (engineerOptions.length === 0) {
    console.log('selectTicket: Loading engineer options...');
    await loadEngineerOptions();
    console.log('selectTicket: After load, engineerOptions length', engineerOptions.length);
  }
  populateAssigneeSelect(assigneeInput, { includeUnassigned: true });
  ensureAssigneeOption(assigneeInput, full.assigneeName);
  assigneeInput.value = full.assigneeName || UNASSIGNED_VALUE;
  applyRoleControls();
  await Promise.all([loadComments(full.id), loadAudit(full.id), loadAssignments(full.id), loadAttachments(full.id)]);
};

const loadComments = async (ticketId) => {
  const data = await request(`/api/tickets/${ticketId}/comments`);
  commentsList.innerHTML = '';
  
  // For NHAN_VIEN, filter out internal comments
  const filteredData = canViewInternalComments() 
    ? data 
    : data.filter(c => c.visibility === 'PUBLIC');
  
  filteredData.forEach((comment) => {
    const item = document.createElement('div');
    item.className = 'list-item';
    const visibilityBadge = comment.visibility === 'INTERNAL' ? ' [Nội bộ]' : '';
    item.textContent = `${comment.actorName || 'Hệ thống'}${visibilityBadge}: ${comment.body}`;
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

// ==================== Attachment Functions ====================

const loadAttachments = async (ticketId) => {
  console.log('loadAttachments: Loading for ticketId =', ticketId);
  try {
    const data = await request(`/api/tickets/${ticketId}/attachments`);
    console.log('loadAttachments: Received data =', data);
    attachmentsList.innerHTML = '';
    
    if (!data || data.length === 0) {
      attachmentsList.innerHTML = '<p class="muted">Chưa có tệp đính kèm.</p>';
      return;
    }
    
    data.forEach((attachment) => {
      const item = document.createElement('div');
      item.className = 'attachment-item';
      
      const icon = getFileIcon(attachment.contentType);
      const size = formatFileSize(attachment.fileSize);
      const date = new Date(attachment.createdAt).toLocaleString('vi-VN');
      
      const downloadLink = document.createElement('a');
      downloadLink.href = '#';
      downloadLink.className = 'attachment-name';
      downloadLink.textContent = attachment.originalName;
      downloadLink.style.color = 'var(--accent)';
      downloadLink.style.textDecoration = 'none';
      downloadLink.style.fontWeight = '500';
      downloadLink.style.wordBreak = 'break-all';
      downloadLink.addEventListener('click', (e) => {
        e.preventDefault();
        downloadAttachmentFile(attachment.id, attachment.originalName, attachment.contentType);
      });
      
      item.innerHTML = `<span class="attachment-icon">${icon}</span>`;
      const infoDiv = document.createElement('div');
      infoDiv.className = 'attachment-info';
      infoDiv.appendChild(downloadLink);
      const metaSpan = document.createElement('span');
      metaSpan.className = 'attachment-meta';
      metaSpan.textContent = `${size} • ${date}`;
      infoDiv.appendChild(metaSpan);
      item.appendChild(infoDiv);
      attachmentsList.appendChild(item);
    });
  } catch (error) {
    console.error('loadAttachments: Error =', error);
    attachmentsList.innerHTML = '<p class="muted">Lỗi khi tải danh sách đính kèm.</p>';
  }
};

const downloadAttachmentFile = async (attachmentId, fileName, contentType) => {
  try {
    const token = localStorage.getItem(tokenKey);
    const response = await fetch(`/api/tickets/attachments/${attachmentId}/download`, {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${token}`
      }
    });
    
    if (!response.ok) {
      throw new Error('Download failed');
    }
    
    const blob = await response.blob();
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = fileName;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    window.URL.revokeObjectURL(url);
  } catch (error) {
    console.error('Download error:', error);
    alert('Lỗi khi tải tệp: ' + error.message);
  }
};

const uploadAttachments = async (ticketId, files) => {
  if (!files || files.length === 0) return;
  
  const token = localStorage.getItem(tokenKey);
  let uploadCount = 0;
  let errorCount = 0;
  
  for (const file of files) {
    const formData = new FormData();
    formData.append('file', file);
    
    try {
      // NOTE: Do NOT set Content-Type header manually with FormData!
      // Browser must set it with the correct boundary
      const response = await fetch(`/api/tickets/${ticketId}/attachments`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${token}`
          // Content-Type will be auto-set to multipart/form-data with boundary
        },
        body: formData
      });
      
      if (response.ok) {
        uploadCount++;
        console.log(`Uploaded: ${file.name}`);
      } else {
        errorCount++;
        const errorText = await response.text();
        console.error(`Upload failed for ${file.name}:`, response.status, errorText);
        alert(`Lỗi khi tải lên tệp ${file.name}: ${errorText}`);
      }
    } catch (error) {
      errorCount++;
      console.error('Error uploading attachment:', error);
      alert(`Lỗi khi tải lên tệp ${file.name}: ${error.message}`);
    }
  }
  
  console.log(`Upload complete: ${uploadCount} success, ${errorCount} failed`);
  
  // Refresh attachments list
  await loadAttachments(ticketId);
};

const getFileIcon = (contentType) => {
  if (!contentType) return '📄';
  if (contentType.startsWith('image/')) return '🖼️';
  if (contentType === 'application/pdf') return '📕';
  if (contentType === 'text/plain') return '📝';
  return '📎';
};

const formatFileSize = (bytes) => {
  if (!bytes) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB'];
  let i = 0;
  let size = bytes;
  while (size >= 1024 && i < units.length - 1) {
    size /= 1024;
    i++;
  }
  return `${size.toFixed(1)} ${units[i]}`;
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
  if (!canAssignTickets()) {
    alert('Bạn không có quyền phân công phiếu. Chỉ Trưởng phòng IT mới có quyền này.');
    return;
  }
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
  if (!canAssignTickets()) {
    alert('Bạn không có quyền phân công phiếu. Chỉ Trưởng phòng IT mới có quyền này.');
    return;
  }
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
  
  // Check internal comment permission
  if (commentVisibility.value === 'INTERNAL' && !canAddInternalComments()) {
    alert('Bạn không có quyền thêm bình luận nội bộ. Chỉ nhân viên IT mới có quyền này.');
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

// Format datetime for display
const formatDateTime = (dateString) => {
  if (!dateString) return '';
  try {
    const date = new Date(dateString);
    return date.toLocaleString('vi-VN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit'
    });
  } catch (e) {
    return dateString;
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
  // Only staff can see queue
  if (!isStaff()) {
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
  renderReportTable(columns, rows);
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

const updatePendingBadge = (count) => {
  const badges = ['pending-users-badge', 'pending-users-badge-nav', 'pending-users-badge-dd'];
  badges.forEach(id => {
    const badge = document.getElementById(id);
    if (badge) {
      if (count > 0) {
        badge.textContent = count;
        badge.classList.remove('hidden');
      } else {
        badge.classList.add('hidden');
      }
    }
  });
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

  // Load pending users section (visible for ADMIN and TRUONG_PHONG)
  // ADMIN sees all pending, TRUONG_PHONG sees pending in their department
  if (isAdmin() || isTruongPhong()) {
    await loadPendingUsers();
  }

  // Load delete requests section (visible for ADMIN and TRUONG_PHONG)
  // ADMIN sees with action buttons, TRUONG_PHONG sees as pending (read-only)
  if (isAdmin() || isTruongPhong()) {
    await loadDeleteRequests();
  }

  // Load departments section (visible for ADMIN and GIAM_DOC)
  if (isAdmin() || isGiamDoc()) {
    await loadDepartmentsTable();
  }

  // Count truly pending users from the loaded data (approved=false AND no rejectionReason)
  // This ensures badge count matches what's actually displayed in the pending table
  let pendingCount = 0;
  data.content.forEach((user) => {
    if (user.approved === false && !user.rejectionReason) {
      pendingCount++;
    }
  });

  data.content.forEach((user) => {
    const row = document.createElement('tr');
    const deptInfo = user.departmentCode ? ` (${user.departmentCode})` : ' (—)';

    // Determine status display
    let statusBadge = '';
    let statusClass = '';
    if (!user.approved && user.rejectionReason) {
      statusBadge = 'Từ chối';
      statusClass = 'status-rejected';
    } else if (!user.approved) {
      statusBadge = 'Chờ duyệt';
      statusClass = 'status-pending';
    } else if (!user.enabled) {
      statusBadge = 'Vô hiệu hoá';
      statusClass = 'status-disabled';
    } else {
      statusBadge = 'Hoạt động';
      statusClass = 'status-active';
    }

    row.innerHTML = `
      <td>${user.username}</td>
      <td>${user.displayName || '—'}</td>
      <td>${formatRole(user.role)}</td>
      <td>${deptInfo}</td>
      <td><span class="status-badge ${statusClass}">${statusBadge}</span></td>
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

  // Update pending badge if exists
  updatePendingBadge(pendingCount);
};

const loadPendingUsers = async () => {
  const pendingSection = document.getElementById('pending-users-section');
  const pendingTbody = document.getElementById('pending-users-tbody');
  const pendingCountBadge = document.getElementById('pending-count-badge');

  if (!pendingSection || !pendingTbody) return;

  try {
    const pendingUsers = await request('/api/admin/users/pending');

    // Filter: only show users with approved=false AND no rejectionReason (truly pending)
    const trulyPendingUsers = pendingUsers.filter(user => 
      user.approved === false && !user.rejectionReason
    );

    if (trulyPendingUsers.length === 0) {
      pendingSection.classList.add('hidden');
      // Update badge
      updatePendingBadge(0);
      return;
    }

    pendingSection.classList.remove('hidden');
    pendingTbody.innerHTML = '';

    if (pendingCountBadge) {
      pendingCountBadge.textContent = `(${trulyPendingUsers.length})`;
    }

    // Update pending badge in nav
    updatePendingBadge(trulyPendingUsers.length);

    trulyPendingUsers.forEach((user) => {
      const row = document.createElement('tr');
      const deptInfo = user.departmentCode || '—';
      const createdAt = user.createdAt ? new Date(user.createdAt).toLocaleDateString('vi-VN') : '—';

      // For ADMIN: show approve/reject buttons
      // For TRUONG_PHONG: show status only
      let actionHtml = '';
      if (isAdmin()) {
        actionHtml = `
          <button class="btn-approve btn-small" data-user-id="${user.id}">✓ Duyệt</button>
          <button class="btn-reject btn-small" data-user-id="${user.id}">✗ Từ chối</button>
        `;
      } else {
        actionHtml = '<span class="status-badge status-pending">Đang chờ duyệt</span>';
      }

      row.innerHTML = `
        <td>${user.username}</td>
        <td>${user.displayName || '—'}</td>
        <td>${formatRole(user.role)}</td>
        <td>${deptInfo}</td>
        <td>${createdAt}</td>
        <td>${actionHtml}</td>
      `;

      // Click row to view detail (only for admin)
      if (isAdmin()) {
        row.addEventListener('click', () => selectUser(user));
      }

      pendingTbody.appendChild(row);
    });

    // Attach event listeners for approve/reject buttons (ADMIN only)
    pendingTbody.querySelectorAll('.btn-approve').forEach((btn) => {
      btn.addEventListener('click', async (e) => {
        e.stopPropagation();
        const userId = btn.dataset.userId;
        const userDisplayName = btn.closest('tr').querySelector('td:nth-child(2)')?.textContent || btn.closest('tr').querySelector('td:first-child')?.textContent;
        if (!confirm('Bạn có chắc muốn duyệt tài khoản này?')) return;
        try {
          await request(`/api/admin/users/${userId}/approve`, { method: 'POST', body: JSON.stringify({}) });
          // Reload both pending and all users
          await loadPendingUsers();
          await loadAdminUsers();
          alert('Đã duyệt tài khoản thành công!');
        } catch (error) {
          alert('Lỗi: ' + error.message);
        }
      });
    });

    pendingTbody.querySelectorAll('.btn-reject').forEach((btn) => {
      btn.addEventListener('click', async (e) => {
        e.stopPropagation();
        const userId = btn.dataset.userId;
        const userDisplayName = btn.closest('tr').querySelector('td:nth-child(2)')?.textContent || btn.closest('tr').querySelector('td:first-child')?.textContent;
        
        // Show reject modal instead of prompt
        const rejectModal = document.getElementById('reject-modal');
        const rejectModalUsername = document.getElementById('reject-modal-username');
        const rejectReasonInput = document.getElementById('reject-reason-input');
        
        if (rejectModal && rejectModalUsername && rejectReasonInput) {
          rejectModalUsername.textContent = `Tài khoản: ${userDisplayName}`;
          rejectReasonInput.value = '';
          rejectModal.classList.remove('hidden');
          rejectModal.setAttribute('aria-hidden', 'false');
          rejectReasonInput.focus();
          
          // Store userId for later use
          rejectModal.dataset.userId = userId;
          rejectModal.dataset.userDisplayName = userDisplayName;
        }
      });
    });
  } catch (error) {
    console.error('Error loading pending users:', error);
    pendingSection.classList.add('hidden');
  }
};

// ============ Delete Requests Section ============

const loadDeleteRequests = async () => {
  const deleteRequestsSection = document.getElementById('delete-requests-section');
  const deleteRequestsTbody = document.getElementById('delete-requests-tbody');
  const deleteRequestsCountBadge = document.getElementById('delete-requests-count-badge');

  if (!deleteRequestsSection || !deleteRequestsTbody) return;

  try {
    // ADMIN and TRUONG_PHONG can see delete requests
    if (!isAdmin() && !isTruongPhong()) {
      deleteRequestsSection.classList.add('hidden');
      return;
    }

    const deleteRequests = await request('/api/admin/users/delete-requests');

    if (deleteRequests.length === 0) {
      deleteRequestsSection.classList.add('hidden');
      if (deleteRequestsCountBadge) {
        deleteRequestsCountBadge.textContent = '';
      }
      return;
    }

    deleteRequestsSection.classList.remove('hidden');
    deleteRequestsTbody.innerHTML = '';

    if (deleteRequestsCountBadge) {
      deleteRequestsCountBadge.textContent = `(${deleteRequests.length})`;
    }

    deleteRequests.forEach((user) => {
      const row = document.createElement('tr');
      const deptInfo = user.departmentCode || '—';
      const requestedBy = user.createdBy || '—';

      // ADMIN sees both buttons, TRUONG_PHONG sees only "Cancel request" button
      // (because TRUONG_PHONG is the one who sent the request, they can only cancel it)
      let actionHtml = '';
      if (isAdmin()) {
        actionHtml = `
          <button class="btn-approve btn-small" data-delete-user-id="${user.id}">✓ Xóa</button>
          <button class="btn-reject btn-small" data-cancel-request-id="${user.id}">✗ Hủy yêu cầu</button>
        `;
      } else {
        actionHtml = `
          <button class="btn-reject btn-small" data-cancel-request-id="${user.id}">✗ Hủy yêu cầu</button>
        `;
      }

      row.innerHTML = `
        <td>${user.username}</td>
        <td>${user.displayName || '—'}</td>
        <td>${formatRole(user.role)}</td>
        <td>${deptInfo}</td>
        <td>${requestedBy}</td>
        <td>${actionHtml}</td>
      `;

      deleteRequestsTbody.appendChild(row);
    });

    // Attach event listeners for approve delete button (ADMIN only)
    deleteRequestsTbody.querySelectorAll('.btn-approve').forEach((btn) => {
      btn.addEventListener('click', async (e) => {
        e.stopPropagation();
        const userId = btn.dataset.deleteUserId;
        const userDisplayName = btn.closest('tr').querySelector('td:nth-child(2)')?.textContent || btn.closest('tr').querySelector('td:first-child')?.textContent;
        if (!confirm(`Bạn có chắc muốn xóa tài khoản "${userDisplayName}"?\n\nHành động này không thể hoàn tác.`)) return;
        try {
          await request(`/api/admin/users/${userId}/delete-request`, { method: 'DELETE' });
          addNotification('delete', 'Xóa tài khoản', `Tài khoản "${userDisplayName}" đã được xóa.`);
          alert('Đã xóa tài khoản thành công!');
          // Reload delete requests
          await loadDeleteRequests();
          // Reload all users
          await loadAdminUsers();
        } catch (error) {
          alert('Lỗi: ' + error.message);
        }
      });
    });

    // Attach event listeners for cancel request button (ADMIN only)
    deleteRequestsTbody.querySelectorAll('.btn-reject').forEach((btn) => {
      btn.addEventListener('click', async (e) => {
        e.stopPropagation();
        const userId = btn.dataset.cancelRequestId;
        const userDisplayName = btn.closest('tr').querySelector('td:nth-child(2)')?.textContent || btn.closest('tr').querySelector('td:first-child')?.textContent;
        if (!confirm(`Bạn có chắc muốn hủy yêu cầu xóa tài khoản "${userDisplayName}"?`)) return;
        try {
          // Cancel the delete request
          await request(`/api/admin/users/${userId}/cancel-delete-request`, { method: 'POST' });
          addNotification('reject', 'Hủy yêu cầu xóa', `Yêu cầu xóa tài khoản "${userDisplayName}" đã bị hủy.`);
          alert('Đã hủy yêu cầu xóa!');
          // Reload delete requests
          await loadDeleteRequests();
          // Reload all users
          await loadAdminUsers();
        } catch (error) {
          alert('Lỗi: ' + error.message);
        }
      });
    });
  } catch (error) {
    console.error('Error loading delete requests:', error);
    deleteRequestsSection.classList.add('hidden');
  }
};

const selectUser = async (user) => {
  selectedUser = user;
  
  // Load departments for the detail modal
  if (userDeptSelect) {
    const depts = await request('/api/departments');
    populateDepartmentSelect(userDeptSelect, depts);
    if (user.departmentId) {
      userDeptSelect.value = user.departmentId;
    }
  }
  
  if (userDetailModal) {
    userDetailModal.classList.remove('hidden');
  }
  if (userDetailHeader) {
    const deptInfo = user.departmentName ? ` - ${user.departmentName}` : '';
    userDetailHeader.textContent = `${formatName(user.username)} (${formatRole(user.role)}${deptInfo})`;
  }
  
  // Populate user info fields
  if (userDisplayName) userDisplayName.value = user.displayName || '';
  if (userTitle) userTitle.value = user.title || '';
  if (userEmail) userEmail.value = user.email || '';
  
  // Handle approval info display
  const userApprovalInfo = document.getElementById('user-approval-info');
  const userApprovalStatus = document.getElementById('user-approval-status');
  const userApprovalDate = document.getElementById('user-approval-date');
  const userApprovalBy = document.getElementById('user-approval-by');
  const userRejectionReason = document.getElementById('user-rejection-reason');
  const userApprovalActions = document.getElementById('user-approval-actions');
  const userRejectionForm = document.getElementById('user-rejection-form');

  // Debug: check user status
  console.log('=== selectUser ===');
  console.log('user.approved:', user.approved);
  console.log('user.rejectionReason:', user.rejectionReason);
  console.log('isApproved:', user.approved);
  console.log('isPending:', !user.approved && !user.rejectionReason);

  // ALWAYS hide all approval-related elements first - use style directly to ensure it works
  if (userApprovalInfo) {
    userApprovalInfo.classList.add('hidden');
    userApprovalInfo.style.display = 'none';
  }
  if (userApprovalActions) {
    userApprovalActions.classList.add('hidden');
    userApprovalActions.style.display = 'none';
  }
  if (userRejectionForm) {
    userRejectionForm.classList.add('hidden');
    userRejectionForm.style.display = 'none';
  }
  // Hide rejected warning by default
  const userRejectedWarning = document.getElementById('user-rejected-warning');
  if (userRejectedWarning) {
    userRejectedWarning.classList.add('hidden');
  }

  // Check user status
  const isPending = !user.approved && !user.rejectionReason;
  const isRejected = !user.approved && user.rejectionReason;
  const isApproved = user.approved;
  const isDisabled = !user.enabled;

  // PENDING user
  if (isPending) {
    if (userApprovalInfo) {
      userApprovalInfo.classList.remove('hidden');
      userApprovalInfo.classList.remove('rejected');
      if (userApprovalStatus) userApprovalStatus.textContent = '⏳ Đang chờ phê duyệt';
      if (userRejectionReason) userRejectionReason.classList.add('hidden');
      if (userApprovalDate && user.approvedAt) {
        userApprovalDate.textContent = 'Thời gian: ' + formatDateTime(user.approvedAt);
      } else if (userApprovalDate) {
        userApprovalDate.textContent = '';
      }
      if (userApprovalBy && user.approvedBy) {
        userApprovalBy.textContent = 'Bởi: ' + user.approvedBy;
      } else if (userApprovalBy) {
        userApprovalBy.textContent = '';
      }
    }
    // Admin: show approve/reject buttons for pending users
    if (isAdmin() && userApprovalActions) {
      userApprovalActions.classList.remove('hidden');
      userApprovalActions.style.display = 'flex';
    }
  }
  // REJECTED user
  else if (isRejected) {
    if (userApprovalInfo) {
      userApprovalInfo.classList.remove('hidden');
      userApprovalInfo.classList.add('rejected');
      if (userApprovalStatus) userApprovalStatus.textContent = '❌ Đã bị từ chối';
      if (userRejectionReason) {
        userRejectionReason.classList.remove('hidden');
        userRejectionReason.textContent = 'Lý do: ' + user.rejectionReason;
      }
      if (userApprovalDate && user.approvedAt) {
        userApprovalDate.textContent = 'Thời gian: ' + formatDateTime(user.approvedAt);
      }
      if (userApprovalBy && user.approvedBy) {
        userApprovalBy.textContent = 'Bởi: ' + user.approvedBy;
      }
    }
    // Show warning that this rejected account cannot be edited
    const userRejectedWarning = document.getElementById('user-rejected-warning');
    if (userRejectedWarning) {
      userRejectedWarning.classList.remove('hidden');
    }
    // Ensure buttons are hidden for rejected users
    if (userApprovalActions) userApprovalActions.classList.add('hidden');
    if (userRejectionForm) userRejectionForm.classList.add('hidden');
  }
  // APPROVED user
  else if (isApproved) {
    // Show approval info if approved by someone
    if (userApprovalBy && user.approvedBy) {
      if (userApprovalInfo) {
        userApprovalInfo.classList.remove('hidden');
        userApprovalInfo.classList.remove('rejected');
        if (userApprovalStatus) userApprovalStatus.textContent = '✓ Đã được phê duyệt';
        if (userRejectionReason) userRejectionReason.classList.add('hidden');
        if (userApprovalDate && user.approvedAt) {
          userApprovalDate.textContent = 'Thời gian: ' + formatDateTime(user.approvedAt);
        }
        userApprovalBy.textContent = 'Bởi: ' + user.approvedBy;
      }
    }
    // Ensure buttons are hidden for approved users
    if (userApprovalActions) userApprovalActions.classList.add('hidden');
    if (userRejectionForm) userRejectionForm.classList.add('hidden');
  }
  // DISABLED user
  else if (isDisabled) {
    if (userApprovalInfo) {
      userApprovalInfo.classList.remove('hidden');
      userApprovalInfo.classList.add('rejected');
      if (userApprovalStatus) userApprovalStatus.textContent = '🔒 Đang bị vô hiệu hoá';
      if (userApprovalDate && user.approvedAt) {
        userApprovalDate.textContent = 'Cập nhật lần cuối: ' + formatDateTime(user.approvedAt);
      }
      if (userApprovalBy && user.approvedBy) {
        userApprovalBy.textContent = 'Bởi: ' + user.approvedBy;
      }
      if (userRejectionReason) userRejectionReason.classList.add('hidden');
    }
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

const loadProfile = async () => {
  const profile = await request('/api/users/me');
  profileDetails.innerHTML = '';
  const item = document.createElement('div');
  item.className = 'list-item';
  const displayName = profile.displayName || profile.username;
  const deptInfo = profile.departmentName ? ` - ${profile.departmentName}` : '';
  const nameLine = displayName ? `${displayName} (${formatRole(profile.role)}${deptInfo})` : `${profile.username} (${formatRole(profile.role)})`;
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
  
  // Update currentUser from profile
  currentUser = {
    id: profile.id,
    username: profile.username,
    role: profile.role,
    departmentId: profile.departmentId,
    departmentCode: profile.departmentCode,
    departmentName: profile.departmentName,
    displayName: profile.displayName,
    email: profile.email,
  };
  localStorage.setItem('ticketing.currentUser', JSON.stringify(currentUser));
  updateTokenStatus();
};

// Event Listeners
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
      await loadDepartments();
    }
    await loadProfile();
  } catch (error) {
    console.log('[DEBUG] Login error:', error);
    
    // Check for specific authentication errors
    const errorCode = error.errorCode;
    
    if (errorCode === 'AUTH_USER_NOT_APPROVED') {
      if (loginError) {
        loginError.innerHTML = `
          <div class="login-error-icon">⚠️</div>
          <div class="login-error-title">Tài khoản chưa được phê duyệt</div>
          <div class="login-error-message">
            Tài khoản của bạn hiện đang trong trạng thái chờ phê duyệt.
            Vui lòng liên hệ <strong>Trưởng phòng</strong> hoặc <strong>Admin</strong> để được hỗ trợ.
          </div>
          ${error.details && error.details.supportContact ? 
            `<div class="login-error-contact">📧 Liên hệ: ${error.details.supportContact}</div>` : ''}
        `;
        loginError.classList.remove('hidden');
      }
      return;
    }
    
    if (errorCode === 'AUTH_USER_DISABLED') {
      if (loginError) {
        loginError.innerHTML = `
          <div class="login-error-icon">🔒</div>
          <div class="login-error-title">Tài khoản đang bị vô hiệu hoá</div>
          <div class="login-error-message">
            Tài khoản của bạn hiện đang bị vô hiệu hoá.
            Vui lòng liên hệ <strong>Trưởng phòng</strong> hoặc <strong>Admin</strong> để được hỗ trợ.
          </div>
          ${error.details && error.details.supportContact ? 
            `<div class="login-error-contact">📧 Liên hệ: ${error.details.supportContact}</div>` : ''}
        `;
        loginError.classList.remove('hidden');
      }
      return;
    }
    
    if (errorCode === 'AUTH_USER_REJECTED') {
      if (loginError) {
        let reasonText = '';
        if (error.details && error.details.rejectionReason) {
          reasonText = `<div class="login-error-reason">Lý do: ${error.details.rejectionReason}</div>`;
        }
        loginError.innerHTML = `
          <div class="login-error-icon">❌</div>
          <div class="login-error-title">Tài khoản đã bị từ chối</div>
          <div class="login-error-message">
            Tài khoản của bạn đã bị từ chối trong quá trình phê duyệt.
            Vui lòng liên hệ <strong>Admin</strong> để biết thêm chi tiết.
          </div>
          ${reasonText}
          ${error.details && error.details.supportContact ? 
            `<div class="login-error-contact">📧 Liên hệ: ${error.details.supportContact}</div>` : ''}
        `;
        loginError.classList.remove('hidden');
      }
      return;
    }
    
    if (errorCode === 'AUTH_INVALID_CREDENTIALS' || error.message.includes('401') || error.message.includes('Unauthorized')) {
      if (loginError) {
        loginError.textContent = 'Tên đăng nhập hoặc mật khẩu không hợp lệ.';
        loginError.classList.remove('hidden');
      }
      return;
    }
    
    // Generic error
    if (loginError) {
      loginError.textContent = error.message;
      loginError.classList.remove('hidden');
    } else {
      alert(error.message);
    }
  }
});

logoutBtn.addEventListener('click', () => {
  setToken(null);
  currentUser = null;
  localStorage.removeItem('ticketing.currentUser');
  // Stop notification polling
  stopNotificationPolling();
  notifications = [];
  updateNotificationBadge(0);
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
    const result = await request('/api/tickets', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
    
    // Save files to upload before clearing
    const filesToUpload = [...selectedFiles];
    
    createForm.reset();
    selectedFiles = [];
    if (selectedFilesList) selectedFilesList.innerHTML = '';
    
    // Upload attachments if any
    if (filesToUpload.length > 0) {
      await uploadAttachments(result.id, filesToUpload);
    }
    
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

// ==================== File Attachment Event Handlers ====================

const initAttachmentHandlers = () => {
  // Handle file selection in create form
  if (attachmentInput) {
    attachmentInput.addEventListener('change', (e) => {
      const files = Array.from(e.target.files);
      selectedFiles = [...selectedFiles, ...files];
      renderSelectedFiles();
    });
  }
  
  // Handle drag and drop on ticket file upload area
  if (ticketFileUploadArea) {
    ['dragenter', 'dragover'].forEach(eventName => {
      ticketFileUploadArea.addEventListener(eventName, (e) => {
        e.preventDefault();
        e.stopPropagation();
        ticketFileUploadArea.classList.add('dragover');
      });
    });
    
    ['dragleave', 'drop'].forEach(eventName => {
      ticketFileUploadArea.addEventListener(eventName, (e) => {
        e.preventDefault();
        e.stopPropagation();
        ticketFileUploadArea.classList.remove('dragover');
      });
    });
    
    ticketFileUploadArea.addEventListener('drop', (e) => {
      const files = Array.from(e.dataTransfer.files);
      if (files.length > 0) {
        ticketSelectedFilesList = [...ticketSelectedFilesList, ...files];
        renderTicketSelectedFiles();
      }
    });
  }
  
  // Handle file selection via input
  if (ticketAttachmentInput) {
    ticketAttachmentInput.addEventListener('change', (e) => {
      const files = Array.from(e.target.files);
      ticketSelectedFilesList = [...ticketSelectedFilesList, ...files];
      renderTicketSelectedFiles();
    });
  }
  
  // Handle upload button in ticket detail
  if (uploadAttachmentBtn) {
    uploadAttachmentBtn.addEventListener('click', async () => {
      if (!selectedTicket) return;
      if (ticketSelectedFilesList.length === 0) {
        alert('Vui lòng chọn tệp đính kèm.');
        return;
      }
      await uploadAttachments(selectedTicket.id, ticketSelectedFilesList);
      ticketSelectedFilesList = [];
      renderTicketSelectedFiles();
      ticketAttachmentInput.value = '';
    });
  }
};

const renderSelectedFiles = () => {
  if (!selectedFilesList) return;
  selectedFilesList.innerHTML = '';
  
  if (selectedFiles.length === 0) {
    selectedFilesList.innerHTML = '';
    return;
  }
  
  selectedFiles.forEach((file, index) => {
    const item = document.createElement('div');
    item.className = 'selected-file-item';
    const icon = getFileIcon(file.type);
    const size = formatFileSize(file.size);
    item.innerHTML = `
      <span>${icon} ${file.name} (${size})</span>
      <button type="button" class="remove-file-btn" data-index="${index}">×</button>
    `;
    selectedFilesList.appendChild(item);
  });
  
  // Add remove button handlers
  document.querySelectorAll('.remove-file-btn').forEach((btn) => {
    btn.addEventListener('click', (e) => {
      const index = parseInt(e.target.dataset.index, 10);
      selectedFiles.splice(index, 1);
      renderSelectedFiles();
    });
  });
};

const renderTicketSelectedFiles = () => {
  if (!ticketSelectedFiles) return;
  ticketSelectedFiles.innerHTML = '';
  
  if (ticketSelectedFilesList.length === 0) {
    return;
  }
  
  ticketSelectedFilesList.forEach((file, index) => {
    const item = document.createElement('div');
    item.className = 'selected-file-item';
    const icon = getFileIcon(file.type);
    const size = formatFileSize(file.size);
    item.innerHTML = `
      <span>${icon} ${file.name} (${size})</span>
      <button type="button" class="remove-file-btn" data-ticket-index="${index}">×</button>
    `;
    ticketSelectedFiles.appendChild(item);
  });
  
  // Add remove button handlers
  document.querySelectorAll('[data-ticket-index]').forEach((btn) => {
    btn.addEventListener('click', (e) => {
      const index = parseInt(e.target.dataset.ticketIndex, 10);
      ticketSelectedFilesList.splice(index, 1);
      renderTicketSelectedFiles();
    });
  });
};

if (filterAssignee) {
  filterAssignee.addEventListener('change', () => {
    console.log('filterAssignee change:', filterAssignee.value);
    ticketPage = 0;
    saveTicketFilters();
    loadTickets({ reset: true }).catch((error) => alert(error.message));
  });
}

if (filterStatus) {
  filterStatus.addEventListener('change', () => {
    console.log('filterStatus change:', filterStatus.value);
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
  openUserModal.addEventListener('click', async () => {
    await loadDepartments();
    if (userCreateModal) {
      userCreateModal.classList.remove('hidden');
    }

    // For TRUONG_PHONG, set default department to their own department
    if (isTruongPhong() && currentUser?.departmentId) {
      const deptSelect = document.getElementById('admin-dept-select');
      if (deptSelect) {
        deptSelect.value = currentUser.departmentId;
        deptSelect.disabled = true;
      }
    } else {
      const deptSelect = document.getElementById('admin-dept-select');
      if (deptSelect) {
        deptSelect.disabled = false;
      }
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
    
    const hasPasswordChange = userPasswordInput && userPasswordInput.value.trim();
    const hasProfileChange = 
      userDisplayName.value !== selectedUser.displayName ||
      userTitle.value !== selectedUser.title ||
      userEmail.value !== selectedUser.email;
    
    // If password change is included, use the combined API
    if (hasPasswordChange && hasProfileChange) {
      const payload = {
        displayName: userDisplayName.value,
        title: userTitle.value,
        email: userEmail.value,
        newPassword: userPasswordInput.value,
      };
      await request(`/api/users/${selectedUser.id}/profile-and-password`, {
        method: 'PATCH',
        body: JSON.stringify(payload),
      });
      userPasswordInput.value = '';
      loadUsers();
      return;
    }
    
    const updates = [];
    
    if (userRoleSelect && !userRoleSelect.disabled && userRoleSelect.value !== selectedUser.role) {
      updates.push(
        request(`/api/users/${selectedUser.id}/role`, {
          method: 'PATCH',
          body: JSON.stringify({ role: userRoleSelect.value }),
        }).then((response) => {
          selectedUser = { ...selectedUser, role: response.role };
        })
      );
    }
    
    if (userEnabledSelect && !userEnabledSelect.disabled) {
      const enabledValue = userEnabledSelect.checked;
      if (enabledValue !== selectedUser.enabled) {
        updates.push(
          request(`/api/users/${selectedUser.id}/enabled`, {
            method: 'PATCH',
            body: JSON.stringify({ enabled: enabledValue }),
          }).then((response) => {
            selectedUser = { ...selectedUser, enabled: response.enabled };
          })
        );
      }
    }
    
    // Password only change
    if (hasPasswordChange) {
      updates.push(
        request(`/api/users/${selectedUser.id}/password`, {
          method: 'PATCH',
          body: JSON.stringify({ password: userPasswordInput.value }),
        })
      );
    }
    
    // Profile only change
    const profilePayload = {
      displayName: userDisplayName.value,
      title: userTitle.value,
      email: userEmail.value,
    };
    if (hasProfileChange) {
      updates.push(
        request(`/api/users/${selectedUser.id}/profile`, {
          method: 'PATCH',
          body: JSON.stringify(profilePayload),
        }).then((response) => {
          selectedUser = { ...selectedUser, ...response };
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
      const deptInfo = selectedUser.departmentName ? ` - ${selectedUser.departmentName}` : '';
      userDetailHeader.textContent = `${formatName(selectedUser.username)} (${formatRole(selectedUser.role)}${deptInfo})`;
    }
    await loadAdminUsers();
    await loadUserAvatarMap();
  });
}

if (userDelete) {
  userDelete.addEventListener('click', async () => {
    if (!selectedUser) return;
    
    if (isAdmin()) {
      // ADMIN: xác nhận và xóa trực tiếp
      const confirmMsg = `Bạn có chắc muốn xóa tài khoản "${selectedUser.username}"?\n\nHành động này không thể hoàn tác.`;
      if (!confirm(confirmMsg)) return;
      
      try {
        await request(`/api/users/${selectedUser.id}`, { method: 'DELETE' });
        addNotification('delete', 'Xóa tài khoản', `Tài khoản "${selectedUser.displayName || selectedUser.username}" đã được xóa.`);
        alert('Đã xóa tài khoản thành công!');
      } catch (error) {
        alert('Lỗi: ' + error.message);
        return;
      }
    } else if (isTruongPhong()) {
      // TRUONG_PHONG: thông báo và gửi yêu cầu xóa đợi ADMIN duyệt
      const confirmMsg = `Bạn có chắc muốn yêu cầu xóa tài khoản "${selectedUser.username}"?\n\nSau khi gửi yêu cầu, Admin sẽ xem xét và duyệt xóa.`;
      if (!confirm(confirmMsg)) return;
      
      try {
        await request(`/api/admin/users/${selectedUser.id}/request-delete`, { method: 'POST' });
        addNotification('request-delete', 'Yêu cầu xóa tài khoản', `Yêu cầu xóa tài khoản "${selectedUser.displayName || selectedUser.username}" đã được gửi cho Admin.`);
        alert('Yêu cầu xóa đã được gửi cho Admin!');
        // Reload delete requests to show the new pending request
        await loadDeleteRequests();
      } catch (error) {
        alert('Lỗi: ' + error.message);
        return;
      }
    }
    
    selectedUser = null;
    if (userDetailModal) {
      userDetailModal.classList.add('hidden');
    }
    await loadAdminUsers();
  });
}

// ============ User Approval Event Listeners ============

// Approve user button
const userApproveBtn = document.getElementById('user-approve-btn');
if (userApproveBtn) {
  userApproveBtn.addEventListener('click', async () => {
    if (!selectedUser || !isAdmin()) return;
    const userDisplayName = selectedUser.displayName || selectedUser.username;
    if (!confirm(`Bạn có chắc muốn duyệt tài khoản "${selectedUser.username}"?`)) return;
    try {
      await request(`/api/admin/users/${selectedUser.id}/approve`, {
        method: 'POST',
        body: JSON.stringify({})
      });
      // Add notification
      addNotification('approve', 'Tài khoản được duyệt', `Tài khoản "${userDisplayName}" đã được duyệt.`);
      // Refresh user list and close modal
      await loadAdminUsers();
      if (userDetailModal) {
        userDetailModal.classList.add('hidden');
      }
      selectedUser = null;
      alert('Đã duyệt tài khoản thành công!');
    } catch (error) {
      alert('Lỗi: ' + error.message);
    }
  });
}

// Reject user button - show rejection form (only for pending users)
const userRejectBtn = document.getElementById('user-reject-btn');
const userRejectionForm = document.getElementById('user-rejection-form');
if (userRejectBtn && userRejectionForm) {
  userRejectBtn.addEventListener('click', () => {
    // Only show form if user is pending
    if (!selectedUser || selectedUser.approved || selectedUser.rejectionReason) {
      alert('Chỉ có thể từ chối tài khoản đang chờ duyệt.');
      return;
    }
    if (!isAdmin()) return;
    userRejectionForm.classList.remove('hidden');
    userRejectionForm.style.display = 'block';
    document.getElementById('user-rejection-reason-input').focus();
  });
}

// Confirm reject button
const userConfirmRejectBtn = document.getElementById('user-confirm-reject');
if (userConfirmRejectBtn) {
  userConfirmRejectBtn.addEventListener('click', async () => {
    if (!selectedUser || !isAdmin()) return;
    const userDisplayName = selectedUser.displayName || selectedUser.username;
    const reasonInput = document.getElementById('user-rejection-reason-input');
    const reason = reasonInput.value.trim();
    if (!reason) {
      alert('Vui lòng nhập lý do từ chối.');
      reasonInput.focus();
      return;
    }
    if (!confirm(`Bạn có chắc muốn từ chối tài khoản "${selectedUser.username}"?`)) return;
    try {
      await request(`/api/admin/users/${selectedUser.id}/reject`, {
        method: 'POST',
        body: JSON.stringify({ reason: reason })
      });
      // Add notification
      addNotification('reject', 'Tài khoản bị từ chối', `Tài khoản "${userDisplayName}" đã bị từ chối. Lý do: ${reason}`);
      // Refresh user list and close modal
      await loadPendingUsers();
      await loadAdminUsers();
      if (userDetailModal) {
        userDetailModal.classList.add('hidden');
      }
      selectedUser = null;
      alert('Đã từ chối tài khoản.');
    } catch (error) {
      alert('Lỗi: ' + error.message);
    }
  });
}

// Cancel reject button
const userCancelRejectBtn = document.getElementById('user-cancel-reject');
if (userCancelRejectBtn && userRejectionForm) {
  userCancelRejectBtn.addEventListener('click', () => {
    userRejectionForm.classList.add('hidden');
    userRejectionForm.style.display = 'none';
    document.getElementById('user-rejection-reason-input').value = '';
  });
}

// Reject modal event listeners (for pending table)
if (rejectModalClose) {
  rejectModalClose.addEventListener('click', () => {
    rejectModal.classList.add('hidden');
    rejectModal.setAttribute('aria-hidden', 'true');
  });
}

if (rejectModalCancel) {
  rejectModalCancel.addEventListener('click', () => {
    rejectModal.classList.add('hidden');
    rejectModal.setAttribute('aria-hidden', 'true');
    if (rejectReasonInput) rejectReasonInput.value = '';
  });
}

if (rejectModalConfirm) {
  rejectModalConfirm.addEventListener('click', async () => {
    const userId = rejectModal.dataset.userId;
    const userDisplayName = rejectModal.dataset.userDisplayName;
    const reason = rejectReasonInput?.value.trim();
    
    if (!reason) {
      alert('Vui lòng nhập lý do từ chối.');
      rejectReasonInput?.focus();
      return;
    }
    
    if (!confirm(`Bạn có chắc muốn từ chối tài khoản "${userDisplayName}"?`)) return;
    
    try {
      await request(`/api/admin/users/${userId}/reject`, {
        method: 'POST',
        body: JSON.stringify({ reason: reason }),
      });
      // Add notification
      addNotification('reject', 'Tài khoản bị từ chối', `Tài khoản "${userDisplayName}" đã bị từ chối. Lý do: ${reason}`);
      // Close modal and reload
      rejectModal.classList.add('hidden');
      rejectModal.setAttribute('aria-hidden', 'true');
      if (rejectReasonInput) rejectReasonInput.value = '';
      await loadPendingUsers();
      await loadAdminUsers();
      alert('Đã từ chối tài khoản.');
    } catch (error) {
      alert('Lỗi: ' + error.message);
    }
  });
}

// Close reject modal on outside click
if (rejectModal) {
  rejectModal.addEventListener('click', (e) => {
    if (e.target === rejectModal) {
      rejectModal.classList.add('hidden');
      rejectModal.setAttribute('aria-hidden', 'true');
      if (rejectReasonInput) rejectReasonInput.value = '';
    }
  });
}

// ============ Department Management ============

// Load departments list (for admin panel table)
const loadDepartmentsTable = async () => {
  if (!isAdmin() && !isGiamDoc()) return;
  const deptTbody = document.getElementById('departments-tbody');
  if (!deptTbody) return;

  try {
    const departments = await request('/api/departments/all');
    
    deptTbody.innerHTML = '';

    if (departments.length === 0) {
      deptTbody.innerHTML = '<tr><td colspan="7" style="text-align:center;color:var(--muted)">Chưa có phòng ban nào.</td></tr>';
      return;
    }

    departments.forEach((dept) => {
      const row = document.createElement('tr');
      const statusClass = dept.enabled ? 'status-active' : 'status-disabled';
      const statusBadge = dept.enabled ? 'Hoạt động' : 'Vô hiệu hoá';
      const managerName = dept.managerName || '—';
      const userCount = dept.userCount || 0;
      const description = dept.description || '—';

      row.innerHTML = `
        <td><strong>${dept.code}</strong></td>
        <td>${dept.name}</td>
        <td title="${description}">${description.length > 30 ? description.substring(0, 30) + '...' : description}</td>
        <td>${managerName}</td>
        <td>${userCount}</td>
        <td><span class="status-badge ${statusClass}">${statusBadge}</span></td>
        <td>
          <button class="btn-edit-dept btn-small secondary" data-dept-id="${dept.id}">Sửa</button>
          <button class="btn-delete-dept btn-small ghost" data-dept-id="${dept.id}" data-dept-name="${dept.name}">Xóa</button>
        </td>
      `;
      deptTbody.appendChild(row);
    });

    // Attach event listeners for edit buttons
    deptTbody.querySelectorAll('.btn-edit-dept').forEach((btn) => {
      btn.addEventListener('click', (e) => {
        e.stopPropagation();
        const deptId = btn.dataset.deptId;
        const dept = departments.find(d => d.id == deptId);
        if (dept) openEditDeptModal(dept);
      });
    });

    // Attach event listeners for delete buttons
    deptTbody.querySelectorAll('.btn-delete-dept').forEach((btn) => {
      btn.addEventListener('click', (e) => {
        e.stopPropagation();
        const deptId = btn.dataset.deptId;
        const deptName = btn.dataset.deptName;
        const dept = departments.find(d => d.id == deptId);
        if (dept) openDeleteDeptModal(dept);
      });
    });
  } catch (error) {
    console.error('Error loading departments:', error);
    deptSection.classList.add('hidden');
  }
};

// Open create department modal
const openCreateDeptModal = () => {
  selectedDepartment = null;
  deptModalTitle.textContent = 'Tạo phòng ban';
  deptModalDesc.textContent = 'Thêm một phòng ban mới vào hệ thống.';
  deptId.value = '';
  deptCode.value = '';
  deptCode.disabled = false;
  deptName.value = '';
  deptDescription.value = '';
  deptEnabled.checked = true;
  document.getElementById('dept-enabled-label').classList.add('hidden');
  deptSubmitBtn.textContent = 'Tạo phòng ban';
  deptModal.classList.remove('hidden');
  deptModal.setAttribute('aria-hidden', 'false');
  deptCode.focus();
  
  // Load users into manager dropdown
  loadManagersForDepartment();
};

// Open edit department modal
const openEditDeptModal = (dept) => {
  selectedDepartment = dept;
  deptModalTitle.textContent = 'Sửa phòng ban';
  deptModalDesc.textContent = `Cập nhật thông tin phòng ban "${dept.name}".`;
  deptId.value = dept.id;
  deptCode.value = dept.code;
  deptCode.disabled = true; // Cannot change code
  deptName.value = dept.name;
  deptDescription.value = dept.description || '';
  deptEnabled.checked = dept.enabled;
  document.getElementById('dept-enabled-label').classList.remove('hidden');
  deptSubmitBtn.textContent = 'Lưu thay đổi';
  deptModal.classList.remove('hidden');
  deptModal.setAttribute('aria-hidden', 'false');
  deptName.focus();
  
  // Load users into manager dropdown and select current manager
  console.log('[DEBUG] openEditDeptModal called, managerId:', dept.managerId);
  loadManagersForDepartment(dept.managerId);
};

// Load users into manager dropdown
let managersCache = [];
const loadManagersForDepartment = async (selectedId = null) => {
  console.log('[DEBUG] loadManagersForDepartment called, selectedId:', selectedId);
  console.log('[DEBUG] deptManager element:', deptManager);
  
  if (!deptManager) {
    console.error('[DEBUG] deptManager is null!');
    return;
  }
  
  deptManager.innerHTML = '<option value="">-- Chọn trưởng phòng --</option>';
  
  try {
    const response = await request('/api/users?page=0&size=100');
    console.log('[DEBUG] loadManagersForDepartment response:', response);
    
    // Handle both paginated response and direct array
    let users = [];
    if (Array.isArray(response)) {
      users = response;
    } else if (response && typeof response === 'object') {
      users = response.content || response.users || Object.values(response)[0] || [];
    }
    
    managersCache = users;
    console.log('[DEBUG] Parsed users count:', users.length);
    
    for (const user of managersCache) {
      // Skip ADMIN and GIAM_DOC roles from manager selection
      if (user.role === 'ADMIN' || user.role === 'GIAM_DOC') continue;
      
      const option = document.createElement('option');
      option.value = user.id;
      option.textContent = `${user.displayName || user.username} (${user.username})`;
      if (selectedId && user.id === selectedId) {
        option.selected = true;
        console.log('[DEBUG] Selected manager:', option.textContent);
      }
      deptManager.appendChild(option);
    }
    
    console.log('[DEBUG] deptManager options count:', deptManager.options.length);
  } catch (e) {
    console.error('Error loading managers:', e);
  }
};

// Open delete department confirmation modal
const openDeleteDeptModal = (dept) => {
  selectedDepartmentForDelete = dept;
  const userCount = dept.userCount || 0;
  
  deptDeleteWarning.textContent = `Bạn có chắc muốn xóa phòng ban "${dept.name}" (${dept.code})?`;
  
  if (userCount > 0) {
    deptDeleteAffected.innerHTML = `
      <strong>Cảnh báo:</strong> Phòng ban này có <strong>${userCount} nhân viên</strong>. 
      Khi xóa, tất cả nhân viên sẽ được gỡ khỏi phòng ban này (phòng ban = null).
    `;
    deptDeleteAffected.classList.remove('hidden');
  } else {
    deptDeleteAffected.classList.add('hidden');
  }
  
  deptDeleteModal.classList.remove('hidden');
  deptDeleteModal.setAttribute('aria-hidden', 'false');
};

// Close department modal
const closeDeptModalHandler = () => {
  // Blur focused element before hiding modal (accessibility)
  if (document.activeElement && deptModal.contains(document.activeElement)) {
    document.activeElement.blur();
  }
  deptModal.classList.add('hidden');
  deptModal.setAttribute('aria-hidden', 'true');
  selectedDepartment = null;
};

// Close delete confirmation modal
const closeDeptDeleteModalHandler = () => {
  deptDeleteModal.classList.add('hidden');
  deptDeleteModal.setAttribute('aria-hidden', 'true');
  selectedDepartmentForDelete = null;
};

// Handle department form submission
deptForm.addEventListener('submit', async (event) => {
  event.preventDefault();
  
  const code = deptCode.value.trim();
  const name = deptName.value.trim();
  const description = deptDescription.value.trim();
  const enabled = deptEnabled.checked;

  if (!code) {
    alert('Mã phòng ban là bắt buộc.');
    deptCode.focus();
    return;
  }
  if (!name) {
    alert('Tên phòng ban là bắt buộc.');
    deptName.focus();
    return;
  }

  try {
    const managerId = deptManager.value ? parseInt(deptManager.value) : null;
    
    if (selectedDepartment) {
      // Update existing department
      await request(`/api/departments/${selectedDepartment.id}`, {
        method: 'PUT',
        body: JSON.stringify({
          name: name,
          description: description,
          enabled: enabled,
          managerId: managerId
        }),
      });
      alert('Đã cập nhật phòng ban thành công!');
    } else {
      // Create new department
      await request('/api/departments', {
        method: 'POST',
        body: JSON.stringify({
          code: code,
          name: name,
          description: description,
          managerId: managerId
        }),
      });
      alert('Đã tạo phòng ban mới thành công!');
    }
    closeDeptModalHandler();
    await loadDepartmentsTable();
    // Refresh department dropdowns in user forms
    await loadDepartmentsForSelects();
  } catch (error) {
    alert('Lỗi: ' + error.message);
  }
});

// Handle delete confirmation
deptConfirmDelete.addEventListener('click', async () => {
  if (!selectedDepartmentForDelete) return;
  
  if (!confirm(`Xác nhận xóa phòng ban "${selectedDepartmentForDelete.name}"?`)) return;
  
  try {
    await request(`/api/departments/${selectedDepartmentForDelete.id}`, {
      method: 'DELETE',
    });
    alert('Đã xóa phòng ban thành công!');
    closeDeptDeleteModalHandler();
    await loadDepartments();
    // Refresh department dropdowns in user forms
    await loadDepartmentsForSelects();
  } catch (error) {
    alert('Lỗi: ' + error.message);
  }
});

// Load departments for select dropdowns
const loadDepartmentsForSelects = async () => {
  try {
    const depts = await request('/api/departments');
    // Populate admin dept select (for user creation)
    if (adminDeptSelect) {
      populateDepartmentSelect(adminDeptSelect, depts);
    }
    // Also update user detail modal dept select
    if (userDeptSelect) {
      populateDepartmentSelect(userDeptSelect, depts);
    }
  } catch (error) {
    console.error('Error loading departments for selects:', error);
  }
};

// Department modal event listeners
if (openDeptModal) {
  openDeptModal.addEventListener('click', openCreateDeptModal);
}
if (closeDeptModal) {
  closeDeptModal.addEventListener('click', closeDeptModalHandler);
}
if (deptCancelBtn) {
  deptCancelBtn.addEventListener('click', closeDeptModalHandler);
}
if (deptModal) {
  deptModal.addEventListener('click', (e) => {
    if (e.target === deptModal) {
      closeDeptModalHandler();
    }
  });
}

// Delete confirmation modal event listeners
if (closeDeptDeleteModal) {
  closeDeptDeleteModal.addEventListener('click', closeDeptDeleteModalHandler);
}
if (deptCancelDelete) {
  deptCancelDelete.addEventListener('click', closeDeptDeleteModalHandler);
}
if (deptDeleteModal) {
  deptDeleteModal.addEventListener('click', (e) => {
    if (e.target === deptDeleteModal) {
      closeDeptDeleteModalHandler();
    }
  });
}

adminCreateForm.addEventListener('submit', async (event) => {
  event.preventDefault();
  if (!validateForm(adminCreateForm)) {
    return;
  }
  const formData = new FormData(adminCreateForm);
  const payload = {
    username: formData.get('username'),
    password: formData.get('password'),
    role: formData.get('role'),
    displayName: formData.get('displayName'),
    title: formData.get('title'),
    email: formData.get('email'),
  };

  // Get departmentId - from form or from current user for TRUONG_PHONG
  const deptSelect = document.getElementById('admin-dept-select');
  if (isTruongPhong() && currentUser?.departmentId) {
    payload.departmentId = currentUser.departmentId;
  } else {
    payload.departmentId = formData.get('departmentId') || null;
  }

  // ADMIN cannot belong to a department
  if (payload.role === 'ADMIN') {
    payload.departmentId = null;
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
    // Reset department select disabled state
    if (deptSelect) {
      deptSelect.disabled = false;
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
    console.log('[DEBUG SAVE] Token:', getToken());
    console.log('[DEBUG SAVE] AvatarPreview:', profileAvatarPreview ? profileAvatarPreview.src : 'null');
    if (!validateProfileFields([
      { label: 'Tên hiển thị', input: profileDisplayName },
      { label: 'Chức danh', input: profileTitle },
      { label: 'Email', input: profileEmail },
    ])) {
      return;
    }
    const payload = {
      displayName: profileDisplayName.value,
      title: profileTitle.value,
      email: profileEmail.value,
      avatarUrl: profileAvatarPreview ? profileAvatarPreview.src : null,
    };
    console.log('[DEBUG SAVE] Payload:', JSON.stringify(payload));
    await request('/api/users/me/profile', {
      method: 'PATCH',
      body: JSON.stringify(payload),
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
initTabs();
initReportDates();
initAutoRefresh();
initTicketInfiniteScroll();
initAttachmentHandlers();
loadNotifications();
loadNotifications(); // Load saved notifications

if (isTokenValid()) {
  // Restore current user
  const stored = localStorage.getItem('ticketing.currentUser');
  if (stored) {
    try {
      currentUser = JSON.parse(stored);
    } catch (e) {}
  }
  
  // Load profile first to get currentUser with role/department
  loadProfile()
    .then(() => {
      // Then load engineer options using the loaded profile
      return loadEngineerOptions();
    })
    .then(() => {
      // Re-populate filter selects after engineer options are loaded
      if (filterAssignee) {
        populateAssigneeSelect(filterAssignee, {
          includeAll: true,
          includeUnassigned: true,
        });
      }
      // Load saved filter values AFTER dropdowns are populated
      loadTicketFilters();
      // Then load tickets with the saved filters
      loadTickets({ reset: true }).catch(() => {});
    })
    .catch(() => {});
  loadDashboard().catch(() => {});
  loadQueue().catch(() => {});
  if (canManageUsers()) {
    loadAdminUsers().catch(() => {});
    loadDepartments().catch(() => {});
  }
}

const themeKey = 'ticketing.theme';

// ==================== Theme ====================

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

// ============ Notification Event Listeners ============

if (notificationBtn) {
  notificationBtn.addEventListener('click', (e) => {
    e.stopPropagation();
    toggleNotificationDropdown();
  });
}

if (clearNotificationsBtn) {
  clearNotificationsBtn.addEventListener('click', (e) => {
    e.stopPropagation();
    clearAllNotifications();
  });
}

// Close dropdown when clicking outside
document.addEventListener('click', (e) => {
  const wrapper = document.querySelector('.notification-wrapper');
  if (notificationDropdown && !notificationDropdown.classList.contains('hidden')) {
    if (wrapper && !wrapper.contains(e.target)) {
      closeNotificationDropdown();
    }
  }
});
