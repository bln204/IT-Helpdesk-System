/**
 * Analytics Module - Dashboard & Reporting
 * 
 * Design Principles:
 * - Real-time KPI metrics as hero elements
 * - Dark slate structure for data visualization
 * - Emerald/Orange/Cyan color system for metrics
 * - Clean grid layout for charts
 */
import { tokenKey } from '../config/constants.js';

const AnalyticsModule = (() => {
    'use strict';

    const API_BASE = '/api/analytics';

    // State
    let dashboardMetrics = null;

    // ==================== Initialization ====================

    function init() {
        if (typeof Auth === 'undefined') {
            console.error('Auth module not found');
            return;
        }

        // Wait for token to be available before initializing
        Auth.onAuthReady(() => {
            setupEventListeners();
            loadDashboard();
        });
    }

    function setupEventListeners() {
        document.addEventListener('click', (e) => {
            if (e.target.closest('#refresh-analytics-btn')) {
                loadDashboard();
            }
            if (e.target.closest('.analytics-tab-btn')) {
                const tab = e.target.closest('.analytics-tab-btn').dataset.analyticsTab;
                switchTab(tab);
            }
        });
    }

    // ==================== API Calls ====================

    async function loadDashboard() {
        try {
            // Get fresh token directly from localStorage every time
            const token = localStorage.getItem(tokenKey);
            if (!token) {
                console.warn('Analytics: No token in localStorage, waiting...');
                await new Promise(resolve => setTimeout(resolve, 500));
                const retryToken = localStorage.getItem(tokenKey);
                if (!retryToken) {
                    Toast.show('Phiên đăng nhập chưa sẵn sàng', 'error');
                    return;
                }
            }
            
            // Use fresh token from localStorage
            const freshToken = localStorage.getItem(tokenKey);
            const response = await fetch(`${API_BASE}/dashboard`, {
                headers: {
                    'Authorization': `Bearer ${freshToken}`,
                    'Content-Type': 'application/json'
                },
                credentials: 'same-origin'
            });
            if (!response.ok) {
                throw new Error('Failed to load dashboard');
            }
            dashboardMetrics = await response.json();
            renderDashboard(dashboardMetrics);
            
            // Load additional chart data
            loadTicketVolume();
            loadAssetHealth();
            loadChangeStatus();
        } catch (error) {
            console.error('Error loading dashboard:', error);
            Toast.show('Lỗi khi tải dashboard', 'error');
        }
    }

    async function loadTicketVolume() {
        try {
            const token = localStorage.getItem(tokenKey);
            if (!token) return;
            const response = await fetch(`${API_BASE}/tickets/volume?days=30`, {
                headers: {
                    'Authorization': `Bearer ${token}`,
                    'Content-Type': 'application/json'
                },
                credentials: 'same-origin'
            });
            if (!response.ok) return;
            const data = await response.json();
            renderTicketVolumeChart(data);
        } catch (error) {
            console.error('Error loading ticket volume:', error);
        }
    }

    async function loadAssetHealth() {
        try {
            const token = localStorage.getItem(tokenKey);
            if (!token) return;
            const response = await fetch(`${API_BASE}/assets/health`, {
                headers: {
                    'Authorization': `Bearer ${token}`,
                    'Content-Type': 'application/json'
                },
                credentials: 'same-origin'
            });
            if (!response.ok) return;
            const data = await response.json();
            renderAssetHealthChart(data);
        } catch (error) {
            console.error('Error loading asset health:', error);
        }
    }

    async function loadChangeStatus() {
        try {
            const token = localStorage.getItem(tokenKey);
            if (!token) return;
            const response = await fetch(`${API_BASE}/changes/by-status`, {
                headers: {
                    'Authorization': `Bearer ${token}`,
                    'Content-Type': 'application/json'
                },
                credentials: 'same-origin'
            });
            if (!response.ok) return;
            const data = await response.json();
            renderChangeStatusChart(data);
        } catch (error) {
            console.error('Error loading change status:', error);
        }
    }

    // ==================== Rendering ====================

    function renderDashboard(metrics) {
        // Render KPI cards
        renderKPICards(metrics);
        
        // Render summary section
        renderSummarySection(metrics);
    }

    function renderKPICards(metrics) {
        const container = document.getElementById('analytics-kpi-cards');
        if (!container) return;

        container.innerHTML = `
            <div class="kpi-card kpi-tickets">
                <div class="kpi-icon">🎫</div>
                <div class="kpi-content">
                    <span class="kpi-value">${metrics.tickets.total}</span>
                    <span class="kpi-label">Total Tickets</span>
                </div>
                <div class="kpi-breakdown">
                    <span class="kpi-stat open">${metrics.tickets.open} Open</span>
                    <span class="kpi-stat resolved">${metrics.tickets.resolved} Resolved</span>
                </div>
            </div>

            <div class="kpi-card kpi-resolution">
                <div class="kpi-icon">📊</div>
                <div class="kpi-content">
                    <span class="kpi-value">${metrics.tickets.resolutionRate}%</span>
                    <span class="kpi-label">Resolution Rate</span>
                </div>
                <div class="kpi-trend positive">
                    <span class="trend-arrow">↑</span>
                    <span>vs last period</span>
                </div>
            </div>

            <div class="kpi-card kpi-assets">
                <div class="kpi-icon">🖥️</div>
                <div class="kpi-content">
                    <span class="kpi-value">${metrics.assets.total}</span>
                    <span class="kpi-label">Total Assets</span>
                </div>
                <div class="kpi-breakdown">
                    <span class="kpi-stat healthy">${metrics.assets.healthy} Healthy</span>
                    <span class="kpi-stat warning">${metrics.assets.warning + metrics.assets.critical} Need Attention</span>
                </div>
            </div>

            <div class="kpi-card kpi-changes">
                <div class="kpi-icon">🔄</div>
                <div class="kpi-content">
                    <span class="kpi-value">${metrics.changes.total}</span>
                    <span class="kpi-label">Total Changes</span>
                </div>
                <div class="kpi-breakdown">
                    <span class="kpi-stat pending">${metrics.changes.pending} Pending</span>
                    <span class="kpi-stat active">${metrics.changes.inProgress} Active</span>
                </div>
            </div>
        `;
    }

    function renderSummarySection(metrics) {
        const container = document.getElementById('analytics-summary');
        if (!container) return;

        container.innerHTML = `
            <div class="summary-section">
                <h4>Service Desk Overview</h4>
                <div class="summary-grid">
                    <div class="summary-item">
                        <span class="summary-value">${metrics.tickets.totalIncidents}</span>
                        <span class="summary-label">Incidents</span>
                    </div>
                    <div class="summary-item">
                        <span class="summary-value">${metrics.tickets.totalServiceRequests}</span>
                        <span class="summary-label">Service Requests</span>
                    </div>
                    <div class="summary-item">
                        <span class="summary-value">${metrics.tickets.inProgress}</span>
                        <span class="summary-label">In Progress</span>
                    </div>
                </div>
            </div>

            <div class="summary-section">
                <h4>Asset Health</h4>
                <div class="summary-grid">
                    <div class="summary-item">
                        <span class="summary-value healthy-text">${metrics.assets.healthy}</span>
                        <span class="summary-label">Healthy</span>
                    </div>
                    <div class="summary-item">
                        <span class="summary-value warning-text">${metrics.assets.warning}</span>
                        <span class="summary-label">Warning</span>
                    </div>
                    <div class="summary-item">
                        <span class="summary-value critical-text">${metrics.assets.critical}</span>
                        <span class="summary-label">Critical</span>
                    </div>
                </div>
            </div>

            <div class="summary-section">
                <h4>Changes</h4>
                <div class="summary-grid">
                    <div class="summary-item">
                        <span class="summary-value">${metrics.changes.pending}</span>
                        <span class="summary-label">Pending Approval</span>
                    </div>
                    <div class="summary-item">
                        <span class="summary-value">${metrics.changes.inProgress}</span>
                        <span class="summary-label">In Progress</span>
                    </div>
                    <div class="summary-item">
                        <span class="summary-value success-text">${metrics.changes.completed}</span>
                        <span class="summary-label">Completed</span>
                    </div>
                </div>
            </div>
        `;
    }

    function renderTicketVolumeChart(data) {
        const container = document.getElementById('ticket-volume-chart');
        if (!container) return;

        if (!data || data.length === 0) {
            container.innerHTML = '<p class="chart-empty">No data available</p>';
            return;
        }

        // Simple bar chart using divs
        const maxValue = Math.max(...data.map(d => d.value));
        
        container.innerHTML = `
            <div class="bar-chart">
                ${data.slice(-14).map(item => `
                    <div class="bar-container" title="${item.date}: ${item.value} tickets">
                        <div class="bar" style="height: ${(item.value / maxValue) * 100}%"></div>
                        <span class="bar-label">${formatShortDate(item.date)}</span>
                    </div>
                `).join('')}
            </div>
        `;
    }

    function renderAssetHealthChart(data) {
        const container = document.getElementById('asset-health-chart');
        if (!container) return;

        if (!data || data.length === 0) {
            container.innerHTML = '<p class="chart-empty">No data available</p>';
            return;
        }

        const total = data.reduce((sum, item) => sum + item.count, 0);
        
        container.innerHTML = `
            <div class="donut-chart">
                <div class="donut-center">
                    <span class="donut-total">${total}</span>
                    <span class="donut-label">Assets</span>
                </div>
                <div class="donut-legend">
                    ${data.map(item => `
                        <div class="legend-item">
                            <span class="legend-color" style="background: ${item.color}"></span>
                            <span class="legend-label">${item.label}</span>
                            <span class="legend-value">${item.count}</span>
                        </div>
                    `).join('')}
                </div>
            </div>
        `;
    }

    function renderChangeStatusChart(data) {
        const container = document.getElementById('change-status-chart');
        if (!container) return;

        if (!data || data.length === 0) {
            container.innerHTML = '<p class="chart-empty">No data available</p>';
            return;
        }

        container.innerHTML = `
            <div class="horizontal-bar-chart">
                ${data.map(item => {
                    const maxValue = Math.max(...data.map(d => d.count));
                    const width = maxValue > 0 ? (item.count / maxValue) * 100 : 0;
                    return `
                        <div class="h-bar-item">
                            <div class="h-bar-header">
                                <span class="h-bar-label">${item.status}</span>
                                <span class="h-bar-value">${item.count}</span>
                            </div>
                            <div class="h-bar-track">
                                <div class="h-bar-fill" style="width: ${width}%; background: ${item.color}"></div>
                            </div>
                        </div>
                    `;
                }).join('')}
            </div>
        `;
    }

    // ==================== Tab Switching ====================

    function switchTab(tab) {
        document.querySelectorAll('.analytics-tab-btn').forEach(btn => {
            btn.classList.toggle('active', btn.dataset.analyticsTab === tab);
        });

        document.getElementById('analytics-overview-section')?.classList.toggle('hidden', tab !== 'overview');
        document.getElementById('analytics-reports-section')?.classList.toggle('hidden', tab !== 'reports');
        document.getElementById('analytics-tickets-section')?.classList.toggle('hidden', tab !== 'tickets');
    }

    // ==================== Helpers ====================

    function formatShortDate(dateStr) {
        if (!dateStr) return '';
        const date = new Date(dateStr);
        return `${date.getDate()}/${date.getMonth() + 1}`;
    }

    // ==================== Public API ====================

    return {
        init,
        loadDashboard
    };
})();

// Auto-initialize
document.addEventListener('DOMContentLoaded', () => {
    setTimeout(() => AnalyticsModule.init(), 100);
});

window.AnalyticsModule = AnalyticsModule;
export { AnalyticsModule };
