/**
 * SLA Timer Module - Hiển thị countdown timer và SLA status
 */
const SlaTimer = (() => {
    'use strict';

    // Timer update interval (60 seconds)
    const UPDATE_INTERVAL = 60000;
    
    // Thresholds for warning colors
    const THRESHOLDS = {
        BREACHED: 0,           // Red - SLA already breached
        CRITICAL: 0.25,        // Orange - Less than 25% time remaining
        WARNING: 0.50,         // Yellow - Less than 50% time remaining
        OK: 1.0                // Green - Plenty of time remaining
    };

    let timerInterval = null;
    let slaTimerCallbacks = [];

    // ==================== Initialization ====================

    function init() {
        // Start global timer updates
        startGlobalTimer();
    }

    function destroy() {
        stopGlobalTimer();
    }

    // ==================== Timer Control ====================

    function startGlobalTimer() {
        if (timerInterval) return;
        timerInterval = setInterval(updateAllTimers, UPDATE_INTERVAL);
    }

    function stopGlobalTimer() {
        if (timerInterval) {
            clearInterval(timerInterval);
            timerInterval = null;
        }
    }

    function updateAllTimers() {
        // Update all SLA timer elements on the page
        document.querySelectorAll('[data-sla-timer]').forEach(el => {
            const deadline = el.dataset.slaDeadline;
            const type = el.dataset.slaType; // 'response' or 'resolution'
            if (deadline) {
                el.innerHTML = formatTimeRemaining(new Date(deadline), type, deadline);
            }
        });

        // Update SLA badges
        document.querySelectorAll('[data-sla-status]').forEach(el => {
            const ticketId = el.dataset.ticketId;
            const responseAt = el.dataset.slaResponseAt;
            const resolutionAt = el.dataset.slaResolutionAt;
            const firstResponseAt = el.dataset.firstResponseAt;
            const resolvedAt = el.dataset.resolvedAt;
            const status = el.dataset.resolvedAt ? 'resolved' : getSlaStatus(responseAt, resolutionAt, firstResponseAt);
            el.innerHTML = renderSlaStatusBadge(status, el.dataset);
        });

        // Call registered callbacks
        slaTimerCallbacks.forEach(cb => {
            try {
                cb();
            } catch (e) {
                console.error('SLA timer callback error:', e);
            }
        });
    }

    // ==================== SLA Status Calculation ====================

    function getSlaStatus(responseAt, resolutionAt, firstResponseAt, resolvedAt) {
        const now = new Date();
        
        // If resolved, show green checkmark
        if (resolvedAt) {
            return 'resolved';
        }

        // Check resolution SLA first (more important)
        if (resolutionAt) {
            const resolutionDeadline = new Date(resolutionAt);
            if (now > resolutionDeadline) {
                return 'breached-resolution';
            }
        }

        // Check response SLA
        if (responseAt && !firstResponseAt) {
            const responseDeadline = new Date(responseAt);
            if (now > responseDeadline) {
                return 'breached-response';
            }
        }

        // Calculate time remaining percentage
        const percentage = calculateTimeRemainingPercentage(responseAt, resolutionAt, firstResponseAt);
        
        if (percentage <= THRESHOLDS.CRITICAL) {
            return 'critical';
        } else if (percentage <= THRESHOLDS.WARNING) {
            return 'warning';
        }
        
        return 'ok';
    }

    function calculateTimeRemainingPercentage(responseAt, resolutionAt, firstResponseAt) {
        const now = new Date();
        
        // If already responded, check resolution SLA
        if (firstResponseAt) {
            if (resolutionAt) {
                const end = new Date(resolutionAt);
                const start = new Date(firstResponseAt);
                const total = end - start;
                const remaining = end - now;
                return total > 0 ? remaining / total : 0;
            }
            return 1; // No resolution deadline
        }

        // Not responded yet, check response SLA
        if (responseAt) {
            const deadline = new Date(responseAt);
            const remaining = deadline - now;
            if (remaining <= 0) return 0;
            // Assume 2 hours total for percentage calculation
            const estimatedTotal = 2 * 60 * 60 * 1000;
            return Math.min(remaining / estimatedTotal, 1);
        }

        return 1; // No SLA data
    }

    // ==================== Time Formatting ====================

    function formatTimeRemaining(deadline, type = 'resolution', originalDeadline) {
        if (!deadline || !(deadline instanceof Date) || isNaN(deadline.getTime())) {
            return '<span class="sla-timer unknown">—</span>';
        }

        const now = new Date();
        const diffMs = deadline - now;
        const isOverdue = diffMs < 0;
        const absDiffMs = Math.abs(diffMs);

        // Format time
        const timeStr = formatDuration(absDiffMs);
        
        // Determine status class
        let statusClass = 'sla-timer ok';
        if (isOverdue) {
            statusClass = 'sla-timer breached';
        } else if (diffMs < 15 * 60 * 1000) { // < 15 minutes
            statusClass = 'sla-timer critical';
        } else if (diffMs < 60 * 60 * 1000) { // < 1 hour
            statusClass = 'sla-timer warning';
        }

        // Format label
        const label = type === 'response' ? 'Phản hồi' : 'Giải quyết';
        const prefix = isOverdue ? 'Quá hạn ' : '';

        return `
            <div class="${statusClass}">
                <span class="sla-timer-label">${label}</span>
                <span class="sla-timer-value">${prefix}${timeStr}</span>
            </div>
        `;
    }

    function formatDuration(ms) {
        if (ms < 0) ms = -ms;
        
        const seconds = Math.floor(ms / 1000);
        const minutes = Math.floor(seconds / 60);
        const hours = Math.floor(minutes / 60);
        const days = Math.floor(hours / 24);

        if (days > 0) {
            const remainingHours = hours % 24;
            return `${days}d ${remainingHours}h`;
        }
        if (hours > 0) {
            const remainingMinutes = minutes % 60;
            return `${hours}h ${remainingMinutes}p`;
        }
        if (minutes > 0) {
            return `${minutes}p`;
        }
        return '<1p';
    }

    // ==================== SLA Status Badge ====================

    function renderSlaStatusBadge(status, data = {}) {
        const badges = {
            'ok': `<span class="sla-badge sla-ok" title="SLA đang được đáp ứng">
                <span class="sla-badge-icon">✓</span>
            </span>`,
            
            'warning': `<span class="sla-badge sla-warning" title="SLA sắp hết hạn">
                <span class="sla-badge-icon">⚠</span>
            </span>`,
            
            'critical': `<span class="sla-badge sla-critical" title="SLA gần đến hạn">
                <span class="sla-badge-icon">!</span>
            </span>`,
            
            'breached-response': `<span class="sla-badge sla-breached" title="SLA phản hồi đã bị vi phạm">
                <span class="sla-badge-icon">✗</span> PR
            </span>`,
            
            'breached-resolution': `<span class="sla-badge sla-breached" title="SLA giải quyết đã bị vi phạm">
                <span class="sla-badge-icon">✗</span> RS
            </span>`,
            
            'resolved': `<span class="sla-badge sla-resolved" title="Đã giải quyết">
                <span class="sla-badge-icon">✓</span>
            </span>`,
            
            'unknown': `<span class="sla-badge sla-unknown" title="Không có SLA">
                <span class="sla-badge-icon">—</span>
            </span>`
        };

        return badges[status] || badges['unknown'];
    }

    // ==================== SLA Info Card ====================

    function renderSlaInfoCard(ticket) {
        const responseAt = ticket.slaResponseAt ? new Date(ticket.slaResponseAt) : null;
        const resolutionAt = ticket.slaResolutionAt ? new Date(ticket.slaResolutionAt) : null;
        const firstResponseAt = ticket.firstResponseAt ? new Date(ticket.firstResponseAt) : null;
        const resolvedAt = ticket.resolvedAt ? new Date(ticket.resolvedAt) : null;
        const createdAt = ticket.createdAt ? new Date(ticket.createdAt) : null;

        // Calculate status
        const responseStatus = getSlaStatus(
            ticket.slaResponseAt, 
            ticket.slaResolutionAt, 
            ticket.firstResponseAt,
            ticket.resolvedAt
        );

        return `
            <div class="sla-info-card">
                <div class="sla-info-header">
                    <span class="sla-info-title">⏱️ SLA</span>
                    ${renderSlaStatusBadge(responseStatus)}
                </div>
                
                <div class="sla-info-body">
                    ${renderSlaTimeRow(
                        'Phản hồi',
                        ticket.slaResponseAt,
                        firstResponseAt,
                        ticket.firstResponseAt ? 'Đã phản hồi' : null,
                        responseAt
                    )}
                    
                    ${renderSlaTimeRow(
                        'Giải quyết', 
                        ticket.slaResolutionAt,
                        resolvedAt,
                        resolvedAt ? 'Đã giải quyết' : null,
                        resolutionAt
                    )}
                </div>
                
                <div class="sla-info-footer">
                    <span class="sla-policy-name">${ticket.priority || 'MEDIUM'} SLA</span>
                </div>
            </div>
        `;
    }

    function renderSlaTimeRow(label, deadline, actualTime, completedText, deadlineDate) {
        const now = new Date();
        const isBreached = deadlineDate && now > deadlineDate;
        const isPending = deadlineDate && now <= deadlineDate;
        
        let statusClass = 'sla-time-pending';
        let statusText = formatTimeRemaining(deadlineDate, label.toLowerCase(), deadline);
        
        if (actualTime) {
            statusClass = 'sla-time-completed';
            statusText = `<span class="sla-completed-text">${completedText || 'Hoàn thành'}</span>`;
        } else if (isBreached) {
            statusClass = 'sla-time-breached';
        } else if (isPending) {
            statusClass = 'sla-time-pending';
        }

        return `
            <div class="sla-time-row ${statusClass}">
                <span class="sla-time-label">${label}</span>
                <span class="sla-time-value">${statusText}</span>
            </div>
        `;
    }

    // ==================== SLA Progress Bar ====================

    function renderSlaProgressBar(ticket) {
        const createdAt = ticket.createdAt ? new Date(ticket.createdAt) : new Date();
        const resolutionAt = ticket.slaResolutionAt ? new Date(ticket.slaResolutionAt) : null;
        const resolvedAt = ticket.resolvedAt ? new Date(ticket.resolvedAt) : null;
        const now = new Date();

        if (!resolutionAt) return '';

        const totalMs = resolutionAt - createdAt;
        const elapsedMs = resolvedAt ? (resolvedAt - createdAt) : (now - createdAt);
        const percentage = Math.min((elapsedMs / totalMs) * 100, 100);

        let barClass = 'sla-progress-bar ok';
        if (percentage >= 100) {
            barClass = 'sla-progress-bar breached';
        } else if (percentage >= 75) {
            barClass = 'sla-progress-bar warning';
        } else if (percentage >= 50) {
            barClass = 'sla-progress-bar caution';
        }

        return `
            <div class="sla-progress-container">
                <div class="${barClass}">
                    <div class="sla-progress-fill" style="width: ${percentage}%"></div>
                </div>
                <span class="sla-progress-label">${Math.round(percentage)}%</span>
            </div>
        `;
    }

    // ==================== Callbacks ====================

    function onTimerUpdate(callback) {
        slaTimerCallbacks.push(callback);
        return () => {
            slaTimerCallbacks = slaTimerCallbacks.filter(cb => cb !== callback);
        };
    }

    // ==================== Public API ====================

    return {
        init,
        destroy,
        formatTimeRemaining,
        getSlaStatus,
        renderSlaStatusBadge,
        renderSlaInfoCard,
        renderSlaProgressBar,
        onTimerUpdate,
        startGlobalTimer,
        stopGlobalTimer,
        updateAllTimers
    };
})();

// Auto-initialize when DOM is ready
document.addEventListener('DOMContentLoaded', () => {
    // Defer initialization to wait for auth
    setTimeout(() => {
        if (window.Auth && window.Auth.isLoggedIn && window.Auth.isLoggedIn()) {
            SlaTimer.init();
        }
    }, 500);
});

// Initialize when auth is ready
if (window.Auth && window.Auth.onAuthReady) {
    window.Auth.onAuthReady(() => SlaTimer.init());
}

// Export globally
window.SlaTimer = SlaTimer;
export { SlaTimer };
