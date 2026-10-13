import { render, screen } from "@testing-library/react";
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
        ...overrides,
    };
}

describe("AdminImportsSection — motif d'un import bloqué", () => {
    afterEach(() => {
        vi.unstubAllGlobals();
    });

    it("affiche pourquoi chaque source a été bloquée, et rien pour une source normale", async () => {
        vi.stubGlobal(
            "fetch",
            vi.fn(async () =>
                new Response(
                    JSON.stringify([
                        result("DATAtourisme PACA", {
                            errors: 1,
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
                    ]),
                    { headers: { "Content-Type": "application/json" } },
                ),
            ),
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
});
