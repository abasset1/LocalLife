import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import AdminTrashSection from "./AdminTrashSection";

vi.mock("../../auth/authStorage", () => ({
    getToken: () => "jeton-admin",
    clearToken: () => undefined,
}));

function json(body: unknown, status = 200): Response {
    return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

const ITEM = {
    id: 9, title: "Concert au parc", city: "Avignon", startDate: "2026-11-01T20:00:00", previousStatus: "PUBLISHED",
    sourceId: 1, createdBy: 7, deletedAt: "2026-10-22T08:00:00Z", deletedBy: 1, deletedByEmail: "admin@example.com",
    deletionReason: "Doublon", deletionBatch: "b-42",
};

afterEach(() => vi.unstubAllGlobals());

describe("AdminTrashSection", () => {
    it("liste la corbeille et restaure une activité, puis un lot", async () => {
        const user = userEvent.setup();
        let items = [ITEM, { ...ITEM, id: 10, title: "Marché nocturne" }];
        const calls: string[] = [];
        vi.stubGlobal("fetch", vi.fn(async (url: string, init?: RequestInit) => {
            calls.push(`${init?.method ?? "GET"} ${url}`);
            if (url === "/api/v1/admin/activities/9/restore") {
                items = items.filter((item) => item.id !== 9);
                return new Response(null, { status: 204 });
            }
            if (url === "/api/v1/admin/activities/trash/batches/b-42/restore") {
                items = [];
                return json({ count: 1 });
            }
            return json({ items, total: items.length, page: 0, size: 20 });
        }));
        const onRestored = vi.fn();
        render(<AdminTrashSection onRestored={onRestored} />);

        expect(await screen.findByText("Concert au parc")).toBeInTheDocument();
        expect(screen.getByText(/purgées définitivement après\s+30 jours/)).toBeInTheDocument();
        expect(screen.getAllByText("Motif : Doublon")).toHaveLength(2);
        expect(calls[0]).toBe("GET /api/v1/admin/activities/trash?page=0&size=20");

        await user.click(screen.getAllByRole("button", { name: "Restaurer" })[0]);
        expect(await screen.findByText("« Concert au parc » restaurée.")).toBeInTheDocument();
        expect(screen.queryByText("Concert au parc")).not.toBeInTheDocument();

        await user.click(screen.getByRole("button", { name: "Restaurer le lot" }));
        expect(await screen.findByText("Lot b-42 : 1 activité restaurée.")).toBeInTheDocument();
        expect(await screen.findByText("La corbeille est vide.")).toBeInTheDocument();
        expect(onRestored).toHaveBeenCalledTimes(2);
    });
});
