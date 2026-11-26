const reducedMotion = window.matchMedia(
    "(prefers-reduced-motion: reduce)"
);

let desiredEmptyState = null;
let workspaceTimer = null;
let transitionId = 0;
let composerFrame = null;
let composerTimer = null;
let activeComposer = null;
let activeComposerFinish = null;
let workspaceElements = null;

function cancelComposerMotion(composer) {
    if (composerFrame !== null) {
        window.cancelAnimationFrame(composerFrame);
        composerFrame = null;
    }

    if (composerTimer !== null) {
        window.clearTimeout(composerTimer);
        composerTimer = null;
    }

    if (activeComposer) {
        activeComposer.removeEventListener(
            "transitionend",
            activeComposerFinish
        );
        activeComposer.style.transition = "";
        activeComposer.style.transform = "";
    }

    activeComposer = composer;
    activeComposerFinish = null;
}

function applyWorkspaceState(workspace, isEmpty) {
    workspace.classList.toggle("workspace-empty", isEmpty);
    workspace.classList.toggle("workspace-active", !isEmpty);
}

function animateComposer(composer, previousTop, nextTop) {
    cancelComposerMotion(composer);

    if (reducedMotion.matches) {
        composer.style.transition = "";
        composer.style.transform = "";
        activeComposer = null;
        activeComposerFinish = null;
        return;
    }

    const delta = previousTop - nextTop;

    if (!delta) {
        activeComposer = null;
        return;
    }

    const id = transitionId;
    const finish = (event) => {
        if (
            event &&
            (event.target !== composer || event.propertyName !== "transform")
        ) {
            return;
        }

        if (id !== transitionId) {
            return;
        }

        if (composerTimer !== null) {
            window.clearTimeout(composerTimer);
            composerTimer = null;
        }

        composer.removeEventListener("transitionend", finish);
        composer.style.transition = "";
        composer.style.transform = "";
        activeComposer = null;
        activeComposerFinish = null;
    };

    activeComposer = composer;
    activeComposerFinish = finish;
    composer.style.transition = "none";
    composer.style.transform = `translateY(${delta}px)`;
    composer.getBoundingClientRect();

    composerFrame = window.requestAnimationFrame(() => {
        composerFrame = null;

        if (id !== transitionId) {
            return;
        }

        composer.addEventListener("transitionend", finish);
        composer.style.transition =
            "transform var(--duration-slow) var(--ease-out)";
        composer.style.transform = "none";
        composerTimer = window.setTimeout(finish, 320);
    });
}

export function transitionWorkspace(
    isEmpty,
    { workspace, composer, heading, suggestions }
) {
    workspaceElements = {
        workspace,
        composer,
        heading,
        suggestions
    };

    if (desiredEmptyState === null) {
        desiredEmptyState = isEmpty;
        applyWorkspaceState(workspace, isEmpty);
        return;
    }

    if (desiredEmptyState === isEmpty) {
        return;
    }

    desiredEmptyState = isEmpty;
    const id = ++transitionId;

    if (workspaceTimer !== null) {
        window.clearTimeout(workspaceTimer);
        workspaceTimer = null;
    }

    cancelComposerMotion(composer);
    heading.classList.remove("motion-fade-out");
    suggestions.classList.remove("motion-fade-out");
    workspace.classList.remove("workspace-revealing");

    if (reducedMotion.matches) {
        applyWorkspaceState(workspace, isEmpty);
        return;
    }

    if (!isEmpty) {
        heading.classList.add("motion-fade-out");
        suggestions.classList.add("motion-fade-out");

        workspaceTimer = window.setTimeout(() => {
            workspaceTimer = null;

            if (id !== transitionId) {
                return;
            }

            heading.classList.remove("motion-fade-out");
            suggestions.classList.remove("motion-fade-out");

            const previousTop = composer.getBoundingClientRect().top;
            applyWorkspaceState(workspace, false);
            const nextTop = composer.getBoundingClientRect().top;

            animateComposer(composer, previousTop, nextTop);
        }, 150);
        return;
    }

    applyWorkspaceState(workspace, true);
    workspace.classList.add("workspace-revealing");
    heading.getBoundingClientRect();
    suggestions.getBoundingClientRect();
    composerFrame = window.requestAnimationFrame(() => {
        composerFrame = null;

        if (id === transitionId) {
            workspace.classList.remove("workspace-revealing");
        }
    });
}

reducedMotion.addEventListener("change", (event) => {
    if (!event.matches || !workspaceElements) {
        return;
    }

    transitionId += 1;

    if (workspaceTimer !== null) {
        window.clearTimeout(workspaceTimer);
        workspaceTimer = null;
    }

    const {
        workspace,
        composer,
        heading,
        suggestions
    } = workspaceElements;

    heading.classList.remove("motion-fade-out");
    suggestions.classList.remove("motion-fade-out");
    workspace.classList.remove("workspace-revealing");
    applyWorkspaceState(workspace, desiredEmptyState);
    cancelComposerMotion(composer);
    activeComposer = null;
    activeComposerFinish = null;
});
