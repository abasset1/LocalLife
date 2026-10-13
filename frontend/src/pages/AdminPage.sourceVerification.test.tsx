import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import AdminPage from "./AdminPage";

vi.mock("../auth/authStorage", () => ({
    getPayload: () => ({ role: "ADMIN" }),
    getToken: () => "jeton-admin",
    clearToken: () => undefined,
}));

const SOURCE = {
    id: 20,
    name: "DATAtourisme PACA (data.gouv.fr)",
    type: "DATATOURISME",
    url: "https://www.data.gouv.fr/api/1/datasets/r/83a1f131-9e23-4c3b-b1c6-e58f33fe7b80",
    status: "INACTIVE",
    lastSyncAt: null,
    agendaUid: null,
    regionFilter: "84",
};

function report(verdict: string, activated: boolean, problems: string[] = []) {
    return {
        verdict,
        problems,
        collected: 118,
        invalid: 0,
        wouldCreate: 118,
        wouldUpdate: 0,
        wouldArchive: 0,
        archiveWithheld: 0,
        sampleTitles: ["Parcours de l'Art"],
        activated,
    };
}

function json(body: unknown, status = 200): Response {
    return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

/** `fetch` simulé par URL ; l'activation répond avec `activation` et, si elle réussit, la source devient active. */
function stubFetch(activation: { status: number; body: unknown }) {
    let sourceStatus = "INACTIVE";
    const calls: string[] = [];
    vi.stubGlobal(
        "fetch",
        vi.fn(async (input: string, init?: RequestInit) => {
            calls.push(`${init?.method ?? "GET"} ${input}`);
            if (input.endsWith(`/api/v1/admin/import/sources/${SOURCE.id}/activate`)) {
                if (activation.status === 200) {
                    sourceStatus = "ACTIVE";
                }
                return json(activation.body, activation.status);
            }
            if (input.startsWith("/api/v1/sources/default-categories")) {
                return json({});
            }
            if (input.startsWith("/api/v1/sources")) {
                return json([{ ...SOURCE, status: sourceStatus }]);
            }
            return json([]);
        }),
    );
    return calls;
}

async function openAgendas() {
    render(
        <MemoryRouter>
            <AdminPage />
        </MemoryRouter>,
    );
    await userEvent.click(screen.getByRole("button", { name: "Agendas" }));
    return within((await screen.findByRole("heading", { name: SOURCE.name })).closest("li") as HTMLElement);
}

describe("AdminPage — activation vérifiée d'une source DATATOURISME", () => {
    beforeEach(() => {
        vi.unstubAllGlobals();
    });

    it("affiche le refus et ne présente pas la source comme active", async () => {
        const calls = stubFetch({
            status: 409,
            body: report("SUSPICIOUS", false, ["Aucun élément collecté : vérifier la ressource et le filtre."]),
        });
        const card = await openAgendas();

        await userEvent.click(card.getByRole("button", { name: "Activer après vérification" }));

        expect(await card.findByText("Résultat suspect")).toBeInTheDocument();
        expect(card.getByText(/aucune écriture, source inchangée/)).toBeInTheDocument();
        expect(card.getByText("Aucun élément collecté : vérifier la ressource et le filtre.")).toBeInTheDocument();
        expect(card.getByText("INACTIVE")).toBeInTheDocument();
        expect(calls).toContain(`POST /api/v1/admin/import/sources/${SOURCE.id}/activate`);
    });

    it("active la source après un essai réussi et retire le bouton d'activation", async () => {
        stubFetch({ status: 200, body: report("OK", true) });
        const card = await openAgendas();

        await userEvent.click(card.getByRole("button", { name: "Activer après vérification" }));

        expect(await card.findByText("ACTIVE")).toBeInTheDocument();
        expect(card.getByText(/source activée/)).toBeInTheDocument();
        expect(card.queryByRole("button", { name: "Activer après vérification" })).not.toBeInTheDocument();
        expect(card.getByRole("button", { name: "Essai à blanc" })).toBeInTheDocument();
    });
});
