// ==================== Global Configuration ====================
export const API_BASE = '';
export const tokenKey = 'ticketing.jwt';
export const UNASSIGNED_VALUE = 'UNASSIGNED';

// Demo credentials for prefill functionality
export const DEMO_CREDENTIALS = {
  admin: { username: 'admin', password: '12345678' },
  giamDoc: { username: 'mayagiamdoc', password: '12345678' },
  truongPhongIT: { username: 'tp.it', password: '12345678' },
  nhanVien: { username: 'tech.smith', password: '12345678' }
};

// ==================== State Variables ====================
// Current user info (from login response)
export let currentUser = null;
export let notifications = [];
export let pendingUsersCache = []; // Cache for pending users count
export let notificationPollingTimer = null;
export const NOTIFICATION_POLLING_MS = 10000; // Poll every 10 seconds

// ==================== DOM Element References ====================
// Login
export const loginForm = document.getElementById('login-form');
export const loginError = document.getElementById('login-error');
export const loginPrefillNote = document.getElementById('login-prefill-note');

// Notifications
export const notificationBtn = document.getElementById('notification-btn');
export const notificationBadge = document.getElementById('notification-badge');
export const notificationDropdown = document.getElementById('notification-dropdown');
export const notificationList = document.getElementById('notification-list');
export const clearNotificationsBtn = document.getElementById('clear-notifications');

// Navigation & Layout
export const logoutBtn = document.getElementById('logout-btn');
export const tokenStatus = document.getElementById('token-status');
export const sidebarUsername = document.getElementById('sidebar-username');
export const sidebarRole = document.getElementById('sidebar-role');
export const sidebarDept = document.getElementById('sidebar-dept');
export const logoMark = document.getElementById('logo-mark');
export const logoMarkImg = document.getElementById('logo-mark-img');
export const logoMarkFallback = document.getElementById('logo-mark-fallback');
export const userDisplay = document.getElementById('user-display');
export const themeToggle = document.getElementById('theme-toggle');
export const apiStatus = document.getElementById('api-status');
export const navCard = document.querySelector('.nav-card');
export const appShell = document.getElementById('app-shell');
export const navToggle = document.getElementById('nav-toggle');

export const navLinks = Array.from(document.querySelectorAll('.nav-card a'));
export const views = Array.from(document.querySelectorAll('.view'));

// Tickets
export const createForm = document.getElementById('create-form');
export const assigneeCreate = document.getElementById('assignee-create');
export const ticketsList = document.getElementById('tickets-list');
export const refreshBtn = document.getElementById('refresh-btn');
export const clearFiltersBtn = document.getElementById('clear-filters-btn');
export const autoRefreshToggle = document.getElementById('auto-refresh-toggle');
export const refreshStatus = document.getElementById('refresh-status');
export const filterSearch = document.getElementById('filter-search');
export const filterAssignee = document.getElementById('filter-assignee');
export const filterStatus = document.getElementById('filter-status');
export const filterSort = document.getElementById('filter-sort');
export const openCreateModal = document.getElementById('open-create-modal');
export const closeCreateModal = document.getElementById('close-create-modal');
export const createModal = document.getElementById('create-modal');
export const ticketBoard = document.getElementById('ticket-board');
export const ticketsSentinel = document.getElementById('tickets-sentinel');

// Ticket Detail
export const ticketDetails = document.getElementById('ticket-details');
export const ticketActions = document.getElementById('ticket-actions');
export const statusSelect = document.getElementById('status-select');
export const prioritySelect = document.getElementById('priority-select');
export const assigneeInput = document.getElementById('assignee-input');
export const closeDetails = document.getElementById('close-details');
export const ticketDetailsPanel = document.getElementById('ticket-details-panel');
export const ticketTabButtons = Array.from(document.querySelectorAll('.tab-btn'));
export const ticketTabPanels = Array.from(document.querySelectorAll('[data-tab-panel]'));
export const updateStatusBtn = document.getElementById('update-status-btn');
export const updatePriorityBtn = document.getElementById('update-priority-btn');
export const updateAssigneeBtn = document.getElementById('update-assignee-btn');
export const auditDownloadBtn = document.getElementById('audit-download-btn');
export const assignMeBtn = document.getElementById('assign-me-btn');
export const commentPanel = document.getElementById('comment-panel');
export const commentVisibility = document.getElementById('comment-visibility');
export const commentBody = document.getElementById('comment-body');
export const addCommentBtn = document.getElementById('add-comment-btn');
export const commentsList = document.getElementById('comments-list');
export const auditList = document.getElementById('audit-list');
export const assignmentsList = document.getElementById('assignments-list');
export const attachmentsList = document.getElementById('ticket-attachments-list');
export const attachmentInput = document.getElementById('attachment-input');
export const selectedFilesList = document.getElementById('selected-files');
export const uploadAttachmentBtn = document.getElementById('upload-attachment-btn');
export const ticketAttachmentInput = document.getElementById('ticket-attachment-input');
export const attachmentUploadSection = document.getElementById('attachment-upload-section');
export const ticketFileUploadArea = document.getElementById('ticket-file-upload-area');
export const ticketSelectedFiles = document.getElementById('ticket-selected-files');

// File Selection State
export let selectedFiles = [];
export let ticketSelectedFilesList = [];

// Reports
export const reportFrom = document.getElementById('report-from');
export const reportTo = document.getElementById('report-to');
export const engineerReportBtn = document.getElementById('engineer-report-btn');
export const requesterReportBtn = document.getElementById('requester-report-btn');
export const backlogReportBtn = document.getElementById('backlog-report-btn');
export const slaReportBtn = document.getElementById('sla-report-btn');
export const reportOutput = document.getElementById('report-output');
export const reportDownloadBtn = document.getElementById('report-download-btn');
export const reportButtons = [engineerReportBtn, requesterReportBtn, backlogReportBtn, slaReportBtn].filter(Boolean);

// Admin - Users
export const adminCreateForm = document.getElementById('admin-create-form');
export const adminRoleSelect = document.getElementById('admin-role-select');
export const adminDeptSelect = document.getElementById('admin-dept-select');
export const adminUsers = document.getElementById('admin-users');
export const userSearch = document.getElementById('user-search');
export const userSearchBtn = document.getElementById('user-search-btn');
export const usersPrev = document.getElementById('users-prev');
export const usersNext = document.getElementById('users-next');
export const usersPage = document.getElementById('users-page');

// Admin - User Detail Modal
export const userDetailModal = document.getElementById('user-detail-modal');
export const userDetailHeader = document.getElementById('user-detail-header');
export const userDisplayName = document.getElementById('user-display-name');
export const userTitle = document.getElementById('user-title');
export const userEmail = document.getElementById('user-email');
export const userRoleSelect = document.getElementById('user-role-select');
export const userDeptSelect = document.getElementById('user-dept-select');
export const userEnabledSelect = document.getElementById('user-enabled-select');
export const userPasswordInput = document.getElementById('user-password-input');
export const userSaveRole = document.getElementById('user-save-role');
export const userSavePassword = document.getElementById('user-save-password');
export const userSaveProfile = document.getElementById('user-save-profile');
export const userDelete = document.getElementById('user-delete');
export const userAudit = document.getElementById('user-audit');
export const userAuditPrev = document.getElementById('user-audit-prev');
export const userAuditNext = document.getElementById('user-audit-next');
export const userAuditPageLabel = document.getElementById('user-audit-page');
export const openUserModal = document.getElementById('open-user-modal');
export const closeUserModal = document.getElementById('close-user-modal');
export const userCreateModal = document.getElementById('user-create-modal');
export const closeUserDetail = document.getElementById('close-user-detail');
export const rejectModal = document.getElementById('reject-modal');
export const rejectModalClose = document.getElementById('reject-modal-close');
export const rejectModalConfirm = document.getElementById('reject-modal-confirm');
export const rejectModalCancel = document.getElementById('reject-modal-cancel');
export const rejectReasonInput = document.getElementById('reject-reason-input');

// Profile
export const profileDetails = document.getElementById('profile-details');
export const profilePasswordForm = document.getElementById('profile-password-form');
export const profileDisplayName = document.getElementById('profile-display-name');
export const profileTitle = document.getElementById('profile-title');
export const profileEmail = document.getElementById('profile-email');
export const profileSaveBtn = document.getElementById('profile-save-btn');
export const profileAvatar = document.getElementById('profile-avatar');
export const profileAvatarPreview = document.getElementById('profile-avatar-preview');
export const profileAvatarFallback = document.getElementById('profile-avatar-fallback');
export const profileCard = document.getElementById('profile-card');

// Departments
export const departmentsTbody = document.getElementById('departments-tbody');
export const openDeptModal = document.getElementById('open-dept-modal');
export const deptModal = document.getElementById('dept-modal');
export const closeDeptModal = document.getElementById('close-dept-modal');
export const deptForm = document.getElementById('dept-form');
export const deptModalTitle = document.getElementById('dept-modal-title');
export const deptModalDesc = document.getElementById('dept-modal-desc');
export const deptId = document.getElementById('dept-id');
export const deptCode = document.getElementById('dept-code');
export const deptName = document.getElementById('dept-name');
export const deptDescription = document.getElementById('dept-description');
export const deptEnabled = document.getElementById('dept-enabled');
export const deptManager = document.getElementById('dept-manager');
export const deptSubmitBtn = document.getElementById('dept-submit-btn');
export const deptCancelBtn = document.getElementById('dept-cancel-btn');
export const deptDeleteModal = document.getElementById('dept-delete-modal');
export const closeDeptDeleteModal = document.getElementById('close-dept-delete-modal');
export const deptDeleteWarning = document.getElementById('dept-delete-warning');
export const deptDeleteAffected = document.getElementById('dept-delete-affected');
export const deptConfirmDelete = document.getElementById('dept-confirm-delete');
export const deptCancelDelete = document.getElementById('dept-cancel-delete');

// State Variables for User Management
export let userPage = 0;
export const userPageSize = 10;
export let selectedUser = null;
export let userAuditPage = 0;
export const userAuditPageSize = 5;
export let userAuditEntries = [];
export let userAvatarMap = new Map();
export let userAvatarReady = false;

// State Variables for Department Management
export let selectedDepartment = null;
export let selectedDepartmentForDelete = null;
export let managersCache = [];

// State Variables for Ticket Management
export let selectedTicket = null;
export let ticketPage = 0;
export const ticketPageSize = 20;
export let ticketHasMore = true;
export let ticketLoading = false;
export let ticketsCache = [];
export let currentReport = null;
export let autoRefreshTimer = null;
export const autoRefreshKey = 'ticketing.autoRefresh';
export const autoRefreshMs = 60000;

// State Variables for Engineer Options
export let engineerOptions = [];

// Tab State
export let currentAdminTab = 'users';

// Theme
export const themeKey = 'ticketing.theme';
