import { render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import ActivityAlertsSection from "./ActivityAlertsSection";

vi.mock("../../auth/authStorage", () => ({
    getPayload: () => ({ userId: 7, email: "u@example.com", role: "USER" }),
    getToken: () => "jeton",
    clearToken: () => undefined,
}));

const ALERT = { id: 3, categoryCodes: [], includeFoodTrucks: true, city: "Avignon", latitude: 43.9, longitude: 4.8,
    radiusKm: 10, notifyEmail: true, notifyPush: false };

function stubFetch(alerts: unknown[]) {
    vi.stubGlobal("fetch", vi.fn(async (url: string) => {
        if (url === "/api/v1/users/me/alerts") {
            return new Response(JSON.stringify(alerts), { status: 200 });
        }
        if (url === "/api/v1/users/me/preferences") {
            return new Response(JSON.stringify({ message: "absent" }), { status: 404 });
        }
        return new Response(JSON.stringify([]), { status: 200 });
    }));
}

afterEach(() => vi.unstubAllGlobals());

describe("ActivityAlertsSection", () => {
    it("compte les alertes et bloque la création à la limite de l'offre", async () => {
        stubFetch([ALERT]);
        render(<ActivityAlertsSection maxAlerts={1} />);

        expect(await screen.findByText("1 / 1 alerte")).toBeInTheDocument();
        expect(screen.getByRole("button", { name: "Créer l'alerte" })).toBeDisabled();
        expect(screen.getByText(/Tu as atteint la limite de 1 alerte/)).toBeInTheDocument();
        expect(screen.getByRole("link", { name: "passe à une offre supérieure" }))
            .toHaveAttribute("href", "#profile-subscription-title");
    });

    it("laisse créer une alerte sous la limite", async () => {
        stubFetch([ALERT]);
        render(<ActivityAlertsSection maxAlerts={10} />);

        expect(await screen.findByText("1 / 10 alertes")).toBeInTheDocument();
        expect(screen.queryByText(/Tu as atteint la limite/)).not.toBeInTheDocument();
    });
});
