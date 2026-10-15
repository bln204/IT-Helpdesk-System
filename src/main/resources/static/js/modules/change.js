/**
 * Change Management Module - Distinctive IT Operations Interface
 * 
 * Design Principles:
 * - Risk visualization as hero element
 * - Split-pane layout for efficiency
 * - Timeline for status progression
 * - IBM Plex Sans typography for technical interfaces
 */
const ChangeManagement = (() => {
    'use strict';
    // Họ tên từ danh bạ (userService.getDisplayName); fallback về giá trị gốc khi chưa nạp.
    const nameOf = (u) => (u && window.displayNameOf ? window.displayNameOf(u) : u) || '';

    const API_BASE = '/api/changes';

    // State
    let changes = [];
    let currentChange = null;
    let isSubmitting = false;

    // ==================== Initialization ====================

    function init() {
        if (typeof window.Auth === 'undefined') {
            console.error('Auth module not found');
            return;
        }

        window.Auth.onAuthReady(() => {
            setupEventListeners();
            loadChanges();
        });
    }

    function setupEventListeners() {
        document.addEventListener('click', (e) => {
            if (e.target.closest('#refresh-change-btn')) {
                loadChanges();
            }
            if (e.target.closest('.change-create-btn')) {
                openCreateModal();
            }
            if (e.target.closest('.change-view-btn')) {
                const id = parseInt(e.target.closest('.change-view-btn').dataset.id);
                viewChange(id);
            }
            if (e.target.closest('#close-change-modal') || e.target.id === 'change-modal') {
                closeModal();
            }
            if (e.target.closest('#cancel-change-btn')) {
                closeModal();
            }
        });

        document.addEventListener('submit', (e) => {
            if (e.target.id === 'change-form') {
                e.preventDefault();
                submitChangeForm();
            }
        });

        // Tab switching
        document.addEventListener('click', (e) => {
            if (e.target.closest('.change-tab-btn')) {
                const tab = e.target.closest('.change-tab-btn').dataset.changeTab;
                switchChangeTab(tab);
            }
        });
    }

    // ==================== API Calls ====================

    async function loadChanges(params = {}) {
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
            if (!response.ok) throw new Error('Không thể tải danh sách thay đổi');
            const data = await response.json();
            changes = data.content || [];
            renderChangesList(changes);
        } catch (error) {
            console.error('Error loading changes:', error);
            Toast.show('Lỗi khi tải danh sách thay đổi', 'error');
        }
    }

    async function loadChangeById(id) {
        const response = await fetch(`${API_BASE}/${id}`, { headers: window.Auth.getAuthHeaders() });
        if (!response.ok) throw new Error('Không thể tải thay đổi');
        return await response.json();
    }

    async function loadTimeline(id) {
        const response = await fetch(`${API_BASE}/${id}/timeline`, { headers: window.Auth.getAuthHeaders() });
        if (!response.ok) throw new Error('Không thể tải dòng thời gian');
        return await response.json();
    }

    async function submitChange(data) {
        const response = await fetch(API_BASE, {
            method: 'POST',
            headers: window.Auth.getAuthHeaders(),
            body: JSON.stringify(data)
        });
        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.message || 'Không thể tạo thay đổi');
        }
        return await response.json();
    }

    async function updateChangeStatus(id, action, body = {}) {
        const response = await fetch(`${API_BASE}/${id}/${action}`, {
            method: 'POST',
            headers: window.Auth.getAuthHeaders(),
            body: JSON.stringify(body)
        });
        if (!response.ok) throw new Error(`Failed to ${action} change`);
        return await response.json();
    }

    // ==================== Rendering ====================

    function renderChangesList(changesList) {
        const container = document.getElementById('change-list');
        if (!container) return;

        if (!changesList || changesList.length === 0) {
            container.innerHTML = `
                <div class="change-empty-state">
                    <div class="change-empty-icon">🔄</div>
                    <h4>Chưa có thay đổi nào</h4>
                    <p>Tạo yêu cầu thay đổi để lên lịch và theo dõi</p>
                </div>
            `;
            return;
        }

        container.innerHTML = changesList.map(change => {
            const statusConfig = getStatusConfig(change.status);
            const riskConfig = getRiskConfig(change.riskLevel);
            
            return `
                <div class="change-card" data-id="${change.id}">
                    <div class="change-card-header">
                        <span class="change-number">${change.changeNumber}</span>
                        <span class="change-type type-${change.changeType?.toLowerCase()}">${change.changeTypeLabel || change.changeType}</span>
                    </div>
                    
                    <h4 class="change-title">${escapeHtml(change.title)}</h4>
                    
                    <div class="change-risk-badge" style="--risk-color: ${riskConfig.color}">
                        <span class="risk-dot"></span>
                        <span class="risk-label">${riskConfig.label}</span>
                        ${change.riskScore ? `<span class="risk-score">${change.riskScore}</span>` : ''}
                    </div>
                    
                    <div class="change-meta">
                        <span class="meta-item">
                            <span class="meta-icon">📅</span>
                            ${change.scheduledStartDate ? formatDate(change.scheduledStartDate) : 'Chưa lên lịch'}
                        </span>
                        <span class="meta-item">
                            <span class="meta-icon">👤</span>
                            ${escapeHtml(nameOf(change.requesterUsername) || change.requesterName || 'Không rõ')}
                        </span>
                    </div>
                    
                    <div class="change-footer">
                        <span class="change-status" style="--status-color: ${statusConfig.color}">
                            ${statusConfig.icon} ${statusConfig.label}
                        </span>
                        <button class="change-view-btn" data-id="${change.id}">Xem chi tiết</button>
                    </div>
                </div>
            `;
        }).join('');
    }

    function getStatusConfig(status) {
        const configs = {
            'DRAFT': { label: 'Nháp', color: '#64748B', icon: '📝' },
            'SUBMITTED': { label: 'Đã gửi duyệt', color: '#F59E0B', icon: '📤' },
            'PENDING_APPROVAL': { label: 'Chờ phê duyệt', color: '#F59E0B', icon: '⏳' },
            'APPROVED': { label: 'Đã phê duyệt', color: '#10B981', icon: '✅' },
            'REJECTED': { label: 'Bị từ chối', color: '#F43F5E', icon: '❌' },
            'SCHEDULED': { label: 'Đã lên lịch', color: '#8B5CF6', icon: '📅' },
            'IN_PROGRESS': { label: 'Đang thực hiện', color: '#3B82F6', icon: '⚡' },
            'COMPLETED': { label: 'Hoàn thành', color: '#10B981', icon: '🎉' },
            'ROLLED_BACK': { label: 'Đã hoàn tác', color: '#F97316', icon: '↩️' },
            'CANCELLED': { label: 'Đã hủy', color: '#6B7280', icon: '🚫' }
        };
        return configs[status] || configs['DRAFT'];
    }

    function getRiskConfig(risk) {
        const configs = {
            'LOW': { label: 'Rủi ro thấp', color: '#10B981' },
            'MEDIUM': { label: 'Rủi ro trung bình', color: '#F59E0B' },
            'HIGH': { label: 'Rủi ro cao', color: '#F97316' },
            'CRITICAL': { label: 'Nghiêm trọng', color: '#F43F5E' }
        };
        return configs[risk] || configs['MEDIUM'];
    }

    // ==================== Tab Switching ====================

    function switchChangeTab(tab) {
        document.querySelectorAll('.change-tab-btn').forEach(btn => {
            btn.classList.toggle('active', btn.dataset.changeTab === tab);
        });

        document.getElementById('change-list-section')?.classList.toggle('hidden', tab !== 'changes');
        document.getElementById('change-calendar-section')?.classList.toggle('hidden', tab !== 'calendar');
    }

    // ==================== Modal ====================

    function openCreateModal() {
        document.getElementById('change-modal-title').textContent = 'Tạo yêu cầu thay đổi';
        resetForm();
        document.getElementById('change-modal').classList.remove('hidden');
        document.body.classList.add('modal-open');
    }

    function viewChange(id) {
        Promise.all([loadChangeById(id), loadTimeline(id)])
            .then(([change, timeline]) => {
                currentChange = change;
                renderChangeDetail(change, timeline);
                document.getElementById('change-modal-title').textContent = change.changeNumber;
                document.getElementById('change-modal').classList.remove('hidden');
                document.body.classList.add('modal-open');
            })
            .catch(err => {
                Toast.show('Lỗi khi tải thay đổi', 'error');
            });
    }

    function renderChangeDetail(change, timeline) {
        const container = document.getElementById('change-form');
        if (!container) return;

        const riskConfig = getRiskConfig(change.riskLevel);
        const statusConfig = getStatusConfig(change.status);

        container.innerHTML = `
            <div class="change-detail-layout">
                <div class="change-detail-main">
                    <div class="change-detail-header">
                        <div class="header-left">
                            <h3>${escapeHtml(change.title)}</h3>
                            <p>${escapeHtml(change.description || 'Chưa có mô tả')}</p>
                        </div>
                        <div class="header-right">
                            <div class="risk-display" style="--risk-color: ${riskConfig.color}">
                                <span class="risk-label">${riskConfig.label}</span>
                                ${change.riskScore ? `<span class="risk-score">${change.riskScore}/100</span>` : ''}
                            </div>
                            <div class="status-display" style="--status-color: ${statusConfig.color}">
                                ${statusConfig.icon} ${statusConfig.label}
                            </div>
                        </div>
                    </div>

                    <div class="change-section">
                        <h4>Đánh giá ảnh hưởng</h4>
                        <div class="impact-grid">
                            <div class="impact-stat">
                                <span class="stat-value">${change.impactLevelLabel || change.impactLevel}</span>
                                <span class="stat-label">Mức ảnh hưởng</span>
                            </div>
                            <div class="impact-stat">
                                <span class="stat-value">${change.affectedUsersCount || 0}</span>
                                <span class="stat-label">Người dùng bị ảnh hưởng</span>
                            </div>
                            <div class="impact-stat">
                                <span class="stat-value">${change.estimatedDowntimeMinutes || 0}m</span>
                                <span class="stat-label">Thời gian gián đoạn dự kiến</span>
                            </div>
                        </div>
                    </div>

                    <div class="change-section">
                        <h4>Kế hoạch triển khai</h4>
                        <p class="section-content">${escapeHtml(change.implementationPlan || 'Chưa có kế hoạch triển khai')}</p>
                    </div>

                    <div class="change-section">
                        <h4>Phương án hoàn tác</h4>
                        <p class="section-content">${escapeHtml(change.rollbackPlan || 'Chưa có phương án hoàn tác')}</p>
                    </div>

                    <div class="change-actions">
                        ${getActionButtons(change)}
                    </div>
                </div>

                <div class="change-detail-sidebar">
                    <div class="sidebar-section">
                        <h5>Chi tiết</h5>
                        <dl class="detail-list">
                            <dt>Danh mục</dt>
                            <dd>${change.category || 'N/A'}</dd>
                            <dt>Độ ưu tiên</dt>
                            <dd>${({ LOW: 'Thấp', MEDIUM: 'Trung bình', HIGH: 'Cao', CRITICAL: 'Nghiêm trọng' })[change.priority] || change.priority || 'N/A'}</dd>
                            <dt>Đã lên lịch</dt>
                            <dd>${change.scheduledStartDate ? formatDate(change.scheduledStartDate) : 'Chưa đặt'}</dd>
                            <dt>Người yêu cầu</dt>
                            <dd>${escapeHtml(nameOf(change.requesterUsername) || change.requesterName || 'Không rõ')}</dd>
                            <dt>Người phụ trách</dt>
                            <dd>${change.changeOwner || 'Chưa phân công'}</dd>
                        </dl>
                    </div>

                    <div class="sidebar-section">
                        <h5>Dòng thời gian</h5>
                        <div class="timeline">
                            ${renderTimeline(timeline)}
                        </div>
                    </div>
                </div>
            </div>
        `;

        setupActionListeners(change);
    }

    function renderTimeline(timeline) {
        if (!timeline || timeline.length === 0) {
            return '<p class="timeline-empty">Chưa có hoạt động</p>';
        }

        const eventLabels = {
            'CREATED': 'Ngày tạo',
            'SUBMITTED': 'Đã gửi duyệt',
            'APPROVED': 'Đã phê duyệt',
            'REJECTED': 'Bị từ chối',
            'SCHEDULED': 'Đã lên lịch',
            'STARTED': 'Bắt đầu triển khai',
            'COMPLETED': 'Hoàn thành',
            'ROLLED_BACK': 'Đã hoàn tác',
            'CANCELLED': 'Đã hủy'
        };

        return timeline.map(event => `
            <div class="timeline-item">
                <div class="timeline-dot"></div>
                <div class="timeline-content">
                    <span class="timeline-event">${eventLabels[event.eventType] || event.eventType}</span>
                    <span class="timeline-actor">${escapeHtml(nameOf(event.actorName) || 'Hệ thống')}</span>
                    <span class="timeline-time">${formatDate(event.createdAt)}</span>
                    ${event.notes ? `<p class="timeline-notes">${escapeHtml(event.notes)}</p>` : ''}
                </div>
            </div>
        `).join('');
    }

    function getActionButtons(change) {
        const actions = [];
        
        switch (change.status) {
            case 'DRAFT':
                actions.push({ action: 'submit', label: 'Gửi duyệt', class: 'primary' });
                actions.push({ action: 'cancel', label: 'Hủy', class: 'ghost' });
                break;
            case 'PENDING_APPROVAL':
                actions.push({ action: 'approve', label: 'Phê duyệt', class: 'success' });
                actions.push({ action: 'reject', label: 'Từ chối', class: 'danger' });
                break;
            case 'APPROVED':
            case 'SCHEDULED':
                actions.push({ action: 'start', label: 'Bắt đầu triển khai', class: 'primary' });
                break;
            case 'IN_PROGRESS':
                actions.push({ action: 'complete', label: 'Đánh dấu hoàn thành', class: 'success' });
                actions.push({ action: 'rollback', label: 'Hoàn tác', class: 'warning' });
                break;
        }
        
        // Mirror ChangeAuthorization: approve/reject = Trưởng phòng IT, the rest = IT operator.
        const allowed = actions.filter(a => ['approve', 'reject'].includes(a.action)
            ? window.Auth?.isItLead?.() : window.Auth?.isItOperator?.());
        return allowed.map(a => 
            `<button class="change-action-btn ${a.class}" data-action="${a.action}">${a.label}</button>`
        ).join('');
    }

    function setupActionListeners(change) {
        document.querySelectorAll('.change-action-btn').forEach(btn => {
            btn.addEventListener('click', async () => {
                const action = btn.dataset.action;
                const id = change.id;
                
                try {
                    switch (action) {
                        case 'approve':
                            await updateChangeStatus(id, 'approve', { approver: 'admin', notes: 'Đã phê duyệt' });
                            break;
                        case 'reject':
                            const reason = prompt('Nhập lý do từ chối:');
                            if (reason) {
                                await updateChangeStatus(id, 'reject', { approver: 'admin', notes: reason });
                            }
                            break;
                        case 'start':
                            await updateChangeStatus(id, 'start');
                            break;
                        case 'complete':
                            const notes = prompt('Nhập ghi chú hoàn thành:');
                            await updateChangeStatus(id, 'complete', { notes: notes || 'Hoàn thành' });
                            break;
                        case 'cancel':
                            await updateChangeStatus(id, 'cancel', { reason: 'Người dùng đã hủy' });
                            break;
                        case 'submit':
                            await updateChangeStatus(id, 'submit');
                            break;
                        case 'rollback':
                            await updateChangeStatus(id, 'reject', { approver: 'admin', notes: 'Đã bắt đầu hoàn tác' });
                            break;
                    }
                    Toast.show('Đã cập nhật thay đổi', 'success');
                    closeModal();
                    loadChanges();
                } catch (err) {
                    Toast.show('Không thể cập nhật thay đổi', 'error');
                }
            });
        });
    }

    function closeModal() {
        document.getElementById('change-modal').classList.add('hidden');
        document.body.classList.remove('modal-open');
        currentChange = null;
    }

    function resetForm() {
        const form = document.getElementById('change-form');
        if (form) {
            form.innerHTML = `
                <div class="change-form-grid">
                    <label class="form-field full-width">
                        <span>Tiêu đề *</span>
                        <input type="text" id="change-title" maxlength="300" required 
                               placeholder="VD: Nâng cấp firmware máy chủ CSDL" />
                    </label>

                    <label class="form-field full-width">
                        <span>Mô tả</span>
                        <textarea id="change-description" rows="3" 
                                  placeholder="Mô tả chi tiết thay đổi..."></textarea>
                    </label>

                    <label class="form-field">
                        <span>Loại</span>
                        <select id="change-type">
                            <option value="STANDARD">Tiêu chuẩn</option>
                            <option value="NORMAL" selected>Thông thường</option>
                            <option value="EMERGENCY">Khẩn cấp</option>
                        </select>
                    </label>

                    <label class="form-field">
                        <span>Danh mục</span>
                        <select id="change-category">
                            <option value="">Chọn danh mục</option>
                            <option value="HARDWARE">Phần cứng</option>
                            <option value="SOFTWARE">Phần mềm</option>
                            <option value="NETWORK">Mạng</option>
                            <option value="SECURITY">Bảo mật</option>
                            <option value="DATABASE">Cơ sở dữ liệu</option>
                            <option value="APPLICATION">Ứng dụng</option>
                            <option value="INFRASTRUCTURE">Hạ tầng</option>
                            <option value="PROCESS">Quy trình</option>
                        </select>
                    </label>

                    <label class="form-field">
                        <span>Mức rủi ro</span>
                        <select id="change-risk">
                            <option value="LOW">Thấp</option>
                            <option value="MEDIUM" selected>Trung bình</option>
                            <option value="HIGH">Cao</option>
                            <option value="CRITICAL">Nghiêm trọng</option>
                        </select>
                    </label>

                    <label class="form-field">
                        <span>Độ ưu tiên</span>
                        <select id="change-priority">
                            <option value="LOW">Thấp</option>
                            <option value="MEDIUM" selected>Trung bình</option>
                            <option value="HIGH">Cao</option>
                            <option value="CRITICAL">Nghiêm trọng</option>
                        </select>
                    </label>

                    <label class="form-field">
                        <span>Mức ảnh hưởng</span>
                        <select id="change-impact">
                            <option value="MINIMAL">Rất ít</option>
                            <option value="MODERATE" selected>Vừa phải</option>
                            <option value="SIGNIFICANT">Đáng kể</option>
                            <option value="SEVERE">Nghiêm trọng</option>
                            <option value="CATASTROPHIC">Thảm họa</option>
                        </select>
                    </label>

                    <label class="form-field">
                        <span>Người dùng bị ảnh hưởng</span>
                        <input type="number" id="change-users" min="0" value="0" />
                    </label>

                    <label class="form-field">
                        <span>Thời gian bắt đầu</span>
                        <input type="datetime-local" id="change-start" />
                    </label>

                    <label class="form-field">
                        <span>Thời gian gián đoạn (phút)</span>
                        <input type="number" id="change-downtime" min="0" value="0" />
                    </label>

                    <label class="form-field full-width">
                        <span>Kế hoạch triển khai</span>
                        <textarea id="change-plan" rows="4" 
                                  placeholder="Các bước triển khai..."></textarea>
                    </label>

                    <label class="form-field full-width">
                        <span>Phương án hoàn tác</span>
                        <textarea id="change-rollback" rows="3" 
                                  placeholder="Cách hoàn tác nếu có sự cố..."></textarea>
                    </label>
                </div>

                <div class="form-actions">
                    <button type="submit" id="change-submit-btn" class="btn-primary">Tạo yêu cầu thay đổi</button>
                    <button type="button" id="cancel-change-btn" class="btn-ghost">Hủy</button>
                </div>
            `;
        }
    }

    async function submitChangeForm() {
        if (isSubmitting) return;
        isSubmitting = true;

        const submitBtn = document.getElementById('change-submit-btn');
        if (submitBtn) {
            submitBtn.disabled = true;
            submitBtn.textContent = 'Đang tạo...';
        }

        const data = {
            title: document.getElementById('change-title').value.trim(),
            description: document.getElementById('change-description').value.trim(),
            changeType: document.getElementById('change-type').value,
            category: document.getElementById('change-category').value,
            riskLevel: document.getElementById('change-risk').value,
            priority: document.getElementById('change-priority').value,
            impactLevel: document.getElementById('change-impact').value,
            affectedUsersCount: parseInt(document.getElementById('change-users').value) || 0,
            scheduledStartDate: document.getElementById('change-start').value || null,
            estimatedDowntimeMinutes: parseInt(document.getElementById('change-downtime').value) || 0,
            implementationPlan: document.getElementById('change-plan').value.trim(),
            rollbackPlan: document.getElementById('change-rollback').value.trim(),
            requesterUsername: 'admin',
            requesterName: 'Admin User'
        };

        if (!data.title) {
            Toast.show('Vui lòng nhập tiêu đề (Title)', 'error');
            if (submitBtn) {
                submitBtn.disabled = false;
                submitBtn.textContent = 'Tạo yêu cầu thay đổi';
            }
            isSubmitting = false;
            return;
        }
        
        if (data.title.length < 5) {
            Toast.show('Tiêu đề phải có ít nhất 5 ký tự', 'error');
            if (submitBtn) {
                submitBtn.disabled = false;
                submitBtn.textContent = 'Tạo yêu cầu thay đổi';
            }
            isSubmitting = false;
            return;
        }
        
        if (data.title.length > 300) {
            Toast.show('Tiêu đề không được vượt quá 300 ký tự', 'error');
            if (submitBtn) {
                submitBtn.disabled = false;
                submitBtn.textContent = 'Tạo yêu cầu thay đổi';
            }
            isSubmitting = false;
            return;
        }

        try {
            await submitChange(data);
            Toast.show('Đã tạo yêu cầu thay đổi', 'success');
            closeModal();
            loadChanges();
        } catch (error) {
            Toast.show(error.message, 'error');
        } finally {
            if (submitBtn) {
                submitBtn.disabled = false;
                submitBtn.textContent = 'Tạo yêu cầu thay đổi';
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
            month: 'short',
            hour: '2-digit',
            minute: '2-digit'
        });
    }

    // ==================== Public API ====================

    return {
        init,
        loadChanges
    };
})();

// Auto-initialize
document.addEventListener('DOMContentLoaded', () => {
    setTimeout(() => ChangeManagement.init(), 100);
});

window.ChangeManagement = ChangeManagement;
export { ChangeManagement };
