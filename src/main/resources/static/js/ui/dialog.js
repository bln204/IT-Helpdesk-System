// ==================== Dialog Module ====================
// System modal replacing native alert/confirm/prompt ("localhost says:").
// Reuses the existing .modal / .modal-header / .modal-footer styles.

const Dialog = (() => {
  let modal = null;
  let els = null;
  let pending = null;

  const build = () => {
    modal = document.createElement('div');
    modal.className = 'modal app-dialog hidden';
    modal.innerHTML = `
      <div class="modal-content" role="dialog" aria-modal="true" aria-labelledby="app-dialog-title">
        <header class="modal-header panel-header">
          <h3 id="app-dialog-title"></h3>
          <button type="button" class="close-btn" data-dialog="cancel" aria-label="Đóng">&times;</button>
        </header>
        <div class="modal-body">
          <p class="app-dialog-message"></p>
          <p class="app-dialog-note"></p>
          <div class="modal-rejection-form app-dialog-input hidden">
            <textarea rows="3"></textarea>
            <p class="field-error app-dialog-error hidden"></p>
          </div>
        </div>
        <footer class="modal-footer">
          <button type="button" class="secondary" data-dialog="cancel"></button>
          <button type="button" data-dialog="ok"></button>
        </footer>
      </div>`;
    document.body.appendChild(modal);
    els = {
      title: modal.querySelector('h3'),
      message: modal.querySelector('.app-dialog-message'),
      note: modal.querySelector('.app-dialog-note'),
      inputWrap: modal.querySelector('.app-dialog-input'),
      input: modal.querySelector('textarea'),
      error: modal.querySelector('.app-dialog-error'),
      cancel: modal.querySelector('.modal-footer [data-dialog="cancel"]'),
      ok: modal.querySelector('[data-dialog="ok"]'),
    };

    modal.addEventListener('click', (e) => {
      if (e.target === modal || e.target.closest('[data-dialog="cancel"]')) finish(false);
      else if (e.target.closest('[data-dialog="ok"]')) finish(true);
    });
    modal.addEventListener('keydown', (e) => {
      if (e.key === 'Escape') { e.preventDefault(); finish(false); }
      // Enter confirms; Shift+Enter keeps a newline in the textarea.
      else if (e.key === 'Enter' && !e.shiftKey && e.target.tagName !== 'BUTTON') { e.preventDefault(); finish(true); }
      else if (e.key === 'Tab') trapFocus(e);
    });
  };

  const trapFocus = (e) => {
    const focusable = [...modal.querySelectorAll('button, textarea')].filter((el) => el.offsetParent !== null);
    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last.focus(); }
    else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus(); }
  };

  const finish = (accepted) => {
    if (!pending) return;
    const { kind, resolve, required, returnFocus } = pending;
    if (kind === 'prompt' && accepted && required && !els.input.value.trim()) {
      els.error.classList.remove('hidden');
      els.input.focus();
      return;
    }
    pending = null;
    modal.classList.add('hidden');
    returnFocus?.focus?.();
    if (kind === 'prompt') resolve(accepted ? els.input.value.trim() : null);
    else if (kind === 'confirm') resolve(accepted);
    else resolve();
  };

  const open = (kind, message, opts = {}) => {
    if (!modal) build();
    if (pending) finish(false); // a newer dialog replaces an unanswered one
    const {
      title = kind === 'alert' ? 'Thông báo' : 'Xác nhận',
      note = '',
      okText = kind === 'alert' ? 'Đã hiểu' : 'Đồng ý',
      cancelText = 'Để sau',
      danger = false,
      placeholder = '',
      required = false,
      requiredText = 'Vui lòng nhập nội dung trước khi tiếp tục.',
      defaultValue = '',
    } = opts;

    els.title.textContent = title;
    els.message.textContent = message;
    els.note.textContent = note;
    els.note.classList.toggle('hidden', !note);
    els.ok.textContent = okText;
    els.ok.classList.toggle('danger', danger);
    els.cancel.textContent = cancelText;
    els.cancel.classList.toggle('hidden', kind === 'alert');
    els.inputWrap.classList.toggle('hidden', kind !== 'prompt');
    els.input.value = defaultValue;
    els.input.placeholder = placeholder;
    els.error.textContent = requiredText;
    els.error.classList.add('hidden');

    return new Promise((resolve) => {
      pending = { kind, resolve, required, returnFocus: document.activeElement };
      modal.classList.remove('hidden');
      (kind === 'prompt' ? els.input : els.ok).focus();
    });
  };

  return {
    alert: (message, opts) => open('alert', message, opts),
    confirm: (message, opts) => open('confirm', message, opts),
    prompt: (message, opts) => open('prompt', message, opts),
  };
})();

window.Dialog = Dialog;
export { Dialog };
