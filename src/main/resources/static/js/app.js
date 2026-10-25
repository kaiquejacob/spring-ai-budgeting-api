import {
    sendChatMessage,
    sendReceipt,
    sendAudio,
    confirmTransaction
} from "./api.js";

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
const uploadTriggers = document.querySelectorAll("[data-upload-trigger]");

const fileInputs = {
    receipt: document.querySelector("#receipt-input"),
    audio: document.querySelector("#audio-input")
};

const currency = new Intl.NumberFormat("pt-BR", {
    style: "currency",
    currency: "BRL"
});

const categoryNames = {
    GROCERIES: "Mercado",
    PHARMA: "Farmácia",
    AUTO: "Automóvel"
};

const periodNames = {
    TODAY: "Hoje",
    YESTERDAY: "Ontem",
    THIS_WEEK: "Esta semana",
    LAST_WEEK: "Semana passada",
    CURRENT_MONTH: "Mês atual",
    LAST_MONTH: "Mês passado",
    ALL_TIME: "Todo o período"
};

let sessionId = null;
let busy = false;
let pendingProposal = null;

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

    uploadTriggers.forEach((button) => {
        button.disabled = busy || pendingProposal !== null;
    });

    if (pendingProposal) {
        pendingProposal.button.disabled =
            busy || pendingProposal.state !== "pending";
    }

    sendButton.textContent = busy ? "Aguarde…" : "Enviar";
    form.setAttribute("aria-busy", String(busy));
}

function beginOperation(message) {
    busy = true;
    clearError();
    welcome.hidden = true;
    status.textContent = message;
    updateControls();
}

function finishOperation() {
    busy = false;
    status.textContent = "";
    updateControls();
    input.focus();
}

function clearError() {
    errorBox.textContent = "";
    errorBox.hidden = true;
}

function showError(message) {
    errorBox.textContent = message;
    errorBox.hidden = false;
}

function errorMessage(error) {
    return error instanceof Error
        ? error.message
        : "Ocorreu uma falha inesperada.";
}

function element(tag, className, text) {
    const node = document.createElement(tag);

    if (className) {
        node.className = className;
    }

    if (text !== undefined) {
        node.textContent = text;
    }

    return node;
}

function addMessage(author, text, userMessage = false) {
    const article = element(
        "article",
        userMessage
            ? "message message-user"
            : "message message-assistant"
    );

    article.append(
        element("span", "message-author", author),
        element(
            "div",
            userMessage ? "message-bubble" : "message-text",
            text
        )
    );

    messages.append(article);
    scrollToLatest();

    return article;
}

function addNotice(text) {
    messages.append(
        element("p", "conversation-notice", text)
    );

    scrollToLatest();
}

function updateSession(nextSessionId) {
    if (sessionId && sessionId !== nextSessionId) {
        addNotice(
            "O servidor iniciou uma nova sessão. O contexto anterior pode não estar disponível."
        );
    }

    sessionId = nextSessionId;
}

function addDetail(list, label, value) {
    const row = document.createElement("div");

    row.append(
        element("dt", "", label),
        element("dd", "", value)
    );

    list.append(row);
}

function formatAmount(amount) {
    return typeof amount === "number" && Number.isFinite(amount)
        ? currency.format(amount)
        : "Valor não informado";
}

function validateProposal(data) {
    const proposal = data.proposedTransaction;

    if (
        typeof data.confirmationToken !== "string" ||
        !data.confirmationToken ||
        !proposal ||
        typeof proposal.description !== "string" ||
        !proposal.description.trim() ||
        typeof proposal.amount !== "number" ||
        !Number.isFinite(proposal.amount) ||
        proposal.amount <= 0 ||
        !Object.hasOwn(categoryNames, proposal.category)
    ) {
        throw new Error(
            "A proposta recebida está incompleta. Nenhum registro foi confirmado pela interface."
        );
    }
}

function renderProposal(data, origin) {
    validateProposal(data);

    const proposal = data.proposedTransaction;

    const article = addMessage(
        "Assistente",
        "Confira os dados extraídos. A transação ainda não foi registrada."
    );

    const card = element("section", "transaction-card");
    card.setAttribute("aria-label", "Proposta de transação");

    const header = element("header", "transaction-header");
    const badge = element(
        "span",
        "status-badge",
        "Aguardando confirmação"
    );

    header.append(
        element("span", "section-label", "Proposta de transação"),
        badge
    );

    const details = element("dl", "transaction-details");

    addDetail(
        details,
        "Categoria",
        categoryNames[proposal.category]
    );

    addDetail(details, "Origem da proposta", origin);

    const footer = element("div", "transaction-footer");

    const help = element(
        "p",
        "",
        "Confira os dados. Confirme em até 5 minutos. Se estiverem incorretos, não confirme."
    );

    const button = element(
        "button",
        "button button-primary",
        "Confirmar registro"
    );

    button.type = "button";
    footer.append(help, button);

    card.append(
        header,
        element("h3", "", proposal.description),
        element(
            "p",
            "transaction-amount",
            currency.format(proposal.amount)
        ),
        details,
        footer
    );

    article.append(card);

    const entry = {
        token: data.confirmationToken,
        sessionId: data.sessionId,
        state: "pending",
        card,
        badge,
        help,
        button
    };

    pendingProposal = entry;

    button.addEventListener("click", () => {
        confirmProposal(entry);
    });

    updateControls();
    scrollToLatest();
}

async function confirmProposal(entry) {
    if (
        busy ||
        pendingProposal !== entry ||
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
        entry.badge.textContent = "Registrada";
        entry.button.textContent = "Registro confirmado";

        entry.help.textContent =
            "Transação registrada. Este botão não pode ser utilizado novamente.";

        pendingProposal = null;

        addMessage(
            "Assistente",
            typeof data.message === "string"
                ? data.message
                : "Transação registrada com sucesso."
        );
    } catch (error) {
        entry.state = "unverified";
        entry.badge.textContent = "Confirmação não verificada";
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

function renderTransactionList(transactions) {
    if (!Array.isArray(transactions)) {
        throw new Error(
            "O servidor não retornou uma lista de transações válida."
        );
    }

    if (transactions.length === 0) {
        addMessage(
            "Assistente",
            "Não foram encontradas transações para essa consulta."
        );
        return;
    }

    const text = transactions.map((transaction) => {
        const description =
            typeof transaction.description === "string"
                ? transaction.description
                : "Transação";

        const category =
            categoryNames[transaction.category] ||
            transaction.category ||
            "Não informada";

        return [
            description,
            `Valor: ${formatAmount(transaction.amount)}`,
            `Categoria: ${category}`
        ].join("\n");
    }).join("\n\n");

    addMessage("Assistente", text);
}

function renderFileResponse(data, origin) {
    updateSession(data.sessionId);

    if (data.confirmationToken && data.proposedTransaction) {
        renderProposal(data, origin);
        return;
    }

    if (data.createdTransaction) {
        addMessage(
            "Assistente",
            [
                data.message || "Transação registrada.",
                data.createdTransaction.description || "",
                formatAmount(data.createdTransaction.amount)
            ].filter(Boolean).join("\n")
        );
        return;
    }

    switch (data.intent) {
        case "SUM_TRANSACTIONS_BY_CATEGORY": {
            if (
                typeof data.totalAmount !== "number" ||
                !Number.isFinite(data.totalAmount)
            ) {
                throw new Error(
                    "O servidor retornou um total inválido."
                );
            }

            const category =
                categoryNames[data.category] ||
                data.category ||
                "Não informada";

            const period =
                periodNames[data.period] ||
                data.period ||
                "Não informado";

            addMessage(
                "Assistente",
                [
                    `Categoria: ${category}`,
                    `Período: ${period}`,
                    `Total: ${currency.format(data.totalAmount)}`
                ].join("\n")
            );

            break;
        }

        case "LIST_TRANSACTIONS":
            renderTransactionList(data.transactions);
            break;

        case "LIST_RECENT_TRANSACTIONS":
            renderTransactionList(data.recentTransactions);
            break;

        case "CREATE_TRANSACTION":
            throw new Error(
                "O servidor não retornou uma proposta válida para confirmar."
            );

        default:
            addMessage(
                "Assistente",
                typeof data.message === "string" && data.message.trim()
                    ? data.message
                    : "Não foi possível interpretar a solicitação."
            );
    }
}

async function submitMessage(event) {
    event.preventDefault();

    const message = input.value.trim();

    if (busy || !message) {
        return;
    }

    beginOperation("Consultando informações…");

    const userArticle = addMessage("Você", message, true);
    input.value = "";
    updateControls();

    try {
        const data = await sendChatMessage(message, sessionId);

        updateSession(data.sessionId);
        addMessage("Assistente", data.answer);
    } catch (error) {
        userArticle.append(
            element(
                "span",
                "message-delivery-status",
                "Resposta não recebida."
            )
        );

        input.value = message;
        showError(errorMessage(error));
    } finally {
        finishOperation();
    }
}

function validateFile(file, kind) {
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

async function uploadFile(file, kind) {
    if (busy || pendingProposal) {
        return;
    }

    clearError();

    try {
        validateFile(file, kind);
    } catch (error) {
        showError(errorMessage(error));
        return;
    }

    const isReceipt = kind === "receipt";
    const origin = isReceipt ? "Recibo" : "Áudio";

    beginOperation(
        isReceipt
            ? "Analisando recibo…"
            : "Transcrevendo e interpretando áudio…"
    );

    const userArticle = addMessage(
        "Você",
        `${origin} enviado: ${file.name}`,
        true
    );

    try {
        const data = isReceipt
            ? await sendReceipt(file, sessionId)
            : await sendAudio(file, sessionId);

        renderFileResponse(data, origin);
    } catch (error) {
        userArticle.append(
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

uploadTriggers.forEach((button) => {
    button.addEventListener("click", () => {
        if (busy || pendingProposal) {
            return;
        }

        const kind = button.dataset.uploadTrigger;
        fileInputs[kind].click();
    });
});

Object.entries(fileInputs).forEach(([kind, fileInput]) => {
    fileInput.addEventListener("change", () => {
        const file = fileInput.files?.[0];

        fileInput.value = "";

        if (file) {
            uploadFile(file, kind);
        }
    });
});

newConversationButton.addEventListener("click", () => {
    if (busy) {
        return;
    }

    if (pendingProposal) {
        const proceed = window.confirm(
            "Esta conversa possui uma proposta pendente ou uma confirmação não verificada. " +
            "Iniciar outra conversa não cancela propostas nem desfaz registros. " +
            "Se houve falha na confirmação, confira as movimentações antes de registrar novamente. Continuar?"
        );

        if (!proceed) {
            return;
        }
    }

    sessionId = null;
    pendingProposal = null;

    messages.replaceChildren();
    welcome.hidden = false;
    input.value = "";
    status.textContent = "";

    Object.values(fileInputs).forEach((fileInput) => {
        fileInput.value = "";
    });

    clearError();
    updateControls();

    scrollContainer.scrollTop = 0;
    input.focus();
});

updateControls();