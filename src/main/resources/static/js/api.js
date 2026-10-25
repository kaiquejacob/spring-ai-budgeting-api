// Usuário de demonstração local. Não representa autenticação.
const DEMO_USER_ID = "11111111-1111-1111-1111-111111111111";

function createUrl(path, sessionId) {
    const url = new URL(path, window.location.origin);

    if (sessionId) {
        url.searchParams.set("sessionId", sessionId);
    }

    return url;
}

async function request(path, sessionId, options = {}) {
    let response;

    try {
        response = await fetch(createUrl(path, sessionId), {
            ...options,
            headers: {
                "Accept": "application/json",
                "X-User-Id": DEMO_USER_ID,
                ...options.headers
            }
        });
    } catch {
        throw new Error(
            "Não foi possível receber uma resposta do servidor. Verifique a conexão e se a aplicação está em execução."
        );
    }

    const data = await response.json().catch(() => null);

    if (!response.ok) {
        throw new Error(
            typeof data?.message === "string"
                ? data.message
                : `A solicitação falhou (HTTP ${response.status}).`
        );
    }

    if (!data || typeof data !== "object") {
        throw new Error("O servidor retornou uma resposta inválida.");
    }

    return data;
}

export async function sendChatMessage(message, sessionId) {
    const data = await request(
        "/transactions/ai/chat",
        sessionId,
        {
            method: "POST",
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

async function sendFile(path, file, sessionId) {
    const formData = new FormData();
    formData.append("file", file);

    const data = await request(path, sessionId, {
        method: "POST",
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

export function sendReceipt(file, sessionId) {
    return sendFile(
        "/transactions/ai/receipt",
        file,
        sessionId
    );
}

export function sendAudio(file, sessionId) {
    return sendFile(
        "/transactions/ai",
        file,
        sessionId
    );
}

export async function confirmTransaction(token, sessionId) {
    const data = await request(
        `/transactions/${encodeURIComponent(token)}/confirm`,
        sessionId,
        {
            method: "POST"
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