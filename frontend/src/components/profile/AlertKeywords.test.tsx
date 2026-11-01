import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import ActivityAlertsSection from "./ActivityAlertsSection";
import { addKeywords } from "./AlertKeywordsInput";

vi.mock("../../auth/authStorage", () => ({
    getPayload: () => ({ userId: 7, email: "u@example.com", role: "USER" }),
    getToken: () => "jeton",
    clearToken: () => undefined,
}));

const ALERT = { id: 3, categoryCodes: ["MUSIQUE"], includeFoodTrucks: false, city: "Lyon", latitude: 45.76,
    longitude: 4.83, radiusKm: 10, notifyEmail: true, notifyPush: false, keywords: ["jazz"] };

let requests: { url: string; method: string; body: unknown }[];

function stubFetch() {
    requests = [];
    vi.stubGlobal("fetch", vi.fn(async (url: string, init?: RequestInit) => {
        const method = init?.method ?? "GET";
        const body = init?.body ? JSON.parse(String(init.body)) : null;
        requests.push({ url, method, body });
        if (url === "/api/v1/users/me/alerts" && method === "POST") {
            return new Response(JSON.stringify({ ...ALERT, id: 4, categoryCodes: [], keywords: body.keywords }),
                { status: 201 });
        }
        if (url === "/api/v1/users/me/alerts/3" && method === "PATCH") {
            return new Response(JSON.stringify({ ...ALERT, keywords: body.keywords }), { status: 200 });
        }
        if (url === "/api/v1/users/me/alerts") {
            return new Response(JSON.stringify([ALERT]), { status: 200 });
        }
        if (url === "/api/v1/users/me/preferences") {
            return new Response(JSON.stringify({ defaultCity: "Lyon", defaultLatitude: null, defaultLongitude: null,
                defaultRadiusKm: 10, defaultCategoryCodes: [], configured: true }), { status: 200 });
        }
        if (url.startsWith("/api/v1/users/me/preferences/city")) {
            return new Response(JSON.stringify({ city: "Lyon", latitude: 45.76, longitude: 4.83 }), { status: 200 });
        }
        return new Response(JSON.stringify([{ code: "MUSIQUE", label: "Musique", description: null, active: true }]),
            { status: 200 });
    }));
}

afterEach(() => vi.unstubAllGlobals());

describe("Mots-clés des alertes (Premium et Pro)", () => {
    it("ajoute les mots-clés sans doublon, sans tenir compte des accents", () => {
        expect(addKeywords(["Noël"], "jazz, noel ,  marché   de Noël")).toEqual({ keywords: ["Noël", "jazz", "marché de Noël"] });
        expect(addKeywords([], "a")).toEqual({ error: "Un mot-clé fait de 2 à 60 caractères." });
    });

    it("crée une alerte avec des mots-clés seuls", async () => {
        stubFetch();
        const user = userEvent.setup();
        render(<ActivityAlertsSection canUseKeywords maxAlerts={10} />);

        const input = await screen.findByLabelText(/Mots-clés/);
        await user.type(input, "randonnée{Enter}marché de Noël,");
        const chosen = screen.getByRole("list", { name: "Mots-clés choisis" });
        expect(within(chosen).getAllByRole("listitem").map((item) => item.textContent?.replace("✕", ""))).toEqual([
            "randonnée", "marché de Noël",
        ]);
        await user.click(screen.getByRole("button", { name: "Créer l'alerte" }));

        const created = requests.find((request) => request.method === "POST");
        expect(created?.body).toMatchObject({ categoryCodes: [], keywords: ["randonnée", "marché de Noël"] });
        expect(await screen.findByText("« marché de Noël »")).toBeInTheDocument();
    });

    it("modifie les mots-clés d'une alerte existante", async () => {
        stubFetch();
        const user = userEvent.setup();
        render(<ActivityAlertsSection canUseKeywords maxAlerts={10} />);

        expect(await screen.findByText("« jazz »")).toBeInTheDocument();
        await user.click(screen.getByRole("button", { name: "Modifier les mots-clés de l'alerte Lyon" }));
        await user.type(screen.getByLabelText("Ajouter un mot-clé à l'alerte Lyon"), "blues{Enter}");
        await user.click(screen.getByRole("button", { name: "Retirer le mot-clé jazz" }));
        await user.click(screen.getByRole("button", { name: "Enregistrer" }));

        expect(requests).toContainEqual({ url: "/api/v1/users/me/alerts/3", method: "PATCH", body: { keywords: ["blues"] } });
        expect(await screen.findByText("« blues »")).toBeInTheDocument();
    });

    it("propose Premium, gratuit pour le moment, à un compte gratuit", async () => {
        stubFetch();
        render(<ActivityAlertsSection canUseKeywords={false} maxAlerts={1} />);

        expect(await screen.findByText(/réservés à Premium/)).toHaveTextContent("gratuit pour le moment");
        expect(screen.getByRole("link", { name: "Passer à Premium" })).toHaveAttribute("href", "#profile-subscription-title");
        expect(screen.queryByPlaceholderText(/jazz, marché de Noël/)).toBeNull();
        // Une alerte qui a déjà des mots-clés peut toujours les retirer.
        expect(screen.getByRole("button", { name: "Modifier les mots-clés de l'alerte Lyon" })).toBeInTheDocument();
    });
});
