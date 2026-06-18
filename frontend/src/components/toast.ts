// Lightweight, dependency-free toast notifications.
//
// Replaces native, blocking `window.alert()` calls with non-blocking,
// auto-dismissing toasts. Implemented as a tiny imperative module (no React
// context/provider needed) so it can be called from anywhere — event handlers,
// SSE callbacks, services — without prop drilling. Styles live in index.css.

export type ToastType = "info" | "success" | "error";

const CONTAINER_ID = "app-toast-container";
const DEFAULT_DURATION_MS = 5000;

function ensureContainer(): HTMLDivElement {
    let container = document.getElementById(CONTAINER_ID) as HTMLDivElement | null;

    if (!container) {
        container = document.createElement("div");
        container.id = CONTAINER_ID;
        container.className = "app-toast-container";
        container.setAttribute("aria-live", "polite");
        container.setAttribute("role", "status");
        document.body.appendChild(container);
    }

    return container;
}

/**
 * Shows a non-blocking toast. Returns immediately. The toast auto-dismisses
 * after `durationMs` and can also be dismissed by clicking it.
 */
export function showToast(
    message: string,
    type: ToastType = "info",
    durationMs: number = DEFAULT_DURATION_MS,
): void {
    if (!message) {
        return;
    }

    const container = ensureContainer();

    const toast = document.createElement("div");
    toast.className = `app-toast app-toast--${type}`;
    toast.textContent = message;

    let dismissed = false;
    let timer: number | undefined;

    const remove = () => {
        if (dismissed) {
            return;
        }
        dismissed = true;
        if (timer !== undefined) {
            window.clearTimeout(timer);
        }
        toast.classList.remove("app-toast--visible");
        // Wait for the fade-out transition before detaching.
        window.setTimeout(() => {
            toast.remove();
            if (container.childElementCount === 0) {
                container.remove();
            }
        }, 200);
    };

    toast.addEventListener("click", remove);
    container.appendChild(toast);

    // Trigger the enter transition on the next frame.
    requestAnimationFrame(() => toast.classList.add("app-toast--visible"));

    timer = window.setTimeout(remove, durationMs);
}
