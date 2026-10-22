import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import AdminProModerationSection from "./AdminProModerationSection";

vi.mock("../../auth/authStorage", () => ({
    getPayload: () => ({ userId: 1, email: "admin@example.com", role: "ADMIN" }),
    getToken: () => "jeton-admin",
    clearToken: () => undefined,
}));

function json(body: unknown, status = 200): Response {
    return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

const CHANGE = {
    id: 8, placeId: 42, placeName: "Musée Calvet", status: "PENDING", address: "1 place Neuve", postalCode: "84000",
    city: "Avignon", latitude: 43.9, longitude: 4.8, description: null, website: null, phone: null, email: null,
    decisionReason: null, createdAt: "2026-10-22T08:00:00Z", decidedAt: null,
};

afterEach(() => vi.unstubAllGlobals());

describe("AdminProModerationSection", () => {
    it("valide un food truck, refuse une proposition avec motif et affiche le refus du serveur", async () => {
        const user = userEvent.setup();
        const calls: { method: string; url: string; body: unknown }[] = [];
        vi.stubGlobal("fetch", vi.fn(async (url: string, init?: RequestInit) => {
            const method = init?.method ?? "GET";
            calls.push({ method, url, body: init?.body ? JSON.parse(init.body as string) : undefined });
            if (url === "/api/v1/admin/foodtrucks?status=PENDING") {
                return json([{ foodTruck: { id: 3, name: "Tacos Loco", category: "Tacos", description: null, contact: null },
                    ownerId: 7, ownerEmail: "pro@example.com", suspendedByAdminAt: null }]);
            }
            if (url === "/api/v1/admin/foodtrucks/3/approve") {
                return json({ message: "Tu ne peux pas valider ton propre food truck." }, 403);
            }
            if (url === "/api/v1/admin/place-changes?status=PENDING") {
                return json([{ change: CHANGE, requesterId: 7, requesterEmail: "pro@example.com", moderatorId: null }]);
            }
            if (url === "/api/v1/admin/place-changes/8" && method === "GET") {
                return json({
                    proposal: { change: CHANGE, requesterId: 7, requesterEmail: "pro@example.com", moderatorId: null },
                    current: { id: 42, name: "Musée Calvet", address: "65 rue Joseph Vernet", postalCode: "84000",
                        city: "Avignon", description: null, website: null, phone: null, email: null },
                    managers: [{ userId: 7, email: "pro@example.com", claimId: 5, grantedAt: "2026-10-01T00:00:00Z" }],
                });
            }
            if (url === "/api/v1/admin/place-changes/8/reject") {
                return json({ ...CHANGE, status: "REJECTED" });
            }
            return json([]);
        }));
        render(<AdminProModerationSection />);

        await user.click(await screen.findByRole("button", { name: "Valider" }));
        expect(await screen.findByText("Tu ne peux pas valider ton propre food truck.")).toBeInTheDocument();

        await user.click(await screen.findByRole("button", { name: "Examiner" }));
        const table = await screen.findByRole("table");
        expect(within(table).getByText("65 rue Joseph Vernet")).toBeInTheDocument();
        expect(within(table).getByText("1 place Neuve")).toBeInTheDocument();
        expect(screen.getByRole("button", { name: "Retirer" })).toBeInTheDocument();
        const reject = screen.getAllByRole("button", { name: "Refuser" }).at(-1) as HTMLElement;
        expect(reject).toBeDisabled();
        await user.type(screen.getByLabelText("Motif (obligatoire pour refuser)"), "Adresse erronée");
        await user.click(reject);

        expect(await screen.findByText("Proposition pour « Musée Calvet » refusée.")).toBeInTheDocument();
        expect(calls.find((call) => call.url === "/api/v1/admin/place-changes/8/reject")?.body)
            .toEqual({ reason: "Adresse erronée" });
    });

    it("retire un gestionnaire d'un lieu avec un motif obligatoire", async () => {
        const user = userEvent.setup();
        const calls: { method: string; url: string; body: unknown }[] = [];
        vi.stubGlobal("fetch", vi.fn(async (url: string, init?: RequestInit) => {
            const method = init?.method ?? "GET";
            calls.push({ method, url, body: init?.body ? JSON.parse(init.body as string) : undefined });
            if (url === "/api/v1/admin/places/42/managers") {
                return json([{ userId: 7, email: "pro@example.com", claimId: 5, grantedAt: "2026-10-01T00:00:00Z" }]);
            }
            if (url === "/api/v1/admin/places/42/managers/7/revoke") {
                return new Response(null, { status: 204 });
            }
            return json([]);
        }));
        const prompt = vi.fn().mockReturnValueOnce("  ").mockReturnValueOnce("Fermeture définitive");
        vi.stubGlobal("prompt", prompt);
        render(<AdminProModerationSection />);

        await user.type(screen.getByLabelText("Identifiant du lieu"), "42");
        await user.click(screen.getByRole("button", { name: "Afficher les gestionnaires" }));
        await user.click(await screen.findByRole("button", { name: "Retirer" }));
        expect(calls.some((call) => call.url.endsWith("/revoke"))).toBe(false);

        await user.click(screen.getByRole("button", { name: "Retirer" }));
        expect(await screen.findByText("pro@example.com ne gère plus ce lieu.")).toBeInTheDocument();
        expect(calls.find((call) => call.url === "/api/v1/admin/places/42/managers/7/revoke")?.body)
            .toEqual({ reason: "Fermeture définitive" });
    });
});
