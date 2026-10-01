// ==================== Draft Service ====================
// Purpose: Auto-save form drafts to localStorage for recovery
// Handles: Draft saving, recovery prompt, conflict detection, navigation warnings

const DRAFT_KEY_PREFIX = 'ticketing.draft.';
const DRAFT_VERSION = 1;
const DRAFT_EXPIRY_MS = 7 * 24 * 60 * 60 * 1000; // 7 days

/**
 * Generate a unique draft key for a form
 * @param {string} formType - Type of form (e.g., 'ticket-create', 'ticket-edit')
 * @param {string|number} formId - Optional ID for specific form instances
 * @returns {string} - Unique draft key
 */
export const generateDraftKey = (formType, formId = null) => {
    const baseKey = `${DRAFT_KEY_PREFIX}${formType}`;
    return formId ? `${baseKey}.${formId}` : baseKey;
};

/**
 * Save form data to draft
 * @param {string} key - Draft key
 * @param {Object} formData - Form field values
 * @param {Object} metadata - Additional metadata (formType, ticketId, etc.)
 * @returns {Object} - Result with success status
 */
export const saveDraft = (key, formData, metadata = {}) => {
    try {
        const draft = {
            version: DRAFT_VERSION,
            timestamp: Date.now(),
            formData,
            metadata,
        };
        
        localStorage.setItem(key, JSON.stringify(draft));
        
        // Dispatch event for UI feedback
        if (typeof window !== 'undefined') {
            window.dispatchEvent(new CustomEvent('draftSaved', {
                detail: { key, timestamp: draft.timestamp }
            }));
        }
        
        return { success: true, timestamp: draft.timestamp };
    } catch (error) {
        console.error('[DraftService] Failed to save draft:', error);
        return { success: false, error: error.message };
    }
};

/**
 * Load draft from storage
 * @param {string} key - Draft key
 * @returns {Object|null} - Draft data or null if not found/expired
 */
export const loadDraft = (key) => {
    try {
        const raw = localStorage.getItem(key);
        if (!raw) return null;
        
        const draft = JSON.parse(raw);
        
        // Check version compatibility
        if (!draft.version || draft.version > DRAFT_VERSION) {
            console.warn('[DraftService] Draft version mismatch, clearing:', key);
            deleteDraft(key);
            return null;
        }
        
        // Check expiry
        if (draft.timestamp && (Date.now() - draft.timestamp) > DRAFT_EXPIRY_MS) {
            console.info('[DraftService] Draft expired, clearing:', key);
            deleteDraft(key);
            return null;
        }
        
        return draft;
    } catch (error) {
        console.error('[DraftService] Failed to load draft:', error);
        deleteDraft(key);
        return null;
    }
};

/**
 * Delete draft from storage
 * @param {string} key - Draft key
 */
export const deleteDraft = (key) => {
    try {
        localStorage.removeItem(key);
    } catch (error) {
        console.error('[DraftService] Failed to delete draft:', error);
    }
};

/**
 * Check if a draft exists
 * @param {string} key - Draft key
 * @returns {boolean} - True if draft exists and is valid
 */
export const hasDraft = (key) => {
    return loadDraft(key) !== null;
};

/**
 * Get time since draft was saved
 * @param {string} key - Draft key
 * @returns {string} - Human-readable time string
 */
export const getDraftAge = (key) => {
    const draft = loadDraft(key);
    if (!draft || !draft.timestamp) return null;
    
    const diffMs = Date.now() - draft.timestamp;
    const diffMins = Math.floor(diffMs / 60000);
    const diffHours = Math.floor(diffMs / 3600000);
    const diffDays = Math.floor(diffMs / 86400000);
    
    if (diffMins < 1) return 'vừa xong';
    if (diffMins < 60) return `${diffMins} phút trước`;
    if (diffHours < 24) return `${diffHours} giờ trước`;
    return `${diffDays} ngày trước`;
};

/**
 * List all available drafts
 * @returns {Array} - Array of draft info objects
 */
export const listDrafts = () => {
    const drafts = [];
    
    try {
        for (let i = 0; i < localStorage.length; i++) {
            const key = localStorage.key(i);
            if (key && key.startsWith(DRAFT_KEY_PREFIX)) {
                const draft = loadDraft(key);
                if (draft) {
                    drafts.push({
                        key,
                        type: draft.metadata?.formType || 'unknown',
                        id: draft.metadata?.ticketId || null,
                        timestamp: draft.timestamp,
                        age: getDraftAge(key),
                        formData: draft.formData,
                    });
                }
            }
        }
    } catch (error) {
        console.error('[DraftService] Failed to list drafts:', error);
    }
    
    return drafts.sort((a, b) => b.timestamp - a.timestamp);
};

/**
 * Clear all drafts
 */
export const clearAllDrafts = () => {
    try {
        const keysToRemove = [];
        for (let i = 0; i < localStorage.length; i++) {
            const key = localStorage.key(i);
            if (key && key.startsWith(DRAFT_KEY_PREFIX)) {
                keysToRemove.push(key);
            }
        }
        keysToRemove.forEach(key => localStorage.removeItem(key));
    } catch (error) {
        console.error('[DraftService] Failed to clear drafts:', error);
    }
};

/**
 * Form state tracker for detecting unsaved changes
 */
let formStateTracker = new Map();

/**
 * Track form for unsaved changes detection
 * @param {string} formId - Form ID or key
 * @param {HTMLFormElement} form - Form element
 */
export const trackForm = (formId, form) => {
    if (!form) return;
    
    const initialData = getFormData(form);
    formStateTracker.set(formId, {
        form,
        initialData,
        hasChanges: false,
        submitInProgress: false,
    });
    
    // Listen for input changes
    const handler = () => {
        const tracker = formStateTracker.get(formId);
        if (tracker && !tracker.submitInProgress) {
            const currentData = getFormData(form);
            tracker.hasChanges = !isEqual(initialData, currentData);
        }
    };
    
    // Use debounce for performance
    let debounceTimer = null;
    const debouncedHandler = () => {
        if (debounceTimer) clearTimeout(debounceTimer);
        debounceTimer = setTimeout(handler, 300);
    };
    
    form.addEventListener('input', debouncedHandler);
    form.addEventListener('change', debouncedHandler);
    
    // Store handler reference for cleanup
    const tracker = formStateTracker.get(formId);
    if (tracker) {
        tracker.cleanup = () => {
            form.removeEventListener('input', debouncedHandler);
            form.removeEventListener('change', debouncedHandler);
            if (debounceTimer) clearTimeout(debounceTimer);
        };
    }
};

/**
 * Mark form as submitted (to prevent beforeunload warning)
 * @param {string} formId - Form ID or key
 */
export const markFormSubmitted = (formId) => {
    const tracker = formStateTracker.get(formId);
    if (tracker) {
        tracker.submitInProgress = true;
        tracker.hasChanges = false;
    }
};

/**
 * Check if form has unsaved changes
 * @param {string} formId - Form ID or key
 * @returns {boolean} - True if form has unsaved changes
 */
export const hasUnsavedChanges = (formId) => {
    const tracker = formStateTracker.get(formId);
    return tracker ? tracker.hasChanges : false;
};

/**
 * Untrack form (cleanup)
 * @param {string} formId - Form ID or key
 */
export const untrackForm = (formId) => {
    const tracker = formStateTracker.get(formId);
    if (tracker) {
        if (tracker.cleanup) tracker.cleanup();
        formStateTracker.delete(formId);
    }
};

/**
 * Get all form IDs with unsaved changes
 * @returns {Array} - Array of form IDs with unsaved changes
 */
export const getFormsWithUnsavedChanges = () => {
    const forms = [];
    formStateTracker.forEach((tracker, formId) => {
        if (tracker.hasChanges) {
            forms.push(formId);
        }
    });
    return forms;
};

/**
 * Setup beforeunload warning for forms with unsaved changes
 */
export const setupBeforeUnloadWarning = () => {
    if (typeof window === 'undefined') return;
    
    const handler = (event) => {
        const unsavedForms = getFormsWithUnsavedChanges();
        if (unsavedForms.length > 0) {
            // Standard way to show browser's default warning
            event.preventDefault();
            event.returnValue = 'Bạn có thay đổi chưa lưu. Bạn có chắc muốn rời khỏi trang này?';
            return event.returnValue;
        }
    };
    
    window.addEventListener('beforeunload', handler);
    
    // Return cleanup function
    return () => {
        window.removeEventListener('beforeunload', handler);
    };
};

// ==================== Helper Functions ====================

/**
 * Get form data as object
 * @param {HTMLFormElement} form - Form element
 * @returns {Object} - Form field values
 */
const getFormData = (form) => {
    const formData = new FormData(form);
    const data = {};
    
    for (const [key, value] of formData.entries()) {
        // Handle multiple values with same name (like select with multiple)
        if (data[key] !== undefined) {
            if (Array.isArray(data[key])) {
                data[key].push(value);
            } else {
                data[key] = [data[key], value];
            }
        } else {
            data[key] = value;
        }
    }
    
    return data;
};

/**
 * Compare two objects for equality
 * @param {Object} a - First object
 * @param {Object} b - Second object
 * @returns {boolean} - True if equal
 */
const isEqual = (a, b) => {
    const keysA = Object.keys(a);
    const keysB = Object.keys(b);
    
    if (keysA.length !== keysB.length) return false;
    
    for (const key of keysA) {
        if (a[key] !== b[key]) return false;
    }
    
    return true;
};

// ==================== Default Export ====================
export default {
    generateDraftKey,
    saveDraft,
    loadDraft,
    deleteDraft,
    hasDraft,
    getDraftAge,
    listDrafts,
    clearAllDrafts,
    trackForm,
    markFormSubmitted,
    hasUnsavedChanges,
    untrackForm,
    getFormsWithUnsavedChanges,
    setupBeforeUnloadWarning,
};
