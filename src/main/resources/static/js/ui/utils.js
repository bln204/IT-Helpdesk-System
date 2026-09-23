// ==================== UI Utilities Module ====================
import { hasRole, isAdmin, isGiamDoc, isTruongPhong, isNhanVien, isITStaff, isInITDepartment } from '../core/auth.js';
import { engineerOptions as globalEngineerOptions } from '../config/constants.js';

// ==================== Status and Format Labels ====================
export const STATUS_LABELS = {
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

export const ROLE_LABELS = {
  ADMIN: 'Quản trị viên',
  GIAM_DOC: 'Giám đốc',
  TRUONG_PHONG: 'Trưởng phòng',
  NHAN_VIEN: 'Nhân viên',
};

export const AUDIT_ACTION_LABELS = {
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

// ==================== Name Formatting ====================
export const formatName = (value) => {
  if (!value) return '';
  return value
    .split(/[\s_-]+/)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1).toLowerCase())
    .join(' ');
};

export const formatRole = (role) => {
  if (!role) return '';
  const clean = role.replace('ROLE_', '').toUpperCase();
  return ROLE_LABELS[clean] || formatName(clean);
};

export const formatAuditAction = (action) => {
  if (!action) return '';
  const upper = String(action).toUpperCase();
  return AUDIT_ACTION_LABELS[upper] || formatName(action.replace(/_/g, ' '));
};

export const formatFieldName = (field) => {
  if (!field) return '';
  const map = {
    status: 'trạng thái',
    priority: 'độ ưu tiên',
    assignee: 'người phân công',
    comment: 'bình luận',
  };
  return map[field.toLowerCase()] || field;
};

export const formatAuditValue = (field, value) => {
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

export const formatSlaBucket = (bucket) => {
  if (!bucket) return '';
  return bucket.replace(/days/g, 'ngày');
};

export const formatStatus = (value) => {
  if (!value) return '';
  const upper = String(value).toUpperCase();
  if (STATUS_LABELS[upper]) {
    return STATUS_LABELS[upper];
  }
  return formatName(value.replace(/_/g, ' '));
};

// ==================== Date/Time Formatting ====================
export const formatDateTime = (dateString) => {
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

export const toIsoDateTime = (value, endOfDay = false) => {
  if (!value) return '';
  if (value.includes('T')) return value;
  return `${value}T${endOfDay ? '23:59:59' : '00:00:00'}`;
};

export const formatHours = (value) => Number.isFinite(value) ? value.toFixed(2) : '0.00';

// ==================== Permission Checks ====================
export const canManageUsers = () => hasRole('ROLE_ADMIN') || hasRole('ROLE_GIAM_DOC') || hasRole('ROLE_TRUONG_PHONG');
export const canProcessTickets = () => hasRole('ROLE_ADMIN') || hasRole('ROLE_GIAM_DOC') || hasRole('ROLE_TRUONG_PHONG') || isITStaff();
export const canAssignTickets = () => hasRole('ROLE_ADMIN') || hasRole('ROLE_GIAM_DOC') || (hasRole('ROLE_TRUONG_PHONG') && isInITDepartment());
export const canViewInternalComments = () => !isNhanVien() || isITStaff();
export const canAddInternalComments = () => isInITDepartment() || isAdmin() || isGiamDoc();
export const isStaff = () => !isNhanVien() || isITStaff();

// ==================== Form Validation ====================
export const validateProfileFields = (fields) => {
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

export const setFieldError = (input, message) => {
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

export const clearFieldError = (input) => {
  if (!input) return;
  const label = input.closest('label');
  if (!label) return;
  label.classList.remove('invalid');
  const error = label.querySelector('.field-error');
  if (error) {
    error.remove();
  }
};

export const clearFormErrors = (form) => {
  if (!form) return;
  form.querySelectorAll('.field-error').forEach((node) => node.remove());
  form.querySelectorAll('label.invalid').forEach((label) => label.classList.remove('invalid'));
};

export const validateForm = (form) => {
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

export const attachValidationHandlers = (container) => {
  if (!container) return;
  const inputs = container.querySelectorAll('input, select, textarea');
  inputs.forEach((input) => {
    const handler = () => clearFieldError(input);
    input.addEventListener('input', handler);
    input.addEventListener('change', handler);
  });
};

// ==================== Select Utilities ====================
export const setSelectValue = (select, value) => {
  if (!select) return;
  const options = Array.from(select.options);
  const match = options.some((option) => option.value === value);
  select.value = match ? value : options[0]?.value || '';
};

export const normalizeAssigneeInput = (value) => {
  const UNASSIGNED_VALUE = 'UNASSIGNED';
  if (!value || value === UNASSIGNED_VALUE) {
    return null;
  }
  return value;
};

export const ensureAssigneeOption = (select, value) => {
  if (!select || !value) return;
  const exists = Array.from(select.options).some((option) => option.value === value);
  if (!exists) {
    const option = document.createElement('option');
    option.value = value;
    option.textContent = formatName(value);
    select.appendChild(option);
  }
};

export const populateAssigneeSelect = async (
  select,
  { includeAll = false, includeUnassigned = false } = {}
) => {
  if (!select) return;
  const UNASSIGNED_VALUE = 'UNASSIGNED';
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
  // Get global engineerOptions from constants
  const { engineerOptions } = await import('../config/constants.js');
  console.log('populateAssigneeSelect: Using global engineerOptions:', engineerOptions.length);
  engineerOptions.forEach((username) => {
    const option = document.createElement('option');
    option.value = username;
    option.textContent = formatName(username);
    option.dataset.username = username;
    select.appendChild(option);
  });
  if (current) {
    select.value = current;
  }
};

// ==================== User Avatar Map ====================
let userAvatarMap = new Map();
let userAvatarReady = false;

export const getUserAvatarMap = () => userAvatarMap;
export const setUserAvatarMap = (map) => { userAvatarMap = map; };
export const isUserAvatarReady = () => userAvatarReady;
export const setUserAvatarReady = (ready) => { userAvatarReady = ready; };

export const getDisplayName = (value) => {
  if (!value) return '';
  const entry = userAvatarMap.get(value.toLowerCase());
  if (entry && entry.displayName) {
    return entry.displayName;
  }
  return formatName(value);
};

// ==================== Auto Refresh ====================
const autoRefreshKey = 'ticketing.autoRefresh';
const autoRefreshMs = 60000;
let autoRefreshTimer = null;

export const stopAutoRefresh = () => {
  if (autoRefreshTimer) {
    clearInterval(autoRefreshTimer);
    autoRefreshTimer = null;
  }
};

export const startAutoRefresh = (loadTicketsFn) => {
  stopAutoRefresh();
  autoRefreshTimer = setInterval(() => {
    if (window.location.hash.includes('tickets')) {
      loadTicketsFn({ reset: true }).catch(() => {});
    }
  }, autoRefreshMs);
};

export const initAutoRefresh = (loadTicketsFn) => {
  const autoRefreshToggle = document.getElementById('auto-refresh-toggle');
  if (!autoRefreshToggle) return;
  const saved = localStorage.getItem(autoRefreshKey);
  const enabled = saved === 'true';
  autoRefreshToggle.checked = enabled;
  if (enabled) {
    startAutoRefresh(loadTicketsFn);
  }
  autoRefreshToggle.addEventListener('change', () => {
    const isOn = autoRefreshToggle.checked;
    localStorage.setItem(autoRefreshKey, isOn ? 'true' : 'false');
    if (isOn) {
      startAutoRefresh(loadTicketsFn);
    } else {
      stopAutoRefresh();
    }
  });
};

// ==================== Ticket Infinite Scroll ====================
export const initTicketInfiniteScroll = (loadTicketsFn) => {
  const ticketsSentinel = document.getElementById('tickets-sentinel');
  if (!ticketsSentinel) return;
  const observer = new IntersectionObserver((entries) => {
    entries.forEach((entry) => {
      if (entry.isIntersecting) {
        loadTicketsFn().catch(() => {});
      }
    });
  }, { root: document.querySelector('.app-main'), threshold: 0.1 });
  observer.observe(ticketsSentinel);
};

// ==================== Ticket Filters ====================
const filterKey = 'ticketing.ticketFilters';

export const saveTicketFilters = () => {
  const filterAssignee = document.getElementById('filter-assignee');
  const filterStatus = document.getElementById('filter-status');
  const filterSort = document.getElementById('filter-sort');
  const filterSearch = document.getElementById('filter-search');
  if (!filterAssignee || !filterStatus || !filterSort || !filterSearch) return;
  const payload = {
    assignee: filterAssignee.value,
    status: filterStatus.value,
    sort: filterSort.value,
    search: filterSearch.value,
  };
  localStorage.setItem(filterKey, JSON.stringify(payload));
};

export const loadTicketFilters = () => {
  const filterAssignee = document.getElementById('filter-assignee');
  const filterStatus = document.getElementById('filter-status');
  const filterSort = document.getElementById('filter-sort');
  const filterSearch = document.getElementById('filter-search');
  if (!filterAssignee || !filterStatus || !filterSort || !filterSearch) return;
  const raw = localStorage.getItem(filterKey);
  if (!raw) return;
  try {
    const payload = JSON.parse(raw);
    if (payload.assignee !== undefined) filterAssignee.value = payload.assignee;
    if (payload.status !== undefined) filterStatus.value = payload.status;
    if (payload.sort !== undefined) filterSort.value = payload.sort;
    if (payload.search !== undefined) filterSearch.value = payload.search;
  } catch (error) {
    localStorage.removeItem(filterKey);
  }
};

// ==================== Report Date Initialization ====================
export const initReportDates = () => {
  const reportFrom = document.getElementById('report-from');
  const reportTo = document.getElementById('report-to');
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

// ==================== Build Report Params ====================
export const buildReportParams = () => {
  const reportFrom = document.getElementById('report-from');
  const reportTo = document.getElementById('report-to');
  const params = new URLSearchParams();
  const fromValue = toIsoDateTime(reportFrom.value, false);
  const toValue = toIsoDateTime(reportTo.value, true);
  if (fromValue) params.append('from', fromValue);
  if (toValue) params.append('to', toValue);
  return params.toString();
};

export const buildDashboardParams = () => {
  const end = new Date();
  const start = new Date();
  start.setDate(end.getDate() - 30);
  const params = new URLSearchParams();
  params.append('from', start.toISOString());
  params.append('to', end.toISOString());
  return params.toString();
};

// ==================== Theme ====================
const themeKey = 'ticketing.theme';

export const applyTheme = (theme) => {
  document.body.classList.toggle('dark', theme === 'dark');
  const themeToggle = document.getElementById('theme-toggle');
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

export const initTheme = () => {
  const savedTheme = localStorage.getItem(themeKey);
  applyTheme(savedTheme || 'light');
  const themeToggle = document.getElementById('theme-toggle');
  if (themeToggle) {
    themeToggle.addEventListener('click', () => {
      const next = document.body.classList.contains('dark') ? 'light' : 'dark';
      localStorage.setItem(themeKey, next);
      applyTheme(next);
    });
  }
};

// ==================== File Utilities ====================
export const getFileIcon = (contentType) => {
  if (!contentType) return '📄';
  if (contentType.startsWith('image/')) return '🖼️';
  if (contentType === 'application/pdf') return '📕';
  if (contentType === 'text/plain') return '📝';
  return '📎';
};

export const formatFileSize = (bytes) => {
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

// ==================== Table Rendering ====================
export const renderTable = (columns, rows) => {
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

// ==================== Tabs Initialization ====================
export const initTabs = () => {
  document.querySelectorAll('.tab-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      const tab = btn.dataset.tab;
      if (!tab) return;
      
      // Update active tab
      document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      
      // Show/hide content
      document.querySelectorAll('.tab-content').forEach(c => c.classList.add('hidden'));
      const tabContent = document.getElementById(`tab-${tab}`);
      if (tabContent) tabContent.classList.remove('hidden');
      
      // Load data for tab
      if (tab === 'departments') {
        import('../services/departmentService.js').then(module => {
          module.loadDepartmentsTable();
        });
      }
    });
  });
};
