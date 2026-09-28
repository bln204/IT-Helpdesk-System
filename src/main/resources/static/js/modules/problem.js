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
                loadProblems();
            }
            if (e.target.closest('.problem-create-btn')) {
                openCreateModal();
            }
            if (e.target.closest('.problem-view-btn')) {
                const id = parseInt(e.target.closest('.problem-view-btn').dataset.id);
                viewProblem(id);
            }
            if (e.target.closest('#close-problem-modal') || e.target.id === 'problem-modal') {
                closeModal();
            }
            if (e.target.closest('#cancel-problem-btn')) {
                closeModal();
            }
        });

        document.addEventListener('submit', (e) => {
            if (e.target.id === 'problem-form') {
                e.preventDefault();
                submitProblem();
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

    async function loadKnownErrors() {
        try {
            const response = await fetch(`${API_BASE}/known-errors`, { headers: window.Auth.getAuthHeaders() });
            if (!response.ok) throw new Error('Failed to load known errors');
            knownErrors = await response.json();
            renderKnownErrors(knownErrors);
        } catch (error) {
            console.error('Error loading known errors:', error);
        }
    }

    async function loadProblemById(id) {
        const response = await fetch(`${API_BASE}/${id}`, { headers: window.Auth.getAuthHeaders() });
        if (!response.ok) throw new Error('Failed to load problem');
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
                        <span>📅 ${formatDate(problem.createdAt)}</span>
                    </div>
                    <div class="problem-footer">
                        <span class="problem-status ${status.class}">${status.label}</span>
                        <button class="secondary btn-sm problem-view-btn" data-id="${problem.id}">Xem</button>
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
                <div class="known-error-meta">
                    <span>⚡ ${error.occurrenceCount || 0} occurrences</span>
                    <span>📅 Last: ${formatDate(error.lastOccurrenceAt)}</span>
                </div>
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

    function renderProblemDetail(problem) {
        const container = document.getElementById('problem-form');
        if (!container) return;

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
        `;

        // Set current status
        document.getElementById('problem-status-select').value = problem.status;

        // Add status change listener
        document.getElementById('problem-status-select').addEventListener('change', async (e) => {
            try {
                await updateProblemStatus(problem.id, e.target.value);
                Toast.show('Đã cập nhật trạng thái', 'success');
                loadProblems();
            } catch (err) {
                Toast.show('Lỗi khi cập nhật trạng thái', 'error');
            }
        });
    }

    function closeModal() {
        document.getElementById('problem-modal').classList.add('hidden');
        document.body.classList.remove('modal-open');
        currentProblem = null;
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
        loadKnownErrors
    };
})();

// Auto-initialize
document.addEventListener('DOMContentLoaded', () => {
    setTimeout(() => ProblemManagement.init(), 100);
});

window.ProblemManagement = ProblemManagement;
export { ProblemManagement };
