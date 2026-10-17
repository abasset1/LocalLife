import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import App from "./App";
import type { Activity, MapBounds, UserPosition } from "./components/explorer/types";

/**
 * Spec P0-A — localisation explicite. Leaflet ne s'affiche pas dans jsdom : la carte est remplacée par
 * ses marqueurs et la position sur laquelle elle est centrée ; comme la vraie carte, elle transmet sa
 * zone dès l'affichage.
 */
vi.mock("./components/explorer/ExplorerMap", async () => {
    const { useEffect } = await import("react");
    return {
        default: ({
            activities,
            userPosition,
            onBoundsChange,
        }: {
            activities: Activity[];
            userPosition: UserPosition | null;
            onBoundsChange: (bounds: MapBounds) => void;
        }) => {
            useEffect(() => {
                onBoundsChange({ swLatitude: 43, swLongitude: 4, neLatitude: 44.5, neLongitude: 6.5 });
            }, []);
            return (
                <div data-testid="map">
                    {activities.map((activity) => (
                        <span key={activity.id}>marqueur {activity.title}</span>
                    ))}
                    <span data-testid="map-position">
                        {userPosition ? `${userPosition.latitude},${userPosition.longitude}` : "aucune"}
                    </span>
                </div>
            );
        },
    };
});

const ACTIVITY = {
    id: 1,
    title: "Marché",
    category: "",
    categoryCodes: [],
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

let urls: string[];
let preferences: unknown;

beforeEach(() => {
    urls = [];
    preferences = null;
    vi.stubGlobal(
        "fetch",
        vi.fn((url: string) => {
            urls.push(url);
            let body: unknown = [];
            if (url.startsWith("/api/v1/activities/nearby") || url.startsWith("/api/v1/activities/within-bounds")) {
                body = [ACTIVITY];
            } else if (url.startsWith("/api/v1/users/me/preferences")) {
                body = preferences;
            } else if (url.startsWith("/api/v1/geocoding/city-suggestions")) {
                body = [{ name: "Lyon", postalCodes: ["69001"], department: "69", latitude: 45.758, longitude: 4.835 }];
            }
            return Promise.resolve(new Response(JSON.stringify(body), { status: 200 }));
        }),
    );
});

afterEach(() => {
    vi.unstubAllGlobals();
    window.localStorage.clear();
});

/** Géolocalisation simulée : chaque demande attend la réponse choisie par le test. */
function stubGeolocation() {
    const pending: { success: PositionCallback; failure: PositionErrorCallback }[] = [];
    const getCurrentPosition = vi.fn((success: PositionCallback, failure: PositionErrorCallback) => {
        pending.push({ success, failure });
    });
    vi.stubGlobal("navigator", { ...navigator, geolocation: { getCurrentPosition } });
    return {
        getCurrentPosition,
        succeed: (latitude: number, longitude: number) =>
            pending.shift()!.success({ coords: { latitude, longitude } } as GeolocationPosition),
        fail: (code: number) =>
            pending.shift()!.failure({ code, PERMISSION_DENIED: 1, POSITION_UNAVAILABLE: 2, TIMEOUT: 3 } as GeolocationPositionError),
    };
}

async function renderApp() {
    const user = userEvent.setup();
    render(
        <MemoryRouter>
            <App />
        </MemoryRouter>,
    );
    await screen.findByText("marqueur Marché");
    return user;
}

function activityCalls(endpoint: "nearby" | "within-bounds") {
    return urls.filter((url) => url.startsWith(`/api/v1/activities/${endpoint}`));
}

function prompt() {
    return screen.queryAllByRole("status").find((element) => element.classList.contains("explorer-location-prompt"));
}

describe("App — localisation explicite (spec P0-A)", () => {
    it("première visite : aucune demande de position, aucune recherche autour d'une position supposée", async () => {
        const geolocation = stubGeolocation();
        await renderApp();

        expect(geolocation.getCurrentPosition).not.toHaveBeenCalled();
        expect(activityCalls("nearby")).toHaveLength(0);
        expect(activityCalls("within-bounds").length).toBeGreaterThan(0);
        expect(screen.getByTestId("map-position")).toHaveTextContent("aucune");
        expect(prompt()).toHaveTextContent("La carte montre une vue d'ensemble, pas ta position.");
        expect(screen.queryByText(/Marseille/)).toBeNull();
    });

    it("« Autour de moi » demande la position et recentre la recherche", async () => {
        const geolocation = stubGeolocation();
        const user = await renderApp();

        await user.click(screen.getAllByRole("button", { name: "⌖ Autour de moi" })[0]);
        expect(geolocation.getCurrentPosition).toHaveBeenCalledTimes(1);
        geolocation.succeed(45.1, 5.7);

        await waitFor(() => expect(screen.getByTestId("map-position")).toHaveTextContent("45.1,5.7"));
        expect(prompt()).toBeUndefined();
        // Spec P0-B : le bouton indique que la recherche part de la position.
        expect(screen.getByRole("button", { name: "⌖ Autour de moi" })).toHaveAttribute("aria-pressed", "true");
    });

    it("permission refusée : message, choix d'une ville proposé, la carte reste utilisable", async () => {
        const geolocation = stubGeolocation();
        const user = await renderApp();

        await user.click(screen.getAllByRole("button", { name: "⌖ Autour de moi" })[0]);
        geolocation.fail(1);

        await waitFor(() => expect(prompt()).toHaveTextContent("Localisation refusée."));
        expect(screen.getByText("marqueur Marché")).toBeInTheDocument();
        await user.click(screen.getByRole("button", { name: "📍 Choisir ma ville" }));
        expect(screen.getByRole("combobox", { name: /Rechercher une activité/ })).toHaveFocus();
    });

    it("position indisponible ou délai dépassé : nouvelle tentative ou ville proposées", async () => {
        const geolocation = stubGeolocation();
        const user = await renderApp();

        await user.click(screen.getAllByRole("button", { name: "⌖ Autour de moi" })[0]);
        geolocation.fail(3);

        await waitFor(() => expect(prompt()).toHaveTextContent("Position introuvable pour le moment."));
        await user.click(screen.getByRole("button", { name: "⌖ Réessayer" }));
        expect(geolocation.getCurrentPosition).toHaveBeenCalledTimes(2);
    });

    it("ville choisie dans les suggestions : la carte et la recherche l'utilisent", async () => {
        stubGeolocation();
        const user = await renderApp();

        await user.type(screen.getByRole("combobox", { name: /Rechercher une activité/ }), "lyo");
        await user.click(await screen.findByRole("option", { name: /Lyon/ }));

        expect(screen.getByTestId("map-position")).toHaveTextContent("45.758,4.835");
        expect(prompt()).toBeUndefined();
    });

    it("ville enregistrée dans les préférences : utilisée sans invitation ni demande de position", async () => {
        const geolocation = stubGeolocation();
        window.localStorage.setItem("locallife.jwt", `x.${btoa(JSON.stringify({ userId: 1, email: "a@b.c", role: "USER" }))}.y`);
        preferences = {
            defaultCity: "Avignon",
            defaultLatitude: 43.95,
            defaultLongitude: 4.81,
            defaultRadiusKm: null,
            defaultCategoryCodes: [],
            configured: true,
        };
        await renderApp();

        expect(screen.getByTestId("map-position")).toHaveTextContent("43.95,4.81");
        expect(prompt()).toBeUndefined();
        expect(geolocation.getCurrentPosition).not.toHaveBeenCalled();
    });
});
