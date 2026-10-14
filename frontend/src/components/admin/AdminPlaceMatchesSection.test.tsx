import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import AdminPlaceMatchesSection from "./AdminPlaceMatchesSection";

vi.mock("../../auth/authStorage", () => ({
    getToken: () => "jeton-admin",
    clearToken: () => undefined,
}));

function match(eventLocationId: number, placeId: number, overrides: Record<string, unknown> = {}) {
    return {
        level: "CERTAIN", reason: null, distanceMeters: 9, eventLocationId,
        eventLocationName: null, eventLocationAddress: "31 rue Giraud Cinéma Le Luberon", eventLocationCity: "Pertuis",
        activityCount: 3, placeId, placeName: "Cinéma Le Luberon", placeAddress: "31 rue Giraud",
        placeType: "Cinema", placeStatus: "PENDING", linkedPlaceId: null, ...overrides,
    };
}

function json(body: unknown, status = 200) {
    return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

function stubApi() {
    const calls: { url: string; init?: RequestInit }[] = [];
    vi.stubGlobal("fetch", vi.fn(async (url: string, init?: RequestInit) => {
        calls.push({ url, init });
        if (url.startsWith("/api/v1/admin/places/matches?")) {
            return json({
                pairsByLevel: { CERTAIN: 2, PROBABLE: 1 }, eventLocationsByLevel: {}, activitiesByLevel: {},
                matches: [match(10, 1), match(11, 2, { placeName: "Château de Simiane", linkedPlaceId: 2 })],
            });
        }
        if (url === "/api/v1/admin/places/links") {
            return json({ linked: [{ eventLocationId: 10, placeId: 1 }], refused: [] });
        }
        return new Response(null, { status: 204 });
    }));
    return calls;
}

describe("AdminPlaceMatchesSection", () => {
    afterEach(() => {
        vi.unstubAllGlobals();
        vi.restoreAllMocks();
    });

    it("liste les paires certaines du Vaucluse et distingue les paires déjà reliées", async () => {
        const calls = stubApi();
        render(<AdminPlaceMatchesSection />);

        expect(await screen.findByText("Cinéma Le Luberon")).toBeInTheDocument();
        expect(calls[0].url).toBe("/api/v1/admin/places/matches?level=CERTAIN&limit=200&department=84");
        expect(screen.getByRole("button", { name: "Certaines (2)" })).toBeInTheDocument();
        expect(screen.getByText("Relié")).toBeInTheDocument();
        expect(screen.getByRole("button", { name: "Délier" })).toBeInTheDocument();
        expect(screen.queryByRole("checkbox", { name: /Château de Simiane/ })).not.toBeInTheDocument();
    });

    it("relie la sélection après confirmation", async () => {
        const calls = stubApi();
        vi.spyOn(window, "confirm").mockReturnValue(true);
        render(<AdminPlaceMatchesSection />);

        await userEvent.click(await screen.findByRole("checkbox", { name: /Cinéma Le Luberon$/ }));
        await userEvent.click(screen.getByRole("button", { name: "Relier la sélection" }));

        await waitFor(() => expect(screen.getByRole("status")).toHaveTextContent("1 reliée(s)."));
        const post = calls.find((call) => call.url === "/api/v1/admin/places/links");
        expect(JSON.parse(post?.init?.body as string)).toEqual({ links: [{ eventLocationId: 10, placeId: 1 }] });
    });

    it("délie après confirmation", async () => {
        const calls = stubApi();
        vi.spyOn(window, "confirm").mockReturnValue(true);
        render(<AdminPlaceMatchesSection />);

        await userEvent.click(await screen.findByRole("button", { name: "Délier" }));

        await waitFor(() => expect(screen.getByRole("status")).toHaveTextContent("Lien retiré."));
        expect(calls.some((call) => call.url === "/api/v1/admin/places/links/11" && call.init?.method === "DELETE"))
            .toBe(true);
    });
});
