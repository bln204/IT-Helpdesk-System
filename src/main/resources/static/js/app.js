// ==================== Main Application Entry Point ====================
// IT Ticketing System - Modular Frontend

// Import core modules
import { initElements, updateTokenStatus, updateNavVisibility, isTokenValid } from './core/auth.js';
import { routeGuard, setRoute } from './core/router.js';

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
  canManageUsers 
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
  console.log('[APP] Initializing application...');
  
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
        // Then load engineer options
        return loadEngineerOptions();
      })
      .then(() => {
        // Load user avatar map if can manage users
        if (canManageUsers()) {
          return loadUserAvatarMap();
        }
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
  }
};

// Theme key
const themeKey = 'ticketing.theme';

// Initialize on DOM ready
document.addEventListener('DOMContentLoaded', () => {
  init();
});

// Export for debugging
window.appInit = init;
window.currentUser = currentUser;
