import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import App from "./App";
import ExplorerMenu from "./components/explorer/ExplorerMenu";
import { ALL_CLOSED, LaunchFeaturesProvider, parseLaunchFeatures } from "./features/launchFeatures";

/**
 * Lancement progressif (05/11/2026) : tant que le serveur les dit fermés, lieux, food trucks et offres Premium/Pro
 * disparaissent du site ; seules les activités restent sur la carte.
 */
vi.mock("./components/explorer/ExplorerMap", () => ({
    default: ({ layer = "activities" }: { layer?: string }) => <div data-testid="map">{`couche ${layer}`}</div>,
}));

const fetchMock = vi.fn((input: RequestInfo | URL) => {
    const url = String(input);
    if (url.startsWith("/api/v1/features")) {
        return Promise.resolve(new Response(JSON.stringify(ALL_CLOSED), { status: 200 }));
    }
    return Promise.resolve(new Response("[]", { status: 200 }));
});

beforeEach(() => {
    try {
        // Couche mémorisée avant le lancement : elle ne doit pas rouvrir une fonction fermée.
        window.localStorage.setItem("locallife.mapLayer", "foodTrucks");
    } catch {
        // stockage indisponible : sans effet sur le test
    }
    fetchMock.mockClear();
    vi.stubGlobal("fetch", fetchMock);
});

describe("Lancement : fonctions fermées", () => {
    it("n'affiche que les activités, sans bascule ni chargement des food trucks", async () => {
        render(<MemoryRouter><LaunchFeaturesProvider><App /></LaunchFeaturesProvider></MemoryRouter>);

        await waitFor(() => expect(fetchMock).toHaveBeenCalledWith("/api/v1/features", expect.anything()));
        expect(await screen.findByTestId("map")).toHaveTextContent("couche activities");
        expect(screen.queryByRole("button", { name: /Lieux/ })).not.toBeInTheDocument();
        expect(screen.queryByRole("button", { name: /Food trucks/ })).not.toBeInTheDocument();
        expect(fetchMock.mock.calls.some(([url]) => String(url).startsWith("/api/v1/foodtrucks"))).toBe(false);
    });

    it("masque « Proposer une activité » et « Nouveautés » tant que Premium et Pro sont fermés", async () => {
        render(
            <MemoryRouter>
                <LaunchFeaturesProvider>
                    <ExplorerMenu currentUser={null} isOpen onClose={() => undefined}
                        onLogout={() => undefined} onOpenContributionForm={() => undefined} />
                </LaunchFeaturesProvider>
            </MemoryRouter>,
        );

        await waitFor(() => expect(fetchMock).toHaveBeenCalled());
        expect(screen.queryByRole("button", { name: "Proposer une activité" })).not.toBeInTheDocument();
        expect(screen.queryByRole("link", { name: /Nouveautés/ })).not.toBeInTheDocument();
        expect(screen.getByRole("link", { name: "Se connecter" })).toBeInTheDocument();
    });

    it("lit la réponse du serveur de façon stricte : un champ absent compte comme fermé", () => {
        expect(parseLaunchFeatures({ places: true, premium: "yes" })).toEqual({
            places: true, foodTrucks: false, premium: false, pro: false,
        });
        expect(parseLaunchFeatures(null)).toEqual(ALL_CLOSED);
    });
});
