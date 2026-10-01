// ==================== Main Application Entry Point ====================
// IT Ticketing System - Modular Frontend

import { initElements, updateTokenStatus, updateNavVisibility, isTokenValid, Auth } from './core/auth.js';
import { routeGuard, setRoute } from './core/router.js';
import { DashboardModule } from './modules/dashboard.js';

// Expose Auth globally for modules that use it
window.Auth = Auth;

// Import Toast module first
import './ui/toast.js';

// Expose DashboardModule globally
window.DashboardModule = DashboardModule;

// Import SlaPolicy module
import { SlaPolicy } from './modules/slaPolicy.js';
window.SlaPolicy = SlaPolicy;

// Import SlaTimer module
import { SlaTimer } from './modules/slaTimer.js';
window.SlaTimer = SlaTimer;

// Import Escalation module
import { Escalation } from './modules/escalation.js';
window.Escalation = Escalation;

// Import Incident module
import { Incident } from './modules/incident.js';
window.Incident = Incident;

// Import ServiceRequest module
import { ServiceRequest } from './modules/serviceRequest.js';
window.ServiceRequest = ServiceRequest;

// Import Problem module
import { ProblemManagement } from './modules/problem.js';
window.ProblemManagement = ProblemManagement;

// Import Change module
import { ChangeManagement } from './modules/change.js';
window.ChangeManagement = ChangeManagement;

// ==================== Edge Case & Error Handling Modules ====================

// Import XSS Sanitization module (auto-applied in service modules)
import './core/sanitize.js';

// Import Draft Service for auto-save functionality
import { setupBeforeUnloadWarning, listDrafts } from './services/draftService.js';
window.DraftService = { setupBeforeUnloadWarning, listDrafts };

// Import Concurrent Edit module
import { setupConcurrentEditWarning } from './modules/concurrentEdit.js';
window.ConcurrentEdit = { setupConcurrentEditWarning };

// Import Loading Spinner
import { showLoading, hideLoading, resetLoading } from './ui/loading.js';
window.LoadingUI = { showLoading, hideLoading, resetLoading };

// ==================== Asset Management ====================

// Import Asset module
import { AssetManagement } from './modules/asset.js';
window.AssetManagement = AssetManagement;

// Import AssetLink module
import { AssetLinkModule } from './modules/assetLink.js';
window.AssetLinkModule = AssetLinkModule;

// Import Analytics module
import { AnalyticsModule } from './modules/analytics.js';
window.AnalyticsModule = AnalyticsModule;

// Import Reports module
import * as ReportModule from './modules/reports.js';
window.ReportModule = ReportModule;

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
  canManageUsers as checkCanManageUsers,
  isITStaff 
} from './services/userService.js';
import { loadDepartments, loadDepartmentsTable } from './services/departmentService.js';

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

/**
 * Check for draft recovery on page load
 * Shows recovery prompt if unsaved drafts exist
 */
const checkDraftRecovery = () => {
    // Only check if on ticket creation page
    const createForm = document.getElementById('create-form');
    if (!createForm) return;
    
    // Check for drafts
    const drafts = listDrafts();
    const ticketDrafts = drafts.filter(d => d.type === 'ticket-create');
    
    if (ticketDrafts.length > 0) {
        const latestDraft = ticketDrafts[0];
        const recovery = confirm(
            `Tìm thấy bản nháp được lưu lúc ${latestDraft.age}.\n\n` +
            `Bạn có muốn khôi phục nội dung đã nhập trước đó không?\n\n` +
            `Nhấn OK để khôi phục.\n` +
            `Nhấn Cancel để xóa bản nháp và bắt đầu mới.`
        );
        
        if (recovery && latestDraft.formData) {
            // Populate form with draft data
            Object.entries(latestDraft.formData).forEach(([key, value]) => {
                const input = createForm.querySelector(`[name="${key}"]`);
                if (input) {
                    input.value = value;
                }
            });
        } else {
            // Clear the draft
            import('./services/draftService.js').then(module => {
                module.deleteDraft(latestDraft.key);
            });
        }
    }
};

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
  
  // Initialize beforeunload warning for unsaved changes
  setupBeforeUnloadWarning();
  
  // Check for draft recovery on page load
  checkDraftRecovery();
  
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
        if (checkCanManageUsers() || isITStaff()) {
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
    if (checkCanManageUsers()) {
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

// Load SLA Policies on admin panel
const loadSlaPolicies = async () => {
  console.log('[app.js] loadSlaPolicies called');
  const tabContent = document.getElementById('tab-sla-policies');
  console.log('[app.js] tab-sla-policies exists:', !!tabContent);
  if (!tabContent) return;
  
  console.log('[app.js] window.SlaPolicy:', typeof window.SlaPolicy);
  console.log('[app.js] window.Auth:', typeof window.Auth);
  
  if (window.SlaPolicy) {
    console.log('[app.js] Calling SlaPolicy.init()...');
    window.SlaPolicy.init();
  } else {
    console.error('[app.js] window.SlaPolicy is not defined!');
  }
};

// Load Escalation Rules on admin panel
const loadEscalationRules = async () => {
  const tabContent = document.getElementById('tab-escalation');
  if (!tabContent) return;
  
  if (window.Escalation) {
    window.Escalation.init();
  }
};

// Load Incidents
const loadIncidents = async () => {
  const panel = document.getElementById('incidents-panel');
  if (!panel) return;
  
  if (window.Incident) {
    window.Incident.init();
  }
};

// Load Service Requests
const loadServiceRequests = async () => {
  const panel = document.getElementById('service-requests-panel');
  if (!panel) return;
  
  if (window.ServiceRequest) {
    window.ServiceRequest.init();
  }
};

// Watch for hash changes to load dashboard
window.addEventListener('hashchange', () => {
  const hash = window.location.hash;
  if (hash === '#/dashboard' || hash.includes('dashboard')) {
    loadITDashboard();
  }
});

// Watch for tab changes to load SLA policies
document.addEventListener('tabChanged', (e) => {
  console.log('[app.js] tabChanged event received:', e.detail?.tab);
  if (e.detail && e.detail.tab === 'sla-policies') {
    console.log('[app.js] Calling loadSlaPolicies...');
    loadSlaPolicies();
  }
  if (e.detail && e.detail.tab === 'escalation') {
    console.log('[app.js] Calling loadEscalationRules...');
    loadEscalationRules();
  }
  if (e.detail && e.detail.tab === 'users') {
    console.log('[app.js] Calling loadAdminUsers...');
    loadAdminUsers();
  }
  if (e.detail && e.detail.tab === 'departments') {
    console.log('[app.js] Calling loadDepartmentsTable...');
    loadDepartmentsTable();
  }
});

// Event delegation for SLA policy modal
document.addEventListener('click', (e) => {
  if (e.target.closest('#open-sla-policy-modal')) {
    if (window.SlaPolicy) {
      window.SlaPolicy.openCreateModal();
    }
  }
  if (e.target.closest('#open-escalation-modal')) {
    if (window.Escalation) {
      window.Escalation.openCreateModal();
    }
  }
  if (e.target.closest('#open-incident-modal')) {
    if (window.Incident) {
      window.Incident.openCreateModal();
    }
  }
});

// Load Incidents when hash changes to incidents
window.addEventListener('hashchange', () => {
  const hash = window.location.hash;
  if (hash === '#/incidents') {
    loadIncidents();
  }
  if (hash === '#/service-requests') {
    loadServiceRequests();
  }
  if (hash === '#/problems') {
    loadProblems();
  }
  if (hash === '#/changes') {
    loadChanges();
  }
  if (hash === '#/assets') {
    loadAssets();
  }
  if (hash === '#/analytics') {
    loadAnalytics();
  }
});

// Load Problems
const loadProblems = async () => {
  const panel = document.getElementById('problems-panel');
  if (!panel) return;
  
  if (window.ProblemManagement) {
    window.ProblemManagement.init();
  }
};

// Load Changes
const loadChanges = async () => {
  const panel = document.getElementById('changes-panel');
  if (!panel) return;
  
  if (window.ChangeManagement) {
    window.ChangeManagement.init();
  }
};

// Load Assets
const loadAssets = async () => {
  const panel = document.getElementById('assets-panel');
  if (!panel) return;
  
  if (window.AssetManagement) {
    window.AssetManagement.init();
  }
};

// Load Analytics
const loadAnalytics = async () => {
  const panel = document.getElementById('analytics-panel');
  if (!panel) return;
  
  if (window.AnalyticsModule) {
    window.AnalyticsModule.init();
  }
};

// Setup Service Request tabs
document.addEventListener('click', (e) => {
  if (e.target.closest('.sr-tab-btn')) {
    const tab = e.target.closest('.sr-tab-btn').dataset.srTab;
    
    // Toggle active tab
    document.querySelectorAll('.sr-tab-btn').forEach(btn => {
      btn.classList.toggle('active', btn.dataset.srTab === tab);
    });
    
    // Toggle content
    document.getElementById('sr-catalog-section')?.classList.toggle('hidden', tab !== 'catalog');
    document.getElementById('sr-list-section')?.classList.toggle('hidden', tab !== 'my-requests');
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
