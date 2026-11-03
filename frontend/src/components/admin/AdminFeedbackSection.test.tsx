import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import AdminFeedbackSection from "./AdminFeedbackSection";
import type { FeedbackItem } from "./AdminFeedbackSection";

afterEach(() => vi.unstubAllGlobals());

const ITEM: FeedbackItem = {
    id: 3,
    kind: "BUG",
    message: "Le bouton Suivre ne répond pas",
    pageUrl: "/?zoom=9",
    userAgent: "Mozilla/5.0",
    status: "NEW",
    createdAt: "2026-11-03T10:00:00",
    handledAt: null,
    userId: 7,
    username: "marie",
    email: "marie@example.com",
};

describe("AdminFeedbackSection", () => {
    it("liste les retours à traiter et marque un retour traité", async () => {
        const user = userEvent.setup();
        const onNewCountChange = vi.fn();
        let pending = [ITEM];
        const fetchMock = vi.fn(async (input: string, init?: RequestInit) => {
            if (init?.method === "PATCH") {
                pending = [];
                return new Response(JSON.stringify({ ...ITEM, status: "DONE" }), { headers: { "Content-Type": "application/json" } });
            }
            return new Response(JSON.stringify({ items: pending, total: pending.length, newCount: pending.length }),
                { headers: { "Content-Type": "application/json" } });
        });
        vi.stubGlobal("fetch", fetchMock);
        render(<AdminFeedbackSection onNewCountChange={onNewCountChange} />);

        const item = (await screen.findByText("Le bouton Suivre ne répond pas")).closest("li")!;
        expect(within(item).getByText("🐞 Anomalie")).toBeInTheDocument();
        expect(within(item).getByRole("link", { name: "marie@example.com" })).toHaveAttribute("href", "mailto:marie@example.com");
        expect(within(item).getByText("Page : /?zoom=9")).toBeInTheDocument();
        expect(String(fetchMock.mock.calls[0][0])).toContain("status=NEW");
        expect(onNewCountChange).toHaveBeenLastCalledWith(1);

        await user.click(within(item).getByRole("button", { name: "Marquer traité" }));

        expect(await screen.findByText("Aucun retour à traiter.")).toBeInTheDocument();
        const patch = fetchMock.mock.calls.find(([, init]) => init?.method === "PATCH")!;
        expect(patch[0]).toBe("/api/v1/admin/feedback/3");
        expect(JSON.parse(String(patch[1]!.body))).toEqual({ done: true });
        expect(onNewCountChange).toHaveBeenLastCalledWith(0);
    });
});
