// ==================== Notification Service ====================
import { API_BASE, tokenKey, NOTIFICATION_POLLING_MS } from '../config/constants.js';
import { request } from '../core/api.js';
import { setRoute } from '../core/router.js';
import { getToken, isTokenValid } from '../core/auth.js';
import { escapeHtml } from '../core/sanitize.js';

let notifications = [];
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
    'ACCOUNT_APPROVED': { type: 'approve', icon: '✓', category: 'account' },
    'ACCOUNT_REJECTED': { type: 'reject', icon: '✗', category: 'account' },
    'ACCOUNT_CREATED_PENDING': { type: 'pending', icon: '⏳', category: 'account' },
    'INFO': { type: 'info', icon: 'ℹ', category: 'general' },
    'PROFILE_CHANGED': { type: 'info', icon: '👤', category: 'profile' },
    // Ticket/Incident notifications
    'TICKET_CREATED': { type: 'info', icon: '📋', category: 'ticket' },
    'TICKET_ASSIGNED': { type: 'info', icon: '👤', category: 'ticket' },
    'TICKET_ASSIGNED_TO_TEAM': { type: 'info', icon: '👥', category: 'ticket' },
    'TICKET_UNASSIGNED': { type: 'info', icon: '👤', category: 'ticket' },
    'TICKET_REASSIGNED': { type: 'info', icon: '👤', category: 'ticket' },
    'TICKET_STATUS_CHANGED': { type: 'info', icon: '🔄', category: 'ticket' },
    'TICKET_IN_PROGRESS': { type: 'info', icon: '🔄', category: 'ticket' },
    'TICKET_WAITING_FOR_INFO': { type: 'info', icon: '⏳', category: 'ticket' },
    'TICKET_INFO_PROVIDED': { type: 'info', icon: '✅', category: 'ticket' },
    'TICKET_RESOLVED': { type: 'info', icon: '✅', category: 'ticket' },
    'TICKET_CLOSED': { type: 'info', icon: '🔒', category: 'ticket' },
    'TICKET_REOPENED': { type: 'info', icon: '🔓', category: 'ticket' },
    'TICKET_ESCALATED': { type: 'warning', icon: '⚠️', category: 'ticket' },
    'TICKET_COMMENT_ADDED': { type: 'info', icon: '💬', category: 'ticket' },
    'TICKET_SLA_WARNING': { type: 'warning', icon: '⏰', category: 'ticket' },
    'TICKET_SLA_BREACHED': { type: 'error', icon: '🚨', category: 'ticket' },
  };
  
  const mapping = typeMap[notif.type] || { type: 'info', icon: 'ℹ', category: 'general' };
  
  return {
    id: notif.id,
    type: notif.type, // Lưu type gốc từ backend
    category: mapping.category,
    displayType: mapping.type,
    icon: mapping.icon,
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
  notifications.forEach((notif) => {
    const item = document.createElement('div');
    item.className = `notification-item${notif.read ? '' : ' unread'}`;

    const timeAgo = getTimeAgo(notif.timestamp);
    const icon = notif.icon || 'ℹ';

    // Hiển thị ticket number nếu có
    const ticketInfo = notif.ticketNumber ? `<span class="notification-ticket-number">#${notif.ticketNumber}</span>` : '';
    const ticketIdAttr = notif.ticketId ? `data-ticket-id="${notif.ticketId}"` : '';
    const categoryAttr = `data-category="${notif.category || 'general'}"`;

    item.innerHTML = `
      <div class="notification-item-content">
        <span class="notification-icon">${escapeHtml(icon)}</span>
        <div class="notification-text">
          <div class="notification-item-title">${escapeHtml(notif.title || '')}${ticketInfo}</div>
          <div class="notification-item-message">${escapeHtml(notif.message || '')}</div>
        </div>
      </div>
      <div class="notification-item-footer">
        <span class="notification-item-time">${escapeHtml(timeAgo)}</span>
        ${!notif.read ? '<span class="notification-unread-dot"></span>' : ''}
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
  // Đánh dấu đã đọc
  await markNotificationRead(notif.id);
  closeNotificationDropdown();

  // Xác định route dựa trên category
  const category = notif.category || 'general';

  if (category === 'ticket' && notif.ticketId) {
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
              Toast.show('Không tìm thấy incident', 'error');
            }
          }
        }
      } catch (error) {
        console.error('Error loading incident from notification:', error);
        Toast.show('Không thể mở incident', 'error');
      }
    }, 500);
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
