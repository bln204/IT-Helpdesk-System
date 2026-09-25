// ==================== API Request Wrapper ====================
import { API_BASE } from '../config/constants.js';

export const authHeaders = () => {
  const token = localStorage.getItem('ticketing.jwt');
  return token ? { Authorization: `Bearer ${token}` } : {};
};

export const request = async (path, options = {}) => {
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
  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(options.headers || {}),
      ...authHeaders(),
    },
  });
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

    // Only logout on 401 (token invalid/expired), not on 403 (permission denied)
    if (response.status === 401 && localStorage.getItem('ticketing.jwt')) {
      const errorCode = errorData.errorCode;
      if (errorCode && (errorCode.startsWith('AUTH_USER') || errorCode === 'AUTH_INVALID_CREDENTIALS')) {
        const error = new Error(errorMessage);
        error.errorCode = errorCode;
        error.status = response.status;
        error.details = errorData.details;
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

    const error = new Error(errorMessage);
    error.errorCode = errorData.errorCode;
    error.status = response.status;
    error.details = errorData.details;
    throw error;
  }

  if (response.status === 204) {
    return null;
  }
  return response.json();
};
