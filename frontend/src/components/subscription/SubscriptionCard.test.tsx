import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import SubscriptionCard from "./SubscriptionCard";

vi.mock("../../auth/authStorage", () => ({
    getPayload: () => ({ userId: 7, email: "u@example.com", role: "USER" }),
    getToken: () => "jeton",
    clearToken: () => undefined,
}));

function json(body: unknown, status = 200): Response {
    return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

afterEach(() => vi.unstubAllGlobals());

describe("SubscriptionCard", () => {
    it("active Premium tout de suite", async () => {
        const user = userEvent.setup();
        const active = { id: 4, plan: "PREMIUM", status: "ACTIVE", startedAt: "2026-10-19T10:00:00Z", accessEndsAt: null,
            cancelAtPeriodEnd: false, decisionReason: null, requestedAt: "2026-10-19T10:00:00Z",
            updatedAt: "2026-10-19T10:00:00Z" };
        const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
            if (url.endsWith("/subscribe") && init?.method === "POST") {
                return json({ plan: "PREMIUM", current: active, pending: null, history: [active] });
            }
            return json({ plan: "FREE", current: null, pending: null, history: [] });
        });
        vi.stubGlobal("fetch", fetchMock);
        render(<MemoryRouter><SubscriptionCard /></MemoryRouter>);

        expect(await screen.findByText("Gratuit")).toBeInTheDocument();
        await user.click(screen.getByRole("button", { name: "Passer à Premium" }));

        expect(await screen.findByText("Offre Premium activée.")).toBeInTheDocument();
        expect(screen.getByText("Sans échéance, jusqu'à résiliation.")).toBeInTheDocument();
        expect(screen.queryByRole("button", { name: "Passer à Premium" })).not.toBeInTheDocument();
        expect(fetchMock).toHaveBeenCalledWith("/api/v1/users/me/subscription/subscribe",
            expect.objectContaining({ method: "POST", body: JSON.stringify({ plan: "PREMIUM" }) }));
    });

    it("envoie une demande Pro, examinée par l'équipe, sans ouvrir l'espace professionnel", async () => {
        const user = userEvent.setup();
        const pending = { id: 6, plan: "PRO", status: "REQUESTED", startedAt: null, accessEndsAt: null,
            cancelAtPeriodEnd: false, decisionReason: null, requestedAt: "2026-10-21T10:00:00Z",
            updatedAt: "2026-10-21T10:00:00Z" };
        vi.stubGlobal("fetch", vi.fn(async (url: string, init?: RequestInit) => {
            if (url.endsWith("/subscribe") && init?.method === "POST") {
                return json({ plan: "FREE", current: null, pending, history: [pending] });
            }
            return json({ plan: "FREE", current: null, pending: null, history: [] });
        }));
        render(<MemoryRouter><SubscriptionCard /></MemoryRouter>);

        await user.click(await screen.findByRole("button", { name: "Demander l'offre Pro" }));

        expect(await screen.findByText(/Demande d'offre Pro envoyée/)).toBeInTheDocument();
        expect(screen.getByText(/Demande de passage à/)).toBeInTheDocument();
        expect(screen.queryByRole("button", { name: "Demander l'offre Pro" })).not.toBeInTheDocument();
        expect(screen.getByRole("button", { name: "Retirer ma demande" })).toBeInTheDocument();
        expect(screen.queryByText("Ouvrir l'espace professionnel →")).not.toBeInTheDocument();
    });

    it("explique la date de fin d'accès d'un abonnement résilié", async () => {
        vi.stubGlobal("fetch", vi.fn(async () => json({
            plan: "PRO",
            current: { id: 5, plan: "PRO", status: "CANCELED", startedAt: "2026-10-01T00:00:00Z",
                accessEndsAt: "2026-11-30T23:00:00Z", cancelAtPeriodEnd: true, decisionReason: null,
                requestedAt: "2026-10-01T00:00:00Z", updatedAt: "2026-10-19T00:00:00Z" },
            pending: null, history: [],
        })));
        render(<MemoryRouter><SubscriptionCard /></MemoryRouter>);
        expect(await screen.findByText(/Résilié : accès conservé jusqu'au/)).toBeInTheDocument();
        expect(screen.getByText("Ouvrir l'espace professionnel →")).toBeInTheDocument();
    });

    it("affiche le refus du serveur quand on demande Premium pendant une offre Pro", async () => {
        const user = userEvent.setup();
        const pro = { id: 5, plan: "PRO", status: "ACTIVE", startedAt: "2026-10-01T00:00:00Z", accessEndsAt: null,
            cancelAtPeriodEnd: false, decisionReason: null, requestedAt: "2026-10-01T00:00:00Z",
            updatedAt: "2026-10-01T00:00:00Z" };
        vi.stubGlobal("fetch", vi.fn(async (url: string, init?: RequestInit) => {
            if (url.endsWith("/subscribe") && init?.method === "POST") {
                return json({ message: "Ton offre Pro inclut déjà Premium : résilie-la d'abord." }, 409);
            }
            return json({ plan: "PRO", current: pro, pending: null, history: [pro] });
        }));
        render(<MemoryRouter><SubscriptionCard /></MemoryRouter>);

        await user.click(await screen.findByRole("button", { name: "Passer à Premium" }));

        expect(await screen.findByText("Ton offre Pro inclut déjà Premium : résilie-la d'abord.")).toBeInTheDocument();
    });
});
