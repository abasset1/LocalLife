import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import AdminSubscriptionsSection from "./AdminSubscriptionsSection";

vi.mock("../../auth/authStorage", () => ({
    getPayload: () => ({ userId: 1, email: "admin@example.com", role: "ADMIN" }),
    isAuthenticated: () => true,
    subscribeSession: () => () => undefined,
    refreshSession: async () => null,
    onAuthenticated: async () => null,
    clearSession: async () => undefined,
    clearSessionLocal: () => undefined,
    readCsrfToken: () => null,
}));

const REQUEST = {
    userId: 7,
    userEmail: "pro@example.com",
    subscription: { id: 12, plan: "PRO", status: "REQUESTED", startedAt: null, accessEndsAt: null,
        cancelAtPeriodEnd: false, decisionReason: null, requestedAt: "2026-11-01T10:00:00Z",
        updatedAt: "2026-11-01T10:00:00Z" },
};

let pending: unknown[];
let requests: { url: string; method: string }[];

beforeEach(() => {
    pending = [REQUEST];
    requests = [];
    vi.stubGlobal("fetch", vi.fn(async (url: string, init?: RequestInit) => {
        const method = init?.method ?? "GET";
        requests.push({ url, method });
        if (url === "/api/v1/admin/subscriptions/12/approve") {
            pending = [];
            return new Response("{}", { status: 200 });
        }
        if (url.includes("status=REQUESTED")) {
            return new Response(JSON.stringify(pending), { status: 200 });
        }
        return new Response("[]", { status: 200 });
    }));
});

afterEach(() => vi.unstubAllGlobals());

describe("AdminSubscriptionsSection — demandes d'offre Pro", () => {
    it("affiche d'abord les demandes Pro en attente, avec leur nombre, et permet de les activer", async () => {
        const user = userEvent.setup();
        const onPendingCountChange = vi.fn();
        render(<AdminSubscriptionsSection onPendingCountChange={onPendingCountChange} />);

        expect(await screen.findByText("pro@example.com · Pro")).toBeInTheDocument();
        expect(await screen.findByRole("button", { name: /Demandes Pro à traiter\s*\(1\)/ })).toHaveClass("admin-tab-active");
        expect(onPendingCountChange).toHaveBeenLastCalledWith(1);
        expect(screen.queryByText(/activée tout de suite\.$/)).toBeNull();

        await user.click(screen.getByRole("button", { name: "Activer" }));

        expect(requests).toContainEqual({ url: "/api/v1/admin/subscriptions/12/approve", method: "POST" });
        expect(await screen.findByText("Aucune demande d'offre Pro en attente.")).toBeInTheDocument();
        expect(await screen.findByRole("button", { name: /Demandes Pro à traiter\s*\(0\)/ })).toBeInTheDocument();
        expect(onPendingCountChange).toHaveBeenLastCalledWith(0);
    });
});
