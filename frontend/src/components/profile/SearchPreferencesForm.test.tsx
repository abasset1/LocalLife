import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import SearchPreferencesForm from "./SearchPreferencesForm";

vi.mock("../../auth/authStorage", () => ({
    getPayload: () => ({ userId: 7, email: "u@example.com", role: "USER" }),
    isAuthenticated: () => true,
    subscribeSession: () => () => undefined,
    refreshSession: async () => null,
    onAuthenticated: async () => null,
    clearSession: async () => undefined,
    clearSessionLocal: () => undefined,
    readCsrfToken: () => null,
}));

const PREFERENCES = {
    defaultCity: null,
    defaultLatitude: null,
    defaultLongitude: null,
    defaultRadiusKm: null,
    defaultCategoryCodes: [],
    defaultMapLayer: "foodTrucks",
    configured: true,
};

function stubFetch() {
    const puts: unknown[] = [];
    vi.stubGlobal("fetch", vi.fn(async (url: string, init?: RequestInit) => {
        if (url === "/api/v1/users/me/preferences" && init?.method === "PUT") {
            const body = JSON.parse(String(init.body));
            puts.push(body);
            return new Response(JSON.stringify({ ...PREFERENCES, defaultMapLayer: body.defaultMapLayer || null }));
        }
        if (url === "/api/v1/users/me/preferences") {
            return new Response(JSON.stringify(PREFERENCES));
        }
        return new Response(JSON.stringify([]));
    }));
    return puts;
}

afterEach(() => vi.unstubAllGlobals());

describe("SearchPreferencesForm — carte à l'ouverture", () => {
    it("affiche la carte enregistrée et enregistre le nouveau choix", async () => {
        const puts = stubFetch();
        const user = userEvent.setup();
        render(<SearchPreferencesForm />);

        expect(await screen.findByRole("radio", { name: "🚚 Food trucks" })).toBeChecked();

        await user.click(screen.getByRole("radio", { name: "🏛 Lieux" }));
        await user.click(screen.getByRole("button", { name: "Enregistrer" }));

        expect(await screen.findByText("Paramètres enregistrés.")).toBeInTheDocument();
        expect(puts).toEqual([expect.objectContaining({ defaultMapLayer: "places" })]);
    });

    it("« Dernière utilisée » efface la carte enregistrée", async () => {
        const puts = stubFetch();
        const user = userEvent.setup();
        render(<SearchPreferencesForm />);

        await user.click(await screen.findByRole("radio", { name: "Dernière utilisée" }));
        await user.click(screen.getByRole("button", { name: "Enregistrer" }));

        expect(await screen.findByText("Paramètres enregistrés.")).toBeInTheDocument();
        expect(puts).toEqual([expect.objectContaining({ defaultMapLayer: "" })]);
        expect(screen.getByRole("radio", { name: "Dernière utilisée" })).toBeChecked();
    });
});
