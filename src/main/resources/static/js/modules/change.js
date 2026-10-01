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
            if (!response.ok) throw new Error('Failed to load changes');
            const data = await response.json();
            changes = data.content || [];
            renderChangesList(changes);
        } catch (error) {
            console.error('Error loading changes:', error);
            Toast.show('Lỗi khi tải danh sách Changes', 'error');
        }
    }

    async function loadChangeById(id) {
        const response = await fetch(`${API_BASE}/${id}`, { headers: window.Auth.getAuthHeaders() });
        if (!response.ok) throw new Error('Failed to load change');
        return await response.json();
    }

    async function loadTimeline(id) {
        const response = await fetch(`${API_BASE}/${id}/timeline`, { headers: window.Auth.getAuthHeaders() });
        if (!response.ok) throw new Error('Failed to load timeline');
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
            throw new Error(error.message || 'Failed to create change');
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
                    <h4>No active changes</h4>
                    <p>Schedule and manage infrastructure changes here</p>
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
                            ${change.scheduledStartDate ? formatDate(change.scheduledStartDate) : 'Not scheduled'}
                        </span>
                        <span class="meta-item">
                            <span class="meta-icon">👤</span>
                            ${change.requesterName || change.requesterUsername || 'Unknown'}
                        </span>
                    </div>
                    
                    <div class="change-footer">
                        <span class="change-status" style="--status-color: ${statusConfig.color}">
                            ${statusConfig.icon} ${statusConfig.label}
                        </span>
                        <button class="change-view-btn" data-id="${change.id}">View details</button>
                    </div>
                </div>
            `;
        }).join('');
    }

    function getStatusConfig(status) {
        const configs = {
            'DRAFT': { label: 'Draft', color: '#64748B', icon: '📝' },
            'SUBMITTED': { label: 'Submitted', color: '#F59E0B', icon: '📤' },
            'PENDING_APPROVAL': { label: 'Pending Approval', color: '#F59E0B', icon: '⏳' },
            'APPROVED': { label: 'Approved', color: '#10B981', icon: '✅' },
            'REJECTED': { label: 'Rejected', color: '#F43F5E', icon: '❌' },
            'SCHEDULED': { label: 'Scheduled', color: '#8B5CF6', icon: '📅' },
            'IN_PROGRESS': { label: 'In Progress', color: '#3B82F6', icon: '⚡' },
            'COMPLETED': { label: 'Completed', color: '#10B981', icon: '🎉' },
            'ROLLED_BACK': { label: 'Rolled Back', color: '#F97316', icon: '↩️' },
            'CANCELLED': { label: 'Cancelled', color: '#6B7280', icon: '🚫' }
        };
        return configs[status] || configs['DRAFT'];
    }

    function getRiskConfig(risk) {
        const configs = {
            'LOW': { label: 'Low Risk', color: '#10B981' },
            'MEDIUM': { label: 'Medium Risk', color: '#F59E0B' },
            'HIGH': { label: 'High Risk', color: '#F97316' },
            'CRITICAL': { label: 'Critical', color: '#F43F5E' }
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
        document.getElementById('change-modal-title').textContent = 'New Change Request';
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
                Toast.show('Error loading change', 'error');
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
                            <p>${escapeHtml(change.description || 'No description')}</p>
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
                        <h4>Impact Assessment</h4>
                        <div class="impact-grid">
                            <div class="impact-stat">
                                <span class="stat-value">${change.impactLevelLabel || change.impactLevel}</span>
                                <span class="stat-label">Impact Level</span>
                            </div>
                            <div class="impact-stat">
                                <span class="stat-value">${change.affectedUsersCount || 0}</span>
                                <span class="stat-label">Users Affected</span>
                            </div>
                            <div class="impact-stat">
                                <span class="stat-value">${change.estimatedDowntimeMinutes || 0}m</span>
                                <span class="stat-label">Downtime Est.</span>
                            </div>
                        </div>
                    </div>

                    <div class="change-section">
                        <h4>Implementation Plan</h4>
                        <p class="section-content">${escapeHtml(change.implementationPlan || 'No implementation plan defined')}</p>
                    </div>

                    <div class="change-section">
                        <h4>Rollback Procedure</h4>
                        <p class="section-content">${escapeHtml(change.rollbackPlan || 'No rollback procedure defined')}</p>
                    </div>

                    <div class="change-actions">
                        ${getActionButtons(change)}
                    </div>
                </div>

                <div class="change-detail-sidebar">
                    <div class="sidebar-section">
                        <h5>Details</h5>
                        <dl class="detail-list">
                            <dt>Category</dt>
                            <dd>${change.category || 'N/A'}</dd>
                            <dt>Priority</dt>
                            <dd>${change.priority || 'N/A'}</dd>
                            <dt>Scheduled</dt>
                            <dd>${change.scheduledStartDate ? formatDate(change.scheduledStartDate) : 'Not set'}</dd>
                            <dt>Requester</dt>
                            <dd>${change.requesterName || 'Unknown'}</dd>
                            <dt>Owner</dt>
                            <dd>${change.changeOwner || 'Unassigned'}</dd>
                        </dl>
                    </div>

                    <div class="sidebar-section">
                        <h5>Timeline</h5>
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
            return '<p class="timeline-empty">No activity yet</p>';
        }

        const eventLabels = {
            'CREATED': 'Created',
            'SUBMITTED': 'Submitted for review',
            'APPROVED': 'Approved',
            'REJECTED': 'Rejected',
            'SCHEDULED': 'Scheduled',
            'STARTED': 'Implementation started',
            'COMPLETED': 'Completed',
            'ROLLED_BACK': 'Rolled back',
            'CANCELLED': 'Cancelled'
        };

        return timeline.map(event => `
            <div class="timeline-item">
                <div class="timeline-dot"></div>
                <div class="timeline-content">
                    <span class="timeline-event">${eventLabels[event.eventType] || event.eventType}</span>
                    <span class="timeline-actor">${event.actorName || 'System'}</span>
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
                actions.push({ action: 'submit', label: 'Submit for Review', class: 'primary' });
                actions.push({ action: 'cancel', label: 'Cancel', class: 'ghost' });
                break;
            case 'PENDING_APPROVAL':
                actions.push({ action: 'approve', label: 'Approve', class: 'success' });
                actions.push({ action: 'reject', label: 'Reject', class: 'danger' });
                break;
            case 'APPROVED':
            case 'SCHEDULED':
                actions.push({ action: 'start', label: 'Start Implementation', class: 'primary' });
                break;
            case 'IN_PROGRESS':
                actions.push({ action: 'complete', label: 'Mark Complete', class: 'success' });
                actions.push({ action: 'rollback', label: 'Rollback', class: 'warning' });
                break;
        }
        
        return actions.map(a => 
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
                            await updateChangeStatus(id, 'approve', { approver: 'admin', notes: 'Approved' });
                            break;
                        case 'reject':
                            const reason = prompt('Enter rejection reason:');
                            if (reason) {
                                await updateChangeStatus(id, 'reject', { approver: 'admin', notes: reason });
                            }
                            break;
                        case 'start':
                            await updateChangeStatus(id, 'start');
                            break;
                        case 'complete':
                            const notes = prompt('Enter completion notes:');
                            await updateChangeStatus(id, 'complete', { notes: notes || 'Completed' });
                            break;
                        case 'cancel':
                            await updateChangeStatus(id, 'cancel', { reason: 'Cancelled by user' });
                            break;
                        case 'submit':
                            await updateChangeStatus(id, 'submit');
                            break;
                        case 'rollback':
                            await updateChangeStatus(id, 'reject', { approver: 'admin', notes: 'Rollback initiated' });
                            break;
                    }
                    Toast.show('Change updated successfully', 'success');
                    closeModal();
                    loadChanges();
                } catch (err) {
                    Toast.show('Failed to update change', 'error');
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
                        <span>Title *</span>
                        <input type="text" id="change-title" maxlength="300" required 
                               placeholder="e.g., Upgrade database server firmware" />
                    </label>

                    <label class="form-field full-width">
                        <span>Description</span>
                        <textarea id="change-description" rows="3" 
                                  placeholder="Detailed description of the change..."></textarea>
                    </label>

                    <label class="form-field">
                        <span>Type</span>
                        <select id="change-type">
                            <option value="STANDARD">Standard</option>
                            <option value="NORMAL" selected>Normal</option>
                            <option value="EMERGENCY">Emergency</option>
                        </select>
                    </label>

                    <label class="form-field">
                        <span>Category</span>
                        <select id="change-category">
                            <option value="">Select category</option>
                            <option value="HARDWARE">Hardware</option>
                            <option value="SOFTWARE">Software</option>
                            <option value="NETWORK">Network</option>
                            <option value="SECURITY">Security</option>
                            <option value="DATABASE">Database</option>
                            <option value="APPLICATION">Application</option>
                            <option value="INFRASTRUCTURE">Infrastructure</option>
                            <option value="PROCESS">Process</option>
                        </select>
                    </label>

                    <label class="form-field">
                        <span>Risk Level</span>
                        <select id="change-risk">
                            <option value="LOW">Low</option>
                            <option value="MEDIUM" selected>Medium</option>
                            <option value="HIGH">High</option>
                            <option value="CRITICAL">Critical</option>
                        </select>
                    </label>

                    <label class="form-field">
                        <span>Priority</span>
                        <select id="change-priority">
                            <option value="LOW">Low</option>
                            <option value="MEDIUM" selected>Medium</option>
                            <option value="HIGH">High</option>
                            <option value="CRITICAL">Critical</option>
                        </select>
                    </label>

                    <label class="form-field">
                        <span>Impact Level</span>
                        <select id="change-impact">
                            <option value="MINIMAL">Minimal</option>
                            <option value="MODERATE" selected>Moderate</option>
                            <option value="SIGNIFICANT">Significant</option>
                            <option value="SEVERE">Severe</option>
                            <option value="CATASTROPHIC">Catastrophic</option>
                        </select>
                    </label>

                    <label class="form-field">
                        <span>Affected Users</span>
                        <input type="number" id="change-users" min="0" value="0" />
                    </label>

                    <label class="form-field">
                        <span>Scheduled Start</span>
                        <input type="datetime-local" id="change-start" />
                    </label>

                    <label class="form-field">
                        <span>Downtime (minutes)</span>
                        <input type="number" id="change-downtime" min="0" value="0" />
                    </label>

                    <label class="form-field full-width">
                        <span>Implementation Plan</span>
                        <textarea id="change-plan" rows="4" 
                                  placeholder="Step-by-step implementation instructions..."></textarea>
                    </label>

                    <label class="form-field full-width">
                        <span>Rollback Plan</span>
                        <textarea id="change-rollback" rows="3" 
                                  placeholder="How to rollback if something goes wrong..."></textarea>
                    </label>
                </div>

                <div class="form-actions">
                    <button type="submit" id="change-submit-btn" class="btn-primary">Create Change Request</button>
                    <button type="button" id="cancel-change-btn" class="btn-ghost">Cancel</button>
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
            submitBtn.textContent = 'Creating...';
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
                submitBtn.textContent = 'Create Change Request';
            }
            isSubmitting = false;
            return;
        }
        
        if (data.title.length < 5) {
            Toast.show('Tiêu đề phải có ít nhất 5 ký tự', 'error');
            if (submitBtn) {
                submitBtn.disabled = false;
                submitBtn.textContent = 'Create Change Request';
            }
            isSubmitting = false;
            return;
        }
        
        if (data.title.length > 300) {
            Toast.show('Tiêu đề không được vượt quá 300 ký tự', 'error');
            if (submitBtn) {
                submitBtn.disabled = false;
                submitBtn.textContent = 'Create Change Request';
            }
            isSubmitting = false;
            return;
        }

        try {
            await submitChange(data);
            Toast.show('Change request created', 'success');
            closeModal();
            loadChanges();
        } catch (error) {
            Toast.show(error.message, 'error');
        } finally {
            if (submitBtn) {
                submitBtn.disabled = false;
                submitBtn.textContent = 'Create Change Request';
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
