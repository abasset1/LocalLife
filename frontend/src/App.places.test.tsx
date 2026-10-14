import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import App from "./App";

/**
 * Bouton de la couche « Lieux » (ADR-0011, phase B). Leaflet ne s'affiche pas dans jsdom : la carte est remplacée
 * par une version qui indique si la couche lui est demandée ; le chargement des lieux (PlacesLayer) est vérifié à
 * part et dans un navigateur réel.
 */
const MOCK_PLACES = [
    { id: 1, name: "Palais des Papes", placeType: "Palace", address: null, postalCode: "84000", city: "Avignon",
        latitude: 43.95, longitude: 4.8, description: null, website: null, coordinatePrecision: "UNKNOWN" },
];

vi.mock("./components/explorer/ExplorerMap", async () => {
    const { useEffect } = await import("react");
    return {
        default: ({ showPlaces = false, onPlacesChange }: {
            showPlaces?: boolean;
            onPlacesChange?: (places: unknown[]) => void;
        }) => {
            // Comme PlacesLayer : lieux de la zone transmis quand la couche est affichée.
            useEffect(() => {
                onPlacesChange?.(showPlaces ? MOCK_PLACES : []);
            }, [showPlaces, onPlacesChange]);
            return <div data-testid="map">{showPlaces ? "couche lieux visible" : "couche lieux masquée"}</div>;
        },
    };
});

beforeEach(() => {
    try {
        window.localStorage.removeItem("locallife.showPlaces");
    } catch {
        // stockage indisponible : sans effet sur le test
    }
    vi.stubGlobal("fetch", vi.fn(() => Promise.resolve(new Response("[]", { status: 200 }))));
});

describe("App — bouton de la couche « Lieux »", () => {
    it("affiche puis masque la couche, et mémorise le choix", async () => {
        const user = userEvent.setup();
        render(<MemoryRouter><App /></MemoryRouter>);
        const toggle = await screen.findByRole("button", { name: /Lieux/ });

        expect(toggle).toHaveAttribute("aria-pressed", "false");
        expect(screen.getByTestId("map")).toHaveTextContent("couche lieux masquée");

        await user.click(toggle);
        expect(toggle).toHaveAttribute("aria-pressed", "true");
        expect(screen.getByTestId("map")).toHaveTextContent("couche lieux visible");
        expect(window.localStorage.getItem("locallife.showPlaces")).toBe("true");

        await user.click(toggle);
        expect(screen.getByTestId("map")).toHaveTextContent("couche lieux masquée");
    });

    it("propose l'onglet « Lieux » dans la liste quand la couche est affichée", async () => {
        const user = userEvent.setup();
        render(<MemoryRouter><App /></MemoryRouter>);
        expect(screen.queryByRole("tab", { name: /Lieux/ })).not.toBeInTheDocument();

        await user.click(await screen.findByRole("button", { name: /Lieux/ }));
        await user.click(await screen.findByRole("tab", { name: "Lieux (1)" }));

        expect(screen.getByRole("tab", { name: "Lieux (1)" })).toHaveAttribute("aria-selected", "true");
        expect(screen.getByRole("button", { name: /Palais des Papes/ })).toBeInTheDocument();
    });
});
