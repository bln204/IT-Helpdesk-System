// ==================== XSS Sanitization Module ====================
// Purpose: Sanitize user input to prevent Cross-Site Scripting (XSS) attacks
// All user-generated content MUST be sanitized before rendering to HTML

/**
 * XSS Sanitization utility
 * 
 * This module provides functions to sanitize user input and prevent XSS attacks.
 * User input is NEVER trusted and should always be escaped when rendered in HTML.
 */

// Map of HTML entities that need to be escaped
const HTML_ENTITIES = {
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#x27;',  // Using hex entity to avoid issues with single quotes
    '/': '&#x2F;',  // Forward slash to break attribute escaping
    '`': '&#x60;',  // Backtick to prevent template literal injection
    '=': '&#x3D;'   // Equals sign to prevent attribute injection
};

/**
 * Escape HTML special characters in a string
 * This prevents XSS by converting dangerous characters to their HTML entities
 * 
 * @param {string} text - The text to escape
 * @returns {string} - The escaped text safe for HTML rendering
 */
export const escapeHtml = (text) => {
    if (text === null || text === undefined) {
        return '';
    }
    
    // Convert to string if not already
    const str = String(text);
    
    // Replace dangerous characters with their HTML entities
    // Using regex with global flag for better performance
    return str.replace(/[&<>"'`=/]/g, (char) => HTML_ENTITIES[char] || char);
};

/**
 * Escape HTML special characters in text content (alias for escapeHtml)
 * Use this when placing text content inside HTML tags
 * 
 * @param {string} text - The text to escape
 * @returns {string} - The escaped text
 */
export const escapeText = escapeHtml;

/**
 * Escape text for use in HTML attribute values
 * This is more strict than escapeHtml as attributes have additional risks
 * 
 * @param {string} text - The text to escape
 * @returns {string} - The escaped text safe for attributes
 */
export const escapeAttribute = (text) => {
    if (text === null || text === undefined) {
        return '';
    }
    return escapeHtml(String(text)).replace(/"/g, '&quot;');
};

/**
 * Sanitize HTML to remove dangerous tags and attributes
 * This is for cases where you need to allow SOME HTML (like formatting)
 * but want to prevent XSS attacks
 * 
 * WARNING: Only use this if you explicitly want to allow some HTML.
 * For most cases, escapeHtml() is the correct function to use.
 * 
 * @param {string} html - The HTML to sanitize
 * @returns {string} - The sanitized HTML
 */
export const sanitizeHtml = (html) => {
    if (html === null || html === undefined) {
        return '';
    }
    
    const str = String(html);
    
    // First, escape all HTML entities
    let sanitized = escapeHtml(str);
    
    // Remove dangerous patterns that may have been introduced via entity encoding bypass
    const dangerousPatterns = [
        /javascript:/gi,
        /vbscript:/gi,
        /data:/gi,
        /on\w+=/gi,  // Event handlers like onclick, onerror, etc.
    ];
    
    dangerousPatterns.forEach(pattern => {
        sanitized = sanitized.replace(pattern, '');
    });
    
    return sanitized;
};

/**
 * Validate input length and optionally truncate
 * 
 * @param {string} text - The text to validate
 * @param {number} maxLength - Maximum allowed length
 * @param {boolean} truncate - Whether to truncate if exceeds max (default: true)
 * @returns {{ valid: boolean, text: string, message?: string }}
 */
export const validateLength = (text, maxLength, truncate = true) => {
    if (!text) {
        return { valid: true, text: '' };
    }
    
    const str = String(text);
    
    if (str.length <= maxLength) {
        return { valid: true, text: str };
    }
    
    if (truncate) {
        return {
            valid: true,
            text: str.substring(0, maxLength),
            message: `Text truncated to ${maxLength} characters`
        };
    }
    
    return {
        valid: false,
        text: str,
        message: `Text exceeds maximum length of ${maxLength} characters`
    };
};

/**
 * Check if text contains potentially dangerous patterns
 * This is for logging/alerting purposes, not for security (use escapeHtml for that)
 * 
 * @param {string} text - The text to check
 * @returns {{ hasDanger: boolean, patterns: string[] }}
 */
export const detectDangerousPatterns = (text) => {
    if (!text) {
        return { hasDanger: false, patterns: [] };
    }
    
    const str = String(text);
    const detectedPatterns = [];
    
    const dangerousPatterns = [
        { pattern: /<script/i, name: 'script_tag' },
        { pattern: /javascript:/i, name: 'javascript_protocol' },
        { pattern: /on\w+=/i, name: 'event_handler' },
        { pattern: /<iframe/gi, name: 'iframe_tag' },
        { pattern: /<embed/gi, name: 'embed_tag' },
        { pattern: /<object/gi, name: 'object_tag' },
        { pattern: /data:/i, name: 'data_protocol' },
        { pattern: /<img[^>]*src=["']?[^"']*["']?[^>]*>/gi, name: 'img_with_src' },
    ];
    
    dangerousPatterns.forEach(({ pattern, name }) => {
        if (pattern.test(str)) {
            detectedPatterns.push(name);
        }
    });
    
    return {
        hasDanger: detectedPatterns.length > 0,
        patterns: detectedPatterns
    };
};

/**
 * Sanitize search input to prevent SQL injection patterns
 * Note: SQL injection is primarily prevented at the backend with parameterized queries
 * This is for additional defense and logging
 * 
 * @param {string} searchText - The search text to sanitize
 * @returns {string} - Sanitized search text
 */
export const sanitizeSearchInput = (searchText) => {
    if (!searchText) {
        return '';
    }
    
    // Remove or escape potentially dangerous SQL patterns
    // Note: This is defense-in-depth, the actual SQL injection protection
    // comes from parameterized queries in the backend (JPA)
    const str = String(searchText);
    
    // Remove common SQL injection patterns
    return str
        .replace(/'/g, "''")  // Escape single quotes
        .replace(/\\/g, '\\\\')  // Escape backslashes
        .trim();  // Remove leading/trailing whitespace
};

/**
 * Create a safe HTML template function
 * This ensures all dynamic values are escaped before being inserted
 * 
 * @param {string} template - Template string with ${value} placeholders
 * @param {Object} values - Object containing values to interpolate
 * @returns {string} - Safe HTML string
 */
export const createSafeHtml = (template, values) => {
    // First escape all values
    const safeValues = {};
    for (const [key, value] of Object.entries(values)) {
        safeValues[key] = escapeHtml(String(value ?? ''));
    }
    
    // Then interpolate using a simple template replacement
    return template.replace(/\$\{(\w+)\}/g, (match, key) => {
        return safeValues[key] ?? '';
    });
};

/**
 * Validate and sanitize form input
 * 
 * @param {string} value - Form input value
 * @param {Object} options - Validation options
 * @param {number} options.maxLength - Maximum length
 * @param {boolean} options.allowHtml - Whether to allow HTML (NOT recommended)
 * @param {boolean} options.trim - Whether to trim whitespace
 * @returns {{ valid: boolean, value: string, error?: string }}
 */
export const validateFormInput = (value, options = {}) => {
    const {
        maxLength = Infinity,
        allowHtml = false,
        trim = true
    } = options;
    
    if (value === null || value === undefined) {
        return { valid: true, value: '' };
    }
    
    let str = String(value);
    
    // Trim if requested
    if (trim) {
        str = str.trim();
    }
    
    // Check for empty
    if (!str) {
        return { valid: true, value: '' };
    }
    
    // Check length
    if (str.length > maxLength) {
        return {
            valid: false,
            value: str,
            error: `Nội dung vượt quá ${maxLength} ký tự cho phép`
        };
    }
    
    // Sanitize based on options
    if (!allowHtml) {
        str = escapeHtml(str);
    } else {
        str = sanitizeHtml(str);
    }
    
    return { valid: true, value: str };
};

// Export a default object with all functions for convenience
export default {
    escapeHtml,
    escapeText,
    escapeAttribute,
    sanitizeHtml,
    validateLength,
    detectDangerousPatterns,
    sanitizeSearchInput,
    createSafeHtml,
    validateFormInput
};
