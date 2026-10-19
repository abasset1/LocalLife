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
    it("montre l'offre gratuite, puis active Pro tout de suite", async () => {
        const user = userEvent.setup();
        const active = { id: 4, plan: "PRO", status: "ACTIVE", startedAt: "2026-10-19T10:00:00Z", accessEndsAt: null,
            cancelAtPeriodEnd: false, decisionReason: null, requestedAt: "2026-10-19T10:00:00Z",
            updatedAt: "2026-10-19T10:00:00Z" };
        const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
            if (url.endsWith("/subscribe") && init?.method === "POST") {
                return json({ plan: "PRO", current: active, pending: null, history: [active] });
            }
            return json({ plan: "FREE", current: null, pending: null, history: [] });
        });
        vi.stubGlobal("fetch", fetchMock);
        render(<MemoryRouter><SubscriptionCard /></MemoryRouter>);

        expect(await screen.findByText("Gratuit")).toBeInTheDocument();
        await user.click(screen.getByRole("button", { name: "Passer à Pro" }));

        expect(await screen.findByText("Offre Pro activée.")).toBeInTheDocument();
        expect(screen.getByText("Sans échéance, jusqu'à résiliation.")).toBeInTheDocument();
        expect(screen.getByText("Ouvrir l'espace professionnel →")).toBeInTheDocument();
        expect(screen.queryByRole("button", { name: "Passer à Pro" })).not.toBeInTheDocument();
        expect(fetchMock).toHaveBeenCalledWith("/api/v1/users/me/subscription/subscribe",
            expect.objectContaining({ method: "POST", body: JSON.stringify({ plan: "PRO" }) }));
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
});
