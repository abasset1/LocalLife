import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import App from "./App";
import type { Activity, MapBounds } from "./components/explorer/types";

/**
 * Filtre « Gratuit » de la carte : appliqué dans le navigateur sur `priceType`, sans nouvelle requête. Leaflet ne s'affiche pas dans
 * jsdom : la carte est remplacée par ses marqueurs ; comme la vraie carte, elle transmet sa zone.
 */
vi.mock("./components/explorer/ExplorerMap", async () => {
    const { useEffect } = await import("react");
    return {
        default: ({ activities, onBoundsChange }: { activities: Activity[]; onBoundsChange: (bounds: MapBounds) => void }) => {
            useEffect(() => {
                onBoundsChange({ swLatitude: 43, swLongitude: 4, neLatitude: 44.5, neLongitude: 6.5 });
            }, []);
            return (
                <div data-testid="map">
                    {activities.map((activity) => (
                        <span key={activity.id}>marqueur {activity.title}</span>
                    ))}
                </div>
            );
        },
    };
});

const BASE = {
    id: 1,
    title: "Marché",
    category: "",
    categoryCodes: ["CULTURE"],
    latitude: 43.9,
    longitude: 4.8,
    startDate: "2026-11-01T10:00:00",
    status: "PUBLISHED",
    sourceName: "Agenda",
    address: null,
    city: "Avignon",
    createdBy: null,
    createdByUsername: null,
    likedByCurrentUser: false,
    mainImageUrl: null,
};

const ACTIVITY = { ...BASE, priceType: "FREE" };
const CONCERT = { ...BASE, id: 2, title: "Concert", priceType: "PAID" };
const VISITE = { ...BASE, id: 3, title: "Visite", priceType: "UNKNOWN" };

/** Réponses successives de la recherche de la carte : activités, code d'erreur, ou panne réseau. */
let mapResponses: (Activity[] | number | "network")[];
let mapUrls: string[];

beforeEach(() => {
    mapResponses = [];
    mapUrls = [];
    vi.stubGlobal(
        "fetch",
        vi.fn((url: string) => {
            if (url.startsWith("/api/v1/activities/within-bounds") || url.startsWith("/api/v1/activities/nearby")) {
                mapUrls.push(url);
                const next = mapResponses.length > 1 ? mapResponses.shift()! : mapResponses[0] ?? [ACTIVITY as Activity];
                if (next === "network") {
                    return Promise.reject(new TypeError("Failed to fetch"));
                }
                if (typeof next === "number") {
                    return Promise.resolve(new Response(JSON.stringify({ message: "Erreur interne" }), { status: next }));
                }
                return Promise.resolve(new Response(JSON.stringify(next), { status: 200 }));
            }
            const body = url.startsWith("/api/v1/categories")
                ? [{ code: "CULTURE", label: "Culture", description: null, active: true }]
                : [];
            return Promise.resolve(new Response(JSON.stringify(body), { status: 200 }));
        }),
    );
});

afterEach(() => {
    vi.unstubAllGlobals();
});

function renderApp() {
    const user = userEvent.setup();
    render(
        <MemoryRouter>
            <App />
        </MemoryRouter>,
    );
    return user;
}

function floating(role: "alert" | "status") {
    return screen.queryAllByRole(role).find((element) => element.classList.contains("explorer-status-floating"));
}

describe("App — filtre Gratuit", () => {
    it("ne garde que les activités gratuites sur la carte et dans la liste, sans relancer la recherche", async () => {
        mapResponses = [[ACTIVITY, CONCERT, VISITE] as Activity[]];
        const user = renderApp();
        await screen.findByText("marqueur Concert");
        const requests = mapUrls.length;

        const toggle = screen.getByRole("button", { name: "Gratuit" });
        expect(toggle).toHaveAttribute("aria-pressed", "false");
        await user.click(toggle);

        expect(toggle).toHaveAttribute("aria-pressed", "true");
        expect(screen.getByText("marqueur Marché")).toBeInTheDocument();
        expect(screen.queryByText("marqueur Concert")).toBeNull();
        expect(screen.queryByText("marqueur Visite")).toBeNull();
        expect(screen.getByText("1 activité dans cette zone")).toBeInTheDocument();
        expect(mapUrls).toHaveLength(requests);

        await user.click(toggle);
        expect(screen.getByText("marqueur Concert")).toBeInTheDocument();
        expect(screen.getByText("marqueur Visite")).toBeInTheDocument();
    });

    it("aucune activité gratuite dans la zone : le dit, et propose de tout revoir", async () => {
        mapResponses = [[CONCERT, VISITE] as Activity[]];
        const user = renderApp();
        await screen.findByText("marqueur Concert");

        await user.click(screen.getByRole("button", { name: "Gratuit" }));

        const help = await waitFor(() => {
            const element = floating("status");
            expect(element).toHaveTextContent("Aucune activité gratuite dans cette zone.");
            return element!;
        });
        expect(screen.queryByText("marqueur Concert")).toBeNull();

        await user.click(within(help).getByRole("button", { name: "Voir toutes les activités" }));
        expect(screen.getByText("marqueur Concert")).toBeInTheDocument();
        expect(screen.getByRole("button", { name: "Gratuit" })).toHaveAttribute("aria-pressed", "false");
    });
});
