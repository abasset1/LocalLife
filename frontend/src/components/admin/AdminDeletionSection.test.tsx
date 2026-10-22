import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import AdminDeletionSection from "./AdminDeletionSection";

vi.mock("../../auth/authStorage", () => ({
    getToken: () => "jeton-admin",
    clearToken: () => undefined,
}));

function json(body: unknown, status = 200): Response {
    return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

afterEach(() => vi.unstubAllGlobals());

describe("AdminDeletionSection", () => {
    it("envoie le motif et le mot de passe, puis restaure le lot supprimé", async () => {
        const user = userEvent.setup();
        let passwordOk = false;
        const calls: { method: string; url: string; init?: RequestInit }[] = [];
        vi.stubGlobal("fetch", vi.fn(async (url: string, init?: RequestInit) => {
            const method = init?.method ?? "GET";
            calls.push({ method, url, init });
            if (url === "/api/v1/sources") {
                return json([{ id: 4, name: "OpenAgenda Vaucluse" }]);
            }
            if (url.startsWith("/api/v1/admin/activities/deletion-preview")) {
                return json({ count: 3 });
            }
            if (method === "DELETE") {
                passwordOk = new Headers(init?.headers).get("X-Confirm-Password") === "bon-mot-de-passe";
                return passwordOk
                    ? json({ count: 3, batch: "b-42" })
                    : json({ message: "Confirme avec ton mot de passe." }, 428);
            }
            if (url === "/api/v1/admin/activities/trash/batches/b-42/restore") {
                return json({ count: 3 });
            }
            return json([]);
        }));
        const onDeleted = vi.fn();
        render(<AdminDeletionSection onActivitiesDeleted={onDeleted} />);

        await user.selectOptions(await screen.findByLabelText("Agenda / source"), "4");
        await user.click(screen.getByRole("button", { name: "Compter les activités visées" }));
        await user.click(await screen.findByRole("button", { name: "Supprimer 3 activités" }));

        let dialog = screen.getByRole("dialog");
        await user.type(within(dialog).getByLabelText("Motif de la suppression (journalisé)"), "Agenda abandonné");
        await user.type(within(dialog).getByLabelText("Ton mot de passe (confirmation)"), "faux");
        await user.click(within(dialog).getByRole("button", { name: "Mettre à la corbeille" }));
        expect(await screen.findByText("Confirme avec ton mot de passe.")).toBeInTheDocument();

        // L'aperçu reste valable après un 428 : nouvelle tentative avec le bon mot de passe.
        await user.click(screen.getByRole("button", { name: "Supprimer 3 activités" }));
        dialog = screen.getByRole("dialog");
        await user.type(within(dialog).getByLabelText("Motif de la suppression (journalisé)"), "Agenda abandonné");
        await user.type(within(dialog).getByLabelText("Ton mot de passe (confirmation)"), "bon-mot-de-passe");
        await user.click(within(dialog).getByRole("button", { name: "Mettre à la corbeille" }));

        expect(await screen.findByText("3 activités mises à la corbeille (lot b-42).")).toBeInTheDocument();
        const deletion = calls.filter((call) => call.method === "DELETE").at(-1);
        expect(deletion?.url).toBe("/api/v1/admin/activities?sourceId=4&expectedCount=3&reason=Agenda%20abandonn%C3%A9");
        expect(new Headers(deletion?.init?.headers).get("X-Confirm-Password")).toBe("bon-mot-de-passe");
        expect(onDeleted).toHaveBeenCalledTimes(1);

        await user.click(screen.getByRole("button", { name: "Restaurer ce lot" }));
        expect(await screen.findByText("3 activités restaurées.")).toBeInTheDocument();
        expect(calls.some((call) => call.method === "POST"
            && call.url === "/api/v1/admin/activities/trash/batches/b-42/restore")).toBe(true);
    });
});
