import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import ProFoodTrucksSection from "./ProFoodTrucksSection";

vi.mock("../../auth/authStorage", () => ({
    getPayload: () => ({ userId: 7, email: "pro@example.com", role: "PRO" }),
    getToken: () => "jeton-pro",
    clearToken: () => undefined,
}));

function json(body: unknown, status = 200): Response {
    return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

const TRUCK = { id: 3, name: "Tacos Loco", category: "Tacos", description: null, contact: null, status: "DRAFT",
    suspendedByLocalLife: false, approved: false, reviewReason: null, suspendedForAccess: false };

afterEach(() => vi.unstubAllGlobals());

describe("ProFoodTrucksSection", () => {
    it("crée un food truck, puis un emplacement en brouillon à une nouvelle adresse", async () => {
        const user = userEvent.setup();
        let trucks: unknown[] = [];
        const calls: { method: string; url: string; body: unknown }[] = [];
        vi.stubGlobal("fetch", vi.fn(async (url: string, init?: RequestInit) => {
            const method = init?.method ?? "GET";
            const body = init?.body ? JSON.parse(init.body as string) : undefined;
            calls.push({ method, url, body });
            if (url === "/api/v1/pro/foodtrucks" && method === "POST") {
                trucks = [TRUCK];
                return json(TRUCK, 201);
            }
            if (url === "/api/v1/pro/foodtrucks") {
                return json(trucks);
            }
            if (url === "/api/v1/pro/foodtrucks/3/presences" && method === "POST") {
                return json({ id: 9, status: "DRAFT" }, 201);
            }
            return json([]);
        }));
        render(<ProFoodTrucksSection />);

        await user.click(await screen.findByRole("button", { name: "Créer un food truck" }));
        await user.type(screen.getByLabelText("Nom commercial"), "Tacos Loco");
        await user.type(screen.getByLabelText("Cuisine"), "Tacos");
        await user.click(screen.getByRole("button", { name: "Enregistrer" }));
        expect(await screen.findByText(/créé en brouillon/)).toBeInTheDocument();

        expect(await screen.findByText(/aucune autorisation de stationnement/)).toBeInTheDocument();
        await user.click(screen.getByRole("button", { name: "Ajouter un emplacement" }));
        await user.click(screen.getByLabelText("Une autre adresse"));
        await user.type(screen.getByLabelText("Adresse"), "1 rue Test");
        await user.type(screen.getByLabelText("Commune"), "Avignon");
        await user.click(screen.getByLabelText("Sam"));
        await user.type(screen.getByLabelText("Arrivée"), "08:00");
        await user.type(screen.getByLabelText("Départ"), "13:00");
        await user.click(screen.getByRole("button", { name: "Enregistrer en brouillon" }));

        expect(await screen.findByText(/Emplacement enregistré en brouillon/)).toBeInTheDocument();
        const created = calls.find((call) => call.url === "/api/v1/pro/foodtrucks/3/presences" && call.method === "POST");
        expect(created?.body).toMatchObject({
            spotKind: "MARKET", days: ["SA"], startTime: "08:00", endTime: "13:00",
            newSpot: { address: "1 rue Test", city: "Avignon", latitude: null, longitude: null },
        });
    });

    it("envoie un food truck jamais validé en validation, puis retire la demande", async () => {
        const user = userEvent.setup();
        let truck = { ...TRUCK };
        const calls: { method: string; url: string }[] = [];
        vi.stubGlobal("fetch", vi.fn(async (url: string, init?: RequestInit) => {
            const method = init?.method ?? "GET";
            calls.push({ method, url });
            if (url === "/api/v1/pro/foodtrucks/3/publish") {
                truck = { ...truck, status: "PENDING" };
                return json(truck);
            }
            if (url === "/api/v1/pro/foodtrucks/3/suspend") {
                truck = { ...truck, status: "DRAFT" };
                return json(truck);
            }
            if (url === "/api/v1/pro/foodtrucks") {
                return json([truck]);
            }
            return json([]);
        }));
        render(<ProFoodTrucksSection />);

        expect(await screen.findByText("🚚 Tacos Loco")).toBeInTheDocument();
        expect(screen.queryByRole("button", { name: "Publier" })).not.toBeInTheDocument();
        await user.click(screen.getByRole("button", { name: "Envoyer en validation" }));

        expect(await screen.findByText(/est envoyé en validation/)).toBeInTheDocument();
        expect(await screen.findByText(/En validation · Tacos/)).toBeInTheDocument();
        expect(screen.queryByRole("button", { name: "Envoyer en validation" })).not.toBeInTheDocument();
        expect(screen.getByRole("button", { name: "Archiver" })).toBeInTheDocument();

        await user.click(screen.getByRole("button", { name: "Retirer la demande" }));
        expect(await screen.findByText(/Demande retirée/)).toBeInTheDocument();
        expect(calls.filter((call) => call.method === "POST").map((call) => call.url)).toEqual([
            "/api/v1/pro/foodtrucks/3/publish", "/api/v1/pro/foodtrucks/3/suspend",
        ]);
    });

    it("affiche le motif d'un refus et propose de publier directement un food truck déjà validé", async () => {
        vi.stubGlobal("fetch", vi.fn(async (url: string) => (url === "/api/v1/pro/foodtrucks"
            ? json([
                { ...TRUCK, status: "REJECTED", reviewReason: "Photos manquantes" },
                { ...TRUCK, id: 4, name: "Crêpes Party", status: "DRAFT", approved: true },
                { ...TRUCK, id: 5, name: "Burger Bus", status: "SUSPENDED", approved: true, suspendedForAccess: true },
            ])
            : json([]))));
        render(<ProFoodTrucksSection />);

        expect(await screen.findByText("Motif du refus : Photos manquantes")).toBeInTheDocument();
        expect(screen.getByText(/Refusé · Tacos/)).toBeInTheDocument();
        expect(screen.getByText(/Masqué : ton accès professionnel a pris fin/)).toBeInTheDocument();
        expect(screen.getByRole("button", { name: "Envoyer en validation" })).toBeInTheDocument();
        expect(screen.getAllByRole("button", { name: "Publier" })).toHaveLength(1);
    });
});
