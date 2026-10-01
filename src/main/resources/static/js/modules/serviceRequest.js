/**
 * Service Request Module - Quản lý Service Requests & Catalog
 */
const ServiceRequest = (() => {
    'use strict';

    const API_BASE = '/api/service-requests';
    const CATALOG_API = '/api/service-requests/catalog';

    // State
    let catalog = [];
    let requests = [];
    let isSubmitting = false;

    // ==================== Initialization ====================

    function init() {
        if (typeof window.Auth === 'undefined') {
            console.error('Auth module not found');
            return;
        }

        window.Auth.onAuthReady(() => {
            setupEventListeners();
            loadCatalog();
            loadRequests();
        });
    }

    function setupEventListeners() {
        document.addEventListener('click', (e) => {
            if (e.target.closest('#refresh-sr-btn')) {
                loadRequests();
            }
            if (e.target.closest('.sr-create-btn')) {
                openCreateModal();
            }
            if (e.target.closest('.sr-view-btn')) {
                const id = parseInt(e.target.closest('.sr-view-btn').dataset.id);
                viewRequest(id);
            }
            if (e.target.closest('.sr-catalog-item')) {
                const id = parseInt(e.target.closest('.sr-catalog-item').dataset.id);
                selectService(id);
            }
        });

        document.addEventListener('submit', (e) => {
            if (e.target.id === 'sr-form') {
                e.preventDefault();
                submitRequestForm();
            }
        });

        document.addEventListener('click', (e) => {
            if (e.target.closest('#close-sr-modal') || e.target.id === 'sr-modal') {
                closeModal();
            }
            if (e.target.closest('#cancel-sr-btn')) {
                closeModal();
            }
        });
    }

    // ==================== API Calls ====================

    async function loadCatalog() {
        try {
            const response = await fetch(CATALOG_API, { headers: window.Auth.getAuthHeaders() });
            if (!response.ok) throw new Error('Failed to load catalog');
            catalog = await response.json();
            renderCatalog();
        } catch (error) {
            console.error('Error loading catalog:', error);
        }
    }

    async function loadRequests(params = {}) {
        try {
            // Try to load user's own requests first
            let url = `${API_BASE}/my`;
            const queryParams = new URLSearchParams();
            
            if (params.status) queryParams.append('status', params.status);
            if (params.page) queryParams.append('page', params.page);
            queryParams.append('size', '20');
            
            if (queryParams.toString()) {
                url += '?' + queryParams.toString();
            }

            const response = await fetch(url, { headers: window.Auth.getAuthHeaders() });
            if (!response.ok) throw new Error('Failed to load requests');
            const data = await response.json();
            requests = data.content || [];
            renderRequestsList(requests);
        } catch (error) {
            console.error('Error loading requests:', error);
        }
    }

    async function getRequestById(id) {
        const response = await fetch(`${API_BASE}/${id}`, { headers: window.Auth.getAuthHeaders() });
        if (!response.ok) throw new Error('Failed to load request');
        return await response.json();
    }

    async function createRequest(data) {
        const response = await fetch(`${API_BASE}/my`, {
            method: 'POST',
            headers: window.Auth.getAuthHeaders(),
            body: JSON.stringify(data)
        });
        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.message || 'Failed to create request');
        }
        return await response.json();
    }

    async function takeOwnership(id) {
        const response = await fetch(`${API_BASE}/${id}/take-ownership`, {
            method: 'PATCH',
            headers: window.Auth.getAuthHeaders()
        });
        if (!response.ok) throw new Error('Failed to take ownership');
        return await response.json();
    }

    async function completeRequest(id, resolution) {
        const response = await fetch(`${API_BASE}/${id}/complete`, {
            method: 'PATCH',
            headers: window.Auth.getAuthHeaders(),
            body: JSON.stringify({ resolution })
        });
        if (!response.ok) throw new Error('Failed to complete request');
        return await response.json();
    }

    async function confirmCompletion(id) {
        const response = await fetch(`${API_BASE}/${id}/confirm`, {
            method: 'PATCH',
            headers: window.Auth.getAuthHeaders()
        });
        if (!response.ok) throw new Error('Failed to confirm completion');
        return await response.json();
    }

    async function reopenRequest(id, reason) {
        const response = await fetch(`${API_BASE}/${id}/reopen`, {
            method: 'PATCH',
            headers: window.Auth.getAuthHeaders(),
            body: JSON.stringify({ reason })
        });
        if (!response.ok) throw new Error('Failed to reopen request');
        return await response.json();
    }

    async function getComments(requestId) {
        const response = await fetch(`${API_BASE}/${requestId}/comments`, { headers: window.Auth.getAuthHeaders() });
        if (!response.ok) throw new Error('Failed to load comments');
        return await response.json();
    }

    async function addComment(requestId, body, isInternal) {
        const response = await fetch(`${API_BASE}/${requestId}/comments`, {
            method: 'POST',
            headers: window.Auth.getAuthHeaders(),
            body: JSON.stringify({ body, internal: isInternal })
        });
        if (!response.ok) throw new Error('Failed to add comment');
        return await response.json();
    }

    async function submitRequest(data) {
        const response = await fetch(API_BASE, {
            method: 'POST',
            headers: window.Auth.getAuthHeaders(),
            body: JSON.stringify(data)
        });
        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.message || 'Failed to submit request');
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

    // ==================== Rendering ====================

    function renderCatalog() {
        const container = document.getElementById('sr-catalog');
        if (!container) return;

        // Group by category
        const grouped = catalog.reduce((acc, service) => {
            const cat = service.category || 'Khác';
            if (!acc[cat]) acc[cat] = [];
            acc[cat].push(service);
            return acc;
        }, {});

        container.innerHTML = Object.entries(grouped).map(([category, services]) => `
            <div class="sr-category-section">
                <h4 class="sr-category-title">${category}</h4>
                <div class="sr-catalog-grid">
                    ${services.map(service => `
                        <div class="sr-catalog-item" data-id="${service.id}">
                            <div class="sr-item-icon" style="background: ${service.color || '#3498db'}20; color: ${service.color || '#3498db'}">
                                ${service.icon || '📋'}
                            </div>
                            <div class="sr-item-info">
                                <h5>${escapeHtml(service.name)}</h5>
                                <p>${escapeHtml(service.description || '').substring(0, 80)}${service.description?.length > 80 ? '...' : ''}</p>
                                <span class="sr-item-sla">
                                    ⏱️ ${formatSla(service.responseSlaMinutes)} - ${formatSla(service.resolutionSlaMinutes)}
                                </span>
                            </div>
                        </div>
                    `).join('')}
                </div>
            </div>
        `).join('');
    }

    function renderRequestsList(requestsList) {
        const container = document.getElementById('sr-list');
        if (!container) return;

        if (!requestsList || requestsList.length === 0) {
            container.innerHTML = '<div class="empty-state"><p>Chưa có yêu cầu nào.</p></div>';
            return;
        }

        const statusLabels = {
            'SUBMITTED': { label: 'Đã gửi', class: 'submitted' },
            'PENDING_APPROVAL': { label: 'Chờ phê duyệt', class: 'pending' },
            'APPROVED': { label: 'Đã duyệt', class: 'approved' },
            'IN_PROGRESS': { label: 'Đang xử lý', class: 'in-progress' },
            'COMPLETED': { label: 'Hoàn thành', class: 'completed' },
            'REJECTED': { label: 'Từ chối', class: 'rejected' },
            'CANCELLED': { label: 'Hủy', class: 'cancelled' }
        };

        container.innerHTML = requestsList.map(sr => {
            const status = statusLabels[sr.status] || statusLabels['SUBMITTED'];
            return `
                <div class="sr-card">
                    <div class="sr-card-header">
                        <span class="sr-number">${sr.requestNumber}</span>
                        <span class="sr-status ${status.class}">${status.label}</span>
                    </div>
                    <h4 class="sr-title">${escapeHtml(sr.title)}</h4>
                    <p class="sr-service">${escapeHtml(sr.serviceName || 'General Request')}</p>
                    <div class="sr-meta">
                        <span>👤 ${sr.requesterName || sr.requesterUsername || 'Unknown'}</span>
                        <span>📅 ${formatDate(sr.createdAt)}</span>
                    </div>
                    <div class="sr-actions">
                        <button class="secondary btn-sm sr-view-btn" data-id="${sr.id}">Xem</button>
                    </div>
                </div>
            `;
        }).join('');
    }

    // ==================== Modal ====================

    function selectService(serviceId) {
        const service = catalog.find(s => s.id === serviceId);
        if (!service) return;

        currentService = service;
        document.getElementById('sr-service-name').textContent = service.name;
        document.getElementById('sr-service-id').value = service.id;
        document.getElementById('sr-form-title').value = service.name;
        document.getElementById('sr-form-description').value = service.description || '';
        openModal();
    }

    let currentService = null;
    let currentRequest = null;

    async function viewRequest(id) {
        try {
            currentRequest = await getRequestById(id);
            const comments = await getComments(id);
            showRequestDetail(currentRequest, comments);
        } catch (error) {
            Toast.show('Không thể tải thông tin yêu cầu', 'error');
        }
    }

    function showRequestDetail(request, comments = []) {
        const statusLabels = {
            'SUBMITTED': 'Đã gửi',
            'PENDING_APPROVAL': 'Chờ phê duyệt',
            'APPROVED': 'Đã duyệt',
            'IN_PROGRESS': 'Đang xử lý',
            'COMPLETED': 'Hoàn thành',
            'CLOSED': 'Đã đóng',
            'REJECTED': 'Từ chối',
            'CANCELLED': 'Hủy bỏ'
        };

        const currentUser = window.Auth?.getCurrentUser?.();
        const isITStaff = currentUser && ['ADMIN', 'GIAM_DOC', 'TRUONG_PHONG', 'NHAN_VIEN'].includes(currentUser.role);
        const isAssignedTo = request.assignedTo === currentUser?.username;
        const isRequester = request.requesterUsername === currentUser?.username;

        const canTake = !request.assignedTo && ['SUBMITTED', 'PENDING_APPROVAL', 'APPROVED'].includes(request.status);
        const canComplete = isITStaff && ['IN_PROGRESS', 'APPROVED'].includes(request.status);
        const canConfirm = !isITStaff && request.status === 'COMPLETED';
        const canReopen = !isITStaff && ['COMPLETED', 'CLOSED'].includes(request.status);

        const container = document.getElementById('sr-detail-content');
        if (!container) return;

        container.innerHTML = `
            <div class="sr-detail-header">
                <div>
                    <span class="sr-number">${request.requestNumber}</span>
                    <h3>${escapeHtml(request.title)}</h3>
                </div>
                <button class="ghost" onclick="ServiceRequest.closeDetail()">Đóng</button>
            </div>

            <div class="sr-detail-status">
                <span class="sr-status ${request.status?.toLowerCase()}">${statusLabels[request.status] || request.status}</span>
                ${request.serviceName ? `<span>Dịch vụ: ${request.serviceName}</span>` : ''}
                <span>Người phụ trách: ${request.assignedTo || 'Chưa gán'}</span>
            </div>

            ${request.resolution ? `
            <div class="sr-resolution-box">
                <h4>Giải pháp</h4>
                <p>${escapeHtml(request.resolution)}</p>
            </div>
            ` : ''}

            <div class="sr-detail-section">
                <h4>Mô tả</h4>
                <p>${escapeHtml(request.description || 'Không có mô tả')}</p>
            </div>

            <!-- Action Buttons -->
            <div class="sr-detail-actions">
                ${canTake ? `
                    <button class="primary" onclick="ServiceRequest.handleTakeOwnership(${request.id})">
                        📥 Nhận yêu cầu
                    </button>
                ` : ''}
                ${canComplete ? `
                    <button class="primary" onclick="ServiceRequest.openCompleteModal(${request.id})">
                        ✓ Hoàn thành
                    </button>
                ` : ''}
                ${canConfirm ? `
                    <button class="success" onclick="ServiceRequest.handleConfirm(${request.id})">
                        ✓ Xác nhận hoàn thành
                    </button>
                ` : ''}
                ${canReopen ? `
                    <button class="warning" onclick="ServiceRequest.openReopenModal(${request.id})">
                        🔄 Yêu cầu mở lại
                    </button>
                ` : ''}
            </div>

            <!-- Complete Modal -->
            <div id="complete-modal-${request.id}" class="sr-action-modal hidden">
                <div class="modal-content">
                    <h4>Hoàn thành yêu cầu</h4>
                    <textarea id="complete-text-${request.id}" placeholder="Nhập mô tả giải pháp..." rows="4"></textarea>
                    <div class="modal-actions">
                        <button class="secondary" onclick="ServiceRequest.closeCompleteModal(${request.id})">Hủy</button>
                        <button class="primary" onclick="ServiceRequest.handleComplete(${request.id})">Xác nhận</button>
                    </div>
                </div>
            </div>

            <!-- Reopen Modal -->
            <div id="reopen-modal-${request.id}" class="sr-action-modal hidden">
                <div class="modal-content">
                    <h4>Yêu cầu mở lại</h4>
                    <textarea id="reopen-text-${request.id}" placeholder="Lý do mở lại..." rows="3"></textarea>
                    <div class="modal-actions">
                        <button class="secondary" onclick="ServiceRequest.closeReopenModal(${request.id})">Hủy</button>
                        <button class="warning" onclick="ServiceRequest.handleReopen(${request.id})">Gửi yêu cầu</button>
                    </div>
                </div>
            </div>

            <!-- Comments Section -->
            <div class="sr-detail-section">
                <h4>Ghi chú</h4>
                <div class="add-comment-form">
                    <textarea id="comment-text-${request.id}" placeholder="Nhập ghi chú..." rows="2"></textarea>
                    <label class="checkbox-label">
                        <input type="checkbox" id="comment-internal-${request.id}">
                        Ghi chú nội bộ (chỉ IT nhìn thấy)
                    </label>
                    <button class="secondary btn-sm" onclick="ServiceRequest.handleAddComment(${request.id})">Thêm ghi chú</button>
                </div>
                <div class="comments-list">
                    ${comments.length === 0 ? '<p class="muted">Chưa có ghi chú nào</p>' : ''}
                    ${comments.map(c => `
                        <div class="comment-item ${c.visibility === 'INTERNAL' ? 'internal' : ''}">
                            <div class="comment-header">
                                <span class="comment-author">${c.authorName || 'System'}</span>
                                <span class="comment-time">${formatDate(c.createdAt)}</span>
                                ${c.visibility === 'INTERNAL' ? '<span class="comment-badge">Nội bộ</span>' : ''}
                            </div>
                            <div class="comment-body">${escapeHtml(c.body)}</div>
                        </div>
                    `).join('')}
                </div>
            </div>

            <div class="sr-detail-section">
                <h4>Thông tin</h4>
                <div class="sr-info-grid">
                    <div><span>Người yêu cầu:</span> ${request.requesterName || request.requesterUsername || 'N/A'}</div>
                    <div><span>Phòng ban:</span> ${request.requesterDepartment || 'N/A'}</div>
                    <div><span>Email:</span> ${request.requesterEmail || 'N/A'}</div>
                    <div><span>Ngày tạo:</span> ${formatDate(request.createdAt)}</div>
                    <div><span>Deadline:</span> ${request.slaResolutionAt ? formatDate(request.slaResolutionAt) : 'N/A'}</div>
                </div>
            </div>
        `;

        document.getElementById('sr-detail-modal')?.classList.remove('hidden');
    }

    function closeDetail() {
        document.getElementById('sr-detail-modal')?.classList.add('hidden');
        currentRequest = null;
    }

    function openCompleteModal(id) {
        document.getElementById(`complete-modal-${id}`)?.classList.remove('hidden');
    }

    function closeCompleteModal(id) {
        document.getElementById(`complete-modal-${id}`)?.classList.add('hidden');
    }

    function openReopenModal(id) {
        document.getElementById(`reopen-modal-${id}`)?.classList.remove('hidden');
    }

    function closeReopenModal(id) {
        document.getElementById(`reopen-modal-${id}`)?.classList.add('hidden');
    }

    async function handleTakeOwnership(id) {
        try {
            await takeOwnership(id);
            Toast.show('Đã nhận yêu cầu', 'success');
            viewRequest(id);
            loadRequests();
        } catch (error) {
            Toast.show(error.message, 'error');
        }
    }

    async function handleComplete(id) {
        const resolution = document.getElementById(`complete-text-${id}`)?.value?.trim();
        try {
            await completeRequest(id, resolution);
            Toast.show('Đã hoàn thành yêu cầu', 'success');
            closeCompleteModal(id);
            viewRequest(id);
            loadRequests();
        } catch (error) {
            Toast.show(error.message, 'error');
        }
    }

    async function handleConfirm(id) {
        if (!confirm('Xác nhận hoàn thành yêu cầu này?')) return;
        try {
            await confirmCompletion(id);
            Toast.show('Đã xác nhận và đóng yêu cầu', 'success');
            closeDetail();
            loadRequests();
        } catch (error) {
            Toast.show(error.message, 'error');
        }
    }

    async function handleReopen(id) {
        const reason = document.getElementById(`reopen-text-${id}`)?.value?.trim();
        try {
            await reopenRequest(id, reason);
            Toast.show('Đã yêu cầu mở lại', 'success');
            closeReopenModal(id);
            viewRequest(id);
            loadRequests();
        } catch (error) {
            Toast.show(error.message, 'error');
        }
    }

    async function handleAddComment(id) {
        const body = document.getElementById(`comment-text-${id}`)?.value?.trim();
        const isInternal = document.getElementById(`comment-internal-${id}`)?.checked;
        if (!body) {
            Toast.show('Vui lòng nhập ghi chú', 'error');
            return;
        }
        try {
            await addComment(id, body, isInternal);
            Toast.show('Đã thêm ghi chú', 'success');
            document.getElementById(`comment-text-${id}`).value = '';
            viewRequest(id);
        } catch (error) {
            Toast.show(error.message, 'error');
        }
    }

    function openCreateModal() {
        currentService = null;
        resetForm();
        document.getElementById('sr-modal-title').textContent = 'Tạo Service Request';
        openModal();
    }

    function openModal() {
        const modal = document.getElementById('sr-modal');
        if (modal) {
            modal.classList.remove('hidden');
            document.body.classList.add('modal-open');
        }
    }

    function closeModal() {
        const modal = document.getElementById('sr-modal');
        if (modal) {
            modal.classList.add('hidden');
            document.body.classList.remove('modal-open');
        }
        currentService = null;
        resetForm();
    }

    function resetForm() {
        const form = document.getElementById('sr-form');
        if (form) form.reset();
    }

    async function submitRequestForm() {
        if (isSubmitting) return;
        isSubmitting = true;

        const submitBtn = document.getElementById('sr-submit-btn');
        if (submitBtn) {
            submitBtn.disabled = true;
            submitBtn.textContent = 'Đang gửi...';
        }

        const data = {
            title: document.getElementById('sr-form-title').value.trim(),
            description: document.getElementById('sr-form-description').value.trim(),
            serviceId: parseInt(document.getElementById('sr-service-id').value) || null
        };

        if (!data.title) {
            Toast.show('Vui lòng nhập tiêu đề', 'error');
            if (submitBtn) {
                submitBtn.disabled = false;
                submitBtn.textContent = 'Gửi yêu cầu';
            }
            isSubmitting = false;
            return;
        }

        try {
            await submitRequest(data);
            Toast.show('Đã gửi yêu cầu thành công!', 'success');
            closeModal();
            loadRequests();
        } catch (error) {
            Toast.show(error.message, 'error');
        } finally {
            if (submitBtn) {
                submitBtn.disabled = false;
                submitBtn.textContent = 'Gửi yêu cầu';
            }
            isSubmitting = false;
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
            hour: '2-digit',
            minute: '2-digit'
        });
    }

    function formatSla(minutes) {
        if (!minutes) return 'N/A';
        const hours = Math.floor(minutes / 60);
        const days = Math.floor(hours / 24);
        if (days > 0) return `${days}d`;
        if (hours > 0) return `${hours}h`;
        return `${minutes}m`;
    }

    // ==================== Public API ====================

    return {
        init,
        loadCatalog,
        loadRequests,
        viewRequest,
        openCreateModal,
        closeModal,
        closeDetail,
        handleTakeOwnership,
        handleComplete,
        handleConfirm,
        handleReopen,
        handleAddComment,
        openCompleteModal,
        closeCompleteModal,
        openReopenModal,
        closeReopenModal
    };
})();

// Auto-initialize
document.addEventListener('DOMContentLoaded', () => {
    setTimeout(() => ServiceRequest.init(), 100);
});

window.ServiceRequest = ServiceRequest;
export { ServiceRequest };
