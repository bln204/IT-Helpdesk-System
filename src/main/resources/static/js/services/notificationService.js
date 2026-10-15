// ==================== Notification Service ====================
import { API_BASE, tokenKey, NOTIFICATION_POLLING_MS } from '../config/constants.js';
import { request } from '../core/api.js';
import { setRoute } from '../core/router.js';
import { getToken, isTokenValid, getCurrentUsername } from '../core/auth.js';
import { escapeHtml } from '../core/sanitize.js';
import { STATUS_LABELS } from '../ui/utils.js';

const SLA_LABELS = { RESPONSE: 'phản hồi', RESOLUTION: 'giải quyết' };

// Backend messages (and rows already stored) carry raw enum codes / English terms;
// rewrite them with the labels the rest of the UI uses.
export const localizeNotificationText = (text) => {
  if (!text) return '';
  return String(text)
    .replace(/(từ|sang) ([A-Z_]+)\b/g, (m, w, code) => `${w} ${STATUS_LABELS[code] || code}`)
    .replace(/Priority: ([A-Z]+)\b/g, (m, code) => `Độ ưu tiên: ${STATUS_LABELS[code] || code}`)
    .replace(/\b(RESPONSE|RESOLUTION)\b/g, (m, code) => SLA_LABELS[code])
    .replace(/SLA VI PHẠM/g, 'Vi phạm SLA')
    .replace(/đã bị breached/g, 'đã bị vi phạm')
    .replace(/Status thay đổi/g, 'Cập nhật trạng thái')
    .replace(/(được|đã được) escalate/g, '$1 chuyển cấp')
    // usernames may contain dots (tp.it); a trailing dot is sentence punctuation
    .replace(/bởi ([^\s,()]+?)(\.?)(?=[\s,()]|$)/g, (m, who, dot) =>
      `bởi ${who === 'SYSTEM' ? 'Hệ thống' : (window.displayNameOf?.(who) || who)}${dot}`)
    .replace(/\bTicket\b/g, 'Phiếu')
    .replace(/\bticket\b/g, 'phiếu')
    .replace(/\bIncident\b/g, 'Sự cố');
};

let notifications = [];

// "Clicked" is separate from backend `read` (opening the dropdown marks everything read for the badge).
// shortcut: kept in localStorage per user, so it is per-browser; move to a backend column if it must sync across devices.
const clickedKey = () => `ticketing.notifications.clicked.${getCurrentUsername() || ''}`;
const getClickedIds = () => {
  try {
    return new Set(JSON.parse(localStorage.getItem(clickedKey()) || '[]'));
  } catch (e) {
    return new Set();
  }
};
const markNotificationClicked = (id) => {
  const ids = getClickedIds();
  ids.add(id);
  try {
    localStorage.setItem(clickedKey(), JSON.stringify([...ids].slice(-500)));
  } catch (e) { /* storage unavailable: highlight just stays */ }
};
let notificationPollingTimer = null;
let notificationList, notificationDropdown;

export const startNotificationPolling = () => {
  stopNotificationPolling();
  notificationPollingTimer = setInterval(async () => {
    await loadNotifications();
  }, NOTIFICATION_POLLING_MS);
};

export const stopNotificationPolling = () => {
  if (notificationPollingTimer) {
    clearInterval(notificationPollingTimer);
    notificationPollingTimer = null;
  }
};

// Fetch notifications from backend API
export const fetchNotifications = async () => {
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
export const fetchUnreadCount = async () => {
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
export const markNotificationReadAPI = async (id) => {
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
export const markAllNotificationsReadAPI = async () => {
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
export const deleteAllNotificationsAPI = async () => {
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

// Convert backend notification to frontend format
export const convertBackendNotification = (notif) => {
  const typeMap = {
    'ACCOUNT_APPROVED': { type: 'approve', category: 'account' },
    'ACCOUNT_REJECTED': { type: 'reject', category: 'account' },
    'ACCOUNT_CREATED_PENDING': { type: 'pending', category: 'account' },
    'INFO': { type: 'info', category: 'general' },
    'PROFILE_CHANGED': { type: 'info', category: 'profile' },
    // Ticket/Incident notifications
    'TICKET_CREATED': { type: 'info', category: 'ticket' },
    'TICKET_ASSIGNED': { type: 'info', category: 'ticket' },
    'TICKET_ASSIGNED_TO_TEAM': { type: 'info', category: 'ticket' },
    'TICKET_UNASSIGNED': { type: 'info', category: 'ticket' },
    'TICKET_REASSIGNED': { type: 'info', category: 'ticket' },
    'TICKET_STATUS_CHANGED': { type: 'info', category: 'ticket' },
    'TICKET_IN_PROGRESS': { type: 'info', category: 'ticket' },
    'TICKET_WAITING_FOR_INFO': { type: 'info', category: 'ticket' },
    'TICKET_INFO_PROVIDED': { type: 'info', category: 'ticket' },
    'TICKET_RESOLVED': { type: 'info', category: 'ticket' },
    'TICKET_CLOSED': { type: 'info', category: 'ticket' },
    'TICKET_REOPENED': { type: 'info', category: 'ticket' },
    'TICKET_ESCALATED': { type: 'warning', category: 'ticket' },
    'TICKET_COMMENT_ADDED': { type: 'info', category: 'ticket' },
    'TICKET_SLA_WARNING': { type: 'warning', category: 'ticket' },
    'TICKET_SLA_BREACHED': { type: 'error', category: 'ticket' },
    'SERVICE_REQUEST_CREATED': { type: 'info', category: 'service-request' },
    'SERVICE_REQUEST_UPDATED': { type: 'info', category: 'service-request' },
  };
  
  const mapping = typeMap[notif.type] || { type: 'info', category: 'general' };
  
  return {
    id: notif.id,
    type: notif.type, // Lưu type gốc từ backend
    category: mapping.category,
    displayType: mapping.type,
    title: notif.title || notif.message,
    message: notif.message || notif.title,
    ticketId: notif.ticketId, // ID của ticket/incident
    ticketNumber: notif.ticketNumber, // Số ticket/incident để hiển thị
    userId: notif.relatedUserId,
    timestamp: notif.createdAt,
    read: notif.read || false,
  };
};

export const addNotification = async (type, title, message, userId = null) => {
  const notification = {
    id: Date.now(),
    type,
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

  localStorage.setItem('ticketing.notifications', JSON.stringify(notifications.slice(0, 50)));

  try {
    await request('/api/notifications', {
      method: 'POST',
      body: JSON.stringify({ type, title, message, userId })
    });
  } catch (error) {
    console.error('Failed to save notification to backend:', error);
  }
};

export const loadNotifications = async () => {
  const token = localStorage.getItem(tokenKey);
  if (!token) {
    notifications = [];
    updateNotificationBadge(0);
    return;
  }

  try {
    const { notifications: fetchedNotifications, unreadCount } = await fetchNotifications();
    notifications = fetchedNotifications.map(convertBackendNotification);
    updateNotificationBadge(unreadCount);
    renderNotifications();
  } catch (error) {
    console.error('Error loading notifications:', error);
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

export const updateNotificationBadge = (count) => {
  const badge = document.getElementById('notification-badge');
  if (!badge) return;
  if (count > 0) {
    badge.textContent = count > 99 ? '99+' : count;
    badge.style.display = 'flex';
    badge.classList.remove('hidden');
  } else {
    badge.style.display = 'none';
    badge.classList.add('hidden');
  }
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

export const renderNotifications = () => {
  notificationList = document.getElementById('notification-list');
  if (!notificationList) return;

  if (notifications.length === 0) {
    notificationList.innerHTML = '<div class="notification-empty">Không có thông báo</div>';
    return;
  }

  notificationList.innerHTML = '';
  const clickedIds = getClickedIds();
  notifications.forEach((notif) => {
    const clicked = clickedIds.has(notif.id);
    const item = document.createElement('div');
    item.className = `notification-item ${clicked ? 'clicked' : 'unread'}`;

    const timeAgo = getTimeAgo(notif.timestamp);

    const title = localizeNotificationText(notif.title);
    const message = localizeNotificationText(notif.message);
    // Hiển thị ticket number nếu tiêu đề/nội dung chưa có
    const ticketInfo = notif.ticketNumber && !`${title} ${message}`.includes(notif.ticketNumber)
      ? `<span class="notification-ticket-number">#${escapeHtml(notif.ticketNumber)}</span>` : '';

    item.innerHTML = `
      <div class="notification-item-content">
        <div class="notification-text">
          <div class="notification-item-title">${escapeHtml(title)}${ticketInfo}</div>
          <div class="notification-item-message">${escapeHtml(message)}</div>
        </div>
      </div>
      <div class="notification-item-footer">
        <span class="notification-item-time">${escapeHtml(timeAgo)}</span>
        ${!clicked ? '<span class="notification-unread-dot"></span>' : ''}
      </div>
    `;

    // Lưu thông tin vào dataset
    item.dataset.ticketId = notif.ticketId || '';
    item.dataset.ticketNumber = notif.ticketNumber || '';
    item.dataset.category = notif.category || 'general';

    item.addEventListener('click', () => {
      handleNotificationClick(notif);
    });

    notificationList.appendChild(item);
  });
};

/**
 * Xử lý click vào notification - chuyển đến trang phù hợp
 */
const handleNotificationClick = async (notif) => {
  markNotificationClicked(notif.id);
  // Đánh dấu đã đọc
  await markNotificationRead(notif.id);
  closeNotificationDropdown();

  // Xác định route dựa trên category
  const category = notif.category || 'general';

  // TICKET_* types are shared by tickets and incidents; the number prefix tells them apart
  if (category === 'ticket' && notif.ticketId && !String(notif.ticketNumber || '').startsWith('INC-')) {
    window.location.hash = '#/tickets';
    try {
      const { selectTicket } = await import('./ticketService.js');
      await selectTicket({ id: notif.ticketId });
    } catch (error) {
      console.error('Error opening ticket from notification:', error);
      window.Toast?.show('Không thể mở phiếu hỗ trợ', 'error');
    }
  } else if (category === 'ticket' && notif.ticketId) {
    // Chuyển đến trang incidents với incident được chọn
    window.location.hash = '#/incidents';

    // Đợi một chút để view load xong
    setTimeout(async () => {
      try {
        // Load incidents list trước
        if (window.Incident) {
          await window.Incident.loadIncidents();
          // Tìm incident trong danh sách và mở detail
          const incidents = window.Incident.getIncidents() || [];
          const incident = incidents.find(i => i.id === notif.ticketId);
          if (incident) {
            window.Incident.viewIncident(notif.ticketId);
          } else {
            // Thử load trực tiếp incident bằng API
            const response = await fetch(`/api/incidents/${notif.ticketId}`, {
              headers: window.Auth.getAuthHeaders()
            });
            if (response.ok) {
              window.Incident.viewIncident(notif.ticketId);
            } else {
              window.Toast?.show('Không tìm thấy sự cố', 'error');
            }
          }
        }
      } catch (error) {
        console.error('Error loading incident from notification:', error);
        window.Toast?.show('Không thể mở sự cố', 'error');
      }
    }, 500);
  } else if (category === 'service-request') {
    window.location.hash = '#/service-requests';
    if (notif.ticketId) setTimeout(() => window.ServiceRequest?.viewRequest(notif.ticketId), 500);
  } else if (category === 'account') {
    // Chuyển đến trang admin để duyệt tài khoản
    window.location.hash = '#/admin';
  } else if (category === 'profile') {
    // Chuyển đến trang profile
    window.location.hash = '#/profile';
  } else {
    // Default - chuyển đến dashboard
    window.location.hash = '#/dashboard';
  }
};

export const markNotificationRead = async (id) => {
  const notif = notifications.find(n => n.id === id);
  if (notif && !notif.read) {
    notif.read = true;
    const unreadCount = notifications.filter(n => !n.read).length;
    updateNotificationBadge(unreadCount);
    renderNotifications();
    await markNotificationReadAPI(id);
    localStorage.setItem('ticketing.notifications', JSON.stringify(notifications.slice(0, 50)));
  }
};

export const markAllNotificationsRead = async () => {
  notifications.forEach(n => n.read = true);
  updateNotificationBadge(0);
  renderNotifications();
  await markAllNotificationsReadAPI();
  localStorage.setItem('ticketing.notifications', JSON.stringify(notifications.slice(0, 50)));
};

export const clearAllNotifications = async () => {
  await deleteAllNotificationsAPI();
  notifications = [];
  updateNotificationBadge(0);
  renderNotifications();
  localStorage.setItem('ticketing.notifications', JSON.stringify(notifications));
};

export const toggleNotificationDropdown = () => {
  notificationDropdown = document.getElementById('notification-dropdown');
  if (notificationDropdown) {
    notificationDropdown.classList.toggle('hidden');
    if (!notificationDropdown.classList.contains('hidden')) {
      markAllNotificationsRead();
    }
  }
};

export const closeNotificationDropdown = () => {
  notificationDropdown = document.getElementById('notification-dropdown');
  if (notificationDropdown) {
    notificationDropdown.classList.add('hidden');
  }
};

export const getNotifications = () => notifications;
export const setNotifications = (notifs) => { notifications = notifs; };
export const getNotificationPollingTimer = () => notificationPollingTimer;
export const setNotificationPollingTimer = (timer) => { notificationPollingTimer = timer; };
