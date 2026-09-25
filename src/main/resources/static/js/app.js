// ==================== Main Application Entry Point ====================
// IT Ticketing System - Modular Frontend

import { initElements, updateTokenStatus, updateNavVisibility, isTokenValid } from './core/auth.js';
import { routeGuard, setRoute } from './core/router.js';
import { DashboardModule } from './modules/dashboard.js';

// Expose DashboardModule globally
window.DashboardModule = DashboardModule;

// Import services
import { 
  loadNotifications, 
  startNotificationPolling, 
  stopNotificationPolling 
} from './services/notificationService.js';
import { 
  loadTickets, 
  loadDashboard, 
  loadQueue,
  initTicketEvents
} from './services/ticketService.js';
import { 
  loadAdminUsers, 
  loadProfile, 
  loadUserAvatarMap, 
  loadEngineerOptions,
  canManageUsers,
  isITStaff 
} from './services/userService.js';
import { loadDepartments } from './services/departmentService.js';

// Import UI utilities
import { 
  initReportDates, 
  initTheme, 
  initTabs,
  loadTicketFilters,
  applyTheme 
} from './ui/utils.js';

// Import event handlers
import { attachEventHandlers } from './handlers/eventHandlers.js';

// Register notification functions for auth.js
import { setLoadNotifications, setStartNotificationPolling, setStopNotificationPolling } from './core/auth.js';
setLoadNotifications(loadNotifications);
setStartNotificationPolling(startNotificationPolling);
setStopNotificationPolling(stopNotificationPolling);

// Current user reference
let currentUser = null;

// Initialize application
const init = async () => {
  // Initialize elements
  initElements();
  
  // Initialize theme
  initTheme();
  
  // Initialize tabs
  initTabs();
  
  // Initialize report dates
  initReportDates();

  // Attach all event handlers
  attachEventHandlers();

  // Initialize ticket-specific events
  initTicketEvents();
  
  // Update token status and nav visibility
  updateTokenStatus();
  updateNavVisibility();
  
  // Route guard
  routeGuard();
  
  // Load notifications
  loadNotifications();
  loadNotifications(); // Load saved notifications
  
  // Check if user is already logged in
  const stored = localStorage.getItem('ticketing.currentUser');
  if (stored) {
    try {
      currentUser = JSON.parse(stored);
      window.currentUser = currentUser;
    } catch (e) {
      currentUser = null;
    }
  }
  
  // If token is valid, load initial data
  if (isTokenValid()) {
    // Load profile first
    await loadProfile()
      .then(() => {
        // Load user avatar map first (needed for getDisplayName in engineer options)
        if (canManageUsers() || isITStaff()) {
          return loadUserAvatarMap();
        }
      })
      .then(() => {
        // Then load engineer options
        return loadEngineerOptions();
      })
      .then(() => {
        // Load saved filter values
        loadTicketFilters();
        // Load tickets
        return loadTickets({ reset: true });
      })
      .catch(() => {});
    
    // Load dashboard and queue
    loadDashboard().catch(() => {});
    loadQueue().catch(() => {});
    
    // Load admin data if applicable
    if (canManageUsers()) {
      loadAdminUsers().catch(() => {});
      loadDepartments().catch(() => {});
    }
    
    // Load IT Staff Dashboard if on dashboard view
    loadITDashboard();
  }
};

// Load IT Staff Dashboard Module
const loadITDashboard = async () => {
  const dashboardContent = document.getElementById('dashboard-content');
  if (!dashboardContent) return;
  
  // DashboardModule is already imported at top of file
  if (window.DashboardModule) {
    window.DashboardModule.init();
  }
};

// Watch for hash changes to load dashboard
window.addEventListener('hashchange', () => {
  const hash = window.location.hash;
  if (hash === '#/dashboard' || hash.includes('dashboard')) {
    loadITDashboard();
  }
});

// Theme key
const themeKey = 'ticketing.theme';

// Initialize on DOM ready
document.addEventListener('DOMContentLoaded', () => {
  init();
});

// Export for debugging
window.appInit = init;
window.currentUser = currentUser;
