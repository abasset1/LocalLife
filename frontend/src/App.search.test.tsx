import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import App from "./App";
import type { GlobalSearchItem, GlobalSearchPage } from "./components/explorer/globalSearch";
import type { Activity } from "./components/explorer/types";

/**
 * Leaflet ne s'affiche pas dans jsdom : la carte est remplacée par la liste de ses marqueurs, avec la
 * page de résultats sur laquelle elle se cadre et l'activité qu'elle centre.
 */
vi.mock("./components/explorer/ExplorerMap", () => ({
    default: ({
        activities,
        fitKey,
        selectedActivity,
        onSelectActivity,
    }: {
        activities: Activity[];
        fitKey?: string | null;
        selectedActivity: Activity | null;
        onSelectActivity: (activity: Activity) => void;
    }) => (
        <div data-fit-key={fitKey ?? ""} data-testid="map">
            {activities
                .filter((activity) => Number.isFinite(activity.latitude))
                .map((activity) => (
                    <button key={activity.id} onClick={() => onSelectActivity(activity)} type="button">
                        marqueur {activity.title}
                    </button>
                ))}
            {selectedActivity && <span data-testid="map-centre">{selectedActivity.title}</span>}
        </div>
    ),
}));

interface Call {
    url: string;
    respond: (body: unknown, status?: number) => void;
}

let calls: Call[];
/** Réponses immédiates des appels hors recherche ; la recherche attend `respond`. */
const MAP_ACTIVITY = {
    id: 99,
    title: "Visible sur la carte",
    category: "",
    categoryCodes: [],
    latitude: 43.3,
    longitude: 5.37,
    startDate: "2026-11-01T10:00:00",
    status: "PUBLISHED",
    sourceName: "Agenda",
    address: null,
    city: "Marseille",
    createdBy: null,
    createdByUsername: null,
    likedByCurrentUser: false,
    mainImageUrl: null,
};

beforeEach(() => {
    calls = [];
    vi.stubGlobal(
        "fetch",
        vi.fn((url: string) => {
            if (url.startsWith("/api/v1/activities/search")) {
                return new Promise<Response>((resolve) => {
                    calls.push({
                        url,
                        respond: (body, status = 200) => resolve(new Response(JSON.stringify(body), { status })),
                    });
                });
            }
            calls.push({ url, respond: () => undefined });
            let body: unknown = [];
            if (url.startsWith("/api/v1/categories")) {
                body = [{ code: "CULTURE", label: "Culture", description: null, active: true }];
            } else if (url.startsWith("/api/v1/geocoding/city-suggestions")) {
                body = url.includes("q=lyo")
                    ? [{ name: "Lyon", postalCodes: ["69001", "69009"], department: "69", latitude: 45.758, longitude: 4.835 }]
                    : [];
            } else if (url.startsWith("/api/v1/activities/nearby") || url.startsWith("/api/v1/activities/within-bounds")) {
                body = [MAP_ACTIVITY];
            }
            return Promise.resolve(new Response(JSON.stringify(body), { status: 200 }));
        }),
    );
});

function item(id: number, overrides: Partial<GlobalSearchItem> = {}): GlobalSearchItem {
    return {
        id,
        title: `Concert ${id}`,
        description: null,
        category: null,
        categoryCodes: ["CULTURE"],
        city: "Lyon",
        address: `${id} rue de la République`,
        latitude: 45.76,
        longitude: 4.83,
        startDate: "2026-11-02T20:00:00",
        endDate: null,
        relevance: "TITLE_CONTAINS",
        distanceMeters: null,
        ...overrides,
    };
}

function page(items: GlobalSearchItem[], total = items.length, offset = 0): GlobalSearchPage {
    return { items, limit: 20, offset, total };
}

function searchCalls() {
    return calls.filter((call) => call.url.startsWith("/api/v1/activities/search"));
}

function lastSearchParams() {
    const all = searchCalls();
    return new URL(all[all.length - 1].url, "http://localhost").searchParams;
}

async function renderApp() {
    const user = userEvent.setup();
    render(
        <MemoryRouter>
            <App />
        </MemoryRouter>,
    );
    await screen.findByRole("button", { name: "marqueur Visible sur la carte" });
    return user;
}

async function submitSearch(user: ReturnType<typeof userEvent.setup>, text: string) {
    const input = screen.getByRole("combobox", { name: /Rechercher une activité/ });
    await user.clear(input);
    await user.type(input, `${text}{Enter}`);
}

function banner() {
    return screen.getAllByRole("status").find((element) => element.classList.contains("explorer-search-banner"))
        ?? screen.getByRole("alert");
}

describe("App — recherche globale (#92)", () => {
    it("envoie le texte à la recherche globale sans géocodage ni rayon", async () => {
        const user = await renderApp();
        await submitSearch(user, "concert lyon");

        await waitFor(() => expect(searchCalls()).toHaveLength(1));
        const params = lastSearchParams();
        expect(params.get("q")).toBe("concert lyon");
        expect(params.has("latitude")).toBe(false);
        expect(params.has("radius")).toBe(false);
        expect(calls.some((call) => call.url.includes("/geocoding/"))).toBe(false);
        expect(banner()).toHaveTextContent("Recherche de « concert lyon »…");
    });

    it("liste et carte montrent le même ensemble de résultats, hors zone visible", async () => {
        const user = await renderApp();
        await submitSearch(user, "concert");
        await waitFor(() => expect(searchCalls()).toHaveLength(1));
        await act(async () =>
            searchCalls()[0].respond(page([item(1), item(2, { latitude: null, longitude: null, city: null, address: null })])));

        expect(banner()).toHaveTextContent("2 résultats pour « concert »");
        expect(banner()).toHaveTextContent("1 sans lieu précis, dans la liste seulement");
        const list = screen.getByRole("list", { name: "Résultats de la recherche" });
        expect(within(list).getAllByRole("listitem")).toHaveLength(2);
        expect(within(list).getByText("Lieu non précisé")).toBeInTheDocument();
        const map = screen.getByTestId("map");
        expect(within(map).getByText("marqueur Concert 1")).toBeInTheDocument();
        // L'activité de la zone visible n'est plus affichée pendant la recherche.
        expect(within(map).queryByText("marqueur Visible sur la carte")).toBeNull();
        expect(map.dataset.fitKey).not.toBe("");
    });

    it("choisir un résultat centre la carte dessus sans effacer la recherche", async () => {
        const user = await renderApp();
        await submitSearch(user, "concert");
        await waitFor(() => expect(searchCalls()).toHaveLength(1));
        await act(async () => searchCalls()[0].respond(page([item(1)])));

        await user.click(screen.getByRole("button", { name: "Liste" }));
        expect(document.querySelector(".explorer-main")).toHaveAttribute("data-mobile-view", "list");
        const list = screen.getByRole("list", { name: "Résultats de la recherche" });
        await user.click(within(list).getByRole("button", { name: /Concert 1/ }));
        expect(screen.getByTestId("map-centre")).toHaveTextContent("Concert 1");
        // Mobile : la vue Carte s'affiche pour montrer l'aperçu et la carte centrée.
        expect(document.querySelector(".explorer-main")).toHaveAttribute("data-mobile-view", "map");
        expect(banner()).toHaveTextContent("1 résultat pour « concert »");
        expect(screen.getByRole("combobox", { name: /Rechercher une activité/ })).toHaveValue("concert");
    });

    it("pagination : pages suivante et précédente avec le contrat limit/offset", async () => {
        const user = await renderApp();
        await submitSearch(user, "fete");
        await waitFor(() => expect(searchCalls()).toHaveLength(1));
        await act(async () =>
            searchCalls()[0].respond(page(Array.from({ length: 20 }, (_, index) => item(index + 1)), 45)));
        expect(banner()).toHaveTextContent("45 résultats pour « fete » · 1–20 sur 45");
        expect(screen.getByRole("button", { name: "‹ Précédents" })).toBeDisabled();

        await user.click(screen.getByRole("button", { name: "Suivants ›" }));
        await waitFor(() => expect(searchCalls()).toHaveLength(2));
        expect(lastSearchParams().get("offset")).toBe("20");
        expect(lastSearchParams().get("q")).toBe("fete");
        await act(async () => searchCalls()[1].respond(page([item(21)], 45, 20)));
        expect(banner()).toHaveTextContent("21–21 sur 45");

        await user.click(screen.getByRole("button", { name: "‹ Précédents" }));
        await waitFor(() => expect(searchCalls()).toHaveLength(3));
        expect(lastSearchParams().get("offset")).toBe("0");
    });

    it("la recherche et la page survivent à la bascule Carte/Liste", async () => {
        const user = await renderApp();
        await submitSearch(user, "fete");
        await waitFor(() => expect(searchCalls()).toHaveLength(1));
        await act(async () => searchCalls()[0].respond(page([item(1)], 45)));
        await user.click(screen.getByRole("button", { name: "Suivants ›" }));
        await waitFor(() => expect(searchCalls()).toHaveLength(2));
        await act(async () => searchCalls()[1].respond(page([item(21)], 45, 20)));

        await user.click(screen.getByRole("button", { name: /Liste/ }));
        await user.click(screen.getByRole("button", { name: /Carte/ }));
        expect(searchCalls()).toHaveLength(2);
        expect(banner()).toHaveTextContent("21–21 sur 45");
        expect(screen.getByRole("combobox", { name: /Rechercher une activité/ })).toHaveValue("fete");
    });

    it("un filtre catégorie relance la recherche en première page avec le filtre", async () => {
        const user = await renderApp();
        await submitSearch(user, "fete");
        await waitFor(() => expect(searchCalls()).toHaveLength(1));
        await act(async () => searchCalls()[0].respond(page([item(1)], 45)));
        await user.click(screen.getByRole("button", { name: "Suivants ›" }));
        await waitFor(() => expect(searchCalls()).toHaveLength(2));
        await act(async () => searchCalls()[1].respond(page([item(21)], 45, 20)));

        await user.click(screen.getByRole("button", { name: /^Catégories/ }));
        await user.click(screen.getByRole("checkbox", { name: /Culture/ }));
        await waitFor(() => expect(searchCalls()).toHaveLength(3));
        expect(lastSearchParams().get("category")).toBe("CULTURE");
        expect(lastSearchParams().get("offset")).toBe("0");
        expect(lastSearchParams().get("q")).toBe("fete");
    });

    it("aucun résultat, erreur avec réessai, puis effacement", async () => {
        const user = await renderApp();
        await submitSearch(user, "zzz");
        await waitFor(() => expect(searchCalls()).toHaveLength(1));
        await act(async () => searchCalls()[0].respond(page([])));
        expect(banner()).toHaveTextContent("Aucun résultat pour « zzz ». Essaie un autre mot.");

        await submitSearch(user, "jazz");
        await waitFor(() => expect(searchCalls()).toHaveLength(2));
        await act(async () => searchCalls()[1].respond({ message: "Service indisponible" }, 503));
        const alert = screen.getAllByRole("alert").find((element) => element.classList.contains("explorer-search-banner"));
        expect(alert).toHaveTextContent("La recherche de « jazz » a échoué : Service indisponible");

        await user.click(screen.getByRole("button", { name: "Réessayer" }));
        await waitFor(() => expect(searchCalls()).toHaveLength(3));
        await act(async () => searchCalls()[2].respond(page([item(1)])));
        expect(banner()).toHaveTextContent("1 résultat pour « jazz »");

        await user.click(screen.getByRole("button", { name: "Effacer ✕" }));
        expect(screen.getByRole("combobox", { name: /Rechercher une activité/ })).toHaveValue("");
        expect(await screen.findByRole("button", { name: "marqueur Visible sur la carte" })).toBeInTheDocument();
        expect(screen.queryByRole("list", { name: "Résultats de la recherche" })).toBeNull();
    });

    it("texte trop court : message immédiat, aucun appel, la carte garde ses activités", async () => {
        const user = await renderApp();
        await submitSearch(user, "c");
        expect(banner()).toHaveTextContent("« c » : Saisis au moins 2 caractères, dont une lettre ou un chiffre.");
        expect(searchCalls()).toHaveLength(0);
        expect(screen.getByRole("button", { name: "marqueur Visible sur la carte" })).toBeInTheDocument();
    });

    it("position déjà connue (géolocalisation acceptée) : envoyée avec le texte", async () => {
        vi.stubGlobal("navigator", {
            ...navigator,
            geolocation: {
                getCurrentPosition: (success: PositionCallback) =>
                    success({ coords: { latitude: 43.2965, longitude: 5.3698 } } as GeolocationPosition),
            },
        });
        try {
            const user = await renderApp();
            await submitSearch(user, "concert");
            await waitFor(() => expect(searchCalls()).toHaveLength(1));
            expect(lastSearchParams().get("latitude")).toBe("43.2965");
            expect(lastSearchParams().get("longitude")).toBe("5.3698");
            await act(async () => searchCalls()[0].respond(page([item(1, { distanceMeters: 1234 })])));
            expect(screen.getByRole("list", { name: "Résultats de la recherche" })).toHaveTextContent("à 1,2 km");
        } finally {
            vi.unstubAllGlobals();
        }
    });

    it("choisir une commune proposée reste distinct : la carte s'y déplace et la recherche texte s'efface", async () => {
        const user = await renderApp();
        await submitSearch(user, "concert");
        await waitFor(() => expect(searchCalls()).toHaveLength(1));
        await act(async () => searchCalls()[0].respond(page([item(1)])));

        const input = screen.getByRole("combobox", { name: /Rechercher une activité/ });
        await user.clear(input);
        await user.type(input, "lyo");
        await user.click(await screen.findByRole("option", { name: /Lyon/ }));

        expect(banner()).toHaveTextContent("📍 Lyon");
        expect(screen.queryByRole("list", { name: "Résultats de la recherche" })).toBeNull();
        expect(searchCalls()).toHaveLength(1);
        expect(calls.some((call) => call.url.includes("/geocoding/city?"))).toBe(false);

        // La commune choisie devient la position de référence de la recherche suivante.
        await submitSearch(user, "concert");
        await waitFor(() => expect(searchCalls()).toHaveLength(2));
        expect(lastSearchParams().get("latitude")).toBe("45.758");
    });
});
