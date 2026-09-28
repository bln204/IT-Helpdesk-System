/**
 * Asset Management Module - Health-Centric IT Asset Tracking
 */
const AssetManagement = (() => {
    'use strict';

    const API_BASE = '/api/assets';
    const ASSET_TYPES = ['SERVER', 'WORKSTATION', 'LAPTOP', 'NETWORK_DEVICE', 'PRINTER', 'MONITOR', 'MOBILE_DEVICE', 'SOFTWARE_LICENSE', 'DATABASE', 'STORAGE', 'CLOUD_RESOURCE', 'OTHER'];
    const ASSET_STATUSES = ['ACTIVE', 'INACTIVE', 'MAINTENANCE', 'REPAIR', 'RETIRED', 'DISPOSED', 'LOST'];
    const HEALTH_STATUSES = ['HEALTHY', 'WARNING', 'CRITICAL', 'UNKNOWN'];
    const CLOUD_PROVIDERS = ['AWS', 'AZURE', 'GCP', 'ALIYUN', 'OTHER'];
    const LICENSE_TYPES = ['PERPETUAL', 'SUBSCRIPTION', 'OPEN_SOURCE', 'FREE', 'TRIAL', 'OTHER'];

    const VALIDATION = {
        name: { required: true, maxLength: 200, label: 'Name' },
        assetType: { required: true, label: 'Asset Type' },
        assetNumber: { required: true, maxLength: 50, label: 'Asset Number' }
    };

    // State
    let assets = [];
    let currentAsset = null;
    let stats = null;
    let isSubmitting = false;
    let activeTab = 'basic';

    // ==================== Initialization ====================

    function init() {
        if (typeof window.Auth === 'undefined') {
            console.error('Auth module not found');
            return;
        }

        window.Auth.onAuthReady(() => {
            setupEventListeners();
            loadStatistics();
            loadAssets();
        });
    }

    function setupEventListeners() {
        // Track listener registration
        if (window._assetListenersSet) {
            return;
        }
        window._assetListenersSet = true;

        // Main action buttons
        document.addEventListener('click', (e) => {
            const target = e.target;

            // Search
            if (e.target.closest('#asset-search-btn')) {
                const searchValue = document.getElementById('asset-search')?.value || '';
                loadAssets({ search: searchValue });
            }

            // Refresh
            if (e.target.closest('#refresh-asset-btn')) {
                loadAssets();
                loadStatistics();
            }
            // Create new
            if (e.target.closest('.asset-create-btn')) {
                e.preventDefault();
                e.stopPropagation();
                openCreateModal();
            }
            // View details
            if (e.target.closest('.asset-view-btn')) {
                e.preventDefault();
                e.stopPropagation();
                const id = parseInt(e.target.closest('.asset-view-btn').dataset.id);
                viewAsset(id);
            }
            // Tabs
            if (e.target.closest('.asset-tab-btn')) {
                switchAssetTab(e.target.closest('.asset-tab-btn').dataset.assetTab);
            }
        });

        // Search input Enter key
        document.addEventListener('keydown', (e) => {
            if (e.target.id === 'asset-search' && e.key === 'Enter') {
                e.preventDefault();
                const searchValue = e.target.value || '';
                loadAssets({ search: searchValue });
            }
        });

        // Modal close
        document.addEventListener('click', (e) => {
            if (e.target.id === 'asset-modal') {
                closeModal();
            }
            if (e.target.closest('#close-asset-modal')) {
                closeModal();
            }
        });

        // Form submit
        document.addEventListener('submit', (e) => {
            if (e.target.id === 'asset-form') {
                e.preventDefault();
                submitAssetForm();
            }
        });

        // Delegated events within modal
        document.addEventListener('click', (e) => {
            const modal = document.getElementById('asset-modal');
            if (!modal || modal.classList.contains('hidden')) return;

            // Cancel button (only in create mode)
            if (e.target.closest('#cancel-asset-btn')) {
                e.preventDefault();
                console.log('Cancel clicked');
                closeModal();
            }

            // Form tabs
            if (e.target.closest('.form-tab')) {
                e.preventDefault();
                console.log('Form tab clicked:', e.target.closest('.form-tab').dataset.tab);
                switchFormTab(e.target.closest('.form-tab').dataset.tab);
            }

            // Form navigation
            if (e.target.closest('.form-next-btn')) {
                e.preventDefault();
                console.log('Next clicked');
                goToNextTab();
            }
            if (e.target.closest('.form-prev-btn')) {
                e.preventDefault();
                console.log('Prev clicked');
                goToPrevTab();
            }

            // Detail actions
            if (e.target.closest('.asset-edit-btn')) {
                e.preventDefault();
                console.log('Edit clicked');
                renderEditForm(currentAsset);
            }
            if (e.target.closest('.asset-delete-btn')) {
                e.preventDefault();
                console.log('Delete clicked');
                confirmDeleteAsset();
            }
            if (e.target.closest('.asset-cancel-edit-btn')) {
                e.preventDefault();
                console.log('Cancel edit clicked');
                renderAssetDetail(currentAsset);
            }
        });

        // Real-time validation
        document.addEventListener('blur', (e) => {
            if (e.target.matches('#asset-form input, #asset-form select, #asset-form textarea')) {
                validateField(e.target);
            }
        });

        document.addEventListener('input', (e) => {
            if (e.target.matches('#asset-form input, #asset-form select, #asset-form textarea')) {
                clearFieldError(e.target);
            }
        });
    }

    // ==================== Validation ====================

    function validateField(field) {
        const id = field.id;
        const value = field.value.trim();
        const fieldKey = id.replace('asset-', '');
        const rules = VALIDATION[fieldKey];
        if (!rules) return true;

        if (rules.required && !value) {
            showFieldError(field, `${rules.label} is required`);
            return false;
        }
        if (rules.maxLength && value.length > rules.maxLength) {
            showFieldError(field, `Max ${rules.maxLength} characters`);
            return false;
        }
        clearFieldError(field);
        return true;
    }

    function validateAllFields() {
        let isValid = true;
        let firstError = null;
        const requiredFields = ['asset-number', 'asset-name', 'asset-type'];
        
        requiredFields.forEach(id => {
            const field = document.getElementById(id);
            if (field && !field.value.trim()) {
                showFieldError(field, 'This field is required');
                if (!firstError) firstError = field;
                isValid = false;
            }
        });

        const seatsTotal = parseInt(document.getElementById('asset-license-seats-total')?.value) || 0;
        const seatsUsed = parseInt(document.getElementById('asset-license-seats-used')?.value) || 0;
        if (seatsTotal > 0 && seatsUsed > seatsTotal) {
            const field = document.getElementById('asset-license-seats-used');
            showFieldError(field, 'Used seats cannot exceed total');
            if (!firstError) firstError = field;
            isValid = false;
        }

        if (firstError) {
            firstError.focus();
            firstError.scrollIntoView({ behavior: 'smooth', block: 'center' });
        }
        return isValid;
    }

    function showFieldError(field, message) {
        field.classList.add('error');
        let errorEl = field.parentElement.querySelector('.field-error');
        if (!errorEl) {
            errorEl = document.createElement('span');
            errorEl.className = 'field-error';
            field.parentElement.appendChild(errorEl);
        }
        errorEl.textContent = message;
    }

    function clearFieldError(field) {
        field.classList.remove('error');
        const errorEl = field.parentElement.querySelector('.field-error');
        if (errorEl) errorEl.remove();
    }

    // ==================== API Calls ====================

    async function loadStatistics() {
        try {
            const response = await fetch(`${API_BASE}/stats`, { headers: window.Auth.getAuthHeaders() });
            if (!response.ok) throw new Error('Failed to load statistics');
            stats = await response.json();
            renderStatistics(stats);
        } catch (error) {
            console.error('Error loading statistics:', error);
        }
    }

    async function loadAssets(params = {}) {
        try {
            let url = API_BASE;
            const queryParams = new URLSearchParams();
            if (params.type) queryParams.append('type', params.type);
            if (params.health) queryParams.append('health', params.health);
            if (params.search) queryParams.append('search', params.search);
            if (params.page) queryParams.append('page', params.page);
            queryParams.append('size', '24');
            if (queryParams.toString()) url += '?' + queryParams.toString();

            const response = await fetch(url, { headers: window.Auth.getAuthHeaders() });
            if (!response.ok) throw new Error('Failed to load assets');
            const data = await response.json();
            assets = data.content || [];
            renderAssetsList(assets);
        } catch (error) {
            console.error('Error loading assets:', error);
            Toast.show('Lỗi khi tải danh sách Assets', 'error');
        }
    }

    async function loadAssetById(id) {
        const response = await fetch(`${API_BASE}/${id}`, { headers: window.Auth.getAuthHeaders() });
        if (!response.ok) throw new Error('Failed to load asset');
        return await response.json();
    }

    async function submitAsset(data) {
        const response = await fetch(API_BASE, {
            method: 'POST',
            headers: window.Auth.getAuthHeaders(),
            body: JSON.stringify(data)
        });
        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.message || 'Failed to create asset');
        }
        return await response.json();
    }

    async function updateAsset(id, data) {
        const response = await fetch(`${API_BASE}/${id}`, {
            method: 'PUT',
            headers: window.Auth.getAuthHeaders(),
            body: JSON.stringify(data)
        });
        if (!response.ok) {
            const contentType = response.headers.get('content-type');
            if (contentType && contentType.includes('application/json')) {
                const error = await response.json();
                throw new Error(error.message || 'Failed to update asset');
            }
            throw new Error('Failed to update asset');
        }
        return await response.json();
    }

    async function deleteAsset(id) {
        const response = await fetch(`${API_BASE}/${id}`, {
            method: 'DELETE',
            headers: window.Auth.getAuthHeaders()
        });
        if (!response.ok) {
            // Try to parse error message if available
            const contentType = response.headers.get('content-type');
            if (contentType && contentType.includes('application/json')) {
                const error = await response.json();
                throw new Error(error.message || 'Failed to delete asset');
            }
            throw new Error('Failed to delete asset');
        }
        // DELETE returns 204 No Content, no body to parse
        return { success: true };
    }

    async function updateAssetHealth(id, healthStatus, notes) {
        const response = await fetch(`${API_BASE}/${id}/health`, {
            method: 'PATCH',
            headers: window.Auth.getAuthHeaders(),
            body: JSON.stringify({ healthStatus, healthNotes: notes })
        });
        if (!response.ok) throw new Error('Failed to update health status');
        return await response.json();
    }

    // ==================== Rendering ====================

    function renderStatistics(stats) {
        const container = document.getElementById('asset-stats');
        if (!container || !stats) return;

        container.innerHTML = `
            <div class="stat-card stat-total">
                <span class="stat-value">${stats.totalAssets}</span>
                <span class="stat-label">Total Assets</span>
            </div>
            <div class="stat-card stat-healthy">
                <span class="stat-value">${stats.healthyAssets}</span>
                <span class="stat-label">Healthy</span>
            </div>
            <div class="stat-card stat-warning">
                <span class="stat-value">${stats.warningAssets + stats.criticalAssets}</span>
                <span class="stat-label">Needs Attention</span>
            </div>
            <div class="stat-card stat-warranty">
                <span class="stat-value">${stats.expiringWarranties}</span>
                <span class="stat-label">Warranty Expiring</span>
            </div>
        `;
    }

    function renderAssetsList(assetsList) {
        const container = document.getElementById('asset-list');
        if (!container) return;

        if (!assetsList || assetsList.length === 0) {
            container.innerHTML = `
                <div class="asset-empty-state">
                    <div class="asset-empty-icon">🖥️</div>
                    <h4>No assets found</h4>
                    <p>Start by adding your first IT asset</p>
                </div>
            `;
            return;
        }

        container.innerHTML = assetsList.map(asset => {
            const healthConfig = getHealthConfig(asset.healthStatus);
            const typeIcon = getTypeIcon(asset.assetType);
            
            return `
                <div class="asset-card" data-id="${asset.id}">
                    <div class="asset-card-header">
                        <div class="asset-type-badge">
                            <span class="type-icon">${typeIcon}</span>
                            <span class="type-label">${asset.assetTypeLabel || asset.assetType}</span>
                        </div>
                        <div class="health-indicator" style="--health-color: ${healthConfig.color}">
                            <span class="health-dot"></span>
                            <span class="health-label">${healthConfig.label}</span>
                        </div>
                    </div>
                    
                    <h4 class="asset-name">${escapeHtml(asset.name)}</h4>
                    
                    <div class="asset-meta">
                        <span class="asset-id">${asset.assetNumber}</span>
                        ${asset.location ? `<span class="asset-location">${escapeHtml(asset.location)}</span>` : ''}
                    </div>
                    
                    ${asset.warrantyExpiringSoon || asset.warrantyExpired ? `
                        <div class="warranty-alert ${asset.warrantyExpired ? 'expired' : 'expiring'}">
                            ${asset.warrantyExpired ? '⚠️ Warranty Expired' : '📅 Warranty Expiring Soon'}
                        </div>
                    ` : ''}
                    
                    <div class="asset-footer">
                        <span class="asset-status status-${asset.status?.toLowerCase()}">${asset.statusLabel || asset.status}</span>
                        <button class="asset-view-btn" data-id="${asset.id}" onclick="window.AssetManagement.viewAsset(${asset.id})">Details</button>
                    </div>
                </div>
            `;
        }).join('');
    }

    function getHealthConfig(health) {
        const configs = {
            'HEALTHY': { label: 'Healthy', color: '#10B981' },
            'WARNING': { label: 'Warning', color: '#F59E0B' },
            'CRITICAL': { label: 'Critical', color: '#F43F5E' },
            'UNKNOWN': { label: 'Unknown', color: '#6B7280' }
        };
        return configs[health] || configs['UNKNOWN'];
    }

    function getTypeIcon(type) {
        const icons = {
            'SERVER': '🖥️', 'WORKSTATION': '🖥️', 'LAPTOP': '💻',
            'NETWORK_DEVICE': '🌐', 'PRINTER': '🖨️', 'MONITOR': '🖵',
            'MOBILE_DEVICE': '📱', 'SOFTWARE_LICENSE': '📜',
            'DATABASE': '🗄️', 'STORAGE': '💾', 'CLOUD_RESOURCE': '☁️', 'OTHER': '📦'
        };
        return icons[type] || '📦';
    }

    function formatLabel(value) {
        return value.replace(/_/g, ' ').toLowerCase()
            .replace(/\b\w/g, l => l.toUpperCase());
    }

    function switchAssetTab(tab) {
        document.querySelectorAll('.asset-tab-btn').forEach(btn => {
            btn.classList.toggle('active', btn.dataset.assetTab === tab);
        });
        document.getElementById('asset-list-section')?.classList.toggle('hidden', tab !== 'assets');
        document.getElementById('asset-alerts-section')?.classList.toggle('hidden', tab !== 'alerts');
    }

    // ==================== Form Tabs ====================
    
    function switchFormTab(tab) {
        activeTab = tab;
        document.querySelectorAll('.form-tab').forEach(t => {
            t.classList.toggle('active', t.dataset.tab === tab);
        });
        document.querySelectorAll('.form-tab-content').forEach(c => {
            c.classList.toggle('active', c.dataset.tab === tab);
        });
        updateTabNavigation();
    }

    function goToNextTab() {
        const tabs = ['basic', 'location', 'network', 'financial', 'software', 'vendor'];
        const currentIndex = tabs.indexOf(activeTab);
        if (currentIndex < tabs.length - 1) {
            switchFormTab(tabs[currentIndex + 1]);
        }
    }

    function goToPrevTab() {
        const tabs = ['basic', 'location', 'network', 'financial', 'software', 'vendor'];
        const currentIndex = tabs.indexOf(activeTab);
        if (currentIndex > 0) {
            switchFormTab(tabs[currentIndex - 1]);
        }
    }

    function updateTabNavigation() {
        const tabs = ['basic', 'location', 'network', 'financial', 'software', 'vendor'];
        const currentIndex = tabs.indexOf(activeTab);
        
        const prevBtn = document.querySelector('.form-prev-btn');
        const nextBtn = document.querySelector('.form-next-btn');
        
        if (prevBtn) prevBtn.classList.toggle('hidden', currentIndex === 0);
        if (nextBtn) nextBtn.classList.toggle('hidden', currentIndex === tabs.length - 1);
    }

    // ==================== Modal ====================

    function openCreateModal() {
        console.log('openCreateModal called');
        currentAsset = null;
        activeTab = 'basic';
        document.getElementById('asset-modal-title').textContent = 'New Asset';
        document.getElementById('asset-modal').classList.remove('detail-mode');
        renderCreateForm();
        document.getElementById('asset-modal').classList.remove('hidden');
        document.body.classList.add('modal-open');
        generateAssetNumber();
    }

    function viewAsset(id) {
        console.log('viewAsset called, id:', id);
        loadAssetById(id).then(asset => {
            console.log('Asset loaded:', asset);
            currentAsset = asset;
            document.getElementById('asset-modal-title').textContent = asset.assetNumber;
            document.getElementById('asset-modal').classList.add('detail-mode');
            renderAssetDetail(asset);
            document.getElementById('asset-modal').classList.remove('hidden');
            document.body.classList.add('modal-open');
        }).catch(err => {
            console.error('Error loading asset:', err);
            Toast.show('Error loading asset', 'error');
        });
    }

    function generateAssetNumber() {
        const prefix = 'AST';
        const timestamp = Date.now().toString().slice(-8);
        const random = Math.floor(Math.random() * 100).toString().padStart(2, '0');
        const input = document.getElementById('asset-number');
        if (input) input.value = `${prefix}-${timestamp}-${random}`;
    }

    function closeModal() {
        document.getElementById('asset-modal').classList.add('hidden');
        document.body.classList.remove('modal-open');
        currentAsset = null;
        activeTab = 'basic';
    }

    // ==================== Delete ====================

    function confirmDeleteAsset() {
        if (!currentAsset) return;
        
        const form = document.getElementById('asset-form');
        if (!form) return;
        
        // Show confirmation in form area
        form.innerHTML = `
            <div class="delete-confirm">
                <div class="delete-icon">⚠️</div>
                <h3>Delete Asset?</h3>
                <p>Are you sure you want to delete <strong>"${escapeHtml(currentAsset.name)}"</strong>?</p>
                <p class="delete-warning">This action cannot be undone.</p>
                <div class="delete-actions">
                    <button type="button" class="btn-ghost" id="delete-cancel-btn">Cancel</button>
                    <button type="button" class="btn-danger" id="delete-confirm-btn">Delete</button>
                </div>
            </div>
        `;

        // Add event listeners
        document.getElementById('delete-cancel-btn').addEventListener('click', () => {
            renderAssetDetail(currentAsset);
        });

        document.getElementById('delete-confirm-btn').addEventListener('click', async () => {
            try {
                await deleteAsset(currentAsset.id);
                Toast.show('Asset deleted successfully', 'success');
                closeModal();
                loadAssets();
                loadStatistics();
            } catch (error) {
                Toast.show(error.message || 'Failed to delete asset', 'error');
            }
        });
    }

    // ==================== Render Detail ====================

    function renderAssetDetail(asset) {
        console.log('renderAssetDetail called, asset:', asset);
        const container = document.getElementById('asset-form');
        if (!container) {
            console.error('asset-form container not found!');
            return;
        }

        const healthConfig = getHealthConfig(asset.healthStatus);
        const typeIcon = getTypeIcon(asset.assetType);

        container.innerHTML = `
            <div class="detail-view">
            <div class="detail-header">
                <div class="detail-icon" style="--icon-color: ${healthConfig.color}">
                    ${typeIcon}
                </div>
                <div class="detail-info">
                    <h3>${escapeHtml(asset.name)}</h3>
                    <p>${escapeHtml(asset.description || 'No description')}</p>
                    <div class="detail-badges">
                        <span class="badge health" style="--badge-color: ${healthConfig.color}">${healthConfig.label}</span>
                        <span class="badge status">${asset.statusLabel || asset.status}</span>
                    </div>
                </div>
            </div>

            <div class="detail-actions">
                <button class="btn-secondary asset-edit-btn">
                    <span>✏️</span> Edit
                </button>
                <button class="btn-danger asset-delete-btn">
                    <span>🗑️</span> Delete
                </button>
            </div>

            <div class="detail-content">
                <div class="detail-section">
                    <h4>Basic Information</h4>
                    <div class="detail-grid">
                        <div class="detail-item"><label>Asset Number</label><span class="mono">${asset.assetNumber || 'N/A'}</span></div>
                        <div class="detail-item"><label>Type</label><span>${asset.assetTypeLabel || asset.assetType || 'N/A'}</span></div>
                        <div class="detail-item"><label>Category</label><span>${asset.category || 'N/A'}</span></div>
                        <div class="detail-item"><label>Manufacturer</label><span>${asset.manufacturer || 'N/A'}</span></div>
                        <div class="detail-item"><label>Model</label><span>${asset.model || 'N/A'}</span></div>
                        <div class="detail-item"><label>Serial Number</label><span class="mono">${asset.serialNumber || 'N/A'}</span></div>
                        <div class="detail-item"><label>Part Number</label><span>${asset.partNumber || 'N/A'}</span></div>
                    </div>
                </div>

                <div class="detail-section">
                    <h4>Location</h4>
                    <div class="detail-grid">
                        <div class="detail-item"><label>Location</label><span>${asset.location || 'N/A'}</span></div>
                        <div class="detail-item"><label>Building</label><span>${asset.building || 'N/A'}</span></div>
                        <div class="detail-item"><label>Floor</label><span>${asset.floor || 'N/A'}</span></div>
                        <div class="detail-item"><label>Room</label><span>${asset.room || 'N/A'}</span></div>
                        <div class="detail-item"><label>Rack Position</label><span>${asset.rackPosition || 'N/A'}</span></div>
                    </div>
                </div>

                <div class="detail-section">
                    <h4>Assignment</h4>
                    <div class="detail-grid">
                        <div class="detail-item"><label>Assigned To</label><span>${asset.assignedTo || 'Unassigned'}</span></div>
                        <div class="detail-item"><label>Department</label><span>${asset.assignedDepartment || 'N/A'}</span></div>
                        <div class="detail-item"><label>Assigned Location</label><span>${asset.assignedLocation || 'N/A'}</span></div>
                    </div>
                </div>

                <div class="detail-section">
                    <h4>Network</h4>
                    <div class="detail-grid">
                        <div class="detail-item"><label>IP Address</label><span class="mono">${asset.ipAddress || 'N/A'}</span></div>
                        <div class="detail-item"><label>MAC Address</label><span class="mono">${asset.macAddress || 'N/A'}</span></div>
                        <div class="detail-item"><label>Hostname</label><span class="mono">${asset.hostname || 'N/A'}</span></div>
                        <div class="detail-item"><label>Network Segment</label><span>${asset.networkSegment || 'N/A'}</span></div>
                        <div class="detail-item"><label>VLAN</label><span>${asset.vlan || 'N/A'}</span></div>
                    </div>
                </div>

                <div class="detail-section">
                    <h4>Financial</h4>
                    <div class="detail-grid">
                        <div class="detail-item"><label>Purchase Date</label><span>${asset.purchaseDate || 'N/A'}</span></div>
                        <div class="detail-item"><label>Purchase Cost</label><span>${asset.purchaseCost ? formatCurrency(asset.purchaseCost) : 'N/A'}</span></div>
                        <div class="detail-item"><label>Current Value</label><span>${asset.currentValue ? formatCurrency(asset.currentValue) : 'N/A'}</span></div>
                        <div class="detail-item"><label>Depreciation</label><span>${asset.depreciationRate ? asset.depreciationRate + '%' : 'N/A'}</span></div>
                        <div class="detail-item"><label>Warranty Expiry</label><span class="${asset.warrantyExpired ? 'text-danger' : asset.warrantyExpiringSoon ? 'text-warning' : ''}">${asset.warrantyExpiryDate || 'N/A'}</span></div>
                        <div class="detail-item"><label>Lease Expiry</label><span>${asset.leaseExpiryDate || 'N/A'}</span></div>
                    </div>
                </div>

                <div class="detail-section">
                    <h4>Software & License</h4>
                    <div class="detail-grid">
                        <div class="detail-item"><label>Software</label><span>${asset.softwareName || 'N/A'}</span></div>
                        <div class="detail-item"><label>Version</label><span>${asset.softwareVersion || 'N/A'}</span></div>
                        <div class="detail-item"><label>License Type</label><span>${asset.licenseType || 'N/A'}</span></div>
                        <div class="detail-item"><label>License Key</label><span class="mono">${asset.licenseKey || 'N/A'}</span></div>
                        <div class="detail-item"><label>License Seats</label><span>${asset.licenseSeatsUsed || 0} / ${asset.licenseSeatsTotal || 0}</span></div>
                    </div>
                </div>

                <div class="detail-section">
                    <h4>Cloud Resources</h4>
                    <div class="detail-grid">
                        <div class="detail-item"><label>Provider</label><span>${asset.cloudProvider || 'N/A'}</span></div>
                        <div class="detail-item"><label>Region</label><span>${asset.cloudRegion || 'N/A'}</span></div>
                        <div class="detail-item"><label>Resource ID</label><span class="mono">${asset.cloudResourceId || 'N/A'}</span></div>
                        <div class="detail-item"><label>Resource Type</label><span>${asset.resourceType || 'N/A'}</span></div>
                    </div>
                </div>

                <div class="detail-section">
                    <h4>Maintenance</h4>
                    <div class="detail-grid">
                        <div class="detail-item"><label>Last Maintenance</label><span>${asset.lastMaintenanceDate || 'N/A'}</span></div>
                        <div class="detail-item"><label>Next Maintenance</label><span>${asset.nextMaintenanceDate || 'N/A'}</span></div>
                    </div>
                    ${asset.healthNotes ? `
                    <div class="health-notes-box">
                        <label>Health Notes</label>
                        <p>${escapeHtml(asset.healthNotes)}</p>
                    </div>
                    ` : ''}
                </div>

                <div class="detail-section">
                    <h4>Vendor</h4>
                    <div class="detail-grid">
                        <div class="detail-item"><label>Vendor Name</label><span>${asset.vendorName || 'N/A'}</span></div>
                        <div class="detail-item"><label>Contact</label><span>${asset.vendorContact || 'N/A'}</span></div>
                        <div class="detail-item"><label>Contract</label><span>${asset.vendorContractNumber || 'N/A'}</span></div>
                    </div>
                </div>

                <div class="detail-section">
                    <h4>Other</h4>
                    <div class="detail-grid">
                        <div class="detail-item full"><label>Tags</label><span>${asset.tags || 'N/A'}</span></div>
                        <div class="detail-item full"><label>Notes</label><span>${asset.notes || 'N/A'}</span></div>
                        ${asset.photoUrl ? `<div class="detail-item full"><label>Photo</label><a href="${asset.photoUrl}" target="_blank">View Photo</a></div>` : ''}
                    </div>
                </div>

                ${asset.specifications && Object.keys(asset.specifications).length > 0 ? `
                <div class="detail-section">
                    <h4>Custom Specifications</h4>
                    <div class="specs-json">
                        <pre>${JSON.stringify(asset.specifications, null, 2)}</pre>
                    </div>
                </div>
                ` : ''}

                <div class="detail-section metadata">
                    <div class="detail-grid">
                        <div class="detail-item"><label>Created</label><span>${asset.createdAt ? new Date(asset.createdAt).toLocaleString() : 'N/A'}</span></div>
                        <div class="detail-item"><label>Created By</label><span>${asset.createdBy || 'N/A'}</span></div>
                        <div class="detail-item"><label>Updated</label><span>${asset.updatedAt ? new Date(asset.updatedAt).toLocaleString() : 'N/A'}</span></div>
                        <div class="detail-item"><label>Updated By</label><span>${asset.updatedBy || 'N/A'}</span></div>
                    </div>
                </div>
            </div>
        `;
    }

    // ==================== Render Edit Form ====================

    function renderEditForm(asset) {
        const container = document.getElementById('asset-form');
        if (!container) return;

        container.innerHTML = `
            <div class="form-tabs">
                <button class="form-tab active" data-tab="basic"><span class="tab-number">1</span><span class="tab-label">Basic</span></button>
                <button class="form-tab" data-tab="location"><span class="tab-number">2</span><span class="tab-label">Location</span></button>
                <button class="form-tab" data-tab="network"><span class="tab-number">3</span><span class="tab-label">Network</span></button>
                <button class="form-tab" data-tab="financial"><span class="tab-number">4</span><span class="tab-label">Financial</span></button>
                <button class="form-tab" data-tab="software"><span class="tab-number">5</span><span class="tab-label">Software</span></button>
                <button class="form-tab" data-tab="vendor"><span class="tab-number">6</span><span class="tab-label">Vendor</span></button>
            </div>

            <div class="form-tab-content active" data-tab="basic">
                <div class="form-group">
                    <label class="form-field"><span>Asset Number</span><input type="text" id="asset-number" value="${escapeHtml(asset.assetNumber || '')}" maxlength="50" /></label>
                    <label class="form-field"><span>Asset Type</span><select id="asset-type" required>${ASSET_TYPES.map(t => `<option value="${t}" ${asset.assetType === t ? 'selected' : ''}>${t.replace(/_/g, ' ')}</option>`).join('')}</select></label>
                    <label class="form-field full"><span>Asset Name</span><input type="text" id="asset-name" value="${escapeHtml(asset.name || '')}" maxlength="200" required /></label>
                    <label class="form-field"><span>Category</span><input type="text" id="asset-category" value="${escapeHtml(asset.category || '')}" maxlength="100" /></label>
                    <label class="form-field"><span>Status</span><select id="asset-status">${ASSET_STATUSES.map(s => `<option value="${s}" ${asset.status === s ? 'selected' : ''}>${s.replace(/_/g, ' ')}</option>`).join('')}</select></label>
                    <label class="form-field"><span>Health</span><select id="asset-health-status">${HEALTH_STATUSES.map(h => `<option value="${h}" ${asset.healthStatus === h ? 'selected' : ''}>${h}</option>`).join('')}</select></label>
                    <label class="form-field"><span>Manufacturer</span><input type="text" id="asset-manufacturer" value="${escapeHtml(asset.manufacturer || '')}" maxlength="100" /></label>
                    <label class="form-field"><span>Model</span><input type="text" id="asset-model" value="${escapeHtml(asset.model || '')}" maxlength="100" /></label>
                    <label class="form-field"><span>Serial Number</span><input type="text" id="asset-serial" value="${escapeHtml(asset.serialNumber || '')}" maxlength="100" /></label>
                    <label class="form-field"><span>Part Number</span><input type="text" id="asset-part-number" value="${escapeHtml(asset.partNumber || '')}" maxlength="100" /></label>
                    <label class="form-field full"><span>Description</span><textarea id="asset-description" rows="2">${escapeHtml(asset.description || '')}</textarea></label>
                </div>
            </div>

            <div class="form-tab-content" data-tab="location">
                <div class="form-group">
                    <label class="form-field full"><span>Location</span><input type="text" id="asset-location" value="${escapeHtml(asset.location || '')}" maxlength="200" /></label>
                    <label class="form-field"><span>Building</span><input type="text" id="asset-building" value="${escapeHtml(asset.building || '')}" maxlength="100" /></label>
                    <label class="form-field"><span>Floor</span><input type="text" id="asset-floor" value="${escapeHtml(asset.floor || '')}" maxlength="50" /></label>
                    <label class="form-field"><span>Room</span><input type="text" id="asset-room" value="${escapeHtml(asset.room || '')}" maxlength="100" /></label>
                    <label class="form-field"><span>Rack Position</span><input type="text" id="asset-rack-position" value="${escapeHtml(asset.rackPosition || '')}" maxlength="50" /></label>
                    <label class="form-field full"><span>Assigned To</span><input type="text" id="asset-assigned-to" value="${escapeHtml(asset.assignedTo || '')}" maxlength="100" /></label>
                    <label class="form-field"><span>Department</span><input type="text" id="asset-assigned-department" value="${escapeHtml(asset.assignedDepartment || '')}" maxlength="100" /></label>
                    <label class="form-field"><span>Assigned Location</span><input type="text" id="asset-assigned-location" value="${escapeHtml(asset.assignedLocation || '')}" maxlength="200" /></label>
                    <label class="form-field"><span>Parent Asset</span><select id="asset-parent-asset"><option value="">-- No Parent --</option>${assets.filter(a => a.id !== asset.id).map(a => `<option value="${a.id}" ${asset.parentAssetId === a.id ? 'selected' : ''}>${a.assetNumber}</option>`).join('')}</select></label>
                </div>
            </div>

            <div class="form-tab-content" data-tab="network">
                <div class="form-group">
                    <label class="form-field"><span>IP Address</span><input type="text" id="asset-ip-address" value="${escapeHtml(asset.ipAddress || '')}" placeholder="192.168.1.10" /></label>
                    <label class="form-field"><span>MAC Address</span><input type="text" id="asset-mac-address" value="${escapeHtml(asset.macAddress || '')}" placeholder="AA:BB:CC:DD:EE:FF" /></label>
                    <label class="form-field"><span>Hostname</span><input type="text" id="asset-hostname" value="${escapeHtml(asset.hostname || '')}" maxlength="100" /></label>
                    <label class="form-field"><span>Network Segment</span><input type="text" id="asset-network-segment" value="${escapeHtml(asset.networkSegment || '')}" maxlength="50" /></label>
                    <label class="form-field"><span>VLAN</span><input type="text" id="asset-vlan" value="${escapeHtml(asset.vlan || '')}" maxlength="20" /></label>
                </div>
            </div>

            <div class="form-tab-content" data-tab="financial">
                <div class="form-group">
                    <label class="form-field"><span>Purchase Date</span><input type="date" id="asset-purchase-date" value="${asset.purchaseDate || ''}" /></label>
                    <label class="form-field"><span>Purchase Cost</span><input type="number" id="asset-purchase-cost" value="${asset.purchaseCost || ''}" min="0" step="0.01" /></label>
                    <label class="form-field"><span>Current Value</span><input type="number" id="asset-current-value" value="${asset.currentValue || ''}" min="0" step="0.01" /></label>
                    <label class="form-field"><span>Depreciation Rate (%)</span><input type="number" id="asset-depreciation-rate" value="${asset.depreciationRate || ''}" min="0" max="100" step="0.01" /></label>
                    <label class="form-field"><span>Warranty Expiry</span><input type="date" id="asset-warranty-expiry" value="${asset.warrantyExpiryDate || ''}" /></label>
                    <label class="form-field"><span>Lease Expiry</span><input type="date" id="asset-lease-expiry" value="${asset.leaseExpiryDate || ''}" /></label>
                    <label class="form-field"><span>Last Maintenance</span><input type="date" id="asset-last-maintenance" value="${asset.lastMaintenanceDate || ''}" /></label>
                    <label class="form-field"><span>Next Maintenance</span><input type="date" id="asset-next-maintenance" value="${asset.nextMaintenanceDate || ''}" /></label>
                    <label class="form-field full"><span>Health Notes</span><textarea id="asset-health-notes" rows="2">${escapeHtml(asset.healthNotes || '')}</textarea></label>
                </div>
            </div>

            <div class="form-tab-content" data-tab="software">
                <div class="form-group">
                    <label class="form-field"><span>Software Name</span><input type="text" id="asset-software-name" value="${escapeHtml(asset.softwareName || '')}" maxlength="200" /></label>
                    <label class="form-field"><span>Version</span><input type="text" id="asset-software-version" value="${escapeHtml(asset.softwareVersion || '')}" maxlength="50" /></label>
                    <label class="form-field"><span>License Type</span><select id="asset-license-type"><option value="">Select type</option>${LICENSE_TYPES.map(t => `<option value="${t}" ${asset.licenseType === t ? 'selected' : ''}>${t.replace(/_/g, ' ')}</option>`).join('')}</select></label>
                    <label class="form-field full"><span>License Key</span><input type="text" id="asset-license-key" value="${escapeHtml(asset.licenseKey || '')}" maxlength="200" /></label>
                    <label class="form-field"><span>Total Seats</span><input type="number" id="asset-license-seats-total" value="${asset.licenseSeatsTotal || 0}" min="0" /></label>
                    <label class="form-field"><span>Used Seats</span><input type="number" id="asset-license-seats-used" value="${asset.licenseSeatsUsed || 0}" min="0" /></label>
                    <div class="field-divider"></div>
                    <label class="form-field"><span>Cloud Provider</span><select id="asset-cloud-provider"><option value="">Select provider</option>${CLOUD_PROVIDERS.map(p => `<option value="${p}" ${asset.cloudProvider === p ? 'selected' : ''}>${p}</option>`).join('')}</select></label>
                    <label class="form-field"><span>Region</span><input type="text" id="asset-cloud-region" value="${escapeHtml(asset.cloudRegion || '')}" maxlength="50" /></label>
                    <label class="form-field full"><span>Resource ID</span><input type="text" id="asset-cloud-resource-id" value="${escapeHtml(asset.cloudResourceId || '')}" maxlength="200" /></label>
                    <label class="form-field"><span>Resource Type</span><input type="text" id="asset-resource-type" value="${escapeHtml(asset.resourceType || '')}" maxlength="100" /></label>
                </div>
            </div>

            <div class="form-tab-content" data-tab="vendor">
                <div class="form-group">
                    <label class="form-field full"><span>Vendor Name</span><input type="text" id="asset-vendor-name" value="${escapeHtml(asset.vendorName || '')}" maxlength="200" /></label>
                    <label class="form-field"><span>Contact</span><input type="text" id="asset-vendor-contact" value="${escapeHtml(asset.vendorContact || '')}" maxlength="100" /></label>
                    <label class="form-field"><span>Contract Number</span><input type="text" id="asset-vendor-contract" value="${escapeHtml(asset.vendorContractNumber || '')}" maxlength="100" /></label>
                    <div class="field-divider"></div>
                    <label class="form-field full"><span>Tags</span><input type="text" id="asset-tags" value="${escapeHtml(asset.tags || '')}" /></label>
                    <label class="form-field full"><span>Notes</span><textarea id="asset-notes" rows="3">${escapeHtml(asset.notes || '')}</textarea></label>
                    <label class="form-field full"><span>Photo URL</span><input type="url" id="asset-photo-url" value="${escapeHtml(asset.photoUrl || '')}" maxlength="500" /></label>
                </div>
            </div>

            <div class="form-actions">
                <button type="button" class="btn-ghost asset-cancel-edit-btn">Cancel</button>
                <div class="spacer"></div>
                <button type="submit" class="btn-primary">Save Changes</button>
            </div>
        `;
        
        activeTab = 'basic';
        updateTabNavigation();
    }

    // ==================== Render Create Form ====================

    function renderCreateForm() {
        console.log('renderCreateForm called');
        const form = document.getElementById('asset-form');
        if (!form) {
            console.error('asset-form container not found!');
            return;
        }

        form.innerHTML = `
            <div class="form-tabs">
                <button class="form-tab active" data-tab="basic"><span class="tab-number">1</span><span class="tab-label">Basic</span></button>
                <button class="form-tab" data-tab="location"><span class="tab-number">2</span><span class="tab-label">Location</span></button>
                <button class="form-tab" data-tab="network"><span class="tab-number">3</span><span class="tab-label">Network</span></button>
                <button class="form-tab" data-tab="financial"><span class="tab-number">4</span><span class="tab-label">Financial</span></button>
                <button class="form-tab" data-tab="software"><span class="tab-number">5</span><span class="tab-label">Software</span></button>
                <button class="form-tab" data-tab="vendor"><span class="tab-number">6</span><span class="tab-label">Vendor</span></button>
            </div>

            <div class="form-tab-content active" data-tab="basic">
                <div class="form-group">
                    <label class="form-field"><span>Asset Number</span><input type="text" id="asset-number" maxlength="50" readonly /></label>
                    <label class="form-field"><span>Asset Type</span><select id="asset-type" required><option value="">Select type</option>${ASSET_TYPES.map(t => `<option value="${t}">${t.replace(/_/g, ' ')}</option>`).join('')}</select></label>
                    <label class="form-field full"><span>Asset Name</span><input type="text" id="asset-name" maxlength="200" required placeholder="e.g., Dell PowerEdge R740 Server" /></label>
                    <label class="form-field"><span>Category</span><input type="text" id="asset-category" maxlength="100" placeholder="e.g., Server, Desktop" /></label>
                    <label class="form-field"><span>Status</span><select id="asset-status"><option value="ACTIVE" selected>Active</option>${ASSET_STATUSES.filter(s => s !== 'ACTIVE').map(s => `<option value="${s}">${s.replace(/_/g, ' ')}</option>`).join('')}</select></label>
                    <label class="form-field"><span>Health</span><select id="asset-health-status"><option value="HEALTHY" selected>Healthy</option>${HEALTH_STATUSES.filter(h => h !== 'HEALTHY').map(h => `<option value="${h}">${h}</option>`).join('')}</select></label>
                    <label class="form-field"><span>Manufacturer</span><input type="text" id="asset-manufacturer" maxlength="100" placeholder="e.g., Dell, HP, Cisco" /></label>
                    <label class="form-field"><span>Model</span><input type="text" id="asset-model" maxlength="100" placeholder="e.g., PowerEdge R740" /></label>
                    <label class="form-field"><span>Serial Number</span><input type="text" id="asset-serial" maxlength="100" placeholder="Manufacturer serial" /></label>
                    <label class="form-field"><span>Part Number</span><input type="text" id="asset-part-number" maxlength="100" placeholder="Product/part number" /></label>
                    <label class="form-field full"><span>Description</span><textarea id="asset-description" rows="2" placeholder="Additional details..."></textarea></label>
                </div>
            </div>

            <div class="form-tab-content" data-tab="location">
                <div class="form-group">
                    <label class="form-field full"><span>Location</span><input type="text" id="asset-location" maxlength="200" placeholder="e.g., Data Center A, Floor 3" /></label>
                    <label class="form-field"><span>Building</span><input type="text" id="asset-building" maxlength="100" /></label>
                    <label class="form-field"><span>Floor</span><input type="text" id="asset-floor" maxlength="50" /></label>
                    <label class="form-field"><span>Room</span><input type="text" id="asset-room" maxlength="100" /></label>
                    <label class="form-field"><span>Rack Position</span><input type="text" id="asset-rack-position" maxlength="50" /></label>
                    <label class="form-field full"><span>Assigned To</span><input type="text" id="asset-assigned-to" maxlength="100" /></label>
                    <label class="form-field"><span>Department</span><input type="text" id="asset-assigned-department" maxlength="100" /></label>
                    <label class="form-field"><span>Assigned Location</span><input type="text" id="asset-assigned-location" maxlength="200" /></label>
                    <label class="form-field"><span>Parent Asset</span><select id="asset-parent-asset"><option value="">-- No Parent --</option>${assets.slice(0, 50).map(a => `<option value="${a.id}">${a.assetNumber}</option>`).join('')}</select></label>
                </div>
            </div>

            <div class="form-tab-content" data-tab="network">
                <div class="form-group">
                    <label class="form-field"><span>IP Address</span><input type="text" id="asset-ip-address" placeholder="192.168.1.10" /></label>
                    <label class="form-field"><span>MAC Address</span><input type="text" id="asset-mac-address" placeholder="AA:BB:CC:DD:EE:FF" /></label>
                    <label class="form-field"><span>Hostname</span><input type="text" id="asset-hostname" maxlength="100" /></label>
                    <label class="form-field"><span>Network Segment</span><input type="text" id="asset-network-segment" maxlength="50" /></label>
                    <label class="form-field"><span>VLAN</span><input type="text" id="asset-vlan" maxlength="20" /></label>
                </div>
            </div>

            <div class="form-tab-content" data-tab="financial">
                <div class="form-group">
                    <label class="form-field"><span>Purchase Date</span><input type="date" id="asset-purchase-date" /></label>
                    <label class="form-field"><span>Purchase Cost</span><input type="number" id="asset-purchase-cost" min="0" step="0.01" placeholder="0.00" /></label>
                    <label class="form-field"><span>Current Value</span><input type="number" id="asset-current-value" min="0" step="0.01" placeholder="0.00" /></label>
                    <label class="form-field"><span>Depreciation Rate (%)</span><input type="number" id="asset-depreciation-rate" min="0" max="100" step="0.01" /></label>
                    <label class="form-field"><span>Warranty Expiry</span><input type="date" id="asset-warranty-expiry" /></label>
                    <label class="form-field"><span>Lease Expiry</span><input type="date" id="asset-lease-expiry" /></label>
                    <label class="form-field"><span>Last Maintenance</span><input type="date" id="asset-last-maintenance" /></label>
                    <label class="form-field"><span>Next Maintenance</span><input type="date" id="asset-next-maintenance" /></label>
                    <label class="form-field full"><span>Health Notes</span><textarea id="asset-health-notes" rows="2"></textarea></label>
                </div>
            </div>

            <div class="form-tab-content" data-tab="software">
                <div class="form-group">
                    <label class="form-field"><span>Software Name</span><input type="text" id="asset-software-name" maxlength="200" /></label>
                    <label class="form-field"><span>Version</span><input type="text" id="asset-software-version" maxlength="50" /></label>
                    <label class="form-field"><span>License Type</span><select id="asset-license-type"><option value="">Select type</option>${LICENSE_TYPES.map(t => `<option value="${t}">${t.replace(/_/g, ' ')}</option>`).join('')}</select></label>
                    <label class="form-field full"><span>License Key</span><input type="text" id="asset-license-key" maxlength="200" /></label>
                    <label class="form-field"><span>Total Seats</span><input type="number" id="asset-license-seats-total" min="0" value="0" /></label>
                    <label class="form-field"><span>Used Seats</span><input type="number" id="asset-license-seats-used" min="0" value="0" /></label>
                    <div class="field-divider"></div>
                    <label class="form-field"><span>Cloud Provider</span><select id="asset-cloud-provider"><option value="">Select provider</option>${CLOUD_PROVIDERS.map(p => `<option value="${p}">${p}</option>`).join('')}</select></label>
                    <label class="form-field"><span>Region</span><input type="text" id="asset-cloud-region" maxlength="50" /></label>
                    <label class="form-field full"><span>Resource ID</span><input type="text" id="asset-cloud-resource-id" maxlength="200" /></label>
                    <label class="form-field"><span>Resource Type</span><input type="text" id="asset-resource-type" maxlength="100" /></label>
                </div>
            </div>

            <div class="form-tab-content" data-tab="vendor">
                <div class="form-group">
                    <label class="form-field full"><span>Vendor Name</span><input type="text" id="asset-vendor-name" maxlength="200" /></label>
                    <label class="form-field"><span>Contact</span><input type="text" id="asset-vendor-contact" maxlength="100" /></label>
                    <label class="form-field"><span>Contract Number</span><input type="text" id="asset-vendor-contract" maxlength="100" /></label>
                    <div class="field-divider"></div>
                    <label class="form-field full"><span>Tags</span><input type="text" id="asset-tags" placeholder="server, production (comma separated)" /></label>
                    <label class="form-field full"><span>Notes</span><textarea id="asset-notes" rows="3"></textarea></label>
                    <label class="form-field full"><span>Photo URL</span><input type="url" id="asset-photo-url" maxlength="500" /></label>
                </div>
            </div>

            <div class="form-actions">
                <button type="button" id="cancel-asset-btn" class="btn-ghost">Cancel</button>
                <div class="spacer"></div>
                <button type="submit" class="btn-primary" id="asset-submit-btn">Add Asset</button>
            </div>
        `;
        
        activeTab = 'basic';
        updateTabNavigation();
    }

    // ==================== Submit ====================

    async function submitAssetForm() {
        if (!validateAllFields()) {
            Toast.show('Please fill in required fields', 'error');
            return;
        }

        if (isSubmitting) return;
        isSubmitting = true;

        const submitBtn = document.getElementById('asset-submit-btn') || document.querySelector('.btn-primary[type="submit"]');
        const originalText = submitBtn?.textContent || 'Add Asset';
        if (submitBtn) {
            submitBtn.disabled = true;
            submitBtn.textContent = 'Adding...';
        }

        const data = buildAssetData();

        try {
            if (currentAsset) {
                await updateAsset(currentAsset.id, data);
                Toast.show('Asset updated successfully', 'success');
            } else {
                await submitAsset(data);
                Toast.show('Asset created successfully', 'success');
            }
            closeModal();
            loadAssets();
            loadStatistics();
        } catch (error) {
            Toast.show(error.message || 'Operation failed', 'error');
        } finally {
            if (submitBtn) {
                submitBtn.disabled = false;
                submitBtn.textContent = originalText;
            }
            isSubmitting = false;
        }
    }

    function buildAssetData() {
        const getVal = (id) => document.getElementById(id)?.value.trim() || '';
        const getNum = (id) => {
            const el = document.getElementById(id);
            return el && el.value ? parseFloat(el.value) : null;
        };
        const getDate = (id) => {
            const el = document.getElementById(id);
            return el && el.value ? el.value : null;
        };
        const getSelect = (id) => {
            const el = document.getElementById(id);
            return el && el.value ? el.value : null;
        };

        return {
            assetNumber: getVal('asset-number') || null,
            name: getVal('asset-name'),
            assetType: getSelect('asset-type'),
            status: getSelect('asset-status') || 'ACTIVE',
            healthStatus: getSelect('asset-health-status') || 'HEALTHY',
            description: getVal('asset-description') || null,
            category: getVal('asset-category') || null,
            manufacturer: getVal('asset-manufacturer') || null,
            model: getVal('asset-model') || null,
            serialNumber: getVal('asset-serial') || null,
            partNumber: getVal('asset-part-number') || null,
            location: getVal('asset-location') || null,
            building: getVal('asset-building') || null,
            floor: getVal('asset-floor') || null,
            room: getVal('asset-room') || null,
            rackPosition: getVal('asset-rack-position') || null,
            assignedTo: getVal('asset-assigned-to') || null,
            assignedDepartment: getVal('asset-assigned-department') || null,
            assignedLocation: getVal('asset-assigned-location') || null,
            parentAssetId: getSelect('asset-parent-asset') ? parseInt(getSelect('asset-parent-asset')) : null,
            purchaseDate: getDate('asset-purchase-date'),
            purchaseCost: getNum('asset-purchase-cost'),
            currentValue: getNum('asset-current-value'),
            depreciationRate: getNum('asset-depreciation-rate'),
            warrantyExpiryDate: getDate('asset-warranty-expiry'),
            leaseExpiryDate: getDate('asset-lease-expiry'),
            lastMaintenanceDate: getDate('asset-last-maintenance'),
            nextMaintenanceDate: getDate('asset-next-maintenance'),
            healthNotes: getVal('asset-health-notes') || null,
            ipAddress: getVal('asset-ip-address') || null,
            macAddress: getVal('asset-mac-address') || null,
            hostname: getVal('asset-hostname') || null,
            networkSegment: getVal('asset-network-segment') || null,
            vlan: getVal('asset-vlan') || null,
            softwareName: getVal('asset-software-name') || null,
            softwareVersion: getVal('asset-software-version') || null,
            licenseKey: getVal('asset-license-key') || null,
            licenseType: getVal('asset-license-type') || null,
            licenseSeatsTotal: getNum('asset-license-seats-total') || 0,
            licenseSeatsUsed: getNum('asset-license-seats-used') || 0,
            cloudProvider: getVal('asset-cloud-provider') || null,
            cloudRegion: getVal('asset-cloud-region') || null,
            cloudResourceId: getVal('asset-cloud-resource-id') || null,
            resourceType: getVal('asset-resource-type') || null,
            vendorName: getVal('asset-vendor-name') || null,
            vendorContact: getVal('asset-vendor-contact') || null,
            vendorContractNumber: getVal('asset-vendor-contract') || null,
            tags: getVal('asset-tags') || null,
            notes: getVal('asset-notes') || null,
            photoUrl: getVal('asset-photo-url') || null
        };
    }

    // ==================== Helpers ====================

    function escapeHtml(text) {
        if (!text) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    function formatCurrency(amount) {
        return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(amount);
    }

    // ==================== Public API ====================

    return {
        init,
        loadAssets,
        loadStatistics,
        openCreateModal,
        viewAsset
    };
})();

document.addEventListener('DOMContentLoaded', () => {
    setTimeout(() => AssetManagement.init(), 100);
});

window.AssetManagement = AssetManagement;
export { AssetManagement };
