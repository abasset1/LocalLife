import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import AdminPage from "./AdminPage";

vi.mock("../auth/authStorage", () => ({
    getPayload: () => ({ userId: 1, role: "ADMIN" }),
    getToken: () => "jeton-admin",
    clearToken: () => undefined,
}));

const ACTIVITY = {
    id: 9, title: "Concert au parc", description: "Plein air", category: "MUSIC", latitude: 43.9, longitude: 4.8,
    startDate: "2026-11-01T20:00:00", endDate: "2026-11-01T22:00:00", status: "PENDING", sourceId: 1, importKey: null,
    url: null,
};

function json(body: unknown, status = 200): Response {
    return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

function stubFetch(route: (method: string, url: string) => Response | undefined) {
    const calls: { method: string; url: string; init?: RequestInit }[] = [];
    vi.stubGlobal("fetch", vi.fn(async (url: string, init?: RequestInit) => {
        const method = init?.method ?? "GET";
        calls.push({ method, url, init });
        return route(method, url) ?? (url.startsWith("/api/v1/admin/activities?status=") ? json([ACTIVITY]) : json([]));
    }));
    return calls;
}

function renderPage() {
    render(<MemoryRouter><AdminPage /></MemoryRouter>);
}

afterEach(() => vi.unstubAllGlobals());

describe("AdminPage — modération", () => {
    it("exige un motif pour refuser une activité et l'envoie au serveur", async () => {
        const user = userEvent.setup();
        const calls = stubFetch((method, url) =>
            method === "PATCH" && url === "/api/v1/admin/activities/9/reject" ? json({ ...ACTIVITY, status: "REJECTED" }) : undefined);
        renderPage();

        await user.click(await screen.findByRole("button", { name: "Refuser" }));
        const dialog = screen.getByRole("dialog");
        const confirm = within(dialog).getByRole("button", { name: "Refuser" });
        expect(confirm).toBeDisabled();
        await user.type(within(dialog).getByLabelText("Motif du refus"), "Hors périmètre");
        await user.click(confirm);

        expect(await screen.findByText("Aucune activité dans cette catégorie.")).toBeInTheDocument();
        const call = calls.find((item) => item.url === "/api/v1/admin/activities/9/reject");
        expect(JSON.parse(String(call?.init?.body))).toEqual({ reason: "Hors périmètre" });
    });

    it("annuler le motif n'envoie rien, et un refus 403 du serveur est affiché", async () => {
        const user = userEvent.setup();
        const calls = stubFetch((method, url) => method === "PATCH" && url === "/api/v1/admin/activities/9/publish"
            ? json({ message: "Tu ne peux pas valider une activité que tu as créée." }, 403)
            : undefined);
        renderPage();

        await user.click(await screen.findByRole("button", { name: "Refuser" }));
        await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Annuler" }));
        expect(calls.some((call) => call.method === "PATCH")).toBe(false);

        await user.click(screen.getByRole("button", { name: "Valider" }));
        expect(await screen.findByText("Tu ne peux pas valider une activité que tu as créée.")).toBeInTheDocument();
    });

    it("met une activité à la corbeille avec un motif", async () => {
        const user = userEvent.setup();
        const calls = stubFetch((method) => (method === "DELETE" ? new Response(null, { status: 204 }) : undefined));
        renderPage();

        await user.click(await screen.findByRole("button", { name: "Supprimer" }));
        const dialog = screen.getByRole("dialog");
        await user.type(within(dialog).getByLabelText("Motif de la suppression"), "Doublon");
        await user.click(within(dialog).getByRole("button", { name: "Mettre à la corbeille" }));

        expect(await screen.findByText("Aucune activité dans cette catégorie.")).toBeInTheDocument();
        expect(calls.find((call) => call.method === "DELETE")?.url).toBe("/api/v1/admin/activities/9?reason=Doublon");
    });
});
