/**
 * IT Staff Dashboard Module
 * Hiển thị dashboard tổng quan cho IT staff
 */

const DashboardModule = (() => {
    'use strict';
    
    // DOM Elements
    let dashboardContainer = null;
    
    // Helper function for authenticated fetch - read token fresh each time
    const authFetch = async (url, options = {}) => {
        const token = localStorage.getItem('ticketing.jwt');
        const headers = {
            'Authorization': `Bearer ${token || ''}`,
            'Content-Type': 'application/json'
        };
        return fetch(url, { ...options, headers: { ...headers, ...options.headers } });
    };
    
    // State
    let currentTab = 'overview';
    
    // ============ Initialize ============
    
    function init() {
        dashboardContainer = document.getElementById('dashboard-content');
        if (!dashboardContainer) return;
        
        renderDashboard();
        
        // Attach event listeners for quick action buttons
        const createBtn = document.getElementById('dashboard-create-ticket');
        const refreshBtn = document.getElementById('dashboard-refresh');
        const unassignedLink = document.getElementById('dashboard-unassigned-link');
        
        if (createBtn) {
            createBtn.addEventListener('click', () => {
                const createModal = document.getElementById('create-modal');
                if (createModal) createModal.classList.remove('hidden');
            });
        }
        
        if (refreshBtn) {
            refreshBtn.addEventListener('click', () => {
                loadDashboardData();
            });
        }
        
        if (unassignedLink) {
            unassignedLink.addEventListener('click', (e) => {
                e.preventDefault();
                // Navigate to tickets page with unassigned filter
                window.location.hash = '#/tickets';
                // Store filter in sessionStorage for ticketService to pick up
                sessionStorage.setItem('ticketing.pendingFilter', 'unassigned');
            });
        }
        
        // Use event delegation on dashboard container for ticket/activity items
        dashboardContainer.addEventListener('click', (e) => {
            const ticketItem = e.target.closest('.dashboard-ticket-item[data-ticket-id]');
            const activityItem = e.target.closest('.activity-item[data-ticket-id]');
            
            if (ticketItem) {
                const ticketId = ticketItem.dataset.ticketId;
                navigateToTicket(ticketId);
            } else if (activityItem) {
                const ticketId = activityItem.dataset.ticketId;
                navigateToTicket(ticketId);
            }
        });
        
        loadDashboardData();
    }
    
    // ============ Render ============
    
    function renderDashboard() {
        dashboardContainer.innerHTML = `
            <div class="dashboard-container">
                <div class="dashboard-header">
                    <h1>IT Dashboard</h1>
                    <p>Xem tổng quan công việc và tình trạng ticket</p>
                </div>
                
                <!-- Stats Grid -->
                <div class="dashboard-stats" id="dashboard-stats">
                    <div class="stat-card">
                        <div class="stat-icon teal">
                            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                                <path d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2"/>
                            </svg>
                        </div>
                        <div class="stat-content">
                            <label>Đang xử lý</label>
                            <strong id="stat-my-tickets">--</strong>
                        </div>
                    </div>
                    
                    <div class="stat-card">
                        <div class="stat-icon accent">
                            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                                <circle cx="12" cy="12" r="10"/>
                                <path d="M12 6v6l4 2"/>
                            </svg>
                        </div>
                        <div class="stat-content">
                            <label>Ticket mở</label>
                            <strong id="stat-open">--</strong>
                        </div>
                    </div>
                    
                    <div class="stat-card">
                        <div class="stat-icon warning">
                            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                                <circle cx="12" cy="12" r="10"/>
                                <line x1="12" y1="8" x2="12" y2="12"/>
                                <line x1="12" y1="16" x2="12.01" y2="16"/>
                            </svg>
                        </div>
                        <div class="stat-content">
                            <label>Chưa gán</label>
                            <strong id="stat-unassigned">--</strong>
                        </div>
                    </div>
                    
                    <div class="stat-card">
                        <div class="stat-icon danger">
                            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                                <path d="M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z"/>
                                <line x1="12" y1="9" x2="12" y2="13"/>
                                <line x1="12" y1="17" x2="12.01" y2="17"/>
                            </svg>
                        </div>
                        <div class="stat-content">
                            <label>SLA Breach</label>
                            <strong id="stat-sla-breached">--</strong>
                        </div>
                    </div>
                </div>
                
                <!-- Main Dashboard Grid -->
                <div class="dashboard-grid">
                    <!-- Main Content -->
                    <div class="dashboard-main">
                        <!-- Quick Actions -->
                        <div class="dashboard-section">
                            <header>
                                <h3>Thao tác nhanh</h3>
                            </header>
                            <div class="section-content">
                                <div class="quick-actions">
                                    <button class="quick-action-btn" id="dashboard-create-ticket">
                                        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                                            <line x1="12" y1="5" x2="12" y2="19"/>
                                            <line x1="5" y1="12" x2="19" y2="12"/>
                                        </svg>
                                        Tạo Ticket mới
                                    </button>
                                    <button class="quick-action-btn" id="dashboard-refresh">
                                        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                                            <polyline points="23 4 23 10 17 10"/>
                                            <path d="M20.49 15a9 9 0 11-2.12-9.36L23 10"/>
                                        </svg>
                                        Làm mới
                                    </button>
                                    <a href="#/tickets" class="quick-action-btn" id="dashboard-unassigned-link">
                                        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                                            <path d="M22 11.08V12a10 10 0 11-5.93-9.14"/>
                                            <polyline points="22 4 12 14.01 9 11.01"/>
                                        </svg>
                                        Xem Ticket chưa gán
                                    </a>
                                </div>
                            </div>
                        </div>
                        
                        <!-- My Tickets -->
                        <div class="dashboard-section">
                            <header>
                                <h3>Ticket của tôi</h3>
                                <span class="section-meta" id="my-tickets-count">-- tickets</span>
                            </header>
                            <ul class="dashboard-ticket-list" id="my-tickets-list">
                                <li class="dashboard-loading">Đang tải...</li>
                            </ul>
                        </div>
                        
                        <!-- Urgent Tickets -->
                        <div class="dashboard-section">
                            <header>
                                <h3>Cần xử lý gấp</h3>
                                <span class="section-meta" id="urgent-tickets-count">-- tickets</span>
                            </header>
                            <ul class="dashboard-ticket-list" id="urgent-tickets-list">
                                <li class="dashboard-loading">Đang tải...</li>
                            </ul>
                        </div>
                    </div>
                    
                    <!-- Sidebar -->
                    <div class="dashboard-sidebar">
                        <!-- SLA Summary -->
                        <div class="dashboard-section">
                            <header>
                                <h3>SLA Summary</h3>
                            </header>
                            <div class="section-content">
                                <div class="sla-summary">
                                    <div class="sla-metric" id="sla-response">
                                        <label>Response SLA</label>
                                        <strong>--%</strong>
                                    </div>
                                    <div class="sla-metric" id="sla-resolution">
                                        <label>Resolution SLA</label>
                                        <strong>--%</strong>
                                    </div>
                                    <div class="sla-metric" id="sla-open">
                                        <label>Open Tickets</label>
                                        <strong>--</strong>
                                    </div>
                                    <div class="sla-metric" id="sla-breached">
                                        <label>Breached</label>
                                        <strong class="text-danger">--</strong>
                                    </div>
                                </div>
                            </div>
                        </div>
                        
                        <!-- Team Workload -->
                        <div class="dashboard-section">
                            <header>
                                <h3>Team Workload</h3>
                            </header>
                            <div class="section-content">
                                <ul class="team-workload-list" id="team-workload-list">
                                    <li class="dashboard-loading">Đang tải...</li>
                                </ul>
                            </div>
                        </div>
                        
                        <!-- Recent Activity -->
                        <div class="dashboard-section">
                            <header>
                                <h3>Hoạt động gần đây</h3>
                            </header>
                            <div class="section-content">
                                <ul class="activity-feed" id="activity-feed">
                                    <li class="dashboard-loading">Đang tải...</li>
                                </ul>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        `;
    }
    
    // ============ Data Loading ============
    
    async function loadDashboardData() {
        try {
            await Promise.all([
                loadOverview(),
                loadMyTickets(),
                loadUrgentTickets(),
                loadSlaSummary(),
                loadTeamWorkload(),
                loadRecentActivity()
            ]);
        } catch (error) {
            console.error('Dashboard load error:', error);
            showError('Không thể tải dữ liệu dashboard');
        }
    }
    
    async function loadOverview() {
        try {
            const response = await authFetch('/api/dashboard/overview');
            if (!response.ok) throw new Error('Failed to load overview: ' + response.status);
            
            const data = await response.json();
            console.log('Dashboard overview:', data);
            
            const myTicketsEl = document.getElementById('stat-my-tickets');
            const statOpenEl = document.getElementById('stat-open');
            const statUnassignedEl = document.getElementById('stat-unassigned');
            const statSlaBreachedEl = document.getElementById('stat-sla-breached');
            
            if (myTicketsEl) myTicketsEl.textContent = data.myAssignedTickets ?? 0;
            if (statOpenEl) statOpenEl.textContent = data.openTickets ?? 0;
            if (statUnassignedEl) statUnassignedEl.textContent = data.unassignedTickets ?? 0;
            if (statSlaBreachedEl) statSlaBreachedEl.textContent = (data.slaBreached ?? 0) + (data.responseSlaBreached ?? 0);
        } catch (error) {
            console.error('Overview load error:', error);
            const el = document.getElementById('stat-my-tickets');
            if (el) el.textContent = '?';
        }
    }
    
    async function loadMyTickets() {
        try {
            const response = await authFetch('/api/dashboard/my-tickets?limit=5');
            if (!response.ok) throw new Error('Failed to load my tickets');
            
            const tickets = await response.json();
            const listEl = document.getElementById('my-tickets-list');
            const countEl = document.getElementById('my-tickets-count');
            
            countEl.textContent = `${tickets.length} tickets`;
            
            if (tickets.length === 0) {
                listEl.innerHTML = `
                    <li class="dashboard-empty">
                        <p>Không có ticket nào được gán cho bạn</p>
                    </li>
                `;
                return;
            }
            
            listEl.innerHTML = tickets.map(ticket => renderTicketItem(ticket)).join('');
        } catch (error) {
            console.error('My tickets load error:', error);
        }
    }
    
    async function loadUrgentTickets() {
        try {
            const response = await authFetch('/api/dashboard/urgent-tickets?limit=5');
            if (!response.ok) throw new Error('Failed to load urgent tickets');
            
            const tickets = await response.json();
            const listEl = document.getElementById('urgent-tickets-list');
            const countEl = document.getElementById('urgent-tickets-count');
            
            countEl.textContent = `${tickets.length} tickets`;
            
            if (tickets.length === 0) {
                listEl.innerHTML = `
                    <li class="dashboard-empty">
                        <p>Không có ticket khẩn cấp</p>
                    </li>
                `;
                return;
            }
            
            listEl.innerHTML = tickets.map(ticket => renderTicketItem(ticket, true)).join('');
        } catch (error) {
            console.error('Urgent tickets load error:', error);
        }
    }
    
    async function loadSlaSummary() {
        try {
            const response = await authFetch('/api/dashboard/sla-summary');
            if (!response.ok) throw new Error('Failed to load SLA summary');
            
            const data = await response.json();
            
            const responseEl = document.getElementById('sla-response');
            const resolutionEl = document.getElementById('sla-resolution');
            const openEl = document.getElementById('sla-open');
            const breachedEl = document.getElementById('sla-breached');
            
            responseEl.querySelector('strong').textContent = `${data.responseSlaCompliance}%`;
            resolutionEl.querySelector('strong').textContent = `${data.resolutionSlaCompliance}%`;
            openEl.querySelector('strong').textContent = data.openTickets;
            breachedEl.querySelector('strong').textContent = data.responseSlaBreached + data.resolutionSlaBreached;
            
            // Color coding
            responseEl.className = `sla-metric ${data.responseSlaCompliance < 80 ? 'danger' : data.responseSlaCompliance < 95 ? 'warning' : ''}`;
            resolutionEl.className = `sla-metric ${data.resolutionSlaCompliance < 80 ? 'danger' : data.resolutionSlaCompliance < 95 ? 'warning' : ''}`;
        } catch (error) {
            console.error('SLA summary load error:', error);
        }
    }
    
    async function loadTeamWorkload() {
        try {
            // Get user's teams first
            const teamsResponse = await authFetch('/api/teams/user/me');
            if (!teamsResponse.ok) {
                document.getElementById('team-workload-list').innerHTML = 
                    '<li class="dashboard-empty"><p>Không có team</p></li>';
                return;
            }
            
            const teams = await teamsResponse.json();
            if (!teams || teams.length === 0) {
                document.getElementById('team-workload-list').innerHTML = 
                    '<li class="dashboard-empty"><p>Không có team</p></li>';
                return;
            }
            
            // Get first team ID from response
            let teamId;
            if (teams[0].teamId) {
                teamId = teams[0].teamId;
            } else if (teams[0].id) {
                teamId = teams[0].id;
            } else if (teams[0].team?.id) {
                teamId = teams[0].team.id;
            }
            
            if (!teamId) {
                document.getElementById('team-workload-list').innerHTML = 
                    '<li class="dashboard-empty"><p>Không tìm thấy team</p></li>';
                return;
            }
            
            const response = await authFetch(`/api/dashboard/team-workload/${teamId}`);
            if (!response.ok) throw new Error('Failed to load team workload');
            
            const workload = await response.json();
            const listEl = document.getElementById('team-workload-list');
            
            if (workload.length === 0) {
                listEl.innerHTML = '<li class="dashboard-empty"><p>Không có thành viên</p></li>';
                return;
            }
            
            listEl.innerHTML = workload.map(member => `
                <li class="team-member-item">
                    <div class="member-avatar">${getInitials(member.displayName || member.username)}</div>
                    <div class="member-info">
                        <div class="member-name">${member.displayName || member.username}</div>
                        <div class="member-role">${member.role || 'Member'}</div>
                    </div>
                    <div class="member-workload">
                        <div class="member-ticket-count">${member.activeTickets}</div>
                        <div class="member-ticket-label">tickets</div>
                        <div class="workload-bar">
                            <div class="workload-bar-fill ${getWorkloadLevel(member.activeTickets)}" 
                                 style="width: ${Math.min(100, member.activeTickets * 10)}%"></div>
                        </div>
                    </div>
                </li>
            `).join('');
        } catch (error) {
            console.error('Team workload load error:', error);
        }
    }
    
    async function loadRecentActivity() {
        try {
            const response = await authFetch('/api/dashboard/recent-activity?limit=5');
            if (!response.ok) throw new Error('Failed to load recent activity');
            
            const activities = await response.json();
            const listEl = document.getElementById('activity-feed');
            
            if (activities.length === 0) {
                listEl.innerHTML = '<li class="dashboard-empty"><p>Không có hoạt động gần đây</p></li>';
                return;
            }
            
            listEl.innerHTML = activities.map(activity => `
                <li class="activity-item" data-ticket-id="${activity.ticketId}">
                    <div class="activity-icon">📝</div>
                    <div class="activity-content">
                        <div class="activity-text">
                            <strong>${activity.ticketNumber}</strong> - ${truncate(activity.title, 40)}
                        </div>
                        <div class="activity-time">${formatTimeAgo(activity.createdAt)}</div>
                    </div>
                </li>
            `).join('');
        } catch (error) {
            console.error('Recent activity load error:', error);
        }
    }
    
    // ============ Render Helpers ============
    
    function renderTicketItem(ticket, isUrgent = false) {
        const priorityClass = ticket.priority?.toLowerCase() || 'medium';
        const statusClass = getStatusClass(ticket.status);
        const slaWarning = ticket.slaResponseAt && !ticket.firstResponseAt && isUrgent;
        
        return `
            <li class="dashboard-ticket-item" data-ticket-id="${ticket.id}">
                <div class="ticket-priority-dot ${priorityClass}"></div>
                <div class="ticket-info">
                    <div class="ticket-info-header">
                        <span class="ticket-number">${ticket.ticketNumber}</span>
                        <span class="ticket-status-badge ${statusClass}">${formatStatus(ticket.status)}</span>
                        ${slaWarning ? '<span class="sla-indicator danger">⚠️ SLA</span>' : ''}
                    </div>
                    <div class="ticket-title">${ticket.title}</div>
                    <div class="ticket-meta">
                        <span>📅 ${formatDate(ticket.createdAt)}</span>
                        <span>👤 ${ticket.requesterName || ticket.requesterUsername || 'N/A'}</span>
                    </div>
                </div>
            </li>
        `;
    }
    
    function navigateToTicket(ticketId) {
        // Navigate to tickets view
        window.location.hash = '#/tickets';
        
        // Store ticket ID to select after view loads
        sessionStorage.setItem('ticketing.selectedTicketId', ticketId);
        
        // Select ticket after a short delay to allow view to render
        setTimeout(async () => {
            try {
                const { selectTicket } = await import('../services/ticketService.js');
                await selectTicket({ id: parseInt(ticketId) });
            } catch (err) {
                console.error('Error selecting ticket:', err);
            }
        }, 100);
    }
    
    // ============ Utilities ============
    
    function getStatusClass(status) {
        const statusMap = {
            'NEW': 'new',
            'ASSIGNED': 'in-progress',
            'IN_PROGRESS': 'in-progress',
            'WAITING_FOR_USER': 'blocked',
            'RESOLVED': 'resolved',
            'CLOSED': 'closed',
            'ESCALATED': 'blocked'
        };
        return statusMap[status] || '';
    }
    
    function formatStatus(status) {
        const statusMap = {
            'NEW': 'Mới',
            'ASSIGNED': 'Đã gán',
            'IN_PROGRESS': 'Đang xử lý',
            'WAITING_FOR_USER': 'Chờ thông tin',
            'RESOLVED': 'Đã giải quyết',
            'CLOSED': 'Đã đóng',
            'ESCALATED': 'Escalated'
        };
        return statusMap[status] || status;
    }
    
    function getWorkloadLevel(tickets) {
        if (tickets <= 3) return 'low';
        if (tickets <= 7) return 'medium';
        return 'high';
    }
    
    function getInitials(name) {
        if (!name) return '?';
        const parts = name.split(' ');
        if (parts.length >= 2) {
            return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
        }
        return name.substring(0, 2).toUpperCase();
    }
    
    function formatDate(dateStr) {
        if (!dateStr) return '';
        const date = new Date(dateStr);
        return date.toLocaleDateString('vi-VN', { day: '2-digit', month: 'short' });
    }
    
    function formatTimeAgo(dateStr) {
        if (!dateStr) return '';
        const date = new Date(dateStr);
        const now = new Date();
        const diff = now - date;
        
        const minutes = Math.floor(diff / 60000);
        const hours = Math.floor(diff / 3600000);
        const days = Math.floor(diff / 86400000);
        
        if (minutes < 60) return `${minutes} phút trước`;
        if (hours < 24) return `${hours} giờ trước`;
        if (days < 7) return `${days} ngày trước`;
        return formatDate(dateStr);
    }
    
    function truncate(str, len) {
        if (!str) return '';
        return str.length > len ? str.substring(0, len) + '...' : str;
    }
    
    function showError(message) {
        // Simple error display
        const container = document.querySelector('.dashboard-container');
        if (container) {
            container.insertAdjacentHTML('afterbegin', `
                <div class="user-approval-info rejected" style="margin-bottom: 16px;">
                    <div class="info-value">${message}</div>
                </div>
            `);
        }
    }
    
    // ============ Public API ============
    
    return {
        init,
        refreshData: loadDashboardData,
        showCreateTicket: () => {
            if (typeof TicketsModule !== 'undefined' && TicketsModule.showCreateModal) {
                TicketsModule.showCreateModal();
            } else {
                window.location.href = '/tickets?action=create';
            }
        }
    };
})();

// Export for ES modules
export { DashboardModule };

// Auto-initialize when DOM is ready (for direct script loading or as fallback)
document.addEventListener('DOMContentLoaded', () => {
    if (!window.DashboardModule) {
        window.DashboardModule = DashboardModule;
    }
    if (document.getElementById('dashboard-content')) {
        DashboardModule.init();
    }
});
