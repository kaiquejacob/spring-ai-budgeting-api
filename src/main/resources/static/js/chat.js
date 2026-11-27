import {
    clearError,
    dom,
    element,
    errorMessage,
    showError,
    updateControls
} from "./dom.js";
import { confirmTransaction, sendAudio, sendChatMessage, sendReceipt } from "./api.js";
import {
    addMessage,
    clearConversation,
    renderConfirmation,
    renderFileResponse,
    showLoading,
    showLoadingError,
    updateSession
} from "./render.js";
import { state } from "./state.js";
import { expireSession } from "./auth.js";
import { updateAttachmentPreview } from "./upload.js";
import { refreshRecent, returnToAssistant } from "./history.js";

function beginOperation(message) {
    state.busy = true;
    clearError();
    dom.status.textContent = message;
    updateControls();
}

function finishOperation() {
    state.busy = false;
    dom.status.textContent = "";
    updateControls();
    dom.input.focus();
}

function stopForSessionChange(signal, error) {
    if (signal.aborted) return true;
    if (error.status !== 401) return false;
    expireSession();
    return true;
}

function updateProposalBadge(entry, text) {
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
        entry.badge.textContent = text;
        return;
    }

    if (entry.badgeFrame !== undefined) {
        window.cancelAnimationFrame(entry.badgeFrame);
    }

    entry.badge.style.opacity = "0";
    entry.badgeFrame = window.requestAnimationFrame(() => {
        entry.badge.textContent = text;
        entry.badge.style.opacity = "1";
        entry.badgeFrame = undefined;
    });
}

export async function confirmProposal(entry) {
    const signal = state.accountSession?.signal;
    if (
        !signal || state.busy ||
        state.pendingProposal !== entry ||
        entry.state !== "pending"
    ) {
        return;
    }

    entry.state = "confirming";
    entry.button.textContent = "Confirmando…";

    beginOperation("Registrando transação…");

    try {
        const data = await confirmTransaction(
            entry.token,
            entry.sessionId,
            signal
        );
        if (signal.aborted) return;

        entry.state = "confirmed";
        entry.card.classList.add("is-confirmed");
        entry.badge.classList.add("is-confirmed");
        updateProposalBadge(entry, "Registrada");
        entry.button.textContent = "Registro confirmado";

        entry.help.textContent =
            "Transação registrada. Este botão não pode ser utilizado novamente.";

        state.pendingProposal = null;

        renderConfirmation(data);
    } catch (error) {
        if (stopForSessionChange(signal, error)) return;
        entry.state = "unverified";
        updateProposalBadge(entry, "Confirmação não verificada");
        entry.button.textContent = "Verifique as movimentações";

        entry.help.textContent =
            "Não foi possível verificar a confirmação. Consulte as últimas transações antes de gerar outra proposta.";

        showError(
            `${errorMessage(error)} Não houve reenvio automático.`
        );
    } finally {
        if (!signal.aborted) {
            entry.button.disabled = true;
            finishOperation();
        }
    }
}

export async function submitMessage(event) {
    event.preventDefault();

    const message = dom.input.value.trim();
    const signal = state.accountSession?.signal;

    if (!signal || state.busy || (!message && state.selectedAttachment === null)) {
        return;
    }

    if (state.selectedAttachment) {
        await submitAttachment(message, signal);
        return;
    }

    beginOperation("Consultando informações…");

    const userArticle = addMessage("Você", message, true);
    dom.input.value = "";
    updateControls();
    showLoading(dom.status.textContent);

    try {
        const data = await sendChatMessage(message, state.sessionId, signal);
        if (signal.aborted) return;

        updateSession(data.sessionId);
        addMessage("Assistente", data.answer);
        refreshRecent();
    } catch (error) {
        if (stopForSessionChange(signal, error)) return;
        showLoadingError(errorMessage(error));
        userArticle.append(
            element(
                "span",
                "message-delivery-status",
                "Resposta não recebida."
            )
        );

        dom.input.value = message;
        showError(errorMessage(error));
    } finally {
        if (!signal.aborted) finishOperation();
    }
}

async function submitAttachment(message, signal) {
    const attachment = state.selectedAttachment;
    const { file, kind } = attachment;
    const isReceipt = kind === "receipt";
    const origin = isReceipt ? "Recibo" : "Áudio";
    let messageArticle = null;
    let messageDelivered = false;

    const operationMessage = message
        ? "Enviando mensagem e analisando arquivo…"
        : isReceipt
            ? "Analisando recibo…"
            : "Transcrevendo e interpretando áudio…";
    beginOperation(operationMessage);

    if (message) {
        messageArticle = addMessage("Você", message, true);
        dom.input.value = "";
    }

    const attachmentArticle = addMessage(
        "Você",
        `${origin} enviado: ${file.name}`,
        true
    );
    updateControls();
    showLoading(operationMessage);

    try {
        if (message) {
            const chatResponse = await sendChatMessage(message, state.sessionId, signal);
            if (signal.aborted) return;

            updateSession(chatResponse.sessionId);
            addMessage("Assistente", chatResponse.answer);
            refreshRecent();
            messageDelivered = true;
            showLoading(operationMessage);
        }

        const data = isReceipt
            ? await sendReceipt(file, state.sessionId, signal)
            : await sendAudio(file, state.sessionId, signal);
        if (signal.aborted) return;

        renderFileResponse(data, origin);
        state.selectedAttachment = null;
        updateAttachmentPreview();
    } catch (error) {
        if (stopForSessionChange(signal, error)) return;
        showLoadingError(
            `${errorMessage(error)} Não houve reenvio automático.`
        );
        if (messageArticle && !messageDelivered) {
            messageArticle.append(
                element(
                    "span",
                    "message-delivery-status",
                    "Resposta não recebida."
                )
            );
            dom.input.value = message;
        }

        attachmentArticle.append(
            element(
                "span",
                "message-delivery-status",
                "Não foi possível concluir o processamento."
            )
        );

        showError(
            `${errorMessage(error)} Não houve reenvio automático.`
        );
    } finally {
        if (!signal.aborted) finishOperation();
    }
}

export function resetConversation() {
    if (state.busy) {
        return;
    }

    if (state.pendingProposal) {
        const proceed = window.confirm(
            "Esta conversa possui uma proposta pendente ou uma confirmação não verificada. " +
            "Iniciar outra conversa não cancela propostas nem desfaz registros. " +
            "Se houve falha na confirmação, confira as movimentações antes de registrar novamente. Continuar?"
        );

        if (!proceed) {
            return;
        }
    }

    clearConversation();
    returnToAssistant();
    dom.input.focus();
}
