import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import App from "./App";
import type { Activity, MapBounds } from "./components/explorer/types";

/**
 * Carte « Night » : activités qui ont lieu entre 20 h et 6 h (soirées, concerts, festivals…), demandées au serveur
 * avec `night=true`, et affichées comme les activités. Leaflet ne s'affiche pas dans jsdom : la carte est remplacée par
 * une version qui indique la couche et les marqueurs ; comme la vraie carte, elle transmet sa zone.
 */
vi.mock("./components/explorer/ExplorerMap", async () => {
    const { useEffect } = await import("react");
    return {
        default: ({ activities, layer = "activities", onBoundsChange }: {
            activities: Activity[];
            layer?: string;
            onBoundsChange: (bounds: MapBounds) => void;
        }) => {
            useEffect(() => {
                onBoundsChange({ swLatitude: 43, swLongitude: 4, neLatitude: 44.5, neLongitude: 6.5 });
            }, []);
            return (
                <div data-testid="map">
                    {`couche ${layer}`}
                    {activities.map((activity) => <span key={activity.id}> marqueur {activity.title}</span>)}
                </div>
            );
        },
    };
});

const BASE = {
    category: "",
    categoryCodes: [],
    latitude: 43.9,
    longitude: 4.8,
    status: "PUBLISHED",
    sourceName: "Agenda",
    address: null,
    city: "Avignon",
    createdBy: null,
    createdByUsername: null,
    likedByCurrentUser: false,
    mainImageUrl: null,
};
const MARKET = { ...BASE, id: 1, title: "Marché", startDate: "2026-11-08T09:00:00" };
const PARTY = { ...BASE, id: 2, title: "Soirée électro", startDate: "2026-11-08T23:00:00" };

let mapUrls: string[];

beforeEach(() => {
    mapUrls = [];
    try {
        window.localStorage.removeItem("locallife.mapLayer");
    } catch {
        // stockage indisponible : sans effet sur le test
    }
    vi.stubGlobal(
        "fetch",
        vi.fn((url: string) => {
            if (url.startsWith("/api/v1/activities/within-bounds") || url.startsWith("/api/v1/activities/nearby")) {
                mapUrls.push(url);
                const body = url.includes("night=true") ? [PARTY] : [MARKET, PARTY];
                return Promise.resolve(new Response(JSON.stringify(body), { status: 200 }));
            }
            return Promise.resolve(new Response("[]", { status: 200 }));
        }),
    );
});

describe("App — carte Night", () => {
    it("propose l'onglet Night à côté des autres cartes", async () => {
        render(<MemoryRouter><App /></MemoryRouter>);

        const night = await screen.findByRole("button", { name: /Night/ });
        expect(night).toHaveTextContent("🍸");
        expect(night).toHaveAttribute("aria-pressed", "false");
    });

    it("ne charge que les activités de nuit, et mémorise le choix", async () => {
        const user = userEvent.setup();
        render(<MemoryRouter><App /></MemoryRouter>);
        expect(await screen.findByText(/marqueur Marché/)).toBeInTheDocument();
        expect(mapUrls.every((url) => !url.includes("night="))).toBe(true);

        const night = screen.getByRole("button", { name: /Night/ });
        await user.click(night);

        expect(night).toHaveAttribute("aria-pressed", "true");
        await waitFor(() => expect(mapUrls.at(-1)).toContain("night=true"));
        expect(await screen.findByText(/1 activité de nuit dans cette zone/)).toBeInTheDocument();
        // Mêmes marqueurs que les activités, réduits aux activités de nuit.
        expect(screen.getByTestId("map")).toHaveTextContent("couche activities");
        expect(screen.getByTestId("map")).toHaveTextContent("marqueur Soirée électro");
        expect(screen.getByTestId("map")).not.toHaveTextContent("marqueur Marché");
        expect(window.localStorage.getItem("locallife.mapLayer")).toBe("night");

        await user.click(screen.getByRole("button", { name: /Activités/ }));
        await waitFor(() => expect(screen.getByTestId("map")).toHaveTextContent("marqueur Marché"));
        expect(mapUrls.at(-1)).not.toContain("night=");
    });

    it("passe tout le site en thème nuit, avec le logo néon, le temps que la carte Night est affichée", async () => {
        const user = userEvent.setup();
        const { unmount } = render(<MemoryRouter><App /></MemoryRouter>);
        await screen.findByText(/marqueur Marché/);
        expect(document.documentElement.dataset.llTheme).toBeUndefined();
        expect(document.querySelector(".neiri-night-mark")).toBeNull();

        await user.click(screen.getByRole("button", { name: /Night/ }));
        expect(document.documentElement.dataset.llTheme).toBe("night");
        expect(document.querySelector(".neiri-night-mark")).toHaveAttribute("src", "/neiri-night.webp");
        expect(document.querySelector(".neiri-mark")).toBeNull();

        await user.click(screen.getByRole("button", { name: /Activités/ }));
        expect(document.documentElement.dataset.llTheme).toBeUndefined();
        expect(document.querySelector(".neiri-night-mark")).toBeNull();

        // Quitter l'Explorer sur la carte Night rend aussi le thème clair aux autres pages.
        await user.click(screen.getByRole("button", { name: /Night/ }));
        expect(document.documentElement.dataset.llTheme).toBe("night");
        unmount();
        expect(document.documentElement.dataset.llTheme).toBeUndefined();
    });

    it("reprend la carte Night choisie à la visite précédente", async () => {
        window.localStorage.setItem("locallife.mapLayer", "night");
        render(<MemoryRouter><App /></MemoryRouter>);

        expect(await screen.findByRole("button", { name: /Night/ })).toHaveAttribute("aria-pressed", "true");
        await waitFor(() => expect(mapUrls.length).toBeGreaterThan(0));
        expect(mapUrls.every((url) => url.includes("night=true"))).toBe(true);
    });
});
