import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import App from "./App";
import type { Activity, MapBounds } from "./components/explorer/types";

/**
 * Spec P0-D — chargement, erreur et absence de résultat de la carte. Leaflet ne s'affiche pas dans
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

const ACTIVITY = {
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

describe("App — états de la carte (spec P0-D)", () => {
    it("erreur serveur : message distinct d'une zone vide, « Réessayer » relance sans recharger", async () => {
        mapResponses = [503, [ACTIVITY as Activity]];
        const user = renderApp();

        await waitFor(() => expect(floating("alert")).toHaveTextContent("Le chargement des activités a échoué."));
        expect(screen.queryByText(/Aucune activité/)).toBeNull();
        expect(floating("alert")).not.toHaveTextContent("Erreur interne");

        await user.click(within(floating("alert")!).getByRole("button", { name: "Réessayer" }));

        expect(await screen.findByText("marqueur Marché")).toBeInTheDocument();
        expect(floating("alert")).toBeUndefined();
        expect(mapUrls).toHaveLength(2);
        expect(mapUrls[1]).toBe(mapUrls[0]);
    });

    it("perte de réseau : message de connexion, puis nouvelle tentative avec les mêmes filtres", async () => {
        mapResponses = [[ACTIVITY as Activity], "network", [ACTIVITY as Activity]];
        const user = renderApp();
        await screen.findByText("marqueur Marché");

        await user.click(screen.getByRole("button", { name: /^Catégories/ }));
        await user.click(screen.getByRole("checkbox", { name: "Culture" }));
        await waitFor(() => expect(floating("alert")).toHaveTextContent("Connexion impossible : vérifie ton réseau."));

        await user.click(within(floating("alert")!).getByRole("button", { name: "Réessayer" }));
        await screen.findByText("marqueur Marché");
        expect(new URL(mapUrls[mapUrls.length - 1], "http://localhost").searchParams.get("category")).toBe("CULTURE");
    });

    it("aucun résultat avec un filtre : retirer le filtre est proposé, sans changement automatique", async () => {
        mapResponses = [[ACTIVITY as Activity], [], [ACTIVITY as Activity]];
        const user = renderApp();
        await screen.findByText("marqueur Marché");

        await user.click(screen.getByRole("button", { name: /^Catégories/ }));
        await user.click(screen.getByRole("checkbox", { name: "Culture" }));
        await waitFor(() =>
            expect(floating("status")).toHaveTextContent("Aucune activité ne correspond à tes critères dans cette zone."));
        const help = floating("status")!;
        expect(within(help).queryByRole("button", { name: "Toutes les dates" })).toBeNull();
        expect(new URL(mapUrls[mapUrls.length - 1], "http://localhost").searchParams.get("category")).toBe("CULTURE");

        await user.click(within(help).getByRole("button", { name: "Toutes les catégories" }));
        await screen.findByText("marqueur Marché");
        expect(new URL(mapUrls[mapUrls.length - 1], "http://localhost").searchParams.has("category")).toBe(false);
    });

    it("aucun résultat sans filtre : seule une autre ville est proposée", async () => {
        mapResponses = [[]];
        const user = renderApp();

        await waitFor(() => expect(floating("status")).toHaveTextContent("Aucune activité trouvée dans cette zone."));
        const help = floating("status")!;
        expect(within(help).getAllByRole("button").map((button) => button.textContent)).toEqual(["📍 Autre ville"]);

        await user.click(within(help).getByRole("button", { name: "📍 Autre ville" }));
        expect(screen.getByRole("combobox", { name: /Rechercher une activité/ })).toHaveFocus();
    });

    it("aucun résultat dans un rayon : l'élargir au palier suivant est proposé", async () => {
        mapResponses = [[ACTIVITY as Activity], [], [ACTIVITY as Activity]];
        const user = renderApp();
        await screen.findByText("marqueur Marché");

        await user.click(screen.getByRole("button", { name: /Rayon/ }));
        await user.click(screen.getByRole("button", { name: "5 km" }));
        const help = await waitFor(() => {
            const element = floating("status");
            expect(element).toHaveTextContent("Aucune activité ne correspond");
            return element!;
        });

        await user.click(within(help).getByRole("button", { name: "Élargir à 10 km" }));
        await screen.findByText("marqueur Marché");
        expect(screen.getByRole("button", { name: /10 km/ })).toBeInTheDocument();
    });
});
