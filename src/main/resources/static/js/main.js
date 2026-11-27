import { dom, updateControls } from "./dom.js";
import { initializeAuth } from "./auth.js";
import { confirmProposal, resetConversation, submitMessage } from "./chat.js";
import { state } from "./state.js";
import {
    handleFileSelection,
    removeAttachment,
    triggerUpload,
    updateAttachmentPreview
} from "./upload.js";
import { updateWorkspaceState } from "./render.js";
import { initializeHistory } from "./history.js";

dom.form.addEventListener("submit", submitMessage);

dom.input.addEventListener("input", updateControls);

dom.input.addEventListener("keydown", (event) => {
    if (
        event.key === "Enter" &&
        !event.shiftKey &&
        !event.isComposing
    ) {
        event.preventDefault();

        if (
            !state.busy &&
            (dom.input.value.trim() || state.selectedAttachment !== null)
        ) {
            dom.form.requestSubmit();
        }
    }
});

dom.suggestions.forEach((button) => {
    button.addEventListener("click", () => {
        if (state.busy) {
            return;
        }

        const prompt = button.querySelector(".suggestion-description");
        dom.input.value = prompt.dataset.prompt || prompt.textContent.trim();

        updateControls();
        dom.input.focus();
    });
});

dom.uploadTriggers.forEach((button) => {
    button.addEventListener("click", () => {
        triggerUpload(button);
    });
});

Object.entries(dom.fileInputs).forEach(([kind, fileInput]) => {
    fileInput.addEventListener("change", () => {
        handleFileSelection(kind, fileInput);
    });
});

dom.removeAttachmentButton.addEventListener("click", removeAttachment);

dom.messages.addEventListener("click", (event) => {
    const button = event.target.closest(".transaction-card .button");
    const proposal = state.pendingProposal;

    if (button && proposal?.card.contains(button)) {
        confirmProposal(proposal);
    }
});

dom.newConversationButton.addEventListener("click", resetConversation);

updateWorkspaceState();
updateAttachmentPreview();
updateControls();
initializeHistory();
initializeAuth();
