// Identificação para demonstração local. Não representa autenticação.
const DEMO_USER_ID = "11111111-1111-1111-1111-111111111111";

export async function sendChatMessage(message, sessionId) {
    const url = new URL(
        "/transactions/ai/chat",
        window.location.origin
    );

    if (sessionId) {
        url.searchParams.set("sessionId", sessionId);
    }

    let response;

    try {
        response = await fetch(url, {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                "Accept": "application/json",
                "X-User-Id": DEMO_USER_ID
            },
            body: JSON.stringify({ message })
        });
    } catch {
        throw new Error(
            "Não foi possível conectar ao servidor. Verifique se a aplicação está em execução."
        );
    }

    const data = await response.json().catch(() => null);

    if (!response.ok) {
        const serverMessage =
            typeof data?.message === "string"
                ? data.message
                : `A solicitação falhou (HTTP ${response.status}).`;

        throw new Error(serverMessage);
    }

    if (
        typeof data?.answer !== "string" ||
        !data.answer.trim() ||
        typeof data?.sessionId !== "string" ||
        !data.sessionId.trim()
    ) {
        throw new Error(
            "O servidor retornou uma resposta incompleta. Tente iniciar uma nova conversa."
        );
    }

    return data;
}