import {
    clearError,
    dom,
    errorMessage,
    showError,
    updateControls
} from "./dom.js";
import { state } from "./state.js";

export function updateAttachmentPreview() {
    dom.attachmentPreview.hidden = state.selectedAttachment === null;
    dom.attachmentName.textContent =
        state.selectedAttachment?.file.name || "";
}

export function validateFile(file, kind) {
    if (file.size === 0) {
        throw new Error("O arquivo selecionado está vazio.");
    }

    if (file.size > 9 * 1024 * 1024) {
        throw new Error("Selecione um arquivo de até 9 MB.");
    }

    if (kind === "receipt") {
        const validExtension = /\.(jpe?g|png)$/i.test(file.name);

        const validMime =
            !file.type ||
            file.type === "image/jpeg" ||
            file.type === "image/png";

        if (!validExtension || !validMime) {
            throw new Error("Selecione uma imagem JPG ou PNG.");
        }
    } else if (!/\.(mp3|m4a|wav)$/i.test(file.name)) {
        throw new Error("Selecione um áudio MP3, M4A ou WAV.");
    }
}

export function triggerUpload(button) {
    if (state.busy || state.pendingProposal) {
        return;
    }

    const kind = button.dataset.uploadTrigger;
    dom.fileInputs[kind].click();
}

export function handleFileSelection(kind, fileInput) {
    const file = fileInput.files?.[0];

    fileInput.value = "";

    if (!file || state.busy || state.pendingProposal || state.selectedAttachment) {
        return;
    }

    try {
        validateFile(file, kind);
        state.selectedAttachment = { file, kind };
        updateAttachmentPreview();
        clearError();
        updateControls();
    } catch (error) {
        showError(errorMessage(error));
    }
}

export function removeAttachment() {
    if (state.busy || state.selectedAttachment === null) {
        return;
    }

    state.selectedAttachment = null;
    updateAttachmentPreview();
    updateControls();
}
