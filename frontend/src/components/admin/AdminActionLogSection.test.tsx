import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import AdminActionLogSection from "./AdminActionLogSection";

vi.mock("../../auth/authStorage", () => ({
    getToken: () => "jeton-admin",
    clearToken: () => undefined,
}));

function json(body: unknown, status = 200): Response {
    return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

afterEach(() => vi.unstubAllGlobals());

describe("AdminActionLogSection", () => {
    it("affiche le journal, filtre et pagine", async () => {
        const user = userEvent.setup();
        const urls: string[] = [];
        vi.stubGlobal("fetch", vi.fn(async (url: string) => {
            urls.push(url);
            return json({
                items: [{
                    id: 1, occurredAt: "2026-10-22T08:00:00Z", actorId: 1, actorEmail: "admin@example.com",
                    action: "ACTIVITY_REJECT", targetType: "ACTIVITY", targetId: 9, summary: "Activité refusée",
                    before: "{\"status\":\"PENDING\"}", after: "{\"status\":\"REJECTED\"}", reason: "Hors périmètre",
                }],
                total: 30, page: 0, size: 25,
            });
        }));
        render(<AdminActionLogSection />);

        expect(await screen.findByText("ACTIVITY_REJECT · ACTIVITY n° 9")).toBeInTheDocument();
        expect(screen.getByText(/admin@example\.com/)).toBeInTheDocument();
        expect(screen.getByText("Motif : Hors périmètre")).toBeInTheDocument();
        expect(screen.getByText(/"status": "PENDING"/)).toBeInTheDocument();
        expect(screen.getByText(/"status": "REJECTED"/)).toBeInTheDocument();
        expect(urls[0]).toBe("/api/v1/admin/action-log?page=0&size=25");

        await user.type(screen.getByLabelText("Type de cible"), "ACTIVITY");
        await user.type(screen.getByLabelText("Identifiant de cible"), "9");
        await user.click(screen.getByRole("button", { name: "Filtrer" }));
        await screen.findByText("Page 1 / 2");
        expect(urls.at(-1)).toBe("/api/v1/admin/action-log?page=0&size=25&targetType=ACTIVITY&targetId=9");

        await user.click(screen.getByRole("button", { name: "Suivante →" }));
        expect(await screen.findByText("Page 2 / 2")).toBeInTheDocument();
        expect(urls.at(-1)).toBe("/api/v1/admin/action-log?page=1&size=25&targetType=ACTIVITY&targetId=9");
    });
});
