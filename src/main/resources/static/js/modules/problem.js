/**
 * Problem Management Module - Theo dõi nguyên nhân gốc rễ
 */

const ProblemManagement = (() => {
    'use strict';

    const API_BASE = '/api/problems';

    // State
    let problems = [];
    let knownErrors = [];
    let currentProblem = null;
    let currentKnownError = null;
    let isSubmitting = false;

    // ==================== Initialization ====================

    function init() {
        if (typeof window.Auth === 'undefined') {
            console.error('Auth module not found');
            return;
        }

        window.Auth.onAuthReady(() => {
            setupEventListeners();
            loadProblems();
            loadKnownErrors();
        });
    }

    function setupEventListeners() {
        document.addEventListener('click', (e) => {
            if (e.target.closest('#refresh-problem-btn')) {
                const currentTab = document.querySelector('.problem-tab-btn.active')?.dataset.problemTab || 'problems';
                if (currentTab === 'problems') {
                    loadProblems();
                } else {
                    loadKnownErrors();
                }
            }
            if (e.target.closest('.problem-create-btn')) {
                openCreateModal();
            }
            if (e.target.closest('.problem-view-btn')) {
                const id = parseInt(e.target.closest('.problem-view-btn').dataset.id);
                viewProblem(id);
            }
            if (e.target.closest('.known-error-view-btn')) {
                const id = parseInt(e.target.closest('.known-error-view-btn').dataset.id);
                viewKnownError(id);
            }
            if (e.target.closest('.known-error-search-btn')) {
                searchKnownErrors();
            }
            if (e.target.closest('.problem-search-btn')) {
                searchProblems();
            }
            if (e.target.closest('#close-problem-modal') || e.target.id === 'problem-modal') {
                closeModal();
            }
            if (e.target.closest('#cancel-problem-btn')) {
                closeModal();
            }
            if (e.target.closest('#close-known-error-modal') || e.target.id === 'known-error-modal') {
                closeKnownErrorModal();
            }
            if (e.target.closest('#known-error-delete-btn')) {
                const id = parseInt(e.target.closest('#known-error-delete-btn').dataset.id);
                confirmDeleteKnownError(id);
            }
            if (e.target.closest('.problem-add-to-known-error-btn')) {
                const id = parseInt(e.target.closest('.problem-add-to-known-error-btn').dataset.id);
                openAddToKnownErrorModal(id);
            }
        });

        // Enter key for search
        document.addEventListener('keypress', (e) => {
            if (e.key === 'Enter' && e.target.id === 'known-error-search-input') {
                searchKnownErrors();
            }
            if (e.key === 'Enter' && e.target.id === 'problem-search') {
                searchProblems();
            }
        });

        document.addEventListener('submit', (e) => {
            if (e.target.id === 'problem-form') {
                e.preventDefault();
                submitProblemForm();
            }
            if (e.target.id === 'known-error-workaround-form') {
                e.preventDefault();
                submitKnownErrorWorkaround();
            }
            if (e.target.id === 'add-to-known-error-form') {
                e.preventDefault();
                submitAddToKnownError();
            }
        });

        // Tab switching
        document.addEventListener('click', (e) => {
            if (e.target.closest('.problem-tab-btn')) {
                const tab = e.target.closest('.problem-tab-btn').dataset.problemTab;
                switchProblemTab(tab);
            }
        });
    }

    // ==================== API Calls ====================

    async function loadProblems(params = {}) {
        try {
            let url = API_BASE;
            const queryParams = new URLSearchParams();
            
            if (params.status) queryParams.append('status', params.status);
            if (params.search) queryParams.append('search', params.search);
            if (params.page) queryParams.append('page', params.page);
            queryParams.append('size', '20');
            
            if (queryParams.toString()) {
                url += '?' + queryParams.toString();
            }

            const response = await fetch(url, { headers: window.Auth.getAuthHeaders() });
            if (!response.ok) throw new Error('Failed to load problems');
            const data = await response.json();
            problems = data.content || [];
            renderProblemsList(problems);
        } catch (error) {
            console.error('Error loading problems:', error);
            Toast.show('Lỗi khi tải danh sách Problems', 'error');
        }
    }

    async function loadKnownErrors(params = {}) {
        try {
            let url = `${API_BASE}/known-errors`;
            if (params.search) {
                url += `/search?q=${encodeURIComponent(params.search)}`;
            }
            console.log('Loading known errors from:', url);
            const response = await fetch(url, { headers: window.Auth.getAuthHeaders() });
            console.log('Response status:', response.status);
            if (!response.ok) throw new Error('Failed to load known errors');
            const data = await response.json();
            console.log('Received data:', data);
            knownErrors = data;
            renderKnownErrors(knownErrors);
        } catch (error) {
            console.error('Error loading known errors:', error);
            Toast.show('Lỗi khi tải Known Errors', 'error');
        }
    }

    async function loadProblemById(id) {
        const response = await fetch(`${API_BASE}/${id}`, { headers: window.Auth.getAuthHeaders() });
        if (!response.ok) throw new Error('Failed to load problem');
        return await response.json();
    }

    async function loadKnownErrorById(id) {
        const response = await fetch(`${API_BASE}/known-errors/${id}`, { headers: window.Auth.getAuthHeaders() });
        if (!response.ok) throw new Error('Failed to load known error');
        return await response.json();
    }

    async function submitProblem(data) {
        const response = await fetch(API_BASE, {
            method: 'POST',
            headers: window.Auth.getAuthHeaders(),
            body: JSON.stringify(data)
        });
        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.message || 'Failed to create problem');
        }
        return await response.json();
    }

    async function updateProblemStatus(id, status) {
        const response = await fetch(`${API_BASE}/${id}/status`, {
            method: 'PATCH',
            headers: window.Auth.getAuthHeaders(),
            body: JSON.stringify({ status })
        });
        if (!response.ok) throw new Error('Failed to update status');
        return await response.json();
    }

    async function linkIncident(problemId, incidentId) {
        const response = await fetch(`${API_BASE}/${problemId}/incidents`, {
            method: 'POST',
            headers: window.Auth.getAuthHeaders(),
            body: JSON.stringify({ incidentId })
        });
        if (!response.ok) throw new Error('Failed to link incident');
        return await response.json();
    }

    async function addWorkaroundToKnownError(id, workaround, author) {
        const response = await fetch(`${API_BASE}/known-errors/${id}/workaround`, {
            method: 'POST',
            headers: window.Auth.getAuthHeaders(),
            body: JSON.stringify({ 
                workaround: workaround,
                author: author || 'System'
            })
        });
        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.message || 'Failed to add workaround');
        }
        return await response.json();
    }

    async function createKnownError(data) {
        const response = await fetch(`${API_BASE}/known-errors`, {
            method: 'POST',
            headers: window.Auth.getAuthHeaders(),
            body: JSON.stringify(data)
        });
        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.message || 'Failed to create known error');
        }
        return await response.json();
    }

    // PHASE 6.6.3 - atomic create + link. The backend sets the relationship
    // in the same transaction; the caller just needs to refresh the Problem.
    async function createKnownErrorForProblem(problemId, data) {
        const response = await fetch(`${API_BASE}/${problemId}/known-error`, {
            method: 'POST',
            headers: window.Auth.getAuthHeaders(),
            body: JSON.stringify(data)
        });
        if (!response.ok) {
            const err = await response.json().catch(() => ({}));
            throw new Error(err.message || `Failed to create known error (HTTP ${response.status})`);
        }
        return await response.json();
    }

    async function linkExistingKnownError(problemId, knownErrorId) {
        const response = await fetch(`${API_BASE}/${problemId}/known-error/link`, {
            method: 'POST',
            headers: window.Auth.getAuthHeaders(),
            body: JSON.stringify({ knownErrorId })
        });
        if (!response.ok) {
            const err = await response.json().catch(() => ({}));
            throw new Error(err.message || `Failed to link known error (HTTP ${response.status})`);
        }
        return await response.json();
    }

    async function unlinkKnownErrorFromProblem(problemId) {
        const response = await fetch(`${API_BASE}/${problemId}/known-error`, {
            method: 'DELETE',
            headers: window.Auth.getAuthHeaders()
        });
        // 204 No Content is the success signal; we don't try to parse a body.
        if (!response.ok && response.status !== 204) {
            const err = await response.json().catch(() => ({}));
            throw new Error(err.message || `Failed to unlink known error (HTTP ${response.status})`);
        }
    }

    async function deleteKnownError(id) {
        const response = await fetch(`${API_BASE}/known-errors/${id}`, {
            method: 'DELETE',
            headers: window.Auth.getAuthHeaders()
        });
        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.message || 'Failed to delete known error');
        }
    }

    // ==================== Rendering ====================

    function renderProblemsList(problemsList) {
        const container = document.getElementById('problem-list');
        if (!container) return;

        if (!problemsList || problemsList.length === 0) {
            container.innerHTML = '<div class="empty-state"><p>Chưa có Problem nào.</p></div>';
            return;
        }

        const statusLabels = {
            'NEW': { label: 'Mới', class: 'new' },
            'INVESTIGATING': { label: 'Đang điều tra', class: 'investigating' },
            'IDENTIFIED': { label: 'Đã xác định', class: 'identified' },
            'SOLVING': { label: 'Đang giải quyết', class: 'solving' },
            'RESOLVED': { label: 'Đã giải quyết', class: 'resolved' },
            'CLOSED': { label: 'Đã đóng', class: 'closed' }
        };

        const priorityLabels = {
            'LOW': 'Thấp',
            'MEDIUM': 'Trung bình',
            'HIGH': 'Cao',
            'CRITICAL': 'Nghiêm trọng'
        };

        container.innerHTML = problemsList.map(problem => {
            const status = statusLabels[problem.status] || statusLabels['NEW'];
            // PHASE 6.6.3: button visible for any non-closed Problem, role-gated separately.
            const isClosed = problem.status === 'CLOSED';
            const hasKe = !!problem.knownErrorId;
            return `
                <div class="problem-card">
                    <div class="problem-card-header">
                        <span class="problem-number">${problem.problemNumber}</span>
                        <span class="problem-priority priority-${problem.priority?.toLowerCase()}">${priorityLabels[problem.priority] || 'N/A'}</span>
                    </div>
                    <h4 class="problem-title">${escapeHtml(problem.title)}</h4>
                    <p class="problem-category">📁 ${problem.category || 'Không phân loại'}</p>
                    <div class="problem-meta">
                        <span>🔗 ${problem.totalIncidentsLinked || 0} incidents</span>
                        ${hasKe ? `<span>📚 ${problem.knownErrorCode || ('KE #' + problem.knownErrorId)}</span>` : ''}
                        <span>📅 ${formatDate(problem.createdAt)}</span>
                    </div>
                    <div class="problem-footer">
                        <span class="problem-status ${status.class}">${status.label}</span>
                        <div class="problem-card-actions">
                            ${!isClosed ? `
                                <button class="ghost btn-sm problem-add-to-known-error-btn" data-id="${problem.id}" title="Liên kết Known Error">
                                    ${hasKe ? '📚 Đổi Known Error' : '📚 Known Error'}
                                </button>
                            ` : ''}
                            <button class="secondary btn-sm problem-view-btn" data-id="${problem.id}">Xem</button>
                        </div>
                    </div>
                </div>
            `;
        }).join('');
    }

    function renderKnownErrors(errorsList) {
        const container = document.getElementById('known-error-list');
        if (!container) return;

        if (!errorsList || errorsList.length === 0) {
            container.innerHTML = '<div class="empty-state"><p>Chưa có Known Error nào.</p></div>';
            return;
        }

        container.innerHTML = errorsList.map(error => `
            <div class="known-error-card">
                <div class="known-error-header">
                    <span class="error-code">${error.errorCode}</span>
                    <span class="error-status ${error.status?.toLowerCase()}">${error.statusLabel || error.status}</span>
                </div>
                <h5>${escapeHtml(error.title)}</h5>
                <p>${escapeHtml(error.description || '').substring(0, 100)}...</p>
                ${error.workaround ? `<p class="known-error-workaround-preview">💡 ${escapeHtml(error.workaround).substring(0, 80)}...</p>` : ''}
                <div class="known-error-meta">
                    <span>⚡ ${error.occurrenceCount || 0} occurrences</span>
                    <span>📅 Last: ${formatDate(error.lastOccurrenceAt)}</span>
                </div>
                <button class="secondary btn-sm known-error-view-btn" data-id="${error.id}" style="margin-top: 8px;">Xem chi tiết</button>
            </div>
        `).join('');
    }

    // ==================== Tab Switching ====================

    function switchProblemTab(tab) {
        // Update tab buttons
        document.querySelectorAll('.problem-tab-btn').forEach(btn => {
            btn.classList.toggle('active', btn.dataset.problemTab === tab);
        });

        // Update tab content
        document.getElementById('problem-list-section')?.classList.toggle('hidden', tab !== 'problems');
        document.getElementById('known-error-section')?.classList.toggle('hidden', tab !== 'known-errors');

        // Reload data if needed
        if (tab === 'known-errors' && knownErrors.length === 0) {
            loadKnownErrors();
        }
    }

    // ==================== Known Error Search ====================

    function searchKnownErrors() {
        const searchInput = document.getElementById('known-error-search-input');
        const query = searchInput?.value?.trim() || '';
        console.log('Searching known errors with query:', query);
        loadKnownErrors({ search: query });
    }

    function searchProblems() {
        const searchInput = document.getElementById('problem-search');
        const query = searchInput?.value?.trim() || '';
        loadProblems({ search: query });
    }

    // ==================== Modal ====================

    function openCreateModal() {
        document.getElementById('problem-modal-title').textContent = 'Tạo Problem mới';
        resetForm();
        document.getElementById('problem-modal').classList.remove('hidden');
        document.body.classList.add('modal-open');
    }

    function viewProblem(id) {
        loadProblemById(id).then(problem => {
            currentProblem = problem;
            renderProblemDetail(problem);
            document.getElementById('problem-modal-title').textContent = problem.problemNumber;
            document.getElementById('problem-modal').classList.remove('hidden');
            document.body.classList.add('modal-open');
        }).catch(err => {
            Toast.show('Lỗi khi tải Problem', 'error');
        });
    }

    function viewKnownError(id) {
        loadKnownErrorById(id).then(error => {
            currentKnownError = error;
            renderKnownErrorDetail(error);
            // Show delete button based on permission
            const deleteBtn = document.getElementById('known-error-delete-btn');
            if (deleteBtn) {
                deleteBtn.dataset.id = id;
                // Check permissions
                const hasAdmin = window.Auth?.hasRole?.('ADMIN') || false;
                const hasTruongPhong = window.Auth?.hasRole?.('TRUONG_PHONG') || false;
                
                // Fetch full user profile to get department
                import('../core/api.js').then(({ request }) => {
                    request('/api/users/me').then(user => {
                        const deptCode = user?.departmentCode;
                        // Admin hoặc Trưởng phòng IT được phép xóa
                        const canDelete = hasAdmin || hasTruongPhong;
                        console.log('Delete button check:', { hasAdmin, hasTruongPhong, deptCode, canDelete });
                        deleteBtn.style.display = canDelete ? 'inline-flex' : 'none';
                    }).catch(() => {
                        deleteBtn.style.display = 'none';
                    });
                });
            }
            document.getElementById('known-error-modal').classList.remove('hidden');
            document.body.classList.add('modal-open');
        }).catch(err => {
            Toast.show('Lỗi khi tải Known Error', 'error');
        });
    }

    function renderProblemDetail(problem) {
        const container = document.getElementById('problem-form');
        if (!container) return;

        // PHASE 6.6.3 - role gate for Known Error actions.
        // The backend @PreAuthorize is the security boundary; this is UX only.
        const allowedRoles = ['ADMIN', 'GIAM_DOC', 'TRUONG_PHONG'];
        const userRoles = (window.Auth && typeof window.Auth.getRoles === 'function')
            ? window.Auth.getRoles()
            : (window.Auth && typeof window.Auth.getCurrentUser === 'function'
                ? [window.Auth.getCurrentUser()?.role].filter(Boolean)
                : []);
        const canManageKe = userRoles.some(r => allowedRoles.includes(r));
        const isClosed = problem.status === 'CLOSED';
        const hasKe = !!problem.knownErrorId;
        const keStatusClass = problem.knownErrorStatus
            ? `ke-status-${problem.knownErrorStatus.toLowerCase()}`
            : '';

        container.innerHTML = `
            <div class="problem-detail-header">
                <div>
                    <h4>${escapeHtml(problem.title)}</h4>
                    <span class="problem-status ${problem.status?.toLowerCase()}">${problem.statusLabel || problem.status}</span>
                </div>
                <div class="problem-detail-actions">
                    <select id="problem-status-select" class="custom-select">
                        <option value="NEW">Mới</option>
                        <option value="INVESTIGATING">Đang điều tra</option>
                        <option value="IDENTIFIED">Đã xác định</option>
                        <option value="SOLVING">Đang giải quyết</option>
                        <option value="RESOLVED">Đã giải quyết</option>
                        <option value="CLOSED">Đã đóng</option>
                    </select>
                </div>
            </div>

            <div class="problem-detail-grid">
                <div class="detail-section">
                    <h5>📝 Thông tin chung</h5>
                    <div class="detail-item">
                        <label>Tiêu đề</label>
                        <span>${escapeHtml(problem.title)}</span>
                    </div>
                    <div class="detail-item">
                        <label>Mô tả</label>
                        <span>${escapeHtml(problem.description || 'N/A')}</span>
                    </div>
                    <div class="detail-item">
                        <label>Danh mục</label>
                        <span>${problem.category || 'N/A'}</span>
                    </div>
                </div>

                <div class="detail-section">
                    <h5>🔍 Root Cause Analysis</h5>
                    <div class="detail-item">
                        <label>Nguyên nhân gốc rễ</label>
                        <span>${escapeHtml(problem.rootCause || 'Chưa xác định')}</span>
                    </div>
                    <div class="detail-item">
                        <label>Loại nguyên nhân</label>
                        <span>${problem.rootCauseCategory || 'N/A'}</span>
                    </div>
                </div>

                <div class="detail-section">
                    <h5>⚡ Workaround & Resolution</h5>
                    <div class="detail-item">
                        <label>Workaround</label>
                        <span>${escapeHtml(problem.workaround || 'Chưa có')}</span>
                    </div>
                    <div class="detail-item">
                        <label>Resolution</label>
                        <span>${escapeHtml(problem.resolution || 'Chưa có')}</span>
                    </div>
                </div>

                <div class="detail-section">
                    <h5>📊 Impact Metrics</h5>
                    <div class="detail-item">
                        <label>Incidents liên quan</label>
                        <span>${problem.totalIncidentsLinked || 0}</span>
                    </div>
                    <div class="detail-item">
                        <label>Người phụ trách</label>
                        <span>${problem.assignedTo || 'Chưa gán'}</span>
                    </div>
                </div>
            </div>

            <!-- PHASE 6.6.3 - Known Error relationship panel -->
            <div class="problem-known-error-section detail-section">
                <h5>📚 Known Error</h5>
                ${hasKe ? `
                    <div class="ke-linked">
                        <span>${escapeHtml(problem.knownErrorCode || ('KE #' + problem.knownErrorId))}</span>
                        <span class="ke-status-badge ${keStatusClass}">${escapeHtml(problem.knownErrorStatus || '')}</span>
                        ${(problem.knownErrorRelatedProblemsCount !== null && problem.knownErrorRelatedProblemsCount !== undefined)
                            ? `<span class="ke-related-count">🔁 ${problem.knownErrorRelatedProblemsCount} problem</span>`
                            : ''}
                    </div>
                ` : `
                    <p class="muted">No linked Known Error</p>
                `}
                ${(!isClosed && canManageKe) ? `
                    <div class="button-row" style="margin-top: 12px;">
                        <button class="secondary" onclick="ProblemManagement.openAddToKnownErrorModal(${problem.id})">
                            ${hasKe ? '🔄 Đổi / Tạo mới' : '➕ Tạo Known Error'}
                        </button>
                        <button class="ghost" onclick="ProblemManagement.openLinkExistingKnownErrorModal(${problem.id})">
                            🔗 Liên kết Known Error đã có
                        </button>
                        ${hasKe ? `
                            <button class="warning" onclick="ProblemManagement.confirmUnlinkKnownError(${problem.id})">
                                ⛓️‍💥 Bỏ liên kết
                            </button>
                        ` : ''}
                    </div>
                ` : ''}
            </div>
        `;

        // Set current status
        const statusSelect = document.getElementById('problem-status-select');
        if (statusSelect) statusSelect.value = problem.status;

        // Add status change listener
        statusSelect?.addEventListener('change', async (e) => {
            try {
                await updateProblemStatus(problem.id, e.target.value);
                Toast.show('Đã cập nhật trạng thái', 'success');
                loadProblems();
            } catch (err) {
                Toast.show('Lỗi khi cập nhật trạng thái', 'error');
            }
        });
    }

    function renderKnownErrorDetail(error) {
        const container = document.getElementById('known-error-detail-content');
        if (!container) return;

        container.innerHTML = `
            <div class="known-error-detail-header">
                <div>
                    <span class="error-code-large">${error.errorCode}</span>
                    <span class="error-status ${error.status?.toLowerCase()}">${error.statusLabel || error.status}</span>
                </div>
            </div>

            <div class="known-error-detail-grid">
                <div class="detail-section">
                    <h5>📝 Thông tin chung</h5>
                    <div class="detail-item">
                        <label>Tiêu đề</label>
                        <span>${escapeHtml(error.title)}</span>
                    </div>
                    <div class="detail-item">
                        <label>Mô tả</label>
                        <span>${escapeHtml(error.description || 'N/A')}</span>
                    </div>
                    <div class="detail-item">
                        <label>Triệu chứng</label>
                        <span>${escapeHtml(error.symptoms || 'N/A')}</span>
                    </div>
                </div>

                <div class="detail-section">
                    <h5>🔍 Root Cause</h5>
                    <div class="detail-item">
                        <label>Nguyên nhân gốc rễ</label>
                        <span>${escapeHtml(error.rootCause || 'Chưa xác định')}</span>
                    </div>
                </div>

                <div class="detail-section">
                    <h5>⚡ Workaround & Fix Steps</h5>
                    <div class="detail-item">
                        <label>Workaround</label>
                        <span>${escapeHtml(error.workaround || 'Chưa có')}</span>
                    </div>
                    <div class="detail-item">
                        <label>Fix Steps</label>
                        <span>${escapeHtml(error.fixSteps || 'Chưa có')}</span>
                    </div>
                </div>

                <div class="detail-section">
                    <h5>📊 Statistics</h5>
                    <div class="detail-item">
                        <label>Số lần xảy ra</label>
                        <span>${error.occurrenceCount || 0}</span>
                    </div>
                    <div class="detail-item">
                        <label>Lần cuối</label>
                        <span>${formatDate(error.lastOccurrenceAt)}</span>
                    </div>
                    <div class="detail-item">
                        <label>Người quản lý</label>
                        <span>${error.managedBy || 'Chưa có'}</span>
                    </div>
                </div>
            </div>

            <!-- Add Workaround Form -->
            <div class="known-error-workaround-section">
                <h5>➕ Thêm Workaround mới</h5>
                <form id="known-error-workaround-form">
                    <input type="hidden" id="known-error-workaround-id" value="${error.id}" />
                    <textarea id="known-error-workaround-input" rows="3" placeholder="Nhập workaround..."></textarea>
                    <div class="button-row" style="margin-top: 8px;">
                        <button type="submit" class="primary btn-sm">Lưu Workaround</button>
                    </div>
                </form>
            </div>
        `;
    }

    async function confirmDeleteKnownError(id) {
        if (!confirm('Bạn có chắc muốn xóa Known Error này?')) {
            return;
        }

        try {
            await deleteKnownError(id);
            Toast.show('Đã xóa Known Error', 'success');
            closeKnownErrorModal();
            loadKnownErrors();
        } catch (error) {
            Toast.show(error.message || 'Lỗi khi xóa Known Error', 'error');
        }
    }

    function closeModal() {
        document.getElementById('problem-modal').classList.add('hidden');
        document.body.classList.remove('modal-open');
        currentProblem = null;
    }

    function closeKnownErrorModal() {
        document.getElementById('known-error-modal').classList.add('hidden');
        document.body.classList.remove('modal-open');
        const deleteBtn = document.getElementById('known-error-delete-btn');
        if (deleteBtn) {
            deleteBtn.style.display = 'none';
        }
        currentKnownError = null;
    }

    // ==================== Add to Known Errors ====================

    function openAddToKnownErrorModal(problemId) {
        // Create modal dynamically
        let modal = document.getElementById('add-to-known-error-modal');
        if (modal) {
            modal.remove();
        }

        modal = document.createElement('div');
        modal.id = 'add-to-known-error-modal';
        modal.className = 'modal';
        modal.innerHTML = `
            <div class="modal-content" style="max-width: 600px;">
                <header class="panel-header">
                    <div>
                        <h3>Thêm vào Known Errors Database</h3>
                        <p>Tạo Known Error từ Problem đã resolve</p>
                    </div>
                    <button class="ghost" onclick="ProblemManagement.closeAddToKnownErrorModal()">Đóng</button>
                </header>
                <form id="add-to-known-error-form" class="stack">
                    <input type="hidden" id="add-to-known-error-problem-id" value="${problemId}" />
                    <label>
                        Tiêu đề Known Error *
                        <input type="text" id="ke-title" required placeholder="VD: Database connection timeout on Server A" />
                    </label>
                    <label>
                        Mô tả
                        <textarea id="ke-description" rows="2" placeholder="Mô tả chi tiết..."></textarea>
                    </label>
                    <label>
                        Triệu chứng (Symptoms)
                        <textarea id="ke-symptoms" rows="2" placeholder="Các triệu chứng quan sát được..."></textarea>
                    </label>
                    <label>
                        Nguyên nhân gốc rễ
                        <textarea id="ke-root-cause" rows="2" placeholder="Nguyên nhân gốc rễ..."></textarea>
                    </label>
                    <label>
                        Loại nguyên nhân
                        <input type="text" id="ke-root-cause-category" placeholder="INFRASTRUCTURE, CONFIGURATION, PROCESS, VENDOR, HUMAN_ERROR, DESIGN_FLAW..." />
                    </label>
                    <label>
                        Workaround (Giải pháp tạm thời)
                        <textarea id="ke-workaround" rows="2" placeholder="Giải pháp tạm thời..."></textarea>
                    </label>
                    <label>
                        Permanent resolution
                        <textarea id="ke-resolution" rows="2" placeholder="Giải pháp vĩnh viễn (nếu có)..."></textarea>
                    </label>
                    <label>
                        Fix Steps (Các bước khắc phục)
                        <textarea id="ke-fix-steps" rows="2" placeholder="Các bước để khắc phục vĩnh viễn..."></textarea>
                    </label>
                    <div class="button-row">
                        <button type="submit" class="primary">Tạo Known Error</button>
                        <button type="button" class="secondary" onclick="ProblemManagement.closeAddToKnownErrorModal()">Hủy</button>
                    </div>
                </form>
            </div>
        `;
        document.body.appendChild(modal);
        modal.classList.remove('hidden');
        document.body.classList.add('modal-open');
    }

    function closeAddToKnownErrorModal() {
        const modal = document.getElementById('add-to-known-error-modal');
        if (modal) {
            modal.classList.add('hidden');
            modal.remove();
        }
        document.body.classList.remove('modal-open');
    }

    async function submitAddToKnownError() {
        if (isSubmitting) return;
        isSubmitting = true;

        const submitBtn = document.querySelector('#add-to-known-error-form button[type="submit"]');
        if (submitBtn) {
            submitBtn.disabled = true;
            submitBtn.textContent = 'Đang tạo...';
        }

        // PHASE 6.6.3 - the hidden input carries the problem id and is the
        // single source of truth for which Problem this KE is being created for.
        const problemIdRaw = document.getElementById('add-to-known-error-problem-id')?.value;
        const problemId = problemIdRaw ? parseInt(problemIdRaw, 10) : null;

        const data = {
            title: document.getElementById('ke-title')?.value?.trim(),
            description: document.getElementById('ke-description')?.value?.trim(),
            symptoms: document.getElementById('ke-symptoms')?.value?.trim(),
            rootCause: document.getElementById('ke-root-cause')?.value?.trim(),
            rootCauseCategory: document.getElementById('ke-root-cause-category')?.value?.trim(),
            workaround: document.getElementById('ke-workaround')?.value?.trim(),
            resolution: document.getElementById('ke-resolution')?.value?.trim(),
            fixSteps: document.getElementById('ke-fix-steps')?.value?.trim()
        };

        if (!data.title) {
            Toast.show('Vui lòng nhập tiêu đề', 'error');
            isSubmitting = false;
            if (submitBtn) {
                submitBtn.disabled = false;
                submitBtn.textContent = 'Tạo Known Error';
            }
            return;
        }

        if (!problemId || Number.isNaN(problemId)) {
            Toast.show('Không xác định được Problem. Vui lòng đóng và mở lại Problem.', 'error');
            isSubmitting = false;
            if (submitBtn) {
                submitBtn.disabled = false;
                submitBtn.textContent = 'Tạo Known Error';
            }
            return;
        }

        try {
            // PHASE 6.6.3 - use the new atomic endpoint that creates the KE
            // AND links it to the Problem in one transaction.
            const knownError = await createKnownErrorForProblem(problemId, data);
            Toast.show(`Đã tạo Known Error ${knownError.errorCode} và liên kết với Problem.`, 'success');
            closeAddToKnownErrorModal();
            // Refresh the underlying Problem so the detail view reflects the
            // new knownErrorId / knownErrorCode / knownErrorStatus.
            await refreshProblemDetail(problemId);
            loadKnownErrors();
        } catch (error) {
            const msg = (error && error.message) || 'Lỗi khi tạo Known Error';
            if (msg.toLowerCase().includes('forbidden') || msg.toLowerCase().includes('không có quyền')) {
                Toast.show('Bạn không có quyền thực hiện thao tác này.', 'error');
            } else {
                Toast.show(msg, 'error');
            }
        } finally {
            isSubmitting = false;
            if (submitBtn) {
                submitBtn.disabled = false;
                submitBtn.textContent = 'Tạo Known Error';
            }
        }
    }

    // PHASE 6.6.3 - new "Link to existing Known Error" UX. The user searches
    // the KEDB, picks a row, the service links it to the Problem. The DTO is
    // intentionally tiny (search field + results + "Link" button per row) so
    // the reuse-first path is the lowest-friction choice.
    function openLinkExistingKnownErrorModal(problemId) {
        let modal = document.getElementById('link-existing-known-error-modal');
        if (modal) modal.remove();

        modal = document.createElement('div');
        modal.id = 'link-existing-known-error-modal';
        modal.className = 'modal';
        modal.innerHTML = `
            <div class="modal-content" style="max-width: 720px;">
                <header class="panel-header">
                    <div>
                        <h3>Liên kết Known Error đã có</h3>
                        <p>Tìm Known Error phù hợp với Problem hiện tại để tránh tạo trùng.</p>
                    </div>
                    <button class="ghost" onclick="ProblemManagement.closeLinkExistingKnownErrorModal()">Đóng</button>
                </header>
                <div class="stack">
                    <input type="hidden" id="link-existing-problem-id" value="${problemId}" />
                    <label>
                        Tìm kiếm Known Error
                        <input type="text" id="link-existing-search-input" placeholder="Nhập từ khóa: tiêu đề, nguyên nhân, workaround..." />
                    </label>
                    <div class="button-row">
                        <button type="button" class="primary" onclick="ProblemManagement.searchKnownErrorsForLink()">Tìm</button>
                    </div>
                    <div id="link-existing-results"></div>
                </div>
            </div>
        `;
        document.body.appendChild(modal);
        modal.classList.remove('hidden');
        document.body.classList.add('modal-open');
        // Auto-load the most recent KEs so the user sees something immediately.
        searchKnownErrorsForLink();
    }

    function closeLinkExistingKnownErrorModal() {
        const modal = document.getElementById('link-existing-known-error-modal');
        if (modal) {
            modal.classList.add('hidden');
            modal.remove();
        }
        document.body.classList.remove('modal-open');
    }

    async function searchKnownErrorsForLink() {
        const query = document.getElementById('link-existing-search-input')?.value?.trim() || '';
        const resultsEl = document.getElementById('link-existing-results');
        if (!resultsEl) return;
        resultsEl.innerHTML = '<p class="muted">Đang tìm...</p>';
        try {
            const url = query
                ? `${API_BASE}/known-errors/search?q=${encodeURIComponent(query)}`
                : `${API_BASE}/known-errors`;
            const response = await fetch(url, { headers: window.Auth.getAuthHeaders() });
            if (!response.ok) throw new Error('Failed to search known errors');
            const results = await response.json();
            renderLinkExistingResults(results || []);
        } catch (err) {
            resultsEl.innerHTML = '<p class="muted">Lỗi khi tìm Known Error.</p>';
        }
    }

    function renderLinkExistingResults(results) {
        const resultsEl = document.getElementById('link-existing-results');
        if (!resultsEl) return;
        if (results.length === 0) {
            resultsEl.innerHTML = '<p class="muted">Không tìm thấy Known Error phù hợp. Bạn có thể tạo mới bằng nút "Tạo Known Error".</p>';
            return;
        }
        // Filter out OBSOLETE rows since they cannot be linked.
        const linkable = results.filter(ke => ke.status !== 'OBSOLETE');
        if (linkable.length === 0) {
            resultsEl.innerHTML = '<p class="muted">Tất cả Known Error tìm được đều OBSOLETE. Vui lòng tạo mới.</p>';
            return;
        }
        resultsEl.innerHTML = linkable.map(ke => `
            <div class="link-existing-row">
                <div class="link-existing-row-text">
                    <strong>${escapeHtml(ke.errorCode)}</strong>
                    <span>${escapeHtml(ke.title || '')}</span>
                    <span class="muted">Status: ${escapeHtml(ke.status || '')} • ${(ke.relatedProblemsCount || 0)} problem</span>
                </div>
                <button class="primary btn-sm" data-id="${ke.id}">Liên kết</button>
            </div>
        `).join('');
        // Wire up per-row click handlers.
        resultsEl.querySelectorAll('button[data-id]').forEach(btn => {
            btn.addEventListener('click', () => {
                const keId = parseInt(btn.dataset.id, 10);
                handleLinkExisting(keId);
            });
        });
    }

    async function handleLinkExisting(knownErrorId) {
        const problemIdRaw = document.getElementById('link-existing-problem-id')?.value;
        const problemId = problemIdRaw ? parseInt(problemIdRaw, 10) : null;
        if (!problemId) {
            Toast.show('Không xác định được Problem.', 'error');
            return;
        }
        try {
            await linkExistingKnownError(problemId, knownErrorId);
            Toast.show('Đã liên kết Known Error với Problem.', 'success');
            closeLinkExistingKnownErrorModal();
            await refreshProblemDetail(problemId);
            loadKnownErrors();
        } catch (error) {
            const msg = (error && error.message) || 'Lỗi khi liên kết Known Error';
            if (msg.toLowerCase().includes('forbidden') || msg.toLowerCase().includes('không có quyền')) {
                Toast.show('Bạn không có quyền thực hiện thao tác này.', 'error');
            } else if (msg.toLowerCase().includes('obsolete')) {
                Toast.show('Known Error đã OBSOLETE, không thể liên kết.', 'error');
            } else {
                Toast.show(msg, 'error');
            }
        }
    }

    function confirmUnlinkKnownError(problemId) {
        if (!confirm('Bỏ liên kết Known Error khỏi Problem này?')) {
            return;
        }
        unlinkKnownErrorFromProblem(problemId)
            .then(async () => {
                Toast.show('Đã bỏ liên kết Known Error.', 'success');
                await refreshProblemDetail(problemId);
                loadKnownErrors();
            })
            .catch((err) => {
                const msg = (err && err.message) || 'Lỗi khi bỏ liên kết';
                if (msg.toLowerCase().includes('forbidden') || msg.toLowerCase().includes('không có quyền')) {
                    Toast.show('Bạn không có quyền thực hiện thao tác này.', 'error');
                } else {
                    Toast.show(msg, 'error');
                }
            });
    }

    // PHASE 6.6.3 - refresh the open Problem detail modal after a KE mutation
    // so the linked-Known-Error panel reflects the new state without a full
    // page reload.
    async function refreshProblemDetail(problemId) {
        try {
            const fresh = await loadProblemById(problemId);
            currentProblem = fresh;
            renderProblemDetail(fresh);
        } catch (err) {
            console.error('refreshProblemDetail failed', err);
        }
    }

    async function submitKnownErrorWorkaround() {
        const id = document.getElementById('known-error-workaround-id')?.value;
        const workaround = document.getElementById('known-error-workaround-input')?.value?.trim();
        const currentUser = window.Auth?.getCurrentUser?.();

        if (!id || !workaround) {
            Toast.show('Vui lòng nhập workaround', 'error');
            return;
        }

        try {
            await addWorkaroundToKnownError(parseInt(id), workaround, currentUser?.displayName || currentUser?.username);
            Toast.show('Đã thêm workaround', 'success');
            // Reload known error detail
            viewKnownError(parseInt(id));
            loadKnownErrors();
        } catch (error) {
            Toast.show(error.message || 'Lỗi khi thêm workaround', 'error');
        }
    }

    function resetForm() {
        const form = document.getElementById('problem-form');
        if (form) {
            form.innerHTML = `
                <input type="hidden" id="problem-id" />
                
                <label>
                    Tiêu đề *
                    <input type="text" id="problem-title" maxlength="300" required placeholder="VD: Server A liên tục crash" />
                </label>
                
                <label>
                    Mô tả
                    <textarea id="problem-description" rows="3" placeholder="Mô tả chi tiết vấn đề..."></textarea>
                </label>
                
                <div class="form-row">
                    <label>
                        Danh mục
                        <select id="problem-category">
                            <option value="">Chọn danh mục</option>
                            <option value="NETWORK">Network</option>
                            <option value="HARDWARE">Hardware</option>
                            <option value="SOFTWARE">Software</option>
                            <option value="SECURITY">Security</option>
                            <option value="DATABASE">Database</option>
                            <option value="APPLICATION">Application</option>
                            <option value="OTHER">Other</option>
                        </select>
                    </label>
                    
                    <label>
                        Ưu tiên
                        <select id="problem-priority">
                            <option value="LOW">Thấp</option>
                            <option value="MEDIUM" selected>Trung bình</option>
                            <option value="HIGH">Cao</option>
                            <option value="CRITICAL">Nghiêm trọng</option>
                        </select>
                    </label>
                </div>
                
                <label>
                    Nguyên nhân gốc rễ (điền sau khi điều tra)
                    <textarea id="problem-root-cause" rows="2" placeholder="Xác định nguyên nhân gốc rễ..."></textarea>
                </label>
                
                <label>
                    Workaround (giải pháp tạm thời)
                    <textarea id="problem-workaround" rows="2" placeholder="Giải pháp tạm thời..."></textarea>
                </label>
                
                <div class="button-row">
                    <button type="submit" id="problem-submit-btn" class="primary">Tạo Problem</button>
                    <button type="button" id="cancel-problem-btn" class="secondary">Hủy</button>
                </div>
            `;
        }
    }

    async function submitProblemForm() {
        if (isSubmitting) return;
        isSubmitting = true;

        const submitBtn = document.getElementById('problem-submit-btn');
        if (submitBtn) {
            submitBtn.disabled = true;
            submitBtn.textContent = 'Đang tạo...';
        }

        const data = {
            title: document.getElementById('problem-title').value.trim(),
            description: document.getElementById('problem-description').value.trim(),
            category: document.getElementById('problem-category').value,
            priority: document.getElementById('problem-priority').value,
            rootCause: document.getElementById('problem-root-cause').value.trim()
        };

        if (!data.title) {
            Toast.show('Vui lòng nhập tiêu đề', 'error');
            if (submitBtn) {
                submitBtn.disabled = false;
                submitBtn.textContent = 'Tạo Problem';
            }
            isSubmitting = false;
            return;
        }

        try {
            await submitProblem(data);
            Toast.show('Đã tạo Problem thành công!', 'success');
            closeModal();
            loadProblems();
        } catch (error) {
            Toast.show(error.message, 'error');
        } finally {
            if (submitBtn) {
                submitBtn.disabled = false;
                submitBtn.textContent = 'Tạo Problem';
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

    // ==================== Public API ====================

    return {
        init,
        loadProblems,
        loadKnownErrors,
        searchKnownErrors,
        viewProblem,
        viewKnownError,
        closeModal,
        closeKnownErrorModal,
        openAddToKnownErrorModal,
        closeAddToKnownErrorModal,
        submitAddToKnownError,
        // PHASE 6.6.3 - new public surface for the reuse-first workflow.
        openLinkExistingKnownErrorModal,
        closeLinkExistingKnownErrorModal,
        searchKnownErrorsForLink,
        confirmUnlinkKnownError,
        refreshProblemDetail,
        confirmDeleteKnownError
    };
})();

// Auto-initialize
document.addEventListener('DOMContentLoaded', () => {
    setTimeout(() => ProblemManagement.init(), 100);
});

window.ProblemManagement = ProblemManagement;
export { ProblemManagement };
