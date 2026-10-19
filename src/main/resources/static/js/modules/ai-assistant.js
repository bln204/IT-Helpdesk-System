// ==================== Floating AI Assistant ====================
// Phase 6.6 frontend integration.
//
// - Uses the existing request helper (core/api.js) so JWT auth, CORS,
//   timeouts, and structured error parsing stay consistent.
// - Calls ONLY the audited backend endpoint POST /api/ai/ask.
// - Per-call timeout is overridden to 180_000 ms (180 s) because the
//   production Gemma3:4b chat timeout is 180 s; the global default of
//   30 s in core/api.js is intentionally NOT changed.
// - Renders AI responses using textContent only. No innerHTML for AI
//   text, no Markdown parser, no new dependencies.
// - Role gate uses the existing window.Auth.hasRole helper; this is
//   purely UX — backend @PreAuthorize remains the security boundary.
// - Conversations are stored server-side (ai_session / ai_message). The
//   panel has a history drawer (GET/DELETE /api/ai/sessions) and the
//   current sessionId is remembered per user in localStorage so a reload
//   resumes the open thread.

import { request } from '../core/api.js';
import { Dialog } from '../ui/dialog.js';
import { Toast } from '../ui/toast.js';

const ENDPOINT = '/api/ai/ask';
const SESSIONS_ENDPOINT = '/api/ai/sessions';
const SESSIONS_LIMIT = 50;
const AI_REQUEST_TIMEOUT_MS = 180000;
const MAX_QUESTION_LENGTH = 500;
const MIN_QUESTION_LENGTH = 5;

const ALLOWED_ROLES = [
    'ROLE_ADMIN',
    'ROLE_GIAM_DOC',
    'ROLE_TRUONG_PHONG',
    'ROLE_NHAN_VIEN',
];

// PHASE 7.7 hotfix-22: chat-box suggestion buttons have
// been removed entirely (constant + render path +
// submitSuggestion helper). The user reported that
// pre-canned suggestions (e.g. "Ticket TKT-2026-00199
// đang ở trạng thái gì?") led the LLM to hallucinate a
// status when the suggested ticket did not exist in
// the database (see hotfix-21 for the server-side
// short-circuit). Removing the buttons at the source
// (frontend) is a defense-in-depth measure so no role
// (NHAN_VIEN, TRUONG_PHONG, GIAM_DOC, ADMIN) ever
// sees them again.

const AI_ERROR_MESSAGES = {
    'ACCESS_DENIED':
        'Bạn không có quyền sử dụng AI Assistant.',
    'REQUEST_TIMEOUT':
        'AI mất nhiều thời gian để phản hồi. Vui lòng thử lại.',
    'NETWORK_ERROR':
        'Không thể kết nối đến hệ thống AI. Vui lòng kiểm tra kết nối và thử lại.',
    'LLM_UNAVAILABLE':
        'Dịch vụ AI hiện không khả dụng. Vui lòng thử lại sau.',
    'LLM_TIMEOUT':
        'AI mất nhiều thời gian để phản hồi. Vui lòng thử lại.',
    'LLM_ERROR_RESPONSE':
        'AI tạm thời không thể tạo câu trả lời. Vui lòng thử lại.',
    'LLM_MALFORMED_RESPONSE':
        'AI tạm thời không thể tạo câu trả lời. Vui lòng thử lại.',
    'LLM_EMPTY_RESPONSE':
        'AI tạm thời không thể tạo câu trả lời. Vui lòng thử lại.',
    'EMBEDDING_UNAVAILABLE':
        'Dịch vụ tìm kiếm AI hiện không khả dụng. Vui lòng thử lại sau.',
    'EMBEDDING_TIMEOUT':
        'AI mất nhiều thời gian để tìm kiếm dữ liệu. Vui lòng thử lại.',
    'EMBEDDING_MALFORMED_RESPONSE':
        'Dịch vụ tìm kiếm AI hiện không khả dụng. Vui lòng thử lại sau.',
    'VALIDATION_ERROR':
        'Câu hỏi không hợp lệ. Vui lòng kiểm tra lại nội dung.',
};

const FALLBACK_ERROR_MESSAGE =
    'AI tạm thời không khả dụng. Vui lòng thử lại sau.';

// ==================== Module state ====================
let state = {
    initialized: false,
    isOpen: false,
    isMinimized: false,
    isBusy: false,
    sessionId: null,    // current conversation thread (Phase 7)
    pendingDraft: null, // TicketDraft awaiting user confirmation
    messages: [], // [{role: 'user'|'ai'|'error', node: HTMLElement}]
    historyOpen: false,
    sessions: [],       // SessionSummary[] from GET /api/ai/sessions
    loadToken: 0,       // bumps on every thread switch; stale loads are dropped
    restoredFor: null,  // username whose saved thread was already restored
};

let dom = null;

// ==================== Helpers ====================

const hasAllowedRole = () => {
    const Auth = window.Auth;
    if (!Auth || typeof Auth.hasRole !== 'function') return false;
    return ALLOWED_ROLES.some((role) => {
        try {
            return Auth.hasRole(role);
        } catch (e) {
            return false;
        }
    });
};

const getAuthUser = () => {
    const Auth = window.Auth;
    if (Auth && typeof Auth.getCurrentUser === 'function') {
        try {
            return Auth.getCurrentUser();
        } catch (e) {
            return null;
        }
    }
    return null;
};

const buildDom = () => {
    // Floating button
    const fab = document.createElement('button');
    fab.className = 'ai-fab hidden';
    fab.type = 'button';
    fab.id = 'ai-fab';
    fab.setAttribute('aria-label', 'Mở trợ lý AI');
    fab.setAttribute('title', 'Trợ lý AI');
    fab.innerHTML = '<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="lucide lucide-message-circle-text" aria-hidden="true"><path d="M3.086 17.509a2 2 0 00-.094-1.167 10 10 0 114.777 4.719 2 2 0 00-1.099-.092l-3.413.998a1 1 0 01-1.236-1.168z"/><path d="M8 12h9"/><path d="M8 16h5"/><path d="M8 8h7"/></svg>';

    // Panel
    const panel = document.createElement('aside');
    panel.className = 'ai-panel hidden';
    panel.id = 'ai-panel';
    panel.setAttribute('role', 'dialog');
    panel.setAttribute('aria-label', 'Trợ lý AI');
    panel.setAttribute('aria-modal', 'false');

    const header = document.createElement('header');
    header.className = 'ai-panel-header';

    const headerText = document.createElement('div');
    headerText.className = 'ai-panel-header-text';
    const title = document.createElement('strong');
    title.textContent = 'Trợ lý AI';
    const subtitle = document.createElement('span');
    subtitle.textContent = 'Trợ lý IT Helpdesk';
    headerText.appendChild(title);
    headerText.appendChild(subtitle);

    const headerActions = document.createElement('div');
    headerActions.className = 'ai-panel-header-actions';
    const historyBtn = document.createElement('button');
    historyBtn.type = 'button';
    historyBtn.id = 'ai-history-btn';
    historyBtn.setAttribute('aria-label', 'Lịch sử trò chuyện');
    historyBtn.setAttribute('title', 'Lịch sử trò chuyện');
    historyBtn.setAttribute('aria-expanded', 'false');
    historyBtn.setAttribute('aria-controls', 'ai-history');
    historyBtn.textContent = '☰';
    headerActions.appendChild(historyBtn);
    const minBtn = document.createElement('button');
    minBtn.type = 'button';
    minBtn.id = 'ai-minimize-btn';
    minBtn.setAttribute('aria-label', 'Thu nhỏ');
    minBtn.setAttribute('title', 'Thu nhỏ');
    minBtn.textContent = '−';
    const closeBtn = document.createElement('button');
    closeBtn.type = 'button';
    closeBtn.id = 'ai-close-btn';
    closeBtn.setAttribute('aria-label', 'Đóng');
    closeBtn.setAttribute('title', 'Đóng');
    closeBtn.textContent = '×';
    headerActions.appendChild(minBtn);
    headerActions.appendChild(closeBtn);

    header.appendChild(headerText);
    header.appendChild(headerActions);

    const body = document.createElement('div');
    body.className = 'ai-panel-body';
    body.id = 'ai-panel-body';

    const footer = document.createElement('footer');
    footer.className = 'ai-panel-footer';

    const inputRow = document.createElement('div');
    inputRow.className = 'ai-input-row';
    const textarea = document.createElement('textarea');
    textarea.id = 'ai-input';
    textarea.rows = 1;
    textarea.maxLength = MAX_QUESTION_LENGTH;
    textarea.placeholder = 'Nhập câu hỏi của bạn...';
    textarea.setAttribute('aria-label', 'Câu hỏi cho AI Assistant');
    const sendBtn = document.createElement('button');
    sendBtn.type = 'button';
    sendBtn.id = 'ai-send-btn';
    sendBtn.className = 'ai-send-btn';
    sendBtn.setAttribute('aria-label', 'Gửi câu hỏi');
    sendBtn.textContent = '➤';
    inputRow.appendChild(textarea);
    inputRow.appendChild(sendBtn);

    const hint = document.createElement('div');
    hint.className = 'ai-hint';
    hint.textContent = 'Enter để gửi, Shift + Enter để xuống dòng.';

    footer.appendChild(inputRow);
    footer.appendChild(hint);

    // History drawer (left of the chat column)
    const history = document.createElement('aside');
    history.className = 'ai-history';
    history.id = 'ai-history';
    history.setAttribute('aria-label', 'Lịch sử trò chuyện');
    const newChatBtn = document.createElement('button');
    newChatBtn.type = 'button';
    newChatBtn.className = 'ai-history-new';
    newChatBtn.textContent = '+ Cuộc trò chuyện mới';
    const historyList = document.createElement('div');
    historyList.className = 'ai-history-list';
    history.appendChild(newChatBtn);
    history.appendChild(historyList);

    panel.appendChild(history);
    panel.appendChild(header);
    panel.appendChild(body);
    panel.appendChild(footer);

    document.body.appendChild(fab);
    document.body.appendChild(panel);

    return {
        fab,
        panel,
        header,
        historyBtn,
        history,
        historyList,
        newChatBtn,
        minBtn,
        closeBtn,
        body,
        textarea,
        sendBtn,
    };
};

const renderIntro = () => {
    if (!dom) return;
    const intro = document.createElement('div');
    intro.className = 'ai-intro';

    const greeting = document.createElement('p');
    greeting.textContent =
        'Xin chào! Tôi là trợ lý AI của hệ thống IT Helpdesk.';
    const description = document.createElement('p');
    description.textContent =
        'Bạn có thể hỏi tôi về phiếu hỗ trợ, trạng thái xử lý hoặc thông tin trong hệ thống.';

    intro.appendChild(greeting);
    intro.appendChild(description);

    // PHASE 7.7 hotfix-22: chat-box suggestion buttons
    // are no longer rendered. The render block that
    // used to call INTRO_SUGGESTIONS.forEach(...) has
    // been removed entirely (the constant is also gone
    // from the top of the file). This applies to all
    // roles.

    dom.body.appendChild(intro);
    state.messages.push({ role: 'intro', node: intro });
};

const appendUserMessage = (text) => {
    if (!dom) return null;
    const msg = document.createElement('div');
    msg.className = 'ai-msg ai-msg-user';
    msg.textContent = text; // textContent only — never innerHTML
    dom.body.appendChild(msg);
    scrollBodyToBottom();
    state.messages.push({ role: 'user', node: msg });
    return msg;
};

const appendLoadingMessage = () => {
    if (!dom) return null;
    const msg = document.createElement('div');
    msg.className = 'ai-msg ai-msg-ai ai-loading';
    msg.textContent = 'AI đang xử lý...';
    dom.body.appendChild(msg);
    scrollBodyToBottom();
    // Return the entry (not the node): removeMessage() expects { node }.
    const entry = { role: 'loading', node: msg };
    state.messages.push(entry);
    return entry;
};

const removeMessage = (entry) => {
    if (!entry || !entry.node || !entry.node.parentNode) return;
    entry.node.parentNode.removeChild(entry.node);
};

const appendAiMessage = (response) => {
    if (!dom) return null;
    const msg = document.createElement('div');
    msg.className = 'ai-msg ai-msg-ai';

    const text = document.createElement('div');
    text.textContent = response && response.answer ? response.answer : '';
    msg.appendChild(text);

    // No-context hint (subtle, only when hasContext is explicitly false).
    // Legacy-pipeline hint only: query-plan answers (searchType 'query-plan') already state counts/empty results
    // for every domain, and "ticket" would be wrong for assets/users/changes.
    const isQueryPlan = response && response.metadata && response.metadata.searchType === 'query-plan';
    // Structured actions (ticket draft, missing field) are not lookups either.
    if (response && !isQueryPlan && !response.action && response.hasContext === false && response.contextCount === 0) {
        const hint = document.createElement('div');
        hint.className = 'ai-no-context';
        hint.textContent = 'Không tìm thấy phiếu hỗ trợ liên quan trong dữ liệu hiện có.';
        msg.appendChild(hint);
    }

    // Sources (collapsible)
    if (
        response &&
        Array.isArray(response.sources) &&
        response.sources.length > 0
    ) {
        const details = document.createElement('details');
        details.className = 'ai-msg-sources';
        const summary = document.createElement('summary');
        summary.textContent = `Nguồn tham khảo (${response.sources.length})`;
        details.appendChild(summary);

        const ul = document.createElement('ul');
        response.sources.forEach((source, index) => {
            const li = document.createElement('li');
            const number = document.createElement('span');
            number.className = 'ai-source-number';
            number.textContent = `${index + 1}.`;
            li.appendChild(number);

            // PHASE 6.6.4 - the sourceType discriminator drives the
            // rendered label. TICKET sources use the existing
            // ticketNumber; KNOWN_ERROR sources use the new
            // knownErrorCode; RELATIONAL sources are rendered with a
            // distinct prefix so the user can see the answer came from
            // a deterministic lookup, not a similarity match.
            const sourceType = source.sourceType || 'TICKET';
            const isKnownError = sourceType === 'KNOWN_ERROR';
            const isRelational = sourceType === 'RELATIONAL';

            const prefix = document.createElement('strong');
            if (isKnownError) {
                prefix.textContent = source.knownErrorCode || '—';
                li.classList.add('ai-source-known-error');
            } else if (isRelational) {
                prefix.textContent = (source.knownErrorCode || source.ticketNumber || '—');
                li.classList.add('ai-source-relational');
            } else {
                prefix.textContent = source.ticketNumber || '—';
                li.classList.add('ai-source-ticket');
            }
            li.appendChild(prefix);

            if (source.title) {
                const titleSpan = document.createElement('span');
                titleSpan.textContent = ` — ${source.title}`;
                li.appendChild(titleSpan);
            }

            // Similarity score is rendered for vector sources but
            // suppressed for RELATIONAL lookups (which are exact, not
            // probabilistic).
            if (
                !isRelational &&
                typeof source.similarityScore === 'number' &&
                !Number.isNaN(source.similarityScore)
            ) {
                const score = document.createElement('span');
                score.className = 'ai-source-score';
                const pct = Math.round(source.similarityScore * 100);
                score.textContent = `Độ liên quan: ${pct}%`;
                li.appendChild(score);
            } else if (isRelational) {
                const tag = document.createElement('span');
                tag.className = 'ai-source-score';
                tag.textContent = 'Tra cứu';
                li.appendChild(tag);
            }
            ul.appendChild(li);
        });
        details.appendChild(ul);
        msg.appendChild(details);
    }

    // Lightweight metadata footer (model + processing time only)
    if (
        response &&
        response.metadata &&
        (response.metadata.model || response.metadata.processingTimeMs)
    ) {
        const meta = document.createElement('div');
        meta.className = 'ai-msg-meta';
        const model = response.metadata.model || 'model';
        const ms = response.metadata.processingTimeMs;
        const seconds = typeof ms === 'number' ? (ms / 1000).toFixed(1) : null;
        meta.textContent = seconds
            ? `${model} • ${seconds}s`
            : `${model}`;
        msg.appendChild(meta);
    }

    dom.body.appendChild(msg);
    scrollBodyToBottom();
    state.messages.push({ role: 'ai', node: msg });
    return msg;
};

const appendErrorMessage = (message) => {
    if (!dom) return null;
    const msg = document.createElement('div');
    msg.className = 'ai-msg-error';
    msg.textContent = message;
    dom.body.appendChild(msg);
    scrollBodyToBottom();
    state.messages.push({ role: 'error', node: msg });
    return msg;
};

const scrollBodyToBottom = () => {
    if (!dom) return;
    dom.body.scrollTop = dom.body.scrollHeight;
};

const setBusy = (busy) => {
    state.isBusy = busy;
    if (!dom) return;
    dom.sendBtn.disabled = busy;
    dom.textarea.disabled = busy;
    // Switching threads mid-answer would drop the reply into the wrong thread.
    dom.history.classList.toggle('is-locked', busy);
    dom.newChatBtn.disabled = busy;
    if (busy) {
        dom.textarea.setAttribute('aria-busy', 'true');
    } else {
        dom.textarea.removeAttribute('aria-busy');
    }
};

const clearConversation = () => {
    if (!dom) return;
    // Drop every rendered message
    state.messages.forEach((entry) => removeMessage(entry));
    state.messages = [];
    renderIntro();
};

const openPanel = () => {
    if (!dom) return;
    state.isOpen = true;
    state.isMinimized = false;
    dom.panel.classList.remove('hidden');
    dom.panel.classList.remove('minimized');
    // Reset minBtn label
    dom.minBtn.textContent = '−';
    dom.minBtn.setAttribute('title', 'Thu nhỏ');
    dom.minBtn.setAttribute('aria-label', 'Thu nhỏ');
    restoreLastSession();
    if (state.messages.length === 0) {
        renderIntro();
    }
    scrollBodyToBottom();
    // Focus management
    setTimeout(() => {
        if (dom && !state.isBusy) dom.textarea.focus();
    }, 50);
};

const closePanel = () => {
    if (!dom) return;
    state.isOpen = false;
    state.isMinimized = false;
    dom.panel.classList.add('hidden');
    dom.panel.classList.remove('minimized');
    if (dom.fab) dom.fab.focus();
};

const toggleMinimize = () => {
    if (!dom) return;
    state.isMinimized = !state.isMinimized;
    if (state.isMinimized && state.historyOpen) setHistoryOpen(false);
    if (state.isMinimized) {
        dom.panel.classList.add('minimized');
        dom.minBtn.textContent = '+';
        dom.minBtn.setAttribute('title', 'Mở rộng');
        dom.minBtn.setAttribute('aria-label', 'Mở rộng');
    } else {
        dom.panel.classList.remove('minimized');
        dom.minBtn.textContent = '−';
        dom.minBtn.setAttribute('title', 'Thu nhỏ');
        dom.minBtn.setAttribute('aria-label', 'Thu nhỏ');
        scrollBodyToBottom();
        if (!state.isBusy) dom.textarea.focus();
    }
};

// ==================== Conversation history ====================

const storageKey = () => {
    const user = getAuthUser();
    return user && user.username ? `ticketing.ai.session.${user.username}` : null;
};

// localStorage can throw (private mode, blocked site data) — never let it break chat.
const saveSessionId = (id) => {
    const key = storageKey();
    if (!key) return;
    try {
        if (id) localStorage.setItem(key, id);
        else localStorage.removeItem(key);
    } catch (e) { /* ignore */ }
};

const readSessionId = () => {
    const key = storageKey();
    if (!key) return null;
    try {
        return localStorage.getItem(key);
    } catch (e) {
        return null;
    }
};

// Wipe the visible thread (messages, draft cards, loading bubbles) without touching history.
const resetThread = () => {
    if (!dom) return;
    state.loadToken += 1;
    state.messages = [];
    state.pendingDraft = null;
    dom.body.replaceChildren();
};

const newChat = () => {
    if (!dom || state.isBusy) return;
    resetThread();
    state.sessionId = null;
    saveSessionId(null);
    renderIntro();
    renderSessions();
    closeHistoryIfOverlay();
    dom.textarea.focus();
};

const dayBucket = (iso) => {
    const d = iso ? new Date(iso) : null;
    if (!d || Number.isNaN(d.getTime())) return 'Cũ hơn';
    const startOfToday = new Date();
    startOfToday.setHours(0, 0, 0, 0);
    const diffDays = (startOfToday - d) / 86400000;
    if (diffDays <= 0) return 'Hôm nay';
    if (diffDays <= 1) return 'Hôm qua';
    if (diffDays <= 7) return '7 ngày qua';
    return 'Cũ hơn';
};

const renderHistoryStatus = (text, retry) => {
    const p = document.createElement('p');
    p.className = 'ai-history-status';
    p.textContent = text;
    if (retry) {
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'ai-history-retry';
        btn.textContent = 'Thử lại';
        btn.addEventListener('click', loadSessions);
        p.appendChild(document.createTextNode(' '));
        p.appendChild(btn);
    }
    dom.historyList.replaceChildren(p);
};

const renderSessions = () => {
    if (!dom) return;
    if (state.sessions.length === 0) {
        renderHistoryStatus('Chưa có cuộc trò chuyện nào. Hãy đặt câu hỏi đầu tiên.');
        return;
    }
    const frag = document.createDocumentFragment();
    let currentGroup = null;
    let ul = null;
    state.sessions.forEach((s) => {
        const group = dayBucket(s.lastActiveAt || s.createdAt);
        if (group !== currentGroup) {
            currentGroup = group;
            const label = document.createElement('h3');
            label.className = 'ai-history-group';
            label.textContent = group;
            ul = document.createElement('ul');
            frag.appendChild(label);
            frag.appendChild(ul);
        }
        const li = document.createElement('li');
        li.className = 'ai-history-item';
        const isCurrent = s.id === state.sessionId;
        if (isCurrent) li.classList.add('is-current');

        const open = document.createElement('button');
        open.type = 'button';
        open.className = 'ai-history-open';
        open.textContent = s.title || 'Cuộc trò chuyện mới';
        open.title = s.title || '';
        if (isCurrent) open.setAttribute('aria-current', 'true');
        open.addEventListener('click', () => openSession(s.id));

        const del = document.createElement('button');
        del.type = 'button';
        del.className = 'ai-history-delete';
        del.setAttribute('aria-label', `Xóa cuộc trò chuyện "${s.title || ''}"`);
        del.title = 'Xóa cuộc trò chuyện';
        del.textContent = '×';
        del.addEventListener('click', () => deleteSession(s));

        li.appendChild(open);
        li.appendChild(del);
        ul.appendChild(li);
    });
    dom.historyList.replaceChildren(frag);
};

const loadSessions = async () => {
    if (!dom) return;
    if (state.sessions.length === 0) renderHistoryStatus('Đang tải lịch sử...');
    try {
        const data = await request(`${SESSIONS_ENDPOINT}?limit=${SESSIONS_LIMIT}`);
        state.sessions = Array.isArray(data) ? data : [];
        renderSessions();
    } catch (err) {
        renderHistoryStatus('Không tải được lịch sử.', true);
    }
};

const openSession = async (id, { silent = false } = {}) => {
    if (!dom || !id || state.isBusy) return;
    resetThread();
    const token = state.loadToken;
    state.sessionId = id;
    renderSessions();
    closeHistoryIfOverlay();
    const loadingEntry = appendLoadingMessage();
    try {
        // shortcut: loads the whole thread at once; add paging if threads grow past ~200 messages.
        const data = await request(`${SESSIONS_ENDPOINT}/${encodeURIComponent(id)}/messages`);
        if (token !== state.loadToken) return; // user switched threads meanwhile
        removeMessage(loadingEntry);
        state.messages = [];
        saveSessionId(id);
        const rows = Array.isArray(data) ? data : [];
        rows.forEach((m) => {
            if (m.role === 'USER') appendUserMessage(m.content || '');
            // Plain text only: old draft/confirm cards are not rebuilt, so they can't create duplicate tickets.
            else if (m.role === 'ASSISTANT') appendAiMessage({ answer: m.content || '' });
        });
        if (rows.length === 0) renderIntro();
    } catch (err) {
        if (token !== state.loadToken) return;
        removeMessage(loadingEntry);
        state.sessionId = null;
        saveSessionId(null);
        renderIntro();
        if (Number(err && err.status) === 404) {
            if (!silent) Toast.error('Cuộc trò chuyện không còn tồn tại.');
            loadSessions();
        } else if (!silent) {
            Toast.error('Không tải được cuộc trò chuyện. Vui lòng thử lại.');
        }
        renderSessions();
    }
};

const deleteSession = async (session) => {
    if (!dom || state.isBusy) return;
    const ok = await Dialog.confirm(
        `Xóa cuộc trò chuyện "${session.title || 'Cuộc trò chuyện mới'}"?`,
        { title: 'Xóa cuộc trò chuyện', okText: 'Xóa', cancelText: 'Giữ lại', danger: true }
    );
    if (!ok) return;
    try {
        await request(`${SESSIONS_ENDPOINT}/${encodeURIComponent(session.id)}`, { method: 'DELETE' });
    } catch (err) {
        // 404 means it is already gone — treat as deleted; anything else keeps the row.
        if (Number(err && err.status) !== 404) {
            Toast.error('Không xóa được cuộc trò chuyện. Vui lòng thử lại.');
            return;
        }
    }
    state.sessions = state.sessions.filter((s) => s.id !== session.id);
    if (state.sessionId === session.id) newChat();
    else renderSessions();
    Toast.success('Đã xóa cuộc trò chuyện.');
};

const isNarrow = () => window.matchMedia('(max-width: 720px)').matches;

const setHistoryOpen = (open) => {
    if (!dom) return;
    state.historyOpen = open;
    dom.panel.classList.toggle('with-history', open);
    dom.historyBtn.setAttribute('aria-expanded', String(open));
    if (open) loadSessions();
};

const closeHistoryIfOverlay = () => {
    if (state.historyOpen && isNarrow()) setHistoryOpen(false);
};

// Resume the user's last thread once per login.
const restoreLastSession = () => {
    const user = getAuthUser();
    const username = user && user.username;
    if (!username || state.restoredFor === username) return;
    state.restoredFor = username;
    const saved = readSessionId();
    if (saved && !state.sessionId && !state.messages.some((m) => m.role === 'user')) {
        openSession(saved, { silent: true });
    }
};

// ==================== API call ====================

const submitQuestion = async (rawQuestion) => {
    if (!dom || state.isBusy) return;

    const question = (rawQuestion || '').trim();
    if (!question) {
        appendErrorMessage('Vui lòng nhập câu hỏi.');
        return;
    }
    if (question.length < MIN_QUESTION_LENGTH) {
        appendErrorMessage(
            `Câu hỏi quá ngắn (tối thiểu ${MIN_QUESTION_LENGTH} ký tự).`
        );
        return;
    }
    if (question.length > MAX_QUESTION_LENGTH) {
        appendErrorMessage(
            `Câu hỏi quá dài (tối đa ${MAX_QUESTION_LENGTH} ký tự).`
        );
        return;
    }

    // Clear intro on first real interaction so the conversation feels real
    if (state.messages.length === 1 && state.messages[0].role === 'intro') {
        state.messages.forEach((entry) => removeMessage(entry));
        state.messages = [];
    }

    appendUserMessage(question);
    // Drop any stale validation error left in the body
    state.messages
        .filter((entry) => entry.role === 'error')
        .forEach((entry) => removeMessage(entry));
    state.messages = state.messages.filter((entry) => entry.role !== 'error');

    // Clear the input only after the question is accepted, so the user
    // can still see what they sent and (on error) copy/retry it.
    dom.textarea.value = '';
    autoResizeTextarea();

    setBusy(true);
    const loadingEntry = appendLoadingMessage();

    try {
        // Phase 7: include sessionId so the server can keep the
        // conversation history across calls. The server returns the
        // id on the first response; we cache it and resend.
        const body = { question };
        if (state.sessionId) body.sessionId = state.sessionId;
        const data = await request(ENDPOINT, {
            method: 'POST',
            body: JSON.stringify(body),
            timeout: AI_REQUEST_TIMEOUT_MS,
        });

        removeMessage(loadingEntry);
        if (
            data &&
            typeof data === 'object' &&
            Object.prototype.hasOwnProperty.call(data, 'answer')
        ) {
            if (data.sessionId) {
                state.sessionId = data.sessionId;
                saveSessionId(data.sessionId);
            }
            handleServerAction(data);
            // Refresh order/title so the active thread sits on top.
            if (state.historyOpen) loadSessions();
        } else {
            appendErrorMessage(
                AI_ERROR_MESSAGES['LLM_MALFORMED_RESPONSE'] || FALLBACK_ERROR_MESSAGE
            );
        }
    } catch (err) {
        removeMessage(loadingEntry);
        const code = err && err.errorCode ? String(err.errorCode) : '';
        const status = err && err.status ? Number(err.status) : 0;

        let message = FALLBACK_ERROR_MESSAGE;
        if (status === 401) {
            // The api.js wrapper already triggers logout on 401 with token;
            // surface a friendly message here without redirecting.
            message =
                'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại để sử dụng AI Assistant.';
        } else if (status === 403 || code === 'ACCESS_DENIED') {
            message = AI_ERROR_MESSAGES['ACCESS_DENIED'] || FALLBACK_ERROR_MESSAGE;
        } else if (AI_ERROR_MESSAGES[code]) {
            message = AI_ERROR_MESSAGES[code];
        } else if (code === 'VALIDATION_ERROR') {
            message =
                (err && err.message) ||
                AI_ERROR_MESSAGES['VALIDATION_ERROR'] ||
                FALLBACK_ERROR_MESSAGE;
        }

        appendErrorMessage(message);
    } finally {
        setBusy(false);
    }
};

// ==================== Phase 7: structured actions ====================

/**
 * Phase 7 - the server can return a structured action alongside
 * the normal text answer. Render the appropriate UI:
 *   - CONFIRM_TICKET_DRAFT  -> preview form + "Create ticket" button
 *   - ASK_MISSING_FIELD     -> highlighted prompt for the missing field
 *   - TICKET_CREATED        -> success card with the new ticket number
 *   - null / unknown        -> plain text answer
 */
const handleServerAction = (data) => {
    const action = data && data.action ? String(data.action) : null;
    const payloadRaw = data && data.actionPayload ? String(data.actionPayload) : null;
    let payload = null;
    if (payloadRaw) {
        try { payload = JSON.parse(payloadRaw); } catch (e) { payload = null; }
    }
    if (action === 'CONFIRM_TICKET_DRAFT' && payload) {
        state.pendingDraft = payload;
        appendAiMessage(data);
        appendDraftPreview(payload);
        return;
    }
    if (action === 'ASK_MISSING_FIELD' && payload && payload.field) {
        appendAiMessage(data);
        appendMissingFieldHint(payload.field);
        return;
    }
    if (action === 'TICKET_CREATED' && payload && payload.ticketNumber) {
        state.pendingDraft = null;
        appendAiMessage(data);
        return;
    }
    appendAiMessage(data);
};

const appendMissingFieldHint = (field) => {
    if (!dom) return;
    const hint = document.createElement('div');
    hint.className = 'ai-msg ai-msg-ai ai-missing-field';
    const label = fieldLabel(field);
    hint.textContent = `👉 Cần cung cấp: ${label}`;
    dom.body.appendChild(hint);
    scrollBodyToBottom();
};

const fieldLabel = (f) => {
    switch (f) {
        case 'title': return 'tieu de van de';
        case 'description': return 'mo ta chi tiet';
        case 'priority': return 'muc do khan cap (LOW/MEDIUM/HIGH/URGENT/CRITICAL)';
        case 'category': return 'loai (SOFTWARE/ACCESS/NETWORK/HARDWARE/SECURITY/EMAIL/ACCOUNT/OTHER)';
        default: return f;
    }
};

const appendDraftPreview = (draft) => {
    if (!dom) return;
    const wrap = document.createElement('div');
    wrap.className = 'ai-msg ai-msg-ai ai-draft-preview';

    const heading = document.createElement('strong');
    heading.textContent = '📝 Phiếu hỗ trợ sẽ tạo:';
    wrap.appendChild(heading);

    const fields = [
        ['Tiêu đề', draft.title],
        ['Mô tả', draft.description],
        ['Mức độ', draft.priority],
        ['Loại', draft.category],
    ];
    fields.forEach(([k, v]) => {
        const row = document.createElement('div');
        row.className = 'ai-draft-row';
        const kk = document.createElement('span');
        kk.className = 'ai-draft-key';
        kk.textContent = k + ':';
        const vv = document.createElement('span');
        vv.className = 'ai-draft-val';
        vv.textContent = v == null ? '(trong)' : String(v);
        row.appendChild(kk);
        row.appendChild(vv);
        wrap.appendChild(row);
    });

    const actions = document.createElement('div');
    actions.className = 'ai-draft-actions';

    const confirmBtn = document.createElement('button');
    confirmBtn.type = 'button';
    confirmBtn.className = 'ai-draft-confirm';
    confirmBtn.textContent = '✓ Tạo phiếu hỗ trợ';
    confirmBtn.addEventListener('click', () => confirmDraft());

    const cancelBtn = document.createElement('button');
    cancelBtn.type = 'button';
    cancelBtn.className = 'ai-draft-cancel';
    cancelBtn.textContent = 'Hủy';
    cancelBtn.addEventListener('click', () => {
        state.pendingDraft = null;
        wrap.remove();
    });

    actions.appendChild(confirmBtn);
    actions.appendChild(cancelBtn);
    wrap.appendChild(actions);

    dom.body.appendChild(wrap);
    scrollBodyToBottom();
};

const confirmDraft = async () => {
    if (!state.sessionId || !state.pendingDraft) return;
    if (state.isBusy) return;
    setBusy(true);
    try {
        const data = await request(
            `/api/ai/sessions/${encodeURIComponent(state.sessionId)}/confirm-ticket`,
            {
                method: 'POST',
                body: JSON.stringify({
                    title: state.pendingDraft.title,
                    description: state.pendingDraft.description,
                    priority: state.pendingDraft.priority,
                    category: state.pendingDraft.category,
                }),
                timeout: 30000,
            }
        );
        if (data && data.action === 'TICKET_CREATED') {
            state.pendingDraft = null;
            appendAiMessage(data);
        } else if (data && data.answer) {
            appendAiMessage(data);
        } else {
            appendErrorMessage('Tạo phiếu hỗ trợ thất bại. Vui lòng thử lại.');
        }
    } catch (err) {
        appendErrorMessage(
            (err && err.message) ||
            'Tạo phiếu hỗ trợ thất bại. Vui lòng thử lại.'
        );
    } finally {
        setBusy(false);
    }
};

// PHASE 7.7 hotfix-22: submitSuggestion removed.
// It was only used by the chat-box suggestion buttons
// (now removed) and is no longer reachable.

const autoResizeTextarea = () => {
    if (!dom) return;
    dom.textarea.style.height = 'auto';
    const next = Math.min(
        dom.textarea.scrollHeight,
        120 // matches CSS max-height
    );
    dom.textarea.style.height = `${next}px`;
};

// ==================== Ticket-detail entry point ====================

const ensureAskTicketBanner = () => {
    if (!dom) return;
    if (document.getElementById('ai-ask-ticket-banner')) return;
    const details = document.getElementById('ticket-details');
    if (!details) return;

    const banner = document.createElement('div');
    banner.className = 'ai-ask-ticket hidden';
    banner.id = 'ai-ask-ticket-banner';

    const text = document.createElement('div');
    const strong = document.createElement('strong');
    strong.textContent = 'Bạn muốn hỏi AI về phiếu hỗ trợ này?';
    text.appendChild(strong);

    const btn = document.createElement('button');
    btn.type = 'button';
    btn.id = 'ai-ask-ticket-btn';
    btn.textContent = 'Hỏi AI';
    btn.addEventListener('click', () => {
        const ticket = window.selectedTicket;
        if (!ticket) return;
        const ticketNumber = ticket.ticketNumber || '';
        if (!ticketNumber) return;
        const suggestion = `Ticket ${ticketNumber} đang ở trạng thái gì?`;
        if (!state.isOpen) {
            openPanel();
        }
        dom.textarea.value = suggestion;
        dom.textarea.focus();
        autoResizeTextarea();
        scrollBodyToBottom();
    });

    banner.appendChild(text);
    banner.appendChild(btn);

    // Anchor the banner inside the ticket-actions section (or fallback)
    const actions = document.getElementById('ticket-actions');
    const parent = actions || details;
    if (parent && parent.parentNode) {
        parent.parentNode.insertBefore(banner, parent.nextSibling);
    } else {
        details.appendChild(banner);
    }
};

const updateAskTicketBanner = () => {
    if (!dom) return;
    const banner = document.getElementById('ai-ask-ticket-banner');
    if (!banner) return;
    const ticket = window.selectedTicket;
    if (ticket && ticket.ticketNumber) {
        banner.classList.remove('hidden');
    } else {
        banner.classList.add('hidden');
    }
};

// ==================== Visibility ====================

const applyRoleGate = () => {
    if (!dom) return;
    const allowed = hasAllowedRole();
    // Logout or account switch: never leave the previous user's thread on screen.
    const user = getAuthUser();
    const username = allowed && user ? user.username : null;
    if (state.restoredFor && state.restoredFor !== username && !state.isBusy) {
        resetThread();
        state.sessionId = null;
        state.sessions = [];
        state.restoredFor = null;
        if (state.historyOpen) setHistoryOpen(false);
    }
    if (allowed) {
        dom.fab.classList.remove('hidden');
        if (state.isOpen) {
            dom.panel.classList.remove('hidden');
        }
    } else {
        dom.fab.classList.add('hidden');
        // Hide panel if it was open; also drop the ticket-detail banner
        dom.panel.classList.add('hidden');
        state.isOpen = false;
        const banner = document.getElementById('ai-ask-ticket-banner');
        if (banner) banner.classList.add('hidden');
    }
};

// ==================== Event wiring ====================

const attachEvents = () => {
    if (!dom) return;

    dom.fab.addEventListener('click', () => {
        if (state.isOpen && !state.isMinimized) {
            closePanel();
        } else if (state.isOpen && state.isMinimized) {
            toggleMinimize();
        } else {
            openPanel();
        }
    });

    dom.minBtn.addEventListener('click', toggleMinimize);
    dom.closeBtn.addEventListener('click', closePanel);
    dom.historyBtn.addEventListener('click', () => setHistoryOpen(!state.historyOpen));
    dom.newChatBtn.addEventListener('click', newChat);

    dom.sendBtn.addEventListener('click', () => {
        const value = dom.textarea.value;
        submitQuestion(value);
        // Clear the textarea on successful send so the next question
        // starts empty. On error the catch block already restored focus.
        if (!state.isBusy) {
            // submitQuestion set busy before returning; if not busy the
            // call returned early (validation error) — leave text as-is.
        } else {
            dom.textarea.value = '';
            autoResizeTextarea();
        }
    });

    dom.textarea.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            submitQuestion(dom.textarea.value);
            if (state.isBusy) {
                dom.textarea.value = '';
                autoResizeTextarea();
            }
        }
    });

    dom.textarea.addEventListener('input', autoResizeTextarea);

    // ESC closes the panel (or restores from minimized)
    document.addEventListener('keydown', (e) => {
        if (e.key !== 'Escape') return;
        if (!state.isOpen) return;
        if (document.querySelector('.app-dialog:not(.hidden)')) return; // let the confirm dialog handle Esc
        if (state.historyOpen) {
            setHistoryOpen(false);
            dom.historyBtn.focus();
        } else if (state.isMinimized) {
            toggleMinimize();
        } else {
            closePanel();
        }
    });
};

// ==================== Bootstrap ====================

export const init = () => {
    if (state.initialized) return;

    if (!document.body) return;
    dom = buildDom();
    attachEvents();
    applyRoleGate();
    ensureAskTicketBanner();
    state.initialized = true;

    // Keep the role gate + ticket banner in sync with the rest of the app.
    // Re-check when role-affecting UI re-renders (e.g. login/logout).
    const observer = new MutationObserver(() => {
        applyRoleGate();
        updateAskTicketBanner();
    });
    observer.observe(document.body, {
        childList: true,
        subtree: true,
        attributes: false,
    });
};

// Expose a small surface so other modules can ask the assistant to ask
// about the currently selected ticket without reaching into internals.
export const askAboutCurrentTicket = () => {
    if (!state.initialized) return;
    if (!dom) return;
    if (!hasAllowedRole()) return;
    const ticket = window.selectedTicket;
    if (!ticket || !ticket.ticketNumber) return;
    if (!state.isOpen) {
        openPanel();
    }
    dom.textarea.value = `Ticket ${ticket.ticketNumber} đang ở trạng thái gì?`;
    autoResizeTextarea();
    dom.textarea.focus();
};

// Re-evaluate the role gate from outside (e.g. after login)
export const refreshRoleGate = () => {
    applyRoleGate();
    updateAskTicketBanner();
};

// Default export keeps the rest of the app consistent with the pattern
// used by SlaPolicy / Escalation / Incident / etc.
const AIAssistant = {
    init,
    askAboutCurrentTicket,
    refreshRoleGate,
};

window.AIAssistant = AIAssistant;
export default AIAssistant;