import { afterEach, describe, expect, it, vi } from "vitest";
import {
    clearSession,
    clearSessionLocal,
    getPayload,
    isAuthenticated,
    onAuthenticated,
    readCsrfToken,
    refreshSession,
} from "./authStorage";

/**
 * Correctif sécurité du 06/11/2026 : la session vit dans un cookie `HttpOnly` illisible par le JS ; l'identité
 * vient de `GET /users/me`, la déconnexion passe par `POST /auth/logout`.
 */
afterEach(() => {
    vi.unstubAllGlobals();
    clearSessionLocal();
    localStorage.clear();
    document.cookie = "XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT";
});

function meResponse() {
    return new Response(JSON.stringify({ id: 7, email: "a@b.c", role: "USER" }), { status: 200 });
}

describe("authStorage — session par cookie", () => {
    it("refreshSession lit l'identité depuis /users/me", async () => {
        vi.stubGlobal("fetch", vi.fn(async () => meResponse()));

        const user = await refreshSession();

        expect(user?.userId).toBe(7);
        expect(user?.role).toBe("USER");
        expect(getPayload()?.email).toBe("a@b.c");
        expect(isAuthenticated()).toBe(true);
    });

    it("refreshSession sur 401 laisse la session fermée", async () => {
        vi.stubGlobal("fetch", vi.fn(async () => new Response("{}", { status: 401 })));

        expect(await refreshSession()).toBeNull();
        expect(isAuthenticated()).toBe(false);
    });

    it("onAuthenticated mémorise l'heure de connexion (ex-claim iat) côté client", async () => {
        vi.stubGlobal("fetch", vi.fn(async () => meResponse()));

        await onAuthenticated();

        expect(getPayload()?.iat).toBeTypeOf("number");
    });

    it("clearSession appelle POST /auth/logout et ferme la session", async () => {
        const fetchMock = vi.fn(async (url: string) => (url.endsWith("/users/me") ? meResponse() : new Response(null, { status: 200 })));
        vi.stubGlobal("fetch", fetchMock);
        await onAuthenticated();
        expect(isAuthenticated()).toBe(true);

        await clearSession();

        expect(isAuthenticated()).toBe(false);
        expect(fetchMock).toHaveBeenCalledWith(
            "/api/v1/auth/logout",
            expect.objectContaining({ method: "POST", credentials: "include" }),
        );
    });

    it("readCsrfToken lit le cookie XSRF-TOKEN", () => {
        document.cookie = "XSRF-TOKEN=abc123";
        expect(readCsrfToken()).toBe("abc123");
    });
});
