import { sendChatMessage } from "./api.js";

const form = document.querySelector("#chat-form");
const input = document.querySelector("#message-input");
const sendButton = document.querySelector("#send-button");
const newConversationButton = document.querySelector(
    "#new-conversation-button"
);
const messages = document.querySelector("#messages");
const welcome = document.querySelector("#welcome");
const status = document.querySelector("#chat-status");
const errorBox = document.querySelector("#chat-error");
const scrollContainer = document.querySelector(".conversation-scroll");
const suggestions = document.querySelectorAll(".suggestion");

let sessionId = null;
let busy = false;

function scrollToLatest() {
    scrollContainer.scrollTop = scrollContainer.scrollHeight;
}

function updateControls() {
    input.disabled = busy;
    sendButton.disabled = busy || !input.value.trim();
    newConversationButton.disabled = busy;

    suggestions.forEach((button) => {
        button.disabled = busy;
    });

    sendButton.textContent = busy ? "Aguarde…" : "Enviar";
    form.setAttribute("aria-busy", String(busy));
}

function clearError() {
    errorBox.textContent = "";
    errorBox.hidden = true;
}

function showError(message) {
    errorBox.textContent = message;
    errorBox.hidden = false;
}

function addMessage(author, text, userMessage = false) {
    const article = document.createElement("article");

    article.className = userMessage
        ? "message message-user"
        : "message message-assistant";

    const authorElement = document.createElement("span");
    authorElement.className = "message-author";
    authorElement.textContent = author;

    const content = document.createElement("div");
    content.className = userMessage
        ? "message-bubble"
        : "message-text";

    content.textContent = text;

    article.append(authorElement, content);
    messages.append(article);

    scrollToLatest();

    return article;
}

function addNotice(text) {
    const notice = document.createElement("p");
    notice.className = "conversation-notice";
    notice.textContent = text;
    messages.append(notice);
}

async function submitMessage(event) {
    event.preventDefault();

    const message = input.value.trim();

    if (busy || !message) {
        return;
    }

    busy = true;
    clearError();
    welcome.hidden = true;

    const userArticle = addMessage("Você", message, true);

    input.value = "";
    status.textContent = "Consultando informações…";
    updateControls();

    try {
        const previousSessionId = sessionId;

        const data = await sendChatMessage(message, sessionId);

        sessionId = data.sessionId;

        if (
            previousSessionId &&
            previousSessionId !== sessionId
        ) {
            addNotice(
                "Uma nova sessão foi iniciada pelo servidor. O contexto anterior pode não estar disponível."
            );
        }

        addMessage("Assistente", data.answer);
    } catch (error) {
        const deliveryStatus = document.createElement("span");
        deliveryStatus.className = "message-delivery-status";
        deliveryStatus.textContent = "Resposta não recebida.";

        userArticle.append(deliveryStatus);

        input.value = message;

        showError(
            error instanceof Error
                ? error.message
                : "Ocorreu uma falha inesperada."
        );
    } finally {
        busy = false;
        status.textContent = "";
        updateControls();
        input.focus();
    }
}

form.addEventListener("submit", submitMessage);

input.addEventListener("input", updateControls);

input.addEventListener("keydown", (event) => {
    if (
        event.key === "Enter" &&
        !event.shiftKey &&
        !event.isComposing
    ) {
        event.preventDefault();

        if (!busy && input.value.trim()) {
            form.requestSubmit();
        }
    }
});

suggestions.forEach((button) => {
    button.addEventListener("click", () => {
        if (busy) {
            return;
        }

        input.value = button
            .querySelector(".suggestion-description")
            .textContent
            .trim();

        updateControls();
        input.focus();
    });
});

newConversationButton.addEventListener("click", () => {
    if (busy) {
        return;
    }

    sessionId = null;
    messages.replaceChildren();
    welcome.hidden = false;
    input.value = "";
    status.textContent = "";
    clearError();
    updateControls();

    scrollContainer.scrollTop = 0;
    input.focus();
});

updateControls();