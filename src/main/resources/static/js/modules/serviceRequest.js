/**
 * Service Request Module - Quản lý Service Requests & Catalog
 *
 * Quyền trên UI khớp ServiceRequestAuthorization (backend vẫn là ranh giới bảo mật):
 * - Mọi người: tạo, xem yêu cầu trong phạm vi được đọc, sửa/hủy yêu cầu của mình khi chưa xử lý,
 *   xác nhận/mở lại khi IT đã hoàn thành.
 * - IT (Trưởng phòng/Nhân viên phòng IT): nhận, phân công, sửa, hoàn thành, hủy.
 * - Trưởng phòng IT: duyệt/từ chối yêu cầu chờ phê duyệt.
 * - ADMIN: xóa.
 */
const ServiceRequest = (() => {
    'use strict';
    // Họ tên từ danh bạ (userService.getDisplayName); fallback về giá trị gốc khi chưa nạp.
    const nameOf = (u) => (u && window.displayNameOf ? window.displayNameOf(u) : u) || '';

    const API_BASE = '/api/service-requests';
    const CATALOG_API = '/api/service-requests/catalog';

    const STATUS_LABELS = {
        'SUBMITTED': { label: 'Đã gửi', class: 'submitted' },
        'PENDING_APPROVAL': { label: 'Chờ phê duyệt', class: 'pending' },
        'APPROVED': { label: 'Đã duyệt', class: 'approved' },
        'IN_PROGRESS': { label: 'Đang xử lý', class: 'in-progress' },
        'COMPLETED': { label: 'Hoàn thành', class: 'completed' },
        'CLOSED': { label: 'Đã đóng', class: 'closed' },
        'REJECTED': { label: 'Từ chối', class: 'rejected' },
        'CANCELLED': { label: 'Đã hủy', class: 'cancelled' }
    };
    const OPEN_STATUSES = ['SUBMITTED', 'PENDING_APPROVAL', 'APPROVED', 'IN_PROGRESS'];

    // State
    let catalog = [];
    let requests = [];
    let isSubmitting = false;
    let editingId = null;
    let searchTimer = null;
    let listenersAttached = false;

    // ==================== Initialization ====================

    function init() {
        if (typeof window.Auth === 'undefined') {
            console.error('Auth module not found');
            return;
        }

        window.Auth.onAuthReady(() => {
            // init() runs on every visit to the page; listeners must be attached only once.
            if (!listenersAttached) {
                listenersAttached = true;
                setupEventListeners();
            }
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
            if (e.target.closest('#close-sr-modal') || e.target.id === 'sr-modal' || e.target.closest('#cancel-sr-btn')) {
                closeModal();
            }
            if (e.target.closest('#close-sr-detail-modal') || e.target.id === 'sr-detail-modal') {
                closeDetail();
            }
        });

        document.addEventListener('submit', (e) => {
            if (e.target.id === 'sr-form') {
                e.preventDefault();
                submitRequestForm();
            }
        });

        document.getElementById('sr-status-filter')?.addEventListener('change', () => loadRequests());
        document.getElementById('sr-search')?.addEventListener('input', () => {
            clearTimeout(searchTimer);
            searchTimer = setTimeout(() => loadRequests(), 300);
        });
    }

    // ==================== API Calls ====================

    /** fetch wrapper: throws the backend's message (e.g. 403/409 reason) so the toast is meaningful. */
    async function api(url, method = 'GET', body) {
        const response = await fetch(url, {
            method,
            headers: window.Auth.getAuthHeaders(),
            ...(body !== undefined && { body: JSON.stringify(body) })
        });
        if (!response.ok) {
            const error = await response.json().catch(() => ({}));
            throw new Error(error.message || 'Thao tác thất bại');
        }
        return response.status === 204 ? null : response.json();
    }

    async function loadCatalog() {
        try {
            catalog = await api(CATALOG_API);
            renderCatalog();
        } catch (error) {
            console.error('Error loading catalog:', error);
        }
    }

    async function loadRequests() {
        try {
            // GET /api/service-requests is scoped server-side: IT/ban giám sát thấy tất cả,
            // trưởng phòng nghiệp vụ thấy phòng mình, nhân viên thấy yêu cầu của mình.
            const queryParams = new URLSearchParams({ size: '50' });
            const status = document.getElementById('sr-status-filter')?.value;
            const search = document.getElementById('sr-search')?.value?.trim();
            if (status) queryParams.append('status', status);
            if (search) queryParams.append('search', search);

            const data = await api(`${API_BASE}?${queryParams}`);
            requests = data.content || [];
            renderRequestsList(requests);
        } catch (error) {
            console.error('Error loading requests:', error);
        }
    }

    // ==================== Permissions ====================

    function permissions(request) {
        const me = window.Auth?.getCurrentUser?.();
        const isIt = !!window.Auth?.isItOperator?.();
        const isItLead = !!window.Auth?.isItLead?.();
        const isRequester = !!me && request.requesterUsername?.toLowerCase() === me.username?.toLowerCase();
        const s = request.status;
        return {
            canEdit: (isIt && OPEN_STATUSES.includes(s)) || (isRequester && ['SUBMITTED', 'PENDING_APPROVAL'].includes(s)),
            canApprove: isItLead && s === 'PENDING_APPROVAL',
            canTake: isIt && !request.assignedTo && ['SUBMITTED', 'APPROVED'].includes(s),
            canAssign: isIt && ['SUBMITTED', 'APPROVED', 'IN_PROGRESS'].includes(s),
            canComplete: isIt && s === 'IN_PROGRESS',
            canCancel: (isIt || isRequester) && OPEN_STATUSES.includes(s),
            canConfirm: isRequester && s === 'COMPLETED',
            canReopen: isRequester && ['COMPLETED', 'CLOSED'].includes(s),
            canDelete: !!window.Auth?.isAdmin?.(),
            canInternal: !!window.Auth?.seesAll?.()
        };
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
                <h4 class="sr-category-title">${escapeHtml(category)}</h4>
                <div class="sr-catalog-grid">
                    ${services.map(service => `
                        <div class="sr-catalog-item" data-id="${service.id}">

                            <div class="sr-item-info">
                                <h5>${escapeHtml(service.name)}</h5>
                                <p>${escapeHtml(service.description || '').substring(0, 80)}${service.description?.length > 80 ? '...' : ''}</p>
                                <span class="sr-item-sla">
                                    SLA: ${formatSla(service.responseSlaMinutes)} - ${formatSla(service.resolutionSlaMinutes)}
                                    ${service.requiresApproval ? ' · Cần phê duyệt' : ''}
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

        container.innerHTML = requestsList.map(sr => {
            const status = STATUS_LABELS[sr.status] || { label: sr.status, class: '' };
            return `
                <div class="sr-card">
                    <div class="sr-card-header">
                        <span class="sr-number">${escapeHtml(sr.requestNumber)}</span>
                        <span class="sr-status ${status.class}">${status.label}</span>
                    </div>
                    <h4 class="sr-title">${escapeHtml(sr.title)}</h4>
                    <p class="sr-service">${escapeHtml(sr.serviceName || 'Yêu cầu chung')}</p>
                    <div class="sr-meta">
                        <span>${escapeHtml(nameOf(sr.requesterUsername) || sr.requesterName || 'Không rõ')}</span>
                        <span>${sr.assignedTo ? 'Phụ trách: ' + escapeHtml(nameOf(sr.assignedTo)) : 'Chưa gán'}</span>
                        <span>${formatDate(sr.createdAt)}</span>
                    </div>
                    <div class="sr-actions">
                        <button class="secondary btn-sm sr-view-btn" data-id="${sr.id}">Xem</button>
                    </div>
                </div>
            `;
        }).join('');
    }

    // ==================== Detail ====================

    let currentRequest = null;

    let departmentNames = {};

    async function loadDepartmentNames() {
        if (Object.keys(departmentNames).length) return;
        try {
            const depts = await api('/api/departments');
            departmentNames = Object.fromEntries((depts || []).map(d => [d.code, d.name]));
        } catch (error) {
            // non-fatal: fall back to the code
        }
    }

    async function viewRequest(id) {
        try {
            await loadDepartmentNames();
            currentRequest = await api(`${API_BASE}/${id}`);
            const comments = await api(`${API_BASE}/${id}/comments`);
            showRequestDetail(currentRequest, comments);
        } catch (error) {
            Toast.show(error.message || 'Không thể tải thông tin yêu cầu', 'error');
        }
    }

    function actionModal(id, kind, title, body, confirmLabel, confirmClass, handler) {
        return `
            <div id="sr-${kind}-modal-${id}" class="incident-action-modal hidden">
                <div class="modal-content">
                    <h4>${title}</h4>
                    ${body}
                    <div class="modal-actions">
                        <button class="secondary" onclick="ServiceRequest.toggleActionModal('${kind}', ${id}, false)">Hủy</button>
                        <button class="${confirmClass}" onclick="ServiceRequest.${handler}(${id})">${confirmLabel}</button>
                    </div>
                </div>
            </div>`;
    }

    function showRequestDetail(request, comments = []) {
        const p = permissions(request);
        const status = STATUS_LABELS[request.status] || { label: request.status, class: '' };
        const id = request.id;

        const container = document.getElementById('sr-detail-content');
        if (!container) return;

        const btn = (show, cls, label, onclick) => show
            ? `<button class="${cls}" onclick="ServiceRequest.${onclick}">${label}</button>` : '';

        container.innerHTML = `
            <div class="sr-detail-header">
                <div>
                    <span class="sr-number">${escapeHtml(request.requestNumber)}</span>
                    <h3>${escapeHtml(request.title)}</h3>
                </div>
            </div>

            <div class="sr-detail-status">
                <span class="sr-status ${status.class}">${status.label}</span>
                ${request.serviceName ? `<span><span class="muted">Dịch vụ:</span> ${escapeHtml(request.serviceName)}</span>` : ''}
                <span><span class="muted">Người phụ trách:</span> ${escapeHtml(nameOf(request.assignedTo) || 'Chưa gán')}</span>
            </div>

            ${request.approvalNotes ? `
            <div class="sr-resolution-box">
                <h4>${request.status === 'REJECTED' ? 'Lý do từ chối' : 'Ghi chú phê duyệt'}</h4>
                <p>${escapeHtml(request.approvalNotes)}</p>
            </div>
            ` : ''}

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
            <div class="sr-detail-actions button-row">
                ${btn(p.canApprove, 'success', 'Phê duyệt', `toggleActionModal('approve', ${id}, true)`)}
                ${btn(p.canApprove, 'danger', 'Từ chối', `toggleActionModal('reject', ${id}, true)`)}
                ${btn(p.canTake, 'primary', 'Nhận yêu cầu', `handleTakeOwnership(${id})`)}
                ${btn(p.canAssign, 'secondary', 'Phân công', `openAssignModal(${id})`)}
                ${btn(p.canComplete, 'primary', 'Hoàn thành', `toggleActionModal('complete', ${id}, true)`)}
                ${btn(p.canConfirm, 'success', 'Xác nhận hoàn thành', `handleConfirm(${id})`)}
                ${btn(p.canReopen, 'warning', 'Yêu cầu mở lại', `toggleActionModal('reopen', ${id}, true)`)}
                ${btn(p.canEdit, 'ghost', 'Sửa', `openEditModal(${id})`)}
                ${btn(p.canCancel, 'warning', 'Hủy yêu cầu', `handleCancel(${id})`)}
                ${btn(p.canDelete, 'danger', 'Xóa', `handleDelete(${id})`)}
            </div>

            ${actionModal(id, 'approve', 'Phê duyệt yêu cầu',
                `<textarea id="sr-approve-text-${id}" placeholder="Ghi chú (không bắt buộc)..." rows="3"></textarea>`,
                'Phê duyệt', 'success', 'handleApprove')}
            ${actionModal(id, 'reject', 'Từ chối yêu cầu',
                `<textarea id="sr-reject-text-${id}" placeholder="Lý do từ chối..." rows="3"></textarea>`,
                'Từ chối', 'danger', 'handleReject')}
            ${actionModal(id, 'assign', 'Phân công yêu cầu', `
                    <label style="display: flex; flex-direction: column; gap: 6px; margin-bottom: 12px;">
                        <span style="font-size: 0.85rem; font-weight: 600;">Chọn người phụ trách:</span>
                        <select id="sr-assign-select-${id}" style="padding: 10px; border-radius: 8px; border: 1px solid var(--border);">
                            <option value="">-- Chọn nhân viên --</option>
                        </select>
                    </label>`,
                'Phân công', 'primary', 'handleAssign')}
            ${actionModal(id, 'complete', 'Hoàn thành yêu cầu',
                `<textarea id="sr-complete-text-${id}" placeholder="Nhập mô tả giải pháp..." rows="4"></textarea>`,
                'Xác nhận', 'primary', 'handleComplete')}
            ${actionModal(id, 'reopen', 'Yêu cầu mở lại',
                `<textarea id="sr-reopen-text-${id}" placeholder="Lý do mở lại..." rows="3"></textarea>`,
                'Gửi yêu cầu', 'warning', 'handleReopen')}

            <!-- Comments Section -->
            <div class="sr-detail-section">
                <h4>Ghi chú</h4>
                <div class="add-comment-form">
                    <textarea id="sr-comment-text-${id}" placeholder="Nhập ghi chú..." rows="2"></textarea>
                    ${p.canInternal ? `
                    <label class="checkbox-label">
                        <span class="toggle-switch">
                            <input type="checkbox" id="sr-comment-internal-${id}">
                            <span class="toggle-slider"></span>
                        </span>
                        Ghi chú nội bộ (chỉ IT nhìn thấy)
                    </label>` : ''}
                    <button class="secondary btn-sm" onclick="ServiceRequest.handleAddComment(${id})">Thêm ghi chú</button>
                </div>
                <div class="comments-list">
                    ${comments.length === 0 ? '<p class="muted">Chưa có ghi chú nào</p>' : ''}
                    ${comments.map(c => `
                        <div class="comment-item ${c.visibility === 'INTERNAL' ? 'internal' : ''}">
                            <div class="comment-header">
                                <span class="comment-author">${escapeHtml(nameOf(c.authorUsername) || c.authorName || 'Hệ thống')}</span>
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
                    <div><span>Người yêu cầu:</span> ${escapeHtml(nameOf(request.requesterUsername) || request.requesterName || 'N/A')}</div>
                    <div><span>Phòng ban:</span> ${escapeHtml(departmentNames[request.requesterDepartment] || request.requesterDepartment || 'N/A')}</div>
                    <div><span>Email:</span> ${escapeHtml(request.requesterEmail || 'N/A')}</div>
                    <div><span>Ngày tạo:</span> ${formatDate(request.createdAt)}</div>
                    <div><span>Hạn:</span> ${request.slaResolutionAt ? formatDate(request.slaResolutionAt) : 'N/A'}</div>
                </div>
            </div>
        `;

        document.getElementById('sr-detail-modal')?.classList.remove('hidden');
        document.body.classList.add('modal-open');
    }

    function closeDetail() {
        document.getElementById('sr-detail-modal')?.classList.add('hidden');
        document.body.classList.remove('modal-open');
        currentRequest = null;
    }

    function toggleActionModal(kind, id, show) {
        document.getElementById(`sr-${kind}-modal-${id}`)?.classList.toggle('hidden', !show);
    }

    // Same flow as Incident.openAssignModal: load IT staff first, then show the modal.
    async function openAssignModal(id) {
        try {
            const staff = await api('/api/users/engineers');
            const select = document.getElementById(`sr-assign-select-${id}`);
            if (select) {
                select.innerHTML = '<option value="">-- Chọn nhân viên --</option>' + staff
                    .map(u => `<option value="${escapeHtml(u.username)}" ${u.username === currentRequest?.assignedTo ? 'selected' : ''}>${escapeHtml(u.displayName || nameOf(u.username))}</option>`)
                    .join('');
            }
            toggleActionModal('assign', id, true);
        } catch (error) {
            console.error('Error loading IT staff:', error);
            Toast.show('Không thể tải danh sách nhân viên IT', 'error');
        }
    }

    /** Run an action, then refresh detail (or close it) and the list. */
    async function act(id, promise, successMsg, { close = false } = {}) {
        try {
            await promise;
            Toast.show(successMsg, 'success');
            if (close) closeDetail(); else viewRequest(id);
            loadRequests();
        } catch (error) {
            Toast.show(error.message, 'error');
        }
    }

    const textOf = (kind, id) => document.getElementById(`sr-${kind}-text-${id}`)?.value?.trim();

    function handleApprove(id) {
        act(id, api(`${API_BASE}/${id}/approve`, 'PATCH', { notes: textOf('approve', id) }), 'Đã phê duyệt yêu cầu');
    }

    function handleReject(id) {
        const reason = textOf('reject', id);
        if (!reason) {
            Toast.show('Vui lòng nhập lý do từ chối', 'error');
            return;
        }
        act(id, api(`${API_BASE}/${id}/reject`, 'PATCH', { notes: reason }), 'Đã từ chối yêu cầu');
    }

    function handleAssign(id) {
        const assignee = document.getElementById(`sr-assign-select-${id}`)?.value;
        if (!assignee) {
            Toast.show('Vui lòng chọn người phụ trách', 'error');
            return;
        }
        act(id, api(`${API_BASE}/${id}/assign`, 'PATCH', { assignee }), 'Đã phân công yêu cầu');
    }

    function handleTakeOwnership(id) {
        act(id, api(`${API_BASE}/${id}/take-ownership`, 'PATCH'), 'Đã nhận yêu cầu');
    }

    function handleComplete(id) {
        act(id, api(`${API_BASE}/${id}/complete`, 'PATCH', { resolution: textOf('complete', id) }), 'Đã hoàn thành yêu cầu');
    }

    async function handleConfirm(id) {
        if (!(await Dialog.confirm('Yêu cầu này đã được xử lý xong như bạn mong muốn?', { title: 'Xác nhận hoàn thành', note: 'Sau khi xác nhận, yêu cầu sẽ được đóng.', okText: 'Xác nhận', cancelText: 'Chưa' }))) return;
        act(id, api(`${API_BASE}/${id}/confirm`, 'PATCH'), 'Đã xác nhận và đóng yêu cầu', { close: true });
    }

    function handleReopen(id) {
        act(id, api(`${API_BASE}/${id}/reopen`, 'PATCH', { reason: textOf('reopen', id) }), 'Đã yêu cầu mở lại');
    }

    async function handleCancel(id) {
        if (!(await Dialog.confirm('Bạn có chắc muốn hủy yêu cầu này?', { title: 'Hủy yêu cầu', okText: 'Hủy yêu cầu', cancelText: 'Giữ lại', danger: true }))) return;
        act(id, api(`${API_BASE}/${id}/cancel`, 'PATCH'), 'Đã hủy yêu cầu');
    }

    async function handleDelete(id) {
        if (!(await Dialog.confirm('Bạn có chắc muốn xóa vĩnh viễn yêu cầu này?', { title: 'Xóa yêu cầu', note: 'Thao tác này không thể hoàn tác.', okText: 'Xóa', cancelText: 'Giữ lại', danger: true }))) return;
        act(id, api(`${API_BASE}/${id}`, 'DELETE'), 'Đã xóa yêu cầu', { close: true });
    }

    async function handleAddComment(id) {
        const body = textOf('comment', id);
        const isInternal = document.getElementById(`sr-comment-internal-${id}`)?.checked || false;
        if (!body) {
            Toast.show('Vui lòng nhập ghi chú', 'error');
            return;
        }
        try {
            await api(`${API_BASE}/${id}/comments`, 'POST', { body, internal: isInternal });
            Toast.show('Đã thêm ghi chú', 'success');
            viewRequest(id);
        } catch (error) {
            Toast.show(error.message, 'error');
        }
    }

    // ==================== Create / Edit Modal ====================

    function selectService(serviceId) {
        const service = catalog.find(s => s.id === serviceId);
        if (!service) return;

        resetForm();
        document.getElementById('sr-modal-title').textContent = 'Tạo yêu cầu dịch vụ';
        document.getElementById('sr-service-name').textContent = service.name;
        document.getElementById('sr-service-id').value = service.id;
        document.getElementById('sr-form-title').value = service.name;
        document.getElementById('sr-form-description').value = service.description || '';
        openModal();
    }

    function openCreateModal() {
        resetForm();
        document.getElementById('sr-modal-title').textContent = 'Tạo yêu cầu dịch vụ';
        document.getElementById('sr-service-name').textContent = 'Yêu cầu chung (không thuộc danh mục)';
        openModal();
    }

    function openEditModal(id) {
        const request = currentRequest;
        if (!request || request.id !== id) return;
        resetForm();
        editingId = id;
        document.getElementById('sr-modal-title').textContent = `Sửa yêu cầu ${request.requestNumber}`;
        document.getElementById('sr-service-name').textContent = request.serviceName || 'Yêu cầu chung';
        document.getElementById('sr-form-title').value = request.title || '';
        document.getElementById('sr-form-description').value = request.description || '';
        const submitBtn = document.getElementById('sr-submit-btn');
        if (submitBtn) submitBtn.textContent = 'Lưu thay đổi';
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
            // keep scroll lock while the detail modal is still open underneath
            if (document.getElementById('sr-detail-modal')?.classList.contains('hidden') !== false) {
                document.body.classList.remove('modal-open');
            }
        }
        resetForm();
    }

    function resetForm() {
        document.getElementById('sr-form')?.reset();
        // form.reset() does not clear a hidden input set via .value
        const serviceId = document.getElementById('sr-service-id');
        if (serviceId) serviceId.value = '';
        const submitBtn = document.getElementById('sr-submit-btn');
        if (submitBtn) submitBtn.textContent = 'Gửi yêu cầu';
        editingId = null;
    }

    async function submitRequestForm() {
        if (isSubmitting) return;

        const data = {
            title: document.getElementById('sr-form-title').value.trim(),
            description: document.getElementById('sr-form-description').value.trim()
        };
        if (!data.title) {
            Toast.show('Vui lòng nhập tiêu đề', 'error');
            return;
        }

        isSubmitting = true;
        const submitBtn = document.getElementById('sr-submit-btn');
        const idleLabel = submitBtn?.textContent;
        if (submitBtn) {
            submitBtn.disabled = true;
            submitBtn.textContent = 'Đang gửi...';
        }

        const id = editingId;
        try {
            if (id) {
                await api(`${API_BASE}/${id}`, 'PUT', data);
                Toast.show('Đã cập nhật yêu cầu', 'success');
            } else {
                data.serviceId = parseInt(document.getElementById('sr-service-id').value) || null;
                await api(API_BASE, 'POST', data);
                Toast.show('Đã gửi yêu cầu thành công!', 'success');
            }
            closeModal();
            loadRequests();
            if (id) viewRequest(id);
        } catch (error) {
            Toast.show(error.message, 'error');
        } finally {
            if (submitBtn) {
                submitBtn.disabled = false;
                if (submitBtn.textContent === 'Đang gửi...') submitBtn.textContent = idleLabel;
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
        openEditModal,
        closeModal,
        closeDetail,
        toggleActionModal,
        openAssignModal,
        handleApprove,
        handleReject,
        handleAssign,
        handleTakeOwnership,
        handleComplete,
        handleConfirm,
        handleReopen,
        handleCancel,
        handleDelete,
        handleAddComment
    };
})();

// Auto-initialize
document.addEventListener('DOMContentLoaded', () => {
    setTimeout(() => ServiceRequest.init(), 100);
});

window.ServiceRequest = ServiceRequest;
export { ServiceRequest };
