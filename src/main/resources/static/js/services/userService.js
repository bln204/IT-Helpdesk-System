// ==================== User Service ====================
import { request } from '../core/api.js';
import { hasRole, isAdmin, isGiamDoc, isTruongPhong, isNhanVien, isITStaff, isInITDepartment } from '../core/auth.js';
import { formatRole, formatName, formatDateTime } from '../ui/utils.js';
import { engineerOptions as globalEngineerOptions } from '../config/constants.js';

// Initialize window.userPage to avoid undefined issues
if (typeof window.userPage === 'undefined') {
  window.userPage = 0;
}

// Prevent race conditions - flag to track if a load is in progress
let adminUsersLoading = false;

let adminUsers, userPage = 0;
const userPageSize = 10;
let selectedUser = null;
let userAuditPage = 0;
const userAuditPageSize = 5;
let userAuditEntries = [];
let userAvatarMap = new Map();
let userAvatarReady = false;
let pendingUsersCache = [];
let currentUser = null;

// Permission checks
export const canManageUsers = () => hasRole('ROLE_ADMIN') || hasRole('ROLE_GIAM_DOC') || hasRole('ROLE_TRUONG_PHONG');
export const canProcessTickets = () => hasRole('ROLE_ADMIN') || hasRole('ROLE_GIAM_DOC') || hasRole('ROLE_TRUONG_PHONG') || isITStaff();
export const canAssignTickets = () => hasRole('ROLE_ADMIN') || hasRole('ROLE_GIAM_DOC') || (hasRole('ROLE_TRUONG_PHONG') && currentUser?.departmentCode === 'IT');
export const canViewInternalComments = () => !isNhanVien() || isITStaff();
export const canAddInternalComments = () => isInITDepartment() || isAdmin() || isGiamDoc();
export const isStaff = () => !isNhanVien() || isITStaff();

export const getCurrentUser = () => currentUser;

export const updatePendingBadge = (count) => {
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

export const loadUserAvatarMap = async () => {
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
    
    const filterAssignee = document.getElementById('filter-assignee');
    const assigneeInput = document.getElementById('assignee-input');
    const assigneeCreate = document.getElementById('assignee-create');
    
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

export const getDisplayName = (value) => {
  if (!value) return '';
  const entry = userAvatarMap.get(value.toLowerCase());
  if (entry && entry.displayName) {
    return entry.displayName;
  }
  return formatName(value);
};

export const getUserAvatarMap = () => userAvatarMap;
export const setUserAvatarMap = (map) => { userAvatarMap = map; };
export const isUserAvatarReady = () => userAvatarReady;
export const setUserAvatarReady = (ready) => { userAvatarReady = ready; };

let engineerOptions = [];

export const populateAssigneeSelect = (
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
  engineerOptions.forEach((username) => {
    const displayName = getDisplayName(username);
    const option = document.createElement('option');
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

export const getEngineerOptions = () => engineerOptions;
export const setEngineerOptions = (options) => { engineerOptions = options; };

export const loadEngineerOptions = async () => {
  const { isTokenValid, hasRole, isAdmin, isGiamDoc, isTruongPhong, isNhanVien, isITStaff } = await import('../core/auth.js');
  if (!isTokenValid()) {
    console.log('loadEngineerOptions: Token invalid');
    return;
  }
  
  console.log('loadEngineerOptions: isAdmin():', isAdmin());
  console.log('loadEngineerOptions: isGiamDoc():', isGiamDoc());
  console.log('loadEngineerOptions: isTruongPhong():', isTruongPhong());
  console.log('loadEngineerOptions: isITStaff():', isITStaff());
  
  // Check if user is eligible to see engineer list
  const isEligibleRole = isAdmin() || isGiamDoc() || isTruongPhong() || isITStaff();
  if (!isEligibleRole) {
    console.log('loadEngineerOptions: Not eligible role');
    return;
  }
  
  try {
    const response = await request('/api/users/engineers');
    console.log('loadEngineerOptions: Raw response', response);
    const parsed = response.map(u => typeof u === 'string' ? u : u.username);
    console.log('loadEngineerOptions: Parsed engineers', parsed);
    
    // Update both local and global engineerOptions
    engineerOptions = parsed;
    globalEngineerOptions.length = 0;
    globalEngineerOptions.push(...parsed);
    console.log('loadEngineerOptions: Updated globalEngineerOptions', globalEngineerOptions.length);
    
    const filterAssignee = document.getElementById('filter-assignee');
    const assigneeInput = document.getElementById('assignee-input');
    const assigneeCreate = document.getElementById('assignee-create');
    
    populateAssigneeSelect(filterAssignee, {
      includeAll: true,
      includeUnassigned: true,
    });
    populateAssigneeSelect(assigneeInput, { includeUnassigned: true });
    populateAssigneeSelect(assigneeCreate, { includeUnassigned: true });
  } catch (error) {
    console.error('loadEngineerOptions: Error loading engineers', error);
    engineerOptions = [];
    globalEngineerOptions.length = 0;
  }
};

export const loadAdminUsers = async () => {
  if (!canManageUsers()) return;
  
  // Prevent race conditions - ignore if already loading
  if (adminUsersLoading) {
    console.log('[loadAdminUsers] Already loading, skipping...');
    return;
  }
  adminUsersLoading = true;
  
  try {
    adminUsers = document.getElementById('admin-users');
    const userSearch = document.getElementById('user-search');
    const usersPage = document.getElementById('users-page');
    const usersPrev = document.getElementById('users-prev');
    const usersNext = document.getElementById('users-next');
    
    const params = new URLSearchParams({
      page: String(window.userPage),  // Use window.userPage to sync with event handlers
      size: String(userPageSize),
    });
    if (userSearch && userSearch.value.trim()) {
      params.append('search', userSearch.value.trim());
    }
    const data = await request(`/api/users?${params.toString()}`);
    if (!adminUsers) return;
    adminUsers.innerHTML = '';

    // Load pending users section
    if (isAdmin() || isTruongPhong()) {
      await loadPendingUsers();
    }

    // Load delete requests section
    if (isAdmin() || isTruongPhong()) {
      await loadDeleteRequests();
    }

    // Load departments section
    if (isAdmin() || isGiamDoc()) {
      const { loadDepartmentsTable } = await import('./departmentService.js');
      await loadDepartmentsTable();
    }

    // Count pending users
    let pendingCount = 0;
    data.content.forEach((user) => {
      if (user.approved === false && !user.rejectionReason) {
        pendingCount++;
      }
    });

    data.content.forEach((user) => {
      const row = document.createElement('tr');
      const deptInfo = user.departmentCode ? ` (${user.departmentCode})` : ' (—)';

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

    updatePendingBadge(pendingCount);
  } finally {
    adminUsersLoading = false;
  }
};

export const loadPendingUsers = async () => {
  const pendingSection = document.getElementById('pending-users-section');
  const pendingTbody = document.getElementById('pending-users-tbody');
  const pendingCountBadge = document.getElementById('pending-count-badge');

  if (!pendingSection || !pendingTbody) return;

  try {
    const pendingUsers = await request('/api/admin/users/pending');
    const trulyPendingUsers = pendingUsers.filter(user => 
      user.approved === false && !user.rejectionReason
    );

    if (trulyPendingUsers.length === 0) {
      pendingSection.classList.add('hidden');
      updatePendingBadge(0);
      return;
    }

    pendingSection.classList.remove('hidden');
    pendingTbody.innerHTML = '';

    if (pendingCountBadge) {
      pendingCountBadge.textContent = `(${trulyPendingUsers.length})`;
    }
    updatePendingBadge(trulyPendingUsers.length);

    trulyPendingUsers.forEach((user) => {
      const row = document.createElement('tr');
      const deptInfo = user.departmentCode || '—';
      const createdAt = user.createdAt ? new Date(user.createdAt).toLocaleDateString('vi-VN') : '—';

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

      if (isAdmin()) {
        row.addEventListener('click', () => selectUser(user));
      }
      pendingTbody.appendChild(row);
    });

    pendingTbody.querySelectorAll('.btn-approve').forEach((btn) => {
      btn.addEventListener('click', async (e) => {
        e.stopPropagation();
        const userId = btn.dataset.userId;
        if (!confirm('Bạn có chắc muốn duyệt tài khoản này?')) return;
        try {
          await request(`/api/admin/users/${userId}/approve`, { method: 'POST', body: JSON.stringify({}) });
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
        
        const rejectModal = document.getElementById('reject-modal');
        const rejectModalUsername = document.getElementById('reject-modal-username');
        const rejectReasonInput = document.getElementById('reject-reason-input');
        
        if (rejectModal && rejectModalUsername && rejectReasonInput) {
          rejectModalUsername.textContent = `Tài khoản: ${userDisplayName}`;
          rejectReasonInput.value = '';
          rejectModal.classList.remove('hidden');
          rejectModal.setAttribute('aria-hidden', 'false');
          rejectReasonInput.focus();
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

export const loadDeleteRequests = async () => {
  const deleteRequestsSection = document.getElementById('delete-requests-section');
  const deleteRequestsTbody = document.getElementById('delete-requests-tbody');
  const deleteRequestsCountBadge = document.getElementById('delete-requests-count-badge');

  if (!deleteRequestsSection || !deleteRequestsTbody) return;

  try {
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

    deleteRequestsTbody.querySelectorAll('.btn-approve').forEach((btn) => {
      btn.addEventListener('click', async (e) => {
        e.stopPropagation();
        const userId = btn.dataset.deleteUserId;
        const userDisplayName = btn.closest('tr').querySelector('td:nth-child(2)')?.textContent || btn.closest('tr').querySelector('td:first-child')?.textContent;
        if (!confirm(`Bạn có chắc muốn xóa tài khoản "${userDisplayName}"?\n\nHành động này không thể hoàn tác.`)) return;
        try {
          await request(`/api/users/${userId}/delete-request`, { method: 'DELETE' });
          const { addNotification } = await import('./notificationService.js');
          addNotification('delete', 'Xóa tài khoản', `Tài khoản "${userDisplayName}" đã được xóa.`);
          alert('Đã xóa tài khoản thành công!');
          await loadDeleteRequests();
          await loadAdminUsers();
        } catch (error) {
          alert('Lỗi: ' + error.message);
        }
      });
    });

    deleteRequestsTbody.querySelectorAll('[data-cancel-request-id]').forEach((btn) => {
      btn.addEventListener('click', async (e) => {
        e.stopPropagation();
        const userId = btn.dataset.cancelRequestId;
        const userDisplayName = btn.closest('tr').querySelector('td:nth-child(2)')?.textContent || btn.closest('tr').querySelector('td:first-child')?.textContent;
        if (!confirm(`Bạn có chắc muốn hủy yêu cầu xóa tài khoản "${userDisplayName}"?`)) return;
        try {
          await request(`/api/admin/users/${userId}/cancel-delete-request`, { method: 'POST' });
          const { addNotification } = await import('./notificationService.js');
          addNotification('reject', 'Hủy yêu cầu xóa', `Yêu cầu xóa tài khoản "${userDisplayName}" đã bị hủy.`);
          alert('Đã hủy yêu cầu xóa!');
          await loadDeleteRequests();
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

export const selectUser = async (user) => {
  selectedUser = user;
  
  const userDeptSelect = document.getElementById('user-dept-select');
  if (userDeptSelect) {
    const depts = await request('/api/departments');
    populateDepartmentSelect(userDeptSelect, depts);
    if (user.departmentId) {
      userDeptSelect.value = user.departmentId;
    }
  }
  
  const userDetailModal = document.getElementById('user-detail-modal');
  const userDetailHeader = document.getElementById('user-detail-header');
  
  if (userDetailModal) {
    userDetailModal.classList.remove('hidden');
  }
  if (userDetailHeader) {
    const deptInfo = user.departmentName ? ` - ${user.departmentName}` : '';
    userDetailHeader.textContent = `${formatName(user.username)} (${formatRole(user.role)}${deptInfo})`;
  }
  
  const userDisplayName = document.getElementById('user-display-name');
  const userTitle = document.getElementById('user-title');
  const userEmail = document.getElementById('user-email');
  const userApprovalInfo = document.getElementById('user-approval-info');
  const userApprovalStatus = document.getElementById('user-approval-status');
  const userApprovalDate = document.getElementById('user-approval-date');
  const userApprovalBy = document.getElementById('user-approval-by');
  const userRejectionReason = document.getElementById('user-rejection-reason');
  const userApprovalActions = document.getElementById('user-approval-actions');
  const userRejectionForm = document.getElementById('user-rejection-form');

  if (userDisplayName) userDisplayName.value = user.displayName || '';
  if (userTitle) userTitle.value = user.title || '';
  if (userEmail) userEmail.value = user.email || '';

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
  const userRejectedWarning = document.getElementById('user-rejected-warning');
  if (userRejectedWarning) {
    userRejectedWarning.classList.add('hidden');
  }

  const isPending = !user.approved && !user.rejectionReason;
  const isRejected = !user.approved && user.rejectionReason;
  const isApproved = user.approved;
  const isDisabled = !user.enabled;

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
    if (isAdmin() && userApprovalActions) {
      userApprovalActions.classList.remove('hidden');
      userApprovalActions.style.display = 'flex';
    }
  } else if (isRejected) {
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
    if (userRejectedWarning) {
      userRejectedWarning.classList.remove('hidden');
    }
    if (userApprovalActions) userApprovalActions.classList.add('hidden');
    if (userRejectionForm) userRejectionForm.classList.add('hidden');
  } else if (isApproved) {
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
    if (userApprovalActions) userApprovalActions.classList.add('hidden');
    if (userRejectionForm) userRejectionForm.classList.add('hidden');
  } else if (isDisabled) {
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
  
  const userPasswordInput = document.getElementById('user-password-input');
  if (userPasswordInput) {
    userPasswordInput.value = '';
  }
  
  const userAudit = document.getElementById('user-audit');
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

export const canManageTargetUser = (user) => {
  if (!user) return false;
  if (isAdmin()) return true;
  if (isGiamDoc()) return user.role !== 'ADMIN';
  if (isTruongPhong()) {
    if (user.role !== 'NHAN_VIEN') return false;
    return user.departmentId === currentUser?.departmentId;
  }
  return false;
};

export const applyUserDetailControls = () => {
  if (!selectedUser) return;
  const canManage = canManageTargetUser(selectedUser);
  const isPending = !selectedUser.approved && !selectedUser.rejectionReason;
  const isRejectedUser = !selectedUser.approved && selectedUser.rejectionReason;
  const isApprovedUser = selectedUser.approved;

  let canEdit = false;
  let canResetPassword = false;
  let canDelete = false;
  
  if (isRejectedUser) {
    if (isAdmin()) {
      canDelete = selectedUser.role !== 'ADMIN';
    }
  } else if (isAdmin()) {
    if (selectedUser.role !== 'ADMIN') {
      canDelete = true;
    }
    canEdit = true;
    if (selectedUser.username !== currentUser?.username) {
      canResetPassword = true;
    }
  } else if (isGiamDoc()) {
    if (selectedUser.role !== 'ADMIN') {
      canEdit = true;
      canResetPassword = true;
      canDelete = true;
    }
  } else if (isTruongPhong()) {
    if (isApprovedUser && canManage) {
      canEdit = true;
    }
    if (selectedUser.role === 'NHAN_VIEN' && canManage) {
      canResetPassword = true;
    }
    if (selectedUser.role === 'NHAN_VIEN' && canManage) {
      canDelete = true;
    }
  }
  
  const userRoleSelect = document.getElementById('user-role-select');
  const userDeptSelect = document.getElementById('user-dept-select');
  const userDisplayName = document.getElementById('user-display-name');
  const userTitle = document.getElementById('user-title');
  const userEmail = document.getElementById('user-email');
  const userEnabledSelect = document.getElementById('user-enabled-select');
  const userPasswordInput = document.getElementById('user-password-input');
  const userSaveRole = document.getElementById('user-save-role');
  const userSavePassword = document.getElementById('user-save-password');
  const userSaveProfile = document.getElementById('user-save-profile');
  const userDelete = document.getElementById('user-delete');
  
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
    userRoleSelect.disabled = true;
  }

  if (userDeptSelect) {
    userDeptSelect.disabled = true;
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
    userSaveRole.disabled = true;
  }
  if (userSavePassword) {
    userSavePassword.disabled = !canResetPassword;
  }
  if (userSaveProfile) {
    userSaveProfile.disabled = !canEdit;
  }
  
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

export const renderUserAuditPage = () => {
  const userAudit = document.getElementById('user-audit');
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
  const userAuditPageLabel = document.getElementById('user-audit-page');
  const userAuditPrev = document.getElementById('user-audit-prev');
  const userAuditNext = document.getElementById('user-audit-next');
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

export const loadProfile = async () => {
  const profile = await request('/api/users/me');
  
  const profileDetails = document.getElementById('profile-details');
  const profileDisplayName = document.getElementById('profile-display-name');
  const profileTitle = document.getElementById('profile-title');
  const profileEmail = document.getElementById('profile-email');
  const profileAvatarPreview = document.getElementById('profile-avatar-preview');
  const profileAvatarFallback = document.getElementById('profile-avatar-fallback');
  const sidebarUsername = document.getElementById('sidebar-username');
  const logoMarkImg = document.getElementById('logo-mark-img');
  const logoMarkFallback = document.getElementById('logo-mark-fallback');
  
  if (profileDetails) {
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
  }
  
  if (profileDisplayName) profileDisplayName.value = profile.displayName || '';
  if (profileTitle) profileTitle.value = profile.title || '';
  if (profileEmail) profileEmail.value = profile.email || '';
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
  
  const { updateTokenStatus } = await import('../core/auth.js');
  updateTokenStatus();
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

export const applyRoleControls = () => {
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
    // Always enable buttons - let the user decide when to update
    // Backend will handle no-op updates gracefully
    if (updateStatusBtn) {
      updateStatusBtn.disabled = false;
    }
    if (updatePriorityBtn) {
      updatePriorityBtn.disabled = false;
    }
    if (updateAssigneeBtn) {
      updateAssigneeBtn.disabled = !canAssignTickets();
    }
    if (assignMeBtn) {
      assignMeBtn.disabled = !currentUser?.username || selectedTicket.assigneeName === currentUser.username || !canAssignTickets();
    }
  };
  
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
  
  if (commentVisibility) {
    if (!canViewInternalComments()) {
      commentVisibility.value = 'PUBLIC';
      commentVisibility.disabled = true;
    } else {
      commentVisibility.disabled = false;
    }
  }
  
  updateActionButtons();
};

export const applyAdminRoleOptions = () => {
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

const formatAuditAction = (action) => {
  if (!action) return '';
  const upper = String(action).toUpperCase();
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
  return AUDIT_ACTION_LABELS[upper] || formatName(action.replace(/_/g, ' '));
};

export const getUserPage = () => window.userPage;
export const setUserPage = (page) => { window.userPage = page; };
export const getSelectedUser = () => selectedUser;
export const setSelectedUser = (user) => { selectedUser = user; };
export const getUserAuditPage = () => userAuditPage;
export const setUserAuditPage = (page) => { userAuditPage = page; };
