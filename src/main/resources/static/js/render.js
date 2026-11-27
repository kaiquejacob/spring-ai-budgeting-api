import {
    clearError,
    dom,
    element,
    scrollToLatest,
    updateControls
} from "./dom.js";
import { resetConversationState, sessionChanged, state } from "./state.js";
import { transitionWorkspace } from "./motion.js";
import { updateAttachmentPreview } from "./upload.js";

const currency = new Intl.NumberFormat("pt-BR", {
    style: "currency",
    currency: "BRL"
});

const occurrenceDate = new Intl.DateTimeFormat("pt-BR", {
    timeZone: "America/Sao_Paulo",
    day: "2-digit",
    month: "2-digit",
    year: "numeric"
});

const typeNames = {
    INCOME: "Receita",
    EXPENSE: "Despesa"
};

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

export function updateWorkspaceState() {
    const isEmpty = dom.messages.childElementCount === 0;
    transitionWorkspace(isEmpty, {
        workspace: dom.workspace,
        composer: dom.form,
        heading: dom.workspace.querySelector(".welcome-heading"),
        suggestions: dom.workspace.querySelector(".suggestions")
    });
}

let activeLoading = null;

export function clearConversation() {
    resetConversationState();
    state.busy = false;
    activeLoading = null;
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
}

function removeLoadingAfterFade(loading) {
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
        loading.remove();
        return;
    }

    loading.classList.remove("message-enter");
    loading.classList.add("message-loading-exit");
    loading.addEventListener("animationend", () => loading.remove(), {
        once: true
    });
}

export function showLoading(message) {
    const loading = element(
        "article",
        "message message-assistant message-loading message-enter"
    );
    const content = element("div", "assistant-loading-content");
    const status = element("div", "assistant-loading-status");
    const dot = element("span", "assistant-loading-dot");

    dot.setAttribute("aria-hidden", "true");
    status.append(
        dot,
        element("span", "assistant-loading-text", message)
    );
    content.append(
        status,
        element("span", "assistant-loading-skeleton"),
        element("span", "assistant-loading-skeleton")
    );
    loading.setAttribute("role", "status");
    loading.append(content);
    dom.messages.append(loading);
    updateWorkspaceState();
    scrollToLatest();
    activeLoading = loading;

    return loading;
}

export function showLoadingError(message) {
    if (!activeLoading) {
        return;
    }

    const loading = activeLoading;
    activeLoading = null;
    loading.classList.remove("message-enter");
    loading.classList.remove("message-loading");
    loading.classList.add("message-loading-error");
    loading.replaceChildren(
        element("div", "message-text", message)
    );
}

function dismissActiveLoading() {
    if (!activeLoading) {
        return false;
    }

    const loading = activeLoading;
    activeLoading = null;
    removeLoadingAfterFade(loading);
    return true;
}

export function addMessage(author, text, userMessage = false) {
    const replacesLoading = !userMessage && dismissActiveLoading();
    const article = element(
        "article",
        userMessage
            ? "message message-user"
            : "message message-assistant"
    );
    article.classList.add(
        replacesLoading ? "message-response" : "message-enter"
    );

    article.append(
        element("span", "message-author", author),
        element(
            "div",
            userMessage ? "message-bubble" : "message-text",
            text
        )
    );

    dom.messages.append(article);
    updateWorkspaceState();
    scrollToLatest();

    return article;
}

export function addNotice(text) {
    dom.messages.append(
        element("p", "conversation-notice", text)
    );

    updateWorkspaceState();
    scrollToLatest();
}

export function updateSession(nextSessionId) {
    if (sessionChanged(nextSessionId)) {
        addNotice(
            "O servidor iniciou uma nova sessão. O contexto anterior pode não estar disponível."
        );
    }

    state.sessionId = nextSessionId;
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

function formatType(type) {
    return typeof type === "string" && Object.hasOwn(typeNames, type)
        ? typeNames[type]
        : "Tipo não informado";
}

function formatOccurrence(occurredAt) {
    const isoTimestamp = /^(\d{4}-\d{2}-\d{2})T\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:\d{2})$/;
    const match = typeof occurredAt === "string" ? isoTimestamp.exec(occurredAt) : null;
    if (!match) {
        return "Data não informada";
    }

    const timestamp = Date.parse(occurredAt);
    const calendarDate = new Date(`${match[1]}T00:00:00Z`);
    // Date.parse pode normalizar um dia inválido para o mês seguinte.
    if (!Number.isFinite(timestamp) || calendarDate.toISOString().slice(0, 10) !== match[1]) {
        return "Data não informada";
    }
    return occurrenceDate.format(timestamp);
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

export function renderProposal(data, origin) {
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

    addDetail(details, "Tipo", formatType(proposal.type));
    addDetail(details, "Data da ocorrência", formatOccurrence(proposal.occurredAt));
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
    card.classList.add("proposal-enter");

    state.pendingProposal = {
        token: data.confirmationToken,
        sessionId: data.sessionId,
        state: "pending",
        card,
        badge,
        help,
        button
    };

    updateControls();
    scrollToLatest();
}

export function renderTransactionList(transactions) {
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
            `Categoria: ${category}`,
            `Tipo: ${formatType(transaction.type)}`,
            `Data da ocorrência: ${formatOccurrence(transaction.occurredAt)}`
        ].join("\n");
    }).join("\n\n");

    addMessage("Assistente", text);
}

export function renderConfirmation(data) {
    const transaction = data.createdTransaction;

    addMessage(
        "Assistente",
        [
            typeof data.message === "string" && data.message
                ? data.message
                : "Transação registrada com sucesso.",
            transaction.description || "",
            formatAmount(transaction.amount),
            `Tipo: ${formatType(transaction.type)}`,
            `Data da ocorrência: ${formatOccurrence(transaction.occurredAt)}`
        ].filter(Boolean).join("\n")
    );
}

export function renderFileResponse(data, origin) {
    updateSession(data.sessionId);

    if (data.confirmationToken && data.proposedTransaction) {
        renderProposal(data, origin);
        return;
    }

    if (data.createdTransaction) {
        renderConfirmation(data);
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
