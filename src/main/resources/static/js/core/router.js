// ==================== Router Module ====================
import { isTokenValid, getToken, hasRole, isTruongPhong, isAdmin, getCurrentUser } from './auth.js';
import { setToken, setCurrentUser } from './auth.js';
import { canManageUsers } from '../services/userService.js';

let currentUser = null;

export const setRoute = (hash) => {
  window.location.hash = hash;
};

export const showView = (viewName) => {
  const views = Array.from(document.querySelectorAll('.view'));
  const navLinks = Array.from(document.querySelectorAll('.nav-card a'));
  views.forEach((view) => {
    view.classList.toggle('active', view.dataset.view === viewName);
  });
  navLinks.forEach((link) => {
    link.classList.toggle('active', link.getAttribute('href') === `#/${viewName}`);
  });
  
  const ticketDetailsPanel = document.getElementById('ticket-details-panel');
  if (viewName !== 'tickets' && ticketDetailsPanel) {
    ticketDetailsPanel.classList.add('hidden');
    window.selectedTicket = null;
  }
  
  // Load tickets when tickets view is shown
  if (viewName === 'tickets') {
    // Check for pending filter from dashboard
    const pendingFilter = sessionStorage.getItem('ticketing.pendingFilter');
    const selectedTicketId = sessionStorage.getItem('ticketing.selectedTicketId');
    
    if (pendingFilter === 'unassigned') {
      sessionStorage.removeItem('ticketing.pendingFilter');
      // Set filter to show unassigned tickets
      import('../services/ticketService.js').then(module => {
        module.setFilterUnassigned();
        module.loadTickets({ reset: true }).catch(() => {});
        // Select ticket if one was pending
        if (selectedTicketId) {
          sessionStorage.removeItem('ticketing.selectedTicketId');
          module.selectTicket({ id: parseInt(selectedTicketId) }).catch(() => {});
        }
      });
    } else {
      import('../services/ticketService.js').then(module => {
        module.loadTickets({ reset: false }).catch(() => {});
        // Select ticket if one was pending
        if (selectedTicketId) {
          sessionStorage.removeItem('ticketing.selectedTicketId');
          module.selectTicket({ id: parseInt(selectedTicketId) }).catch(() => {});
        }
      });
    }
  }
  if (viewName === 'admin' && canManageUsers()) {
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
    // Load admin data dynamically
    import('../services/userService.js').then(module => {
      module.loadAdminUsers().catch(() => {});
    });
    import('../services/departmentService.js').then(module => {
      module.loadDepartments().catch(() => {});
    });
  }
  if (viewName === 'login') {
    const loginForm = document.getElementById('login-form');
    const loginPrefillNote = document.getElementById('login-prefill-note');
    if (loginPrefillNote && loginForm) {
      loginPrefillNote.classList.toggle('hidden', window.location.protocol === 'file:');
      if (window.location.protocol !== 'file:') {
        const usernameInput = loginForm.elements.namedItem('username');
        const passwordInput = loginForm.elements.namedItem('password');
        if (usernameInput && passwordInput) {
          usernameInput.value = 'tech.smith';
          passwordInput.value = '12345678';
        }
      }
    }
  }
};

const isStaff = () => {
  const hasNhansVien = hasRole('ROLE_NHAN_VIEN');
  const isITStaff = hasNhansVien && currentUser?.departmentCode === 'IT';
  return !hasNhansVien || isITStaff;
};

export const routeGuard = () => {
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
    if (subRoute === 'departments' && (isAdmin() || hasRole('ROLE_GIAM_DOC'))) {
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

export const getAssigneeLabel = (ticket, userAvatarMap) => {
  if (!ticket || !ticket.assigneeName) return '';
  const key = ticket.assigneeName.toLowerCase();
  const profile = userAvatarMap.get(key);
  if (profile && profile.displayName) {
    return profile.displayName;
  }
  return formatName(ticket.assigneeName);
};

const formatName = (value) => {
  if (!value) return '';
  return value
    .split(/[\s_-]+/)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1).toLowerCase())
    .join(' ');
};

export { currentUser };
