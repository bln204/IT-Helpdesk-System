// ==================== Date Pickers ====================
// Native <input type="date"> renders in the browser UI locale (mm/dd/yyyy on English Edge).
// flatpickr shows dd/mm/yyyy with Vietnamese labels while the original input keeps the ISO
// value (yyyy-mm-dd / yyyy-mm-ddTHH:mm), so every existing `.value` read keeps working.

const SELECTOR = 'input[type="date"], input[type="datetime-local"]';

const enhance = (input) => {
  if (!window.flatpickr || input._flatpickr) return;
  const withTime = input.type === 'datetime-local';
  window.flatpickr(input, {
    locale: 'vn',
    altInput: true,
    altFormat: withTime ? 'd/m/Y H:i' : 'd/m/Y',
    dateFormat: withTime ? 'Y-m-d\\TH:i' : 'Y-m-d',
    enableTime: withTime,
    time_24hr: true,
    allowInput: true,
    disableMobile: true,
  });
  // altInput copies the original classes; keep the placeholder readable in Vietnamese.
  if (input._flatpickr.altInput && !input._flatpickr.altInput.placeholder) {
    input._flatpickr.altInput.placeholder = withTime ? 'dd/mm/yyyy hh:mm' : 'dd/mm/yyyy';
  }
};

/** Enhance current date inputs and any added later (module modals render them on the fly). */
export const initDatePickers = () => {
  if (!window.flatpickr) return; // CDN unreachable: native inputs still work
  document.querySelectorAll(SELECTOR).forEach(enhance);
  new MutationObserver((mutations) => {
    for (const m of mutations) {
      for (const node of m.addedNodes) {
        if (node.nodeType !== 1) continue;
        if (node.matches(SELECTOR)) enhance(node);
        node.querySelectorAll?.(SELECTOR).forEach(enhance);
      }
    }
  }).observe(document.body, { childList: true, subtree: true });
};
