// ==================== Concurrent Edit Handler Module ====================
// Purpose: Handle concurrent editing conflicts between multiple users
// Features: Version tracking, conflict detection, user notification

/**
 * Track ticket versions to detect concurrent edits
 * This helps prevent lost updates when two users edit the same ticket
 */

// Store ticket versions for comparison
const ticketVersionMap = new Map();

/**
 * Set the version of a ticket when it's loaded
 * @param {number} ticketId - Ticket ID
 * @param {number} version - Version number from backend
 */
export const setTicketVersion = (ticketId, version) => {
    ticketVersionMap.set(ticketId, {
        version,
        lastRefreshed: Date.now(),
        lastEditedBy: null
    });
};

/**
 * Get the stored version of a ticket
 * @param {number} ticketId - Ticket ID
 * @returns {Object|null} - Version info or null if not tracked
 */
export const getTicketVersion = (ticketId) => {
    return ticketVersionMap.get(ticketId) || null;
};

/**
 * Check if a ticket has been modified since it was loaded
 * @param {number} ticketId - Ticket ID
 * @param {number} currentVersion - Current version from server
 * @returns {boolean} - True if versions match (no conflict)
 */
export const isTicketUnchanged = (ticketId, currentVersion) => {
    const stored = ticketVersionMap.get(ticketId);
    if (!stored) return true; // Not tracked, assume unchanged
    
    return stored.version === currentVersion;
};

/**
 * Handle conflict when user tries to save but ticket has been modified
 * @param {number} ticketId - Ticket ID
 * @param {number} serverVersion - Version from server response
 * @returns {Object} - Conflict info with options
 */
export const handleConflict = (ticketId, serverVersion) => {
    const stored = ticketVersionMap.get(ticketId);
    
    // Update stored version to server version
    if (stored) {
        stored.version = serverVersion;
        stored.lastRefreshed = Date.now();
    } else {
        ticketVersionMap.set(ticketId, {
            version: serverVersion,
            lastRefreshed: Date.now(),
            lastEditedBy: null
        });
    }
    
    return {
        hasConflict: true,
        ticketId,
        localVersion: stored?.version,
        serverVersion,
        message: 'Phiếu đã được cập nhật bởi người khác. Các thay đổi của bạn có thể bị ghi đè.',
        suggestion: 'Bạn có thể tải lại trang để xem các thay đổi mới nhất, hoặc tiếp tục lưu (thay đổi của bạn sẽ ghi đè).'
    };
};

/**
 * Mark ticket as being edited by current user
 * @param {number} ticketId - Ticket ID
 * @param {string} userId - Current user ID/username
 */
export const markTicketBeingEdited = (ticketId, userId) => {
    const stored = ticketVersionMap.get(ticketId);
    if (stored) {
        stored.lastEditedBy = userId;
        stored.editingStartTime = Date.now();
    }
};

/**
 * Clear editing state for a ticket
 * @param {number} ticketId - Ticket ID
 */
export const clearTicketEditState = (ticketId) => {
    ticketVersionMap.delete(ticketId);
};

/**
 * Get all tickets being tracked
 * @returns {Array} - Array of tracked ticket info
 */
export const getTrackedTickets = () => {
    const tracked = [];
    ticketVersionMap.forEach((info, ticketId) => {
        tracked.push({
            ticketId,
            version: info.version,
            lastRefreshed: info.lastRefreshed,
            editingDuration: info.editingStartTime 
                ? Math.floor((Date.now() - info.editingStartTime) / 60000) 
                : null
        });
    });
    return tracked;
};

/**
 * Setup concurrent edit warning for ticket detail view
 * This warns users when they try to navigate away with unsaved changes
 */
export const setupConcurrentEditWarning = () => {
    // Check for unsaved ticket edits before navigation
    const checkUnsavedEdits = (event) => {
        const editingTickets = getTrackedTickets().filter(t => t.editingDuration !== null);
        
        if (editingTickets.length > 0) {
            const message = `Bạn đang chỉnh sửa ${editingTickets.length} phiếu. Các thay đổi chưa được lưu sẽ bị mất.`;
            if (event) {
                event.preventDefault();
                event.returnValue = message;
            }
            return message;
        }
    };
    
    // Add beforeunload handler
    window.addEventListener('beforeunload', checkUnsavedEdits);
    
    // Return cleanup function
    return () => {
        window.removeEventListener('beforeunload', checkUnsavedEdits);
    };
};

/**
 * Show conflict resolution dialog
 * @param {Object} conflict - Conflict info from handleConflict
 * @returns {Promise<string>} - User choice: 'reload', 'overwrite', or 'cancel'
 */
export const showConflictDialog = (conflict) => {
    return new Promise((resolve) => {
        // Check if browser confirm is enough or if we need custom dialog
        const choice = confirm(
            `${conflict.message}\n\n` +
            `Phiên bản cục bộ: ${conflict.localVersion}\n` +
            `Phiên bản máy chủ: ${conflict.serverVersion}\n\n` +
            `Nhấn OK để tải lại với dữ liệu mới nhất.\n` +
            `Nhấn Cancel để tiếp tục chỉnh sửa (cảnh báo: thay đổi có thể bị ghi đè).`
        );
        
        if (choice) {
            resolve('reload');
        } else {
            resolve('overwrite');
        }
    });
};

/**
 * Process API response to detect version conflicts
 * @param {Object} response - API response
 * @param {number} ticketId - Ticket ID
 * @returns {Object} - Result with conflict info if any
 */
export const processResponse = (response, ticketId) => {
    // Check for conflict error code
    if (response.errorCode === 'CONCURRENT_MODIFICATION') {
        return handleConflict(ticketId, response.details?.serverVersion);
    }
    
    // If successful, update version if provided
    if (response.version && ticketId) {
        setTicketVersion(ticketId, response.version);
    }
    
    return { hasConflict: false };
};

export default {
    setTicketVersion,
    getTicketVersion,
    isTicketUnchanged,
    handleConflict,
    markTicketBeingEdited,
    clearTicketEditState,
    getTrackedTickets,
    setupConcurrentEditWarning,
    showConflictDialog,
    processResponse
};
