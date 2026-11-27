import { getAccount, invalidateCsrf, login, logout, registerAccount } from "./api.js";
import { dom, errorMessage, showError } from "./dom.js";
import { clearConversation } from "./render.js";
import { state } from "./state.js";
import { clearHistory, refreshRecent } from "./history.js";

const app = document.querySelector("#assistant-app");
const access = document.querySelector("#access");
const title = document.querySelector("#access-title");
const status = document.querySelector("#access-status");
const error = document.querySelector("#access-error");
const retry = document.querySelector("#access-retry");
const form = document.querySelector("#access-form");
const email = document.querySelector("#access-email");
const password = document.querySelector("#access-password");
const submit = document.querySelector("#access-submit");
const switchMode = document.querySelector("#access-switch");
const userEmail = document.querySelector("#user-email");
const avatar = document.querySelector("#user-avatar");
const logoutButton = document.querySelector("#logout");

let registering = false;
let submitting = false;

function feedback(node, message = "") {
    node.textContent = message;
    node.hidden = !message;
}

function setMode(createAccount) {
    registering = createAccount;
    title.textContent = registering ? "Crie sua conta" : "Entre no seu assistente";
    submit.textContent = registering ? "Criar conta" : "Entrar";
    switchMode.textContent = registering ? "Voltar para entrar" : "Criar conta";
    password.autocomplete = registering ? "new-password" : "current-password";
    if (registering) {
        password.minLength = 8;
        password.maxLength = 72;
    } else {
        password.removeAttribute("minlength");
        password.removeAttribute("maxlength");
    }
    password.value = "";
    feedback(error);
    feedback(status);
}

function showLogin(message = "") {
    form.hidden = false;
    retry.hidden = true;
    setMode(false);
    feedback(status, message);
    email.focus();
}

function showAccount(account) {
    state.accountSession = new AbortController();
    userEmail.textContent = account.email;
    userEmail.title = account.email;
    avatar.textContent = account.email[0].toUpperCase();
    logoutButton.disabled = false;
    form.reset();
    access.hidden = true;
    app.hidden = false;
    dom.input.focus();
    refreshRecent();
}

function endSession(message) {
    state.accountSession?.abort();
    state.accountSession = null;
    invalidateCsrf();
    app.hidden = true;
    clearHistory();
    clearConversation();
    userEmail.textContent = "";
    userEmail.removeAttribute("title");
    avatar.textContent = "";
    form.reset();
    access.hidden = false;
    showLogin(message);
}

export function expireSession() {
    endSession("Sua sessão expirou. Entre novamente.");
}

async function sessionExpiredAfterLogoutFailure(failure) {
    if (failure.status === 401) return true;
    if (failure.status !== 403) return false;
    try {
        await getAccount();
        return false;
    } catch (verificationFailure) {
        return verificationFailure.status === 401;
    }
}

async function restoreSession() {
    form.hidden = true;
    retry.hidden = true;
    feedback(error);
    feedback(status, "Verificando sessão…");
    try {
        showAccount(await getAccount());
    } catch (failure) {
        if (failure.status === 401) {
            showLogin();
            return;
        }
        feedback(status);
        feedback(error, "Não foi possível verificar sua sessão. Tente novamente.");
        retry.hidden = false;
        retry.focus();
    }
}

export function initializeAuth() {
    retry.addEventListener("click", restoreSession);
    switchMode.addEventListener("click", () => {
        setMode(!registering);
        email.focus();
    });
    form.addEventListener("submit", async (event) => {
        event.preventDefault();
        if (submitting) return;

        submitting = true;
        submit.disabled = true;
        switchMode.disabled = true;
        form.setAttribute("aria-busy", "true");
        feedback(error);
        feedback(status, registering ? "Criando conta…" : "Entrando…");
        const submittedEmail = email.value;
        const submittedPassword = password.value;
        password.value = "";
        try {
            if (registering) {
                const account = await registerAccount(submittedEmail, submittedPassword);
                email.value = account.email;
                showLogin("Conta criada. Entre com sua senha.");
            } else {
                await login(submittedEmail, submittedPassword);
                await restoreSession();
            }
        } catch (failure) {
            feedback(status);
            feedback(error,
                failure.code === "INVALID_CREDENTIALS" ? "E-mail ou senha inválidos." :
                failure.status === 409 ? "Este e-mail já está cadastrado." : errorMessage(failure));
            password.focus();
        } finally {
            submitting = false;
            submit.disabled = false;
            switchMode.disabled = false;
            form.setAttribute("aria-busy", "false");
        }
    });
    logoutButton.addEventListener("click", async () => {
        const signal = state.accountSession?.signal;
        if (!signal || logoutButton.disabled) return;
        logoutButton.disabled = true;
        try {
            await logout(signal);
            if (!signal.aborted) endSession("Você saiu da sua conta.");
        } catch (failure) {
            if (signal.aborted) return;
            const expired = await sessionExpiredAfterLogoutFailure(failure);
            if (signal.aborted) return;
            if (expired) {
                expireSession();
            } else {
                showError(errorMessage(failure));
                logoutButton.disabled = false;
                logoutButton.focus();
            }
        }
    });
    return restoreSession();
}
