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
vi.mock("./components/explorer/ExplorerMap", () => ({
    default: ({ showPlaces = false }: { showPlaces?: boolean }) => (
        <div data-testid="map">{showPlaces ? "couche lieux visible" : "couche lieux masquée"}</div>
    ),
}));

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
});
