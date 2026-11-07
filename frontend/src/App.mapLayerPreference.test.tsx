import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import App from "./App";

/** Carte ouverte à l'arrivée d'un utilisateur connecté : paramètre `defaultMapLayer`, prioritaire sur le navigateur. */
vi.mock("./components/explorer/ExplorerMap", () => ({
    default: ({ layer = "activities" }: { layer?: string }) => <div data-testid="map">{`couche ${layer}`}</div>,
}));

const mockSession = vi.hoisted(() => ({ current: null as { userId: number; email: string; role: string } | null }));
vi.mock("./auth/authStorage", () => ({
    getPayload: () => mockSession.current,
    isAuthenticated: () => mockSession.current !== null,
    subscribeSession: () => () => undefined,
    refreshSession: async () => mockSession.current,
    onAuthenticated: async () => mockSession.current,
    clearSession: async () => {
        mockSession.current = null;
    },
    clearSessionLocal: () => {
        mockSession.current = null;
    },
    readCsrfToken: () => null,
}));

function stubPreferences(defaultMapLayer: string | null) {
    vi.stubGlobal(
        "fetch",
        vi.fn((url: string) => {
            let body: unknown = [];
            if (url.startsWith("/api/v1/users/me/preferences")) {
                body = {
                    defaultCity: null,
                    defaultLatitude: null,
                    defaultLongitude: null,
                    defaultRadiusKm: null,
                    defaultCategoryCodes: [],
                    defaultMapLayer,
                    configured: true,
                };
            }
            return Promise.resolve(new Response(JSON.stringify(body), { status: 200 }));
        }),
    );
}

beforeEach(() => {
    mockSession.current = { userId: 7, email: "a@b.c", role: "USER" };
});

afterEach(() => {
    vi.unstubAllGlobals();
    window.localStorage.clear();
});

describe("App — carte choisie dans les paramètres", () => {
    it("ouvre la carte enregistrée, plutôt que la dernière utilisée dans le navigateur", async () => {
        window.localStorage.setItem("locallife.mapLayer", "foodTrucks");
        stubPreferences("places");
        render(<MemoryRouter><App /></MemoryRouter>);

        expect(await screen.findByText("couche places")).toBeInTheDocument();
        expect(screen.getByRole("button", { name: /Lieux/ })).toHaveAttribute("aria-pressed", "true");
        // Le souvenir du navigateur n'est pas écrasé par le paramètre.
        expect(window.localStorage.getItem("locallife.mapLayer")).toBe("foodTrucks");
    });

    it("garde la dernière carte utilisée sans carte enregistrée", async () => {
        window.localStorage.setItem("locallife.mapLayer", "foodTrucks");
        stubPreferences(null);
        render(<MemoryRouter><App /></MemoryRouter>);

        expect(await screen.findByRole("button", { name: /Food trucks/ })).toHaveAttribute("aria-pressed", "true");
        expect(screen.getByTestId("map")).toHaveTextContent("couche foodTrucks");
    });
});
