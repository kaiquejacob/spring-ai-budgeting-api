export const state = {
    accountSession: null,
    sessionId: null,
    busy: false,
    pendingProposal: null,
    selectedAttachment: null
};

export function sessionChanged(nextSessionId) {
    return Boolean(
        state.sessionId && state.sessionId !== nextSessionId
    );
}

export function resetConversationState() {
    state.sessionId = null;
    state.pendingProposal = null;
    state.selectedAttachment = null;
}
