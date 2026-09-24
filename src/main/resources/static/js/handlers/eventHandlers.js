// ==================== Event Handlers Module ====================
import { request } from '../core/api.js';
import { login, updateTokenStatus, updateNavVisibility, DEMO_CREDENTIALS, setToken } from '../core/auth.js';
import { routeGuard, setRoute } from '../core/router.js';
import {
  loadNotifications,
  startNotificationPolling,
  stopNotificationPolling,
  toggleNotificationDropdown,
  clearAllNotifications,
  addNotification
} from '../services/notificationService.js';
import {
  loadTickets,
  selectTicket,
  updateStatus,
  updatePriority,
  updateAssignee,
  assignToMe,
  addComment,
  downloadAuditCsv,
  uploadAttachments,
  loadDashboard,
  loadQueue,
  getSelectedTicket,
  getSelectedFiles,
  setSelectedFiles,
  getTicketSelectedFilesList,
  setTicketSelectedFilesList,
  getFileIcon,
  formatFileSize
} from '../services/ticketService.js';
import {
  loadAdminUsers,
  loadProfile,
  loadUserAvatarMap,
  loadEngineerOptions,
  applyRoleControls,
  applyAdminRoleOptions,
  canManageUsers,
  renderUserAuditPage
} from '../services/userService.js';
import {
  loadDepartments,
  loadDepartmentsTable,
  openCreateDeptModal,
  closeDeptModalHandler,
  closeDeptDeleteModalHandler,
  loadDepartmentsForSelects,
  openDeleteDeptModal,
  selectedDepartment,
  selectedDepartmentForDelete
} from '../services/departmentService.js';
import {
  validateForm,
  validateProfileFields,
  setFieldError,
  clearFieldError,
  attachValidationHandlers,
  formatName,
  formatRole,
  saveTicketFilters,
  stopAutoRefresh,
  startAutoRefresh,
  initAutoRefresh,
  initTicketInfiniteScroll
} from '../ui/utils.js';
import {
  loadEngineerReport,
  loadRequesterReport,
  loadBacklogReport,
  loadSlaReport
} from '../services/ticketService.js';
import {
  downloadReportCsv,
  renderReportTable,
  setActiveReportButton
} from '../modules/reports.js';

const UNASSIGNED_VALUE = 'UNASSIGNED';
let selectedFiles = [];
let ticketSelectedFilesList = [];

export const attachEventHandlers = () => {
  // Login Form
  const loginForm = document.getElementById('login-form');
  const loginError = document.getElementById('login-error');
  const logoutBtn = document.getElementById('logout-btn');
  const notificationBtn = document.getElementById('notification-btn');
  const clearNotificationsBtn = document.getElementById('clear-notifications');
  
  if (loginForm) {
    loginForm.addEventListener('submit', async (event) => {
      event.preventDefault();
      if (!validateForm(loginForm)) return;
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
            `;
            loginError.classList.remove('hidden');
          }
          return;
        }
        if (errorCode === 'AUTH_USER_REJECTED') {
          let reasonText = '';
          if (error.details && error.details.rejectionReason) {
            reasonText = `<div class="login-error-reason">Lý do: ${error.details.rejectionReason}</div>`;
          }
          if (loginError) {
            loginError.innerHTML = `
              <div class="login-error-icon">❌</div>
              <div class="login-error-title">Tài khoản đã bị từ chối</div>
              <div class="login-error-message">
                Tài khoản của bạn đã bị từ chối trong quá trình phê duyệt.
                Vui lòng liên hệ <strong>Admin</strong> để biết thêm chi tiết.
              </div>
              ${reasonText}
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
        if (loginError) {
          loginError.textContent = error.message;
          loginError.classList.remove('hidden');
        } else {
          alert(error.message);
        }
      }
    });
  }
  
  // Logout
  if (logoutBtn) {
    logoutBtn.addEventListener('click', () => {
      setToken(null);
      window.currentUser = null;
      localStorage.removeItem('ticketing.currentUser');
      stopNotificationPolling();
      window.notifications = [];
      // Import and call updateNotificationBadge
      import('../services/notificationService.js').then(module => {
        module.updateNotificationBadge(0);
      });
      import('../core/auth.js').then(module => {
        module.showView('login');
      });
      if (loginError) {
        loginError.classList.add('hidden');
        loginError.textContent = '';
      }
    });
  }
  
  // Create Ticket Form
  const createForm = document.getElementById('create-form');
  const createModal = document.getElementById('create-modal');
  const selectedFilesList = document.getElementById('selected-files');
  selectedFiles = [];
  
  if (createForm) {
    createForm.addEventListener('submit', async (event) => {
      event.preventDefault();
      if (!validateForm(createForm)) return;
      const formData = new FormData(createForm);
      const payload = Object.fromEntries(formData.entries());
      payload.assigneeName = payload.assigneeName === UNASSIGNED_VALUE ? null : payload.assigneeName;
      try {
        const result = await request('/api/tickets', {
          method: 'POST',
          body: JSON.stringify(payload),
        });
        const filesToUpload = [...selectedFiles];
        createForm.reset();
        selectedFiles = [];
        if (selectedFilesList) selectedFilesList.innerHTML = '';
        if (filesToUpload.length > 0) {
          await uploadAttachments(result.id, filesToUpload);
        }
        await loadTickets({ reset: true });
        if (createModal) createModal.classList.add('hidden');
      } catch (error) {
        alert(error.message);
      }
    });
  }
  
  // Refresh Button
  const refreshBtn = document.getElementById('refresh-btn');
  if (refreshBtn) {
    refreshBtn.addEventListener('click', () => {
      loadTickets({ reset: true }).catch((error) => alert(error.message));
      loadDashboard().catch((error) => alert(error.message));
      loadQueue().catch((error) => alert(error.message));
    });
  }
  
  // Clear Filters Button
  const clearFiltersBtn = document.getElementById('clear-filters-btn');
  if (clearFiltersBtn) {
    clearFiltersBtn.addEventListener('click', () => {
      window.ticketPage = 0;
      const filterAssignee = document.getElementById('filter-assignee');
      const filterStatus = document.getElementById('filter-status');
      const filterSort = document.getElementById('filter-sort');
      const filterSearch = document.getElementById('filter-search');
      if (filterAssignee) filterAssignee.value = '';
      if (filterStatus) filterStatus.value = '';
      if (filterSort) filterSort.value = 'createdAt,desc';
      if (filterSearch) filterSearch.value = '';
      saveTicketFilters();
      loadTickets({ reset: true }).catch((error) => alert(error.message));
    });
  }
  
  // Filter Selects
  const filterAssignee = document.getElementById('filter-assignee');
  const filterStatus = document.getElementById('filter-status');
  const filterSort = document.getElementById('filter-sort');
  const filterSearch = document.getElementById('filter-search');
  
  if (filterAssignee) {
    filterAssignee.addEventListener('change', () => {
      window.ticketPage = 0;
      saveTicketFilters();
      loadTickets({ reset: true }).catch((error) => alert(error.message));
    });
  }
  if (filterStatus) {
    filterStatus.addEventListener('change', () => {
      window.ticketPage = 0;
      saveTicketFilters();
      loadTickets({ reset: true }).catch((error) => alert(error.message));
    });
  }
  if (filterSort) {
    filterSort.addEventListener('change', () => {
      window.ticketPage = 0;
      saveTicketFilters();
      loadTickets({ reset: true }).catch((error) => alert(error.message));
    });
  }
  if (filterSearch) {
    let searchTimer = null;
    filterSearch.addEventListener('input', () => {
      if (searchTimer) clearTimeout(searchTimer);
      searchTimer = setTimeout(() => {
        window.ticketPage = 0;
        saveTicketFilters();
        loadTickets({ reset: true }).catch((error) => alert(error.message));
      }, 300);
    });
  }
  
  // Create Modal
  const openCreateModal = document.getElementById('open-create-modal');
  const closeCreateModal = document.getElementById('close-create-modal');
  
  if (openCreateModal) {
    openCreateModal.addEventListener('click', () => {
      if (createModal) createModal.classList.remove('hidden');
    });
  }
  if (closeCreateModal) {
    closeCreateModal.addEventListener('click', () => {
      if (createModal) createModal.classList.add('hidden');
    });
  }
  
  // Ticket Detail Close
  const closeDetails = document.getElementById('close-details');
  const ticketDetailsPanel = document.getElementById('ticket-details-panel');
  const ticketBoard = document.getElementById('ticket-board');
  
  if (closeDetails) {
    closeDetails.addEventListener('click', async () => {
      if (ticketDetailsPanel) ticketDetailsPanel.classList.add('hidden');
      if (ticketBoard) ticketBoard.classList.remove('has-detail');
      // Refresh ticket list to show any updates
      const { loadTickets } = await import('../services/ticketService.js');
      await loadTickets({ reset: true }).catch((err) => console.error('[closeDetails] loadTickets error:', err));
      // Clear selected ticket
      window.selectedTicket = null;
      // Reset detail panel to empty state
      const ticketDetails = document.getElementById('ticket-details');
      const ticketActions = document.getElementById('ticket-actions');
      if (ticketDetails) {
        ticketDetails.classList.remove('hidden');
        ticketDetails.innerHTML = 'Chọn một phiếu hỗ trợ để xem chi tiết.';
      }
      if (ticketActions) ticketActions.classList.add('hidden');
    });
  }
  
  // Ticket Tabs
  const ticketTabButtons = Array.from(document.querySelectorAll('.tab-btn'));
  const ticketTabPanels = Array.from(document.querySelectorAll('[data-tab-panel]'));
  
  ticketTabButtons.forEach((btn) => {
    btn.addEventListener('click', () => {
      const target = btn.dataset.tab;
      ticketTabButtons.forEach((item) => item.classList.toggle('active', item === btn));
      ticketTabPanels.forEach((panel) => {
        panel.classList.toggle('active', panel.dataset.tabPanel === target);
      });
    });
  });
  
  // Ticket Actions - handled by initTicketEvents() in ticketService.js
  // updateStatusBtn, updatePriorityBtn, updateAssigneeBtn, assignMeBtn listeners removed to avoid duplication
  
  const addCommentBtn = document.getElementById('add-comment-btn');
  const auditDownloadBtn = document.getElementById('audit-download-btn');
  
  if (addCommentBtn) addCommentBtn.addEventListener('click', () => addComment().catch((err) => alert(err.message)));
  if (auditDownloadBtn) auditDownloadBtn.addEventListener('click', () => downloadAuditCsv().catch((err) => alert(err.message)));
  
  // Report Buttons
  const engineerReportBtn = document.getElementById('engineer-report-btn');
  const requesterReportBtn = document.getElementById('requester-report-btn');
  const backlogReportBtn = document.getElementById('backlog-report-btn');
  const slaReportBtn = document.getElementById('sla-report-btn');
  const reportDownloadBtn = document.getElementById('report-download-btn');
  
  if (engineerReportBtn) engineerReportBtn.addEventListener('click', () => loadEngineerReport().catch((err) => alert(err.message)));
  if (requesterReportBtn) requesterReportBtn.addEventListener('click', () => loadRequesterReport().catch((err) => alert(err.message)));
  if (backlogReportBtn) backlogReportBtn.addEventListener('click', () => loadBacklogReport().catch((err) => alert(err.message)));
  if (slaReportBtn) slaReportBtn.addEventListener('click', () => loadSlaReport().catch((err) => alert(err.message)));
  if (reportDownloadBtn) reportDownloadBtn.addEventListener('click', downloadReportCsv);
  
  // Admin User Management
  const userSearch = document.getElementById('user-search');
  const userSearchBtn = document.getElementById('user-search-btn');
  const usersPrev = document.getElementById('users-prev');
  const usersNext = document.getElementById('users-next');
  const openUserModal = document.getElementById('open-user-modal');
  const closeUserModal = document.getElementById('close-user-modal');
  const userCreateModal = document.getElementById('user-create-modal');
  const closeUserDetail = document.getElementById('close-user-detail');
  const userDetailModal = document.getElementById('user-detail-modal');
  
  if (userSearchBtn) {
    userSearchBtn.addEventListener('click', () => {
      window.userPage = 0;
      loadAdminUsers().catch((err) => alert(err.message));
    });
  }
  if (userSearch) {
    userSearch.addEventListener('keydown', (event) => {
      if (event.key === 'Enter') {
        event.preventDefault();
        window.userPage = 0;
        loadAdminUsers().catch((err) => alert(err.message));
      }
    });
  }
  if (usersPrev) {
    usersPrev.addEventListener('click', () => {
      if (window.userPage > 0) {
        window.userPage -= 1;
        loadAdminUsers().catch((err) => alert(err.message));
      }
    });
  }
  if (usersNext) {
    usersNext.addEventListener('click', () => {
      window.userPage += 1;
      loadAdminUsers().catch((err) => alert(err.message));
    });
  }
  if (openUserModal) {
    openUserModal.addEventListener('click', async () => {
      await loadDepartments();
      if (userCreateModal) userCreateModal.classList.remove('hidden');
      // For TRUONG_PHONG, set default department
      if (window.currentUser?.departmentId) {
        const deptSelect = document.getElementById('admin-dept-select');
        if (deptSelect) {
          deptSelect.value = window.currentUser.departmentId;
          deptSelect.disabled = true;
        }
      }
    });
  }
  if (closeUserModal) {
    closeUserModal.addEventListener('click', () => {
      if (userCreateModal) userCreateModal.classList.add('hidden');
    });
  }
  if (closeUserDetail) {
    closeUserDetail.addEventListener('click', () => {
      // Move focus to body before hiding modal to avoid aria-hidden focus issue
      document.body.focus();
      if (userDetailModal) {
        userDetailModal.classList.add('hidden');
      }
      window.selectedUser = null;
      window.userAuditEntries = [];
      window.userAuditPage = 0;
    });
  }

  // User Audit Pagination
  const userAuditPrev = document.getElementById('user-audit-prev');
  const userAuditNext = document.getElementById('user-audit-next');
  if (userAuditPrev) {
    userAuditPrev.addEventListener('click', () => {
      if (!window.userAuditEntries) window.userAuditEntries = [];
      if (window.userAuditPage > 0) {
        window.userAuditPage -= 1;
        renderUserAuditPage();
      }
    });
  }
  if (userAuditNext) {
    userAuditNext.addEventListener('click', () => {
      if (!window.userAuditEntries) window.userAuditEntries = [];
      const totalPages = Math.max(1, Math.ceil(window.userAuditEntries.length / 5));
      if (window.userAuditPage + 1 < totalPages) {
        window.userAuditPage += 1;
        renderUserAuditPage();
      }
    });
  }

  // User Detail Actions
  const userSaveProfile = document.getElementById('user-save-profile');
  const userDelete = document.getElementById('user-delete');
  const userApproveBtn = document.getElementById('user-approve-btn');
  const userRejectBtn = document.getElementById('user-reject-btn');
  
  if (userSaveProfile) {
    userSaveProfile.addEventListener('click', async () => {
      console.log('[DEBUG] userSaveProfile clicked');
      if (!window.selectedUser) {
        console.log('[DEBUG] No selectedUser');
        return;
      }
      const userDisplayName = document.getElementById('user-display-name');
      const userTitle = document.getElementById('user-title');
      const userEmail = document.getElementById('user-email');
      const userPasswordInput = document.getElementById('user-password-input');
      
      clearFieldError(userPasswordInput);
      if (!validateProfileFields([
        { label: 'Tên hiển thị', input: userDisplayName },
        { label: 'Chức danh', input: userTitle },
        { label: 'Email', input: userEmail },
      ])) {
        console.log('[DEBUG] validateProfileFields failed');
        return;
      }
      
      const hasPasswordChange = userPasswordInput && userPasswordInput.value.trim();
      const hasProfileChange = 
        userDisplayName.value !== window.selectedUser.displayName ||
        userTitle.value !== window.selectedUser.title ||
        userEmail.value !== window.selectedUser.email;
      
      console.log('[DEBUG] hasPasswordChange:', hasPasswordChange);
      console.log('[DEBUG] hasProfileChange:', hasProfileChange);
      
      if (hasPasswordChange && hasProfileChange) {
        console.log('[DEBUG] Case: password + profile change');
        const payload = {
          displayName: userDisplayName.value,
          title: userTitle.value,
          email: userEmail.value,
          newPassword: userPasswordInput.value,
        };
        try {
          await request(`/api/users/${window.selectedUser.id}/profile-and-password`, {
            method: 'PATCH',
            body: JSON.stringify(payload),
          });
          addNotification('success', 'Cập nhật thành công', 'Thông tin và mật khẩu đã được cập nhật. Email thông báo đã được gửi.');
          userPasswordInput.value = '';
          loadAdminUsers();
          console.log('[DEBUG] profile-and-password success');
        } catch (error) {
          alert('Lỗi: ' + error.message);
        }
        return;
      }
      
      const updates = [];
      
      if (hasPasswordChange) {
        console.log('[DEBUG] Adding password update');
        updates.push(
          request(`/api/users/${window.selectedUser.id}/password`, {
            method: 'PATCH',
            body: JSON.stringify({ password: userPasswordInput.value }),
          })
        );
      }
      
      if (hasProfileChange) {
        console.log('[DEBUG] Adding profile update');
        updates.push(
          request(`/api/users/${window.selectedUser.id}/profile`, {
            method: 'PATCH',
            body: JSON.stringify({
              displayName: userDisplayName.value,
              title: userTitle.value,
              email: userEmail.value,
            }),
          })
        );
      }
      
      if (!updates.length) {
        console.log('[DEBUG] No updates needed');
        return;
      }
      
      console.log('[DEBUG] Sending updates:', updates.length);
      try {
        await Promise.all(updates);
        addNotification('success', 'Cập nhật thành công', 'Thông tin người dùng đã được cập nhật.');
        if (userPasswordInput) userPasswordInput.value = '';
        await loadAdminUsers();
        console.log('[DEBUG] Updates complete');
      } catch (error) {
        alert('Lỗi: ' + error.message);
      }
    });
  }
  
  if (userDelete) {
    userDelete.addEventListener('click', async () => {
      console.log('[DEBUG] userDelete clicked');
      if (!window.selectedUser) {
        console.log('[DEBUG] No selectedUser for delete');
        return;
      }
      
      const { isAdmin, isTruongPhong } = await import('../core/auth.js');
      console.log('[DEBUG] isAdmin:', isAdmin(), 'isTruongPhong:', isTruongPhong());
      
      if (isAdmin()) {
        const confirmMsg = `Bạn có chắc muốn xóa tài khoản "${window.selectedUser.username}"?\n\nHành động này không thể hoàn tác.`;
        if (!confirm(confirmMsg)) {
          console.log('[DEBUG] Delete cancelled by user');
          return;
        }
        try {
          console.log('[DEBUG] Deleting user:', window.selectedUser.id);
          await request(`/api/users/${window.selectedUser.id}`, { method: 'DELETE' });
          addNotification('delete', 'Xóa tài khoản', `Tài khoản "${window.selectedUser.displayName || window.selectedUser.username}" đã được xóa.`);
          alert('Đã xóa tài khoản thành công!');
          console.log('[DEBUG] Delete success');
        } catch (error) {
          console.log('[DEBUG] Delete error:', error);
          alert('Lỗi: ' + error.message);
          return;
        }
      } else if (isTruongPhong()) {
        const confirmMsg = `Bạn có chắc muốn yêu cầu xóa tài khoản "${window.selectedUser.username}"?\n\nSau khi gửi yêu cầu, Admin sẽ xem xét và duyệt xóa.`;
        if (!confirm(confirmMsg)) return;
        try {
          await request(`/api/admin/users/${window.selectedUser.id}/request-delete`, { method: 'POST' });
          addNotification('request-delete', 'Yêu cầu xóa tài khoản', `Yêu cầu xóa tài khoản "${window.selectedUser.displayName || window.selectedUser.username}" đã được gửi cho Admin.`);
          alert('Yêu cầu xóa đã được gửi cho Admin!');
        } catch (error) {
          alert('Lỗi: ' + error.message);
          return;
        }
      }
      
      window.selectedUser = null;
      document.body.focus();
      if (userDetailModal) userDetailModal.classList.add('hidden');
      await loadAdminUsers();
    });
  }
  
  // Admin Create Form
  const adminCreateForm = document.getElementById('admin-create-form');
  if (adminCreateForm) {
    adminCreateForm.addEventListener('submit', async (event) => {
      event.preventDefault();
      if (!validateForm(adminCreateForm)) return;
      const formData = new FormData(adminCreateForm);
      const payload = {
        username: formData.get('username'),
        password: formData.get('password'),
        role: formData.get('role'),
        displayName: formData.get('displayName'),
        title: formData.get('title'),
        email: formData.get('email'),
      };
      
      const { isTruongPhong } = await import('../core/auth.js');
      const deptSelect = document.getElementById('admin-dept-select');
      if (isTruongPhong() && window.currentUser?.departmentId) {
        payload.departmentId = window.currentUser.departmentId;
      } else {
        payload.departmentId = formData.get('departmentId') || null;
      }
      
      if (payload.role === 'ADMIN') {
        payload.departmentId = null;
      }
      
      try {
        await request('/api/users', {
          method: 'POST',
          body: JSON.stringify(payload),
        });
        adminCreateForm.reset();
        if (userCreateModal) userCreateModal.classList.add('hidden');
        if (deptSelect) deptSelect.disabled = false;
        await loadAdminUsers();
        await loadUserAvatarMap();
        await loadEngineerOptions();
      } catch (error) {
        alert(error.message);
      }
    });
  }
  
  // Profile
  const profilePasswordForm = document.getElementById('profile-password-form');
  const profileSaveBtn = document.getElementById('profile-save-btn');
  
  if (profilePasswordForm) {
    profilePasswordForm.addEventListener('submit', async (event) => {
      event.preventDefault();
      if (!validateForm(profilePasswordForm)) return;
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
  }
  
  if (profileSaveBtn) {
    profileSaveBtn.addEventListener('click', async () => {
      const profileDisplayName = document.getElementById('profile-display-name');
      const profileTitle = document.getElementById('profile-title');
      const profileEmail = document.getElementById('profile-email');
      const profileAvatarPreview = document.getElementById('profile-avatar-preview');
      
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
      await request('/api/users/me/profile', {
        method: 'PATCH',
        body: JSON.stringify(payload),
      });
      await loadProfile();
    });
  }
  
  // Department Management
  const openDeptModal = document.getElementById('open-dept-modal');
  const closeDeptModal = document.getElementById('close-dept-modal');
  const deptCancelBtn = document.getElementById('dept-cancel-btn');
  const deptModal = document.getElementById('dept-modal');
  const deptForm = document.getElementById('dept-form');
  const closeDeptDeleteModal = document.getElementById('close-dept-delete-modal');
  const deptCancelDelete = document.getElementById('dept-cancel-delete');
  const deptDeleteModal = document.getElementById('dept-delete-modal');
  const deptConfirmDelete = document.getElementById('dept-confirm-delete');
  
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
      if (e.target === deptModal) closeDeptModalHandler();
    });
  }
  if (closeDeptDeleteModal) {
    closeDeptDeleteModal.addEventListener('click', closeDeptDeleteModalHandler);
  }
  if (deptCancelDelete) {
    deptCancelDelete.addEventListener('click', closeDeptDeleteModalHandler);
  }
  if (deptDeleteModal) {
    deptDeleteModal.addEventListener('click', (e) => {
      if (e.target === deptDeleteModal) closeDeptDeleteModalHandler();
    });
  }
  
  if (deptForm) {
    deptForm.addEventListener('submit', async (event) => {
      event.preventDefault();
      
      const deptCode = document.getElementById('dept-code');
      const deptName = document.getElementById('dept-name');
      const deptDescription = document.getElementById('dept-description');
      const deptEnabled = document.getElementById('dept-enabled');
      const deptManager = document.getElementById('dept-manager');
      
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
        
        if (window.selectedDepartment) {
          await request(`/api/departments/${window.selectedDepartment.id}`, {
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
        await loadDepartmentsForSelects();
      } catch (error) {
        alert('Lỗi: ' + error.message);
      }
    });
  }
  
  if (deptConfirmDelete) {
    deptConfirmDelete.addEventListener('click', async () => {
      if (!window.selectedDepartmentForDelete) return;
      if (!confirm(`Xác nhận xóa phòng ban "${window.selectedDepartmentForDelete.name}"?`)) return;
      try {
        await request(`/api/departments/${window.selectedDepartmentForDelete.id}`, {
          method: 'DELETE',
        });
        alert('Đã xóa phòng ban thành công!');
        closeDeptDeleteModalHandler();
        await loadDepartments();
        await loadDepartmentsForSelects();
      } catch (error) {
        alert('Lỗi: ' + error.message);
      }
    });
  }
  
  // Notification Handlers
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
  document.addEventListener('click', (e) => {
    const wrapper = document.querySelector('.notification-wrapper');
    const notificationDropdown = document.getElementById('notification-dropdown');
    if (notificationDropdown && !notificationDropdown.classList.contains('hidden')) {
      if (wrapper && !wrapper.contains(e.target)) {
        import('../services/notificationService.js').then(module => {
          module.closeNotificationDropdown();
        });
      }
    }
  });
  
  // Reject Modal
  const rejectModal = document.getElementById('reject-modal');
  const rejectModalClose = document.getElementById('reject-modal-close');
  const rejectModalCancel = document.getElementById('reject-modal-cancel');
  const rejectModalConfirm = document.getElementById('reject-modal-confirm');
  
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
      const rejectReasonInput = document.getElementById('reject-reason-input');
      if (rejectReasonInput) rejectReasonInput.value = '';
    });
  }
  if (rejectModalConfirm) {
    rejectModalConfirm.addEventListener('click', async () => {
      const userId = rejectModal.dataset.userId;
      const userDisplayName = rejectModal.dataset.userDisplayName;
      const rejectReasonInput = document.getElementById('reject-reason-input');
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
        addNotification('reject', 'Tài khoản bị từ chối', `Tài khoản "${userDisplayName}" đã bị từ chối. Lý do: ${reason}`);
        rejectModal.classList.add('hidden');
        rejectModal.setAttribute('aria-hidden', 'true');
        if (rejectReasonInput) rejectReasonInput.value = '';
        await loadAdminUsers();
        alert('Đã từ chối tài khoản.');
      } catch (error) {
        alert('Lỗi: ' + error.message);
      }
    });
  }
  if (rejectModal) {
    rejectModal.addEventListener('click', (e) => {
      if (e.target === rejectModal) {
        rejectModal.classList.add('hidden');
        rejectModal.setAttribute('aria-hidden', 'true');
        const rejectReasonInput = document.getElementById('reject-reason-input');
        if (rejectReasonInput) rejectReasonInput.value = '';
      }
    });
  }
  
  // Hash change
  window.addEventListener('hashchange', () => {
    routeGuard();
  });
  
  // Nav Toggle
  const navToggle = document.getElementById('nav-toggle');
  const navCard = document.querySelector('.nav-card');
  const navLinks = Array.from(document.querySelectorAll('.nav-card a'));
  
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
  
  // Attach validation handlers
  attachValidationHandlers(loginForm);
  attachValidationHandlers(createForm);
  attachValidationHandlers(adminCreateForm);
  attachValidationHandlers(profilePasswordForm);
  attachValidationHandlers(userDetailModal);
  attachValidationHandlers(document.getElementById('comment-panel'));
  attachValidationHandlers(document.getElementById('profile-panel'));
  
  // Initialize auto refresh and infinite scroll
  initAutoRefresh(loadTickets);
  initTicketInfiniteScroll(loadTickets);
  
  // File attachment handlers
  initAttachmentHandlers();
};

const initAttachmentHandlers = () => {
  const attachmentInput = document.getElementById('attachment-input');
  const ticketAttachmentInput = document.getElementById('ticket-attachment-input');
  const ticketFileUploadArea = document.getElementById('ticket-file-upload-area');
  const uploadAttachmentBtn = document.getElementById('upload-attachment-btn');
  const selectedFilesList = document.getElementById('selected-files');
  const ticketSelectedFiles = document.getElementById('ticket-selected-files');
  
  selectedFiles = [];
  ticketSelectedFilesList = [];
  
  if (attachmentInput) {
    attachmentInput.addEventListener('change', (e) => {
      const files = Array.from(e.target.files);
      selectedFiles = [...selectedFiles, ...files];
      renderSelectedFiles();
    });
  }
  
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
  
  if (ticketAttachmentInput) {
    ticketAttachmentInput.addEventListener('change', (e) => {
      const files = Array.from(e.target.files);
      ticketSelectedFilesList = [...ticketSelectedFilesList, ...files];
      renderTicketSelectedFiles();
    });
  }
  
  if (uploadAttachmentBtn) {
    uploadAttachmentBtn.addEventListener('click', async () => {
      if (!window.selectedTicket) return;
      if (ticketSelectedFilesList.length === 0) {
        alert('Vui lòng chọn tệp đính kèm.');
        return;
      }
      await uploadAttachments(window.selectedTicket.id, ticketSelectedFilesList);
      ticketSelectedFilesList = [];
      renderTicketSelectedFiles();
      ticketAttachmentInput.value = '';
    });
  }
};

const renderSelectedFiles = () => {
  const selectedFilesList = document.getElementById('selected-files');
  if (!selectedFilesList) return;
  selectedFilesList.innerHTML = '';
  
  if (selectedFiles.length === 0) return;
  
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
  
  document.querySelectorAll('.remove-file-btn').forEach((btn) => {
    btn.addEventListener('click', (e) => {
      const index = parseInt(e.target.dataset.index, 10);
      selectedFiles.splice(index, 1);
      renderSelectedFiles();
    });
  });
};

const renderTicketSelectedFiles = () => {
  const ticketSelectedFiles = document.getElementById('ticket-selected-files');
  if (!ticketSelectedFiles) return;
  ticketSelectedFiles.innerHTML = '';
  
  if (ticketSelectedFilesList.length === 0) return;
  
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
  
  document.querySelectorAll('[data-ticket-index]').forEach((btn) => {
    btn.addEventListener('click', (e) => {
      const index = parseInt(e.target.dataset.ticketIndex, 10);
      ticketSelectedFilesList.splice(index, 1);
      renderTicketSelectedFiles();
    });
  });
};
