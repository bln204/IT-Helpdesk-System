/**
 * Report Service - API calls for report generation and export
 */
import { tokenKey } from '../config/constants.js';

const API_BASE = '/api/reports';

const ReportService = {
    
    // ==================== Report Generation ====================

    /**
     * Generate ticket report
     * @param {Object} params - { startDate, endDate, days }
     */
    async generateTicketReport(params = {}) {
        const queryParams = new URLSearchParams();
        if (params.startDate) queryParams.append('startDate', params.startDate);
        if (params.endDate) queryParams.append('endDate', params.endDate);
        if (params.days) queryParams.append('days', params.days);
        
        const token = localStorage.getItem(tokenKey);
        const response = await fetch(`${API_BASE}/tickets`, {
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${token}`,
                'Content-Type': 'application/json'
            },
            credentials: 'same-origin'
        });
        
        if (!response.ok) {
            throw new Error('Failed to generate ticket report');
        }
        
        return await response.json();
    },

    /**
     * Generate SLA compliance report
     * @param {Object} params - { startDate, endDate, days }
     */
    async generateSlaReport(params = {}) {
        const token = localStorage.getItem(tokenKey);
        const response = await fetch(`${API_BASE}/sla`, {
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${token}`,
                'Content-Type': 'application/json'
            },
            credentials: 'same-origin'
        });
        
        if (!response.ok) {
            throw new Error('Failed to generate SLA compliance report');
        }
        
        return await response.json();
    },

    /**
     * Generate asset report
     * @param {Object} params - { startDate, endDate, days }
     */
    async generateAssetReport(params = {}) {
        const token = localStorage.getItem(tokenKey);
        const response = await fetch(`${API_BASE}/assets`, {
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${token}`,
                'Content-Type': 'application/json'
            },
            credentials: 'same-origin'
        });
        
        if (!response.ok) {
            throw new Error('Failed to generate asset report');
        }
        
        return await response.json();
    },

    // ==================== Report Export ====================

    /**
     * Export ticket report
     * @param {string} format - 'csv' or 'excel'
     * @param {Object} params - { startDate, endDate, days }
     */
    async exportTicketReport(format = 'csv', params = {}) {
        const queryParams = new URLSearchParams({ format });
        if (params.startDate) queryParams.append('startDate', params.startDate);
        if (params.endDate) queryParams.append('endDate', params.endDate);
        if (params.days) queryParams.append('days', params.days);
        
        const token = localStorage.getItem(tokenKey);
        const response = await fetch(`${API_BASE}/tickets/export?${queryParams}`, {
            method: 'GET',
            headers: {
                'Authorization': `Bearer ${token}`
            },
            credentials: 'same-origin'
        });
        
        if (!response.ok) {
            throw new Error('Failed to export ticket report');
        }
        
        const blob = await response.blob();
        const filename = `ticket-report.${format === 'excel' ? 'xlsx' : 'csv'}`;
        this.downloadBlob(blob, filename);
    },

    /**
     * Export SLA report
     * @param {string} format - 'csv' or 'excel'
     * @param {Object} params - { startDate, endDate, days }
     */
    async exportSlaReport(format = 'csv', params = {}) {
        const queryParams = new URLSearchParams({ format });
        if (params.startDate) queryParams.append('startDate', params.startDate);
        if (params.endDate) queryParams.append('endDate', params.endDate);
        if (params.days) queryParams.append('days', params.days);
        
        const token = localStorage.getItem(tokenKey);
        const response = await fetch(`${API_BASE}/sla/export?${queryParams}`, {
            method: 'GET',
            headers: {
                'Authorization': `Bearer ${token}`
            },
            credentials: 'same-origin'
        });
        
        if (!response.ok) {
            throw new Error('Failed to export SLA report');
        }
        
        const blob = await response.blob();
        const filename = `sla-compliance-report.${format === 'excel' ? 'xlsx' : 'csv'}`;
        this.downloadBlob(blob, filename);
    },

    /**
     * Export asset report
     * @param {string} format - 'csv' or 'excel'
     * @param {Object} params - { startDate, endDate, days }
     */
    async exportAssetReport(format = 'csv', params = {}) {
        const queryParams = new URLSearchParams({ format });
        if (params.startDate) queryParams.append('startDate', params.startDate);
        if (params.endDate) queryParams.append('endDate', params.endDate);
        if (params.days) queryParams.append('days', params.days);
        
        const token = localStorage.getItem(tokenKey);
        const response = await fetch(`${API_BASE}/assets/export?${queryParams}`, {
            method: 'GET',
            headers: {
                'Authorization': `Bearer ${token}`
            },
            credentials: 'same-origin'
        });
        
        if (!response.ok) {
            throw new Error('Failed to export asset report');
        }
        
        const blob = await response.blob();
        const filename = `asset-inventory-report.${format === 'excel' ? 'xlsx' : 'csv'}`;
        this.downloadBlob(blob, filename);
    },

    // ==================== Report Templates ====================

    /**
     * Get available report templates
     */
    async getReportTemplates() {
        const token = localStorage.getItem(tokenKey);
        const response = await fetch(`${API_BASE}/templates`, {
            headers: {
                'Authorization': `Bearer ${token}`,
                'Content-Type': 'application/json'
            },
            credentials: 'same-origin'
        });
        
        if (!response.ok) {
            throw new Error('Failed to get report templates');
        }
        
        return await response.json();
    },

    // ==================== Helper Methods ====================

    /**
     * Download blob as file
     */
    downloadBlob(blob, filename) {
        const url = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = filename;
        document.body.appendChild(link);
        link.click();
        link.remove();
        window.URL.revokeObjectURL(url);
    }
};

export default ReportService;
