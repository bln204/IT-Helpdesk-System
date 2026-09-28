/**
 * Asset-Ticket Link Management - Connectivity Visualization
 * 
 * Design Principles:
 * - Bidirectional link visualization
 * - Indigo color system for connectivity theme
 * - Link role badges with color coding
 * - Connection lines metaphor
 */
const AssetLinkModule = (() => {
    'use strict';

    const API_BASE = '/api/asset-links';

    // ==================== Link Role Config ====================

    const linkRoleConfig = {
        'AFFECTED': { label: 'Affected', color: '#F59E0B', icon: '💥' },
        'ROOT_CAUSE': { label: 'Root Cause', color: '#F43F5E', icon: '🎯' },
        'SUSPECT': { label: 'Suspect', color: '#8B5CF6', icon: '🔍' },
        'RESOLVED_BY': { label: 'Resolved By', color: '#10B981', icon: '✅' }
    };

    const healthConfig = {
        'HEALTHY': { label: 'Healthy', color: '#10B981' },
        'WARNING': { label: 'Warning', color: '#F59E0B' },
        'CRITICAL': { label: 'Critical', color: '#F43F5E' },
        'UNKNOWN': { label: 'Unknown', color: '#6B7280' }
    };

    // ==================== Load Linked Assets for Incident ====================

    async function loadLinkedAssets(incidentId) {
        try {
            const response = await fetch(`${API_BASE}/incident/${incidentId}`, Auth.getAuthHeaders());
            if (!response.ok) throw new Error('Failed to load linked assets');
            const links = await response.json();
            renderLinkedAssets(links, incidentId);
            return links;
        } catch (error) {
            console.error('Error loading linked assets:', error);
            return [];
        }
    }

    function renderLinkedAssets(links, incidentId) {
        const container = document.getElementById('incident-linked-assets');
        if (!container) return;

        if (!links || links.length === 0) {
            container.innerHTML = `
                <div class="link-empty-state">
                    <span class="link-icon">🔗</span>
                    <p>No assets linked to this incident</p>
                    <button class="link-add-btn" data-incident="${incidentId}">
                        + Link an asset
                    </button>
                </div>
            `;
            return;
        }

        container.innerHTML = `
            <div class="linked-assets-header">
                <span class="linked-count">${links.length} linked asset${links.length > 1 ? 's' : ''}</span>
                <button class="link-add-btn small" data-incident="${incidentId}">
                    + Link asset
                </button>
            </div>
            <div class="linked-assets-list">
                ${links.map(link => renderLinkedAssetItem(link)).join('')}
            </div>
        `;

        // Setup event listeners
        setupLinkedAssetListeners(links, incidentId);
    }

    function renderLinkedAssetItem(link) {
        const roleCfg = linkRoleConfig[link.linkRole] || linkRoleConfig['AFFECTED'];
        const healthCfg = healthConfig[link.healthStatus] || healthConfig['UNKNOWN'];

        return `
            <div class="linked-asset-item" data-link-id="${link.id}">
                <div class="linked-asset-info">
                    <div class="linked-asset-icon">
                        ${getAssetIcon(link.assetType)}
                    </div>
                    <div class="linked-asset-details">
                        <span class="linked-asset-name">${escapeHtml(link.assetName)}</span>
                        <span class="linked-asset-number">${link.assetNumber}</span>
                    </div>
                </div>
                <div class="linked-asset-meta">
                    <span class="linked-health-badge" style="--health-color: ${healthCfg.color}">
                        ${healthCfg.label}
                    </span>
                    <span class="linked-role-badge" style="--role-color: ${roleCfg.color}">
                        ${roleCfg.icon} ${roleCfg.label}
                    </span>
                </div>
                <div class="linked-asset-actions">
                    <button class="link-unlink-btn" data-link-id="${link.id}" title="Unlink">
                        Unlink
                    </button>
                </div>
            </div>
        `;
    }

    function getAssetIcon(type) {
        const icons = {
            'SERVER': '🖥️',
            'WORKSTATION': '🖥️',
            'LAPTOP': '💻',
            'NETWORK_DEVICE': '🌐',
            'PRINTER': '🖨️',
            'MONITOR': '🖵',
            'MOBILE_DEVICE': '📱',
            'SOFTWARE_LICENSE': '📜',
            'STORAGE': '💾',
            'CLOUD_RESOURCE': '☁️'
        };
        return icons[type] || '📦';
    }

    function setupLinkedAssetListeners(links, incidentId) {
        // Add link button
        document.querySelectorAll('.link-add-btn').forEach(btn => {
            btn.addEventListener('click', () => {
                openLinkAssetModal(incidentId);
            });
        });

        // Unlink buttons
        document.querySelectorAll('.link-unlink-btn').forEach(btn => {
            btn.addEventListener('click', async () => {
                const linkId = btn.dataset.linkId;
                await unlinkAsset(linkId, incidentId);
            });
        });
    }

    // ==================== Load Linked Incidents for Asset ====================

    async function loadLinkedIncidents(assetId) {
        try {
            const response = await fetch(`${API_BASE}/asset/${assetId}/incidents`, Auth.getAuthHeaders());
            if (!response.ok) throw new Error('Failed to load linked incidents');
            const links = await response.json();
            renderLinkedIncidents(links, assetId);
            return links;
        } catch (error) {
            console.error('Error loading linked incidents:', error);
            return [];
        }
    }

    function renderLinkedIncidents(links, assetId) {
        const container = document.getElementById('asset-linked-incidents');
        if (!container) return;

        if (!links || links.length === 0) {
            container.innerHTML = `
                <div class="link-empty-state small">
                    <p>No incidents linked</p>
                </div>
            `;
            return;
        }

        container.innerHTML = `
            <div class="linked-incidents-list">
                ${links.map(link => `
                    <div class="linked-incident-item">
                        <span class="linked-incident-id">INC-${link.incidentId}</span>
                        <span class="linked-incident-role" style="--role-color: ${getRoleColor(link.linkRole)}">
                            ${getRoleIcon(link.linkRole)} ${link.linkRoleLabel || link.linkRole}
                        </span>
                    </div>
                `).join('')}
            </div>
        `;
    }

    function getRoleColor(role) {
        return linkRoleConfig[role]?.color || '#6B7280';
    }

    function getRoleIcon(role) {
        return linkRoleConfig[role]?.icon || '🔗';
    }

    // ==================== Link Asset Modal ====================

    function openLinkAssetModal(incidentId) {
        // Create modal content
        const modal = document.createElement('div');
        modal.className = 'modal';
        modal.id = 'link-asset-modal';
        modal.innerHTML = `
            <div class="modal-content link-modal-content">
                <header class="panel-header">
                    <div>
                        <h3>Link Asset to Incident</h3>
                        <p>Search and select an asset to link</p>
                    </div>
                    <button class="ghost link-modal-close">Close</button>
                </header>
                <div class="link-search-section">
                    <input type="text" id="asset-search-input" placeholder="Search by name, number, or IP..." />
                </div>
                <div id="asset-search-results" class="asset-search-results">
                    <p class="search-hint">Start typing to search for assets</p>
                </div>
            </div>
        `;

        document.body.appendChild(modal);
        modal.classList.remove('hidden');
        document.body.classList.add('modal-open');

        // Setup event listeners
        const closeBtn = modal.querySelector('.link-modal-close');
        closeBtn.addEventListener('click', () => closeLinkModal(modal));

        const searchInput = modal.querySelector('#asset-search-input');
        let debounceTimer;
        searchInput.addEventListener('input', () => {
            clearTimeout(debounceTimer);
            debounceTimer = setTimeout(() => {
                searchAssets(searchInput.value, incidentId, modal);
            }, 300);
        });

        searchInput.focus();
    }

    async function searchAssets(query, incidentId, modal) {
        const resultsContainer = modal.querySelector('#asset-search-results');

        if (!query || query.length < 2) {
            resultsContainer.innerHTML = '<p class="search-hint">Start typing to search for assets</p>';
            return;
        }

        try {
            const response = await fetch(`/api/assets?search=${encodeURIComponent(query)}&size=10`, 
                    Auth.getAuthHeaders());
            if (!response.ok) throw new Error('Search failed');
            
            const data = await response.json();
            const assets = data.content || [];

            if (assets.length === 0) {
                resultsContainer.innerHTML = '<p class="search-hint">No assets found</p>';
                return;
            }

            resultsContainer.innerHTML = assets.map(asset => {
                const healthCfg = healthConfig[asset.healthStatus] || healthConfig['UNKNOWN'];
                return `
                    <div class="asset-search-item" data-asset-id="${asset.id}">
                        <div class="asset-search-icon">${getAssetIcon(asset.assetType)}</div>
                        <div class="asset-search-info">
                            <span class="asset-search-name">${escapeHtml(asset.name)}</span>
                            <span class="asset-search-number">${asset.assetNumber}</span>
                        </div>
                        <div class="asset-search-meta">
                            <span class="health-badge-small" style="--health-color: ${healthCfg.color}">
                                ${healthCfg.label}
                            </span>
                            <button class="link-select-btn" data-asset-id="${asset.id}">Select</button>
                        </div>
                    </div>
                `;
            }).join('');

            // Setup select buttons
            resultsContainer.querySelectorAll('.link-select-btn').forEach(btn => {
                btn.addEventListener('click', () => {
                    const assetId = btn.dataset.assetId;
                    showLinkRoleSelector(assetId, incidentId, modal);
                });
            });

        } catch (error) {
            console.error('Error searching assets:', error);
            resultsContainer.innerHTML = '<p class="search-hint">Search failed. Try again.</p>';
        }
    }

    function showLinkRoleSelector(assetId, incidentId, modal) {
        const resultsContainer = modal.querySelector('#asset-search-results');
        
        resultsContainer.innerHTML = `
            <div class="link-role-selector">
                <h4>How is this asset related?</h4>
                <div class="role-options">
                    <button class="role-option-btn" data-role="AFFECTED">
                        <span class="role-icon">💥</span>
                        <span class="role-label">Affected</span>
                        <span class="role-desc">Asset is impacted by this incident</span>
                    </button>
                    <button class="role-option-btn" data-role="ROOT_CAUSE">
                        <span class="role-icon">🎯</span>
                        <span class="role-label">Root Cause</span>
                        <span class="role-desc">Asset is causing the incident</span>
                    </button>
                    <button class="role-option-btn" data-role="SUSPECT">
                        <span class="role-icon">🔍</span>
                        <span class="role-label">Suspect</span>
                        <span class="role-desc">Possibly related, needs investigation</span>
                    </button>
                </div>
                <button class="back-to-search-btn">← Back to search</button>
            </div>
        `;

        // Setup role selection
        resultsContainer.querySelectorAll('.role-option-btn').forEach(btn => {
            btn.addEventListener('click', async () => {
                const role = btn.dataset.role;
                await createLink(assetId, incidentId, role);
                closeLinkModal(modal);
                Toast.show('Asset linked successfully', 'success');
                // Refresh linked assets
                loadLinkedAssets(incidentId);
            });
        });

        // Back button
        resultsContainer.querySelector('.back-to-search-btn').addEventListener('click', () => {
            const searchInput = modal.querySelector('#asset-search-input');
            searchAssets(searchInput.value, incidentId, modal);
        });
    }

    async function createLink(assetId, incidentId, role) {
        const response = await fetch(`${API_BASE}/incident`, {
            method: 'POST',
            headers: Auth.getAuthHeaders(),
            body: JSON.stringify({
                assetId: parseInt(assetId),
                incidentId: parseInt(incidentId),
                linkRole: role,
                impactDescription: ''
            })
        });

        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.message || 'Failed to link asset');
        }
    }

    async function unlinkAsset(linkId, incidentId) {
        if (!confirm('Unlink this asset from the incident?')) return;

        try {
            const response = await fetch(`${API_BASE}/incident/${incidentId}/asset/${linkId}`, {
                method: 'DELETE',
                headers: Auth.getAuthHeaders()
            });

            if (!response.ok) throw new Error('Failed to unlink');

            Toast.show('Asset unlinked', 'success');
            loadLinkedAssets(incidentId);
        } catch (error) {
            console.error('Error unlinking asset:', error);
            Toast.show('Failed to unlink asset', 'error');
        }
    }

    function closeLinkModal(modal) {
        modal.classList.add('hidden');
        document.body.classList.remove('modal-open');
        setTimeout(() => modal.remove(), 200);
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
        loadLinkedAssets,
        loadLinkedIncidents,
        openLinkAssetModal
    };
})();

// Auto-expose globally
window.AssetLinkModule = AssetLinkModule;
export { AssetLinkModule };
