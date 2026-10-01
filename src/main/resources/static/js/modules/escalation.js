/**
 * Escalation Module - Quản lý Escalation Rules
 */
const Escalation = (() => {
    'use strict';

    const API_BASE = '/api/escalation';

    // State
    let rules = [];
    let currentRule = null;

    // ==================== Initialization ====================

    function init() {
        console.log('[Escalation] init() called');
        if (typeof window.Auth === 'undefined') {
            console.error('[Escalation] Auth module not found');
            return;
        }

        // Directly setup and load (auth is already validated during login)
        console.log('[Escalation] Token valid:', window.Auth.isTokenValid());
        setupEventListeners();
        loadRules();
    }

    function setupEventListeners() {
        document.addEventListener('click', (e) => {
            if (e.target.closest('#refresh-escalation-rules-btn')) {
                loadRules();
            }
            if (e.target.closest('.escalation-edit-btn')) {
                const id = parseInt(e.target.closest('.escalation-edit-btn').dataset.id);
                openEditModal(id);
            }
            if (e.target.closest('.escalation-delete-btn')) {
                const id = parseInt(e.target.closest('.escalation-delete-btn').dataset.id);
                confirmDelete(id);
            }
            if (e.target.closest('.escalation-toggle-btn')) {
                const id = parseInt(e.target.closest('.escalation-toggle-btn').dataset.id);
                const enabled = e.target.closest('.escalation-toggle-btn').dataset.enabled === 'true';
                toggleEnabled(id, !enabled);
            }
        });

        document.addEventListener('submit', (e) => {
            if (e.target.id === 'escalation-rule-form') {
                e.preventDefault();
                saveRule();
            }
        });

        document.addEventListener('click', (e) => {
            if (e.target.closest('#close-escalation-modal') || e.target.id === 'escalation-modal') {
                closeModal();
            }
            if (e.target.closest('#cancel-escalation-btn')) {
                closeModal();
            }
        });
    }

    // ==================== API Calls ====================

    async function loadRules() {
        console.log('[Escalation] loadRules() called');
        try {
            const response = await fetch(`${API_BASE}/rules`, { headers: window.Auth.getAuthHeaders() });
            console.log('[Escalation] Response status:', response.status);
            if (!response.ok) throw new Error('Failed to load escalation rules');

            rules = await response.json();
            console.log('[Escalation] Rules loaded:', rules);
            renderRulesList();
        } catch (error) {
            console.error('[Escalation] Error loading escalation rules:', error);
            if (typeof Toast !== 'undefined') {
                Toast.show('Không thể tải danh sách escalation rules', 'error');
            }
        }
    }

    async function getRuleById(id) {
        const response = await fetch(`${API_BASE}/rules/${id}`, { headers: window.Auth.getAuthHeaders() });
        if (!response.ok) throw new Error('Failed to load rule');
        return await response.json();
    }

    async function createRule(data) {
        const response = await fetch(`${API_BASE}/rules`, {
            method: 'POST',
            headers: window.Auth.getAuthHeaders(),
            body: JSON.stringify(data)
        });
        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.message || 'Failed to create rule');
        }
        return await response.json();
    }

    async function updateRule(id, data) {
        const response = await fetch(`${API_BASE}/rules/${id}`, {
            method: 'PUT',
            headers: window.Auth.getAuthHeaders(),
            body: JSON.stringify(data)
        });
        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.message || 'Failed to update rule');
        }
        return await response.json();
    }

    async function deleteRule(id) {
        const response = await fetch(`${API_BASE}/rules/${id}`, {
            method: 'DELETE',
            headers: window.Auth.getAuthHeaders()
        });
        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.message || 'Failed to delete rule');
        }
    }

    async function toggleEnabled(id, enabled) {
        try {
            const response = await fetch(`${API_BASE}/rules/${id}/toggle`, {
                method: 'PATCH',
                headers: window.Auth.getAuthHeaders(),
                body: JSON.stringify({ enabled })
            });
            if (!response.ok) throw new Error('Failed to toggle rule');
            await loadRules();
            Toast.show(enabled ? 'Rule đã được kích hoạt' : 'Rule đã được vô hiệu hóa', 'success');
        } catch (error) {
            Toast.show(error.message, 'error');
        }
    }

    // ==================== Rendering ====================

    function renderRulesList() {
        const container = document.getElementById('escalation-rules-list');
        if (!container) return;

        if (rules.length === 0) {
            container.innerHTML = `
                <div class="empty-state">
                    <p>Chưa có escalation rule nào.</p>
                </div>
            `;
            return;
        }

        // Group by trigger type
        const grouped = rules.reduce((acc, rule) => {
            const trigger = rule.triggerType || 'OTHER';
            if (!acc[trigger]) acc[trigger] = [];
            acc[trigger].push(rule);
            return acc;
        }, {});

        const triggerLabels = {
            'SLA_RESPONSE_WARNING': '⚠️ SLA Phản hồi Warning',
            'SLA_RESPONSE_BREACHED': '✗ SLA Phản hồi Breached',
            'SLA_RESOLUTION_WARNING': '⚠️ SLA Giải quyết Warning',
            'SLA_RESOLUTION_BREACHED': '✗ SLA Giải quyết Breached',
            'CRITICAL_TICKET': '🔴 Ticket Critical',
            'MANUAL': '📋 Thủ công'
        };

        container.innerHTML = Object.entries(grouped).map(([trigger, rulesList]) => `
            <div class="escalation-group">
                <h4 class="escalation-group-header">${triggerLabels[trigger] || trigger}</h4>
                <div class="escalation-rules-grid">
                    ${rulesList.map(rule => renderRuleCard(rule)).join('')}
                </div>
            </div>
        `).join('');
    }

    function renderRuleCard(rule) {
        const statusClass = rule.enabled ? 'enabled' : 'disabled';
        const levelColors = {
            'LEVEL_1': 'level-1',
            'LEVEL_2': 'level-2',
            'LEVEL_3': 'level-3'
        };

        return `
            <div class="escalation-rule-card ${statusClass}">
                <div class="escalation-rule-header">
                    <span class="escalation-rule-name">${escapeHtml(rule.name)}</span>
                    <span class="escalation-level-badge ${levelColors[rule.escalationLevel] || ''}">${rule.escalationLevel || '—'}</span>
                </div>
                <p class="escalation-rule-desc">${escapeHtml(rule.description || 'Không có mô tả')}</p>
                <div class="escalation-rule-details">
                    <div class="escalation-detail">
                        <span class="detail-label">Hành động:</span>
                        <span class="detail-value">${rule.actionLabel || rule.actionType || '—'}</span>
                    </div>
                    <div class="escalation-detail">
                        <span class="detail-label">Ưu tiên tối thiểu:</span>
                        <span class="detail-value">${rule.minPriority || 'Tất cả'}</span>
                    </div>
                    <div class="escalation-detail">
                        <span class="detail-label">Tối đa escalation:</span>
                        <span class="detail-value">${rule.maxEscalations || 3} lần</span>
                    </div>
                </div>
                <div class="escalation-rule-actions">
                    <button class="secondary btn-sm escalation-edit-btn" data-id="${rule.id}">Sửa</button>
                    <button class="ghost btn-sm ${rule.enabled ? 'text-warning' : 'text-success'} escalation-toggle-btn" 
                            data-id="${rule.id}" data-enabled="${rule.enabled}">
                        ${rule.enabled ? 'Vô hiệu' : 'Kích hoạt'}
                    </button>
                    <button class="ghost btn-sm text-danger escalation-delete-btn" data-id="${rule.id}">Xóa</button>
                </div>
            </div>
        `;
    }

    // ==================== Modal ====================

    function openCreateModal() {
        currentRule = null;
        resetForm();
        document.getElementById('escalation-modal-title').textContent = 'Tạo Escalation Rule';
        document.getElementById('escalation-submit-btn').textContent = 'Tạo Rule';
        openModal();
    }

    async function openEditModal(id) {
        try {
            currentRule = await getRuleById(id);
            populateForm(currentRule);
            document.getElementById('escalation-modal-title').textContent = 'Chỉnh sửa Escalation Rule';
            document.getElementById('escalation-submit-btn').textContent = 'Lưu thay đổi';
            openModal();
        } catch (error) {
            Toast.show('Không thể tải thông tin rule', 'error');
        }
    }

    function openModal() {
        const modal = document.getElementById('escalation-modal');
        if (modal) {
            modal.classList.remove('hidden');
            document.body.classList.add('modal-open');
        }
    }

    function closeModal() {
        const modal = document.getElementById('escalation-modal');
        if (modal) {
            modal.classList.add('hidden');
            document.body.classList.remove('modal-open');
        }
        currentRule = null;
        resetForm();
    }

    function resetForm() {
        const form = document.getElementById('escalation-rule-form');
        if (form) form.reset();
        document.getElementById('escalation-rule-id').value = '';
    }

    function populateForm(rule) {
        document.getElementById('escalation-rule-id').value = rule.id;
        document.getElementById('escalation-rule-name').value = rule.name || '';
        document.getElementById('escalation-rule-description').value = rule.description || '';
        document.getElementById('escalation-trigger-type').value = rule.triggerType || 'SLA_RESPONSE_WARNING';
        document.getElementById('escalation-min-priority').value = rule.minPriority || '';
        document.getElementById('escalation-level').value = rule.escalationLevel || 'LEVEL_1';
        document.getElementById('escalation-action-type').value = rule.actionType || 'NOTIFY';
        document.getElementById('escalation-max-escalations').value = rule.maxEscalations || 3;
        document.getElementById('escalation-enabled').checked = rule.enabled !== false;
    }

    function getFormData() {
        return {
            name: document.getElementById('escalation-rule-name').value.trim(),
            description: document.getElementById('escalation-rule-description').value.trim(),
            triggerType: document.getElementById('escalation-trigger-type').value,
            minPriority: document.getElementById('escalation-min-priority').value || null,
            escalationLevel: document.getElementById('escalation-level').value,
            actionType: document.getElementById('escalation-action-type').value,
            maxEscalations: parseInt(document.getElementById('escalation-max-escalations').value) || 3,
            enabled: document.getElementById('escalation-enabled').checked
        };
    }

    async function saveRule() {
        const data = getFormData();

        if (!data.name) {
            Toast.show('Vui lòng nhập tên rule', 'error');
            return;
        }

        try {
            if (currentRule) {
                await updateRule(currentRule.id, data);
                Toast.show('Đã cập nhật escalation rule', 'success');
            } else {
                await createRule(data);
                Toast.show('Đã tạo escalation rule mới', 'success');
            }
            closeModal();
            await loadRules();
        } catch (error) {
            Toast.show(error.message, 'error');
        }
    }

    function confirmDelete(id) {
        const rule = rules.find(r => r.id === id);
        if (!rule) return;

        if (confirm(`Bạn có chắc muốn xóa Escalation Rule "${rule.name}"?\n\nHành động này không thể hoàn tác.`)) {
            deleteRule(id)
                .then(() => {
                    Toast.show('Đã xóa escalation rule', 'success');
                    loadRules();
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

    // ==================== Public API ====================

    return {
        init,
        loadRules,
        openCreateModal,
        openEditModal,
        closeModal
    };
})();

// No auto-initialize - let app.js control initialization after Auth is ready
// This ensures Auth module is always available when Escalation.init() is called

window.Escalation = Escalation;
export { Escalation };
