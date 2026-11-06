import { afterEach, describe, expect, it, vi } from "vitest";
import { apiFetch } from "./apiClient";

/**
 * Correctif sécurité du 06/11/2026 : `apiFetch` s'appuie sur le cookie de session `HttpOnly` (plus de jeton en
 * JavaScript) et joint le jeton anti-CSRF `X-XSRF-TOKEN` sur les écritures.
 */
afterEach(() => {
    vi.unstubAllGlobals();
    document.cookie = "XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT";
});

describe("apiFetch", () => {
    it("envoie le cookie de session et aucun en-tête Authorization", async () => {
        const fetchMock = vi.fn(async (_input: string, _init?: RequestInit) => new Response("{}", { status: 200 }));
        vi.stubGlobal("fetch", fetchMock);

        await apiFetch("/api/v1/users/me");

        const init = fetchMock.mock.calls[0][1] as RequestInit;
        expect(init.credentials).toBe("include");
        expect(new Headers(init.headers).has("Authorization")).toBe(false);
    });

    it("ajoute X-XSRF-TOKEN, recopié du cookie, sur une écriture", async () => {
        document.cookie = "XSRF-TOKEN=jeton-csrf";
        const fetchMock = vi.fn(async (_input: string, _init?: RequestInit) => new Response("{}", { status: 200 }));
        vi.stubGlobal("fetch", fetchMock);

        await apiFetch("/api/v1/users/me/password", { method: "POST" });

        const init = fetchMock.mock.calls[0][1] as RequestInit;
        expect(new Headers(init.headers).get("X-XSRF-TOKEN")).toBe("jeton-csrf");
    });

    it("n'ajoute pas de jeton CSRF sur une lecture (GET)", async () => {
        document.cookie = "XSRF-TOKEN=jeton-csrf";
        const fetchMock = vi.fn(async (_input: string, _init?: RequestInit) => new Response("{}", { status: 200 }));
        vi.stubGlobal("fetch", fetchMock);

        await apiFetch("/api/v1/activities");

        const init = fetchMock.mock.calls[0][1] as RequestInit;
        expect(new Headers(init.headers).has("X-XSRF-TOKEN")).toBe(false);
    });

    it("sur 401, redirige vers /login", async () => {
        vi.stubGlobal("fetch", vi.fn(async () => new Response("{}", { status: 401 })));
        const assign = vi.fn();
        const original = window.location;
        Object.defineProperty(window, "location", {
            configurable: true,
            value: { pathname: "/favorites", assign },
        });

        try {
            await apiFetch("/api/v1/users/me/likes");
            expect(assign).toHaveBeenCalledWith("/login");
        } finally {
            Object.defineProperty(window, "location", { configurable: true, value: original });
        }
    });
});
