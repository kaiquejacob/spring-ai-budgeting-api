import { getConversationMessages, getConversations } from "./api.js";
import { expireSession } from "./auth.js";
import { dom, element } from "./dom.js";
import { createMessage } from "./render.js";
import { state } from "./state.js";

const assistantNavigation = document.querySelector("#assistant-navigation");
const toggle = document.querySelector("#recent-toggle");
const workspace = document.querySelector("#history-workspace");
const title = document.querySelector("#history-title");
const back = document.querySelector("#history-back");
const scroll = document.querySelector("#history-scroll");

const recent = createPager("#recent-items", "#recent-status", "#recent-more", "Carregar mais");
const messages = createPager("#history-messages", "#history-status", "#history-more", "Carregar mais mensagens");
let selection = null;

function createPager(items, status, more, label) {
    return {
        items: document.querySelector(items),
        status: document.querySelector(status),
        more: document.querySelector(more),
        label,
        request: null,
        nextPage: 0
    };
}

function setPanelOpen(open) {
    toggle.setAttribute("aria-expanded", String(open));
}

function resetPager(pager) {
    pager.request?.abort();
    pager.request = null;
    pager.nextPage = 0;
    pager.items.replaceChildren();
    pager.status.textContent = "";
    pager.more.hidden = true;
    pager.more.removeAttribute("aria-disabled");
}

function updateNavigation() {
    const buttons = [assistantNavigation, ...recent.items.querySelectorAll("button")];
    for (const button of buttons) {
        const current = button === assistantNavigation ? selection === null : button.dataset.id === selection;
        button.classList.toggle("navigation-item-active", current);
        if (current) button.setAttribute("aria-current", "page");
        else button.removeAttribute("aria-current");
    }
}

function showAssistant() {
    selection = null;
    resetPager(messages);
    workspace.hidden = true;
    dom.workspace.hidden = false;
    updateNavigation();
}

function visibleNavigation() {
    return toggle.getClientRects().length ? toggle : assistantNavigation;
}

export function returnToAssistant() {
    if (selection === null) return;
    showAssistant();
    (dom.input.disabled ? visibleNavigation() : dom.input).focus();
}

export function clearHistory() {
    showAssistant();
    resetPager(recent);
    setPanelOpen(false);
}

function conversationButton({ conversationId, preview }) {
    const item = element("li");
    const button = element("button", "navigation-item");
    button.type = "button";
    button.dataset.id = conversationId;
    button.title = preview;
    button.append(element("span", "recent-preview", preview));
    button.addEventListener("click", () => {
        selection = conversationId;
        resetPager(messages);
        updateNavigation();
        setPanelOpen(false);
        dom.workspace.hidden = true;
        workspace.hidden = false;
        scroll.scrollTop = 0;
        title.focus();
        loadMessages();
    });
    item.append(button);
    return item;
}

async function loadPage(pager, page, fetchPage, render, copy) {
    const account = state.accountSession?.signal;
    if (!account || account.aborted) return;
    pager.request?.abort();
    const request = new AbortController();
    pager.request = request;
    const signal = AbortSignal.any([account, request.signal]);
    pager.nextPage = page;
    pager.status.textContent = copy.loading;
    pager.more.setAttribute("aria-disabled", "true");

    try {
        const data = await fetchPage(page, signal);
        if (signal.aborted) return;

        const nodes = data.items.flatMap(render);
        const focused = document.activeElement;
        if (page === 0) pager.items.replaceChildren(...nodes);
        else pager.items.append(...nodes);
        updateNavigation();
        pager.nextPage = data.page + 1;
        pager.status.textContent = pager.items.childElementCount ? "" : copy.empty;
        pager.more.textContent = pager.label;
        if (focused === pager.more && !data.hasNext) {
            const next = nodes[0]?.querySelector("button") ?? nodes[0];
            if (next && next.tagName !== "BUTTON") next.tabIndex = -1;
            (next ?? (pager === messages ? title : visibleNavigation())).focus();
        } else if (page === 0 && focused.dataset.id) {
            const replacement = [...pager.items.querySelectorAll("button")]
                .find(button => button.dataset.id === focused.dataset.id);
            (replacement ?? visibleNavigation()).focus();
        }
        pager.more.hidden = !data.hasNext;
    } catch (error) {
        if (signal.aborted) return;
        if (error.status === 401) {
            expireSession();
            return;
        }
        const unavailable = copy.unavailable && error.status === 404;
        pager.status.textContent = unavailable ? copy.unavailable : copy.error;
        if (unavailable && document.activeElement === pager.more) title.focus();
        pager.more.textContent = "Tentar novamente";
        pager.more.hidden = Boolean(unavailable);
    } finally {
        if (!signal.aborted) {
            pager.request = null;
            pager.more.removeAttribute("aria-disabled");
        }
    }
}

export function refreshRecent(page = 0) {
    return loadPage(recent, page, getConversations, conversationButton, {
        loading: "Carregando conversas…",
        empty: "Nenhuma conversa recente.",
        error: "Não foi possível carregar as conversas."
    });
}

function loadMessages() {
    const id = selection;
    if (id === null) return;
    return loadPage(messages, messages.nextPage, (page, signal) => getConversationMessages(id, page, signal),
        ({ question, answer }) => [
            createMessage("Você", question, true),
            createMessage("Assistente", answer)
        ], {
            loading: "Carregando mensagens…",
            empty: "Esta conversa ainda não tem mensagens no histórico.",
            error: "Não foi possível carregar as mensagens.",
            unavailable: "Esta conversa está indisponível."
        });
}

export function initializeHistory() {
    toggle.addEventListener("click", () => setPanelOpen(toggle.getAttribute("aria-expanded") !== "true"));
    assistantNavigation.addEventListener("click", returnToAssistant);
    back.addEventListener("click", returnToAssistant);
    recent.more.addEventListener("click", () => {
        if (!recent.request) refreshRecent(recent.nextPage);
    });
    messages.more.addEventListener("click", () => {
        if (!messages.request) loadMessages();
    });
}
