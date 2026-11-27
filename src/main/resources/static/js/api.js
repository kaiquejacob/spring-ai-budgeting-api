let csrfRequest;

export function invalidateCsrf() {
    csrfRequest = undefined;
}

function getCsrf() {
    if (!csrfRequest) {
        const pending = request("/auth/csrf").then((data) => {
            if (typeof data.headerName !== "string" || !data.headerName ||
                typeof data.token !== "string" || !data.token) {
                throw new Error("O servidor não retornou um token de segurança válido.");
            }
            return data;
        });
        csrfRequest = pending;
        pending.catch(() => {
            if (csrfRequest === pending) invalidateCsrf();
        });
    }
    return csrfRequest;
}

function createUrl(path, sessionId) {
    const url = new URL(path, window.location.origin);

    if (sessionId) {
        url.searchParams.set("sessionId", sessionId);
    }

    return url;
}

async function request(path, sessionId, options = {}) {
    const headers = new Headers(options.headers);
    headers.set("Accept", "application/json");
    const csrf = options.method === "POST" ? getCsrf() : null;
    if (csrf) {
        const { headerName, token } = await csrf;
        headers.set(headerName, token);
    }
    options.signal?.throwIfAborted();

    let response;

    try {
        response = await fetch(createUrl(path, sessionId), {
            ...options,
            credentials: "same-origin",
            headers
        });
    } catch {
        throw new Error(
            "Não foi possível receber uma resposta do servidor. Verifique a conexão e se a aplicação está em execução."
        );
    }

    if (response.status === 204) return null;
    const data = await response.json().catch(() => null);

    if (!response.ok) {
        if (response.status === 403 && csrf && csrf === csrfRequest) invalidateCsrf();
        const error = new Error(
            typeof data?.message === "string"
                ? data.message
                : response.status === 403
                    ? "A solicitação foi bloqueada (403). Verifique os dados antes de tentar novamente."
                    : `A solicitação falhou (HTTP ${response.status}).`
        );
        error.status = response.status;
        error.code = data?.error;
        throw error;
    }

    if (!data || typeof data !== "object") {
        throw new Error("O servidor retornou uma resposta inválida.");
    }

    return data;
}

export function getConversations(page, signal) {
    return request(`/conversations?page=${page}`, null, { signal });
}

export function getConversationMessages(id, page, signal) {
    return request(`/conversations/${encodeURIComponent(id)}/messages?page=${page}`, null, { signal });
}

export async function getAccount() {
    const account = await request("/auth/me");
    if (typeof account?.id !== "string" || !account.id ||
        typeof account.email !== "string" || !account.email) {
        throw new Error("O servidor não retornou os dados da conta.");
    }
    return account;
}

export function registerAccount(email, password) {
    return request("/auth/register", null, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, password })
    });
}

export async function login(email, password) {
    await request("/auth/login", null, {
        method: "POST",
        body: new URLSearchParams({ email, password })
    });
    invalidateCsrf();
}

export async function logout(signal) {
    await request("/auth/logout", null, { method: "POST", signal });
    if (!signal.aborted) invalidateCsrf();
}

export async function sendChatMessage(message, sessionId, signal) {
    const data = await request(
        "/transactions/ai/chat",
        sessionId,
        {
            method: "POST",
            signal,
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify({ message })
        }
    );

    if (
        typeof data.answer !== "string" ||
        !data.answer.trim() ||
        typeof data.sessionId !== "string" ||
        !data.sessionId
    ) {
        throw new Error(
            "O servidor retornou uma resposta de chat incompleta."
        );
    }

    return data;
}

async function sendFile(path, file, sessionId, signal) {
    const formData = new FormData();
    formData.append("file", file);

    const data = await request(path, sessionId, {
        method: "POST",
        signal,
        body: formData
    });

    if (
        typeof data.intent !== "string" ||
        typeof data.sessionId !== "string" ||
        !data.sessionId
    ) {
        throw new Error(
            "O servidor retornou uma resposta de processamento incompleta."
        );
    }

    return data;
}

export function sendReceipt(file, sessionId, signal) {
    return sendFile(
        "/transactions/ai/receipt",
        file,
        sessionId,
        signal
    );
}

export function sendAudio(file, sessionId, signal) {
    return sendFile(
        "/transactions/ai",
        file,
        sessionId,
        signal
    );
}

export async function confirmTransaction(token, sessionId, signal) {
    const data = await request(
        `/transactions/${encodeURIComponent(token)}/confirm`,
        sessionId,
        {
            method: "POST",
            signal
        }
    );

    if (
        data.intent !== "CREATE_TRANSACTION" ||
        !data.createdTransaction
    ) {
        throw new Error(
            "Não foi possível verificar o resultado da confirmação."
        );
    }

    return data;
}
