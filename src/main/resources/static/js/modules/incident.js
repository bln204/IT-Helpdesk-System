/**
 * Incident Module - Quản lý Incidents
 */
const Incident = (() => {
    'use strict';

    const API_BASE = '/api/incidents';

    // State
    let incidents = [];
    let currentIncident = null;
    let isSubmitting = false;

    // ==================== Initialization ====================

    function init() {
        if (typeof window.Auth === 'undefined') {
            console.error('Auth module not found');
            return;
        }

        window.Auth.onAuthReady(() => {
            setupEventListeners();
            loadIncidents();
        });
    }

    function setupEventListeners() {
        document.addEventListener('click', (e) => {
            if (e.target.closest('#refresh-incidents-btn')) {
                loadIncidents();
            }
            if (e.target.closest('.incident-create-btn')) {
                openCreateModal();
            }
            if (e.target.closest('.incident-view-btn')) {
                const id = parseInt(e.target.closest('.incident-view-btn').dataset.id);
                viewIncident(id);
            }
            if (e.target.closest('.incident-edit-btn')) {
                const id = parseInt(e.target.closest('.incident-edit-btn').dataset.id);
                openEditModal(id);
            }
            if (e.target.closest('.incident-delete-btn')) {
                const id = parseInt(e.target.closest('.incident-delete-btn').dataset.id);
                confirmDelete(id);
            }
        });

        document.addEventListener('submit', (e) => {
            if (e.target.id === 'incident-form') {
                e.preventDefault();
                saveIncident();
            }
        });

        document.addEventListener('click', (e) => {
            if (e.target.closest('#close-incident-modal') || e.target.id === 'incident-modal') {
                closeModal();
            }
            if (e.target.closest('#cancel-incident-btn')) {
                closeModal();
            }
            if (e.target.closest('#close-incident-detail-modal')) {
                closeDetail();
            }
        });
    }

    // ==================== API Calls ====================

    async function loadIncidents(params = {}) {
        try {
            let url = API_BASE;
            const queryParams = new URLSearchParams();
            
            if (params.status) queryParams.append('status', params.status);
            if (params.search) queryParams.append('search', params.search);
            queryParams.append('page', params.page || 0);
            queryParams.append('size', params.size || 20);
            
            if (queryParams.toString()) {
                url += '?' + queryParams.toString();
            }

            const response = await fetch(url, { headers: window.Auth.getAuthHeaders() });
            if (!response.ok) throw new Error('Failed to load incidents');

            const data = await response.json();
            incidents = data.content || [];
            renderIncidentsList(incidents);
            renderPagination(data);
        } catch (error) {
            console.error('Error loading incidents:', error);
            if (typeof Toast !== 'undefined') {
                Toast.show('Không thể tải danh sách incidents', 'error');
            }
        }
    }

    // Getter cho incidents list
    function getIncidents() {
        return incidents;
    }

    async function getIncidentById(id) {
        const response = await fetch(`${API_BASE}/${id}`, { headers: window.Auth.getAuthHeaders() });
        if (!response.ok) throw new Error('Failed to load incident');
        return await response.json();
    }

    async function createIncident(data) {
        const response = await fetch(`${API_BASE}/my`, {
            method: 'POST',
            headers: window.Auth.getAuthHeaders(),
            body: JSON.stringify(data)
        });
        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.message || 'Failed to create incident');
        }
        return await response.json();
    }

    async function takeOwnership(incidentId) {
        const response = await fetch(`${API_BASE}/${incidentId}/take-ownership`, {
            method: 'PATCH',
            headers: window.Auth.getAuthHeaders()
        });
        if (!response.ok) throw new Error('Failed to take ownership');
        return await response.json();
    }

    async function resolveIncident(incidentId, resolution) {
        const response = await fetch(`${API_BASE}/${incidentId}/resolve`, {
            method: 'PATCH',
            headers: window.Auth.getAuthHeaders(),
            body: JSON.stringify({ resolution })
        });
        if (!response.ok) throw new Error('Failed to resolve incident');
        return await response.json();
    }

    async function confirmResolution(incidentId) {
        const response = await fetch(`${API_BASE}/${incidentId}/confirm-resolution`, {
            method: 'PATCH',
            headers: window.Auth.getAuthHeaders()
        });
        if (!response.ok) throw new Error('Failed to confirm resolution');
        return await response.json();
    }

    async function reopenIncident(incidentId, reason) {
        const response = await fetch(`${API_BASE}/${incidentId}/reopen`, {
            method: 'PATCH',
            headers: window.Auth.getAuthHeaders(),
            body: JSON.stringify({ reason })
        });
        if (!response.ok) throw new Error('Failed to reopen incident');
        return await response.json();
    }

    async function getTimeline(incidentId) {
        const response = await fetch(`${API_BASE}/${incidentId}/timeline`, { headers: window.Auth.getAuthHeaders() });
        if (!response.ok) throw new Error('Failed to load timeline');
        return await response.json();
    }

    async function addNote(incidentId, note) {
        const response = await fetch(`${API_BASE}/${incidentId}/notes`, {
            method: 'POST',
            headers: window.Auth.getAuthHeaders(),
            body: JSON.stringify({ note })
        });
        if (!response.ok) throw new Error('Failed to add note');
        return await response.json();
    }

    async function updateIncident(id, data) {
        const response = await fetch(`${API_BASE}/${id}`, {
            method: 'PUT',
            headers: window.Auth.getAuthHeaders(),
            body: JSON.stringify(data)
        });
        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.message || 'Failed to update incident');
        }
        return await response.json();
    }

    async function updateStatus(id, status) {
        const response = await fetch(`${API_BASE}/${id}/status`, {
            method: 'PATCH',
            headers: window.Auth.getAuthHeaders(),
            body: JSON.stringify({ status })
        });
        if (!response.ok) throw new Error('Failed to update status');
        return await response.json();
    }

    async function linkTicket(incidentId, ticketId, reason) {
        const response = await fetch(`${API_BASE}/${incidentId}/tickets`, {
            method: 'POST',
            headers: window.Auth.getAuthHeaders(),
            body: JSON.stringify({ ticketId, reason })
        });
        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.message || 'Failed to link ticket');
        }
        return await response.json();
    }

    async function getLinkedTickets(incidentId) {
        const response = await fetch(`${API_BASE}/${incidentId}/tickets`, { headers: window.Auth.getAuthHeaders() });
        if (!response.ok) throw new Error('Failed to load linked tickets');
        return await response.json();
    }

    async function assignIncident(incidentId, assigneeUsername) {
        const response = await fetch(`${API_BASE}/${incidentId}/assign`, {
            method: 'PATCH',
            headers: window.Auth.getAuthHeaders(),
            body: JSON.stringify({ assignee: assigneeUsername })
        });
        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.message || 'Failed to assign incident');
        }
        return await response.json();
    }

    // ==================== Problem Creation from Incident ====================

    async function createProblemFromIncident(incidentId, title, description) {
        const response = await fetch(`/api/problems/from-incident/${incidentId}`, {
            method: 'POST',
            headers: window.Auth.getAuthHeaders(),
            body: JSON.stringify({ title, description })
        });
        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.message || 'Failed to create problem from incident');
        }
        return await response.json();
    }

    async function deleteIncident(id) {
        const response = await fetch(`${API_BASE}/${id}`, {
            method: 'DELETE',
            headers: window.Auth.getAuthHeaders()
        });
        if (!response.ok) throw new Error('Failed to delete incident');
    }

    // ==================== Rendering ====================

    function renderIncidentsList(incidentsList) {
        const container = document.getElementById('incidents-list');
        if (!container) return;

        if (!incidentsList || incidentsList.length === 0) {
            container.innerHTML = `
                <div class="empty-state">
                    <p>Chưa có incident nào.</p>
                    <button class="secondary" onclick="Incident.openCreateModal()">Tạo Incident đầu tiên</button>
                </div>
            `;
            return;
        }

        const statusLabels = {
            'INVESTIGATING': { label: 'Đang điều tra', class: 'investigating' },
            'IN_PROGRESS': { label: 'Đang xử lý', class: 'in-progress' },
            'IDENTIFIED': { label: 'Đã xác định', class: 'identified' },
            'RESOLVED': { label: 'Đã giải quyết', class: 'resolved' },
            'CLOSED': { label: 'Đã đóng', class: 'closed' }
        };

        container.innerHTML = incidentsList.map(incident => {
            const status = statusLabels[incident.status] || statusLabels['INVESTIGATING'];
            const slaStatusClass = incident.slaStatus === 'BREACHED' ? 'sla-breached' : 
                                  incident.slaStatus === 'WARNING' ? 'sla-warning' : '';
            const slaBadge = incident.slaStatus === 'BREACHED' ? '<span class="sla-badge breached">⚠️ SLA</span>' : '';
            
            return `
                <div class="incident-card ${slaStatusClass}">
                    <div class="incident-card-header">
                        <span class="incident-number">${incident.incidentNumber}</span>
                        ${slaBadge}
                        <span class="incident-status ${status.class}">${status.label}</span>
                    </div>
                    <h4 class="incident-title">${escapeHtml(incident.title)}</h4>
                    <p class="incident-desc">${escapeHtml(incident.description || '').substring(0, 150)}${incident.description?.length > 150 ? '...' : ''}</p>
                    <div class="incident-meta">
                        <span>📎 ${incident.linkedTicketCount || 0} tickets</span>
                        <span>👤 ${incident.assignedToUsername || 'Chưa gán'}</span>
                        <span>📅 ${formatDate(incident.createdAt)}</span>
                        ${incident.priority ? `<span class="priority-${incident.priority.toLowerCase()}">⚡ ${incident.priority}</span>` : ''}
                    </div>
                    <div class="incident-actions">
                        <button class="secondary btn-sm incident-view-btn" data-id="${incident.id}">Xem</button>
                        <button class="ghost btn-sm incident-edit-btn" data-id="${incident.id}">Sửa</button>
                        <button class="ghost btn-sm text-danger incident-delete-btn" data-id="${incident.id}">Xóa</button>
                    </div>
                </div>
            `;
        }).join('');
    }

    function renderPagination(data) {
        const container = document.getElementById('incidents-pagination');
        if (!container) return;

        if (data.totalPages <= 1) {
            container.innerHTML = '';
            return;
        }

        let html = '';
        for (let i = 0; i < data.totalPages; i++) {
            html += `<button class="${i === data.number ? 'active' : ''}" onclick="Incident.loadIncidents({page: ${i}})">${i + 1}</button>`;
        }
        container.innerHTML = html;
    }

    // ==================== Modal ====================

    function openCreateModal() {
        currentIncident = null;
        resetForm();
        document.getElementById('incident-modal-title').textContent = 'Tạo Incident mới';
        document.getElementById('incident-submit-btn').textContent = 'Tạo Incident';
        openModal();
    }

    async function openEditModal(id) {
        try {
            currentIncident = await getIncidentById(id);
            populateForm(currentIncident);
            document.getElementById('incident-modal-title').textContent = 'Chỉnh sửa Incident';
            document.getElementById('incident-submit-btn').textContent = 'Lưu thay đổi';
            openModal();
        } catch (error) {
            Toast.show('Không thể tải thông tin incident', 'error');
        }
    }

    async function viewIncident(id) {
        try {
            currentIncident = await getIncidentById(id);
            const linkedTickets = await getLinkedTickets(id);
            const timeline = await getTimeline(id);
            
            showIncidentDetail(currentIncident, linkedTickets, timeline);
        } catch (error) {
            Toast.show('Không thể tải thông tin incident', 'error');
        }
    }

    function showIncidentDetail(incident, linkedTickets, timeline = []) {
        const statusLabels = {
            'INVESTIGATING': 'Đang điều tra',
            'IN_PROGRESS': 'Đang xử lý',
            'IDENTIFIED': 'Đã xác định',
            'RESOLVED': 'Đã giải quyết',
            'CLOSED': 'Đã đóng'
        };
        
        const currentUser = window.Auth?.getCurrentUser?.();
        
        // Kiểm tra user thuộc phòng IT
        const isInITDept = currentUser?.departmentCode === 'IT';
        // Trưởng phòng IT: ROLE_TRUONG_PHONG + phòng IT
        const isTruongPhongIT = currentUser?.role === 'TRUONG_PHONG' && isInITDept;
        // Nhân viên IT: ROLE_NHAN_VIEN + phòng IT
        const isNhanVienIT = currentUser?.role === 'NHAN_VIEN' && isInITDept;
        // Admin thuộc phòng IT
        const isAdminIT = currentUser?.role === 'ADMIN' && isInITDept;
        
        // Có thể nhận incident: Trưởng phòng IT hoặc Nhân viên IT
        const canTakeOwnership = !incident.assignedToUsername && (isTruongPhongIT || isNhanVienIT) && incident.status === 'INVESTIGATING';
        // Có thể giải quyết: Trưởng phòng IT hoặc Nhân viên IT
        const canResolve = (isTruongPhongIT || isNhanVienIT) && ['INVESTIGATING', 'IN_PROGRESS', 'IDENTIFIED'].includes(incident.status);
        // Có thể phân công: Trưởng phòng IT hoặc Admin IT (chỉ phòng IT)
        const canAssign = (isTruongPhongIT || isAdminIT) && !isAdminIT; // Admin IT không phân công, chỉ TP.IT
        // Thực tế: Admin + Trưởng phòng IT được phân công
        const canAssignIncident = (isTruongPhongIT || (currentUser?.role === 'ADMIN'));
        
        const isAssignedTo = incident.assignedToUsername === currentUser?.username;
        const canConfirmResolution = !isInITDept && incident.status === 'RESOLVED';
        const canReopen = !isInITDept && incident.status === 'RESOLVED';
        const isOwner = isTruongPhongIT || isNhanVienIT;
        // Cho phép tạo Problem từ incident đã RESOLVED hoặc CLOSED
        const canCreateProblem = ['RESOLVED', 'CLOSED'].includes(incident.status) && (isTruongPhongIT || isNhanVienIT || isAdminIT);

        const container = document.getElementById('incident-detail-content');
        if (!container) return;

        // SLA Timer HTML
        const slaTimerHtml = generateSlaTimerHtml(incident);

        container.innerHTML = `
            <div class="incident-detail-header">
                <div>
                    <span class="incident-number">${incident.incidentNumber}</span>
                    <h3>${escapeHtml(incident.title)}</h3>
                </div>
                <button class="ghost" onclick="Incident.closeDetail()">Đóng</button>
            </div>
            
            ${slaTimerHtml}
            
            <div class="incident-detail-status">
                <span class="incident-status ${incident.status?.toLowerCase().replace('_', '-')}">${statusLabels[incident.status] || incident.status}</span>
                <span>Priority: <strong>${incident.priority || 'N/A'}</strong></span>
                <span>Impact: ${incident.impactLevel || 'N/A'}</span>
            </div>
            
            <div class="incident-detail-body">
            
            ${incident.resolution ? `
            <div class="incident-resolution-box">
                <h4>Giải pháp</h4>
                <p>${escapeHtml(incident.resolution)}</p>
            </div>
            ` : ''}
            
            ${incident.rootCause ? `
            <div class="incident-detail-section">
                <h4>Nguyên nhân gốc rễ</h4>
                <p>${escapeHtml(incident.rootCause)}</p>
            </div>
            ` : ''}
            
            ${incident.workaround ? `
            <div class="incident-detail-section">
                <h4>Workaround</h4>
                <p>${escapeHtml(incident.workaround)}</p>
            </div>
            ` : ''}
            
            <div class="incident-detail-section">
                <h4>Mô tả</h4>
                <p>${escapeHtml(incident.description || 'Không có mô tả')}</p>
            </div>
            
            <!-- Action Buttons -->
            <div class="incident-detail-actions">
                ${canAssignIncident ? `
                    <button class="primary" onclick="Incident.openAssignModal(${incident.id})">
                        👤 Phân công
                    </button>
                ` : ''}
                ${canTakeOwnership ? `
                    <button class="primary" onclick="Incident.handleTakeOwnership(${incident.id})">
                        📥 Nhận incident
                    </button>
                ` : ''}
                ${canResolve ? `
                    <button class="success" onclick="Incident.openResolveModal(${incident.id})">
                        ✓ Giải quyết
                    </button>
                ` : ''}
                ${canConfirmResolution ? `
                    <button class="success" onclick="Incident.handleConfirmResolution(${incident.id})">
                        ✓ Xác nhận giải pháp
                    </button>
                ` : ''}
                ${canReopen ? `
                    <button class="warning" onclick="Incident.openReopenModal(${incident.id})">
                        🔄 Yêu cầu mở lại
                    </button>
                ` : ''}
                ${isOwner && incident.status !== 'CLOSED' ? `
                    <button class="danger" onclick="Incident.confirmDeleteFromDetail(${incident.id})">
                        🗑️ Xóa
                    </button>
                ` : ''}
                ${canCreateProblem ? `
                    <button class="warning" onclick="Incident.openCreateProblemModal(${incident.id})">
                        📋 Tạo Problem
                    </button>
                ` : ''}
            </div>
            
            <!-- Create Problem Modal -->
            <div id="create-problem-modal-${incident.id}" class="incident-action-modal hidden">
                <div class="modal-content" style="max-width: 500px;">
                    <h4>Tạo Problem từ Incident</h4>
                    <p style="font-size: 0.85rem; color: var(--muted); margin-bottom: 16px;">
                        Một Problem mới sẽ được tạo và tự động liên kết với incident này.
                    </p>
                    <label style="display: flex; flex-direction: column; gap: 6px; margin-bottom: 12px;">
                        <span style="font-size: 0.85rem; font-weight: 600;">Tiêu đề Problem:</span>
                        <input type="text" id="problem-title-${incident.id}" placeholder="VD: [Auto] Problem from ${incident.incidentNumber}" />
                    </label>
                    <label style="display: flex; flex-direction: column; gap: 6px; margin-bottom: 16px;">
                        <span style="font-size: 0.85rem; font-weight: 600;">Mô tả (tùy chọn):</span>
                        <textarea id="problem-description-${incident.id}" rows="3" placeholder="Mô tả chi tiết về problem..."></textarea>
                    </label>
                    <div class="modal-actions">
                        <button class="secondary" onclick="Incident.closeCreateProblemModal(${incident.id})">Hủy</button>
                        <button class="primary" onclick="Incident.handleCreateProblem(${incident.id})">Tạo Problem</button>
                    </div>
                </div>
            </div>
            
            <!-- Assign Modal -->
            <div id="assign-modal-${incident.id}" class="incident-action-modal hidden">
                <div class="modal-content">
                    <h4>Phân công Incident</h4>
                    <label style="display: flex; flex-direction: column; gap: 6px; margin-bottom: 12px;">
                        <span style="font-size: 0.85rem; font-weight: 600;">Chọn người phụ trách:</span>
                        <select id="assign-select-${incident.id}" style="padding: 10px; border-radius: 8px; border: 1px solid var(--border);">
                            <option value="">-- Chọn nhân viên --</option>
                        </select>
                    </label>
                    <div class="modal-actions">
                        <button class="secondary" onclick="Incident.closeAssignModal(${incident.id})">Hủy</button>
                        <button class="primary" onclick="Incident.handleAssign(${incident.id})">Phân công</button>
                    </div>
                </div>
            </div>
            
            <!-- Resolution Modal -->
            <div id="resolve-modal-${incident.id}" class="incident-action-modal hidden">
                <div class="modal-content">
                    <h4>Giải quyết Incident</h4>
                    <textarea id="resolve-text-${incident.id}" placeholder="Nhập giải pháp..." rows="4"></textarea>
                    <div class="modal-actions">
                        <button class="secondary" onclick="Incident.closeResolveModal(${incident.id})">Hủy</button>
                        <button class="primary" onclick="Incident.handleResolve(${incident.id})">Xác nhận</button>
                    </div>
                </div>
            </div>
            
            <!-- Reopen Modal -->
            <div id="reopen-modal-${incident.id}" class="incident-action-modal hidden">
                <div class="modal-content">
                    <h4>Yêu cầu mở lại incident</h4>
                    <textarea id="reopen-text-${incident.id}" placeholder="Lý do mở lại..." rows="3"></textarea>
                    <div class="modal-actions">
                        <button class="secondary" onclick="Incident.closeReopenModal(${incident.id})">Hủy</button>
                        <button class="warning" onclick="Incident.handleReopen(${incident.id})">Gửi yêu cầu</button>
                    </div>
                </div>
            </div>
            
            <!-- Linked Tickets -->
            <div class="incident-detail-section">
                <h4>Tickets liên quan (${linkedTickets.length})</h4>
                <div class="linked-tickets-list">
                    ${linkedTickets.length === 0 ? '<p class="muted">Chưa có ticket nào được liên kết</p>' : ''}
                    ${linkedTickets.map(t => `
                        <div class="linked-ticket-item">
                            <span class="ticket-number">${t.ticketNumber}</span>
                            <span class="ticket-title">${escapeHtml(t.title)}</span>
                            <span class="ticket-status ${t.status?.toLowerCase().replace('_', '-')}">${t.status}</span>
                        </div>
                    `).join('')}
                </div>
            </div>
            
            <!-- Add Note Section -->
            <div class="incident-detail-section">
                <h4>Ghi chú</h4>
                <div class="add-note-form">
                    <textarea id="note-text-${incident.id}" placeholder="Nhập ghi chú..." rows="2"></textarea>
                    <button class="secondary btn-sm" onclick="Incident.handleAddNote(${incident.id})">Thêm ghi chú</button>
                </div>
                <div class="timeline-list">
                    ${timeline.length === 0 ? '<p class="muted">Chưa có ghi chú nào</p>' : ''}
                    ${timeline.map(t => `
                        <div class="timeline-item ${t.eventType === 'NOTE_ADDED' ? 'note' : ''}">
                            <div class="timeline-header">
                                <span class="timeline-actor">${t.actorName || 'System'}</span>
                                <span class="timeline-time">${formatDate(t.createdAt)}</span>
                            </div>
                            <div class="timeline-content">${escapeHtml(t.description || t.title)}</div>
                        </div>
                    `).join('')}
                </div>
            </div>
            
            <div class="incident-detail-section">
                <h4>Thông tin</h4>
                <div class="incident-info-grid">
                    <div><span>Người phụ trách:</span> ${incident.assignedToUsername || 'Chưa gán'}</div>
                    <div><span>Team:</span> ${incident.teamName || 'N/A'}</div>
                    <div><span>Người báo:</span> ${incident.reportedByUsername || currentUser?.username || 'N/A'}</div>
                    <div><span>Ngày tạo:</span> ${formatDate(incident.createdAt)}</div>
                    <div><span>Đã giải quyết:</span> ${incident.resolvedAt ? formatDate(incident.resolvedAt) : 'Chưa'}</div>
                    <div><span>Đã đóng:</span> ${incident.closedAt ? formatDate(incident.closedAt) : 'Chưa'}</div>
                </div>
            </div>
            
            </div>
        `;
        
        document.getElementById('incident-detail-modal').classList.remove('hidden');
        
        // Start SLA timer if incident is active
        if (incident.status !== 'CLOSED' && incident.status !== 'RESOLVED') {
            startSlaTimer(incident);
        }
    }

    function generateSlaTimerHtml(incident) {
        if (!incident.resolutionDeadline && !incident.responseDeadline) {
            return '';
        }
        
        const slaStatus = incident.slaStatus || 'NORMAL';
        const statusClass = slaStatus === 'BREACHED' ? 'sla-breached' : (slaStatus === 'WARNING' ? 'sla-warning' : 'sla-normal');
        
        let html = `<div class="sla-timer-container ${statusClass}">`;
        
        if (incident.responseDeadline && incident.status !== 'RESOLVED' && incident.status !== 'CLOSED') {
            html += `
                <div class="sla-timer-item">
                    <span class="sla-label">Phản hồi:</span>
                    <span class="sla-deadline" id="response-deadline" data-deadline="${incident.responseDeadline}">
                        ${formatDeadline(incident.responseDeadline)}
                    </span>
                </div>
            `;
        }
        
        if (incident.resolutionDeadline && incident.status !== 'RESOLVED' && incident.status !== 'CLOSED') {
            html += `
                <div class="sla-timer-item">
                    <span class="sla-label">Giải quyết:</span>
                    <span class="sla-deadline" id="resolution-deadline" data-deadline="${incident.resolutionDeadline}">
                        ${formatDeadline(incident.resolutionDeadline)}
                    </span>
                </div>
            `;
        }
        
        if (slaStatus === 'BREACHED') {
            html += `<div class="sla-breach-badge">⚠️ SLA BREACHED</div>`;
        }
        
        html += '</div>';
        return html;
    }
    
    function formatDeadline(deadline) {
        if (!deadline) return 'N/A';
        const deadlineDate = new Date(deadline);
        return deadlineDate.toLocaleString('vi-VN', {
            day: '2-digit',
            month: '2-digit',
            hour: '2-digit',
            minute: '2-digit'
        });
    }
    
    let slaTimerInterval = null;
    
    function startSlaTimer(incident) {
        if (slaTimerInterval) {
            clearInterval(slaTimerInterval);
        }
        
        const updateTimer = () => {
            const responseEl = document.getElementById('response-deadline');
            const resolutionEl = document.getElementById('resolution-deadline');
            
            if (responseEl) {
                const deadline = new Date(responseEl.dataset.deadline);
                const now = new Date();
                const diff = deadline - now;
                
                if (diff <= 0) {
                    responseEl.textContent = '⚠️ HẾT HẠN';
                    responseEl.classList.add('breached');
                } else {
                    const hours = Math.floor(diff / (1000 * 60 * 60));
                    const minutes = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60));
                    responseEl.textContent = `${hours}h ${minutes}m`;
                    
                    if (diff < 60 * 60 * 1000) { // < 1 hour
                        responseEl.classList.add('warning');
                    }
                }
            }
            
            if (resolutionEl) {
                const deadline = new Date(resolutionEl.dataset.deadline);
                const now = new Date();
                const diff = deadline - now;
                
                if (diff <= 0) {
                    resolutionEl.textContent = '⚠️ HẾT HẠN';
                    resolutionEl.classList.add('breached');
                } else {
                    const hours = Math.floor(diff / (1000 * 60 * 60));
                    const minutes = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60));
                    resolutionEl.textContent = `${hours}h ${minutes}m`;
                    
                    if (diff < 60 * 60 * 1000) { // < 1 hour
                        resolutionEl.classList.add('warning');
                    }
                }
            }
        };
        
        updateTimer();
        slaTimerInterval = setInterval(updateTimer, 60000); // Update every minute
    }

    function openResolveModal(incidentId) {
        const modal = document.getElementById(`resolve-modal-${incidentId}`);
        if (modal) modal.classList.remove('hidden');
    }
    
    function closeResolveModal(incidentId) {
        const modal = document.getElementById(`resolve-modal-${incidentId}`);
        if (modal) modal.classList.add('hidden');
    }
    
    function openReopenModal(incidentId) {
        const modal = document.getElementById(`reopen-modal-${incidentId}`);
        if (modal) modal.classList.remove('hidden');
    }
    
    function closeReopenModal(incidentId) {
        const modal = document.getElementById(`reopen-modal-${incidentId}`);
        if (modal) modal.classList.add('hidden');
    }

    function openCreateProblemModal(incidentId) {
        const modal = document.getElementById(`create-problem-modal-${incidentId}`);
        if (modal) {
            // Pre-fill title
            const titleInput = document.getElementById(`problem-title-${incidentId}`);
            if (titleInput && currentIncident) {
                titleInput.value = `[Auto] Problem from ${currentIncident.incidentNumber}`;
            }
            modal.classList.remove('hidden');
        }
    }

    function closeCreateProblemModal(incidentId) {
        const modal = document.getElementById(`create-problem-modal-${incidentId}`);
        if (modal) modal.classList.add('hidden');
    }

    async function handleCreateProblem(incidentId) {
        const title = document.getElementById(`problem-title-${incidentId}`)?.value?.trim();
        const description = document.getElementById(`problem-description-${incidentId}`)?.value?.trim();

        if (!title) {
            Toast.show('Vui lòng nhập tiêu đề Problem', 'error');
            return;
        }

        try {
            const problem = await createProblemFromIncident(incidentId, title, description);
            Toast.show(`Đã tạo Problem: ${problem.problemNumber}`, 'success');
            closeCreateProblemModal(incidentId);
            closeDetail();
            // Navigate to Problems page
            if (typeof window.showView === 'function') {
                window.showView('problems');
            } else if (typeof window.navigateTo === 'function') {
                window.navigateTo('problems');
            }
        } catch (error) {
            Toast.show(error.message || 'Lỗi khi tạo Problem', 'error');
        }
    }

    async function handleTakeOwnership(incidentId) {
        try {
            await takeOwnership(incidentId);
            Toast.show('Đã nhận incident', 'success');
            if (slaTimerInterval) clearInterval(slaTimerInterval);
            viewIncident(incidentId);
            loadIncidents();
        } catch (error) {
            Toast.show(error.message, 'error');
        }
    }

    async function handleResolve(incidentId) {
        const resolution = document.getElementById(`resolve-text-${incidentId}`)?.value?.trim();
        if (!resolution) {
            Toast.show('Vui lòng nhập giải pháp', 'error');
            return;
        }
        
        try {
            await resolveIncident(incidentId, resolution);
            Toast.show('Đã giải quyết incident', 'success');
            if (slaTimerInterval) clearInterval(slaTimerInterval);
            closeResolveModal(incidentId);
            viewIncident(incidentId);
            loadIncidents();
        } catch (error) {
            Toast.show(error.message, 'error');
        }
    }

    async function handleConfirmResolution(incidentId) {
        if (!confirm('Bạn có chắc chắn xác nhận giải pháp này?\nIncident sẽ được đóng.')) {
            return;
        }
        
        try {
            await confirmResolution(incidentId);
            Toast.show('Đã xác nhận và đóng incident', 'success');
            if (slaTimerInterval) clearInterval(slaTimerInterval);
            closeDetail();
            loadIncidents();
        } catch (error) {
            Toast.show(error.message, 'error');
        }
    }

    async function handleReopen(incidentId) {
        const reason = document.getElementById(`reopen-text-${incidentId}`)?.value?.trim();
        
        try {
            await reopenIncident(incidentId, reason);
            Toast.show('Đã yêu cầu mở lại incident', 'success');
            if (slaTimerInterval) clearInterval(slaTimerInterval);
            closeReopenModal(incidentId);
            viewIncident(incidentId);
            loadIncidents();
        } catch (error) {
            Toast.show(error.message, 'error');
        }
    }

    async function openAssignModal(incidentId) {
        const modal = document.getElementById(`assign-modal-${incidentId}`);
        if (modal) {
            // Load IT staff list
            await loadITStaffForAssign(incidentId);
            modal.classList.remove('hidden');
        }
    }
    
    async function loadITStaffForAssign(incidentId) {
        try {
            const response = await fetch('/api/users/engineers', { headers: window.Auth.getAuthHeaders() });
            if (!response.ok) throw new Error('Failed to load IT staff');
            const staff = await response.json();
            
            const select = document.getElementById(`assign-select-${incidentId}`);
            if (select) {
                select.innerHTML = '<option value="">-- Chọn nhân viên --</option>';
                staff.forEach(user => {
                    select.innerHTML += `<option value="${user.username}">${user.displayName || user.username} (${user.username})</option>`;
                });
            }
        } catch (error) {
            console.error('Error loading IT staff:', error);
            Toast.show('Không thể tải danh sách nhân viên IT', 'error');
        }
    }
    
    function closeAssignModal(incidentId) {
        const modal = document.getElementById(`assign-modal-${incidentId}`);
        if (modal) modal.classList.add('hidden');
    }
    
    async function handleAssign(incidentId) {
        const select = document.getElementById(`assign-select-${incidentId}`);
        const assigneeUsername = select?.value;
        
        if (!assigneeUsername) {
            Toast.show('Vui lòng chọn người phụ trách', 'error');
            return;
        }
        
        try {
            await assignIncident(incidentId, assigneeUsername);
            Toast.show('Đã phân công incident', 'success');
            closeAssignModal(incidentId);
            viewIncident(incidentId);
            loadIncidents();
        } catch (error) {
            Toast.show(error.message, 'error');
        }
    }

    async function handleAddNote(incidentId) {
        const note = document.getElementById(`note-text-${incidentId}`)?.value?.trim();
        if (!note) {
            Toast.show('Vui lòng nhập ghi chú', 'error');
            return;
        }
        
        try {
            await addNote(incidentId, note);
            Toast.show('Đã thêm ghi chú', 'success');
            document.getElementById(`note-text-${incidentId}`).value = '';
            viewIncident(incidentId);
        } catch (error) {
            Toast.show(error.message, 'error');
        }
    }

    function closeDetail() {
        document.getElementById('incident-detail-modal').classList.add('hidden');
        document.body.classList.remove('modal-open');
        currentIncident = null;
    }

    function openModal() {
        const modal = document.getElementById('incident-modal');
        if (modal) {
            modal.classList.remove('hidden');
            document.body.classList.add('modal-open');
        }
    }

    function closeModal() {
        const modal = document.getElementById('incident-modal');
        if (modal) {
            modal.classList.add('hidden');
            document.body.classList.remove('modal-open');
        }
        currentIncident = null;
        resetForm();
    }

    function resetForm() {
        const form = document.getElementById('incident-form');
        if (form) form.reset();
        document.getElementById('incident-id').value = '';
    }

    function populateForm(incident) {
        document.getElementById('incident-id').value = incident.id;
        document.getElementById('incident-title').value = incident.title || '';
        document.getElementById('incident-description').value = incident.description || '';
        document.getElementById('incident-priority').value = incident.priority || 'HIGH';
        document.getElementById('incident-impact').value = incident.impactLevel || 'LOW';
        document.getElementById('incident-affected-users').value = incident.affectedUsers || 0;
        document.getElementById('incident-root-cause').value = incident.rootCause || '';
        document.getElementById('incident-workaround').value = incident.workaround || '';
        document.getElementById('incident-resolution').value = incident.resolution || '';
    }

    function getFormData() {
        return {
            title: document.getElementById('incident-title').value.trim(),
            description: document.getElementById('incident-description').value.trim(),
            priority: document.getElementById('incident-priority').value,
            impactLevel: document.getElementById('incident-impact').value,
            affectedUsers: parseInt(document.getElementById('incident-affected-users').value) || 0,
            rootCause: document.getElementById('incident-root-cause').value.trim(),
            workaround: document.getElementById('incident-workaround').value.trim(),
            resolution: document.getElementById('incident-resolution').value.trim()
        };
    }

    async function saveIncident() {
        if (isSubmitting) return;
        isSubmitting = true;

        const submitBtn = document.getElementById('incident-submit-btn');
        if (submitBtn) {
            submitBtn.disabled = true;
            submitBtn.textContent = 'Đang xử lý...';
        }

        const data = getFormData();

        if (!data.title) {
            Toast.show('Vui lòng nhập tiêu đề', 'error');
            if (submitBtn) {
                submitBtn.disabled = false;
                submitBtn.textContent = currentIncident ? 'Lưu thay đổi' : 'Tạo Incident';
            }
            isSubmitting = false;
            return;
        }

        try {
            if (currentIncident) {
                await updateIncident(currentIncident.id, data);
                Toast.show('Đã cập nhật incident', 'success');
            } else {
                await createIncident(data);
                Toast.show('Đã tạo incident mới', 'success');
            }
            closeModal();
            loadIncidents();
        } catch (error) {
            Toast.show(error.message, 'error');
        } finally {
            if (submitBtn) {
                submitBtn.disabled = false;
                submitBtn.textContent = currentIncident ? 'Lưu thay đổi' : 'Tạo Incident';
            }
            isSubmitting = false;
        }
    }

    function confirmDelete(id) {
        if (confirm('Bạn có chắc muốn xóa incident này?\n\nHành động này không thể hoàn tác.')) {
            deleteIncident(id)
                .then(() => {
                    Toast.show('Đã xóa incident', 'success');
                    closeDetail();
                    loadIncidents();
                })
                .catch(error => {
                    Toast.show(error.message, 'error');
                });
        }
    }
    
    function confirmDeleteFromDetail(id) {
        if (confirm('Bạn có chắc muốn xóa incident này?\n\nHành động này không thể hoàn tác.')) {
            deleteIncident(id)
                .then(() => {
                    Toast.show('Đã xóa incident', 'success');
                    closeDetail();
                    loadIncidents();
                })
                .catch(error => {
                    Toast.show(error.message, 'error');
                });
        }
    }

    // ==================== Helpers ====================

    function escapeHtml(text) {
        if (!text) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    function formatDate(dateStr) {
        if (!dateStr) return 'N/A';
        return new Date(dateStr).toLocaleString('vi-VN', {
            day: '2-digit',
            month: '2-digit',
            year: 'numeric',
            hour: '2-digit',
            minute: '2-digit'
        });
    }

    // ==================== Public API ====================

    return {
        init,
        loadIncidents,
        getIncidents,
        openCreateModal,
        openEditModal,
        closeModal,
        closeDetail,
        viewIncident,
        handleTakeOwnership,
        handleResolve,
        handleConfirmResolution,
        handleReopen,
        handleAddNote,
        openResolveModal,
        closeResolveModal,
        openReopenModal,
        closeReopenModal,
        confirmDelete,
        confirmDeleteFromDetail,
        openAssignModal,
        closeAssignModal,
        handleAssign,
        openCreateProblemModal,
        closeCreateProblemModal,
        handleCreateProblem
    };
})();

// Auto-initialize
document.addEventListener('DOMContentLoaded', () => {
    setTimeout(() => Incident.init(), 100);
});

window.Incident = Incident;
export { Incident };
