// ==================== Ticket Service ====================
import { request } from '../core/api.js';
import { getToken } from '../core/auth.js';
import { formatStatus, formatName, getDisplayName, formatHours, formatSlaBucket } from '../ui/utils.js';
import { userAvatarMap, selectedTicket, engineerOptions } from '../config/constants.js';
import { setActiveReportButton, renderReportTable, setCurrentReport } from '../modules/reports.js';

let ticketsList, ticketDetails, ticketActions, commentPanel, ticketDetailsPanel, ticketBoard;
let ticketsSentinel, refreshStatus, commentsList, auditList, assignmentsList, attachmentsList;
let statusSelect, prioritySelect, assigneeInput;
let UNASSIGNED_VALUE = 'UNASSIGNED';

let ticketPage = 0;
const ticketPageSize = 20;
let ticketHasMore = true;
let ticketLoading = false;
let ticketsCache = [];
let selectedFiles = [];
let ticketSelectedFilesList = [];
let currentReport = null;
let autoRefreshTimer = null;
const autoRefreshKey = 'ticketing.autoRefresh';
const autoRefreshMs = 60000;

// ==================== Initialize Event Listeners ====================
export const initTicketEvents = () => {
  const updateStatusBtn = document.getElementById('update-status-btn');
  const updatePriorityBtn = document.getElementById('update-priority-btn');
  const updateAssigneeBtn = document.getElementById('update-assignee-btn');
  const assignMeBtn = document.getElementById('assign-me-btn');

  console.log('[initTicketEvents] Buttons found:', {
    updateStatusBtn: !!updateStatusBtn,
    updatePriorityBtn: !!updatePriorityBtn,
    updateAssigneeBtn: !!updateAssigneeBtn,
    assignMeBtn: !!assignMeBtn
  });

  // Remove any existing onclick attributes to avoid conflicts
  if (updateStatusBtn) updateStatusBtn.removeAttribute('onclick');
  if (updatePriorityBtn) updatePriorityBtn.removeAttribute('onclick');
  if (updateAssigneeBtn) updateAssigneeBtn.removeAttribute('onclick');
  if (assignMeBtn) assignMeBtn.removeAttribute('onclick');

  // Attach event listeners with error handling
  if (updateStatusBtn) {
    updateStatusBtn.onclick = async (e) => {
      e.preventDefault();
      e.stopPropagation();
      console.log('[initTicketEvents] Update status button clicked', {
        disabled: updateStatusBtn.disabled,
        selectedTicket: window.selectedTicket?.id
      });
      try {
        await updateStatus();
      } catch (err) {
        console.error('[initTicketEvents] updateStatus error:', err);
        alert('Lỗi: ' + err.message);
      }
    };
    console.log('[initTicketEvents] updateStatusBtn onclick set:', typeof updateStatusBtn.onclick);
  }

  if (updatePriorityBtn) {
    updatePriorityBtn.onclick = async (e) => {
      e.preventDefault();
      e.stopPropagation();
      console.log('[initTicketEvents] Update priority button clicked', {
        disabled: updatePriorityBtn.disabled,
        selectedTicket: window.selectedTicket?.id
      });
      try {
        await updatePriority();
      } catch (err) {
        console.error('[initTicketEvents] updatePriority error:', err);
        alert('Lỗi: ' + err.message);
      }
    };
  }

  if (updateAssigneeBtn) {
    updateAssigneeBtn.onclick = async (e) => {
      e.preventDefault();
      e.stopPropagation();
      console.log('[initTicketEvents] Update assignee button clicked', {
        disabled: updateAssigneeBtn.disabled,
        selectedTicket: window.selectedTicket?.id
      });
      try {
        await updateAssignee();
      } catch (err) {
        console.error('[initTicketEvents] updateAssignee error:', err);
        alert('Lỗi: ' + err.message);
      }
    };
  }

  if (assignMeBtn) {
    assignMeBtn.onclick = async (e) => {
      e.preventDefault();
      e.stopPropagation();
      console.log('[initTicketEvents] Assign to me button clicked', {
        disabled: assignMeBtn.disabled,
        selectedTicket: window.selectedTicket?.id
      });
      try {
        await assignToMe();
      } catch (err) {
        console.error('[initTicketEvents] assignToMe error:', err);
        alert('Lỗi: ' + err.message);
      }
    };
  }

  console.log('[initTicketEvents] All event listeners attached');
};

export const setSelectedTicket = (ticket) => {
  window.selectedTicket = ticket;
};

export const getSelectedTicket = () => window.selectedTicket;

export const getTicketPage = () => ticketPage;
export const setTicketPage = (page) => { ticketPage = page; };
export const getTicketHasMore = () => ticketHasMore;
export const setTicketHasMore = (value) => { ticketHasMore = value; };
export const getTicketLoading = () => ticketLoading;
export const setTicketLoading = (value) => { ticketLoading = value; };
export const getTicketsCache = () => ticketsCache;
export const setTicketsCache = (cache) => { ticketsCache = cache; };
export const getCurrentReport = () => currentReport;
export const getAutoRefreshTimer = () => autoRefreshTimer;
export const setAutoRefreshTimer = (timer) => { autoRefreshTimer = timer; };
export const getSelectedFiles = () => selectedFiles;
export const setSelectedFiles = (files) => { selectedFiles = files; };
export const getTicketSelectedFilesList = () => ticketSelectedFilesList;
export const setTicketSelectedFilesList = (files) => { ticketSelectedFilesList = files; };

const authHeaders = () => {
  const token = getToken();
  return token ? { Authorization: `Bearer ${token}` } : {};
};

export const renderTickets = (rows, { append = false } = {}) => {
  ticketsList = document.getElementById('tickets-list');
  if (!ticketsList) return;
  
  if (!append) {
    ticketsList.innerHTML = '';
  }
  rows.forEach((ticket, index) => {
    const row = document.createElement('div');
    row.className = 'ticket-row';
    row.style.animationDelay = `${index * 40}ms`;
    const createdAt = ticket.createdAt ? new Date(ticket.createdAt).toLocaleString('vi-VN') : 'Không rõ thời gian';
    const priorityClass = ticket.priority ? ticket.priority.toLowerCase() : 'low';
    const statusClass = ticket.status ? ticket.status.toLowerCase().replace(/_/g, '-') : 'new';
    const assigneeKey = ticket.assigneeName ? ticket.assigneeName.toLowerCase() : '';
    const assigneeProfile = assigneeKey ? userAvatarMap.get(assigneeKey) : null;
    const assigneeLabel = assigneeProfile && assigneeProfile.displayName
      ? assigneeProfile.displayName
      : (ticket.assigneeName ? formatName(ticket.assigneeName) : 'Chưa phân công');
    const avatarLetter = assigneeLabel.charAt(0).toUpperCase();
    const avatarImage = assigneeProfile && assigneeProfile.avatarUrl
      ? `<img src="${assigneeProfile.avatarUrl}" alt="${assigneeLabel}" />`
      : '';
    const deptLabel = ticket.departmentName || ticket.departmentCode || '';
    const deptBadge = deptLabel ? `<span class="dept-badge">${deptLabel}</span>` : '';
    
    row.innerHTML = `
      <div class="assignee-avatar" aria-hidden="true">
        ${avatarImage || avatarLetter}
      </div>
      <div class="ticket-row-main">
        <h4>${ticket.title}</h4>
        <div class="ticket-row-meta">${ticket.ticketNumber} • ${formatStatus(ticket.category)} ${deptBadge}</div>
      </div>
      <div class="ticket-row-tags">
        <span class="status ${statusClass}">${formatStatus(ticket.status)}</span>
        <span class="priority ${priorityClass}">${formatStatus(ticket.priority)}</span>
      </div>
      <div class="ticket-row-side">
        <strong>${assigneeLabel}</strong>
        <span>${createdAt}</span>
      </div>
    `;
    row.addEventListener('click', () => selectTicket(ticket));
    ticketsList.appendChild(row);
  });
};

export const loadTickets = async ({ reset = false } = {}) => {
  if (ticketLoading) {
    console.log('loadTickets: BLOCKED by ticketLoading flag');
    return;
  }
  console.log('loadTickets: Starting', { reset, ticketLoading });
  
  ticketsList = document.getElementById('tickets-list');
  ticketsSentinel = document.getElementById('tickets-sentinel');
  refreshStatus = document.getElementById('refresh-status');
  const filterAssignee = document.getElementById('filter-assignee');
  const filterStatus = document.getElementById('filter-status');
  const filterSearch = document.getElementById('filter-search');
  const filterSort = document.getElementById('filter-sort');
  
  if (reset) {
    ticketPage = 0;
    ticketHasMore = true;
    ticketsCache = [];
    if (ticketsList) ticketsList.innerHTML = '';
  }
  if (!ticketHasMore) return;
  ticketLoading = true;
  if (refreshStatus) {
    refreshStatus.textContent = 'Đang làm mới…';
  }
  const params = new URLSearchParams();
  let assigneeFallback = null;
  if (filterAssignee && filterAssignee.value) {
    const selectedOption = filterAssignee.selectedOptions[0];
    params.append('assignee', filterAssignee.value);
    if (selectedOption && selectedOption.dataset.username) {
      assigneeFallback = selectedOption.dataset.username;
    }
  }
  if (filterStatus.value) params.append('status', filterStatus.value);
  if (!filterStatus.value) {
    params.append('excludeClosed', 'true');
  }
  if (filterSearch && filterSearch.value.trim()) {
    params.append('search', filterSearch.value.trim());
  }
  params.append('page', String(ticketPage));
  params.append('size', String(ticketPageSize));
  const sortValue = filterSort && filterSort.value ? filterSort.value : 'createdAt,desc';
  if (!sortValue.startsWith('assigneeDisplay')) {
    params.append('sort', sortValue);
  } else {
    params.append('sort', 'createdAt,desc');
  }
  let data = await request(`/api/tickets?${params.toString()}`);
  if (assigneeFallback && data.content.length === 0 && assigneeFallback !== params.get('assignee')) {
    params.set('assignee', assigneeFallback);
    data = await request(`/api/tickets?${params.toString()}`);
  }
  const rows = data.content.slice();
  if (sortValue.startsWith('assigneeDisplay')) {
    ticketsCache = ticketsCache.concat(rows);
    const sorted = ticketsCache.slice().sort((a, b) => {
      const getAssigneeLabel = (ticket) => {
        if (!ticket || !ticket.assigneeName) return '';
        const key = ticket.assigneeName.toLowerCase();
        const profile = userAvatarMap.get(key);
        if (profile && profile.displayName) {
          return profile.displayName;
        }
        return formatName(ticket.assigneeName);
      };
      const left = getAssigneeLabel(a).toLowerCase();
      const right = getAssigneeLabel(b).toLowerCase();
      if (!left && right) return 1;
      if (!right && left) return -1;
      return left.localeCompare(right);
    });
    renderTickets(sorted, { append: false });
  } else {
    console.log('loadTickets: Calling renderTickets with', rows.length, 'rows');
    renderTickets(rows, { append: !reset });
  }
  ticketHasMore = !data.last;
  ticketPage = data.number + 1;
  if (ticketsSentinel) {
    ticketsSentinel.textContent = ticketHasMore ? 'Đang tải thêm…' : 'Hết danh sách';
    ticketsSentinel.classList.toggle('hidden', !ticketHasMore);
  }
  if (refreshStatus) {
    refreshStatus.textContent = `Cập nhật lần cuối lúc ${new Date().toLocaleTimeString('vi-VN')}`;
  }
  ticketLoading = false;
};

export const selectTicket = async (ticket) => {
  console.log('[selectTicket] START - ticket:', ticket);
  const full = await request(`/api/tickets/${ticket.id}`);
  window.selectedTicket = full;
  console.log('[selectTicket] window.selectedTicket set:', full.id, full.ticketNumber);
  
  ticketDetails = document.getElementById('ticket-details');
  ticketActions = document.getElementById('ticket-actions');
  commentPanel = document.getElementById('comment-panel');
  ticketDetailsPanel = document.getElementById('ticket-details-panel');
  ticketBoard = document.getElementById('ticket-board');
  commentsList = document.getElementById('comments-list');
  auditList = document.getElementById('audit-list');
  assignmentsList = document.getElementById('assignments-list');
  attachmentsList = document.getElementById('ticket-attachments-list');
  statusSelect = document.getElementById('status-select');
  prioritySelect = document.getElementById('priority-select');
  assigneeInput = document.getElementById('assignee-input');
  
  // Get buttons for debugging
  const btnUpdateStatus = document.getElementById('update-status-btn');
  const btnUpdatePriority = document.getElementById('update-priority-btn');
  const btnUpdateAssignee = document.getElementById('update-assignee-btn');
  const btnAssignMe = document.getElementById('assign-me-btn');
  console.log('[selectTicket] Buttons found:', {
    btnUpdateStatus: !!btnUpdateStatus,
    btnUpdatePriority: !!btnUpdatePriority,
    btnUpdateAssignee: !!btnUpdateAssignee,
    btnAssignMe: !!btnAssignMe
  });
  
  const createdAt = full.createdAt ? new Date(full.createdAt).toLocaleString('vi-VN') : 'Không rõ thời gian';
  const updatedAt = full.updatedAt ? new Date(full.updatedAt).toLocaleString('vi-VN') : 'Không rõ thời gian';
  const assignee = full.assigneeName ? formatName(full.assigneeName) : 'Chưa phân công';
  const deptInfo = full.departmentName ? `${full.departmentName} (${full.departmentCode})` : '';
  
  ticketDetails.innerHTML = `
    <div class="ticket-detail-card">
      <div class="ticket-detail-header">
        <h4>${full.title}</h4>
        <div class="ticket-detail-chips">
          <span class="status ${full.status ? full.status.toLowerCase().replace(/_/g, '-') : 'new'}">${formatStatus(full.status)}</span>
          <span class="priority ${full.priority ? full.priority.toLowerCase() : 'low'}">${formatStatus(full.priority)}</span>
          <span class="pill">${formatStatus(full.category)}</span>
          ${deptInfo ? `<span class="dept-badge">${deptInfo}</span>` : ''}
        </div>
      </div>
      <p>${full.description}</p>
      <div class="ticket-detail-grid">
        <div class="ticket-detail-field">
          <span>Người được phân công</span>
          <strong>${assignee}</strong>
        </div>
        <div class="ticket-detail-field">
          <span>Người yêu cầu</span>
          <strong>${formatName(full.requesterName)}</strong>
        </div>
        <div class="ticket-detail-field">
          <span>Mã phiếu</span>
          <strong>${full.ticketNumber}</strong>
        </div>
        <div class="ticket-detail-field">
          <span>Ngày tạo</span>
          <strong>${createdAt}</strong>
        </div>
        <div class="ticket-detail-field">
          <span>Cập nhật</span>
          <strong>${updatedAt}</strong>
        </div>
      </div>
    </div>
  `;
  
  const ticketTabButtons = Array.from(document.querySelectorAll('.tab-btn'));
  const ticketTabPanels = Array.from(document.querySelectorAll('[data-tab-panel]'));
  
  ticketActions.classList.remove('hidden');
  if (commentPanel) commentPanel.classList.remove('hidden');
  ticketDetailsPanel.classList.remove('hidden');
  if (ticketBoard) ticketBoard.classList.add('has-detail');
  
  ticketTabButtons.forEach((btn) => {
    btn.classList.toggle('active', btn.dataset.tab === 'comments');
  });
  ticketTabPanels.forEach((panel) => {
    panel.classList.toggle('active', panel.dataset.tabPanel === 'comments');
  });
  
  const setSelectValue = (select, value) => {
    if (!select) return;
    const options = Array.from(select.options);
    const match = options.some((option) => option.value === value);
    select.value = match ? value : options[0]?.value || '';
  };
  
  setSelectValue(statusSelect, full.status);
  setSelectValue(prioritySelect, full.priority);
  
  console.log('selectTicket: current engineerOptions length', engineerOptions.length);
  if (engineerOptions.length === 0) {
    console.log('selectTicket: Loading engineer options...');
    const { loadEngineerOptions } = await import('./userService.js');
    await loadEngineerOptions();
    console.log('selectTicket: After load, engineerOptions length', engineerOptions.length);
  }
  
  const { populateAssigneeSelect, ensureAssigneeOption } = await import('../ui/utils.js');
  // IMPORTANT: Must await populateAssigneeSelect before setting value
  await populateAssigneeSelect(assigneeInput, { includeUnassigned: true });
  ensureAssigneeOption(assigneeInput, full.assigneeName);
  assigneeInput.value = full.assigneeName || UNASSIGNED_VALUE;
  console.log('selectTicket: assigneeInput value set to:', assigneeInput.value, 'options count:', assigneeInput.options.length);
  
  const { applyRoleControls } = await import('./userService.js');
  applyRoleControls();
  
  // Re-enable buttons after role controls (in case they were disabled)
  const btns = ['update-status-btn', 'update-priority-btn', 'update-assignee-btn', 'assign-me-btn'];
  btns.forEach(id => {
    const btn = document.getElementById(id);
    if (btn) {
      btn.disabled = false;
      console.log(`[selectTicket] Button ${id} enabled, disabled=${btn.disabled}`);
    }
  });
  
  console.log('[selectTicket] Buttons re-enabled after role controls');
  console.log('[selectTicket] Final button states:', btns.map(id => {
    const btn = document.getElementById(id);
    return { id, disabled: btn?.disabled, onclick: btn?.onclick ? 'set' : 'null' };
  }));
  
  await Promise.all([loadComments(full.id), loadAudit(full.id), loadAssignments(full.id), loadAttachments(full.id)]);
};

export const loadComments = async (ticketId) => {
  const data = await request(`/api/tickets/${ticketId}/comments`);
  commentsList = document.getElementById('comments-list');
  if (!commentsList) return;
  commentsList.innerHTML = '';
  
  const { canViewInternalComments } = await import('./userService.js');
  const filteredData = canViewInternalComments() 
    ? data 
    : data.filter(c => c.visibility === 'PUBLIC');
  
  filteredData.forEach((comment) => {
    const item = document.createElement('div');
    item.className = 'list-item';
    const visibilityBadge = comment.visibility === 'INTERNAL' ? ' [Nội bộ]' : '';
    item.textContent = `${comment.actorName || 'Hệ thống'}${visibilityBadge}: ${comment.body}`;
    commentsList.appendChild(item);
  });
};

export const loadAudit = async (ticketId) => {
  const data = await request(`/api/tickets/${ticketId}/audit`);
  auditList = document.getElementById('audit-list');
  if (!auditList) return;
  auditList.innerHTML = '';
  
  const { formatFieldName, formatAuditValue, formatAuditAction } = await import('../ui/utils.js');
  
  data.forEach((entry) => {
    const item = document.createElement('div');
    item.className = 'list-item';
    const when = entry.createdAt ? new Date(entry.createdAt).toLocaleString('vi-VN') : 'không rõ thời gian';
    const actor = entry.actorName ? `bởi ${entry.actorName}` : '';
    const detail = `${formatFieldName(entry.fieldName)} ${formatAuditValue(entry.fieldName, entry.oldValue)} → ${formatAuditValue(entry.fieldName, entry.newValue)}`.trim();
    item.textContent = `${when} • ${formatAuditAction(entry.action)} ${actor} ${detail}`.trim();
    auditList.appendChild(item);
  });
};

export const downloadAuditCsv = async () => {
  if (!window.selectedTicket) return;
  const response = await fetch(`/api/tickets/${window.selectedTicket.id}/audit/export`, {
    headers: {
      ...authHeaders(),
    },
  });
  if (!response.ok) {
    throw new Error(`Xuất dữ liệu thất bại: ${response.status}`);
  }
  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = `phieu-${window.selectedTicket.id}-kiem-tra.csv`;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
};

export const loadAssignments = async (ticketId) => {
  const data = await request(`/api/tickets/${ticketId}/assignments`);
  assignmentsList = document.getElementById('assignments-list');
  if (!assignmentsList) return;
  assignmentsList.innerHTML = '';
  
  if (!data || data.length === 0) {
    assignmentsList.innerHTML = '<p class="muted">Chưa có lịch sử phân công.</p>';
    return;
  }
  
  data.forEach((assignment) => {
    const item = document.createElement('div');
    item.className = 'list-item';
    
    const when = assignment.createdAt ? new Date(assignment.createdAt).toLocaleString('vi-VN') : '';
    const actor = assignment.actorName ? `<span class="actor">bởi ${assignment.actorName}</span>` : '';
    const prev = assignment.previousAssignee ? formatName(assignment.previousAssignee) : '<span class="muted">Chưa phân công</span>';
    const next = assignment.newAssignee ? formatName(assignment.newAssignee) : '<span class="muted">Chưa phân công</span>';
    
    item.innerHTML = `
      <span class="time">${when}</span>
      <span class="assignment-change">${prev} → ${next}</span>
      ${actor}
    `;
    assignmentsList.appendChild(item);
  });
};

export const loadAttachments = async (ticketId) => {
  console.log('loadAttachments: Loading for ticketId =', ticketId);
  attachmentsList = document.getElementById('ticket-attachments-list');
  if (!attachmentsList) return;
  
  try {
    const data = await request(`/api/tickets/${ticketId}/attachments`);
    console.log('loadAttachments: Received data =', data);
    attachmentsList.innerHTML = '';
    
    if (!data || data.length === 0) {
      attachmentsList.innerHTML = '<p class="muted">Chưa có tệp đính kèm.</p>';
      return;
    }
    
    data.forEach((attachment) => {
      const item = document.createElement('div');
      item.className = 'attachment-item';
      
      const icon = getFileIcon(attachment.contentType);
      const size = formatFileSize(attachment.fileSize);
      const date = new Date(attachment.createdAt).toLocaleString('vi-VN');
      
      const downloadLink = document.createElement('a');
      downloadLink.href = '#';
      downloadLink.className = 'attachment-name';
      downloadLink.textContent = attachment.originalName;
      downloadLink.style.color = 'var(--accent)';
      downloadLink.style.textDecoration = 'none';
      downloadLink.style.fontWeight = '500';
      downloadLink.style.wordBreak = 'break-all';
      downloadLink.addEventListener('click', (e) => {
        e.preventDefault();
        downloadAttachmentFile(attachment.id, attachment.originalName, attachment.contentType);
      });
      
      item.innerHTML = `<span class="attachment-icon">${icon}</span>`;
      const infoDiv = document.createElement('div');
      infoDiv.className = 'attachment-info';
      infoDiv.appendChild(downloadLink);
      const metaSpan = document.createElement('span');
      metaSpan.className = 'attachment-meta';
      metaSpan.textContent = `${size} • ${date}`;
      infoDiv.appendChild(metaSpan);
      item.appendChild(infoDiv);
      attachmentsList.appendChild(item);
    });
  } catch (error) {
    console.error('loadAttachments: Error =', error);
    attachmentsList.innerHTML = '<p class="muted">Lỗi khi tải danh sách đính kèm.</p>';
  }
};

export const downloadAttachmentFile = async (attachmentId, fileName, contentType) => {
  try {
    const token = localStorage.getItem('ticketing.jwt');
    const response = await fetch(`/api/tickets/attachments/${attachmentId}/download`, {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${token}`
      }
    });
    
    if (!response.ok) {
      throw new Error('Download failed');
    }
    
    const blob = await response.blob();
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = fileName;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    window.URL.revokeObjectURL(url);
  } catch (error) {
    console.error('Download error:', error);
    alert('Lỗi khi tải tệp: ' + error.message);
  }
};

export const uploadAttachments = async (ticketId, files) => {
  if (!files || files.length === 0) return;
  
  const token = localStorage.getItem('ticketing.jwt');
  let uploadCount = 0;
  let errorCount = 0;
  
  for (const file of files) {
    const formData = new FormData();
    formData.append('file', file);
    
    try {
      const response = await fetch(`/api/tickets/${ticketId}/attachments`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${token}`
        },
        body: formData
      });
      
      if (response.ok) {
        uploadCount++;
        console.log(`Uploaded: ${file.name}`);
      } else {
        errorCount++;
        const errorText = await response.text();
        console.error(`Upload failed for ${file.name}:`, response.status, errorText);
        alert(`Lỗi khi tải lên tệp ${file.name}: ${errorText}`);
      }
    } catch (error) {
      errorCount++;
      console.error('Error uploading attachment:', error);
      alert(`Lỗi khi tải lên tệp ${file.name}: ${error.message}`);
    }
  }
  
  console.log(`Upload complete: ${uploadCount} success, ${errorCount} failed`);
  await loadAttachments(ticketId);
};

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

export const updateStatus = async () => {
  if (!window.selectedTicket) {
    console.log('[updateStatus] No ticket selected');
    return;
  }
  statusSelect = document.getElementById('status-select');
  const newStatus = statusSelect.value;
  const currentStatus = window.selectedTicket.status;
  
  console.log('[updateStatus] Current:', currentStatus, '-> New:', newStatus);
  
  // Always call API - let backend handle idempotency
  try {
    await request(`/api/tickets/${window.selectedTicket.id}/status`, {
      method: 'PATCH',
      body: JSON.stringify({ status: newStatus }),
    });
    console.log('[updateStatus] Success, reloading...');
    await loadTickets();
    await selectTicket({ id: window.selectedTicket.id });
  } catch (err) {
    console.error('[updateStatus] API error:', err);
    alert('Lỗi cập nhật trạng thái: ' + err.message);
  }
};

export const updatePriority = async () => {
  if (!window.selectedTicket) {
    console.log('[updatePriority] No ticket selected');
    return;
  }
  prioritySelect = document.getElementById('priority-select');
  const newPriority = prioritySelect.value;
  const currentPriority = window.selectedTicket.priority;
  
  console.log('[updatePriority] Current:', currentPriority, '-> New:', newPriority);
  
  try {
    await request(`/api/tickets/${window.selectedTicket.id}/priority`, {
      method: 'PATCH',
      body: JSON.stringify({ priority: newPriority }),
    });
    console.log('[updatePriority] Success, reloading...');
    await loadTickets();
    await selectTicket({ id: window.selectedTicket.id });
  } catch (err) {
    console.error('[updatePriority] API error:', err);
    alert('Lỗi cập nhật độ ưu tiên: ' + err.message);
  }
};

export const updateAssignee = async () => {
  if (!window.selectedTicket) {
    console.log('[updateAssignee] No ticket selected');
    return;
  }
  assigneeInput = document.getElementById('assignee-input');
  const { canAssignTickets } = await import('./userService.js');
  
  if (!canAssignTickets()) {
    alert('Bạn không có quyền phân công phiếu. Chỉ Trưởng phòng IT mới có quyền này.');
    return;
  }
  
  const normalizeAssigneeInput = (value) => {
    if (!value || value === UNASSIGNED_VALUE) {
      return null;
    }
    return value;
  };
  
  const assigneeName = normalizeAssigneeInput(assigneeInput.value);
  const currentAssignee = window.selectedTicket.assigneeName || null;
  
  console.log('[updateAssignee] Current:', currentAssignee, '-> New:', assigneeName);
  
  try {
    await request(`/api/tickets/${window.selectedTicket.id}/assignee`, {
      method: 'PATCH',
      body: JSON.stringify({ assigneeName }),
    });
    console.log('[updateAssignee] Success, reloading...');
    await loadTickets();
    await selectTicket({ id: window.selectedTicket.id });
  } catch (err) {
    console.error('[updateAssignee] API error:', err);
    alert('Lỗi cập nhật người phân công: ' + err.message);
  }
};

export const assignToMe = async () => {
  if (!window.selectedTicket) {
    console.log('[assignToMe] No ticket selected');
    return;
  }
  const { canAssignTickets, currentUser } = await import('./userService.js');
  
  if (!canAssignTickets()) {
    alert('Bạn không có quyền phân công phiếu. Chỉ Trưởng phòng IT mới có quyền này.');
    return;
  }
  
  console.log('[assignToMe] Assigning ticket to current user:', currentUser?.username);
  
  try {
    await request(`/api/tickets/${window.selectedTicket.id}/assign/me`, {
      method: 'POST',
    });
    console.log('[assignToMe] Success, reloading...');
    await loadTickets();
    await loadQueue();
    await selectTicket({ id: window.selectedTicket.id });
  } catch (err) {
    console.error('[assignToMe] API error:', err);
    alert('Lỗi giao phiếu: ' + err.message);
  }
};

export const addComment = async () => {
  if (!window.selectedTicket) return;
  const commentBody = document.getElementById('comment-body');
  const commentVisibility = document.getElementById('comment-visibility');
  const { canAddInternalComments, setFieldError, clearFieldError } = await import('../ui/utils.js');
  
  clearFieldError(commentBody);
  if (!commentBody.value || !commentBody.value.trim()) {
    setFieldError(commentBody, 'Bình luận là bắt buộc.');
    return;
  }
  if (!commentBody.checkValidity()) {
    setFieldError(commentBody, commentBody.validationMessage || 'Bình luận không hợp lệ.');
    return;
  }
  if (commentVisibility.value === 'INTERNAL' && !canAddInternalComments()) {
    alert('Bạn không có quyền thêm bình luận nội bộ. Chỉ nhân viên IT mới có quyền này.');
    return;
  }
  
  await request(`/api/tickets/${window.selectedTicket.id}/comments`, {
    method: 'POST',
    body: JSON.stringify({
      visibility: commentVisibility.value,
      body: commentBody.value,
    }),
  });
  commentBody.value = '';
  await loadComments(window.selectedTicket.id);
};

export const loadQueue = async () => {
  const queueList = document.getElementById('queue-list');
  const queuePanel = document.getElementById('queue-panel');
  if (!queueList) return;
  
  const { isStaff } = await import('./userService.js');
  if (!isStaff()) {
    queueList.innerHTML = '';
    if (queuePanel) queuePanel.classList.add('hidden');
    return;
  }
  if (queuePanel) queuePanel.classList.remove('hidden');
  
  const data = await request('/api/tickets/queue');
  queueList.innerHTML = '';
  if (!data.length) {
    const line = document.createElement('div');
    line.className = 'list-item';
    line.textContent = 'Hàng đợi chưa phân công đang trống.';
    queueList.appendChild(line);
    return;
  }
  data.forEach((ticket) => {
    const item = document.createElement('div');
    item.className = 'list-item';
    item.textContent = `${ticket.ticketNumber} • ${ticket.title}`;
    item.addEventListener('click', () => selectTicket(ticket));
    queueList.appendChild(item);
  });
};

export const loadDashboard = async () => {
  const dashboardCards = document.getElementById('dashboard-cards');
  const dashboardStatus = document.getElementById('dashboard-status');
  if (!dashboardCards) return;
  
  const data = await request(`/api/tickets/reports/dashboard?${buildDashboardParams()}`);
  dashboardCards.innerHTML = `
    <div class="dashboard-card">
      <h5>Đang mở</h5>
      <strong>${data.openCount}</strong>
    </div>
    <div class="dashboard-card">
      <h5>Đã đóng (30 ngày)</h5>
      <strong>${data.closedCount}</strong>
    </div>
    <div class="dashboard-card">
      <h5>Quá hạn</h5>
      <strong>${data.overdueCount}</strong>
    </div>
    <div class="dashboard-card">
      <h5>TG xử lý TB (giờ)</h5>
      <strong>${formatHours(data.avgCompletionHours)}</strong>
    </div>
  `;
  if (dashboardStatus) {
    dashboardStatus.innerHTML = '';
    if (data.openByStatus && data.openByStatus.length) {
      data.openByStatus.forEach((item) => {
        const line = document.createElement('div');
        line.className = 'list-item';
        line.textContent = `${formatStatus(item.status)}: ${item.count}`;
        dashboardStatus.appendChild(line);
      });
    }
  }
};

const buildDashboardParams = () => {
  const end = new Date();
  const start = new Date();
  start.setDate(end.getDate() - 30);
  const params = new URLSearchParams();
  params.append('from', start.toISOString());
  params.append('to', end.toISOString());
  return params.toString();
};

// Report functions
export const loadEngineerReport = async () => {
  const data = await request(`/api/tickets/reports/engineer-summary?${buildReportParams()}`);
  const rows = data.map((row) => ({
    'Kỹ sư': getDisplayName(row.engineer),
    'Phiếu được phân công': row.ticketsAssigned,
    'Phiếu đã hoàn thành': row.ticketsCompleted,
    'TB phân công/ngày': formatHours(row.avgAssignedPerDay),
    'TB hoàn thành/ngày': formatHours(row.avgCompletedPerDay),
    'TG hoàn thành TB (giờ)': formatHours(row.avgCompletionHours),
  }));
  const columns = [
    'Kỹ sư',
    'Phiếu được phân công',
    'Phiếu đã hoàn thành',
    'TB phân công/ngày',
    'TB hoàn thành/ngày',
    'TG hoàn thành TB (giờ)',
  ];
  setActiveReportButton(document.getElementById('engineer-report-btn'));
  setCurrentReport('bao-cao-ky-su', columns, rows);
  renderReportTable(columns, rows);
};

export const loadRequesterReport = async () => {
  const data = await request(`/api/tickets/reports/requester-summary?${buildReportParams()}`);
  const rows = data.map((row) => ({
    'Người yêu cầu': getDisplayName(row.requester),
    'Phiếu đã gửi': row.ticketsSubmitted,
    'TG hoàn thành TB (giờ)': formatHours(row.avgCompletionHours),
  }));
  const columns = ['Người yêu cầu', 'Phiếu đã gửi', 'TG hoàn thành TB (giờ)'];
  setActiveReportButton(document.getElementById('requester-report-btn'));
  setCurrentReport('bao-cao-nguoi-yeu-cau', columns, rows);
  renderReportTable(columns, rows);
};

export const loadBacklogReport = async () => {
  const data = await request(`/api/tickets/reports/backlog-aging?${buildReportParams()}`);
  const rows = data.map((row) => ({
    'Trạng thái': formatStatus(row.status),
    'Số lượng mở': row.openCount,
    'Thời gian tồn đọng TB (giờ)': formatHours(row.avgAgeHours),
  }));
  const columns = ['Trạng thái', 'Số lượng mở', 'Thời gian tồn đọng TB (giờ)'];
  setActiveReportButton(document.getElementById('backlog-report-btn'));
  setCurrentReport('thoi-gian-ton-dong', columns, rows);
  renderReportTable(columns, rows);
};

export const loadSlaReport = async () => {
  const data = await request(`/api/tickets/reports/sla-buckets?${buildReportParams()}`);
  const rows = data.map((row) => ({
    'Nhóm thời gian': formatSlaBucket(row.bucket),
    'Số lượng': row.count,
  }));
  const columns = ['Nhóm thời gian', 'Số lượng'];
  setActiveReportButton(document.getElementById('sla-report-btn'));
  setCurrentReport('nhom-sla', columns, rows);
  renderReportTable(columns, rows);
};

const buildReportParams = () => {
  const reportFrom = document.getElementById('report-from');
  const reportTo = document.getElementById('report-to');
  const params = new URLSearchParams();
  const toIsoDateTime = (value, endOfDay = false) => {
    if (!value) return '';
    if (value.includes('T')) return value;
    return `${value}T${endOfDay ? '23:59:59' : '00:00:00'}`;
  };
  const fromValue = toIsoDateTime(reportFrom.value, false);
  const toValue = toIsoDateTime(reportTo.value, true);
  if (fromValue) params.append('from', fromValue);
  if (toValue) params.append('to', toValue);
  return params.toString();
};

// Report state (shared with reports.js)
let reportState = {
  title: '',
  columns: [],
  rows: []
};

export const getReportState = () => reportState;
