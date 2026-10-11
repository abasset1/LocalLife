import { act, renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { installControllableFetch } from "../../test/fetchMock";
import type { PendingRequest } from "../../test/fetchMock";
import { useCitySuggestions } from "./useCitySuggestions";
import type { CitySuggestion } from "./useCitySuggestions";

const LYON: CitySuggestion = {
    name: "Lyon",
    postalCodes: ["69001", "69009"],
    department: "69",
    latitude: 45.758,
    longitude: 4.835,
};
const LYONS_LA_FORET: CitySuggestion = {
    name: "Lyons-la-Forêt",
    postalCodes: ["27480"],
    department: "27",
    latitude: 49.398,
    longitude: 1.476,
};

const DEBOUNCE_MS = 250;

let requests: PendingRequest[];

beforeEach(() => {
    vi.useFakeTimers();
    requests = installControllableFetch();
});

afterEach(() => {
    vi.useRealTimers();
});

function renderSuggestions(initialQuery: string) {
    return renderHook(({ query }) => useCitySuggestions(query, true), { initialProps: { query: initialQuery } });
}

async function advance(ms: number) {
    await act(async () => {
        await vi.advanceTimersByTimeAsync(ms);
    });
}

async function settle(action: () => void) {
    await act(async () => {
        action();
        await vi.runAllTimersAsync();
    });
}

describe("useCitySuggestions — saisie modifiée", () => {
    it("pendant le debounce : une seule requête, pour la dernière saisie", async () => {
        const { result, rerender } = renderSuggestions("ly");
        await advance(100);
        rerender({ query: "lyo" });
        await advance(DEBOUNCE_MS - 1);
        expect(requests).toHaveLength(0);

        await advance(1);
        expect(requests.map((request) => request.query)).toEqual(["lyo"]);
        expect(result.current).toEqual([]);

        await settle(() => requests[0].respond([LYON, LYONS_LA_FORET]));
        expect(result.current).toEqual([LYON, LYONS_LA_FORET]);
    });

    it("pendant une requête en vol : l'ancienne est annulée et ses suggestions ne s'affichent plus", async () => {
        const { result, rerender } = renderSuggestions("lyo");
        await advance(DEBOUNCE_MS);
        await settle(() => requests[0].respond([LYON, LYONS_LA_FORET]));
        expect(result.current).toHaveLength(2);

        rerender({ query: "lyons" });
        // Pendant le nouveau debounce : rien de l'ancienne saisie n'est proposé.
        expect(result.current).toEqual([]);
        await advance(DEBOUNCE_MS);
        expect(requests[1].query).toBe("lyons");

        rerender({ query: "lyons-" });
        expect(requests[1].signal?.aborted).toBe(true);
        expect(result.current).toEqual([]);
    });

    it("réponse tardive d'une ancienne saisie, arrivée après la nouvelle : ignorée", async () => {
        const { result, rerender } = renderSuggestions("lyo");
        await advance(DEBOUNCE_MS);
        rerender({ query: "lyon" });
        await advance(DEBOUNCE_MS);
        expect(requests.map((request) => request.query)).toEqual(["lyo", "lyon"]);

        await settle(() => requests[1].respond([LYON]));
        expect(result.current).toEqual([LYON]);

        // L'ancienne requête répond malgré son annulation : la liste ne change pas.
        await settle(() => requests[0].respond([LYONS_LA_FORET]));
        expect(result.current).toEqual([LYON]);
    });

    it("réponse tardive d'une ancienne saisie, arrivée avant la nouvelle : ignorée", async () => {
        const { result, rerender } = renderSuggestions("lyo");
        await advance(DEBOUNCE_MS);
        rerender({ query: "lyon" });

        await settle(() => requests[0].respond([LYONS_LA_FORET]));
        expect(result.current).toEqual([]);

        await settle(() => requests[1].respond([LYON]));
        expect(result.current).toEqual([LYON]);
    });

    it("erreur réseau de l'ancienne requête après changement de saisie : sans effet", async () => {
        const { result, rerender } = renderSuggestions("lyo");
        await advance(DEBOUNCE_MS);
        rerender({ query: "lyon" });
        await advance(DEBOUNCE_MS);
        await settle(() => requests[1].respond([LYON]));

        await settle(() => requests[0].fail());
        expect(result.current).toEqual([LYON]);
    });

    it("moins de 2 caractères ou désactivé : aucune requête, aucune suggestion", async () => {
        const { result, rerender } = renderHook(({ query, enabled }) => useCitySuggestions(query, enabled), {
            initialProps: { query: "l", enabled: true },
        });
        await advance(DEBOUNCE_MS * 2);
        rerender({ query: "lyo", enabled: false });
        await advance(DEBOUNCE_MS * 2);
        expect(requests).toHaveLength(0);
        expect(result.current).toEqual([]);
    });
});
