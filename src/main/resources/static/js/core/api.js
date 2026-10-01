// ==================== API Request Wrapper ====================
import { API_BASE } from '../config/constants.js';

// ==================== Configuration ====================
const DEFAULT_TIMEOUT_MS = 30000; // 30 seconds
const SLOW_REQUEST_THRESHOLD_MS = 10000; // 10 seconds - log warning

// ==================== Duplicate Request Prevention ====================
// Track in-flight requests to prevent duplicate submissions
const inFlightRequests = new Map();

/**
 * Check if a request is already in-flight (duplicate)
 * @param {string} requestId - Request ID to check
 * @returns {boolean} - True if duplicate
 */
const isDuplicateRequest = (requestId) => {
    return inFlightRequests.has(requestId);
};

/**
 * Mark request as in-flight
 * @param {string} requestId - Request ID
 */
const markRequestInFlight = (requestId) => {
    inFlightRequests.set(requestId, Date.now());
};

/**
 * Remove request from in-flight tracking
 * @param {string} requestId - Request ID
 */
const clearRequestInFlight = (requestId) => {
    inFlightRequests.delete(requestId);
};

/**
 * Cleanup old in-flight requests (older than 60 seconds)
 */
const cleanupStaleRequests = () => {
    const now = Date.now();
    const staleThreshold = 60000; // 60 seconds
    inFlightRequests.forEach((timestamp, requestId) => {
        if (now - timestamp > staleThreshold) {
            inFlightRequests.delete(requestId);
        }
    });
};

// Periodic cleanup every 30 seconds
setInterval(cleanupStaleRequests, 30000);

// ==================== Request Function ====================
export const authHeaders = () => {
    const token = localStorage.getItem('ticketing.jwt');
    return token ? { Authorization: `Bearer ${token}` } : {};
};

/**
 * Check if duplicate request should be blocked
 * @param {string} method - HTTP method
 * @param {string} path - API path
 * @returns {Object|null} - Error object if duplicate, null otherwise
 */
export const checkDuplicateRequest = (method, path) => {
    // Only check POST, PUT, PATCH, DELETE for duplicates
    if (!['POST', 'PUT', 'PATCH', 'DELETE'].includes(method.toUpperCase())) {
        return null;
    }
    
    // Create a key based on method + path
    const requestKey = `${method.toUpperCase()}:${path}`;
    
    if (isDuplicateRequest(requestKey)) {
        return {
            isDuplicate: true,
            message: 'Yêu cầu đang được xử lý. Vui lòng đợi...'
        };
    }
    
    return null;
};

export const request = async (path, options = {}) => {
    const method = options.method || 'GET';
    const timeoutMs = options.timeout || DEFAULT_TIMEOUT_MS;
    
    // Check for duplicate requests
    const duplicateCheck = checkDuplicateRequest(method, path);
    if (duplicateCheck?.isDuplicate) {
        const error = new Error(duplicateCheck.message);
        error.errorCode = 'DUPLICATE_REQUEST';
        error.status = 409;
        throw error;
    }
    
    // Mark request as in-flight
    const requestId = `${method.toUpperCase()}:${path}`;
    markRequestInFlight(requestId);
    
    // Track timing for slow request detection
    const requestStartTime = Date.now();
    
    // Create timeout abort controller
    const timeoutController = new AbortController();
    const timeoutId = setTimeout(() => {
        timeoutController.abort();
    }, timeoutMs);
    
    // Ensure cleanup happens on any exit
    let response;
    try {
        const apiStatus = document.getElementById('api-status');
        const setApiStatus = (text) => {
            let viText = text;
            if (text === 'idle') viText = 'chờ';
            else if (text === 'working') viText = 'đang xử lý';
            else if (text === 'ok') viText = 'sẵn sàng';
            else if (typeof text === 'string' && text.startsWith('error')) viText = text.replace('error', 'lỗi');
            if (apiStatus) apiStatus.textContent = `Trạng thái API: ${viText}`;
        };

        setApiStatus('working');
        
        const fetchOptions = {
            ...options,
            headers: {
                'Content-Type': 'application/json',
                ...(options.headers || {}),
                ...authHeaders(),
            },
            signal: timeoutController.signal,
        };
        
        response = await fetch(`${API_BASE}${path}`, fetchOptions);
        
        const requestDuration = Date.now() - requestStartTime;
        
        // Log slow requests
        if (requestDuration > SLOW_REQUEST_THRESHOLD_MS) {
            console.warn(`[API] Slow request detected: ${method} ${path} took ${requestDuration}ms`);
        }
        
        clearTimeout(timeoutId);
        setApiStatus(response.ok ? 'ok' : `error ${response.status}`);

        if (!response.ok) {
            // Parse error response in our format
            let errorData = null;
            try {
                const contentType = response.headers.get('content-type');
                if (contentType && contentType.includes('application/json')) {
                    errorData = await response.json();
                } else {
                    const text = await response.text();
                    errorData = { message: text };
                }
            } catch (e) {
                errorData = { message: await response.text() };
            }

            // Build error message from our format
            let errorMessage = errorData.message || `Yêu cầu thất bại: ${response.status}`;

            // Add suggestion if available
            if (errorData.details && errorData.details.suggestion) {
                errorMessage += '\n' + errorData.details.suggestion;
            }

            // Add rejection reason if available
            if (errorData.details && errorData.details.rejectionReason) {
                errorMessage += '\nLý do: ' + errorData.details.rejectionReason;
            }

            // Create base error object
            const error = new Error(errorMessage);
            error.errorCode = errorData.errorCode;
            error.status = response.status;
            error.details = errorData.details;
            
            // Check for passwordMustChange flag in error response
            if (errorData.passwordMustChange === true) {
                error.passwordMustChange = true;
            }

            // Handle 403 Forbidden - Password change required
            if (response.status === 403 && errorData.passwordMustChange === true) {
                // Import auth module to handle password change requirement
                import('../core/auth.js').then(module => {
                    module.setPasswordMustChange(true);
                    module.showForcePasswordChangeModal();
                });
                // Re-throw the error so callers can handle it if needed
                throw error;
            }

            // Handle concurrent modification (optimistic locking conflict)
            if (response.status === 409 || errorData.errorCode === 'CONCURRENT_MODIFICATION') {
                error.errorCode = 'CONCURRENT_MODIFICATION';
                error.details = errorData.details || {};
            }

            // Only logout on 401 (token invalid/expired), not on 403 (permission denied)
            if (response.status === 401 && localStorage.getItem('ticketing.jwt')) {
                const errorCode = errorData.errorCode;
                if (errorCode && (errorCode.startsWith('AUTH_USER') || errorCode === 'AUTH_INVALID_CREDENTIALS')) {
                    throw error;
                }
                // Token expired or invalid - logout
                localStorage.removeItem('ticketing.jwt');
                // Import dynamically to avoid circular dependency
                import('../core/auth.js').then(module => {
                    module.setToken(null);
                    module.setCurrentUser(null);
                });
                import('../core/router.js').then(module => {
                    module.showView('login');
                });
            }

            throw error;
        }

        if (response.status === 204) {
            return null;
        }
        return response.json();
    } catch (error) {
        const requestDuration = Date.now() - requestStartTime;
        
        // Handle timeout errors
        if (error.name === 'AbortError' || error.message?.includes('aborted')) {
            console.error(`[API] Request timeout: ${method} ${path} (${requestDuration}ms)`);
            const timeoutError = new Error(`Yêu cầu hết thời gian chờ (${timeoutMs / 1000}s). Vui lòng thử lại hoặc kiểm tra kết nối mạng.`);
            timeoutError.errorCode = 'REQUEST_TIMEOUT';
            timeoutError.status = 408;
            timeoutError.details = {
                suggestion: 'Thử tải lại trang hoặc kiểm tra kết nối mạng.',
                originalError: error.message
            };
            clearTimeout(timeoutId);
            throw timeoutError;
        }
        
        // Handle network errors (no internet, DNS failure, etc.)
        if (error.name === 'TypeError' && error.message?.includes('fetch')) {
            console.error(`[API] Network error: ${method} ${path}`, error);
            const networkError = new Error('Lỗi kết nối mạng. Vui lòng kiểm tra kết nối internet.');
            networkError.errorCode = 'NETWORK_ERROR';
            networkError.status = 0;
            networkError.details = {
                suggestion: 'Kiểm tra kết nối mạng và thử lại.',
                originalError: error.message
            };
            clearTimeout(timeoutId);
            throw networkError;
        }
        
        // Re-throw other errors
        clearTimeout(timeoutId);
        throw error;
    } finally {
        // Always clear in-flight status
        clearRequestInFlight(requestId);
    }
};

/**
 * Create a request with custom timeout
 * @param {string} path - API path
 * @param {Object} options - Request options
 * @param {number} timeout - Timeout in milliseconds
 * @returns {Promise} - API response
 */
export const requestWithTimeout = (path, options, timeout) => {
    return request(path, { ...options, timeout });
};
