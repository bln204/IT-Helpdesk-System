// ==================== Toast Notification Module ====================
// Simple toast notification system

const Toast = (() => {
  let container = null;

  const createContainer = () => {
    if (container) return container;
    container = document.createElement('div');
    container.id = 'toast-container';
    container.className = 'toast-container';
    container.style.cssText = `
      position: fixed;
      bottom: 24px;
      right: 24px;
      z-index: 10000;
      display: flex;
      flex-direction: column;
      gap: 8px;
      pointer-events: none;
    `;
    document.body.appendChild(container);
    return container;
  };

  const show = (message, type = 'info', duration = 4000) => {
    const cont = createContainer();
    
    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
    
    const colors = {
      success: '#10b981',
      error: '#ef4444',
      warning: '#f59e0b',
      info: '#3b82f6'
    };
    
    const icons = {
      success: '✓',
      error: '✗',
      warning: '⚠',
      info: 'ℹ'
    };
    
    const color = colors[type] || colors.info;
    const icon = icons[type] || icons.info;
    
    toast.style.cssText = `
      background: ${color};
      color: white;
      padding: 12px 20px;
      border-radius: 8px;
      box-shadow: 0 4px 12px rgba(0,0,0,0.15);
      font-size: 0.9rem;
      font-weight: 500;
      display: flex;
      align-items: center;
      gap: 10px;
      pointer-events: auto;
      animation: toast-in 0.3s ease;
      max-width: 360px;
      min-width: 200px;
    `;
    
    toast.innerHTML = `
      <span style="font-size: 1.1rem;">${icon}</span>
      <span>${message}</span>
    `;
    
    cont.appendChild(toast);
    
    // Auto remove
    setTimeout(() => {
      toast.style.animation = 'toast-out 0.3s ease forwards';
      setTimeout(() => toast.remove(), 300);
    }, duration);
    
    // Click to dismiss
    toast.addEventListener('click', () => {
      toast.style.animation = 'toast-out 0.3s ease forwards';
      setTimeout(() => toast.remove(), 300);
    });
    
    console.log(`[Toast] ${type}: ${message}`);
  };

  const success = (message) => show(message, 'success');
  const error = (message) => show(message, 'error');
  const warning = (message) => show(message, 'warning');
  const info = (message) => show(message, 'info');

  return { show, success, error, warning, info };
})();

// Add toast animations
const style = document.createElement('style');
style.textContent = `
  @keyframes toast-in {
    from { opacity: 0; transform: translateX(100%); }
    to { opacity: 1; transform: translateX(0); }
  }
  @keyframes toast-out {
    from { opacity: 1; transform: translateX(0); }
    to { opacity: 0; transform: translateX(100%); }
  }
`;
document.head.appendChild(style);

// Export
window.Toast = Toast;
export { Toast };
