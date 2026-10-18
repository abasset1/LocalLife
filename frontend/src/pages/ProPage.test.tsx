import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import ProPage from "./ProPage";

vi.mock("../auth/authStorage", () => ({
    getPayload: () => ({ userId: 7, email: "pro@example.com", role: "PRO" }),
    getToken: () => "jeton-pro",
    clearToken: () => undefined,
}));

function json(body: unknown, status = 200): Response {
    return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

const EXISTING = {
    id: 42, name: "Musée Calvet", placeType: "Museum", address: "65 rue Joseph Vernet", postalCode: "84000",
    city: "Avignon", latitude: 43.944, longitude: 4.802, description: null, website: null,
    coordinatePrecision: "EXACT",
};

function stubFetch(routes: (method: string, url: string, body: unknown) => Response | undefined) {
    const calls: { method: string; url: string; body: unknown }[] = [];
    vi.stubGlobal("fetch", vi.fn(async (input: string, init?: RequestInit) => {
        const method = init?.method ?? "GET";
        const body = init?.body ? JSON.parse(init.body as string) : undefined;
        calls.push({ method, url: input, body });
        return routes(method, input, body) ?? json([]);
    }));
    return calls;
}

function renderPage(path = "/pro") {
    render(
        <MemoryRouter initialEntries={[path]}>
            <ProPage />
        </MemoryRouter>,
    );
}

afterEach(() => vi.unstubAllGlobals());

describe("ProPage", () => {
    it("explique que l'espace est réservé quand le backend refuse le compte", async () => {
        stubFetch(() => json({ message: "Compte professionnel requis." }, 403));
        renderPage();
        expect(await screen.findByText("Compte professionnel requis.")).toBeInTheDocument();
        expect(screen.queryByRole("button", { name: "Créer un lieu" })).not.toBeInTheDocument();
    });

    it("propose trois parcours distincts", async () => {
        stubFetch(() => undefined);
        renderPage();
        expect(await screen.findByRole("button", { name: "Demander la gestion d'un lieu" })).toBeInTheDocument();
        expect(screen.getByRole("button", { name: "Créer un lieu" })).toBeInTheDocument();
        expect(screen.getByRole("button", { name: "Créer un food truck" })).toBeInTheDocument();
    });

    it("vérifie les lieux proches avant de créer, et bascule vers la demande de gestion", async () => {
        const user = userEvent.setup();
        const calls = stubFetch((method, url) => {
            if (url.startsWith("/api/v1/pro/places/similar")) {
                return json({ latitude: 43.944, longitude: 4.802, candidates: [{ place: EXISTING, status: "PUBLISHED", distanceMeters: 12 }] });
            }
            if (method === "POST" && url === "/api/v1/pro/places/42/claims") {
                return json({ id: 5, placeId: 42, placeName: "Musée Calvet", status: "DRAFT" }, 201);
            }
            if (method === "POST" && url === "/api/v1/pro/claims/5/submit") {
                return json({ id: 5, placeId: 42, placeName: "Musée Calvet", status: "SUBMITTED" });
            }
            return undefined;
        });
        renderPage();

        await user.click(await screen.findByRole("button", { name: "Créer un lieu" }));
        await user.type(screen.getByLabelText("Nom"), "Musée Calvet");
        await user.selectOptions(screen.getByLabelText("Catégorie"), "Museum");
        await user.type(screen.getByLabelText("Adresse"), "65 rue Joseph Vernet");
        await user.type(screen.getByLabelText("Commune"), "Avignon");
        await user.click(screen.getByRole("button", { name: "Vérifier et continuer" }));

        expect(await screen.findByText("à 12 m", { exact: false })).toBeInTheDocument();
        expect(calls.some((call) => call.method === "POST" && call.url === "/api/v1/pro/places")).toBe(false);

        await user.click(screen.getByRole("button", { name: "C'est mon lieu" }));
        expect(await screen.findByText("Demande pour « Musée Calvet »")).toBeInTheDocument();
        await user.type(screen.getByLabelText("Ton lien avec le lieu"), "Je suis le directeur");
        await user.click(screen.getByRole("button", { name: "Envoyer la demande" }));

        expect(await screen.findByText("Demande envoyée : LocalLife l'examinera.")).toBeInTheDocument();
        expect(calls.find((call) => call.url === "/api/v1/pro/places/42/claims")?.body)
            .toEqual({ relationship: "Je suis le directeur", evidence: "" });
    });
});
