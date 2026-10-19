import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import App from "./App";

/**
 * Choix du contenu de la carte : activités, lieux (ADR-0011, phase B) ou food trucks (ADR-0013), un seul à la fois.
 * Leaflet ne s'affiche pas dans jsdom : la carte est remplacée par une version qui indique la couche demandée ; le
 * chargement des lieux (PlacesLayer) est vérifié à part et dans un navigateur réel.
 */
const MOCK_PLACES = [
    { id: 1, name: "Palais des Papes", placeType: "Palace", address: null, postalCode: "84000", city: "Avignon",
        latitude: 43.95, longitude: 4.8, description: null, website: null, coordinatePrecision: "UNKNOWN" },
];

vi.mock("./components/explorer/ExplorerMap", async () => {
    const { useEffect } = await import("react");
    return {
        default: ({ layer = "activities", onPlacesChange }: {
            layer?: string;
            onPlacesChange?: (places: unknown[]) => void;
        }) => {
            // Comme PlacesLayer : lieux de la zone transmis quand la couche est affichée.
            useEffect(() => {
                onPlacesChange?.(layer === "places" ? MOCK_PLACES : []);
            }, [layer, onPlacesChange]);
            return <div data-testid="map">{`couche ${layer}`}</div>;
        },
    };
});

beforeEach(() => {
    try {
        window.localStorage.removeItem("locallife.mapLayer");
    } catch {
        // stockage indisponible : sans effet sur le test
    }
    vi.stubGlobal("fetch", vi.fn(() => Promise.resolve(new Response("[]", { status: 200 }))));
});

describe("App — contenu de la carte", () => {
    it("affiche les activités par défaut", async () => {
        render(<MemoryRouter><App /></MemoryRouter>);

        expect(await screen.findByRole("button", { name: /Activités/ })).toHaveAttribute("aria-pressed", "true");
        expect(screen.getByRole("button", { name: /Lieux/ })).toHaveAttribute("aria-pressed", "false");
        expect(screen.getByRole("button", { name: /Food trucks/ })).toHaveAttribute("aria-pressed", "false");
        expect(screen.getByTestId("map")).toHaveTextContent("couche activities");
    });

    it("n'affiche qu'un seul contenu à la fois, et mémorise le choix", async () => {
        const user = userEvent.setup();
        render(<MemoryRouter><App /></MemoryRouter>);
        const activities = await screen.findByRole("button", { name: /Activités/ });
        const places = screen.getByRole("button", { name: /Lieux/ });
        const foodTrucks = screen.getByRole("button", { name: /Food trucks/ });

        await user.click(places);
        expect(places).toHaveAttribute("aria-pressed", "true");
        expect(activities).toHaveAttribute("aria-pressed", "false");
        expect(screen.getByTestId("map")).toHaveTextContent("couche places");
        expect(window.localStorage.getItem("locallife.mapLayer")).toBe("places");

        await user.click(foodTrucks);
        expect(foodTrucks).toHaveAttribute("aria-pressed", "true");
        expect(places).toHaveAttribute("aria-pressed", "false");
        expect(screen.getByTestId("map")).toHaveTextContent("couche foodTrucks");

        await user.click(activities);
        expect(screen.getByTestId("map")).toHaveTextContent("couche activities");
    });

    it("liste les lieux, et non les activités, quand les lieux sont affichés", async () => {
        const user = userEvent.setup();
        render(<MemoryRouter><App /></MemoryRouter>);

        await user.click(await screen.findByRole("button", { name: /Lieux/ }));

        expect(await screen.findByRole("button", { name: /Palais des Papes/ })).toBeInTheDocument();
        expect(screen.queryByRole("tab")).not.toBeInTheDocument();
    });

    it("reprend le dernier contenu choisi", async () => {
        window.localStorage.setItem("locallife.mapLayer", "foodTrucks");
        render(<MemoryRouter><App /></MemoryRouter>);

        expect(await screen.findByRole("button", { name: /Food trucks/ })).toHaveAttribute("aria-pressed", "true");
        expect(screen.getByTestId("map")).toHaveTextContent("couche foodTrucks");
    });
});
