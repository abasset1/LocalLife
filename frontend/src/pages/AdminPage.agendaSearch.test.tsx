import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import AdminPage from "./AdminPage";

vi.mock("../auth/authStorage", () => ({
    getPayload: () => ({ role: "ADMIN" }),
    getToken: () => "jeton-admin",
    clearToken: () => undefined,
}));

function source(id: number, name: string, agendaUid: string | null = null) {
    return { id, name, type: "API", url: null, status: "ACTIVE", lastSyncAt: null, agendaUid, regionFilter: null };
}

const SOURCES = [
    source(129, "Shotgun", "uid-shotgun"),
    source(1375, "Rouen Bibliothèques", "uid-rouen"),
    source(322, "SCARE - Syndicat des Cinémas d'Art, de Répertoire et d'Essai", "16452582"),
];

function json(body: unknown): Response {
    return new Response(JSON.stringify(body), { status: 200, headers: { "Content-Type": "application/json" } });
}

async function openAgendas() {
    vi.stubGlobal(
        "fetch",
        vi.fn(async (input: string) => {
            if (input.startsWith("/api/v1/sources/default-categories")) {
                return json({});
            }
            if (input.startsWith("/api/v1/sources")) {
                return json(SOURCES);
            }
            return json([]);
        }),
    );
    render(
        <MemoryRouter>
            <AdminPage />
        </MemoryRouter>,
    );
    await userEvent.click(screen.getByRole("button", { name: "Agendas" }));
    await screen.findByRole("heading", { name: "Shotgun" });
    return screen.getByRole("searchbox", { name: "Rechercher un agenda" });
}

function agendaNames(): string[] {
    return screen.queryAllByRole("heading", { level: 2 }).map((heading) => heading.textContent ?? "");
}

describe("AdminPage — recherche d'agendas", () => {
    beforeEach(() => {
        vi.unstubAllGlobals();
    });

    it("affiche tous les agendas sans recherche", async () => {
        await openAgendas();

        expect(agendaNames()).toEqual(SOURCES.map((item) => item.name));
    });

    it("filtre sur le nom, sans tenir compte des majuscules ni des accents", async () => {
        const search = await openAgendas();

        await userEvent.type(search, "bibliotheques");

        expect(agendaNames()).toEqual(["Rouen Bibliothèques"]);
        expect(screen.getByText("1 agenda sur 3")).toBeInTheDocument();
    });

    it("trouve un agenda par son identifiant exact ou son uid OpenAgenda", async () => {
        const search = await openAgendas();

        await userEvent.type(search, "129");
        expect(agendaNames()).toEqual(["Shotgun"]);

        await userEvent.clear(search);
        await userEvent.type(search, "16452582");
        expect(agendaNames()).toEqual(["SCARE - Syndicat des Cinémas d'Art, de Répertoire et d'Essai"]);
    });

    it("signale une recherche sans résultat", async () => {
        const search = await openAgendas();

        await userEvent.type(search, "introuvable");

        expect(agendaNames()).toEqual([]);
        expect(screen.getByText("Aucun agenda ne correspond à cette recherche.")).toBeInTheDocument();
    });
});
