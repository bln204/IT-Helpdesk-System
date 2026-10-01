/**
 * SLA Policy Module - Quản lý SLA Policies
 */
const SlaPolicy = (() => {
    'use strict';

    const API_BASE = '/api/sla-policies';

    // State
    let policies = [];
    let currentPolicy = null;

    // DOM Elements
    let slaPolicySection = null;
    let slaPolicyList = null;
    let slaPolicyModal = null;
    let slaPolicyForm = null;

    // ==================== Initialization ====================

    function init() {
        console.log('[SlaPolicy] init() called');
        if (typeof window.Auth === 'undefined') {
            console.error('[SlaPolicy] Auth module not found');
            return;
        }

        // Directly setup and load (auth is already validated during login)
        console.log('[SlaPolicy] Token valid:', window.Auth.isTokenValid());
        setupEventListeners();
        loadPolicies();
    }

    function setupEventListeners() {
        // Delegation for SLA policy section
        document.addEventListener('click', (e) => {
            if (e.target.closest('#refresh-sla-policies-btn')) {
                loadPolicies();
            }
            if (e.target.closest('.sla-policy-edit-btn')) {
                const id = parseInt(e.target.closest('.sla-policy-edit-btn').dataset.id);
                openEditModal(id);
            }
            if (e.target.closest('.sla-policy-delete-btn')) {
                const id = parseInt(e.target.closest('.sla-policy-delete-btn').dataset.id);
                confirmDelete(id);
            }
            if (e.target.closest('.sla-policy-toggle-btn')) {
                const id = parseInt(e.target.closest('.sla-policy-toggle-btn').dataset.id);
                const enabled = e.target.closest('.sla-policy-toggle-btn').dataset.enabled === 'true';
                toggleEnabled(id, !enabled);
            }
            if (e.target.closest('.sla-policy-set-default-btn')) {
                const id = parseInt(e.target.closest('.sla-policy-set-default-btn').dataset.id);
                setAsDefault(id);
            }
        });

        // Form submission
        document.addEventListener('submit', (e) => {
            if (e.target.id === 'sla-policy-form') {
                e.preventDefault();
                savePolicy();
            }
        });

        // Modal close
        document.addEventListener('click', (e) => {
            if (e.target.closest('#close-sla-policy-modal') || e.target.id === 'sla-policy-modal') {
                closeModal();
            }
            if (e.target.closest('#cancel-sla-policy-btn')) {
                closeModal();
            }
        });

        // Priority change - update time suggestions
        document.addEventListener('change', (e) => {
            if (e.target.id === 'sla-policy-priority') {
                updateTimeSuggestions(e.target.value);
            }
        });
    }

    // ==================== API Calls ====================

    async function loadPolicies() {
        console.log('[SlaPolicy] loadPolicies() called');
        try {
            const headers = window.Auth.getAuthHeaders();
            console.log('[SlaPolicy] Headers:', headers);
            const response = await fetch(API_BASE, { headers: headers });
            console.log('[SlaPolicy] Response status:', response.status);
            
            if (!response.ok) {
                throw new Error('Failed to load SLA policies: ' + response.status);
            }

            const data = await response.json();
            console.log('[SlaPolicy] Data received:', data);
            policies = data;
            renderPolicyList();
        } catch (error) {
            console.error('[SlaPolicy] Error:', error);
            Toast.show('Không thể tải danh sách SLA policies', 'error');
        }
    }

    async function getPolicyById(id) {
        try {
            const response = await fetch(`${API_BASE}/${id}`, { headers: window.Auth.getAuthHeaders() });
            if (!response.ok) throw new Error('Failed to load policy');
            return await response.json();
        } catch (error) {
            console.error('Error loading policy:', error);
            throw error;
        }
    }

    async function createPolicy(data) {
        try {
            const response = await fetch(API_BASE, {
                method: 'POST',
                headers: window.Auth.getAuthHeaders(),
                body: JSON.stringify(data)
            });

            if (!response.ok) {
                const error = await response.json();
                throw new Error(error.message || 'Failed to create policy');
            }

            return await response.json();
        } catch (error) {
            console.error('Error creating policy:', error);
            throw error;
        }
    }

    async function updatePolicy(id, data) {
        try {
            const response = await fetch(`${API_BASE}/${id}`, {
                method: 'PUT',
                headers: window.Auth.getAuthHeaders(),
                body: JSON.stringify(data)
            });

            if (!response.ok) {
                const error = await response.json();
                throw new Error(error.message || 'Failed to update policy');
            }

            return await response.json();
        } catch (error) {
            console.error('Error updating policy:', error);
            throw error;
        }
    }

    async function deletePolicy(id) {
        try {
            const response = await fetch(`${API_BASE}/${id}`, {
                method: 'DELETE',
                headers: window.Auth.getAuthHeaders()
            });

            if (!response.ok) {
                const error = await response.json();
                throw new Error(error.message || 'Failed to delete policy');
            }
        } catch (error) {
            console.error('Error deleting policy:', error);
            throw error;
        }
    }

    async function toggleEnabled(id, enabled) {
        try {
            const response = await fetch(`${API_BASE}/${id}/toggle`, {
                method: 'PATCH',
                headers: window.Auth.getAuthHeaders(),
                body: JSON.stringify({ enabled })
            });

            if (!response.ok) {
                const error = await response.json();
                throw new Error(error.message || 'Failed to toggle policy');
            }

            await loadPolicies();
            Toast.show(enabled ? 'Policy đã được kích hoạt' : 'Policy đã được vô hiệu hóa', 'success');
        } catch (error) {
            console.error('Error toggling policy:', error);
            Toast.show(error.message, 'error');
        }
    }

    async function setAsDefault(id) {
        try {
            const response = await fetch(`${API_BASE}/${id}/set-default`, {
                method: 'POST',
                headers: window.Auth.getAuthHeaders()
            });

            if (!response.ok) {
                const error = await response.json();
                throw new Error(error.message || 'Failed to set default');
            }

            await loadPolicies();
            Toast.show('Đã đặt làm policy mặc định', 'success');
        } catch (error) {
            console.error('Error setting default:', error);
            Toast.show(error.message, 'error');
        }
    }

    // ==================== Rendering ====================

    function renderPolicyList() {
        const container = document.getElementById('sla-policy-list');
        console.log('[SlaPolicy] renderPolicyList() called, container:', !!container);
        if (!container) {
            console.error('[SlaPolicy] Container sla-policy-list not found!');
            return;
        }

        if (policies.length === 0) {
            container.innerHTML = `
                <div class="empty-state">
                    <p>Chưa có SLA policy nào.</p>
                    <button class="secondary" onclick="SlaPolicy.openCreateModal()">Tạo SLA Policy đầu tiên</button>
                </div>
            `;
            return;
        }

        // Group by priority
        const grouped = policies.reduce((acc, policy) => {
            const priority = policy.priority;
            if (!acc[priority]) acc[priority] = [];
            acc[priority].push(policy);
            return acc;
        }, {});

        const priorityOrder = ['CRITICAL', 'URGENT', 'HIGH', 'MEDIUM', 'LOW'];
        const priorityLabels = {
            'CRITICAL': { label: 'Nghiêm trọng', class: 'priority-critical' },
            'URGENT': { label: 'Khẩn cấp', class: 'priority-urgent' },
            'HIGH': { label: 'Cao', class: 'priority-high' },
            'MEDIUM': { label: 'Trung bình', class: 'priority-medium' },
            'LOW': { label: 'Thấp', class: 'priority-low' }
        };

        console.log('[SlaPolicy] Rendering', policies.length, 'policies');
        container.innerHTML = priorityOrder
            .filter(p => grouped[p])
            .map(priority => `
                <div class="sla-priority-group">
                    <h4 class="sla-priority-header">
                        <span class="sla-policy-card-priority ${priorityLabels[priority].class}">
                            ${priorityLabels[priority].label}
                        </span>
                    </h4>
                    <div class="sla-policy-grid">
                        ${grouped[priority].map(policy => renderPolicyCard(policy)).join('')}
                    </div>
                </div>
            `).join('');
        console.log('[SlaPolicy] innerHTML set, length:', container.innerHTML.length);
    }

    function renderPolicyCard(policy) {
        const isDefault = policy.isDefault ? '<span class="sla-default-badge">Mặc định</span>' : '';
        const enabled = policy.enabled;

        const actions = [];
        
        if (!isDefault) {
            actions.push(`
                <button class="ghost btn-sm sla-policy-set-default-btn" data-id="${policy.id}" title="Đặt làm mặc định">
                    ★
                </button>
            `);
        }

        actions.push(`
            <button class="secondary btn-sm sla-policy-edit-btn" data-id="${policy.id}">
                Sửa
            </button>
        `);

        if (!policy.isDefault) {
            actions.push(`
                <button class="ghost btn-sm ${enabled ? 'text-warning' : 'text-success'} sla-policy-toggle-btn" 
                        data-id="${policy.id}" data-enabled="${enabled}">
                    ${enabled ? 'Vô hiệu' : 'Kích hoạt'}
                </button>
            `);
            actions.push(`
                <button class="ghost btn-sm text-danger sla-policy-delete-btn" data-id="${policy.id}">
                    Xóa
                </button>
            `);
        }

        return `
            <div class="sla-policy-card ${!enabled ? 'disabled' : ''}">
                <div class="sla-policy-card-header">
                    <div>
                        <span class="sla-policy-card-title">${escapeHtml(policy.name)}${isDefault}</span>
                    </div>
                </div>
                <p class="sla-policy-card-description">${escapeHtml(policy.description || 'Không có mô tả')}</p>
                <div class="sla-policy-card-times">
                    <div class="sla-policy-time">
                        <span class="sla-policy-time-label">Phản hồi</span>
                        <span class="sla-policy-time-value">${policy.responseTimeFormatted || formatMinutes(policy.responseMinutes)}</span>
                    </div>
                    <div class="sla-policy-time">
                        <span class="sla-policy-time-label">Giải quyết</span>
                        <span class="sla-policy-time-value">${policy.resolutionTimeFormatted || formatMinutes(policy.resolutionMinutes)}</span>
                    </div>
                </div>
                <div style="font-size: 0.75rem; color: var(--muted); margin-bottom: 8px;">
                    Cảnh báo: ${policy.warningThreshold}% | Business Hours: ${policy.businessHoursOnly ? 'Có' : 'Không'}
                </div>
                <div class="sla-policy-card-actions">
                    ${actions.join('')}
                </div>
            </div>
        `;
    }

    // ==================== Modal ====================

    function openCreateModal() {
        currentPolicy = null;
        resetForm();
        document.getElementById('sla-policy-modal-title').textContent = 'Tạo SLA Policy mới';
        document.getElementById('sla-policy-submit-btn').textContent = 'Tạo Policy';
        openModal();
    }

    async function openEditModal(id) {
        try {
            currentPolicy = await getPolicyById(id);
            populateForm(currentPolicy);
            document.getElementById('sla-policy-modal-title').textContent = 'Chỉnh sửa SLA Policy';
            document.getElementById('sla-policy-submit-btn').textContent = 'Lưu thay đổi';

            // Disable priority change if it's the only policy for that priority
            const prioritySelect = document.getElementById('sla-policy-priority');
            const policiesForPriority = policies.filter(p => p.priority === currentPolicy.priority);
            if (policiesForPriority.length <= 1 && currentPolicy.isDefault) {
                prioritySelect.disabled = true;
            } else {
                prioritySelect.disabled = false;
            }

            openModal();
        } catch (error) {
            Toast.show('Không thể tải thông tin policy', 'error');
        }
    }

    function openModal() {
        const modal = document.getElementById('sla-policy-modal');
        if (modal) {
            modal.classList.remove('hidden');
            document.body.classList.add('modal-open');
        }
    }

    function closeModal() {
        const modal = document.getElementById('sla-policy-modal');
        if (modal) {
            modal.classList.add('hidden');
            document.body.classList.remove('modal-open');
        }
        currentPolicy = null;
        resetForm();
    }

    function resetForm() {
        const form = document.getElementById('sla-policy-form');
        if (form) form.reset();
        document.getElementById('sla-policy-id').value = '';
        document.getElementById('sla-policy-priority').disabled = false;
    }

    function populateForm(policy) {
        document.getElementById('sla-policy-id').value = policy.id;
        document.getElementById('sla-policy-name').value = policy.name;
        document.getElementById('sla-policy-description').value = policy.description || '';
        document.getElementById('sla-policy-priority').value = policy.priority;
        document.getElementById('sla-policy-response-minutes').value = policy.responseMinutes;
        document.getElementById('sla-policy-resolution-minutes').value = policy.resolutionMinutes;
        document.getElementById('sla-policy-warning-threshold').value = policy.warningThreshold;
        document.getElementById('sla-policy-business-hours').checked = policy.businessHoursOnly;
        document.getElementById('sla-policy-default').checked = policy.isDefault;
        document.getElementById('sla-policy-enabled').checked = policy.enabled;
    }

    function getFormData() {
        return {
            name: document.getElementById('sla-policy-name').value.trim(),
            description: document.getElementById('sla-policy-description').value.trim(),
            priority: document.getElementById('sla-policy-priority').value,
            responseMinutes: parseInt(document.getElementById('sla-policy-response-minutes').value) || 60,
            resolutionMinutes: parseInt(document.getElementById('sla-policy-resolution-minutes').value) || 480,
            warningThreshold: parseInt(document.getElementById('sla-policy-warning-threshold').value) || 75,
            businessHoursOnly: document.getElementById('sla-policy-business-hours').checked,
            isDefault: document.getElementById('sla-policy-default').checked,
            enabled: document.getElementById('sla-policy-enabled').checked
        };
    }

    async function savePolicy() {
        const data = getFormData();

        // Validation
        if (!data.name) {
            Toast.show('Vui lòng nhập tên policy', 'error');
            return;
        }
        if (!data.priority) {
            Toast.show('Vui lòng chọn priority', 'error');
            return;
        }
        if (data.responseMinutes <= 0) {
            Toast.show('Response time phải lớn hơn 0', 'error');
            return;
        }
        if (data.resolutionMinutes <= 0) {
            Toast.show('Resolution time phải lớn hơn 0', 'error');
            return;
        }
        if (data.responseMinutes > data.resolutionMinutes) {
            Toast.show('Response time không được lớn hơn Resolution time', 'error');
            return;
        }

        try {
            let result;
            if (currentPolicy) {
                result = await updatePolicy(currentPolicy.id, data);
                Toast.show('Đã cập nhật SLA policy', 'success');
            } else {
                result = await createPolicy(data);
                Toast.show('Đã tạo SLA policy mới', 'success');
            }

            closeModal();
            await loadPolicies();
        } catch (error) {
            Toast.show(error.message, 'error');
        }
    }

    function confirmDelete(id) {
        const policy = policies.find(p => p.id === id);
        if (!policy) return;

        if (confirm(`Bạn có chắc muốn xóa SLA Policy "${policy.name}"?\n\nHành động này không thể hoàn tác.`)) {
            deletePolicy(id)
                .then(() => {
                    Toast.show('Đã xóa SLA policy', 'success');
                    loadPolicies();
                })
                .catch(error => {
                    Toast.show(error.message, 'error');
                });
        }
    }

    // ==================== Helpers ====================

    function updateTimeSuggestions(priority) {
        // Default suggestions based on priority
        const suggestions = {
            'CRITICAL': { response: 15, resolution: 120 },
            'URGENT': { response: 60, resolution: 360 },
            'HIGH': { response: 30, resolution: 240 },
            'MEDIUM': { response: 120, resolution: 480 },
            'LOW': { response: 480, resolution: 4320 }
        };

        const suggestion = suggestions[priority];
        if (suggestion) {
            const responseInput = document.getElementById('sla-policy-response-minutes');
            const resolutionInput = document.getElementById('sla-policy-resolution-minutes');
            
            // Only update if fields are empty or match previous suggestion
            if (!responseInput.value || responseInput.value === '60') {
                responseInput.value = suggestion.response;
            }
            if (!resolutionInput.value || resolutionInput.value === '480') {
                resolutionInput.value = suggestion.resolution;
            }
        }
    }

    function formatMinutes(minutes) {
        if (minutes < 60) {
            return `${minutes} phút`;
        } else if (minutes < 1440) {
            const hours = Math.floor(minutes / 60);
            return `${hours} giờ`;
        } else {
            const days = Math.floor(minutes / 1440);
            return `${days} ngày`;
        }
    }

    function escapeHtml(text) {
        if (!text) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    // ==================== Public API ====================

    return {
        init,
        loadPolicies,
        openCreateModal,
        openEditModal,
        closeModal
    };
})();

// No auto-initialize - let app.js control initialization after Auth is ready
// This ensures Auth module is always available when Escalation.init() is called

window.SlaPolicy = SlaPolicy;
export { SlaPolicy };
