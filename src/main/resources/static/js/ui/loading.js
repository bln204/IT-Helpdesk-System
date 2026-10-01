// ==================== Loading Spinner Component ====================
// Purpose: Visual feedback for async operations
// Shows loading state during API calls

const LOADING_KEY = 'api-loading';

// Track active loading operations
let loadingCount = 0;
let loadingIndicator = null;

/**
 * Create the loading indicator element if it doesn't exist
 */
const ensureLoadingIndicator = () => {
    if (loadingIndicator) return;
    
    loadingIndicator = document.createElement('div');
    loadingIndicator.id = 'api-loading-indicator';
    loadingIndicator.className = 'loading-indicator hidden';
    loadingIndicator.innerHTML = `
        <div class="loading-spinner"></div>
        <span class="loading-text">Đang xử lý...</span>
    `;
    loadingIndicator.style.cssText = `
        position: fixed;
        top: 20px;
        right: 20px;
        z-index: 10000;
        display: flex;
        align-items: center;
        gap: 8px;
        background: var(--surface, #fff);
        border: 1px solid var(--border, #e0e0e0);
        border-radius: 8px;
        padding: 12px 20px;
        box-shadow: 0 4px 12px rgba(0,0,0,0.15);
        font-size: 14px;
        color: var(--text, #333);
    `;
    
    // Add spinner styles
    const style = document.createElement('style');
    style.textContent = `
        .loading-spinner {
            width: 20px;
            height: 20px;
            border: 2px solid var(--border, #e0e0e0);
            border-top-color: var(--accent, #0066cc);
            border-radius: 50%;
            animation: spin 0.8s linear infinite;
        }
        @keyframes spin {
            to { transform: rotate(360deg); }
        }
        .loading-indicator.dark {
            background: var(--surface-dark, #1a1a1a);
            border-color: var(--border-dark, #333);
            color: var(--text-dark, #fff);
        }
        .loading-indicator.dark .loading-spinner {
            border-color: var(--border-dark, #444);
            border-top-color: var(--accent-dark, #66b3ff);
        }
    `;
    document.head.appendChild(style);
    document.body.appendChild(loadingIndicator);
};

/**
 * Show the loading indicator
 */
export const showLoading = () => {
    loadingCount++;
    ensureLoadingIndicator();
    
    if (loadingIndicator) {
        loadingIndicator.classList.remove('hidden');
        
        // Update theme based on current theme
        if (document.body.classList.contains('dark')) {
            loadingIndicator.classList.add('dark');
        } else {
            loadingIndicator.classList.remove('dark');
        }
        
        // Update text to show count
        const textEl = loadingIndicator.querySelector('.loading-text');
        if (textEl) {
            textEl.textContent = loadingCount > 1 
                ? `Đang xử lý (${loadingCount} yêu cầu)...` 
                : 'Đang xử lý...';
        }
    }
};

/**
 * Hide the loading indicator
 */
export const hideLoading = () => {
    loadingCount = Math.max(0, loadingCount - 1);
    
    if (loadingCount === 0 && loadingIndicator) {
        loadingIndicator.classList.add('hidden');
    } else if (loadingIndicator) {
        const textEl = loadingIndicator.querySelector('.loading-text');
        if (textEl) {
            textEl.textContent = `Đang xử lý (${loadingCount} yêu cầu)...`;
        }
    }
};

/**
 * Get current loading count
 * @returns {number} - Number of active loading operations
 */
export const getLoadingCount = () => loadingCount;

/**
 * Check if something is loading
 * @returns {boolean} - True if there are active loading operations
 */
export const isLoading = () => loadingCount > 0;

/**
 * Reset loading state (for error recovery)
 */
export const resetLoading = () => {
    loadingCount = 0;
    if (loadingIndicator) {
        loadingIndicator.classList.add('hidden');
    }
};

/**
 * Create a loading overlay for specific elements (like forms, buttons)
 * @param {HTMLElement} element - Element to overlay
 * @returns {Object} - Overlay controller
 */
export const createLoadingOverlay = (element) => {
    const overlay = document.createElement('div');
    overlay.className = 'element-loading-overlay';
    overlay.style.cssText = `
        position: absolute;
        top: 0;
        left: 0;
        right: 0;
        bottom: 0;
        background: rgba(255,255,255,0.7);
        display: flex;
        align-items: center;
        justify-content: center;
        z-index: 100;
        border-radius: inherit;
    `;
    
    const spinner = document.createElement('div');
    spinner.className = 'loading-spinner';
    spinner.style.cssText = `
        width: 24px;
        height: 24px;
        border: 2px solid #e0e0e0;
        border-top-color: #0066cc;
        border-radius: 50%;
        animation: spin 0.8s linear infinite;
    `;
    
    overlay.appendChild(spinner);
    element.style.position = 'relative';
    element.appendChild(overlay);
    
    return {
        show: () => {
            overlay.style.display = 'flex';
        },
        hide: () => {
            overlay.style.display = 'none';
        },
        remove: () => {
            overlay.remove();
        }
    };
};

/**
 * Wrapper for async functions to show/hide loading
 * @param {Function} asyncFn - Async function to wrap
 * @param {Object} options - Options
 * @param {HTMLElement} options.element - Element to show overlay on
 * @param {boolean} options.showGlobal - Show global loading indicator
 * @returns {Function} - Wrapped function
 */
export const withLoading = (asyncFn, options = {}) => {
    return async (...args) => {
        let overlay = null;
        
        if (options.element) {
            overlay = createLoadingOverlay(options.element);
            overlay.show();
        }
        
        if (options.showGlobal !== false) {
            showLoading();
        }
        
        try {
            const result = await asyncFn(...args);
            return result;
        } finally {
            if (overlay) overlay.hide();
            if (options.showGlobal !== false) {
                hideLoading();
            }
        }
    };
};

export default {
    showLoading,
    hideLoading,
    getLoadingCount,
    isLoading,
    resetLoading,
    createLoadingOverlay,
    withLoading
};
