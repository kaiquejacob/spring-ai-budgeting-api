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
    renderConfirmation,
    renderFileResponse,
    showLoading,
    showLoadingError,
    updateSession,
    updateWorkspaceState
} from "./render.js";
import { resetConversationState, state } from "./state.js";
import { updateAttachmentPreview } from "./upload.js";

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
    if (
        state.busy ||
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
            entry.sessionId
        );

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
        entry.state = "unverified";
        updateProposalBadge(entry, "Confirmação não verificada");
        entry.button.textContent = "Verifique as movimentações";

        entry.help.textContent =
            "Não foi possível verificar a confirmação. Consulte as últimas transações antes de gerar outra proposta.";

        showError(
            `${errorMessage(error)} Não houve reenvio automático.`
        );
    } finally {
        entry.button.disabled = true;
        finishOperation();
    }
}

export async function submitMessage(event) {
    event.preventDefault();

    const message = dom.input.value.trim();

    if (state.busy || (!message && state.selectedAttachment === null)) {
        return;
    }

    if (state.selectedAttachment) {
        await submitAttachment(message);
        return;
    }

    beginOperation("Consultando informações…");

    const userArticle = addMessage("Você", message, true);
    dom.input.value = "";
    updateControls();
    showLoading(dom.status.textContent);

    try {
        const data = await sendChatMessage(message, state.sessionId);

        updateSession(data.sessionId);
        addMessage("Assistente", data.answer);
    } catch (error) {
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
        finishOperation();
    }
}

async function submitAttachment(message) {
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
            const chatResponse = await sendChatMessage(
                message,
                state.sessionId
            );
            updateSession(chatResponse.sessionId);
            addMessage("Assistente", chatResponse.answer);
            messageDelivered = true;
            showLoading(operationMessage);
        }

        const data = isReceipt
            ? await sendReceipt(file, state.sessionId)
            : await sendAudio(file, state.sessionId);

        renderFileResponse(data, origin);
        state.selectedAttachment = null;
        updateAttachmentPreview();
    } catch (error) {
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
        finishOperation();
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

    resetConversationState();

    dom.messages.replaceChildren();
    dom.input.value = "";
    dom.status.textContent = "";

    Object.values(dom.fileInputs).forEach((fileInput) => {
        fileInput.value = "";
    });

    updateAttachmentPreview();
    updateWorkspaceState();
    clearError();
    updateControls();

    dom.scrollContainer.scrollTop = 0;
    dom.input.focus();
}
