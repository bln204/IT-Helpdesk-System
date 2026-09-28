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

    async function getIncidentById(id) {
        const response = await fetch(`${API_BASE}/${id}`, { headers: window.Auth.getAuthHeaders() });
        if (!response.ok) throw new Error('Failed to load incident');
        return await response.json();
    }

    async function createIncident(data) {
        const response = await fetch(API_BASE, {
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
            'IDENTIFIED': { label: 'Đã xác định', class: 'identified' },
            'RESOLVED': { label: 'Đã giải quyết', class: 'resolved' },
            'CLOSED': { label: 'Đã đóng', class: 'closed' }
        };

        container.innerHTML = incidentsList.map(incident => {
            const status = statusLabels[incident.status] || statusLabels['INVESTIGATING'];
            return `
                <div class="incident-card">
                    <div class="incident-card-header">
                        <span class="incident-number">${incident.incidentNumber}</span>
                        <span class="incident-status ${status.class}">${status.label}</span>
                    </div>
                    <h4 class="incident-title">${escapeHtml(incident.title)}</h4>
                    <p class="incident-desc">${escapeHtml(incident.description || '').substring(0, 150)}${incident.description?.length > 150 ? '...' : ''}</p>
                    <div class="incident-meta">
                        <span>📎 ${incident.linkedTicketCount || 0} tickets</span>
                        <span>👤 ${incident.assignedToUsername || 'Chưa gán'}</span>
                        <span>📅 ${formatDate(incident.createdAt)}</span>
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
            
            showIncidentDetail(currentIncident, linkedTickets);
        } catch (error) {
            Toast.show('Không thể tải thông tin incident', 'error');
        }
    }

    function showIncidentDetail(incident, linkedTickets) {
        const statusLabels = {
            'INVESTIGATING': 'Đang điều tra',
            'IDENTIFIED': 'Đã xác định',
            'RESOLVED': 'Đã giải quyết',
            'CLOSED': 'Đã đóng'
        };

        const container = document.getElementById('incident-detail-content');
        if (!container) return;

        container.innerHTML = `
            <div class="incident-detail-header">
                <div>
                    <span class="incident-number">${incident.incidentNumber}</span>
                    <h3>${escapeHtml(incident.title)}</h3>
                </div>
                <button class="ghost" onclick="Incident.closeDetail()">Đóng</button>
            </div>
            
            <div class="incident-detail-status">
                <span class="incident-status ${incident.status?.toLowerCase()}">${statusLabels[incident.status] || incident.status}</span>
                <span>Priority: ${incident.priority || 'N/A'}</span>
                <span>Impact: ${incident.impactLevel || 'N/A'}</span>
            </div>
            
            <div class="incident-detail-section">
                <h4>Mô tả</h4>
                <p>${escapeHtml(incident.description || 'Không có mô tả')}</p>
            </div>
            
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
            
            ${incident.resolution ? `
            <div class="incident-detail-section">
                <h4>Giải pháp</h4>
                <p>${escapeHtml(incident.resolution)}</p>
            </div>
            ` : ''}
            
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
            
            <div class="incident-detail-section">
                <h4>Thông tin</h4>
                <div class="incident-info-grid">
                    <div><span>Người phụ trách:</span> ${incident.assignedToUsername || 'Chưa gán'}</div>
                    <div><span>Team:</span> ${incident.teamName || 'N/A'}</div>
                    <div><span>Ngày tạo:</span> ${formatDate(incident.createdAt)}</div>
                    <div><span>Đã giải quyết:</span> ${incident.resolvedAt ? formatDate(incident.resolvedAt) : 'Chưa'}</div>
                </div>
            </div>
        `;
        
        document.getElementById('incident-detail-modal').classList.remove('hidden');
    }

    function closeDetail() {
        document.getElementById('incident-detail-modal').classList.add('hidden');
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
        openCreateModal,
        openEditModal,
        closeModal,
        closeDetail
    };
})();

// Auto-initialize
document.addEventListener('DOMContentLoaded', () => {
    setTimeout(() => Incident.init(), 100);
});

window.Incident = Incident;
export { Incident };
