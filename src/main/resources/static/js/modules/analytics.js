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
import ReportService from '../services/reportService.js';

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
            // Report card actions
            if (e.target.closest('.preview-report-btn')) {
                const type = e.target.closest('.preview-report-btn').dataset.type;
                previewReport(type);
            }
            if (e.target.closest('.export-report-btn')) {
                const type = e.target.closest('.export-report-btn').dataset.type;
                const format = e.target.closest('.export-report-btn').dataset.format;
                exportReport(type, format);
            }
            // New report button
            if (e.target.closest('#analytics-new-report-btn')) {
                window.ReportModule.showNewReportDialog();
            }
            // Preview download buttons
            if (e.target.closest('#preview-download-csv')) {
                downloadPreviewReport('csv');
            }
            if (e.target.closest('#preview-download-excel')) {
                downloadPreviewReport('excel');
            }
            // Close preview
            if (e.target.closest('.preview-close-btn')) {
                closeReportPreview();
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

    // ==================== Report Functions ====================

    // Store current preview report type
    let currentPreviewReportType = null;

    async function previewReport(type) {
        const previewArea = document.getElementById('analytics-report-preview');
        const previewContent = document.getElementById('preview-report-content');
        const previewTitle = document.getElementById('preview-report-title');

        if (!previewArea || !previewContent) return;

        // Show loading
        previewArea.classList.remove('hidden');
        previewContent.innerHTML = '<div class="loading-spinner">Đang tải báo cáo...</div>';
        previewTitle.textContent = getReportTitle(type);

        try {
            let report;
            switch (type) {
                case 'tickets':
                    report = await ReportService.generateTicketReport({ days: 30 });
                    previewContent.innerHTML = renderTicketReportPreview(report);
                    break;
                case 'sla':
                    report = await ReportService.generateSlaReport({ days: 30 });
                    previewContent.innerHTML = renderSlaReportPreview(report);
                    break;
                case 'assets':
                    report = await ReportService.generateAssetReport({ days: 30 });
                    previewContent.innerHTML = renderAssetReportPreview(report);
                    break;
                default:
                    previewContent.innerHTML = '<p>Report type not available for preview.</p>';
            }
            currentPreviewReportType = type;
        } catch (error) {
            console.error('Error previewing report:', error);
            previewContent.innerHTML = `<p class="error">Lỗi khi tải báo cáo: ${error.message}</p>`;
        }
    }

    function renderTicketReportPreview(report) {
        return `
            <div class="report-summary-card">
                <div class="summary-stat">
                    <span class="stat-value">${report.stats.total}</span>
                    <span class="stat-label">Total Tickets</span>
                </div>
                <div class="summary-stat">
                    <span class="stat-value">${report.resolutionRate.toFixed(1)}%</span>
                    <span class="stat-label">Resolution Rate</span>
                </div>
            </div>
            <div class="report-breakdown">
                <div class="breakdown-section">
                    <h5>By Status</h5>
                    <div class="breakdown-list">
                        ${Object.entries(report.stats.byStatus).map(([k, v]) => 
                            `<div class="breakdown-item"><span>${k}</span><span class="badge">${v}</span></div>`
                        ).join('')}
                    </div>
                </div>
                <div class="breakdown-section">
                    <h5>By Priority</h5>
                    <div class="breakdown-list">
                        ${Object.entries(report.stats.byPriority).map(([k, v]) => 
                            `<div class="breakdown-item"><span>${k}</span><span class="badge">${v}</span></div>`
                        ).join('')}
                    </div>
                </div>
            </div>
            <div class="report-table-container">
                <table class="data-table report-table">
                    <thead>
                        <tr>
                            <th>Ticket</th>
                            <th>Title</th>
                            <th>Priority</th>
                            <th>Status</th>
                            <th>Assignee</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${report.tickets.slice(0, 20).map(t => `
                            <tr>
                                <td>${t.ticketNumber}</td>
                                <td>${t.title}</td>
                                <td><span class="priority-badge ${t.priority}">${t.priority}</span></td>
                                <td>${t.status}</td>
                                <td>${t.assigneeName || '-'}</td>
                            </tr>
                        `).join('')}
                    </tbody>
                </table>
                ${report.tickets.length > 20 ? `<p class="table-note">Showing 20 of ${report.tickets.length} tickets</p>` : ''}
            </div>
        `;
    }

    function renderSlaReportPreview(report) {
        const complianceColor = report.overallComplianceRate >= 80 ? '#10B981' : (report.overallComplianceRate >= 60 ? '#F59E0B' : '#F43F5E');
        return `
            <div class="report-summary-card sla-summary-card">
                <div class="summary-stat large">
                    <span class="stat-value" style="color: ${complianceColor}">${report.overallComplianceRate.toFixed(1)}%</span>
                    <span class="stat-label">Overall SLA Compliance</span>
                </div>
                <div class="sla-breakdown">
                    <div class="sla-item">
                        <span class="sla-label">Response SLA</span>
                        <div class="sla-bar-container">
                            <div class="sla-bar" style="width: ${report.responseComplianceRate}%; background: ${report.responseComplianceRate >= 80 ? '#10B981' : '#F59E0B'}"></div>
                        </div>
                        <span class="sla-value">${report.responseComplianceRate.toFixed(1)}%</span>
                    </div>
                    <div class="sla-item">
                        <span class="sla-label">Resolution SLA</span>
                        <div class="sla-bar-container">
                            <div class="sla-bar" style="width: ${report.resolutionComplianceRate}%; background: ${report.resolutionComplianceRate >= 80 ? '#10B981' : '#F59E0B'}"></div>
                        </div>
                        <span class="sla-value">${report.resolutionComplianceRate.toFixed(1)}%</span>
                    </div>
                </div>
            </div>
            <div class="report-breakdown">
                <div class="breakdown-section">
                    <h5>Response SLA</h5>
                    <div class="breakdown-list">
                        <div class="breakdown-item"><span>Met</span><span class="badge success">${report.stats.responseMet}</span></div>
                        <div class="breakdown-item"><span>Breached</span><span class="badge danger">${report.stats.responseBreached}</span></div>
                        <div class="breakdown-item"><span>Pending</span><span class="badge">${report.stats.pendingResponse}</span></div>
                    </div>
                </div>
                <div class="breakdown-section">
                    <h5>Resolution SLA</h5>
                    <div class="breakdown-list">
                        <div class="breakdown-item"><span>Met</span><span class="badge success">${report.stats.resolutionMet}</span></div>
                        <div class="breakdown-item"><span>Breached</span><span class="badge danger">${report.stats.resolutionBreached}</span></div>
                        <div class="breakdown-item"><span>Pending</span><span class="badge">${report.stats.pendingResolution}</span></div>
                    </div>
                </div>
            </div>
            <div class="report-table-container">
                <table class="data-table report-table">
                    <thead>
                        <tr>
                            <th>Ticket</th>
                            <th>Priority</th>
                            <th>Response SLA</th>
                            <th>Resolution SLA</th>
                            <th>Assignee</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${report.details.slice(0, 20).map(d => `
                            <tr>
                                <td>${d.ticketNumber}</td>
                                <td><span class="priority-badge ${d.priority}">${d.priority}</span></td>
                                <td>
                                    ${d.responseSlaMet === true ? '<span class="sla-status met">✓ MET</span>' : ''}
                                    ${d.responseSlaMet === false ? '<span class="sla-status breached">✗ BREACHED</span>' : ''}
                                    ${d.responseSlaMet === null ? '<span class="sla-status pending">⏳ PENDING</span>' : ''}
                                </td>
                                <td>
                                    ${d.resolutionSlaMet === true ? '<span class="sla-status met">✓ MET</span>' : ''}
                                    ${d.resolutionSlaMet === false ? '<span class="sla-status breached">✗ BREACHED</span>' : ''}
                                    ${d.resolutionSlaMet === null ? '<span class="sla-status pending">⏳ PENDING</span>' : ''}
                                </td>
                                <td>${d.assignee || '-'}</td>
                            </tr>
                        `).join('')}
                    </tbody>
                </table>
                ${report.details.length > 20 ? `<p class="table-note">Showing 20 of ${report.details.length} tickets</p>` : ''}
            </div>
        `;
    }

    function renderAssetReportPreview(report) {
        return `
            <div class="report-summary-card">
                <div class="summary-stat">
                    <span class="stat-value">${report.stats.total}</span>
                    <span class="stat-label">Total Assets</span>
                </div>
                <div class="summary-stat">
                    <span class="stat-value" style="color: ${report.healthRate >= 80 ? '#10B981' : '#F59E0B'}">${report.healthRate.toFixed(1)}%</span>
                    <span class="stat-label">Health Rate</span>
                </div>
                <div class="summary-stat">
                    <span class="stat-value">${report.activeRate.toFixed(1)}%</span>
                    <span class="stat-label">Active Rate</span>
                </div>
            </div>
            <div class="report-breakdown">
                <div class="breakdown-section">
                    <h5>By Health Status</h5>
                    <div class="breakdown-list">
                        ${Object.entries(report.stats.byHealth).map(([k, v]) => 
                            `<div class="breakdown-item"><span>${k}</span><span class="badge">${v}</span></div>`
                        ).join('')}
                    </div>
                </div>
                <div class="breakdown-section">
                    <h5>By Type</h5>
                    <div class="breakdown-list">
                        ${Object.entries(report.stats.byType).map(([k, v]) => 
                            `<div class="breakdown-item"><span>${k}</span><span class="badge">${v}</span></div>`
                        ).join('')}
                    </div>
                </div>
                <div class="breakdown-section">
                    <h5>By Location</h5>
                    <div class="breakdown-list">
                        ${Object.entries(report.stats.byLocation).map(([k, v]) => 
                            `<div class="breakdown-item"><span>${k || 'Unassigned'}</span><span class="badge">${v}</span></div>`
                        ).join('')}
                    </div>
                </div>
            </div>
            <div class="report-table-container">
                <table class="data-table report-table">
                    <thead>
                        <tr>
                            <th>Asset Number</th>
                            <th>Name</th>
                            <th>Type</th>
                            <th>Status</th>
                            <th>Health</th>
                            <th>Location</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${report.assets.slice(0, 20).map(a => `
                            <tr>
                                <td>${a.assetNumber}</td>
                                <td>${a.name}</td>
                                <td>${a.assetType}</td>
                                <td><span class="status-badge ${a.status}">${a.status}</span></td>
                                <td><span class="health-badge ${a.healthStatus}">${a.healthStatus}</span></td>
                                <td>${a.location || '-'}</td>
                            </tr>
                        `).join('')}
                    </tbody>
                </table>
                ${report.assets.length > 20 ? `<p class="table-note">Showing 20 of ${report.assets.length} assets</p>` : ''}
            </div>
        `;
    }

    function getReportTitle(type) {
        const titles = {
            'tickets': 'Ticket Volume Report',
            'sla': 'SLA Compliance Report',
            'assets': 'Asset Inventory Report',
            'agent': 'Agent Performance Report',
            'changes': 'Change Success Rate Report'
        };
        return titles[type] || 'Report Preview';
    }

    async function exportReport(type, format) {
        try {
            switch (type) {
                case 'tickets':
                    await ReportService.exportTicketReport(format, { days: 30 });
                    break;
                case 'sla':
                    await ReportService.exportSlaReport(format, { days: 30 });
                    break;
                case 'assets':
                    await ReportService.exportAssetReport(format, { days: 30 });
                    break;
            }
            if (window.Toast) {
                window.Toast.show(`Báo cáo đã được tải xuống (${format.toUpperCase()})`, 'success');
            }
        } catch (error) {
            console.error('Export error:', error);
            if (window.Toast) {
                window.Toast.show('Lỗi khi xuất báo cáo: ' + error.message, 'error');
            }
        }
    }

    async function downloadPreviewReport(format) {
        if (!currentPreviewReportType) {
            if (window.Toast) {
                window.Toast.show('Không có báo cáo để tải xuống', 'warning');
            }
            return;
        }
        await exportReport(currentPreviewReportType, format);
    }

    function closeReportPreview() {
        const previewArea = document.getElementById('analytics-report-preview');
        if (previewArea) {
            previewArea.classList.add('hidden');
        }
        currentPreviewReportType = null;
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
