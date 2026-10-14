import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import AdminPlacesSection from "./AdminPlacesSection";

vi.mock("../../auth/authStorage", () => ({
    getToken: () => "jeton-admin",
    clearToken: () => undefined,
}));

const SOURCE = { id: 9, name: "DATAtourisme PACA — lieux", type: "DATATOURISME_LIEUX", status: "INACTIVE", lastSyncAt: null };

function place(id: number, name: string, overrides: Record<string, unknown> = {}) {
    return {
        id, name, placeType: "Church", status: "PENDING", address: "Place Saint-Pierre", postalCode: "84000",
        city: "Avignon", latitude: 43.949, longitude: 4.806, description: null, website: null,
        coordinatePrecision: "UNKNOWN", flags: [], sourceName: "DATAtourisme PACA — lieux", publishedAt: null,
        moderatedAt: null, ...overrides,
    };
}

function json(body: unknown, status = 200) {
    return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

/** Simule l'API : sources, file de modération, actions ; enregistre les appels. */
function stubApi(extra?: (url: string, init?: RequestInit) => Response | undefined) {
    const calls: { url: string; init?: RequestInit }[] = [];
    vi.stubGlobal("fetch", vi.fn(async (url: string, init?: RequestInit) => {
        calls.push({ url, init });
        const custom = extra?.(url, init);
        if (custom) {
            return custom;
        }
        if (url === "/api/v1/sources") {
            return json([SOURCE, { id: 2, name: "OpenAgenda", type: "API", status: "ACTIVE", lastSyncAt: null }]);
        }
        if (url.startsWith("/api/v1/admin/places?")) {
            return json({
                items: [
                    place(1, "Église Saint-Pierre"),
                    place(2, "Circuit piéton du centre", { address: null, flags: ["ROUTE_NAME", "NO_ADDRESS"] }),
                ],
                total: 2, limit: 50, offset: 0,
            });
        }
        return json({});
    }));
    return calls;
}

describe("AdminPlacesSection", () => {
    afterEach(() => {
        vi.unstubAllGlobals();
    });

    it("affiche la file en attente avec les types en français et les signalements", async () => {
        const calls = stubApi();
        render(<AdminPlacesSection />);

        expect(await screen.findByText("Église Saint-Pierre")).toBeInTheDocument();
        expect(screen.getAllByText("Église")).not.toHaveLength(0);
        expect(screen.getByText("Itinéraire ?")).toBeInTheDocument();
        expect(screen.getByText("Sans adresse")).toBeInTheDocument();
        expect(screen.getByText("DATAtourisme PACA — lieux", { selector: "h3" })).toBeInTheDocument();
        expect(screen.queryByText("OpenAgenda", { selector: "h3" })).not.toBeInTheDocument();
        expect(calls.some((call) => call.url.includes("status=PENDING"))).toBe(true);
    });

    it("filtre par département et par signalement", async () => {
        const calls = stubApi();
        render(<AdminPlacesSection />);
        await screen.findByText("Église Saint-Pierre");

        await userEvent.type(screen.getByPlaceholderText("ex. 84"), "84");
        await userEvent.selectOptions(screen.getByLabelText("Signalements"), "true");

        await waitFor(() => expect(calls.some((call) =>
            call.url.includes("department=84") && call.url.includes("flagged=true"))).toBe(true));
    });

    it("publie une sélection par une action groupée, après confirmation", async () => {
        vi.stubGlobal("confirm", () => true);
        const calls = stubApi((url) => (url === "/api/v1/admin/places/moderation"
            ? json({ requested: [1, 2], applied: [1, 2] }) : undefined));
        render(<AdminPlacesSection />);
        await screen.findByText("Église Saint-Pierre");

        await userEvent.click(screen.getByLabelText("Toute la page"));
        await userEvent.click(screen.getByRole("button", { name: "Publier la sélection" }));

        const bulk = await waitFor(() => {
            const call = calls.find((candidate) => candidate.url === "/api/v1/admin/places/moderation");
            expect(call).toBeDefined();
            return call!;
        });
        expect(JSON.parse(String(bulk.init?.body))).toEqual({ ids: [1, 2], action: "PUBLISH" });
        expect(await screen.findByText("2 lieu(x) publié(s) sur 2 sélectionné(s).")).toBeInTheDocument();
    });

    it("lance un essai à blanc de la source et affiche son bilan sans écriture", async () => {
        stubApi((url) => (url === "/api/v1/admin/places/sources/9/dry-run"
            ? json({
                sourceId: 9, sourceName: SOURCE.name, dryRun: true, verdict: "OK", collected: 32332,
                whitelisted: 4771, created: 4771, updated: 0, backToPending: 0, reappeared: 0, archived: 0,
                archiveWithheld: 0, rejectedKept: 0, problems: [],
            })
            : undefined));
        render(<AdminPlacesSection />);
        await screen.findByText("Église Saint-Pierre");

        await userEvent.click(screen.getByRole("button", { name: "Essai à blanc" }));

        expect(await screen.findByText(/4771 retenu\(s\) par la liste blanche/)).toBeInTheDocument();
        expect(screen.getByText(/Aucune écriture/)).toBeInTheDocument();
        expect(screen.getByRole("button", { name: "Activer après vérification" })).toBeInTheDocument();
    });
});
