import { state } from "./state.js";

export const dom = {
    form: document.querySelector("#chat-form"),
    input: document.querySelector("#message-input"),
    sendButton: document.querySelector("#send-button"),
    newConversationButton: document.querySelector(
        "#new-conversation-button"
    ),
    messages: document.querySelector("#messages"),
    workspace: document.querySelector("#workspace"),
    status: document.querySelector("#chat-status"),
    errorBox: document.querySelector("#chat-error"),
    attachmentPreview: document.querySelector("#attachment-preview"),
    attachmentName: document.querySelector("#attachment-name"),
    removeAttachmentButton: document.querySelector(
        "#remove-attachment-button"
    ),
    scrollContainer: document.querySelector(".conversation-scroll"),
    suggestions: document.querySelectorAll(".suggestion"),
    uploadTriggers: document.querySelectorAll("[data-upload-trigger]"),
    fileInputs: {
        receipt: document.querySelector("#receipt-input"),
        audio: document.querySelector("#audio-input")
    }
};

export function scrollToLatest() {
    dom.scrollContainer.scrollTop = dom.scrollContainer.scrollHeight;
}

export function clearError() {
    dom.errorBox.textContent = "";
    dom.errorBox.hidden = true;
}

export function showError(message) {
    dom.errorBox.textContent = message;
    dom.errorBox.hidden = false;
}

export function errorMessage(error) {
    return error instanceof Error
        ? error.message
        : "Ocorreu uma falha inesperada.";
}

export function element(tag, className, text) {
    const node = document.createElement(tag);

    if (className) {
        node.className = className;
    }

    if (text !== undefined) {
        node.textContent = text;
    }

    return node;
}

export function updateControls() {
    dom.input.disabled = state.busy;
    dom.sendButton.disabled =
        state.busy ||
        (!dom.input.value.trim() && state.selectedAttachment === null);
    dom.newConversationButton.disabled = state.busy;

    dom.suggestions.forEach((button) => {
        button.disabled = state.busy;
    });

    dom.uploadTriggers.forEach((button) => {
        button.disabled =
            state.busy ||
            state.pendingProposal !== null ||
            state.selectedAttachment !== null;
    });

    dom.removeAttachmentButton.disabled = state.busy;

    if (state.pendingProposal) {
        state.pendingProposal.button.disabled =
            state.busy || state.pendingProposal.state !== "pending";
    }

    dom.sendButton.setAttribute(
        "aria-label",
        state.busy ? "Aguarde" : "Enviar mensagem"
    );
    dom.sendButton.title = state.busy ? "Aguarde" : "Enviar mensagem";
    dom.form.setAttribute("aria-busy", String(state.busy));
}
