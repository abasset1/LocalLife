import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import App from "./App";
import type { Activity, MapBounds } from "./components/explorer/types";

vi.mock("./auth/authStorage", () => ({
    getPayload: () => ({ userId: 7, email: "u@example.com", role: "USER" }),
    getToken: () => "jeton",
    clearToken: () => undefined,
}));

/** Carte remplacée par un bouton par marqueur (Leaflet ne s'affiche pas dans jsdom). */
vi.mock("./components/explorer/ExplorerMap", async () => {
    const { useEffect } = await import("react");
    return {
        default: ({ activities, onBoundsChange, onSelectActivity }: {
            activities: Activity[];
            onBoundsChange: (bounds: MapBounds) => void;
            onSelectActivity: (activity: Activity) => void;
        }) => {
            useEffect(() => {
                onBoundsChange({ swLatitude: 43, swLongitude: 4, neLatitude: 44.5, neLongitude: 6.5 });
            }, []);
            return (
                <div data-testid="map">
                    {activities.map((activity) => (
                        <button key={activity.id} onClick={() => onSelectActivity(activity)} type="button">
                            marqueur {activity.title}
                        </button>
                    ))}
                </div>
            );
        },
    };
});

const ACTIVITY = {
    id: 1, title: "Marché", category: "", categoryCodes: ["CULTURE"], latitude: 43.9, longitude: 4.8,
    startDate: "2026-11-01T10:00:00", status: "PUBLISHED", sourceName: "Agenda", address: null, city: "Avignon",
    createdBy: null, createdByUsername: null, likedByCurrentUser: false, mainImageUrl: null,
};

const LIMIT_MESSAGE = "Tu as atteint la limite de 5 favoris de l'offre Gratuit : passe à Premium pour en ajouter.";

beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn(async (url: string, init?: RequestInit) => {
        if (url === "/api/v1/activities/1/like" && init?.method === "POST") {
            return new Response(JSON.stringify({ message: LIMIT_MESSAGE }), { status: 403 });
        }
        if (url.startsWith("/api/v1/activities/within-bounds") || url.startsWith("/api/v1/activities/nearby")) {
            return new Response(JSON.stringify([ACTIVITY]), { status: 200 });
        }
        if (url === "/api/v1/users/me/preferences") {
            return new Response(JSON.stringify({ configured: true, defaultCity: null, defaultCategoryCodes: [],
                defaultRadiusKm: null }), { status: 200 });
        }
        if (url === "/api/v1/activities/1") {
            return new Response(JSON.stringify({ message: "absent" }), { status: 404 });
        }
        return new Response(JSON.stringify([]), { status: 200 });
    }));
});

afterEach(() => vi.unstubAllGlobals());

describe("App — favoris limités par l'offre", () => {
    it("affiche le refus du serveur et laisse le cœur vide quand la limite de favoris est atteinte", async () => {
        const user = userEvent.setup();
        render(<MemoryRouter><App /></MemoryRouter>);

        await user.click(await screen.findByRole("button", { name: "marqueur Marché" }));
        await user.click(await screen.findByRole("button", { name: /Voir l'activité/ }));
        await user.click(await screen.findByRole("button", { name: "Ajouter aux favoris" }));

        expect(await screen.findByText(LIMIT_MESSAGE)).toBeInTheDocument();
        expect(screen.getByRole("link", { name: "Voir les offres" })).toHaveAttribute("href", "/profile");
        const heart = screen.getByRole("button", { name: "Ajouter aux favoris" });
        expect(heart).toHaveAttribute("aria-pressed", "false");
        expect(heart).toHaveTextContent("♡");
    });
});
