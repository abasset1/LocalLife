import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import AdminImportsSection from "./AdminImportsSection";

vi.mock("../../auth/authStorage", () => ({
    getToken: () => "jeton-admin",
    clearToken: () => undefined,
}));

function result(sourceName: string, overrides: Record<string, unknown>) {
    return {
        sourceName,
        startedAt: "2026-10-13T10:00:00",
        endedAt: "2026-10-13T10:00:05",
        fetched: 0,
        created: 0,
        updated: 0,
        unchanged: 0,
        archived: 0,
        ignored: 0,
        duplicates: 0,
        needsReview: 0,
        errors: 0,
        archiveWithheld: 0,
        blockedReason: null,
        status: "SUCCESS",
        pagesRead: 1,
        received: 0,
        stopReason: null,
        ...overrides,
    };
}

function discovery(overrides: Record<string, unknown>) {
    return {
        runId: 1,
        scope: "ALL",
        status: "IN_PROGRESS",
        complete: false,
        resumable: true,
        stopReason: "Lot de 20 page(s) traité ; l'annuaire continue : relancer pour poursuivre.",
        pagesThisBatch: 20,
        pagesRead: 20,
        examined: 2000,
        newAgendas: 2000,
        alreadyKnown: 0,
        ignored: 0,
        errors: 0,
        providerTotal: null,
        startedAt: null,
        updatedAt: null,
        endedAt: null,
        ...overrides,
    };
}

const json = (body: unknown, status = 200) =>
    new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

type Route = (url: string, init?: RequestInit) => Response | undefined;

/** `fetch` simulé par URL : chaque appel de la section reçoit la réponse de sa route. */
function stubFetch(route: Route) {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = String(input);
        const routed = route(url, init);
        if (routed) {
            return routed;
        }
        if (url.includes("/discovery-settings")) {
            return json({ defaultPagesPerBatch: 20, maxPagesPerBatch: 200, maxActivationBatch: 2, maxCatalogPageSize: 200 });
        }
        if (url.includes("/discover-agendas/latest")) {
            return new Response(null, { status: 204 });
        }
        if (url.includes("/discovered-agendas")) {
            return json({ items: [], total: 0, page: 0, size: 50 });
        }
        if (url.includes("/sync-states")) {
            return json([]);
        }
        return json({ message: `route non simulée : ${url}` }, 500);
    });
    vi.stubGlobal("fetch", fetchMock);
    return fetchMock;
}

describe("AdminImportsSection — bilan d'import", () => {
    afterEach(() => {
        vi.unstubAllGlobals();
    });

    it("affiche pourquoi chaque source a été bloquée, et rien pour une source normale", async () => {
        stubFetch((url, init) =>
            url.endsWith("/api/v1/admin/import") && init?.method === "POST"
                ? json([
                      result("DATAtourisme PACA", {
                          errors: 1,
                          status: "FAILED",
                          blockedReason:
                              "Collecte en échec, import interrompu sans rien créer, modifier ni archiver : "
                              + "Fichier DATAtourisme vide : import interrompu.",
                      }),
                      result("OpenAgenda Avignon", {
                          fetched: 30,
                          archiveWithheld: 12,
                          blockedReason: "Archivage suspendu : 12 activités à venir sur 12 ont disparu de la collecte.",
                      }),
                      result("Agenda sans souci", { fetched: 5, unchanged: 5 }),
                  ])
                : undefined,
        );
        // L'import demande une confirmation : l'administrateur répond « OK ».
        vi.stubGlobal("confirm", vi.fn(() => true));
        render(<AdminImportsSection onSourcesChanged={() => undefined} />);

        await userEvent.click(screen.getByRole("button", { name: "Lancer l'import" }));

        expect(await screen.findByText(/Fichier DATAtourisme vide/)).toBeInTheDocument();
        expect(screen.getByText(/Archivage suspendu : 12 activités à venir/)).toBeInTheDocument();
        expect(screen.getByText(/archivage suspendu pour 12 activité\(s\)/)).toBeInTheDocument();
        expect(screen.getAllByRole("listitem")).toHaveLength(2);
    });

    it("ne présente jamais un import partiel comme complet", async () => {
        stubFetch((url, init) =>
            url.endsWith("/api/v1/admin/import") && init?.method === "POST"
                ? json([
                      result("Agenda géant", {
                          status: "PARTIAL",
                          pagesRead: 2000,
                          received: 600000,
                          blockedReason:
                              "Collecte incomplète : limite de sécurité de 2000 pages atteinte. Aucun archivage.",
                      }),
                  ])
                : undefined,
        );
        vi.stubGlobal("confirm", vi.fn(() => true));
        render(<AdminImportsSection onSourcesChanged={() => undefined} />);

        await userEvent.click(screen.getByRole("button", { name: "Lancer l'import" }));

        expect(await screen.findByText(/1 collecte\(s\) partielle\(s\)/)).toBeInTheDocument();
        expect(screen.queryByText(/toutes les collectes sont complètes/)).not.toBeInTheDocument();
        expect(screen.getByText("Partiel")).toBeInTheDocument();
        expect(screen.getByText(/limite de sécurité de 2000 pages/)).toBeInTheDocument();
    });

    it("affiche l'état de synchronisation de chaque source", async () => {
        stubFetch((url) =>
            url.includes("/sync-states")
                ? json([
                      {
                          sourceId: 1,
                          sourceName: "OpenAgenda Avignon",
                          sourceStatus: "ACTIVE",
                          lastAttemptAt: "2026-10-20T10:00:00",
                          lastStatus: "PARTIAL",
                          lastMessage: "Collecte incomplète : page 3 en échec après 3 essai(s).",
                          lastPagesRead: 2,
                          lastReceived: 600,
                          lastDurationMs: 1200,
                          lastSuccessAt: null,
                      },
                  ])
                : undefined,
        );
        render(<AdminImportsSection onSourcesChanged={() => undefined} />);

        expect(await screen.findByText(/page 3 en échec après 3 essai/)).toBeInTheDocument();
        expect(screen.getByText("Partiel")).toBeInTheDocument();
    });
});

describe("AdminImportsSection — découverte et sélection des agendas", () => {
    afterEach(() => {
        vi.unstubAllGlobals();
    });

    it("signale un parcours incomplet comme reprenable, sans le présenter comme exhaustif", async () => {
        const fetchMock = stubFetch((url, init) =>
            url.includes("/discover-agendas?") && init?.method === "POST" ? json(discovery({})) : undefined,
        );
        render(<AdminImportsSection onSourcesChanged={() => undefined} />);

        await userEvent.click(screen.getByRole("button", { name: "Lancer la découverte" }));

        expect(await screen.findByText("Parcours incomplet, reprenable")).toBeInTheDocument();
        expect(screen.queryByText("Annuaire lu en entier")).not.toBeInTheDocument();
        expect(screen.getByRole("button", { name: "Poursuivre la découverte" })).toBeInTheDocument();
        expect(fetchMock).toHaveBeenCalledWith(
            expect.stringContaining("/api/v1/admin/import/discover-agendas?scope=ALL"),
            expect.objectContaining({ method: "POST" }),
        );
    });

    it("enchaîne les lots jusqu'à la fin de l'annuaire quand c'est demandé", async () => {
        const batches = [
            discovery({}),
            discovery({ status: "COMPLETE", complete: true, resumable: false, stopReason: "Fin de l'annuaire atteinte." }),
        ];
        stubFetch((url, init) =>
            url.includes("/discover-agendas?") && init?.method === "POST" ? json(batches.shift()) : undefined,
        );
        render(<AdminImportsSection onSourcesChanged={() => undefined} />);

        await userEvent.click(screen.getByLabelText(/Enchaîner les lots/));
        await userEvent.click(screen.getByRole("button", { name: "Lancer la découverte" }));

        expect(await screen.findByText("Annuaire lu en entier")).toBeInTheDocument();
        expect(batches).toHaveLength(0);
    });

    it("crée des sources seulement pour la sélection, dans la limite du lot", async () => {
        const onSourcesChanged = vi.fn();
        const items = ["101", "102", "103"].map((uid) => ({
            agendaUid: uid,
            title: `Agenda ${uid}`,
            url: `https://openagenda.com/agenda-${uid}`,
            official: true,
            sourceId: null,
            sourceName: null,
            sourceStatus: null,
        }));
        const fetchMock = stubFetch((url, init) => {
            if (url.includes("/discovered-agendas/sources") && init?.method === "POST") {
                return json({ requested: 1, created: [{ sourceId: 9, name: "Agenda 101", agendaUid: "101" }], alreadyKnown: [], notFound: [] });
            }
            if (url.includes("/discovered-agendas?")) {
                return json({ items, total: 3, page: 0, size: 50 });
            }
            return undefined;
        });
        vi.stubGlobal("confirm", vi.fn(() => true));
        render(<AdminImportsSection onSourcesChanged={onSourcesChanged} />);

        const table = await screen.findByRole("table");
        await userEvent.click(within(table).getByLabelText("Choisir Agenda 101"));
        await userEvent.click(within(table).getByLabelText("Choisir Agenda 102"));
        await userEvent.click(within(table).getByLabelText("Choisir Agenda 103"));
        // Limite de lot : 2 (simulée) — trois agendas cochés, création impossible.
        expect(screen.getByRole("button", { name: "Créer les sources sélectionnées (3)" })).toBeDisabled();
        expect(screen.getByText(/retire des agendas de la sélection/)).toBeInTheDocument();

        await userEvent.click(within(table).getByLabelText("Choisir Agenda 102"));
        await userEvent.click(within(table).getByLabelText("Choisir Agenda 103"));
        await userEvent.click(screen.getByRole("button", { name: "Créer les sources sélectionnées (1)" }));

        expect(await screen.findByText(/1 source\(s\) créée\(s\)/)).toBeInTheDocument();
        expect(onSourcesChanged).toHaveBeenCalled();
        const activationCall = fetchMock.mock.calls.find(([url]) => String(url).includes("/discovered-agendas/sources"));
        expect(JSON.parse(String(activationCall?.[1]?.body))).toEqual({ agendaUids: ["101"] });
    });
});
