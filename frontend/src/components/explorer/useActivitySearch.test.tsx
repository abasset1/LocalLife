import { act, renderHook, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { GlobalSearchPage, GlobalSearchRequest } from "./globalSearch";
import { useActivitySearch } from "./useActivitySearch";

interface Call {
    url: string;
    signal: AbortSignal | undefined;
    respond: (body: unknown, status?: number) => void;
    fail: () => void;
}

let calls: Call[];

beforeEach(() => {
    calls = [];
    vi.stubGlobal(
        "fetch",
        vi.fn((url: string, init?: RequestInit) =>
            new Promise<Response>((resolve, reject) => {
                calls.push({
                    url,
                    signal: init?.signal ?? undefined,
                    respond: (body, status = 200) =>
                        resolve(new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } })),
                    fail: () => reject(new TypeError("Failed to fetch")),
                });
            })),
    );
});

function request(query: string, offset = 0): GlobalSearchRequest {
    return { query, categoriesKey: "", date: "", dateTo: "", position: null, offset };
}

function page(...ids: number[]): GlobalSearchPage {
    return {
        items: ids.map((id) => ({
            id,
            title: `Activité ${id}`,
            description: null,
            category: null,
            categoryCodes: [],
            city: null,
            address: null,
            latitude: null,
            longitude: null,
            startDate: null,
            endDate: null,
            relevance: "TITLE_CONTAINS",
            distanceMeters: null,
        })),
        limit: 20,
        offset: 0,
        total: ids.length,
    };
}

function searchCalls() {
    return calls.filter((call) => call.url.startsWith("/api/v1/activities/search"));
}

describe("useActivitySearch", () => {
    it("reste au repos sans recherche et n'appelle rien", () => {
        const { result } = renderHook(() => useActivitySearch(null, null));
        expect(result.current.status).toBe("idle");
        expect(calls).toHaveLength(0);
    });

    it("appelle seulement la recherche globale, jamais le géocodage", async () => {
        const { result } = renderHook(() => useActivitySearch(request("lyon"), null));
        expect(result.current.status).toBe("loading");
        await waitFor(() => expect(calls).toHaveLength(1));
        expect(calls[0].url).toContain("/api/v1/activities/search?q=lyon");
        expect(calls.some((call) => call.url.includes("geocoding"))).toBe(false);

        await act(async () => calls[0].respond(page(1, 2)));
        expect(result.current).toMatchObject({ status: "success", page: { total: 2 } });
    });

    it("ignore la réponse tardive d'une recherche remplacée", async () => {
        const { result, rerender } = renderHook(({ req }) => useActivitySearch(req, null), {
            initialProps: { req: request("jazz") },
        });
        await waitFor(() => expect(searchCalls()).toHaveLength(1));
        rerender({ req: request("jazz manouche") });
        await waitFor(() => expect(searchCalls()).toHaveLength(2));
        expect(searchCalls()[0].signal?.aborted).toBe(true);
        // Ancien résultat avant l'annulation : pas encore remplacé, toujours en chargement.
        expect(result.current.status).toBe("loading");

        await act(async () => searchCalls()[0].respond(page(1)));
        expect(result.current.status).toBe("loading");

        await act(async () => searchCalls()[1].respond(page(2, 3)));
        expect(result.current).toMatchObject({ status: "success", page: { items: [{ id: 2 }, { id: 3 }] } });
    });

    it("n'affiche jamais l'ancienne page pendant le chargement de la suivante", async () => {
        const { result, rerender } = renderHook(({ req }) => useActivitySearch(req, null), {
            initialProps: { req: request("fete") },
        });
        await waitFor(() => expect(searchCalls()).toHaveLength(1));
        await act(async () => searchCalls()[0].respond(page(1)));
        expect(result.current.status).toBe("success");

        rerender({ req: request("fete", 20) });
        // Dès le rendu qui change de page, avant même la requête.
        expect(result.current.status).toBe("loading");
        await waitFor(() => expect(searchCalls()).toHaveLength(2));
        expect(searchCalls()[1].url).toContain("offset=20");
    });

    it("passe en erreur avec le message du serveur, puis réessaie", async () => {
        const { result, rerender } = renderHook(({ retry }) => useActivitySearch(request("x1"), null, retry), {
            initialProps: { retry: 0 },
        });
        await waitFor(() => expect(searchCalls()).toHaveLength(1));
        await act(async () => searchCalls()[0].respond({ message: "Requête invalide" }, 400));
        expect(result.current).toEqual({ status: "error", message: "Requête invalide" });

        rerender({ retry: 1 });
        expect(result.current.status).toBe("loading");
        await waitFor(() => expect(searchCalls()).toHaveLength(2));
        await act(async () => searchCalls()[1].fail());
        expect(result.current).toEqual({
            status: "error",
            message: "Impossible de contacter le serveur, réessaie plus tard.",
        });
    });

    it("connecté : marque les activités aimées, sans bloquer si les favoris échouent", async () => {
        const { result } = renderHook(() => useActivitySearch(request("marche"), "jwt"));
        await waitFor(() => expect(calls).toHaveLength(2));
        const likes = calls.find((call) => call.url === "/api/v1/users/me/likes");
        expect(likes).toBeDefined();
        await act(async () => {
            searchCalls()[0].respond(page(4, 5));
            likes!.respond([{ id: 5 }]);
        });
        await waitFor(() => expect(result.current.status).toBe("success"));
        if (result.current.status === "success") {
            expect([...result.current.likedIds]).toEqual([5]);
        }
    });
});
