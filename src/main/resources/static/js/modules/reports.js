// ==================== Reports Module ====================
// Enhanced Reports Module with New Report functionality

import ReportService from '../services/reportService.js';

// State
let currentReport = null;
let currentReportType = null;

// ==================== Public API ====================

export const getCurrentReport = () => currentReport;
export const getCurrentReportType = () => currentReportType;

export const setCurrentReport = (type, title, columns, rows) => {
    currentReportType = type;
    currentReport = {
        type,
        title,
        columns,
        rows,
    };
};

export const clearCurrentReport = () => {
    currentReport = null;
    currentReportType = null;
};

// ==================== Report Table Rendering ====================

export const renderReportTable = (columns, rows) => {
    const reportOutput = document.getElementById('report-output');
    if (!reportOutput) return;
    
    reportOutput.innerHTML = '';
    if (!rows || rows.length === 0) {
        reportOutput.innerHTML = '<div class="empty">Không có kết quả.</div>';
        return;
    }
    
    const renderTable = (cols, tableRows) => {
        const table = document.createElement('table');
        table.className = 'data-table report-table';
        const thead = document.createElement('thead');
        const headerRow = document.createElement('tr');
        cols.forEach((col) => {
            const th = document.createElement('th');
            th.textContent = col;
            headerRow.appendChild(th);
        });
        thead.appendChild(headerRow);
        table.appendChild(thead);
        
        const tbody = document.createElement('tbody');
        tableRows.forEach((row) => {
            const tr = document.createElement('tr');
            cols.forEach((col) => {
                const td = document.createElement('td');
                td.textContent = row[col] ?? '';
                tr.appendChild(td);
            });
            tbody.appendChild(tr);
        });
        table.appendChild(tbody);
        return table;
    };
    
    const wrap = document.createElement('div');
    wrap.className = 'table-wrap';
    wrap.appendChild(renderTable(columns, rows));
    reportOutput.appendChild(wrap);
};

// ==================== New Report Dialog ====================

export const showNewReportDialog = () => {
    const existingDialog = document.getElementById('new-report-dialog');
    if (existingDialog) {
        existingDialog.remove();
    }
    
    const dialog = document.createElement('div');
    dialog.id = 'new-report-dialog';
    dialog.className = 'modal-overlay';
    dialog.innerHTML = `
        <div class="modal-content new-report-modal">
            <div class="modal-header">
                <h3>Tạo Báo Cáo Mới</h3>
                <button class="modal-close" onclick="document.getElementById('new-report-dialog').remove()">&times;</button>
            </div>
            <div class="modal-body">
                <div class="form-group">
                    <label for="report-type">Loại Báo Cáo</label>
                    <select id="report-type" class="form-control">
                        <option value="tickets">Ticket Report</option>
                        <option value="sla">SLA Compliance</option>
                        <option value="assets">Asset Inventory</option>
                    </select>
                </div>
                
                <div class="form-group">
                    <label for="report-date-range">Khoảng Thời Gian</label>
                    <select id="report-date-range" class="form-control">
                        <option value="7">7 ngày gần nhất</option>
                        <option value="30" selected>30 ngày gần nhất</option>
                        <option value="90">90 ngày gần nhất</option>
                        <option value="custom">Tùy chỉnh</option>
                    </select>
                </div>
                
                <div id="custom-date-range" class="date-range-custom hidden">
                    <div class="form-row">
                        <div class="form-group">
                            <label for="report-start-date">Từ Ngày</label>
                            <input type="date" id="report-start-date" class="form-control" />
                        </div>
                        <div class="form-group">
                            <label for="report-end-date">Đến Ngày</label>
                            <input type="date" id="report-end-date" class="form-control" />
                        </div>
                    </div>
                </div>
                
                <div class="form-group">
                    <label for="report-export-format">Định Dạng Xuất</label>
                    <select id="report-export-format" class="form-control">
                        <option value="preview">Xem Trước</option>
                        <option value="csv">CSV</option>
                        <option value="excel">Excel</option>
                    </select>
                </div>
            </div>
            <div class="modal-footer">
                <button class="btn secondary" onclick="document.getElementById('new-report-dialog').remove()">Hủy</button>
                <button id="generate-report-btn" class="btn primary">Tạo Báo Cáo</button>
            </div>
        </div>
    `;
    
    document.body.appendChild(dialog);
    
    // Setup event listeners
    const dateRange = document.getElementById('report-date-range');
    const customDateRange = document.getElementById('custom-date-range');
    const generateBtn = document.getElementById('generate-report-btn');
    
    dateRange.addEventListener('change', () => {
        if (dateRange.value === 'custom') {
            customDateRange.classList.remove('hidden');
        } else {
            customDateRange.classList.add('hidden');
        }
    });
    
    generateBtn.addEventListener('click', handleGenerateReport);
    
    // Close on overlay click
    dialog.addEventListener('click', (e) => {
        if (e.target === dialog) {
            dialog.remove();
        }
    });
};

// ==================== Report Generation Handler ====================

async function handleGenerateReport() {
    const reportType = document.getElementById('report-type').value;
    const dateRange = document.getElementById('report-date-range').value;
    const exportFormat = document.getElementById('report-export-format').value;
    const generateBtn = document.getElementById('generate-report-btn');
    
    let params = {};
    
    if (dateRange === 'custom') {
        const startDate = document.getElementById('report-start-date').value;
        const endDate = document.getElementById('report-end-date').value;
        if (startDate) params.startDate = startDate;
        if (endDate) params.endDate = endDate;
    } else {
        params.days = parseInt(dateRange);
    }
    
    // Show loading state
    generateBtn.disabled = true;
    generateBtn.textContent = 'Đang tạo...';
    
    try {
        if (exportFormat === 'preview') {
            // Generate and show preview
            await generateAndPreviewReport(reportType, params);
        } else {
            // Export directly
            await exportReport(reportType, exportFormat, params);
            Toast.show('Báo cáo đã được tải xuống!', 'success');
        }
        
        // Close dialog
        const dialog = document.getElementById('new-report-dialog');
        if (dialog) dialog.remove();
        
    } catch (error) {
        console.error('Report generation error:', error);
        Toast.show('Lỗi khi tạo báo cáo: ' + error.message, 'error');
    } finally {
        generateBtn.disabled = false;
        generateBtn.textContent = 'Tạo Báo Cáo';
    }
}

// ==================== Generate and Preview ====================

async function generateAndPreviewReport(type, params) {
    let report;
    let columns;
    let rows;
    
    switch (type) {
        case 'tickets':
            report = await ReportService.generateTicketReport(params);
            columns = ['Ticket Number', 'Title', 'Priority', 'Status', 'Requester', 'Assignee', 'Created', 'Resolved'];
            rows = report.tickets.map(t => ({
                'Ticket Number': t.ticketNumber,
                'Title': t.title,
                'Priority': t.priority,
                'Status': t.status,
                'Requester': t.requesterName,
                'Assignee': t.assigneeName || '-',
                'Created': formatDate(t.createdAt),
                'Resolved': t.resolvedAt ? formatDate(t.resolvedAt) : '-'
            }));
            setCurrentReport(type, 'Ticket Report', columns, rows);
            break;
            
        case 'sla':
            report = await ReportService.generateSlaReport(params);
            
            // Show summary stats
            showSlaSummary(report);
            
            columns = ['Ticket', 'Priority', 'Status', 'Response SLA', 'Resolution SLA', 'Response By', 'Resolution By'];
            rows = report.details.map(d => ({
                'Ticket': d.ticketNumber,
                'Priority': d.priority,
                'Status': d.status,
                'Response SLA': formatSlaStatus(d.responseSlaMet),
                'Resolution SLA': formatSlaStatus(d.resolutionSlaMet),
                'Response By': d.firstResponseAt ? formatDate(d.firstResponseAt) : '-',
                'Resolution By': d.resolvedAt ? formatDate(d.resolvedAt) : '-'
            }));
            setCurrentReport(type, 'SLA Compliance Report', columns, rows);
            break;
            
        case 'assets':
            report = await ReportService.generateAssetReport(params);
            
            // Show asset stats
            showAssetSummary(report);
            
            columns = ['Asset Number', 'Name', 'Type', 'Status', 'Health', 'Location', 'Assigned To'];
            rows = report.assets.map(a => ({
                'Asset Number': a.assetNumber,
                'Name': a.name,
                'Type': a.assetType,
                'Status': a.status,
                'Health': a.healthStatus,
                'Location': a.location || '-',
                'Assigned To': a.assignedTo || '-'
            }));
            setCurrentReport(type, 'Asset Inventory Report', columns, rows);
            break;
    }
    
    // Render the report table
    renderReportTable(columns, rows);
    
    // Show export buttons
    showExportButtons(type);
}

// ==================== Export Report ====================

async function exportReport(type, format, params) {
    switch (type) {
        case 'tickets':
            await ReportService.exportTicketReport(format, params);
            break;
        case 'sla':
            await ReportService.exportSlaReport(format, params);
            break;
        case 'assets':
            await ReportService.exportAssetReport(format, params);
            break;
    }
}

// ==================== Summary Display ====================

function showSlaSummary(report) {
    const reportOutput = document.getElementById('report-output');
    if (!reportOutput) return;
    
    const summaryHtml = `
        <div class="report-summary sla-summary">
            <h4>SLA Compliance Summary</h4>
            <div class="summary-stats">
                <div class="stat-card">
                    <div class="stat-value">${report.stats.total}</div>
                    <div class="stat-label">Total Tickets</div>
                </div>
                <div class="stat-card ${report.responseComplianceRate >= 80 ? 'success' : 'warning'}">
                    <div class="stat-value">${report.responseComplianceRate.toFixed(1)}%</div>
                    <div class="stat-label">Response SLA</div>
                </div>
                <div class="stat-card ${report.resolutionComplianceRate >= 80 ? 'success' : 'warning'}">
                    <div class="stat-value">${report.resolutionComplianceRate.toFixed(1)}%</div>
                    <div class="stat-label">Resolution SLA</div>
                </div>
                <div class="stat-card">
                    <div class="stat-value">${report.overallComplianceRate.toFixed(1)}%</div>
                    <div class="stat-label">Overall</div>
                </div>
            </div>
            <div class="sla-details">
                <div class="sla-row">
                    <span class="sla-label">Response SLA Met:</span>
                    <span class="sla-value success">${report.stats.responseMet}</span>
                </div>
                <div class="sla-row">
                    <span class="sla-label">Response SLA Breached:</span>
                    <span class="sla-value danger">${report.stats.responseBreached}</span>
                </div>
                <div class="sla-row">
                    <span class="sla-label">Resolution SLA Met:</span>
                    <span class="sla-value success">${report.stats.resolutionMet}</span>
                </div>
                <div class="sla-row">
                    <span class="sla-label">Resolution SLA Breached:</span>
                    <span class="sla-value danger">${report.stats.resolutionBreached}</span>
                </div>
            </div>
        </div>
    `;
    
    reportOutput.innerHTML = summaryHtml;
}

function showAssetSummary(report) {
    const reportOutput = document.getElementById('report-output');
    if (!reportOutput) return;
    
    const summaryHtml = `
        <div class="report-summary asset-summary">
            <h4>Asset Inventory Summary</h4>
            <div class="summary-stats">
                <div class="stat-card">
                    <div class="stat-value">${report.stats.total}</div>
                    <div class="stat-label">Total Assets</div>
                </div>
                <div class="stat-card ${report.healthRate >= 80 ? 'success' : 'warning'}">
                    <div class="stat-value">${report.healthRate.toFixed(1)}%</div>
                    <div class="stat-label">Health Rate</div>
                </div>
                <div class="stat-card">
                    <div class="stat-value">${report.activeRate.toFixed(1)}%</div>
                    <div class="stat-label">Active Rate</div>
                </div>
            </div>
            <div class="asset-breakdown">
                <div class="breakdown-section">
                    <h5>By Health Status</h5>
                    ${Object.entries(report.stats.byHealth).map(([k, v]) => 
                        `<div class="breakdown-row"><span>${k}</span><span>${v}</span></div>`
                    ).join('')}
                </div>
                <div class="breakdown-section">
                    <h5>By Type</h5>
                    ${Object.entries(report.stats.byType).map(([k, v]) => 
                        `<div class="breakdown-row"><span>${k}</span><span>${v}</span></div>`
                    ).join('')}
                </div>
            </div>
        </div>
    `;
    
    reportOutput.innerHTML = summaryHtml;
}

// ==================== Export Buttons ====================

function showExportButtons(type) {
    const reportOutput = document.getElementById('report-output');
    if (!reportOutput) return;
    
    const buttonsHtml = `
        <div class="report-actions">
            <button class="btn secondary" onclick="ReportModule.exportCurrentReport('csv')">
                📥 Tải CSV
            </button>
            <button class="btn secondary" onclick="ReportModule.exportCurrentReport('excel')">
                📊 Tải Excel
            </button>
        </div>
    `;
    
    reportOutput.insertAdjacentHTML('beforeend', buttonsHtml);
}

// ==================== Export Current Report ====================

export const exportCurrentReport = async (format) => {
    if (!currentReport || !currentReportType) {
        Toast.show('Hãy tạo báo cáo trước khi tải xuống.', 'warning');
        return;
    }
    
    try {
        await exportReport(currentReportType, format, {});
        Toast.show('Báo cáo đã được tải xuống!', 'success');
    } catch (error) {
        console.error('Export error:', error);
        Toast.show('Lỗi khi xuất báo cáo: ' + error.message, 'error');
    }
};

// ==================== Button State Management ====================

export const setActiveReportButton = (button) => {
    const reportButtons = [
        document.getElementById('engineer-report-btn'),
        document.getElementById('requester-report-btn'),
        document.getElementById('backlog-report-btn'),
        document.getElementById('sla-report-btn')
    ].filter(Boolean);
    
    reportButtons.forEach((btn) => btn.classList.remove('active'));
    if (button) {
        button.classList.add('active');
    }
};

// ==================== Legacy CSV Download ====================

export const downloadReportCsv = () => {
    if (!currentReport || !currentReport.rows || !currentReport.rows.length) {
        Toast.show('Hãy chạy báo cáo trước khi tải xuống.', 'warning');
        return;
    }
    
    const escapeCsv = (value) => {
        if (value === null || value === undefined) return '';
        const str = String(value);
        if (/[",\n]/.test(str)) {
            return `"${str.replace(/"/g, '""')}"`;
        }
        return str;
    };
    
    const lines = [];
    lines.push(currentReport.columns.map(escapeCsv).join(','));
    currentReport.rows.forEach((row) => {
        const line = currentReport.columns.map((col) => escapeCsv(row[col]));
        lines.push(line.join(','));
    });
    
    const blob = new Blob([lines.join('\n')], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    const stamp = new Date().toISOString().slice(0, 10);
    link.href = url;
    link.download = `${currentReport.title || 'report'}-${stamp}.csv`;
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
};

// ==================== Helper Functions ====================

function formatDate(dateStr) {
    if (!dateStr) return '-';
    const date = new Date(dateStr);
    return date.toLocaleDateString('vi-VN', {
        day: '2-digit',
        month: '2-digit',
        year: 'numeric'
    });
}

function formatSlaStatus(status) {
    if (status === true) return '✓ MET';
    if (status === false) return '✗ BREACHED';
    return '⏳ PENDING';
}

// ==================== Module Registration ====================

// Make functions globally accessible for onclick handlers
window.ReportModule = {
    showNewReportDialog,
    exportCurrentReport,
    downloadReportCsv
};
