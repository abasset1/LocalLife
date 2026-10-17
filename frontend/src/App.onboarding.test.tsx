import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import App from "./App";

/** Spec P0-C — accueil de première connexion facultatif. Leaflet ne s'affiche pas dans jsdom. */
vi.mock("./components/explorer/ExplorerMap", () => ({
    default: () => <div data-testid="map" />,
}));

let requests: { url: string; method: string }[];

beforeEach(() => {
    requests = [];
    window.localStorage.setItem("locallife.jwt", `x.${btoa(JSON.stringify({ userId: 7, email: "a@b.c", role: "USER" }))}.y`);
    vi.stubGlobal(
        "fetch",
        vi.fn((url: string, init?: RequestInit) => {
            requests.push({ url, method: init?.method ?? "GET" });
            let body: unknown = [];
            if (url.startsWith("/api/v1/users/me/preferences/city")) {
                body = { city: "Lyon", latitude: 45.75, longitude: 4.83 };
            } else if (url.startsWith("/api/v1/users/me/preferences") && init?.method === "PUT") {
                return Promise.resolve(new Response(JSON.stringify({ message: "Service indisponible" }), { status: 503 }));
            } else if (url.startsWith("/api/v1/users/me/preferences")) {
                body = {
                    defaultCity: null,
                    defaultLatitude: null,
                    defaultLongitude: null,
                    defaultRadiusKm: null,
                    defaultCategoryCodes: [],
                    configured: false,
                };
            } else if (url.startsWith("/api/v1/categories")) {
                body = [{ code: "CULTURE", label: "Culture", description: null, active: true }];
            }
            return Promise.resolve(new Response(JSON.stringify(body), { status: 200 }));
        }),
    );
});

afterEach(() => {
    vi.unstubAllGlobals();
    window.localStorage.clear();
    window.sessionStorage.clear();
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

describe("App — accueil de première connexion facultatif (spec P0-C)", () => {
    it("« Plus tard » ferme l'accueil sans déconnecter ni rien enregistrer, pour la session", async () => {
        const user = renderApp();
        await screen.findByRole("dialog", { name: /Bienvenue sur LocalLife/ });

        await user.click(screen.getByRole("button", { name: "Plus tard" }));

        expect(screen.queryByRole("dialog")).toBeNull();
        expect(window.localStorage.getItem("locallife.jwt")).not.toBeNull();
        expect(requests.some((request) => request.method === "PUT")).toBe(false);
        expect(screen.getByRole("combobox", { name: /Rechercher une activité/ })).toBeInTheDocument();

        // Même session (rechargement de la page) : l'accueil ne se rouvre pas.
        cleanup();
        renderApp();
        await waitFor(() => expect(requests.filter((request) => request.url.includes("preferences"))).toHaveLength(2));
        expect(screen.queryByRole("dialog")).toBeNull();
    });

    it("✕ et Échap reportent aussi la configuration", async () => {
        const user = renderApp();
        const dialog = await screen.findByRole("dialog", { name: /Bienvenue sur LocalLife/ });
        await user.click(within(dialog).getByRole("button", { name: "Fermer" }));
        expect(screen.queryByRole("dialog")).toBeNull();

        window.sessionStorage.clear();
        cleanup();
        const secondUser = renderApp();
        await screen.findByRole("dialog", { name: /Bienvenue sur LocalLife/ });
        await secondUser.keyboard("{Escape}");
        expect(screen.queryByRole("dialog")).toBeNull();
    });

    it("une erreur d'enregistrement reste affichée sans fermer l'accueil", async () => {
        const user = renderApp();
        const dialog = await screen.findByRole("dialog", { name: /Bienvenue sur LocalLife/ });

        await user.type(within(dialog).getByRole("textbox", { name: /Ta ville/ }), "lyon");
        await user.click(await within(dialog).findByRole("checkbox", { name: "Culture" }));
        await user.click(within(dialog).getByRole("button", { name: "Continuer" }));

        await waitFor(() => expect(requests.some((request) => request.method === "PUT")).toBe(true));
        expect(await within(dialog).findByText(/Service indisponible/)).toBeInTheDocument();
        expect(screen.getByRole("dialog", { name: /Bienvenue sur LocalLife/ })).toBeInTheDocument();
    });
});
