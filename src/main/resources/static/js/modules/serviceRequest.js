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
            let url = API_BASE;
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
        openCreateModal,
        closeModal
    };
})();

// Auto-initialize
document.addEventListener('DOMContentLoaded', () => {
    setTimeout(() => ServiceRequest.init(), 100);
});

window.ServiceRequest = ServiceRequest;
export { ServiceRequest };
