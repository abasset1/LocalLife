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

describe("apiFetch — jeton anti-CSRF absent ou refusé", () => {
    it("cookie XSRF-TOKEN absent : le redemande avant l'écriture, puis l'envoie", async () => {
        const fetchMock = vi.fn(async (input: string, _init?: RequestInit) => {
            if (input === "/api/v1/users/me") {
                document.cookie = "XSRF-TOKEN=jeton-frais";
            }
            return new Response("{}", { status: 200 });
        });
        vi.stubGlobal("fetch", fetchMock);

        await apiFetch("/api/v1/admin/import", { method: "POST" });

        expect(fetchMock.mock.calls.map((call) => call[0])).toEqual(["/api/v1/users/me", "/api/v1/admin/import"]);
        const init = fetchMock.mock.calls[1][1] as RequestInit;
        expect(new Headers(init.headers).get("X-XSRF-TOKEN")).toBe("jeton-frais");
    });

    it("jeton refusé (403 X-CSRF-Rejected) : en obtient un nouveau et rejoue l'écriture une fois", async () => {
        document.cookie = "XSRF-TOKEN=jeton-perime";
        let imports = 0;
        const fetchMock = vi.fn(async (input: string, _init?: RequestInit) => {
            if (input === "/api/v1/users/me") {
                document.cookie = "XSRF-TOKEN=jeton-frais";
                return new Response("{}", { status: 200 });
            }
            imports += 1;
            return imports === 1
                ? new Response("{}", { status: 403, headers: { "X-CSRF-Rejected": "true" } })
                : new Response("[]", { status: 200 });
        });
        vi.stubGlobal("fetch", fetchMock);

        const response = await apiFetch("/api/v1/admin/import", { method: "POST" });

        expect(response.status).toBe(200);
        expect(fetchMock.mock.calls.map((call) => call[0])).toEqual([
            "/api/v1/admin/import", "/api/v1/users/me", "/api/v1/admin/import",
        ]);
        expect(new Headers((fetchMock.mock.calls[2][1] as RequestInit).headers).get("X-XSRF-TOKEN")).toBe("jeton-frais");
    });

    it("vrai refus de rôle (403 sans X-CSRF-Rejected) : pas de nouvel essai", async () => {
        document.cookie = "XSRF-TOKEN=jeton-csrf";
        const fetchMock = vi.fn(async () => new Response("{}", { status: 403 }));
        vi.stubGlobal("fetch", fetchMock);

        const response = await apiFetch("/api/v1/admin/import", { method: "POST" });

        expect(response.status).toBe(403);
        expect(fetchMock).toHaveBeenCalledTimes(1);
    });

    it("lecture : jamais de jeton redemandé ni de nouvel essai", async () => {
        const fetchMock = vi.fn(async () => new Response("{}", { status: 403, headers: { "X-CSRF-Rejected": "true" } }));
        vi.stubGlobal("fetch", fetchMock);

        await apiFetch("/api/v1/admin/import/sync-states");

        expect(fetchMock).toHaveBeenCalledTimes(1);
    });
});
